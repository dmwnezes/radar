package com.dmwnezes.radar

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf

class MainActivity : ComponentActivity() {
    private val estado by lazy { Estado(applicationContext) }

    override fun onCreate(salvo: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        super.onCreate(salvo)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        Verificacao.agendar(this)
        receberRetorno(intent)
        setContent { App(estado) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receberRetorno(intent)
    }

    private fun receberRetorno(i: Intent?) {
        val u = i?.data ?: return
        if (u.scheme == "com.dmwnezes.radar" && u.host == "xbox") estado.concluirXbox(u)
    }
}
