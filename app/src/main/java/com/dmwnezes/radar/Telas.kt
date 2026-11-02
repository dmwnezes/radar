package com.dmwnezes.radar

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

private val Fundo = Color(0xFF0E0E11)
private val Sup = Color(0xFF1A1A1F)
private val Muted = Color(0xFFA3A3AD)
private val Verde = Color(0xFF2DD4BF)
private val VerdeTexto = Color(0xFF042F2A)
private val Linha = Color(0xFF26262C)
private val Trilho = Color(0xFF2A2A31)
private val Cinza = Color(0xFF71717C)
private val Branco = Color.White

@OptIn(ExperimentalTextApi::class)
private fun variavel(res: Int, peso: Int) = Font(res, FontWeight(peso), variationSettings = FontVariation.Settings(FontVariation.weight(peso)))

private val Instrument = FontFamily(listOf(400, 500, 600, 700).map { variavel(R.font.instrument_sans, it) })
private val Grotesk = FontFamily(Font(R.font.space_grotesk, FontWeight.Bold))

@Composable
private fun T(
    s: String, tam: TextUnit, peso: Int = 400, cor: Color = Branco, fam: FontFamily = Instrument, mod: Modifier = Modifier,
    linhas: Int = Int.MAX_VALUE, riscado: Boolean = false, altura: TextUnit = TextUnit.Unspecified, esp: TextUnit = TextUnit.Unspecified,
) = Text(s, mod, cor, tam, fontWeight = FontWeight(peso), fontFamily = fam, maxLines = linhas, overflow = TextOverflow.Ellipsis,
    textDecoration = if (riscado) TextDecoration.LineThrough else null, lineHeight = altura, letterSpacing = esp)

@Composable
fun App(e: Estado) {
    val a = e.aberto
    BackHandler(a != null) { e.aberto = null }
    Column(Modifier.fillMaxSize().background(Fundo).systemBarsPadding().imePadding()) {
        Box(Modifier.weight(1f)) {
            when {
                a != null -> TelaJogo(e, a)
                e.aba == Aba.HOJE -> TelaHoje(e)
                e.aba == Aba.RADAR -> TelaRadar(e)
                else -> TelaJogos(e)
            }
        }
        if (a == null) Barra(e)
    }
}

@Composable
private fun Barra(e: Estado) {
    Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp, 16.dp, 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(Triple(Aba.RADAR, "Radar", R.drawable.ic_radar), Triple(Aba.HOJE, "Hoje", R.drawable.ic_hoje), Triple(Aba.JOGOS, "Jogos", R.drawable.ic_jogos)).forEach { (aba, nome, ic) ->
            val on = e.aba == aba
            val cor = if (on) Fundo else Branco
            Row(
                Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(12.dp)).background(if (on) Branco else Sup).clickable { e.aba = aba },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
            ) {
                Icon(painterResource(ic), null, Modifier.size(18.dp), cor)
                Spacer(Modifier.width(6.dp))
                T(nome, 13.sp, 600, cor)
            }
        }
    }
}

@Composable
private fun Capa(url: String?, mod: Modifier, raio: Dp = 12.dp) {
    var u by remember(url) { mutableStateOf(url) }
    Box(mod.clip(RoundedCornerShape(raio)).background(Sup)) {
        if (u != null) AsyncImage(model = u, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(), onError = { u = null })
    }
}

@Composable
private fun Coracao(ativo: Boolean, titulo: String, tam: Dp = 20.dp, comFundo: Boolean = false, aoTocar: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = aoTocar).semantics { contentDescription = "Radar: $titulo" }, contentAlignment = Alignment.Center) {
        Box(if (comFundo) Modifier.size(32.dp).clip(CircleShape).background(Fundo) else Modifier, contentAlignment = Alignment.Center) {
            Icon(if (ativo) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null, Modifier.size(tam), if (ativo) Verde else Branco)
        }
    }
}

@Composable
private fun PilulaDesconto(d: Int, tam: TextUnit = 12.sp) {
    if (d > 0) T("-$d%", tam, 700, VerdeTexto, mod = Modifier.clip(RoundedCornerShape(5.dp)).background(Verde).padding(horizontal = 6.dp, vertical = 2.dp))
}

