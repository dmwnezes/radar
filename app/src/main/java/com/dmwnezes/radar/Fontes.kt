package com.dmwnezes.radar

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object Http {
    private const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126 Mobile Safari/537.36"

    fun abrir(url: String, cabecalhos: Map<String, String> = emptyMap()): HttpURLConnection {
        require(url.startsWith("https://")) { "Só HTTPS" }
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000; readTimeout = 30000
            setRequestProperty("User-Agent", UA)
            cabecalhos.forEach { (k, v) -> setRequestProperty(k, v) }
        }
    }

    fun texto(con: HttpURLConnection): String {
        try {
            val cod = con.responseCode
            if (cod !in 200..299) throw java.io.IOException("HTTP $cod")
            return con.inputStream.bufferedReader().use { it.readText() }
        } finally {
            con.disconnect()
        }
    }

    fun get(url: String, cabecalhos: Map<String, String> = emptyMap()) = texto(abrir(url, cabecalhos))

    fun post(url: String, corpo: String, tipo: String, cabecalhos: Map<String, String> = emptyMap()): String {
        val con = abrir(url, cabecalhos + ("Content-Type" to tipo))
        con.requestMethod = "POST"
        con.doOutput = true
        con.outputStream.use { it.write(corpo.toByteArray()) }
        return texto(con)
    }

    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}

/** Leitura das respostas das lojas: funções puras, testadas com respostas reais. */
object Leitura {
    fun gradePs(json: String): List<Preco> {
        val g = JSONObject(json).getJSONObject("data").getJSONObject("categoryGridRetrieve")
        val ps = g.optJSONArray("products") ?: return emptyList()
        return objetos(ps).mapNotNull { produtoPs(it) }
    }

    private fun produtoPs(p: JSONObject): Preco? {
        val pr = p.optJSONObject("price") ?: return null
        if (pr.optBoolean("isFree") || pr.optBoolean("isTiedToSubscription")) return null
        val base = brlParaCent(pr.optString("basePrice")) ?: return null
        val atual = brlParaCent(pr.optString("discountedPrice")) ?: base
        return Preco(Loja.PS, p.getString("id"), p.getString("name"), capaPs(p), base, atual, descontoDe(pr.optString("discountText")))
    }

    private fun capaPs(p: JSONObject): String? {
        val midias = objetos(p.optJSONArray("media") ?: return null).filter { it.optString("type") == "IMAGE" }
        return listOf("MASTER", "GAMEHUB_COVER_ART", "PORTRAIT_BANNER").firstNotNullOfOrNull { r ->
            midias.firstOrNull { it.optString("role") == r }?.optString("url")
        }
    }

    private fun descontoDe(t: String?) = t?.let { Regex("""(\d+)\s*%""").find(it)?.groupValues?.get(1)?.toIntOrNull() } ?: 0

    fun produtoPs(json: String, id: String, titulo: String, capa: String?): Preco? {
        val r = JSONObject(json).getJSONObject("data").optJSONObject("productRetrieve") ?: return null
        val pr = objetos(r.optJSONArray("webctas") ?: return null).mapNotNull { it.optJSONObject("price") }
            .firstOrNull { !it.optBoolean("isTiedToSubscription") && it.optInt("basePriceValue") > 0 } ?: return null
        if (pr.optString("currencyCode") != "BRL") return null
        val fim = pr.optString("endTime").toLongOrNull()
        return Preco(Loja.PS, id, r.optString("name", titulo), capa, pr.getInt("basePriceValue"), pr.getInt("discountedValue"),
            descontoDe(pr.optString("discountText")), fim)
    }

    fun ofertasSteam(json: String): List<Preco> {
        val itens = JSONObject(json).getJSONObject("specials").getJSONArray("items")
        return objetos(itens).filter { it.optString("currency") == "BRL" && it.optInt("type") == 0 && it.optBoolean("discounted") }.map {
            val fim = it.optLong("discount_expiration").takeIf { s -> s > 0 }?.times(1000)
            Preco(Loja.STEAM, it.getInt("id").toString(), it.getString("name"), it.optString("large_capsule_image").ifEmpty { null },
                it.getInt("original_price"), it.getInt("final_price"), it.getInt("discount_percent"), fim)
        }
    }

    fun precoSteam(json: String, id: String, titulo: String, capa: String?): Preco? {
        val o = JSONObject(json).getJSONObject(id)
        val p = o.optJSONObject("data")?.optJSONObject("price_overview") ?: return null
        if (p.optString("currency") != "BRL") return null
        return Preco(Loja.STEAM, id, titulo, capa, p.getInt("initial"), p.getInt("final"), p.getInt("discount_percent"))
    }

