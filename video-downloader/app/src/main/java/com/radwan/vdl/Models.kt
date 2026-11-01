package com.radwan.vdl

data class VideoItem(
    val id: String,
    val title: String,
    val url: String,
    val thumbnail: String?,
    val durationSec: Long?,
    val selected: Boolean = true,
)

enum class Quality(val label: String, val height: Int?) {
    Q2160("4K", 2160),
    Q1440("1440p", 1440),
    Q1080("1080p", 1080),
    Q720("720p", 720),
    Q480("480p", 480),
    Q360("360p", 360),
    AUDIO("صوت فقط (MP3)", null);

    val isAudio get() = height == null
}

enum class Order(val label: String) {
    ORIGINAL("الترتيب الأصلي"),
    REVERSED("عكسي"),
    MANUAL("يدوي"),
}

enum class Status { QUEUED, RUNNING, DONE, FAILED, CANCELLED }

data class DownloadJob(
    val key: String,
    val item: VideoItem,
    val seq: Int,          // رقم الفيديو في الترتيب النهائي (يُستخدم كبادئة لاسم الملف)
    val seqWidth: Int,     // عدد خانات الترقيم (01, 001 ...)
    val quality: Quality,
    val status: Status = Status.QUEUED,
    val progress: Float = 0f,
    val error: String? = null,
) {
    val prefix get() = seq.toString().padStart(seqWidth, '0')
}