@Composable
private fun BarraAlvo(i: ItemRadar, alt: Dp = 8.dp) {
    Box(Modifier.fillMaxWidth().height(alt).clip(RoundedCornerShape(alt / 2)).background(Trilho)) {
        Box(Modifier.fillMaxWidth(i.progresso).fillMaxHeight().clip(RoundedCornerShape(alt / 2)).background(if (i.atingiu) Verde else Cinza))
    }
}

private fun faltaTexto(i: ItemRadar): String {
    val a = i.alvoCent ?: return ""
    val p = i.jogo.melhor?.atualCent ?: return ""
    return if (p <= a) "Alvo atingido" else "Faltam ${formatarBrl(p - a)}"
}

private fun fimTexto(j: Jogo): String? {
    val m = j.melhor?.takeIf { it.desconto > 0 } ?: return null
    val fim = m.fimMs ?: return null
    val dias = (fim - System.currentTimeMillis()) / (24L * 60 * 60 * 1000)
    return when { dias < 0 -> null; dias == 0L -> "Termina hoje"; dias == 1L -> "Termina em 1 dia"; else -> "Termina em $dias dias" }
}

@Composable
private fun TelaHoje(e: Estado) {
    val lista = e.jogosHoje
    val dest = lista.firstOrNull()
    LazyVerticalGrid(GridCells.Fixed(2), Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item(span = { GridItemSpan(2) }) {
            Column {
                Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    T("Em promoção", 34.sp, 600, mod = Modifier.weight(1f), esp = (-0.6).sp)
                    Box(Modifier.size(44.dp).clip(CircleShape).clickable { e.atualizar() }.semantics { contentDescription = "Atualizar" }, contentAlignment = Alignment.Center) {
                        if (e.carregando) CircularProgressIndicator(Modifier.size(20.dp), Branco, 2.dp) else Icon(Icons.Rounded.Refresh, null, tint = Branco)
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    (listOf(null) + Loja.entries).forEach { l ->
                        Box(Modifier.height(44.dp).clickable { e.filtro = l }, contentAlignment = Alignment.Center) {
                            T(when (l) { null -> "Tudo"; Loja.PS -> "PlayStation"; else -> l.nome }, 14.sp, 500, if (e.filtro == l) Branco else Muted)
                        }
                    }
                }
            }
        }
        if (dest == null) {
            item(span = { GridItemSpan(2) }) {
                T(if (e.carregando) "Buscando as promoções de hoje…" else "Nenhuma promoção carregada. Toque em atualizar.", 14.sp, cor = Muted, mod = Modifier.padding(vertical = 40.dp))
            }
        } else {
            item(span = { GridItemSpan(2) }) { Destaque(e, dest) }
            items(lista.drop(1).take(60), key = { it.chave }) { CartaoOferta(e, it) }
        }
    }
}

@Composable
private fun Destaque(e: Estado, j: Jogo) {
    Column(Modifier.clickable { e.abrir(j) }) {
        Box(Modifier.fillMaxWidth().height(200.dp)) {
            Capa(j.capa, Modifier.fillMaxSize())
            Box(Modifier.align(Alignment.TopEnd).padding(6.dp)) { Coracao(e.itemDe(j) != null, j.titulo, 18.dp, true) { e.alternarRadar(j) } }
        }
        T("Melhor do dia · ${j.melhor?.loja?.nome}", 12.sp, cor = Muted, mod = Modifier.padding(top = 12.dp))
        T(j.titulo, 20.sp, 600, mod = Modifier.padding(top = 2.dp))
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PilulaDesconto(j.desconto)
            j.melhor?.let {
                T(formatarBrl(it.baseCent), 14.sp, cor = Muted, riscado = true)
                T(formatarBrl(it.atualCent), 14.sp, 600)
            }
        }
        if (e.jaTem(j)) T("Já na sua biblioteca", 12.sp, cor = Muted, mod = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun CartaoOferta(e: Estado, j: Jogo) {
    val item = e.itemDe(j)
    Column(Modifier.clickable { e.abrir(j) }) {
        Box(Modifier.fillMaxWidth().height(228.dp)) {
            Capa(j.capa, Modifier.fillMaxSize())
            Box(Modifier.align(Alignment.TopEnd).padding(6.dp)) { Coracao(item != null, j.titulo, 18.dp, true) { e.alternarRadar(j) } }
        }
        T(j.melhor?.loja?.nome ?: "", 12.sp, cor = Muted, mod = Modifier.padding(top = 10.dp))
        T(j.titulo, 14.sp, 600, mod = Modifier.padding(top = 2.dp), linhas = 2, altura = 18.sp)
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PilulaDesconto(j.desconto)
            T(j.melhor?.let { formatarBrl(it.atualCent) } ?: "", 13.sp, 600)
        }
        if (item != null) {
            Spacer(Modifier.height(8.dp))
            BarraAlvo(item, 6.dp)
            T(faltaTexto(item), 12.sp, 600, if (item.atingiu) Verde else Muted, mod = Modifier.padding(top = 4.dp))
        }
        if (e.jaTem(j)) T("Já na sua biblioteca", 12.sp, cor = Muted, mod = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun Campo(valor: String, aoMudar: (String) -> Unit, dica: String, secreto: Boolean = false, aoEnviar: () -> Unit = {}, icone: Boolean = false, noCartao: Boolean = true) {
    Row(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp)).background(if (noCartao) Fundo else Sup).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icone) { Icon(Icons.Rounded.Search, null, Modifier.size(18.dp), Muted); Spacer(Modifier.width(10.dp)) }
        Box(Modifier.weight(1f)) {
            if (valor.isEmpty()) T(dica, 14.sp, cor = Muted)
            BasicTextField(
                valor, aoMudar, Modifier.fillMaxWidth().semantics { contentDescription = dica }, singleLine = true,
                textStyle = TextStyle(color = Branco, fontSize = 14.sp, fontFamily = Instrument), cursorBrush = SolidColor(Verde),
                visualTransformation = if (secreto) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = !secreto, keyboardType = if (secreto) KeyboardType.Password else KeyboardType.Text, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { aoEnviar() }),
            )
        }
    }
}

