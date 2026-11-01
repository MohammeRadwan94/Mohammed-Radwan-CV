package com.radwan.vdl

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Screen { HOME, LIST, QUEUE, LOGIN }

data class UiState(
    val screen: Screen = Screen.HOME,
    val url: String = "",
    val loading: Boolean = false,
    val message: String? = null,
    val items: List<VideoItem> = emptyList(),
    val quality: Quality = Quality.Q1080,
    val order: Order = Order.ORIGINAL,
    val hasCookies: Boolean = false,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val _ui = MutableStateFlow(UiState(hasCookies = CookieStore.hasCookies(app)))
    val ui: StateFlow<UiState> = _ui.asStateFlow()
    val jobs = DownloadManager.jobs

    private var original: List<VideoItem> = emptyList()

    fun setUrl(u: String) = _ui.update { it.copy(url = u) }
    fun go(s: Screen) = _ui.update { it.copy(screen = s, message = null) }
    fun setQuality(q: Quality) = _ui.update { it.copy(quality = q) }
    fun refreshCookies() = _ui.update { it.copy(hasCookies = CookieStore.hasCookies(getApplication())) }

    fun logout() {
        CookieStore.clear(getApplication())
        refreshCookies()
    }

    fun fetch() {
        val url = _ui.value.url.trim()
        if (!url.startsWith("http")) {
            _ui.update { it.copy(message = "الصق رابطاً صحيحاً يبدأ بـ http") }
            return
        }
        _ui.update { it.copy(loading = true, message = null) }
        viewModelScope.launch {
            try {
                val items = Engine.fetch(url, CookieStore.file(getApplication()))
                original = items
                _ui.update { it.copy(loading = false, items = items, order = Order.ORIGINAL, screen = Screen.LIST) }
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, message = "فشل جلب الرابط: ${e.message?.take(300)}") }
            }
        }
    }

    fun setOrder(o: Order) = _ui.update {
        val items = when (o) {
            Order.ORIGINAL -> original.map { v -> it.items.firstOrNull { x -> x.id == v.id } ?: v }
            Order.REVERSED -> original.reversed().map { v -> it.items.firstOrNull { x -> x.id == v.id } ?: v }
            Order.MANUAL -> it.items
        }
        it.copy(order = o, items = items)
    }

    fun toggle(id: String) = _ui.update { s ->
        s.copy(items = s.items.map { if (it.id == id) it.copy(selected = !it.selected) else it })
    }

    fun selectAll(value: Boolean) = _ui.update { s -> s.copy(items = s.items.map { it.copy(selected = value) }) }

    fun move(index: Int, delta: Int) = _ui.update { s ->
        val to = index + delta
        if (to !in s.items.indices) s
        else s.copy(
            order = Order.MANUAL,
            items = s.items.toMutableList().also { l -> l.add(to, l.removeAt(index)) },
        )
    }

    fun startDownload() {
        val s = _ui.value
        val chosen = s.items.filter { it.selected }
        if (chosen.isEmpty()) {
            _ui.update { it.copy(message = "اختر فيديو واحداً على الأقل") }
            return
        }
        DownloadManager.enqueue(chosen, s.quality)
        DownloadService.start(getApplication())
        go(Screen.QUEUE)
    }

    fun resume() {
        DownloadManager.retryFailed()
        DownloadService.start(getApplication())
    }

    fun updateEngine() {
        _ui.update { it.copy(loading = true, message = "جاري تحديث yt-dlp…") }
        viewModelScope.launch {
            val msg = try {
                "تم التحديث: ${Engine.update(getApplication())}"
            } catch (e: Exception) {
                "فشل التحديث: ${e.message?.take(200)}"
            }
            _ui.update { it.copy(loading = false, message = msg) }
        }
    }
}
