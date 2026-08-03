# YT Lite (youtubeview)

Native Android WebView wrapper for m.youtube.com — no address bar, single-tab feel,
ad-blocking, background audio/video playback, and Picture-in-Picture.

## Apa yang diperbaiki (dari versi awal)

1. **Icon aplikasi hilang** — manifest mereferensikan `@mipmap/ic_launcher` tapi filenya
   tidak ada di project (build pasti gagal). Sudah ditambahkan launcher icon untuk semua
   density + adaptive icon (API 26+).
2. **Gradle wrapper tidak lengkap** — hanya ada `gradle-wrapper.properties`, tanpa
   `gradlew`, `gradlew.bat`, dan `gradle-wrapper.jar`. Sudah dilengkapi supaya bisa build
   langsung lewat command line (`./gradlew assembleRelease`) tanpa buka Android Studio dulu.
3. **Video fullscreen tidak jalan** — `onShowCustomView`/`onHideCustomView` di
   `WebChromeClient` dikosongkan, jadi tombol fullscreen di video player tidak berfungsi.
   Sekarang ada container khusus (`fullscreenContainer`) yang menampilkan custom view dan
   otomatis rotate ke landscape.
4. **Back button** — pindah dari `onBackPressed()` (deprecated, konflik dengan predictive-back
   gesture Android 13+/14) ke `OnBackPressedCallback`. Back sekarang juga keluar dari
   fullscreen video dulu sebelum navigasi mundur di WebView.
5. **Ad-block diperluas** — daftar domain iklan ditambah, termasuk `imasdk.googleapis.com`
   (Google IMA SDK) yang menyajikan iklan video pre-roll/mid-roll di web player, plus
   beberapa endpoint tracking YouTube lainnya.
6. **Notification permission (Android 13+)** — `POST_NOTIFICATIONS` sudah dideklarasikan di
   manifest tapi tidak pernah diminta saat runtime, padahal wajib untuk targetSdk 34.
   Sekarang di-request otomatis saat app dibuka.
7. **Audio focus** — service background sekarang benar-benar request `AudioFocus` supaya
   playback tidak konflik dengan aplikasi media lain, dan melepasnya saat service berhenti.
8. **Immersive / edge-to-edge UI** — status bar & navigation bar disembunyikan (bisa muncul
   sementara dengan swipe) supaya benar-benar terasa seperti app native, bukan browser.
9. **Kode mati dihapus** — `YoutubeWebViewClient.kt` tidak pernah dipakai (MainActivity punya
   `WebViewClient` sendiri secara inline), jadi dihapus supaya tidak membingungkan.
10. **`onCreateWindow`/link eksternal** — navigasi selain domain YouTube & Google accounts
    diblokir dari membajak WebView (mencegah redirect ke browser luar / "buka di app lain").
11. **`onDestroy`** — service background sekarang dihentikan saat activity benar-benar
    ditutup, supaya tidak jadi notification "nyangkut" setelah app di-force close.

## Catatan penting

- **Iklan di dalam video (pre-roll/mid-roll) yang datang dari domain video YouTube sendiri**
  (bukan `imasdk.googleapis.com`) kadang tidak bisa diblokir 100% tanpa merusak playback
  video utama, karena berbagi CDN yang sama. Ad-block di sini menutup jalur ad-request yang
  paling umum, tapi bukan jaminan zero-ads mutlak selamanya (YouTube sering ubah endpoint).
- App ini **tidak login/pakai akun Google native** — cookie & login YouTube tersimpan di
  dalam WebView (via `domStorageEnabled`), jadi login tetap bisa lewat browser di dalam app.
- `applicationId` masih `com.youtube.webview` dan nama app "YT Lite" — ganti di
  `app/build.gradle.kts` dan `strings.xml` kalau mau publish/rename.

## Build APK

Butuh Android SDK ter-install (lewat Android Studio, atau `sdkmanager` command line) dan
Java 17.

```bash
cd youtubeview-main
./gradlew assembleDebug      # -> app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease    # -> app/build/outputs/apk/release/app-release-unsigned.apk (perlu di-sign)
```

Atau paling gampang: buka folder ini di **Android Studio** → biarkan Gradle sync →
`Build > Build Bundle(s) / APK(s) > Build APK(s)`.

Untuk release APK yang siap install (signed), butuh keystore sendiri — Android Studio bisa
generate ini lewat `Build > Generate Signed Bundle / APK`.