@Composable
private fun TelaRadar(e: Estado) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 28.dp, 16.dp, 24.dp)) {
        item {
            T("${e.radar.size}", 64.sp, 700, fam = Grotesk, altura = 64.sp, esp = (-1.8).sp)
            T(if (e.radar.size == 1) "jogo no radar" else "jogos no radar", 18.sp, 500, mod = Modifier.padding(top = 4.dp))
            T("No preço-alvo hoje: ${e.radar.count { it.atingiu }}", 14.sp, cor = Muted, mod = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(18.dp))
            Campo(e.termo, { e.termo = it; if (it.isBlank()) e.resultados = emptyList() }, "Adicionar jogo ao radar", aoEnviar = { e.buscar() }, icone = true, noCartao = false)
            Spacer(Modifier.height(18.dp))
            if (e.buscando) T("Procurando nas lojas…", 14.sp, cor = Muted, mod = Modifier.padding(bottom = 16.dp))
        }
        if (e.resultados.isNotEmpty()) {
            items(e.resultados.take(12), key = { "r" + it.chave }) { j ->
                Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).clickable { e.abrir(j) }, verticalAlignment = Alignment.CenterVertically) {
                        Capa(j.capa, Modifier.size(56.dp, 74.dp), 8.dp)
                        Column(Modifier.padding(start = 12.dp)) {
                            T(j.titulo, 14.sp, 600, linhas = 2)
                            T(j.melhor?.let { "${formatarBrl(it.atualCent)} · ${j.precos.keys.joinToString(", ") { l -> l.nome }}" } ?: "", 12.sp, cor = Muted, mod = Modifier.padding(top = 2.dp))
                        }
                    }
                    Coracao(e.itemDe(j) != null, j.titulo) { e.alternarRadar(j) }
                }
            }
            item { Box(Modifier.fillMaxWidth().height(1.dp).background(Linha)); Spacer(Modifier.height(16.dp)) }
        }
        if (e.radar.isEmpty() && e.resultados.isEmpty()) {
            item { T("Toque no coração de um jogo ou procure acima. O Radar avisa quando ele entrar em promoção.", 14.sp, cor = Muted, altura = 21.sp) }
        }
        items(e.radar, key = { it.jogo.chave }) { i -> LinhaRadar(e, i) }
    }
}

