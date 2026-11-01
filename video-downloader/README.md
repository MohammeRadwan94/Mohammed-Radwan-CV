# Video Downloader (Android) — استخدام شخصي

تطبيق Android (Kotlin + Jetpack Compose) فوق `yt-dlp` عبر `youtubedl-android`.

## المزايا (MVP)
- لصق رابط أو Share من أي تطبيق: فيديو مفرد أو Playlist (YouTube) أو فيديو Facebook / LinkedIn.
- عرض الـ Playlist بالترتيب الأصلي مع اختيار فيديوهات محددة.
- جودة: 4K حتى 360p أو صوت فقط MP3.
- ترتيب: أصلي / عكسي / يدوي (↑ ↓). الملفات تُرقَّم (`01 - Title.mp4`) حسب الترتيب النهائي.
- تسجيل دخول (WebView) لحفظ الكوكيز للمحتوى الخاص — تبقى داخل التطبيق فقط.
- تنزيل متتابع في الخلفية (Foreground Service)، الحفظ في `Movies/VideoDownloader`.
- زر «تحديث المحرك» لتحديث yt-dlp عند تعطّل موقع.

## البناء
لا يلزمك Android Studio: GitHub Actions يبني الـ APK (تبويب Actions ← Artifacts).
محلياً: `./gradlew assembleDebug` (يتطلب JDK 17 و Android SDK).

## ملاحظات
- minSdk 29 (Android 10+).
- للاستخدام الشخصي فقط (sideload)؛ غير مناسب لـ Google Play.
- التزم بشروط استخدام المواقع وحقوق المحتوى.
