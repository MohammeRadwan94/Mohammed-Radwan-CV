package com.radwan.vdl

import android.content.Context
import android.webkit.CookieManager
import java.io.File
import java.net.URI

/**
 * يحوّل كوكيز الـ WebView (بعد تسجيل الدخول) إلى ملف Netscape cookies.txt يفهمه yt-dlp.
 * الملف يبقى داخل التخزين الخاص بالتطبيق فقط.
 */
object CookieStore {

    fun file(ctx: Context) = File(ctx.filesDir, "cookies.txt")

    fun hasCookies(ctx: Context) = file(ctx).let { it.exists() && it.length() > 40 }

    fun clear(ctx: Context) {
        file(ctx).delete()
        CookieManager.getInstance().removeAllCookies(null)
    }

    /** يحفظ كوكيز الموقع الحالي، ويستبدل أي كوكيز قديمة لنفس الدومين. */
    fun saveFrom(ctx: Context, siteUrl: String): Boolean {
        val raw = CookieManager.getInstance().getCookie(siteUrl) ?: return false
        val host = URI(siteUrl).host ?: return false
        val domain = "." + host.split('.').takeLast(2).joinToString(".")
        val existing = file(ctx).takeIf { it.exists() }?.readLines()
            ?.filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith(domain) }
            ?: emptyList()
        val fresh = raw.split(';').mapNotNull { pair ->
            val i = pair.indexOf('=')
            if (i <= 0) null else {
                val name = pair.substring(0, i).trim()
                val value = pair.substring(i + 1).trim()
                "$domain\tTRUE\t/\tTRUE\t2147483647\t$name\t$value"
            }
        }
        file(ctx).writeText(
            (listOf("# Netscape HTTP Cookie File") + existing + fresh).joinToString("\n") + "\n"
        )
        return fresh.isNotEmpty()
    }
}