@Composable
private fun LinhaRadar(e: Estado, i: ItemRadar) {
    val j = i.jogo
    Column {
        Row(Modifier.fillMaxWidth()) {
            Row(Modifier.weight(1f).clickable { e.abrir(j) }) {
                Capa(j.capa, Modifier.size(84.dp, 112.dp), 10.dp)
                Column(Modifier.padding(start = 14.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    T(j.titulo, 16.sp, 600, linhas = 2)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        T(j.melhor?.let { formatarBrl(it.atualCent) } ?: "—", 14.sp, 600)
                        T(j.melhor?.loja?.nome ?: "", 14.sp, cor = Muted)
                    }
                    Spacer(Modifier.height(1.dp))
                    BarraAlvo(i)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        T(i.alvoCent?.let { "Alvo ${formatarBrl(it)}" } ?: "", 12.sp, cor = Muted)
                        T(faltaTexto(i), 12.sp, 600, if (i.atingiu) Verde else Muted)
                    }
                    if (e.jaTem(j)) T("Já na sua biblioteca", 12.sp, cor = Muted)
                }
            }
            Coracao(true, j.titulo) { e.alternarRadar(j) }
        }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Linha))
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun BotaoBranco(texto: String, mod: Modifier = Modifier, ativo: Boolean = true, aoTocar: () -> Unit) {
    Box(mod.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(10.dp)).background(if (ativo) Branco else Trilho).clickable(enabled = ativo, onClick = aoTocar), contentAlignment = Alignment.Center) {
        T(texto, 14.sp, 600, if (ativo) Fundo else Muted)
    }
}

@Composable
private fun TelaJogo(e: Estado, j: Jogo) {
    val ctx = LocalContext.current
    val item = e.itemDe(j)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.padding(start = 6.dp, top = 10.dp).size(44.dp).clip(CircleShape).clickable { e.aberto = null }.semantics { contentDescription = "Voltar" }, contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = Branco)
        }
        Column(Modifier.padding(16.dp, 6.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            T(j.titulo, 28.sp, 600, altura = 31.sp, esp = (-0.5).sp)
            Capa(j.capa, Modifier.fillMaxWidth().height(210.dp))
            if (e.jaTem(j)) T("Já na sua biblioteca", 13.sp, 600, Verde)
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Sup).padding(16.dp)) {
                T("Preço agora · ${j.melhor?.loja?.nome ?: ""}", 13.sp, cor = Muted)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    T(j.melhor?.let { formatarBrl(it.atualCent) } ?: "—", 36.sp, 700, fam = Grotesk, esp = (-0.7).sp)
                    PilulaDesconto(j.desconto, 13.sp)
                }
                fimTexto(j)?.let { T(it, 13.sp, cor = Muted) }
                if (item != null) {
                    Spacer(Modifier.height(10.dp)); BarraAlvo(item); Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        T(item.alvoCent?.let { "Alvo ${formatarBrl(it)}" } ?: "", 13.sp, cor = Muted)
                        T(faltaTexto(item), 13.sp, 600, if (item.atingiu) Verde else Muted)
                    }
                }
            }
            BotaoBranco(if (item != null) "No radar" else "Colocar no radar") { e.alternarRadar(j) }
            Column {
                T("Lojas", 14.sp, 600, mod = Modifier.padding(bottom = 4.dp))
                Loja.entries.forEach { l ->
                    val p = j.precos[l]
                    Row(
                        Modifier.fillMaxWidth().height(44.dp).then(if (p != null) Modifier.clickable { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(p.url))) } else Modifier),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        T(l.nome, 14.sp, cor = Muted)
                        T(p?.let { formatarBrl(it.atualCent) } ?: if (e.comparando) "Procurando…" else "Não encontrado", 14.sp, 600, if (p != null && p == j.melhor) Verde else Muted)
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Linha))
                }
            }
            Column {
                T("Histórico de preço", 14.sp, 600, mod = Modifier.padding(bottom = 12.dp))
                val h = item?.historico.orEmpty().takeLast(30)
                if (h.size < 2) {
                    T(if (item == null) "O histórico começa quando o jogo entra no radar." else "O histórico cresce a cada verificação diária.", 13.sp, cor = Muted)
                } else {
                    val max = h.maxOf { it.cent }.toFloat(); val min = h.minOf { it.cent }
                    Row(Modifier.fillMaxWidth().height(72.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        h.forEach { p ->
                            Box(Modifier.weight(1f).fillMaxHeight((p.cent / max).coerceAtLeast(0.05f)).clip(RoundedCornerShape(3.dp)).background(if (p.cent == min) Verde else Color(0xFF3A3A42)))
                        }
                    }
                    T("Menor preço visto: ${formatarBrl(min)}", 13.sp, cor = Muted, mod = Modifier.padding(top = 10.dp))
                }
            }
        }
    }
}

