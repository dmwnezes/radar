package com.dmwnezes.radar

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Tokens e chaves das contas. Ficam só neste aparelho, cifrados com AES-256-GCM.
 * A chave mora no Android Keystore: não sai do aparelho e não entra em cópias de segurança.
 */
class Cofre(ctx: Context) {
    private val arquivo = File(ctx.noBackupFilesDir, "cofre.bin")

    private fun chave(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
        }.generateKey()
    }

    @Synchronized
    private fun ler(): JSONObject {
        if (!arquivo.exists()) return JSONObject()
        return try {
            val b = arquivo.readBytes()
            val c = Cipher.getInstance(TRANSFORMACAO)
            c.init(Cipher.DECRYPT_MODE, chave(), GCMParameterSpec(128, b, 0, 12))
            JSONObject(String(c.doFinal(b, 12, b.size - 12)))
        } catch (_: Exception) {
            // Sem conseguir abrir, as contas pedem nova conexão. Nunca cai para texto aberto.
            JSONObject()
        }
    }

    @Synchronized
    private fun gravar(o: JSONObject) {
        val c = Cipher.getInstance(TRANSFORMACAO)
        c.init(Cipher.ENCRYPT_MODE, chave())
        val tmp = File(arquivo.path + ".tmp")
        tmp.writeBytes(c.iv + c.doFinal(o.toString().toByteArray()))
        tmp.renameTo(arquivo)
    }

    operator fun get(nome: String): String? = ler().optString(nome).ifEmpty { null }

    fun salvar(vararg pares: Pair<String, String>) = gravar(ler().apply { pares.forEach { (k, v) -> put(k, v) } })

    fun apagar(vararg nomes: String) = gravar(ler().apply { nomes.forEach { remove(it) } })

    private companion object {
        const val ALIAS = "radar_cofre"
        const val TRANSFORMACAO = "AES/GCM/NoPadding"
    }
}