    fun buscaSteam(json: String): List<Preco> = objetos(JSONObject(json).optJSONArray("items") ?: JSONArray()).mapNotNull {
        val pr = it.optJSONObject("price") ?: return@mapNotNull null
        if (it.optString("type") != "app" || pr.optString("currency") != "BRL") return@mapNotNull null
        val ini = pr.getInt("initial"); val fin = pr.getInt("final")
        val d = if (ini > 0) ((ini - fin) * 100 + ini / 2) / ini else 0
        Preco(Loja.STEAM, it.getInt("id").toString(), it.getString("name"), it.optString("tiny_image").ifEmpty { null }, ini, fin, d)
    }

    /** A lista de ofertas do Xbox vem no estado inicial da página. */
    fun ofertasXbox(html: String): List<Preco> {
        val marca = "window.__PRELOADED_STATE__"
        val i = html.indexOf(marca).takeIf { it >= 0 } ?: return emptyList()
        val ini = html.indexOf('{', i)
        val est = JSONTokener(html.substring(ini)).nextValue() as JSONObject
        val soma = est.getJSONObject("core2").getJSONObject("products").getJSONObject("productSummaries")
        return soma.keys().asSequence().toList().mapNotNull { k ->
            val s = soma.getJSONObject(k)
            val pr = s.optJSONObject("specificPrices")?.optJSONArray("purchaseable")?.optJSONObject(0) ?: return@mapNotNull null
            if (pr.optString("currency") != "BRL") return@mapNotNull null
            val atual = centDe(pr.getDouble("listPrice")); val base = centDe(pr.getDouble("msrp"))
            val img = s.optJSONObject("images")
            val capa = (img?.optJSONObject("boxArt") ?: img?.optJSONObject("poster"))?.optString("url")
            Preco(Loja.XBOX, s.getString("productId"), s.getString("title"), capa, base, atual,
                Math.round(pr.optDouble("discountPercentage", 0.0)).toInt(), dataXboxAmericana(pr.optString("endDateUtc")))
        }
    }

    fun produtosXbox(json: String): List<Preco> = objetos(JSONObject(json).optJSONArray("Products") ?: JSONArray()).mapNotNull { p ->
        val loc = p.getJSONArray("LocalizedProperties").getJSONObject(0)
        val sku = p.getJSONArray("DisplaySkuAvailabilities").getJSONObject(0)
        val disp = objetos(sku.getJSONArray("Availabilities")).firstOrNull { a ->
            a.optJSONArray("Actions")?.let { acts -> (0 until acts.length()).any { acts.getString(it) == "Purchase" } } == true
        } ?: return@mapNotNull null
        val pr = disp.getJSONObject("OrderManagementData").getJSONObject("Price")
        if (pr.optString("CurrencyCode") != "BRL") return@mapNotNull null
        val atual = centDe(pr.getDouble("ListPrice")); val base = centDe(pr.getDouble("MSRP"))
        if (base <= 0) return@mapNotNull null
        val imgs = objetos(loc.optJSONArray("Images") ?: JSONArray())
        val capa = (imgs.firstOrNull { it.optString("ImagePurpose") == "BoxArt" } ?: imgs.firstOrNull())?.optString("Uri")?.let { if (it.startsWith("//")) "https:$it" else it }
        val melhor = sku.optJSONArray("HistoricalBestAvailabilities")?.optJSONObject(0)?.optJSONObject("OrderManagementData")?.optJSONObject("Price")
            ?.optDouble("ListPrice")?.takeIf { it > 0 }?.let { centDe(it) }
        val fim = disp.optJSONObject("Conditions")?.optString("EndDate")?.let { dataIso(it) }?.takeIf { atual < base }
        val d = if (atual < base) ((base - atual) * 100 + base / 2) / base else 0
        Preco(Loja.XBOX, p.getString("ProductId"), loc.getString("ProductTitle"), capa, base, atual, d, fim, melhor)
    }

    fun buscaXbox(json: String): List<String> = objetos(JSONObject(json).optJSONArray("Results") ?: JSONArray())
        .flatMap { objetos(it.optJSONArray("Products") ?: JSONArray()) }
        .filter { it.optString("Type") == "Game" }.map { it.getString("ProductId") }

    private fun centDe(v: Double) = Math.round(v * 100).toInt()

