package com.dmwnezes.radar

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.os.Handler
import android.os.Looper

enum class Aba { RADAR, HOJE, JOGOS }

class Estado(private val ctx: Context) {
    val dados = Dados(ctx)
    val contas = Contas(Cofre(ctx))
    private val busca = Busca(dados, contas)
    private val principal = Handler(Looper.getMainLooper())

    var aba by mutableStateOf(Aba.HOJE)
    var radar by mutableStateOf(dados.radar())
    var ofertas by mutableStateOf(dados.ofertas())
    var biblioteca by mutableStateOf(dados.biblioteca())
    var filtro by mutableStateOf<Loja?>(null)
    var carregando by mutableStateOf(false)
    var aberto by mutableStateOf<Jogo?>(null)
    var comparando by mutableStateOf(false)
    var termo by mutableStateOf("")
    var resultados by mutableStateOf<List<Jogo>>(emptyList())
    var buscando by mutableStateOf(false)
    var erroConta by mutableStateOf<String?>(null)
    var contasVersao by mutableStateOf(0)
    var novaVersao by mutableStateOf<Atualizador.Versao?>(null)

    private val possuidos get() = biblioteca.map { chaveDoTitulo(it.titulo) }.toSet()
    fun jaTem(j: Jogo) = j.chave in possuidos
    fun itemDe(j: Jogo) = radar.firstOrNull { it.jogo.chave == j.chave }

    val jogosHoje: List<Jogo> get() {
        val f = filtro
        return rankear(agrupar(if (f == null) ofertas else ofertas.filter { it.loja == f }))
    }

    private fun emFundo(trabalho: () -> Unit) = Thread(trabalho).start()
    private fun naTela(f: () -> Unit) { principal.post(f) }

    init {
        if (ofertas.isEmpty() || System.currentTimeMillis() - dados.ofertasEm > 24L * 60 * 60 * 1000) atualizar()
        emFundo { runCatching { Atualizador.verificar(ctx) }.getOrNull()?.let { v -> naTela { novaVersao = v } } }
    }

    fun atualizar() {
        if (carregando) return
        carregando = true
        emFundo {
            runCatching {
                Verificacao.atualizarOfertas(dados)
                Verificacao.verificarRadar(ctx, dados, notificar = true)
                Verificacao.sincronizarContas(dados, contas)
            }
            naTela { recarregar(); carregando = false }
        }
    }

    private fun recarregar() {
        radar = dados.radar(); ofertas = dados.ofertas(); biblioteca = dados.biblioteca()
    }

    fun abrir(j: Jogo) {
        aberto = j
        comparando = true
        emFundo {
            val c = busca.completar(j)
            naTela { if (aberto?.chave == j.chave) aberto = c; comparando = false }
        }
    }

    fun alternarRadar(j: Jogo) {
        if (itemDe(j) != null) {
            dados.alterarRadar { l -> l.filter { it.jogo.chave != j.chave } }
            radar = dados.radar()
            return
        }
        val novo = registrarPreco(ItemRadar(j), j.melhor?.atualCent ?: 0, hoje())
        dados.alterarRadar { it + novo }
        radar = dados.radar()
        emFundo {
            val c = busca.completar(j)
            val precos = c.precos.mapValues { (_, p) -> runCatching { Fontes.precoAtual(p) }.getOrNull()?.copy(titulo = p.titulo, capa = p.capa) ?: p }
            dados.alterarRadar { l -> l.map { if (it.jogo.chave == j.chave) it.copy(jogo = c.copy(precos = precos)) else it } }
            naTela { radar = dados.radar() }
        }
    }

    fun buscar() {
        val t = termo.trim()
        if (t.length < 2) { resultados = emptyList(); return }
        buscando = true
        emFundo {
            val r = runCatching { busca.buscar(t) }.getOrDefault(emptyList())
            naTela { if (termo.trim() == t) resultados = r; buscando = false }
        }
    }

    private fun conta(acao: () -> Unit, depois: Loja) {
        erroConta = null
        carregando = true
        emFundo {
            val e = runCatching { acao() }.exceptionOrNull()
            if (e == null) {
                runCatching {
                    dados.trocarBiblioteca(depois, when (depois) {
                        Loja.PS -> contas.bibliotecaPs()
                        Loja.STEAM -> contas.bibliotecaSteam()
                        Loja.XBOX -> contas.bibliotecaXbox()
                    })
                }
            }
            naTela {
                // A mensagem de erro nunca repete o token.
                erroConta = when (e) { null -> null; is ContaInvalida -> e.message; else -> "Não consegui conectar. Verifique a internet e tente de novo." }
                carregando = false; contasVersao++; recarregar()
            }
        }
    }

    fun conectarPs(t: String) = conta({ contas.conectarPs(t) }, Loja.PS)
    fun conectarSteam(id: String, chave: String) = conta({ contas.conectarSteam(id, chave) }, Loja.STEAM)
    fun iniciarXbox(cliente: String): Uri? = try { erroConta = null; contas.iniciarXbox(cliente) } catch (e: ContaInvalida) { erroConta = e.message; null }
    fun concluirXbox(u: Uri) = conta({ contas.concluirXbox(u) }, Loja.XBOX)

    fun desconectar(l: Loja) {
        contas.desconectar(l)
        dados.trocarBiblioteca(l, emptyList())
        contasVersao++
        recarregar()
    }

    fun instalarNovaVersao() {
        val v = novaVersao ?: return
        emFundo { runCatching { Atualizador.baixarEInstalar(ctx, v) } }
    }

    private fun hoje() = System.currentTimeMillis() / (24L * 60 * 60 * 1000)
}
