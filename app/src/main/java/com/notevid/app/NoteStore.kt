package com.notevid.app

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import java.io.IOException
import java.nio.charset.StandardCharsets

/** Uses SAF's persisted folder access; no broad READ_MEDIA/WRITE_EXTERNAL_STORAGE privileges. */
class NoteStore(private val context: Context) {
    data class VideoFile(val name: String, val uri: Uri, val folderUri: Uri, val bytes: Long)

    fun rootFolder(uri: Uri): DocumentFile = DocumentFile.fromTreeUri(context, uri)
        ?: throw IOException("Không mở được thư mục đã chọn.")

    fun allNamesIn(rootUri: Uri): List<String> = rootFolder(rootUri).listFiles().mapNotNull { it.name }

    /** An individually picked video is accepted only if its parent is our writable SAF folder. */
    fun videoFromPickedFile(folderUri: Uri, fileUri: Uri): VideoFile? {
        if (folderUri.authority != fileUri.authority) return null
        val parentDocumentId = try { DocumentsContract.getTreeDocumentId(folderUri) }
            catch (_: Exception) { return null }
        val fileDocumentId = try { DocumentsContract.getDocumentId(fileUri) }
            catch (_: Exception) { return null }
        // Document ids for Android's ExternalStorageProvider are paths like primary:Movies/Sao/a.mp4.
        // Other providers may use opaque IDs. In that case, verify against enumerated files below.
        val exact = rootFolder(folderUri).listFiles().firstOrNull { item ->
            item.isFile && (try { DocumentsContract.getDocumentId(item.uri) == fileDocumentId }
                catch (_: Exception) { false })
        }
        val isDirectChild = exact != null || fileDocumentId.substringBeforeLast('/', "") == parentDocumentId
        if (!isDirectChild) return null
        val displayName = exact?.name ?: context.contentResolver.query(fileUri,
            arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: return null
        val isVideo = exact?.let { isVideo(it) } ?: isVideoName(displayName)
        if (!isVideo) return null
        val bytes = exact?.length() ?: 0L
        return VideoFile(displayName, fileUri, folderUri, bytes)
    }

    fun videosIn(rootUri: Uri): List<VideoFile> {
        val folder = rootFolder(rootUri)
        return folder.listFiles().filter { it.isFile && isVideo(it) }
            .map { VideoFile(it.name ?: "video", it.uri, rootUri, it.length()) }
            .sortedBy { it.name.lowercase() }
    }

    private fun isVideo(f: DocumentFile): Boolean {
        val name = f.name.orEmpty().lowercase()
        return f.type?.startsWith("video/") == true || isVideoName(name)
    }

    private fun isVideoName(name: String): Boolean = listOf(
        ".mp4", ".mkv", ".webm", ".mov", ".m4v", ".avi", ".3gp", ".3g2",
        ".ts", ".m2ts", ".mts", ".mpeg", ".mpg", ".wmv", ".flv"
    ).any { name.lowercase().endsWith(it) }

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