@Composable
private fun CartaoConta(titulo: String, texto: String, conteudo: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Sup).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        T(titulo, 18.sp, 600)
        T(texto, 14.sp, cor = Muted, altura = 21.sp)
        conteudo()
    }
}

@Composable
private fun Conectada(e: Estado, l: Loja, nome: String) {
    val n = e.biblioteca.count { it.loja == l }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Sup).padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        T("$nome conectada · $n jogos", 14.sp, 500, mod = Modifier.weight(1f))
        Box(Modifier.height(48.dp).clickable { e.desconectar(l) }.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) { T("Desconectar", 13.sp, 600, Muted) }
    }
}

@Composable
private fun TelaJogos(e: Estado) {
    val ctx = LocalContext.current
    e.contasVersao
    var npsso by remember { mutableStateOf("") }
    var steamId by remember { mutableStateOf("") }
    var steamChave by remember { mutableStateOf("") }
    var azure by remember { mutableStateOf("") }
    fun abrir(u: String) = ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)))
    LazyVerticalGrid(GridCells.Fixed(2), Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 28.dp, 16.dp, 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item(span = { GridItemSpan(2) }) { T("Meus jogos", 34.sp, 600, esp = (-0.6).sp) }
        e.erroConta?.let { m -> item(span = { GridItemSpan(2) }) { T(m, 14.sp, 600, Color(0xFFFF8A8A)) } }
        e.novaVersao?.let { v ->
            item(span = { GridItemSpan(2) }) {
                BotaoBranco("Atualizar para a versão ${v.nome}") {
                    if (Atualizador.podeInstalar(ctx)) e.instalarNovaVersao()
                    else ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")))
                }
            }
        }
        item(span = { GridItemSpan(2) }) {
            if (e.contas.psConectada) Conectada(e, Loja.PS, "PlayStation") else CartaoConta(
                "Traga sua conta PlayStation",
                "Entre em playstation.com, abra o link abaixo e copie o código npsso. Ele fica criptografado só neste aparelho e vai apenas para a Sony. Trate esse código como sua senha.",
            ) {
                T("Abrir página do código", 14.sp, 600, Verde, mod = Modifier.clickable { abrir("https://ca.account.sony.com/api/v1/ssocookie") }.padding(vertical = 6.dp))
                Campo(npsso, { npsso = it }, "Colar código npsso", secreto = true)
                BotaoBranco("Conectar conta", ativo = npsso.isNotBlank() && !e.carregando) { e.conectarPs(npsso); npsso = "" }
            }
        }
        item(span = { GridItemSpan(2) }) {
            if (e.contas.steamConectada) Conectada(e, Loja.STEAM, "Steam") else CartaoConta(
                "Conectar Steam",
                "Use seu SteamID de 17 números e uma chave da API da Steam. Seus jogos precisam estar visíveis no perfil.",
            ) {
                T("Criar chave da API", 14.sp, 600, Verde, mod = Modifier.clickable { abrir("https://steamcommunity.com/dev/apikey") }.padding(vertical = 6.dp))
                Campo(steamId, { steamId = it }, "SteamID")
                Campo(steamChave, { steamChave = it }, "Chave da API", secreto = true)
                BotaoBranco("Conectar conta", ativo = steamId.isNotBlank() && steamChave.isNotBlank() && !e.carregando) { e.conectarSteam(steamId, steamChave); steamChave = "" }
            }
        }
        item(span = { GridItemSpan(2) }) {
            if (e.contas.xboxConectada) Conectada(e, Loja.XBOX, "Xbox") else CartaoConta(
                "Conectar Xbox",
                "Precisa de um app registrado no Azure, com o endereço de retorno ${Contas.RETORNO_XBOX}. O Xbox informa os jogos já jogados, não as compras.",
            ) {
                Campo(azure, { azure = it }, "Client ID do Azure")
                BotaoBranco("Entrar com a Microsoft", ativo = azure.isNotBlank()) { e.iniciarXbox(azure)?.let { u -> ctx.startActivity(Intent(Intent.ACTION_VIEW, u)) } }
            }
        }
        val lista = e.biblioteca.distinctBy { chaveDoTitulo(it.titulo) }.sortedBy { it.titulo.lowercase() }
        items(lista, key = { it.loja.name + it.titulo }) { p ->
            Column {
                Capa(p.capa, Modifier.fillMaxWidth().height(228.dp))
                T(p.titulo, 14.sp, 600, mod = Modifier.padding(top = 10.dp), linhas = 2)
            }
        }
    }
}
