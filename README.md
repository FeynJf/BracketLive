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

## Pengaturan (v1.4)
- **Tampilan:** 5 tema UI (Neon Navy, AMOLED, Slate, Ungu Aurora, Hutan Gelap), 6 warna aksen, saklar animasi
- **Overlay:** toggle, ukuran 70–150%, transparansi panel, lama tahan ikon, 9 pilihan ikon (termasuk foto sendiri) dan 7 desain tombol
- **Panel overlay:** 6 desain (Kaca Hitam, Neon, Solid Tema, Gradien Aksen, Minimal, Kaca Blur), sudut, transparansi, lebar, tinggi, zoom isi; bisa juga diubah dengan menggeser pojok bawah panel
- **Turnamen:** format bracket (Eliminasi penuh / Langsung Semifinal), poin per kill, poin per posisi
- **Koneksi Live:** baca feed JSON custom room secara real-time

## Mode 8 anggota
Setiap game punya mode **CRF System (8 Anggota)** (sistem poin) dan **Eliminasi (8 Anggota)** (bracket).
Roster 8 anggota diisi lewat tombol 👥 di baris tim (mode poin) atau tahan logo tim (mode bracket).

## Koneksi Live (custom room)
Game mobile tidak menyediakan API resmi untuk membaca room dari luar, jadi Bracket Live membaca **feed JSON**
(HTTPS) yang diisi dari tool/bot/spreadsheet panitia. Tim yang ikut terdeteksi otomatis dan poin dihitung
otomatis dari aturan di Pengaturan → Turnamen.

```json
{
  "match": 1,
  "teams": [
    { "name": "EVOS", "kills": 5, "placement": 2, "members": ["A", "B", "C"] },
    { "name": "RRQ",  "kills": 3, "placement": 1 }
  ]
}
```
Poin tim = kills × poin per kill + poin posisi. Nomor `match` yang berbeda dijumlahkan.

## Sertifikat
7 tema warna, 4 bentuk (Landscape, Portrait, Persegi, 16:9), background gambar sendiri, dan tanda tangan
tegak bersambung dari nama panitia.

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
