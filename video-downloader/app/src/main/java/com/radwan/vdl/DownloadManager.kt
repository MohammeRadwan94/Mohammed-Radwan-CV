package com.radwan.vdl

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * طابور تنزيل بسيط: ينفّذ المهام بالتتابع حسب الترتيب النهائي (للحفاظ على ترتيب الـ Playlist
 * وتقليل خطر الحظر من المواقع). الحالة في StateFlow ليراها الـ UI والـ Service.
 */
object DownloadManager {
    private val _jobs = MutableStateFlow<List<DownloadJob>>(emptyList())
    val jobs: StateFlow<List<DownloadJob>> = _jobs

    @Volatile private var cancelAll = false

    fun enqueue(items: List<VideoItem>, quality: Quality) {
        val width = maxOf(2, items.size.toString().length)
        val batch = System.currentTimeMillis()
        val new = items.mapIndexed { i, v ->
            DownloadJob("${batch}_$i", v, i + 1, width, quality)
        }
        _jobs.update { it + new }
    }

    fun clearFinished() = _jobs.update { list ->
        list.filter { it.status == Status.QUEUED || it.status == Status.RUNNING }
    }

    fun cancelAll() {
        cancelAll = true
        _jobs.value.filter { it.status == Status.RUNNING }.forEach { Engine.cancel(it.key) }
        _jobs.update { list -> list.map { if (it.status == Status.QUEUED) it.copy(status = Status.CANCELLED) else it } }
    }

    fun retryFailed() = _jobs.update { list ->
        list.map { if (it.status == Status.FAILED || it.status == Status.CANCELLED) it.copy(status = Status.QUEUED, progress = 0f, error = null) else it }
    }

    private fun patch(key: String, f: (DownloadJob) -> DownloadJob) =
        _jobs.update { list -> list.map { if (it.key == key) f(it) else it } }

    /** يعمل داخل الـ Foreground Service. onUpdate يحدّث نص الإشعار. */
    suspend fun runAll(ctx: Context, onUpdate: (done: Int, total: Int, current: String?) -> Unit) {
        cancelAll = false
        val cookies = CookieStore.file(ctx)
        while (!cancelAll) {
            val next = _jobs.value.firstOrNull { it.status == Status.QUEUED } ?: break
            val all = _jobs.value
            onUpdate(all.count { it.status == Status.DONE }, all.size, next.item.title)
            patch(next.key) { it.copy(status = Status.RUNNING, progress = 0f) }
            try {
                val file = Engine.download(ctx, next, cookies) { p -> patch(next.key) { it.copy(progress = p) } }
                Engine.exportToGallery(ctx, file, next.quality.isAudio)
                patch(next.key) { it.copy(status = Status.DONE, progress = 1f) }
            } catch (e: CancellationException) {
                patch(next.key) { it.copy(status = Status.CANCELLED) }
                throw e
            } catch (e: Exception) {
                patch(next.key) {
                    if (cancelAll) it.copy(status = Status.CANCELLED)
                    else it.copy(status = Status.FAILED, error = e.message?.take(300))
                }
            }
        }
    }
}
