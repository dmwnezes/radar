package com.dmwnezes.radar

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RadarTest {
    private fun res(n: String) = javaClass.classLoader!!.getResource(n)!!.readText()

    private fun preco(cent: Int, base: Int = 10000, d: Int = 0, loja: Loja = Loja.PS, fim: Long? = null) =
        Preco(loja, "id", "Jogo", null, base, cent, d, fim)

    private fun item(p: Preco, h: List<Ponto> = emptyList()) = ItemRadar(Jogo("jogo", "Jogo", null, mapOf(p.loja to p)), h)

    @Test fun chaveJuntaEdicoesEPlataformas() {
        assertEquals("resident evil 4", chaveDoTitulo("Resident Evil 4 PS4 e PS5"))
        assertEquals("red dead redemption 2", chaveDoTitulo("Red Dead Redemption 2: Edição Definitiva"))
        assertEquals("god of war ragnarok", chaveDoTitulo("God of War Ragnarök™"))
        assertEquals("hollow knight silksong", chaveDoTitulo("Hollow Knight: Silksong"))
        assertNotEquals(chaveDoTitulo("Silent Hill 2"), chaveDoTitulo("SILENT HILL 2 Deluxe Edition"))
    }

    @Test fun precosEmReal() {
        assertEquals(119950, brlParaCent("R$1.199,50"))
        assertEquals(4987, brlParaCent("R$49,87"))
        assertNull(brlParaCent("Gratuito"))
        assertEquals("R$ 1.199,50", formatarBrl(119950))
        assertEquals("R$ 0,05", formatarBrl(5))
    }

    @Test fun lePlayStation() {
        val l = Leitura.gradePs(res("ps_grid.json"))
        assertEquals(2, l.size)
        assertEquals("UP0102-PPSA07411_00-RE4RMAINGAME0000", l[0].id)
        assertEquals(19950, l[0].baseCent); assertEquals(4987, l[0].atualCent); assertEquals(75, l[0].desconto)
        assertNotNull(l[0].capa)
        val p = Leitura.produtoPs(res("ps_produto.json"), "x", "Days Gone", null)!!
        assertEquals(24950, p.baseCent); assertEquals(9980, p.atualCent); assertEquals(60, p.desconto)
        assertEquals(1793865540000L, p.fimMs)
    }

    @Test fun leSteam() {
        val l = Leitura.ofertasSteam(res("steam_ofertas.json"))
        assertEquals(2, l.size); assertEquals(72, l[0].desconto); assertEquals(2491, l[0].atualCent)
        assertEquals(8899, Leitura.precoSteam(res("steam_preco.json"), "1145350", "Hades II", null)!!.atualCent)
        assertEquals("Hades II", Leitura.buscaSteam(res("steam_busca.json"))[0].titulo)
    }

    @Test fun leXbox() {
        val l = Leitura.ofertasXbox(res("xbox_ofertas.html"))
        assertEquals(2, l.size); assertTrue(l.all { it.loja == Loja.XBOX && it.baseCent > 0 })
        val s = l.first { it.id == "9P9JDV1PV21H" }
        assertEquals(15980, s.atualCent); assertEquals(39950, s.baseCent); assertEquals(60, s.desconto); assertNotNull(s.fimMs)
        val p = Leitura.produtosXbox(res("xbox_produto.json")).single()
        assertEquals(15980, p.atualCent); assertEquals(60, p.desconto); assertEquals(15980, p.melhorConhecido)
        assertTrue(p.capa!!.startsWith("https://"))
        assertEquals(listOf("9P8DL6W0JBB8", "9PCR3Z2MSM4T"), Leitura.buscaXbox(res("xbox_busca.json")))
    }

    @Test fun alvoVemDoHistorico() {
        assertEquals(7000, alvoSugerido(10000, null))
        assertEquals(7000, alvoSugerido(10000, 10000))
        assertEquals(4500, alvoSugerido(10000, 4500))
    }

    @Test fun avisaEntradaEmPromocaoAlvoEMenorPreco() {
        val antes = item(preco(10000), listOf(Ponto(1, 10000), Ponto(2, 8000)))
        val (novo, f) = aplicarVerificacao(antes, Jogo("jogo", "Jogo", null, mapOf(Loja.PS to preco(6000, d = 40))), 3L * 86400000)
        assertTrue(f.any { it.startsWith("Entrou em promoção: R$ 60,00 na PS Store") })
        assertTrue("Atingiu o preço-alvo" in f); assertTrue("Menor preço já visto" in f)
        assertEquals(Ponto(3, 6000), novo.historico.last())
    }

    @Test fun naoRepeteAvisoSemMudanca() {
        val p = preco(6000, d = 40)
        val (_, f) = aplicarVerificacao(item(p, listOf(Ponto(1, 6000))), Jogo("jogo", "Jogo", null, mapOf(Loja.PS to p)), 2L * 86400000)
        assertTrue(f.isEmpty())
    }

    @Test fun avisaFimDaPromocaoUmaVez() {
        val agora = 5L * 86400000
        val p = preco(6000, d = 40, fim = agora + 3600000)
        val j = Jogo("jogo", "Jogo", null, mapOf(Loja.PS to p))
        val (novo, f1) = aplicarVerificacao(item(p, listOf(Ponto(4, 6000))), j, agora)
        assertEquals(listOf("Promoção na PS Store termina em breve"), f1)
        assertTrue(aplicarVerificacao(novo, j, agora + 1000).second.isEmpty())
    }

    @Test fun agrupaLojasERankeia() {
        val g = agrupar(listOf(
            Preco(Loja.PS, "a", "Elden Ring PS5", "capa-ps", 22900, 11450, 50),
            Preco(Loja.STEAM, "b", "ELDEN RING", null, 22900, 13740, 40),
            Preco(Loja.XBOX, "c", "Outro Jogo", null, 10000, 2000, 80),
        ))
        assertEquals(2, g.size)
        val er = g.first { it.chave == "elden ring" }
        assertEquals(Loja.PS, er.melhor!!.loja); assertEquals("capa-ps", er.capa)
        assertEquals("outro jogo", rankear(g)[0].chave)
    }

    @Test fun jsonIdaEVolta() {
        val i = ItemRadar(Jogo("j", "J", null, mapOf(Loja.XBOX to Preco(Loja.XBOX, "x", "J", "c", 100, 50, 50, 9L, 40))), listOf(Ponto(1, 50)), setOf("a"))
        assertEquals(i, Json.item(JSONObject(Json.item(i).toString())))
    }

    @Test fun tokenSoVaiParaDominioCerto() {
        assertTrue(Dominios.permitido(Loja.PS, "https://ca.account.sony.com/api/authz/v3/oauth/token"))
        assertFalse(Dominios.permitido(Loja.PS, "http://ca.account.sony.com/x"))
        assertFalse(Dominios.permitido(Loja.PS, "https://ca.account.sony.com.evil.io/x"))
        assertFalse(Dominios.permitido(Loja.STEAM, "https://web.np.playstation.com/x"))
    }

    @Test fun leNpssoPuroOuEmJson() {
        val t = "a".repeat(64)
        assertEquals(t, lerNpsso(" $t "))
        assertEquals(t, lerNpsso("""{"npsso":"$t"}"""))
        assertNull(lerNpsso("curto"))
    }

    @Test fun plusNaoContaComoComprado() {
        val l = Leitura.objetos(org.json.JSONArray("""[{"name":"Comprado","membership":"NONE"},{"name":"Da Plus","membership":"PS_PLUS"}]"""))
        assertEquals(listOf("Comprado"), lerCompradosPs(l).map { it.titulo })
    }
}
