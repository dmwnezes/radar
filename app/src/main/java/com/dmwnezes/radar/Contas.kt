package com.dmwnezes.radar

import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

data class Possuido(val titulo: String, val capa: String?, val loja: Loja)

/** Domínios que podem receber credenciais de cada conta. Qualquer outro é recusado. */
object Dominios {
    private val PERMITIDOS = mapOf(
        Loja.PS to setOf("ca.account.sony.com", "web.np.playstation.com"),
        Loja.STEAM to setOf("api.steampowered.com"),
        Loja.XBOX to setOf("login.live.com", "user.auth.xboxlive.com", "xsts.auth.xboxlive.com", "titlehub.xboxlive.com"),
    )

    fun permitido(loja: Loja, url: String): Boolean {
        val u = runCatching { java.net.URI(url) }.getOrNull() ?: return false
        return u.scheme == "https" && u.host in PERMITIDOS.getValue(loja)
    }

    fun exigir(loja: Loja, url: String) = require(permitido(loja, url)) { "Destino não permitido" }
}

/** Aceita o token puro ou o JSON inteiro que a página da Sony mostra. */
fun lerNpsso(colado: String): String? {
    val t = colado.trim()
    val v = Regex(""""npsso"\s*:\s*"([A-Za-z0-9]+)"""").find(t)?.groupValues?.get(1) ?: t
    return v.takeIf { Regex("[A-Za-z0-9]{64}").matches(it) }
}

class ContaInvalida(msg: String) : Exception(msg)

class Contas(private val cofre: Cofre) {
    val psConectada get() = cofre["ps_npsso"] != null
    val steamConectada get() = cofre["steam_chave"] != null
    val xboxConectada get() = cofre["xbox_refresh"] != null

    // ---- PlayStation (API interna da Sony, sem garantia oficial) ----
    private val OAUTH = "https://ca.account.sony.com/api/authz/v3/oauth"
    private val BASICO = "Basic MDk1MTUxNTktNzIzNy00MzcwLTliNDAtMzgwNmU2N2MwODkxOnVjUGprYTV0bnRCMktxc1A="
    private val REDIRECT = "com.scee.psxandroid.scecompcall://redirect"

    fun conectarPs(colado: String) {
        val npsso = lerNpsso(colado) ?: throw ContaInvalida("Token inválido: confira se copiou os 64 caracteres.")
        tokenPeloNpsso(npsso)
        cofre.salvar("ps_npsso" to npsso)
    }

    private fun tokenPeloNpsso(npsso: String): String {
        val url = "$OAUTH/authorize?access_type=offline&client_id=09515159-7237-4370-9b40-3806e67c0891" +
            "&redirect_uri=${Http.enc(REDIRECT)}&response_type=code&scope=${Http.enc("psn:mobile.v2.core psn:clientapp")}"
        Dominios.exigir(Loja.PS, url)
        val con = Http.abrir(url, mapOf("Cookie" to "npsso=$npsso")).apply { instanceFollowRedirects = false }
        val destino = try { con.responseCode; con.getHeaderField("Location") } finally { con.disconnect() }
        val codigo = destino?.let { Uri.parse(it).getQueryParameter("code") }
            ?: throw ContaInvalida("A Sony recusou o token. Gere outro e tente de novo.")
        return lerTokenPs(Http.post("$OAUTH/token",
            "code=${Http.enc(codigo)}&redirect_uri=${Http.enc(REDIRECT)}&grant_type=authorization_code&token_format=jwt",
            "application/x-www-form-urlencoded", mapOf("Authorization" to BASICO)))
    }

    private fun lerTokenPs(json: String) = JSONObject(json).getString("access_token")

    private fun acessoPs(): String = tokenPeloNpsso(cofre["ps_npsso"] ?: throw ContaInvalida("PlayStation não conectada"))

    /** Só compras: jogos da PS Plus (membership diferente de NONE) não contam. */
    fun bibliotecaPs(): List<Possuido> {
        val token = acessoPs()
        val saida = mutableListOf<Possuido>()
        var inicio = 0
        while (inicio < 2000) {
            val v = """{"isActive":true,"platform":["ps4","ps5"],"size":100,"start":$inicio,"sortBy":"ACTIVE_DATE","sortDirection":"desc"}"""
            val url = Fontes.urlGraphql("getPurchasedGameList", v, "827a423f6a8ddca4107ac01395af2ec0eafd8396fc7fa204aaf9b7ed2eefa168")
            Dominios.exigir(Loja.PS, url)
            val jogos = JSONObject(Http.get(url, mapOf("Authorization" to "Bearer $token", "Content-Type" to "application/json")))
                .getJSONObject("data").getJSONObject("purchasedTitlesRetrieve").optJSONArray("games") ?: JSONArray()
            val lista = Leitura.objetos(jogos)
            saida += lerCompradosPs(lista)
            if (lista.size < 100) break
            inicio += 100
        }
        return saida
    }

    /** Busca completa da Sony, disponível só com a conta conectada. */
    fun buscarPs(termo: String): List<Preco> {
        if (!psConectada) return emptyList()
        val v = """{"searchTerm":${JSONObject.quote(termo)},"searchContext":"MobileUniversalSearchGame","displayTitleLocale":"pt-BR"}"""
        val url = Fontes.urlGraphql("metGetContextSearchResults", v, Fontes.HASH_BUSCA)
        Dominios.exigir(Loja.PS, url)
        val r = JSONObject(Http.get(url, mapOf("Authorization" to "Bearer ${acessoPs()}", "x-psn-store-locale-override" to "pt-BR", "Content-Type" to "application/json")))
        return lerBuscaPs(r)
    }

    // ---- Steam ----
    fun conectarSteam(id: String, chave: String) {
        val sid = id.trim(); val k = chave.trim()
        if (!Regex("\\d{17}").matches(sid) || !Regex("[A-Fa-f0-9]{32}").matches(k)) {
            throw ContaInvalida("Confira: o SteamID tem 17 números e a chave da API tem 32 caracteres.")
        }
        pedirSteam(sid, k)
        cofre.salvar("steam_id" to sid, "steam_chave" to k)
    }

    private fun pedirSteam(id: String, chave: String): String {
        val url = "https://api.steampowered.com/IPlayerService/GetOwnedGames/v1/?key=$chave&steamid=$id&include_appinfo=1&format=json"
        Dominios.exigir(Loja.STEAM, url)
        return Http.get(url)
    }

    fun bibliotecaSteam(): List<Possuido> {
        val json = pedirSteam(cofre["steam_id"] ?: return emptyList(), cofre["steam_chave"] ?: return emptyList())
        return Leitura.objetos(JSONObject(json).getJSONObject("response").optJSONArray("games") ?: JSONArray()).map {
            val id = it.getInt("appid")
            Possuido(it.getString("name"), "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/$id/library_600x900.jpg", Loja.STEAM)
        }
    }

    // ---- Xbox (login oficial da Microsoft, com um app registrado por você no Azure) ----
    fun iniciarXbox(clienteId: String): Uri {
        val id = clienteId.trim()
        if (!Regex("[0-9a-fA-F-]{36}").matches(id)) throw ContaInvalida("O Client ID do Azure tem 36 caracteres.")
        val verificador = Base64.encodeToString(ByteArray(48).also { SecureRandom().nextBytes(it) }, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val estado = Base64.encodeToString(ByteArray(16).also { SecureRandom().nextBytes(it) }, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val desafio = Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(verificador.toByteArray()), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        cofre.salvar("xbox_cliente" to id, "xbox_verificador" to verificador, "xbox_estado" to estado)
        return Uri.parse("https://login.live.com/oauth20_authorize.srf").buildUpon()
            .appendQueryParameter("client_id", id).appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", RETORNO_XBOX).appendQueryParameter("scope", ESCOPO_XBOX)
            .appendQueryParameter("code_challenge", desafio).appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("state", estado).build()
    }

    fun concluirXbox(retorno: Uri) {
        if (retorno.getQueryParameter("state") != cofre["xbox_estado"]) throw ContaInvalida("Login do Xbox não confere. Tente de novo.")
        val codigo = retorno.getQueryParameter("code") ?: throw ContaInvalida("A Microsoft não autorizou o login.")
        val corpo = "client_id=${cofre["xbox_cliente"]}&grant_type=authorization_code&code=${Http.enc(codigo)}" +
            "&redirect_uri=${Http.enc(RETORNO_XBOX)}&scope=${Http.enc(ESCOPO_XBOX)}&code_verifier=${cofre["xbox_verificador"]}"
        val r = JSONObject(Http.post("https://login.live.com/oauth20_token.srf", corpo, "application/x-www-form-urlencoded"))
        cofre.salvar("xbox_refresh" to r.getString("refresh_token"))
        cofre.apagar("xbox_verificador", "xbox_estado")
    }

    /** O Xbox informa os jogos já jogados, não a lista de compras. */
    fun bibliotecaXbox(): List<Possuido> {
        val corpo = "client_id=${cofre["xbox_cliente"]}&grant_type=refresh_token&refresh_token=${Http.enc(cofre["xbox_refresh"] ?: return emptyList())}&scope=${Http.enc(ESCOPO_XBOX)}"
        val o = JSONObject(Http.post("https://login.live.com/oauth20_token.srf", corpo, "application/x-www-form-urlencoded"))
        o.optString("refresh_token").takeIf { it.isNotEmpty() }?.let { cofre.salvar("xbox_refresh" to it) }
        val xau = JSONObject(Http.post("https://user.auth.xboxlive.com/user/authenticate",
            JSONObject().put("RelyingParty", "http://auth.xboxlive.com").put("TokenType", "JWT").put("Properties",
                JSONObject().put("AuthMethod", "RPS").put("SiteName", "user.auth.xboxlive.com").put("RpsTicket", "d=" + o.getString("access_token"))).toString(),
            "application/json", mapOf("x-xbl-contract-version" to "1", "Accept" to "application/json"))).getString("Token")
        val xsts = JSONObject(Http.post("https://xsts.auth.xboxlive.com/xsts/authorize",
            JSONObject().put("RelyingParty", "http://xboxlive.com").put("TokenType", "JWT").put("Properties",
                JSONObject().put("UserTokens", JSONArray().put(xau)).put("SandboxId", "RETAIL")).toString(),
            "application/json", mapOf("x-xbl-contract-version" to "1", "Accept" to "application/json")))
        val xui = xsts.getJSONObject("DisplayClaims").getJSONArray("xui").getJSONObject(0)
        val url = "https://titlehub.xboxlive.com/users/xuid(${xui.getString("xid")})/titles/titlehistory/decoration/detail?maxItems=1000"
        val r = Http.get(url, mapOf("Authorization" to "XBL3.0 x=${xui.getString("uhs")};${xsts.getString("Token")}",
            "x-xbl-contract-version" to "2", "Accept-Language" to "pt-BR", "Accept" to "application/json"))
        return Leitura.objetos(JSONObject(r).optJSONArray("titles") ?: JSONArray())
            .filter { t -> t.optJSONArray("devices")?.let { d -> (0 until d.length()).any { d.getString(it).startsWith("Xbox") } } != false }
            .map { Possuido(it.getString("name"), it.optString("displayImage").ifEmpty { null }, Loja.XBOX) }
    }

    fun desconectar(loja: Loja) = when (loja) {
        Loja.PS -> cofre.apagar("ps_npsso")
        Loja.STEAM -> cofre.apagar("steam_id", "steam_chave")
        Loja.XBOX -> cofre.apagar("xbox_refresh", "xbox_cliente", "xbox_verificador", "xbox_estado")
    }

    companion object {
        const val RETORNO_XBOX = "com.dmwnezes.radar://xbox"
        private const val ESCOPO_XBOX = "Xboxlive.signin Xboxlive.offline_access"
    }
}

fun lerCompradosPs(jogos: List<JSONObject>): List<Possuido> = jogos
    .filter { it.optString("membership") == "NONE" }
    .map { Possuido(it.getString("name"), it.optJSONObject("image")?.optString("url"), Loja.PS) }

fun lerBuscaPs(r: JSONObject): List<Preco> {
    val blocos = r.optJSONObject("data")?.optJSONObject("universalContextSearch")?.optJSONArray("results") ?: return emptyList()
    return Leitura.objetos(blocos).flatMap { Leitura.objetos(it.optJSONArray("searchResults") ?: JSONArray()) }.mapNotNull { sr ->
        val res = sr.optJSONObject("result") ?: return@mapNotNull null
        val prod = if (res.optString("__typename") == "Concept") res.optJSONObject("defaultProduct") else res
        prod ?: return@mapNotNull null
        val pr = prod.optJSONObject("price") ?: res.optJSONObject("price") ?: return@mapNotNull null
        val base = pr.optInt("basePriceValue"); if (base <= 0 || pr.optBoolean("isTiedToSubscription")) return@mapNotNull null
        val atual = pr.optInt("discountedValue", base)
        val capa = Leitura.objetos(prod.optJSONArray("media") ?: JSONArray()).firstOrNull { it.optString("role") == "MASTER" }?.optString("url")
        val d = Regex("""(\d+)\s*%""").find(pr.optString("discountText"))?.groupValues?.get(1)?.toIntOrNull() ?: 0
        Preco(Loja.PS, prod.getString("id"), prod.getString("name"), capa, base, atual, d, pr.optString("endTime").toLongOrNull())
    }
}
