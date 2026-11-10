# Overlay tidak muncul di Realme — checklist

## 1. Build ulang APK
Android Studio → Build → Build APK → install ke HP (uninstall app lama dulu).

## 2. Izin wajib (semua harus ON)
Settings → App management → **Game Bracket**
- Display over other apps / Tampil di atas layer lain → **ON**
- Autostart → **ON**
- Battery usage → **Allow background activity** / jangan dioptimize
- Notifications → **ON**

## 3. Urutan pakai
1. Buka Game Bracket
2. Tekan **1. Izinkan Tampil di Atas Aplikasi** → aktifkan switch
3. Kembali ke app — status harus ✅ Izin OK
4. Tekan **2. Mulai Overlay**
5. Harus muncul Toast: "Overlay aktif"
6. Harus ada notifikasi: "Game Bracket — Overlay berjalan"
7. Bar pink "Game Bracket — geser di sini" harus kelihatan di layar
8. Buka TikTok — bar pink tetap di atas

## 4. Jika masih tidak muncul
- Restart HP setelah kasih izin
- Matikan "Memory freeze" / "Put to sleep" untuk Game Bracket
- Cek Settings → Special app access → Display over other apps → Game Bracket ON
- Pastikan bukan install dari HTML browser, harus APK

## 5. Tanda service jalan
Notifikasi ongoing "Game Bracket" = service hidup.
Kalau notifikasi tidak ada = service gagal start (izin / battery).
