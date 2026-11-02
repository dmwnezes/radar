package com.dmwnezes.radar

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Conversão para JSON: pura, para os testes rodarem fora do Android. */
object Json {
    fun preco(p: Preco) = JSONObject().put("loja", p.loja.name).put("id", p.id).put("t", p.titulo).put("capa", p.capa ?: JSONObject.NULL)
        .put("b", p.baseCent).put("a", p.atualCent).put("d", p.desconto).put("fim", p.fimMs ?: JSONObject.NULL).put("mc", p.melhorConhecido ?: JSONObject.NULL)

    fun preco(o: JSONObject) = Preco(Loja.valueOf(o.getString("loja")), o.getString("id"), o.getString("t"), texto(o, "capa"),
        o.getInt("b"), o.getInt("a"), o.getInt("d"), if (o.isNull("fim")) null else o.getLong("fim"), if (o.isNull("mc")) null else o.getInt("mc"))

    private fun texto(o: JSONObject, k: String) = if (o.isNull(k)) null else o.getString(k)

    fun precos(l: List<Preco>) = JSONArray().apply { l.forEach { put(preco(it)) } }
    fun precos(a: JSONArray) = Leitura.objetos(a).map { preco(it) }

    fun item(i: ItemRadar) = JSONObject().put("c", i.jogo.chave).put("t", i.jogo.titulo).put("capa", i.jogo.capa ?: JSONObject.NULL)
        .put("p", precos(i.jogo.precos.values.toList()))
        .put("h", JSONArray().apply { i.historico.forEach { put(JSONArray().put(it.dia).put(it.cent)) } })
        .put("av", JSONArray(i.avisados.toList()))

    fun item(o: JSONObject): ItemRadar {
        val h = o.getJSONArray("h"); val av = o.getJSONArray("av")
        return ItemRadar(
            Jogo(o.getString("c"), o.getString("t"), texto(o, "capa"), precos(o.getJSONArray("p")).associateBy { it.loja }),
            (0 until h.length()).map { h.getJSONArray(it).let { p -> Ponto(p.getLong(0), p.getInt(1)) } },
            (0 until av.length()).map { av.getString(it) }.toSet(),
        )
    }
}

class Dados(ctx: Context) {
    private val pasta = ctx.filesDir

    private fun ler(nome: String): String? = File(pasta, nome).takeIf { it.exists() }?.readText()

    private fun gravar(nome: String, conteudo: String) {
        val tmp = File(pasta, "$nome.tmp")
        tmp.writeText(conteudo)
        tmp.renameTo(File(pasta, nome))
    }

    fun radar(): List<ItemRadar> = synchronized(TRAVA) {
        val t = ler("radar.json") ?: return emptyList()
        runCatching { Leitura.objetos(JSONArray(t)).map { Json.item(it) } }.getOrDefault(emptyList())
    }

    fun salvarRadar(l: List<ItemRadar>) = synchronized(TRAVA) {
        gravar("radar.json", JSONArray().apply { l.forEach { put(Json.item(it)) } }.toString())
    }

    fun alterarRadar(f: (List<ItemRadar>) -> List<ItemRadar>) = synchronized(TRAVA) { salvarRadar(f(radar())) }

    fun ofertas(): List<Preco> = lista("ofertas.json")
    fun salvarOfertas(l: List<Preco>) = gravarLista("ofertas.json", l)
    val ofertasEm: Long get() = File(pasta, "ofertas.json").lastModified()

    fun indicePs(): List<Preco> = lista("indice_ps.json")
    fun salvarIndicePs(l: List<Preco>) = gravarLista("indice_ps.json", l)
    val indiceEm: Long get() = File(pasta, "indice_ps.json").lastModified()

    private fun lista(nome: String) = synchronized(TRAVA) {
        val t = ler(nome)
        if (t == null) emptyList() else runCatching { Json.precos(JSONArray(t)) }.getOrDefault(emptyList())
    }

    private fun gravarLista(nome: String, l: List<Preco>) = synchronized(TRAVA) { gravar(nome, Json.precos(l).toString()) }

    fun biblioteca(): List<Possuido> = synchronized(TRAVA) {
        val t = ler("biblioteca.json") ?: return emptyList()
        runCatching {
            Leitura.objetos(JSONArray(t)).map { Possuido(it.getString("t"), it.optString("capa").ifEmpty { null }, Loja.valueOf(it.getString("loja"))) }
        }.getOrDefault(emptyList())
    }

    /** Troca os jogos de uma loja. As demais ficam como estão. */
    fun trocarBiblioteca(loja: Loja, novos: List<Possuido>) = synchronized(TRAVA) {
        val tudo = biblioteca().filter { it.loja != loja } + novos
        gravar("biblioteca.json", JSONArray().apply {
            tudo.forEach { put(JSONObject().put("t", it.titulo).put("capa", it.capa ?: "").put("loja", it.loja.name)) }
        }.toString())
    }

    private companion object { val TRAVA = Any() }
}

/** Procura o mesmo jogo nas outras lojas, pelo título. */
class Busca(private val dados: Dados, private val contas: Contas) {
    private fun indice(): List<Preco> {
        val semana = 7L * 24 * 60 * 60 * 1000
        if (System.currentTimeMillis() - dados.indiceEm > semana || dados.indicePs().isEmpty()) {
            runCatching { Fontes.catalogoPs(20) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { dados.salvarIndicePs(it) }
        }
        return dados.indicePs()
    }

    private fun buscarPs(termo: String): List<Preco> {
        val conta = runCatching { contas.buscarPs(termo) }.getOrDefault(emptyList())
        val c = chaveDoTitulo(termo).split(' ').filter { it.isNotEmpty() }
        val locais = indice().filter { p -> val k = chaveDoTitulo(p.titulo); c.all { it in k } }
        return (conta + locais).distinctBy { it.id }
    }

    fun buscar(termo: String): List<Jogo> {
        val fontes: List<() -> List<Preco>> = listOf({ buscarPs(termo) }, { Fontes.buscarXbox(termo) }, { Fontes.buscarSteam(termo) })
        val res = java.util.Collections.synchronizedList(mutableListOf<Preco>())
        fontes.map { f -> Thread { runCatching { res += f() } }.apply { start() } }.forEach { it.join() }
        val c = chaveDoTitulo(termo)
        return agrupar(res.toList()).sortedByDescending { if (it.chave == c) 2 else if (it.chave.startsWith(c)) 1 else 0 }
    }

    /** Completa as lojas que faltam e devolve o jogo com os preços de hoje. */
    fun completar(jogo: Jogo): Jogo {
        val achados = runCatching { buscar(jogo.titulo).firstOrNull { it.chave == jogo.chave } }.getOrNull()
        val base = jogo.precos + (achados?.precos ?: emptyMap()).filterKeys { it !in jogo.precos }
        return jogo.copy(capa = jogo.capa ?: achados?.capa, precos = base)
    }
}
