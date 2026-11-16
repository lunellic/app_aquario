package br.com.lunelli.lulucoreef.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Fotos: redimensionadas no app (max. 1280 px), guardadas em arquivo local e enviadas ao bucket
 * privado "fotos" em <user_id>/<uuid>.jpg quando houver rede (fila em SharedPreferences).
 */
class Photos(private val context: Context, private val api: Api, private val session: SessionStore) {
    private val dir = File(context.filesDir, "fotos").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("photo_queue", Context.MODE_PRIVATE)

    private fun localFile(path: String) = File(dir, path.substringAfterLast('/'))

    /** Importa a imagem escolhida e devolve o path remoto (ou null se nao deu para ler). */
    fun import(uri: Uri): String? {
        val uid = session.userId ?: return null
        val bytes = try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_PX * 2) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val raw = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
            val scale = MAX_PX.toFloat() / maxOf(raw.width, raw.height)
            val bmp = if (scale < 1f) Bitmap.createScaledBitmap(raw, (raw.width * scale).toInt(), (raw.height * scale).toInt(), true) else raw
            ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
        } catch (_: Exception) {
            return null
        }
        val path = "$uid/${newId()}.jpg"
        localFile(path).writeBytes(bytes)
        synchronized(prefs) { prefs.edit().putStringSet("pending", queue() + path).commit() }
        return path
    }

    /** Bitmap da foto: arquivo local ou, se nao houver, baixa e guarda. Bloqueante. */
    fun load(path: String, maxPx: Int): Bitmap? {
        val file = localFile(path)
        if (!file.exists()) {
            val bytes = try { api.downloadPhoto(path) } catch (_: Exception) { null } ?: return null
            file.writeBytes(bytes)
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxPx * 2) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    fun uploadPending() {
        for (path in queue()) {
            val file = localFile(path)
            if (file.exists()) api.uploadPhoto(path, file.readBytes())
            synchronized(prefs) { prefs.edit().putStringSet("pending", queue() - path).commit() }
        }
    }

    fun pendingCount(): Int = queue().size

    private fun queue(): Set<String> = prefs.getStringSet("pending", emptySet())!!.toSet()

    companion object {
        const val MAX_PX = 1280
    }
}
