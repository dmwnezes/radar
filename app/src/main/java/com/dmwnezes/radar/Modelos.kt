package com.dmwnezes.radar

import java.text.Normalizer

enum class Loja(val nome: String) { PS("PS Store"), XBOX("Xbox"), STEAM("Steam") }

/** Preço de um jogo em uma loja, sempre em centavos de real. */
data class Preco(
    val loja: Loja,
    val id: String,
    val titulo: String,
    val capa: String?,
    val baseCent: Int,
    val atualCent: Int,
    val desconto: Int,
    val fimMs: Long? = null,
    val melhorConhecido: Int? = null,
) {
    val url: String get() = when (loja) {
        Loja.PS -> "https://store.playstation.com/pt-br/product/$id"
        Loja.XBOX -> "https://www.xbox.com/pt-BR/games/store/x/$id"
        Loja.STEAM -> "https://store.steampowered.com/app/$id"
    }
}

/** Um jogo, juntando as lojas onde ele foi encontrado. */
data class Jogo(val chave: String, val titulo: String, val capa: String?, val precos: Map<Loja, Preco>) {
    val melhor: Preco? get() = precos.values.minByOrNull { it.atualCent }
    val desconto: Int get() = melhor?.desconto ?: 0
}

data class Ponto(val dia: Long, val cent: Int)

data class ItemRadar(
    val jogo: Jogo,
    val historico: List<Ponto> = emptyList(),
    val avisados: Set<String> = emptySet(),
) {
    val menorVisto: Int? get() = (historico.map { it.cent } + jogo.precos.values.mapNotNull { it.melhorConhecido }).minOrNull()
    val alvoCent: Int? get() = alvoSugerido(jogo.melhor?.baseCent, menorVisto)
    val atingiu: Boolean get() { val a = alvoCent; val p = jogo.melhor?.atualCent; return a != null && p != null && p <= a }
    val progresso: Float get() {
        val a = alvoCent ?: return 0f
        val p = jogo.melhor?.atualCent ?: return 0f
        return if (p <= a) 1f else a.toFloat() / p
    }
}

private val RUIDO = Regex("""\b(ps4|ps5|playstation ?[45]?|xbox( one| series x\|?s?)?|windows|pc)\b( e | & | and )?""")
private val EDICAO = Regex("""\b(edicao )?(padrao|standard|definitiva|definitive)( edition| edicao)?\b""")

/** Chave para reconhecer o mesmo jogo em lojas diferentes. */
fun chaveDoTitulo(titulo: String): String {
    val sem = Normalizer.normalize(titulo.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
    val limpo = sem.replace(Regex("[™®©]"), "").replace(RUIDO, " ").replace(EDICAO, " ")
    return limpo.replace(Regex("[^a-z0-9]+"), " ").trim()
}

/** "R$1.199,50" vira 119950. Devolve null para textos como "Gratuito". */
fun brlParaCent(texto: String?): Int? {
    val m = Regex("""(\d[\d.]*),(\d{2})""").find(texto ?: return null) ?: return null
    return (m.groupValues[1].replace(".", "") + m.groupValues[2]).toIntOrNull()
}

fun formatarBrl(cent: Int): String {
    val reais = "%,d".format(java.util.Locale.ROOT, cent / 100).replace(',', '.')
    return "R$ $reais,%02d".format(cent % 100)
}

/** Alvo: o menor preço já visto. Sem promoção no histórico ainda, começa em 30% abaixo do preço cheio. */
fun alvoSugerido(baseCent: Int?, menorVisto: Int?): Int? {
    if (baseCent == null) return menorVisto
    return if (menorVisto != null && menorVisto < baseCent) menorVisto else baseCent * 70 / 100
}

fun registrarPreco(item: ItemRadar, cent: Int, dia: Long): ItemRadar {
    val ult = item.historico.lastOrNull()
    val h = when {
        ult == null || ult.dia < dia -> item.historico + Ponto(dia, cent)
        else -> item.historico.dropLast(1) + Ponto(dia, minOf(ult.cent, cent))
    }
    return item.copy(historico = h.takeLast(365))
}

private const val DIA_E_MEIO_MS = 36L * 60 * 60 * 1000

/** Compara a verificação anterior com a nova. Devolve o item atualizado e as frases do aviso. */
fun aplicarVerificacao(antes: ItemRadar, jogoNovo: Jogo, agoraMs: Long): Pair<ItemRadar, List<String>> {
    val dia = agoraMs / (24L * 60 * 60 * 1000)
    val p = jogoNovo.melhor ?: return antes to emptyList()
    val frases = mutableListOf<String>()

    jogoNovo.precos.values.filter { it.desconto > 0 && (antes.jogo.precos[it.loja]?.desconto ?: 0) == 0 }
        .minByOrNull { it.atualCent }
        ?.let { frases += "Entrou em promoção: ${formatarBrl(it.atualCent)} na ${it.loja.nome} (-${it.desconto}%)" }

    val alvoAntes = antes.alvoCent
    val precoAntes = antes.jogo.melhor?.atualCent
    if (alvoAntes != null && precoAntes != null && precoAntes > alvoAntes && p.atualCent <= alvoAntes) {
        frases += "Atingiu o preço-alvo"
    }
    val menorAntes = antes.menorVisto
    if (menorAntes != null && p.atualCent < menorAntes) frases += "Menor preço já visto"

    val avisados = antes.avisados.toMutableSet()
    jogoNovo.precos.values.filter { it.desconto > 0 && it.fimMs != null && it.fimMs - agoraMs in 0..DIA_E_MEIO_MS }.forEach {
        if (avisados.add("fim:${it.loja}:${it.fimMs}")) frases += "Promoção na ${it.loja.nome} termina em breve"
    }

    val novo = registrarPreco(antes.copy(jogo = jogoNovo, avisados = avisados), p.atualCent, dia)
    return novo to frases
}

/** Junta preços de lojas diferentes pelo título. */
fun agrupar(precos: List<Preco>): List<Jogo> =
    precos.groupBy { chaveDoTitulo(it.titulo) }.filterKeys { it.isNotEmpty() }.map { (chave, lista) ->
        val porLoja = lista.groupBy { it.loja }.mapValues { (_, v) -> v.minBy { it.atualCent } }
        val m = porLoja.values.minBy { it.atualCent }
        Jogo(chave, m.titulo, porLoja[Loja.PS]?.capa ?: m.capa, porLoja)
    }

fun rankear(jogos: List<Jogo>): List<Jogo> = jogos.filter { it.desconto > 0 }.sortedByDescending { it.desconto }