    private fun dataXboxAmericana(s: String?): Long? = runCatching {
        LocalDateTime.parse(s, DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss")).toInstant(ZoneOffset.UTC).toEpochMilli()
    }.getOrNull()

    private fun dataIso(s: String): Long? = runCatching { Instant.parse(s).toEpochMilli() }.getOrNull()

    fun objetos(a: JSONArray): List<JSONObject> = (0 until a.length()).mapNotNull { a.optJSONObject(it) }
}

object Fontes {
    private const val GRAPHQL = "https://web.np.playstation.com/api/graphql/v1/op"
    private const val HASH_GRADE = "4ce7d410a4db2c8b635a48c1dcec375906ff63b19dadd87e073f8fd0c0481d35"
    private const val HASH_PRODUTO = "8532da7eda369efdad054ca8f885394a2d0c22d03c5259a422ae2bb3b98c5c99"
    const val HASH_BUSCA = "a2fbc15433b37ca7bfcd7112f741735e13268f5e9ebd5ffce51b85acc126f41d"
    private const val OFERTAS_PS = "3f772501-f6f8-49b7-abac-874a88ca4897"
    private const val CATALOGO_PS = "4cbf39e2-5749-4970-ba81-93a489e4570c"
    private const val CATALOGO_XBOX = "https://displaycatalog.mp.microsoft.com/v7.0"

    fun urlGraphql(op: String, variaveis: String, hash: String) = GRAPHQL +
        "?operationName=$op&variables=${Http.enc(variaveis)}" +
        "&extensions=${Http.enc("""{"persistedQuery":{"version":1,"sha256Hash":"$hash"}}""")}"

    private val LOCAL_PS = mapOf("x-psn-store-locale-override" to "pt-BR", "Content-Type" to "application/json")

    fun gradePs(categoria: String, tamanho: Int, deslocamento: Int): List<Preco> {
        val v = """{"id":"$categoria","pageArgs":{"size":$tamanho,"offset":$deslocamento},"sortBy":{"name":"sales30","isAscending":false},""" +
            """"filterBy":["targetPlatforms:PS5","storeDisplayClassification:FULL_GAME"],"facetOptions":[]}"""
        return Leitura.gradePs(Http.get(urlGraphql("categoryGridRetrieve", v, HASH_GRADE), LOCAL_PS))
    }

    fun ofertasPs() = gradePs(OFERTAS_PS, 60, 0)

    fun catalogoPs(paginas: Int) = (0 until paginas).flatMap { gradePs(CATALOGO_PS, 100, it * 100) }

    fun ofertasSteam() = Leitura.ofertasSteam(Http.get("https://store.steampowered.com/api/featuredcategories?cc=br&l=portuguese"))

    fun ofertasXbox() = Leitura.ofertasXbox(Http.get("https://www.xbox.com/pt-BR/games/browse/DynamicChannel.GameDeals"))

    fun produtosXbox(ids: List<String>): List<Preco> {
        if (ids.isEmpty()) return emptyList()
        return Leitura.produtosXbox(Http.get("$CATALOGO_XBOX/products?market=BR&languages=pt-BR&bigIds=${ids.joinToString(",")}"))
    }

    fun buscarSteam(t: String) = Leitura.buscaSteam(Http.get("https://store.steampowered.com/api/storesearch/?term=${Http.enc(t)}&l=portuguese&cc=BR"))

    fun buscarXbox(t: String): List<Preco> = produtosXbox(Leitura.buscaXbox(Http.get(
        "$CATALOGO_XBOX/productFamilies/autosuggest?market=BR&languages=pt-BR&query=${Http.enc(t)}&productFamilyNames=Games&platformdependencyname=Windows.Xbox"
    )))

    /** Preço de hoje na loja. Devolve null se o jogo saiu de venda. */
    fun precoAtual(p: Preco): Preco? = when (p.loja) {
        Loja.PS -> Leitura.produtoPs(
            Http.get(urlGraphql("productRetrieveForCtasWithPrice", """{"productId":"${p.id}"}""", HASH_PRODUTO), LOCAL_PS), p.id, p.titulo, p.capa,
        )
        Loja.STEAM -> Leitura.precoSteam(
            Http.get("https://store.steampowered.com/api/appdetails?appids=${p.id}&cc=br&filters=price_overview"), p.id, p.titulo, p.capa,
        )
        Loja.XBOX -> produtosXbox(listOf(p.id)).firstOrNull()
    }
}
