package app.kin.services

import android.content.ContentProvider
import android.content.ContentValues
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

/** Test APK only: simulates a file provider whose original file can disappear. */
class SoundFixtureProvider : ContentProvider() {
    override fun onCreate() = true
    private fun file(uri: Uri) = File(context!!.cacheDir,"fixture-${uri.lastPathSegment}.wav")
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        require(mode == "r")
        val file = file(uri)
        if (!file.exists()) {
            when (uri.lastPathSegment) {
                "invalid" -> file.writeText("Not an audio file")
                "large" -> java.io.RandomAccessFile(file,"rw").use { it.setLength(10L * 1024 * 1024 + 1) }
                else -> {
                    val rate = 22050;val count = rate * 4
                    val b = ByteBuffer.allocate(44 + count * 2).order(ByteOrder.LITTLE_ENDIAN)
                    b.put("RIFF".toByteArray()).putInt(36 + count * 2).put("WAVEfmt ".toByteArray())
                        .putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
                        .put("data".toByteArray()).putInt(count * 2)
                    repeat(count) { i -> b.putShort((sin(2 * Math.PI * 440 * i / rate) * 6000).toInt().toShort()) }
                    file.writeBytes(b.array())
                }
            }
        }
        return ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY)
    }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?) =
        MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)).apply { addRow(arrayOf("My phone tone.wav")) }
    override fun getType(uri: Uri) = "audio/wav"
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = if (file(uri).delete()) 1 else 0
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0
}
