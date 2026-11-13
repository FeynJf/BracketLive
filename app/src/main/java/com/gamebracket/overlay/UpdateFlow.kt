package com.gamebracket.overlay

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.graphics.Typeface
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.pm.PackageInfoCompat
import java.io.File

/** Alur update di dalam aplikasi: cek rilis → dialog → unduh → install. */
class UpdateFlow(
    private val act: AppCompatActivity,
    private val onStatus: (String) -> Unit
) {
    private var pendingApk: File? = null

    /** Dipanggil setelah cek berhasil: (info build terbaru, build terpasang, apakah ada update). */
    var onResult: ((UpdateChecker.Info, Int, Boolean) -> Unit)? = null

    fun currentBuild(): Int = try {
        PackageInfoCompat.getLongVersionCode(act.packageManager.getPackageInfo(act.packageName, 0)).toInt()
    } catch (e: Exception) { 0 }

    fun versionName(): String = try {
        act.packageManager.getPackageInfo(act.packageName, 0).versionName ?: ""
    } catch (e: Exception) { "" }

    fun check(manual: Boolean) {
        if (manual) onStatus("Mengecek update…")
        Thread {
            val res = try {
                UpdateChecker.fetchLatest()
            } catch (e: Exception) {
                UpdateChecker.Check(null, UpdateChecker.explain(e))
            }
            act.runOnUiThread {
                if (act.isFinishing || act.isDestroyed) return@runOnUiThread
                val cur = currentBuild()
                val info = res.info
                if (info != null) onResult?.invoke(info, cur, info.build > cur)
                when {
                    info == null -> if (manual) onStatus(res.error ?: "Gagal mengecek update")
                    info.build > cur -> {
                        if (manual) onStatus("Update tersedia: build ${info.build}")
                        showDialog(info, cur)
                    }
                    else -> if (manual) onStatus("Sudah versi terbaru (build $cur)")
                }
            }
        }.start()
    }

    /** Panggil dari onResume(): lanjutkan install setelah pengguna memberi izin. */
    fun onResume() {
        pendingApk?.let { apk ->
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || act.packageManager.canRequestPackageInstalls()) {
                pendingApk = null
                install(apk)
            }
        }
    }

    private fun dp(v: Int) = (v * act.resources.displayMetrics.density).toInt()

    private fun col(id: Int) = androidx.core.content.ContextCompat.getColor(act, id)

    private fun label(text: String) = TextView(act, null, 0, R.style.SectionLabel).apply {
        this.text = text
        setPadding(0, dp(14), 0, dp(6))
    }

    /** Kotak kecil: judul kecil + nilai besar. */
    private fun versionChip(caption: String, value: String, accent: Boolean): View {
        val box = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(8), dp(14), dp(8))
            setBackgroundResource(if (accent) R.drawable.bg_chip_active else R.drawable.bg_chip)
        }
        box.addView(TextView(act).apply {
            text = caption
            textSize = 8f
            letterSpacing = 0.15f
            setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD))
            setTextColor(if (accent) col(R.color.on_accent) else col(R.color.muted))
        })
        box.addView(TextView(act).apply {
            text = value
            textSize = 15f
            setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD))
            setTextColor(if (accent) col(R.color.on_accent) else col(R.color.text))
        })
        return box
    }

    /** Daftar "yang baru": baris pertama judul versi, sisanya poin dengan penanda berwarna. */
    private fun notesView(raw: String): View {
        val col = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        val accent = ThemeManager.accent1(act)
        UpdateChecker.prettyNotes(raw).lines().filter { it.isNotBlank() }.forEachIndexed { i, line ->
            val bullet = line.startsWith("•")
            val row = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, if (i == 0) 0 else dp(5), 0, 0) }
            if (bullet) {
                row.addView(TextView(act).apply { text = "▸"; textSize = 12f; setTextColor(accent); setPadding(0, 0, dp(8), 0) })
            }
            row.addView(TextView(act).apply {
                text = if (bullet) line.removePrefix("•").trim() else line
                textSize = if (bullet) 12.5f else 13f
                setLineSpacing(0f, 1.15f)
                setTextColor(if (bullet) col(R.color.text) else accent)
                if (!bullet) setTypeface(typeface, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            col.addView(row)
        }
        return col
    }

    private fun showDialog(info: UpdateChecker.Info, cur: Int) {
        val d = PremiumDialog(act).title("UPDATE TERSEDIA", "BRACKET LIVE · BUILD ${info.build}")

        // versi terpasang ➜ versi terbaru (+ ukuran)
        val row = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(versionChip("TERPASANG", "build $cur", false))
        row.addView(TextView(act).apply {
            text = "➜"
            textSize = 16f
            setTextColor(ThemeManager.accent1(act))
            setPadding(dp(10), 0, dp(10), 0)
        })
        row.addView(versionChip("TERBARU", "build ${info.build}", true))
        if (info.size > 0) {
            row.addView(TextView(act).apply {
                text = "%.1f MB".format(info.size / 1048576.0)
                textSize = 11f
                setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD))
                setTextColor(col(R.color.muted_light))
                setPadding(dp(12), 0, 0, 0)
            })
        }
        d.body.addView(row)

        val notes = UpdateChecker.prettyNotes(info.notes)
        if (notes.isNotBlank()) {
            d.body.addView(label("YANG BARU"))
            val scroll = MaxHeightScrollView(act).apply {
                maxHeightPx = dp(220)
                isVerticalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                setPadding(dp(12), dp(10), dp(12), dp(10))
                setBackgroundResource(R.drawable.bg_chip)
                addView(notesView(info.notes))
            }
            d.body.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        d.primary("⬇  Download & Install") { download(info); true }
        d.secondary("Nanti")
        d.show()
    }

    private fun download(info: UpdateChecker.Info) {
        val d = PremiumDialog(act).title("MENGUNDUH UPDATE", "JANGAN TUTUP APLIKASI").cancelable(false)

        val pct = TextView(act).apply {
            text = "0%"
            textSize = 42f
            gravity = Gravity.CENTER
            setTextColor(ThemeManager.accent1(act))
            setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL))
        }
        val bar = NeonProgressView(act)
        val status = TextView(act).apply {
            text = "Menyambung ke GitHub…"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(col(R.color.muted_light))
            setPadding(0, dp(12), 0, 0)
        }
        val detail = TextView(act).apply {
            text = ""
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(col(R.color.muted))
            setPadding(0, dp(4), 0, 0)
        }
        d.body.addView(pct)
        d.body.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(14)).apply { topMargin = dp(8) })
        d.body.addView(status)
        d.body.addView(detail)

        var cancelled = false
        d.secondary("Batalkan") { cancelled = true; true }
        d.show()
        if (Anim.enabled(act)) Anim.pop(pct)

        val t0 = System.currentTimeMillis()
        var lastUi = 0L
        Thread {
            try {
                val f = UpdateChecker.download(act, info.apkUrl, { cancelled }) { p, done, total ->
                    val now = System.currentTimeMillis()
                    if (now - lastUi < 120 && p in 1..99) return@download
                    lastUi = now
                    val secs = ((now - t0) / 1000.0).coerceAtLeast(0.3)
                    val speed = done / secs
                    act.runOnUiThread {
                        if (p >= 0) {
                            pct.text = "$p%"
                            bar.setProgress(p / 100f)
                        }
                        status.text = "Mengunduh…"
                        val mb = { v: Long -> "%.1f".format(v / 1048576.0) }
                        detail.text = if (total > 0) "${mb(done)} / ${mb(total)} MB  ·  ${"%.1f".format(speed / 1048576.0)} MB/s"
                        else "${mb(done)} MB  ·  ${"%.1f".format(speed / 1048576.0)} MB/s"
                    }
                }
                act.runOnUiThread {
                    pct.text = "100%"
                    bar.setProgress(1f)
                    status.text = "Memverifikasi berkas…"
                    val problem = UpdateChecker.verify(act, f, currentBuild())
                    if (problem != null) {
                        showFailure(d, info, problem)
                    } else {
                        status.text = "✓ Berhasil diunduh & diverifikasi"
                        status.setTextColor(col(R.color.green))
                        detail.text = "Layar pemasangan akan terbuka"
                        d.title("UPDATE SIAP", "TINGGAL PASANG")
                        d.primary("Pasang Sekarang") { install(f); false }
                        d.secondary("Tutup") { true }
                        d.dialog.setCancelable(true)
                        Anim.pop(status)
                        install(f)
                    }
                }
            } catch (e: UpdateChecker.DownloadCancelled) {
                act.runOnUiThread { d.dismiss(); onStatus("Unduhan dibatalkan") }
            } catch (e: Exception) {
                CrashLog.record(act, "download update", e)
                act.runOnUiThread {
                    val msg = when (e) {
                        is java.net.UnknownHostException -> "Tidak ada koneksi internet"
                        is java.net.SocketTimeoutException -> "Koneksi terlalu lambat, coba lagi"
                        else -> e.message ?: "Terjadi kesalahan"
                    }
                    showFailure(d, info, msg)
                }
            }
        }.start()
    }

    /** Ubah dialog download jadi status gagal dengan tombol Coba Lagi. */
    private fun showFailure(d: PremiumDialog, info: UpdateChecker.Info, reason: String) {
        onStatus("Gagal mengunduh update: $reason")
        d.dismiss()
        val f = PremiumDialog(act).title("GAGAL MENGUNDUH", "UPDATE BELUM TERPASANG")
        f.message(reason).setTextColor(col(R.color.red))
        f.primary("Coba Lagi") { download(info); true }
        f.secondary("Tutup")
        f.show()
    }

    private fun install(apk: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !act.packageManager.canRequestPackageInstalls()) {
            pendingApk = apk
            PremiumDialog.message(
                act, "IZIN INSTALL", "SATU KALI SAJA",
                "Izinkan Bracket Live untuk memasang aplikasi, lalu kembali ke sini. Update akan dilanjutkan otomatis.",
                "Buka Settings", "Batal",
                onPositive = {
                    act.startActivity(
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${act.packageName}"))
                    )
                },
                onNegative = { pendingApk = null }
            )
            return
        }
        try {
            act.startActivity(UpdateChecker.installIntent(act, apk))
        } catch (e: Exception) {
            CrashLog.record(act, "buka installer", e)
            onStatus("Gagal membuka installer: ${e.message}")
        }
    }
}
