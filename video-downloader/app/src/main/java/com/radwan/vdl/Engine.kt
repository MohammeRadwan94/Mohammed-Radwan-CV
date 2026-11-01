package com.radwan.vdl

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/** غلاف رفيع حول yt-dlp. كل منطق المواقع (YouTube / Facebook / LinkedIn) داخل yt-dlp نفسه. */
object Engine {

    fun init(app: Context) {
        YoutubeDL.getInstance().init(app)
        FFmpeg.getInstance().init(app)
    }

    /** يحدّث yt-dlp لآخر إصدار. مهم لأن المواقع تكسر الـ extractors باستمرار. */
    suspend fun update(app: Context): String = withContext(Dispatchers.IO) {
        val status = YoutubeDL.getInstance().updateYoutubeDL(app, YoutubeDL.UpdateChannel.STABLE)
        status?.name ?: "UNKNOWN"
    }

    /** يجلب عناصر الرابط: Playlist (بالترتيب الأصلي) أو فيديو مفرد. */
    suspend fun fetch(url: String, cookies: File?): List<VideoItem> = withContext(Dispatchers.IO) {
        val req = YoutubeDLRequest(url).apply {
            addOption("-J")
            addOption("--flat-playlist")
            addOption("--no-warnings")
            if (cookies != null && cookies.exists()) addOption("--cookies", cookies.absolutePath)
        }
        val out = YoutubeDL.getInstance().execute(req).out
        parse(out, url)
    }

    private fun parse(json: String, originalUrl: String): List<VideoItem> {
        val root = JSONObject(json)
        val entries = root.optJSONArray("entries")
        if (entries == null) return listOf(toItem(root, originalUrl))
        val list = ArrayList<VideoItem>(entries.length())
        for (i in 0 until entries.length()) {
            val e = entries.optJSONObject(i) ?: continue
            list += toItem(e, null)
        }
        return list
    }

    private fun toItem(o: JSONObject, fallbackUrl: String?): VideoItem {
        val id = o.optString("id")
        val url = o.optString("webpage_url").ifBlank { o.optString("url") }
            .ifBlank { fallbackUrl ?: "" }
            .let { if (it.startsWith("http")) it else "https://www.youtube.com/watch?v=$id" }
        val thumb = o.optString("thumbnail").ifBlank {
            o.optJSONArray("thumbnails")?.let { t ->
                if (t.length() > 0) t.optJSONObject(t.length() - 1)?.optString("url") else null
            }
        }
        return VideoItem(
            id = id.ifBlank { url },
            title = o.optString("title").ifBlank { "بدون عنوان" },
            url = url,
            thumbnail = thumb?.ifBlank { null },
            durationSec = if (o.has("duration") && !o.isNull("duration")) o.optLong("duration") else null,
        )
    }

    /** ينزّل فيديو واحداً إلى مجلد مؤقت ويعيد الملف الناتج. */
    suspend fun download(
        app: Context,
        job: DownloadJob,
        cookies: File?,
        onProgress: (Float) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val dir = File(app.cacheDir, "dl/${job.key}").apply { deleteRecursively(); mkdirs() }
        val req = YoutubeDLRequest(job.item.url).apply {
            addOption("--no-playlist")
            addOption("--windows-filenames")
            addOption("-o", "${dir.absolutePath}/${job.prefix} - %(title).120B.%(ext)s")
            if (cookies != null && cookies.exists()) addOption("--cookies", cookies.absolutePath)
            val h = job.quality.height
            if (h == null) {
                addOption("-x")
                addOption("--audio-format", "mp3")
                addOption("-f", "ba/b")
            } else {
                addOption("-f", "bv*[height<=$h]+ba/b[height<=$h]/b")
                addOption("--merge-output-format", "mp4")
            }
        }
        YoutubeDL.getInstance().execute(req, job.key) { progress, _, _ ->
            if (progress >= 0) onProgress(progress / 100f)
        }
        dir.listFiles { f -> f.isFile && !f.name.endsWith(".part") && !f.name.endsWith(".ytdl") }
            ?.maxByOrNull { it.length() }
            ?: error("لم يتم العثور على الملف الناتج")
    }

    fun cancel(key: String) {
        YoutubeDL.getInstance().destroyProcessById(key)
    }

    /** ينقل الملف إلى Movies/VideoDownloader أو Music/VideoDownloader عبر MediaStore. */
    fun exportToGallery(app: Context, file: File, audio: Boolean) {
        val collection = if (audio) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val folder = if (audio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES
        val mime = when (file.extension.lowercase()) {
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            else -> if (audio) "audio/*" else "video/mp4"
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$folder/VideoDownloader")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = app.contentResolver
        val uri = resolver.insert(collection, values) ?: error("تعذّر الحفظ في المعرض")
        resolver.openOutputStream(uri)!!.use { out -> file.inputStream().use { it.copyTo(out) } }
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        file.parentFile?.deleteRecursively()
    }
}
