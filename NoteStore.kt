package com.notevid.app

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.IOException
import java.nio.charset.StandardCharsets

/** Uses SAF's persisted folder access; no broad READ_MEDIA/WRITE_EXTERNAL_STORAGE privileges. */
class NoteStore(private val context: Context) {
    data class VideoFile(val name: String, val uri: Uri, val folderUri: Uri, val bytes: Long)

    fun rootFolder(uri: Uri): DocumentFile = DocumentFile.fromTreeUri(context, uri)
        ?: throw IOException("Không mở được thư mục đã chọn.")

    fun videosIn(rootUri: Uri): List<VideoFile> {
        val folder = rootFolder(rootUri)
        return folder.listFiles().filter { it.isFile && isVideo(it) }
            .map { VideoFile(it.name ?: "video", it.uri, rootUri, it.length()) }
            .sortedBy { it.name.lowercase() }
    }

    private fun isVideo(f: DocumentFile): Boolean {
        val name = f.name.orEmpty().lowercase()
        return f.type?.startsWith("video/") == true ||
            listOf(".mp4", ".mkv", ".webm", ".mov", ".m4v", ".avi", ".3gp").any { name.endsWith(it) }
    }

    private fun sidecarName(name: String): String = name.substringBeforeLast('.', name) + ".notevid.json"

    fun read(video: VideoFile): MutableList<VideoNote> {
        val folder = DocumentFile.fromTreeUri(context, video.folderUri)
            ?: throw IOException("Không truy cập được thư mục video.")
        val file = folder.findFile(sidecarName(video.name)) ?: return mutableListOf()
        return context.contentResolver.openInputStream(file.uri).use { stream ->
            if (stream == null) throw IOException("Không đọc được ${file.name}")
            NoteJson.decode(stream.reader(StandardCharsets.UTF_8).readText())
        }
    }

    /** Never overwrite the original video. The document provider must allow writing in this folder. */
    fun write(video: VideoFile, notes: List<VideoNote>) {
        val folder = DocumentFile.fromTreeUri(context, video.folderUri)
            ?: throw IOException("Không truy cập được thư mục video.")
        val filename = sidecarName(video.name)
        val sidecar = folder.findFile(filename)
            ?: folder.createFile("application/json", filename)
            ?: throw IOException("Không thể tạo $filename trong thư mục video.")
        val bytes = NoteJson.encode(video.name, notes).toByteArray(StandardCharsets.UTF_8)
        val stream = try {
            context.contentResolver.openOutputStream(sidecar.uri, "rwt")
        } catch (_: Exception) {
            context.contentResolver.openOutputStream(sidecar.uri, "wt")
        } ?: throw IOException("Không thể ghi $filename")
        stream.use { it.write(bytes); it.flush() }
    }
}
