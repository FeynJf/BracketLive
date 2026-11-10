# Game Bracket (Native Android)

Aplikasi bracket turnamen — **Kotlin + XML**, tanpa HTML/WebView.

## UI di dalam app
- Pilih game & mode
- Bracket eliminasi BO1/BO3/BO5
- Ranking Battle Royale
- Tombol **Overlay** (mengambang di atas app lain)
- Sertifikat juara → Galeri

## Build di GitHub Actions
1. Buat repo baru di GitHub
2. Upload **seluruh isi** folder ini (settings.gradle harus di root repo)
3. Actions → **Build APK** → Run workflow
4. Download artifact **GameBracket-APK**

## Build di Android Studio
1. Extract project
2. File → Open → pilih folder yang ada `settings.gradle`
3. Tunggu Gradle Sync
4. Run ▶ ke HP

## Overlay Realme
Settings → App management → Game Bracket → Display over other apps = ON  
Lalu di app tekan tombol **Overlay**.
