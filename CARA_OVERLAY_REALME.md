# Overlay tidak muncul di Realme — checklist

## 1. Build ulang APK
Android Studio → Build → Build APK → install ke HP (uninstall app lama dulu).

## 2. Izin wajib (semua harus ON)
Settings → App management → **Bracket Live**
- Display over other apps / Tampil di atas layer lain → **ON**
- Autostart → **ON**
- Battery usage → **Allow background activity** / jangan dioptimize
- Notifications → **ON**

## 3. Urutan pakai
1. Buka Bracket Live → ketuk ikon **pengaturan** (kanan atas)
2. Nyalakan toggle **Overlay**
3. Kalau belum ada izin, tekan **Buka Settings** → aktifkan izin → kembali ke app (overlay otomatis menyala)
4. Tulisan di samping toggle berubah menjadi **"Overlay Aktif"**
5. **Keluar dari aplikasi** (Home) — ikon **B** akan muncul melayang. Saat kamu di dalam app, ikon sengaja disembunyikan
6. **Tahan** ikon B sampai cincin penuh untuk membuka panel (ketukan singkat tidak membuka, supaya aman saat live)
7. Ketuk **—** untuk menutup panel kembali jadi ikon, ikon **◐** untuk mengubah transparansi
8. Matikan overlay dengan toggle di Pengaturan atau tombol **×** di panel

Di Pengaturan kamu juga bisa mengatur **transparansi panel** dan **lama menahan ikon**.

## 4. Jika masih tidak muncul
- Restart HP setelah kasih izin
- Matikan "Memory freeze" / "Put to sleep" untuk Bracket Live
- Cek Settings → Special app access → Display over other apps → Bracket Live ON
- Pastikan install dari APK

## 5. Tanda service jalan
Notifikasi ongoing "Bracket Live" = service hidup.
Kalau notifikasi tidak ada = service gagal start (izin / battery).
