# Bracket Live (Native Android)

Aplikasi bracket turnamen untuk live streaming — **Kotlin + XML**, tanpa HTML/WebView.

## Fitur
- Tampilan bertema gamer/esports (dark neon)
- Pilih game & mode: Free Fire, PUBG Mobile, Mobile Legends, COD Mobile, Valorant, dll.
- Bracket eliminasi 4/8/16 slot dengan format BO1/BO3/BO5
- Ranking Battle Royale
- **Pengaturan** (ikon di kanan atas): toggle Overlay, transparansi panel, lama tahan ikon, dan Cek Update
- **Overlay berbentuk ikon "B"** yang bisa digeser, hanya muncul saat kamu di luar aplikasi; **tahan** ikon untuk membuka panel
  - Edit nama tim, tambah/kurangi skor, tentukan pemenang langsung dari overlay
  - Panel hitam transparan; ketuk **—** untuk menutup kembali jadi ikon
- Logo untuk setiap tim (ketuk lingkaran logo di app, ambil dari galeri)
- Sertifikat juara yang bisa diedit sebelum disimpan (teks, logo, tema, tanda tangan tegak bersambung)
- Update otomatis: app mengecek rilis terbaru di GitHub lalu mengarahkan Download & Install

## Update otomatis
Workflow `.github/workflows/build-apk.yml` membuat GitHub Release `build-<nomor>` berisi APK di setiap build.
Saat aplikasi dibuka, nomor build di app dibandingkan dengan rilis terbaru; kalau ada yang lebih baru
muncul dialog **Download & Install**. Bisa juga dicek manual lewat Pengaturan → Cek Update.

## Build di GitHub Actions
1. Upload seluruh isi folder ini ke repo (settings.gradle harus di root)
2. Actions → **Build APK** → Run workflow
3. Download artifact APK

## Build di Android Studio
1. File → Open → pilih folder yang ada `settings.gradle`
2. Tunggu Gradle Sync
3. Run ▶ ke HP

## Overlay
Settings → App management → Bracket Live → Display over other apps = ON,
lalu nyalakan toggle **Overlay** di Pengaturan aplikasi.
