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
1. Buka Bracket Live
2. Nyalakan toggle **Overlay** di bagian atas
3. Kalau belum ada izin, tekan **Buka Settings** → aktifkan izin → kembali ke app (overlay otomatis menyala)
4. Tulisan di samping toggle berubah menjadi **"Overlay Aktif"**
5. Muncul ikon **B** melayang di layar — geser sesuka hati
6. Ketuk ikon **B** untuk membuka panel, ketuk **—** untuk menutup lagi
7. Matikan overlay dengan toggle di app atau tombol **×** di panel

## 4. Jika masih tidak muncul
- Restart HP setelah kasih izin
- Matikan "Memory freeze" / "Put to sleep" untuk Bracket Live
- Cek Settings → Special app access → Display over other apps → Bracket Live ON
- Pastikan install dari APK

## 5. Tanda service jalan
Notifikasi ongoing "Bracket Live" = service hidup.
Kalau notifikasi tidak ada = service gagal start (izin / battery).
