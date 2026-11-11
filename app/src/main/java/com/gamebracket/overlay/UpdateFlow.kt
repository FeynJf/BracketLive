package com.gamebracket.overlay

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.ProgressBar
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

    private fun showDialog(info: UpdateChecker.Info, cur: Int) {
        val notes = info.notes.trim().take(400)
        val mb = if (info.size > 0) " · %.1f MB".format(info.size / 1048576.0) else ""
        val msg = "Versi baru tersedia: build ${info.build}$mb (kamu: build $cur)." +
            if (notes.isNotEmpty()) "\n\n$notes" else ""
        AlertDialog.Builder(act)
            .setTitle("Update tersedia")
            .setMessage(msg)
            .setPositiveButton("Download & Install") { _, _ -> download(info) }
            .setNegativeButton("Nanti", null)
            .show()
    }

    private fun download(info: UpdateChecker.Info) {
        val bar = ProgressBar(act, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            isIndeterminate = true
        }
        val box = LinearLayout(act).apply {
            setPadding(dp(24), dp(16), dp(24), dp(8))
            addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        val dlg = AlertDialog.Builder(act)
            .setTitle("Mengunduh update…")
            .setView(box)
            .setCancelable(false)
            .create()
        dlg.show()
        Thread {
            try {
                val f = UpdateChecker.download(act, info.apkUrl) { pct ->
                    act.runOnUiThread {
                        if (pct >= 0) {
                            bar.isIndeterminate = false
                            bar.progress = pct
                        }
                    }
                }
                act.runOnUiThread {
                    dlg.dismiss()
                    val problem = UpdateChecker.verify(act, f, currentBuild())
                    if (problem != null) {
                        onStatus(problem)
                        Toast.makeText(act, problem, Toast.LENGTH_LONG).show()
                    } else {
                        install(f)
                    }
                }
            } catch (e: Exception) {
                act.runOnUiThread {
                    dlg.dismiss()
                    onStatus("Gagal mengunduh update: ${e.message}")
                    Toast.makeText(act, "Gagal mengunduh update", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun install(apk: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !act.packageManager.canRequestPackageInstalls()) {
            pendingApk = apk
            AlertDialog.Builder(act)
                .setTitle("Izin install")
                .setMessage("Izinkan Bracket Live untuk memasang aplikasi, lalu kembali ke sini. Update akan dilanjutkan otomatis.")
                .setPositiveButton("Buka Settings") { _, _ ->
                    act.startActivity(
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${act.packageName}"))
                    )
                }
                .setNegativeButton("Batal") { _, _ -> pendingApk = null }
                .show()
            return
        }
        try {
            act.startActivity(UpdateChecker.installIntent(act, apk))
        } catch (e: Exception) {
            onStatus("Gagal membuka installer: ${e.message}")
        }
    }
}
