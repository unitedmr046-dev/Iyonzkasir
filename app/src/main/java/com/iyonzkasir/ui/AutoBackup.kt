package com.iyonzkasir.ui

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.iyonzkasir.IyonzApp
import com.iyonzkasir.data.SettingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

// ═══════════════════════════════════════════════════════════
// MENU PHOTO MANAGER
// Simpan foto menu di internal storage (bisa di-backup)
// ═══════════════════════════════════════════════════════════
object MenuPhotoManager {
    private const val DIR_NAME = "menu_photos"

    private fun photosDir(context: Context): File {
        val d = File(context.filesDir, DIR_NAME)
        if (!d.exists()) d.mkdirs()
        return d
    }

    /** Copy foto dari content:// ke internal. Return path baru. */
    suspend fun copyToInternal(
        context: Context, sourceUri: Uri, menuId: Long
    ): String? = withContext(Dispatchers.IO) {
        try {
            val mime = context.contentResolver.getType(sourceUri)
            val ext = when {
                mime == null -> "jpg"
                mime.contains("png") -> "png"
                mime.contains("webp") -> "webp"
                mime.contains("jpeg") || mime.contains("jpg") -> "jpg"
                else -> "jpg"
            }
            val fname = "menu_${menuId}_${System.currentTimeMillis()}.$ext"
            val target = File(photosDir(context), fname)
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(target).use { output ->
                    input.copyTo(output)
                }
            }
            target.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    /** Hapus foto dari internal (kalau path valid di menu_photos). */
    fun deleteByPath(path: String?) {
        if (path.isNullOrBlank()) return
        if (!path.contains(DIR_NAME)) return // jangan hapus foto galeri user
        try {
            val f = File(path)
            if (f.exists() && f.isFile) f.delete()
        } catch (_: Exception) {}
    }

    /** Bersihkan foto yang nggak dipakai lagi (orphan). */
    suspend fun cleanupOrphans(context: Context, usedPaths: Set<String>) =
        withContext(Dispatchers.IO) {
            try {
                photosDir(context).listFiles()?.forEach { f ->
                    if (f.absolutePath !in usedPaths) f.delete()
                }
            } catch (_: Exception) {}
        }

    fun isInternalPath(path: String?): Boolean =
        !path.isNullOrBlank() && path.contains(DIR_NAME)
}

// ═══════════════════════════════════════════════════════════
// AUTO BACKUP WORKER
// ═══════════════════════════════════════════════════════════
class AutoBackupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val app = ctx.applicationContext as? IyonzApp ?: return Result.failure()

        return try {
            // Cek interval: minimal 24 jam sejak backup terakhir
            val lastTs = app.settingRepo.getLastBackupTimestamp()
            val now = System.currentTimeMillis()
            val dayMs = 24L * 60 * 60 * 1000
            if (now - lastTs < dayMs) {
                return Result.success()
            }

            // Backup ke internal
            val dir = File(ctx.filesDir, "auto_backups")
            dir.mkdirs()
            val fname = "auto-backup-${now}.iyonz"
            val target = File(dir, fname)

            FileOutputStream(target).use { out ->
                val r = BackupService.backup(ctx, AUTO_PASSWORD, out)
                if (r.isFailure) {
                    target.delete()
                    return Result.retry()
                }
            }

            // Update setting
            app.settingRepo.setLastBackupTimestamp(now)
            app.settingRepo.setLastBackupName(fname)

            // Rotasi: simpan 7 terakhir
            rotateBackups(dir, 7)

            // Notif
            BackupNotifier.notifySuccess(ctx, target)

            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun rotateBackups(dir: File, keepCount: Int) {
        try {
            val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".iyonz") }
                ?.sortedByDescending { it.lastModified() } ?: return
            files.drop(keepCount).forEach { it.delete() }
        } catch (_: Exception) {}
    }

    companion object {
        const val AUTO_PASSWORD = "iyonz-autobak-internal-2025"
        const val UNIQUE_NAME = "iyonzkasir-auto-backup"

        /** Ambil file auto backup terbaru (untuk share manual). */
        fun latestAutoBackup(context: Context): File? {
            val dir = File(context.filesDir, "auto_backups")
            return dir.listFiles { f -> f.isFile && f.name.endsWith(".iyonz") }
                ?.maxByOrNull { it.lastModified() }
        }

        fun getAllAutoBackups(context: Context): List<File> {
            val dir = File(context.filesDir, "auto_backups")
            return dir.listFiles { f -> f.isFile && f.name.endsWith(".iyonz") }
                ?.sortedByDescending { it.lastModified() }
                ?: emptyList()
        }
    }
}

// ═══════════════════════════════════════════════════════════
// BACKUP SCHEDULER
// ═══════════════════════════════════════════════════════════
object BackupScheduler {
    /** Jadwalkan auto backup harian. */
    fun scheduleDaily(context: Context) {
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(
            1, TimeUnit.DAYS
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .setInitialDelay(30, TimeUnit.MINUTES) // delay awal 30 menit
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            AutoBackupWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /** Batalkan auto backup. */
    fun cancel(context: Context) {
        WorkManager.getInstance(context)
            .cancelUniqueWork(AutoBackupWorker.UNIQUE_NAME)
    }

    /** Jalankan backup sekarang (one-shot). */
    fun runNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<AutoBackupWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }
}

// ═══════════════════════════════════════════════════════════
// BACKUP NOTIFIER
// ═══════════════════════════════════════════════════════════
object BackupNotifier {
    private const val CHANNEL_ID = "iyonz_auto_backup"
    private const val NOTIF_ID = 8801

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val existing = (context.getSystemService(Context.NOTIFICATION_SERVICE)
                    as? NotificationManager)
                ?.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID, "Auto Backup",
                    NotificationManager.IMPORTANCE_LOW
                ).apply { description = "Notifikasi backup otomatis" }
                (context.getSystemService(Context.NOTIFICATION_SERVICE)
                        as NotificationManager).createNotificationChannel(ch)
            }
        }
    }

    fun notifySuccess(context: Context, file: File) {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        ensureChannel(context)

        // Intent buka app
        val openIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }
        val pi = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle("Auto Backup berhasil")
            .setContentText("Data tersimpan aman di memori aplikasi")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()

        (context.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager).notify(NOTIF_ID, notif)
    }

    /** Cek apakah perlu reminder (7 hari belum backup). */
    suspend fun maybeRemind(context: Context, settingRepo: SettingRepository) {
        val last = settingRepo.getLastBackupTimestamp()
        val now = System.currentTimeMillis()
        val weekMs = 7L * 24 * 60 * 60 * 1000
        if (last > 0 && now - last < weekMs) return
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        ensureChannel(context)

        val openIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
        val pi = PendingIntent.getActivity(
            context, 1, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Waktunya backup data")
            .setContentText("Udah 7 hari nggak backup. Yuk backup sekarang.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        (context.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager).notify(NOTIF_ID + 1, notif)
    }
}
