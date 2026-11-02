package com.dmwnezes.radar

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.pm.PackageInfoCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Atualização automática pelas Releases do GitHub (dmwnezes/radar).
 * Baixa o Radar.apk mais novo e instala com o PackageInstaller do Android.
 * No Android 12+ as atualizações seguintes ficam silenciosas depois da primeira confirmação.
 */
fun codigoDaTag(tag: String): Int? = tag.substringAfterLast('.').toIntOrNull()

object Atualizador {
    private const val API = "https://api.github.com/repos/dmwnezes/radar/releases/latest"
    private const val CANAL = "atualizacoes"
    const val JOB = 5102

    data class Versao(val codigo: Int, val nome: String, val apk: String, val notas: String)

    fun codigoAtual(ctx: Context): Long =
        PackageInfoCompat.getLongVersionCode(ctx.packageManager.getPackageInfo(ctx.packageName, 0))

    fun nomeAtual(ctx: Context): String = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"

    /** Rede: chame fora da thread principal. Devolve a versão nova, ou null se já está atualizado. */
    fun verificar(ctx: Context): Versao? {
        val con = (URL(API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000; readTimeout = 15000
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        try {
            if (con.responseCode != 200) return null
            val o = JSONObject(con.inputStream.bufferedReader().use { it.readText() })
            val tag = o.getString("tag_name")
            val codigo = codigoDaTag(tag) ?: return null
            val assets = o.getJSONArray("assets")
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.getString("name").endsWith(".apk") }?.getString("browser_download_url") ?: return null
            if (codigo <= codigoAtual(ctx)) return null
            return Versao(codigo, tag.removePrefix("v"), apk, o.optString("body"))
        } finally {
            con.disconnect()
        }
    }

    fun podeInstalar(ctx: Context) = ctx.packageManager.canRequestPackageInstalls()

    /** Rede + disco: fora da thread principal. aoProgresso recebe 0..1. */
    fun baixarEInstalar(ctx: Context, v: Versao, aoProgresso: (Float) -> Unit = {}) {
        val instalador = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(ctx.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = instalador.createSession(params)
        try {
            instalador.openSession(id).use { sessao ->
                val con = URL(v.apk).openConnection() as HttpURLConnection
                con.connectTimeout = 15000; con.readTimeout = 30000
                try {
                    val total = con.contentLengthLong.takeIf { it > 0 } ?: -1L
                    con.inputStream.use { entrada ->
                        sessao.openWrite("Radar.apk", 0, total).use { saida ->
                            val buf = ByteArray(64 * 1024)
                            var lido = 0L
                            while (true) {
                                val n = entrada.read(buf)
                                if (n < 0) break
                                saida.write(buf, 0, n)
                                lido += n
                                if (total > 0) aoProgresso(lido.toFloat() / total)
                            }
                            sessao.fsync(saida)
                        }
                    }
                } finally {
                    con.disconnect()
                }
                val retorno = PendingIntent.getBroadcast(
                    ctx, id, Intent(ctx, InstalacaoReceiver::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                sessao.commit(retorno.intentSender)
            }
        } catch (e: Exception) {
            instalador.abandonSession(id)
            throw e
        }
    }

    fun avisar(ctx: Context, titulo: String, texto: String, toque: PendingIntent) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CANAL, "Atualizações do app", NotificationManager.IMPORTANCE_DEFAULT))
        try {
            NotificationManagerCompat.from(ctx).notify(
                JOB,
                NotificationCompat.Builder(ctx, CANAL)
                    .setSmallIcon(R.drawable.ic_notificacao)
                    .setContentTitle(titulo).setContentText(texto)
                    .setContentIntent(toque).setAutoCancel(true).build(),
            )
        } catch (_: SecurityException) {
            // Sem permissão de notificação: o app mostra o aviso quando for aberto.
        }
    }
}

/** Recebe o resultado da instalação. Se o Android pedir confirmação, abre a tela dele. */
class InstalacaoReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1) != PackageInstaller.STATUS_PENDING_USER_ACTION) return
        val confirmar = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
        confirmar ?: return
        confirmar.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(confirmar)
        } catch (_: Exception) {
            // App em segundo plano: o Android bloqueia abrir telas, então avisa por notificação.
            val toque = PendingIntent.getActivity(ctx, 1, confirmar, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            Atualizador.avisar(ctx, "Atualização pronta", "Toque para concluir a instalação", toque)
        }
    }
}
