package com.radwan.vdl

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleShare(intent)
        setContent { MaterialTheme { Root(vm) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    private fun handleShare(i: Intent?) {
        if (i?.action == Intent.ACTION_SEND) {
            val text = i.getStringExtra(Intent.EXTRA_TEXT) ?: return
            Regex("https?://\\S+").find(text)?.value?.let { vm.setUrl(it); vm.go(Screen.HOME) }
        }
    }
}

@Composable
private fun Root(vm: MainViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    BackHandler(enabled = ui.screen != Screen.HOME) { vm.go(Screen.HOME) }

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.systemBarsPadding().padding(16.dp)) {
            when (ui.screen) {
                Screen.HOME -> Home(vm, ui)
                Screen.LIST -> ListScreen(vm, ui)
                Screen.QUEUE -> Queue(vm)
                Screen.LOGIN -> Login(vm)
            }
        }
    }
}

@Composable
private fun Home(vm: MainViewModel, ui: UiState) {
    Text("Video Downloader", style = MaterialTheme.typography.headlineMedium)
    Text("YouTube • Facebook • LinkedIn", style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = ui.url, onValueChange = vm::setUrl, modifier = Modifier.fillMaxWidth(),
        label = { Text("رابط الفيديو أو الـ Playlist") }, singleLine = true,
    )
    Spacer(Modifier.height(12.dp))
    Button(onClick = vm::fetch, enabled = !ui.loading, modifier = Modifier.fillMaxWidth()) {
        Text(if (ui.loading) "جاري التحميل…" else "عرض المحتوى")
    }
    if (ui.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
    ui.message?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
    Spacer(Modifier.height(24.dp))
    HorizontalDivider()
    Spacer(Modifier.height(12.dp))
    Text(
        if (ui.hasCookies) "✅ تم تسجيل الدخول (الكوكيز محفوظة)" else "غير مسجّل — المحتوى الخاص يحتاج تسجيل دخول",
        style = MaterialTheme.typography.bodyMedium,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        OutlinedButton(onClick = { vm.go(Screen.LOGIN) }) { Text("تسجيل الدخول") }
        if (ui.hasCookies) OutlinedButton(onClick = vm::logout) { Text("حذف الكوكيز") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        OutlinedButton(onClick = { vm.go(Screen.QUEUE) }) { Text("التنزيلات") }
        OutlinedButton(onClick = vm::updateEngine, enabled = !ui.loading) { Text("تحديث المحرك") }
    }
}

@Composable
private fun ListScreen(vm: MainViewModel, ui: UiState) {
    Text("${ui.items.size} فيديو • المحدد ${ui.items.count { it.selected }}", style = MaterialTheme.typography.titleMedium)

    Spacer(Modifier.height(8.dp))
    Text("الجودة")
    LazyRowChips(Quality.entries, ui.quality, { it.label }, vm::setQuality)
    Text("الترتيب", modifier = Modifier.padding(top = 8.dp))
    LazyRowChips(Order.entries, ui.order, { it.label }, vm::setOrder)

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        TextButton(onClick = { vm.selectAll(true) }) { Text("تحديد الكل") }
        TextButton(onClick = { vm.selectAll(false) }) { Text("إلغاء التحديد") }
    }

    LazyColumn(Modifier.weight(1f)) {
        itemsIndexed(ui.items, key = { _, v -> v.id }) { idx, v ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = v.selected, onCheckedChange = { vm.toggle(v.id) })
                AsyncImage(
                    model = v.thumbnail, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(width = 96.dp, height = 54.dp).clip(MaterialTheme.shapes.small),
                )
                Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    Text("${idx + 1}. ${v.title}", maxLines = 2, style = MaterialTheme.typography.bodyMedium)
                    v.durationSec?.let { Text(fmt(it), style = MaterialTheme.typography.bodySmall) }
                }
                TextButton(onClick = { vm.move(idx, -1) }, enabled = idx > 0) { Text("↑") }
                TextButton(onClick = { vm.move(idx, +1) }, enabled = idx < ui.items.lastIndex) { Text("↓") }
            }
        }
    }
    ui.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Button(onClick = vm::startDownload, modifier = Modifier.fillMaxWidth()) { Text("بدء التنزيل") }
}

@Composable
private fun <T> LazyRowChips(options: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options) { o ->
            FilterChip(selected = o == selected, onClick = { onPick(o) }, label = { Text(label(o)) })
        }
    }
}

@Composable
private fun Queue(vm: MainViewModel) {
    val jobs by vm.jobs.collectAsStateWithLifecycle()
    Text("التنزيلات (${jobs.count { it.status == Status.DONE }}/${jobs.size})", style = MaterialTheme.typography.titleLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        OutlinedButton(onClick = vm::resume) { Text("إعادة المحاولة") }
        OutlinedButton(onClick = DownloadManager::cancelAll) { Text("إيقاف الكل") }
        OutlinedButton(onClick = DownloadManager::clearFinished) { Text("مسح المنتهي") }
    }
    LazyColumn(Modifier.weight(1f)) {
        items(jobs, key = { it.key }) { j ->
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text("${j.prefix} - ${j.item.title}", maxLines = 2, style = MaterialTheme.typography.bodyMedium)
                when (j.status) {
                    Status.RUNNING -> LinearProgressIndicator(progress = { j.progress }, modifier = Modifier.fillMaxWidth())
                    Status.DONE -> Text("✅ اكتمل — Movies/VideoDownloader", style = MaterialTheme.typography.bodySmall)
                    Status.FAILED -> Text("❌ ${j.error ?: "فشل"}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Status.CANCELLED -> Text("⏹ أُوقف", style = MaterialTheme.typography.bodySmall)
                    Status.QUEUED -> Text("في الانتظار", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private val SITES = listOf(
    "Facebook" to "https://www.facebook.com/login",
    "LinkedIn" to "https://www.linkedin.com/login",
    "YouTube" to "https://accounts.google.com/ServiceLogin?service=youtube&continue=https://www.youtube.com/",
)
private const val DESKTOP_UA =
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun Login(vm: MainViewModel) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var site by remember { mutableStateOf(SITES[0]) }
    var current by remember { mutableStateOf(site.second) }
    var saved by remember { mutableStateOf<String?>(null) }

    Text("سجّل الدخول ثم اضغط «حفظ الجلسة»", style = MaterialTheme.typography.titleMedium)
    LazyRowChips(SITES, site, { it.first }) { site = it; saved = null }
    Spacer(Modifier.height(8.dp))
    key(site) {
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { c ->
                WebView(c).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = DESKTOP_UA
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) { current = url }
                    }
                    loadUrl(site.second)
                }
            },
        )
    }
    saved?.let { Text(it, modifier = Modifier.padding(top = 4.dp)) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        Button(onClick = {
            val base = when (site.first) {
                "Facebook" -> "https://www.facebook.com/"
                "LinkedIn" -> "https://www.linkedin.com/"
                else -> "https://www.youtube.com/"
            }
            val ok = CookieStore.saveFrom(ctx, base) |
                (site.first == "YouTube" && CookieStore.saveFrom(ctx, "https://accounts.google.com/"))
            saved = if (ok) "✅ تم حفظ جلسة ${site.first}" else "لا توجد كوكيز — سجّل الدخول أولاً"
            vm.refreshCookies()
        }) { Text("حفظ الجلسة") }
        OutlinedButton(onClick = { vm.go(Screen.HOME) }) { Text("رجوع") }
    }
}

private fun fmt(sec: Long): String {
    val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
