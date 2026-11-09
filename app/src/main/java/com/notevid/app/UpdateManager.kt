package com.notevid.app

import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

/** GitHub Releases APK OTA; user ALWAYS confirms the Android package installer. */
class UpdateManager(private val activity: Activity) {
    private val worker = Executors.newSingleThreadExecutor()
    private val prefs = activity.getSharedPreferences("notevid", 0)

    data class Asset(val name: String, val url: String)
    data class Release(val version: String, val apk: Asset, val checksums: Asset)

    fun maybeAutoCheck() {
        val repo = getRepo()
        if (repo.startsWith("OWNER/")) return
        if (!prefs.getBoolean("autoCheck", true)) return
        val last = prefs.getLong("lastUpdateCheck", 0L)
        if (System.currentTimeMillis() - last < 24L * 60 * 60 * 1000) return
        prefs.edit().putLong("lastUpdateCheck", System.currentTimeMillis()).apply()
        check(silent = true)
    }

    fun getRepo(): String = prefs.getString("githubRepo", BuildConfig.GITHUB_REPO).orEmpty().trim()

    fun check(silent: Boolean = false) {
        val repo = getRepo()
        if (!Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$").matches(repo) || repo.startsWith("OWNER/")) {
            if (!silent) info("Hãy đặt chủ repository dạng ten-github/NoteVid trong Cài đặt.")
            return
        }
        if (!silent) Toast.makeText(activity, "Đang kiểm tra cập nhật…", Toast.LENGTH_SHORT).show()
        worker.execute {
            try {
                val release = readRelease(repo)
                activity.runOnUiThread {
                    if (VersionCheck.isNewer(release.version, BuildConfig.VERSION_NAME)) {
                        AlertDialog.Builder(activity)
                            .setTitle("NoteVid ${release.version}")
                            .setMessage("Có bản mới. Tải APK từ GitHub Releases, kiểm tra SHA-256 rồi mở trình cài đặt Android?")
                            .setPositiveButton("Tải và cài") { _, _ -> downloadAndInstall(release) }
                            .setNegativeButton("Để sau", null).show()
                    } else if (!silent) info("Bạn đang dùng phiên bản mới nhất: ${BuildConfig.VERSION_NAME}.")
                }
            } catch (e: Exception) {
                if (!silent) activity.runOnUiThread { info("Không kiểm tra được cập nhật: ${e.message}") }
            }
        }
    }

    private fun readRelease(repo: String): Release {
        val json = JSONObject(getText("https://api.github.com/repos/$repo/releases/latest"))
        val tag = json.getString("tag_name")
        require(VersionCheck.parse(tag) != null) { "Tag bản phát hành phải dạng v1.2.3" }
        val assets = json.getJSONArray("assets")
        var apk: Asset? = null
        var sha: Asset? = null
        for (i in 0 until assets.length()) {
            val item = assets.getJSONObject(i)
            val asset = Asset(item.getString("name"), item.getString("browser_download_url"))
            if (asset.name.endsWith(".apk") && asset.name.startsWith("NoteVid-")) apk = asset
            if (asset.name == "SHA256SUMS.txt") sha = asset
        }
        return Release(tag.removePrefix("v"), apk ?: throw IOException("Thiếu APK trên Release"),
            sha ?: throw IOException("Thiếu SHA256SUMS.txt trên Release"))
    }

    private fun connection(url: String): HttpURLConnection {
        require(url.startsWith("https://")) { "Chỉ hỗ trợ HTTPS" }
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "NoteVid/${BuildConfig.VERSION_NAME}")
            setRequestProperty("Accept", "*/*")
        }
    }

    private fun getText(url: String): String {
        val c = connection(url)
        try {
            if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText().take(100_000) }
        } finally { c.disconnect() }
    }

    private fun downloadAndInstall(release: Release) {
        val dialog = ProgressDialog(activity).apply {
            setTitle("Đang tải NoteVid ${release.version}")
            setMessage("Tải APK và xác minh SHA-256…")
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            setCancelable(false)
            show()
        }
        worker.execute {
            val extDir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            val target = if (extDir != null) File(extDir, "NoteVid-update.apk")
                else File(File(activity.cacheDir, "updates").apply { mkdirs() }, "NoteVid-update.apk")
            try {
                val checksums = getText(release.checksums.url)
                val expected = checksums.lineSequence().map { it.trim() }.firstNotNullOfOrNull { line ->
                    val match = Regex("^([a-fA-F0-9]{64})\\s+\\*?(.+)$").matchEntire(line)
                    if (match != null && match.groupValues[2] == release.apk.name) match.groupValues[1] else null
                } ?: throw IOException("Không có SHA-256 của APK trong manifest")
                val c = connection(release.apk.url)
                val digest = MessageDigest.getInstance("SHA-256")
                try {
                    if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
                    val length = c.contentLengthLong
                    if (length > 200L * 1024 * 1024) throw IOException("APK quá lớn")
                    var done = 0L
                    c.inputStream.use { input ->
                        target.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                done += n
                                if (done > 200L * 1024 * 1024) throw IOException("APK quá lớn")
                                digest.update(buffer, 0, n)
                                output.write(buffer, 0, n)
                                if (length > 0) activity.runOnUiThread {
                                    dialog.progress = ((done * 100) / length).toInt().coerceIn(0, 100)
                                }
                            }
                        }
                    }
                } finally { c.disconnect() }
                val found = digest.digest().joinToString("") { "%02x".format(it) }
                if (!found.equals(expected, ignoreCase = true)) throw IOException("SHA-256 không khớp: đã hủy cài đặt")
                activity.runOnUiThread { dialog.dismiss(); install(target) }
            } catch (e: Exception) {
                target.delete()
                activity.runOnUiThread { dialog.dismiss(); info("Không thể cập nhật: ${e.message}") }
            }
        }
    }

    private fun install(apk: File) {
        if (Build.VERSION.SDK_INT >= 26 && !activity.packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(activity)
                .setTitle("Cấp quyền cài bản cập nhật")
                .setMessage("Android cần bạn cho phép NoteVid cài ứng dụng từ nguồn này. Sau khi cấp quyền, quay lại NoteVid và kiểm tra cập nhật một lần nữa.")
                .setPositiveButton("Mở cài đặt") { _, _ ->
                    activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${activity.packageName}")))
                }.setNegativeButton("Hủy", null).show()
            return
        }
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try { activity.startActivity(intent) }
        catch (e: Exception) { info("Không mở được trình cài đặt: ${e.message}") }
    }

    private fun info(message: String) = AlertDialog.Builder(activity)
        .setTitle("Cập nhật NoteVid").setMessage(message).setPositiveButton("OK", null).show()
}
