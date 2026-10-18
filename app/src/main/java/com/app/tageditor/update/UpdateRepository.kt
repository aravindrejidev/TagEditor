package com.app.tageditor.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.app.tageditor.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** A release found on GitHub that carries an APK. */
data class UpdateInfo(
    val version: String,
    val title: String,
    val notes: String,
    val publishedAt: String,
    val pageUrl: String,
    val apkUrl: String,
    val apkSize: Long,
    val sha256: String?
)

/** "2026-10-17T05:21:33Z" -> "17 Oct 2026" (falls back to the raw date). */
fun friendlyDate(iso: String): String {
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        parser.timeZone = TimeZone.getTimeZone("UTC")
        val date = parser.parse(iso) ?: return iso.take(10)
        SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(date)
    } catch (e: Exception) {
        iso.take(10)
    }
}

/**
 * Talks to the GitHub Releases API, downloads the APK and hands it to the system installer.
 * The repository ("owner/repo") is injected at build time by the release workflow.
 */
class UpdateRepository(context: Context) {

    private val appContext: Context = context.applicationContext
    private val updatesDir = File(appContext.cacheDir, "updates")

    val repoSlug: String = BuildConfig.GITHUB_REPO
    val currentVersion: String = BuildConfig.VERSION_NAME
    val isConfigured: Boolean = repoSlug.contains('/')

    // ---------------------------------------------------------------------------------------
    // Checking
    // ---------------------------------------------------------------------------------------

    /** Newest published release that has an APK attached, or null when there is none. */
    suspend fun fetchLatest(): UpdateInfo? = withContext(Dispatchers.IO) {
        val connection = URL("https://api.github.com/repos/$repoSlug/releases/latest")
            .openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "TagEditor-Updater")
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_NOT_FOUND) return@withContext null
            if (code !in 200..299) throw IOException("GitHub answered with HTTP $code")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            parseRelease(JSONObject(body))
        } finally {
            connection.disconnect()
        }
    }

    private fun JSONObject.text(key: String): String = if (isNull(key)) "" else optString(key)

    private fun parseRelease(json: JSONObject): UpdateInfo? {
        val tag = json.text("tag_name").trim()
        if (tag.isEmpty()) return null
        val assets = json.optJSONArray("assets") ?: return null
        var apk: JSONObject? = null
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            if (asset.text("name").endsWith(".apk", ignoreCase = true)) {
                apk = asset
                break
            }
        }
        val chosen = apk ?: return null
        val downloadUrl = chosen.text("browser_download_url")
        // Only ever download from this repository's own releases.
        if (!downloadUrl.startsWith("https://github.com/$repoSlug/", ignoreCase = true)) return null
        val digest = chosen.text("digest")
        return UpdateInfo(
            version = tag.removePrefix("v").removePrefix("V").trim(),
            title = json.text("name").ifBlank { "Tag Editor $tag" },
            notes = json.text("body").trim(),
            publishedAt = json.text("published_at"),
            pageUrl = json.text("html_url"),
            apkUrl = downloadUrl,
            apkSize = chosen.optLong("size", 0L),
            sha256 = if (digest.startsWith("sha256:", ignoreCase = true)) digest.substring(7).lowercase() else null
        )
    }

    /** Compares dotted version numbers: 1.10.0 is newer than 1.9.3. Suffixes such as -beta are ignored. */
    fun isNewer(remote: String, local: String): Boolean {
        val a = numbers(remote)
        val b = numbers(local)
        for (index in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(index) { 0 }
            val y = b.getOrElse(index) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun numbers(version: String): List<Int> {
        return Regex("\\d+").findAll(version.substringBefore('-'))
            .map { it.value.toIntOrNull() ?: 0 }
            .toList()
    }

    // ---------------------------------------------------------------------------------------
    // Downloading
    // ---------------------------------------------------------------------------------------

    /** Downloads the APK into the app cache and verifies it. [onProgress] gets (bytes done, bytes total). */
    suspend fun download(info: UpdateInfo, onProgress: (Long, Long) -> Unit): File =
        withContext(Dispatchers.IO) {
            updatesDir.mkdirs()
            updatesDir.listFiles()?.forEach { it.delete() }
            val target = File(updatesDir, "TagEditor-${info.version}.apk")
            val partial = File(updatesDir, target.name + ".part")

            val connection = URL(info.apkUrl).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", "TagEditor-Updater")
                connection.setRequestProperty("Accept", "application/octet-stream")
                val code = connection.responseCode
                if (code !in 200..299) throw IOException("Download failed (HTTP $code)")

                val total = if (connection.contentLengthLong > 0) connection.contentLengthLong else info.apkSize
                val hash = MessageDigest.getInstance("SHA-256")
                connection.inputStream.use { input ->
                    FileOutputStream(partial).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var done = 0L
                        var lastReport = 0L
                        while (true) {
                            ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            hash.update(buffer, 0, read)
                            done += read
                            val now = System.nanoTime()
                            if (now - lastReport > 80_000_000L) {
                                onProgress(done, total)
                                lastReport = now
                            }
                        }
                        onProgress(done, total)
                    }
                }

                if (info.apkSize > 0 && partial.length() != info.apkSize) {
                    partial.delete()
                    throw IOException("The download is incomplete. Please try again.")
                }
                val expected = info.sha256
                if (expected != null) {
                    val actual = hash.digest().joinToString("") { "%02x".format(it) }
                    if (!actual.equals(expected, ignoreCase = true)) {
                        partial.delete()
                        throw IOException("The download is corrupted (checksum mismatch).")
                    }
                }
                if (!partial.renameTo(target)) throw IOException("Could not save the update file.")
                verifyPackage(target)
                target
            } finally {
                connection.disconnect()
            }
        }

    @Suppress("DEPRECATION")
    private fun verifyPackage(file: File) {
        val archive = appContext.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
            ?: throw IOException("The downloaded file is not a valid APK.")
        if (archive.packageName != appContext.packageName) {
            file.delete()
            throw IOException("The downloaded APK belongs to a different app.")
        }
    }

    // ---------------------------------------------------------------------------------------
    // Installing
    // ---------------------------------------------------------------------------------------

    fun canInstallPackages(): Boolean {
        return if (Build.VERSION.SDK_INT >= 26) appContext.packageManager.canRequestPackageInstalls() else true
    }

    /** Opens the system screen where the user allows this app to install updates. */
    fun unknownSourcesIntent(): Intent {
        return Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:" + appContext.packageName)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun installIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(appContext, appContext.packageName + ".updates", file)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun pageIntent(url: String): Intent {
        return Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Removes downloaded APKs that are no longer needed. */
    fun cleanup() {
        try {
            updatesDir.listFiles()?.forEach { it.delete() }
        } catch (e: Exception) {
            // ignore
        }
    }
}
