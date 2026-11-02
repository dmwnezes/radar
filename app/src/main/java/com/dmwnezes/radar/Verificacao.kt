package com.dmwnezes.radar

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object Verificacao {
    private const val JOB = 5101
    private const val CANAL = "promocoes"

    /** Uma vez por dia, com internet, mesmo sem abrir o app. */
    fun agendar(ctx: Context) {
        val js = ctx.getSystemService(JobScheduler::class.java)
        if (js.getPendingJob(JOB) != null) return
        js.schedule(
            JobInfo.Builder(JOB, ComponentName(ctx, VerificacaoJob::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(24L * 60 * 60 * 1000)
                .setPersisted(true)
                .build()
        )
    }

    fun atualizarOfertas(dados: Dados) {
        val fontes: List<() -> List<Preco>> = listOf(Fontes::ofertasPs, Fontes::ofertasXbox, Fontes::ofertasSteam)
        val res = java.util.Collections.synchronizedList(mutableListOf<Preco>())
        fontes.map { f -> Thread { runCatching { res += f() } }.apply { start() } }.forEach { it.join() }
        if (res.isNotEmpty()) dados.salvarOfertas(res.toList())
    }

    /** Devolve o jogo com os preços de hoje. Se uma loja falhar, mantém o último preço dela. */
    private fun precosDeHoje(j: Jogo): Jogo = j.copy(precos = j.precos.mapValues { (_, p) ->
        runCatching { Fontes.precoAtual(p) }.getOrNull()?.let { novo ->
            novo.copy(capa = novo.capa ?: p.capa, titulo = p.titulo)
        } ?: p
    })

    fun verificarRadar(ctx: Context, dados: Dados, notificar: Boolean) {
        val agora = System.currentTimeMillis()
        dados.radar().forEach { antes ->
            val novoJogo = precosDeHoje(antes.jogo)
            dados.alterarRadar { atual ->
                val base = atual.firstOrNull { it.jogo.chave == antes.jogo.chave } ?: return@alterarRadar atual
                val (novo, frases) = aplicarVerificacao(base, novoJogo, agora)
                if (notificar && frases.isNotEmpty()) avisar(ctx, novo.jogo, frases)
                atual.map { if (it.jogo.chave == novo.jogo.chave) novo else it }
            }
        }
    }

    fun sincronizarContas(dados: Dados, contas: Contas) {
        if (contas.psConectada) runCatching { dados.trocarBiblioteca(Loja.PS, contas.bibliotecaPs()) }
        if (contas.steamConectada) runCatching { dados.trocarBiblioteca(Loja.STEAM, contas.bibliotecaSteam()) }
        if (contas.xboxConectada) runCatching { dados.trocarBiblioteca(Loja.XBOX, contas.bibliotecaXbox()) }
    }

    private fun avisar(ctx: Context, j: Jogo, frases: List<String>) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CANAL, "Promoções do Radar", NotificationManager.IMPORTANCE_HIGH))
        val abrir = PendingIntent.getActivity(ctx, 3, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val texto = frases.joinToString("\n")
        try {
            NotificationManagerCompat.from(ctx).notify(
                j.chave.hashCode(),
                NotificationCompat.Builder(ctx, CANAL)
                    .setSmallIcon(R.drawable.ic_notificacao)
                    .setContentTitle(j.titulo).setContentText(frases.first())
                    .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
                    .setContentIntent(abrir).setAutoCancel(true).build(),
            )
        } catch (_: SecurityException) {
            // Sem permissão de notificação: o preço novo aparece no Radar.
        }
    }
}

class VerificacaoJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Thread {
            try {
                val dados = Dados(this)
                Verificacao.atualizarOfertas(dados)
                Verificacao.verificarRadar(this, dados, notificar = true)
                Verificacao.sincronizarContas(dados, Contas(Cofre(this)))
                Atualizador.verificar(this)?.let { v ->
                    if (Atualizador.podeInstalar(this)) Atualizador.baixarEInstalar(this, v)
                }
            } catch (_: Exception) {
                // Sem internet: tenta de novo na próxima verificação.
            }
            jobFinished(params, false)
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters) = true
}
