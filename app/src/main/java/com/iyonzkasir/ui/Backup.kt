package com.iyonzkasir.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.iyonzkasir.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.*
import java.security.SecureRandom
import java.security.spec.KeySpec
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

// ═══════════════════════════════════════════════════════════
// BACKUP SERVICE — .iyonz format (ZIP + AES-256)
// ═══════════════════════════════════════════════════════════
object BackupService {
    private const val MAGIC = "IYONZ1"
    private const val DB_NAME = "iyonzkasir.db"
    private const val SALT_SIZE = 16
    private const val IV_SIZE = 16
    private const val FILE_EXT = ".iyonz"

    fun namaFileDefault(): String {
        val ts = SimpleDateFormat("yyyyMMdd-HHmm", Locale("id")).format(Date())
        return "iyonzkasir-$ts$FILE_EXT"
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec: KeySpec = PBEKeySpec(password.toCharArray(), salt, 10_000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val key = factory.generateSecret(spec).encoded
        return SecretKeySpec(key, "AES")
    }

    /** Backup database + foto + setting ke file .iyonz di OutputStream. */
    suspend fun backup(
        context: Context,
        password: String,
        output: OutputStream
    ): Result<Long> = withContext(Dispatchers.IO) {
        try {
            // 1. Buat ZIP sementara (in-memory)
            val baos = ByteArrayOutputStream()
            ZipOutputStream(BufferedOutputStream(baos)).use { zos ->
                // a) Database
                val dbFile = context.getDatabasePath(DB_NAME)
                if (dbFile.exists()) {
                    zos.putNextEntry(ZipEntry(DB_NAME))
                    dbFile.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
                // b) Foto menu (folder internal)
                val photosDir = File(context.filesDir, "menu_photos")
                if (photosDir.exists() && photosDir.isDirectory) {
                    photosDir.listFiles()?.forEach { f ->
                        if (f.isFile) {
                            zos.putNextEntry(ZipEntry("menu_photos/${f.name}"))
                            f.inputStream().use { it.copyTo(zos) }
                            zos.closeEntry()
                        }
                    }
                }
                // c) Manifest
                val manifest = """
                    {
                      "app": "iyonzkasir",
                      "version": 1,
                      "timestamp": ${System.currentTimeMillis()}
                    }
                """.trimIndent()
                zos.putNextEntry(ZipEntry("manifest.json"))
                zos.write(manifest.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
            val zipBytes = baos.toByteArray()

            // 2. Enkripsi AES-256-CBC
            val salt = ByteArray(SALT_SIZE).also { SecureRandom().nextBytes(it) }
            val iv = ByteArray(IV_SIZE).also { SecureRandom().nextBytes(it) }
            val key = deriveKey(password, salt)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))

            // 3. Tulis: MAGIC + saltLen + salt + ivLen + iv + ciphertext
            val out = DataOutputStream(BufferedOutputStream(output))
            out.write(MAGIC.toByteArray(Charsets.US_ASCII))
            out.writeInt(salt.size); out.write(salt)
            out.writeInt(iv.size); out.write(iv)

            val cOut = CipherOutputStream(out, cipher)
            cOut.write(zipBytes)
            cOut.flush()
            cOut.close()

            Result.success((zipBytes.size + 64).toLong())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Restore dari file .iyonz. Password harus match. */
    suspend fun restore(
        context: Context,
        password: String,
        input: InputStream
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val dis = DataInputStream(BufferedInputStream(input))

            // 1. Header
            val magic = ByteArray(MAGIC.length)
            dis.readFully(magic)
            if (String(magic, Charsets.US_ASCII) != MAGIC) {
                return@withContext Result.failure(
                    Exception("File bukan backup iyonzkasir"))
            }
            val saltLen = dis.readInt()
            if (saltLen != SALT_SIZE) return@withContext Result.failure(
                Exception("Format file tidak valid"))
            val salt = ByteArray(saltLen).also { dis.readFully(it) }
            val ivLen = dis.readInt()
            if (ivLen != IV_SIZE) return@withContext Result.failure(
                Exception("Format file tidak valid"))
            val iv = ByteArray(ivLen).also { dis.readFully(it) }

            // 2. Decrypt
            val key = deriveKey(password, salt)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))

            val decrypted = ByteArrayOutputStream()
            try {
                CipherInputStream(dis, cipher).use { it.copyTo(decrypted) }
            } catch (e: Exception) {
                return@withContext Result.failure(
                    Exception("Password salah atau file rusak"))
            }
            val zipBytes = decrypted.toByteArray()

            // 3. Backup file lama (safety)
            val dbFile = context.getDatabasePath(DB_NAME)
            if (dbFile.exists()) {
                dbFile.copyTo(File(dbFile.parent, "$DB_NAME.bak"), overwrite = true)
            }

            // 4. Tutup koneksi database dulu biar aman
            try { AppDatabase.get(context).close() } catch (_: Exception) {}

            // 5. Extract ZIP
            ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    when {
                        entry.name == DB_NAME -> {
                            FileOutputStream(dbFile).use { zis.copyTo(it) }
                        }
                        entry.name.startsWith("menu_photos/") -> {
                            val fname = entry.name.substringAfter("menu_photos/")
                            if (fname.isNotEmpty()) {
                                val dir = File(context.filesDir, "menu_photos")
                                dir.mkdirs()
                                FileOutputStream(File(dir, fname)).use { zis.copyTo(it) }
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            // 6. Hapus WAL/SHM (biar bersih)
            File(dbFile.parent, "$DB_NAME-wal").delete()
            File(dbFile.parent, "$DB_NAME-shm").delete()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Restart aplikasi (setelah restore). */
    fun restartApp(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }
}

// ═══════════════════════════════════════════════════════════
// BACKUP SCREEN
// ═══════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRoute(app: IyonzApp, nav: NavHostController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var lastTs by remember { mutableStateOf(0L) }
    var lastName by remember { mutableStateOf("") }
    var pendingMode by remember { mutableStateOf("") } // "backup" | "restore"

    // Dialog password
    var showPwdDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        lastTs = app.settingRepo.getLastBackupTimestamp()
        lastName = app.settingRepo.getLastBackupName()
    }

    // File picker untuk simpan backup
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // Simpan uri di variabel sementara via pendingMode
        pendingUri = uri
        pendingMode = "backup"
        showPwdDialog = true
    }

    // File picker untuk restore
    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        pendingUri = uri
        pendingMode = "restore"
        showPwdDialog = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup & Restore") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Info status backup terakhir
            Card(colors = CardDefaults.cardColors(containerColor = BRAND_LIGHT)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudUpload, null, tint = BRAND,
                            modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Backup Terakhir",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold)
                            Text(
                                if (lastTs == 0L) "Belum pernah backup"
                                else SimpleDateFormat("dd MMM yyyy • HH:mm",
                                    Locale("id")).format(Date(lastTs)),
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (lastName.isNotBlank())
                                Text(lastName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Penjelasan
            Card(colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("ℹ️ Info",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium)
                    Text("• Backup berisi: menu, harga, foto, riwayat, user, setting",
                        style = MaterialTheme.typography.bodySmall)
                    Text("• File dilindungi password. Jangan lupa passwordnya!",
                        style = MaterialTheme.typography.bodySmall, color = DANGER)
                    Text("• Simpan file di Google Drive / WhatsApp biar aman",
                        style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(Modifier.height(4.dp))

            // Tombol Backup
            Button(
                onClick = {
                    val fname = BackupService.namaFileDefault()
                    saveLauncher.launch(fname)
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BRAND)
            ) {
                Icon(Icons.Default.Backup, null)
                Spacer(Modifier.width(8.dp))
                Text("Backup Sekarang", fontWeight = FontWeight.Bold)
            }

            // Tombol Restore
            OutlinedButton(
                onClick = { openLauncher.launch(arrayOf("*/*")) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(Icons.Default.SettingsBackupRestore, null)
                Spacer(Modifier.width(8.dp))
                Text("Restore dari File", fontWeight = FontWeight.Bold)
            }

            // Info kalau ada proses
            if (busy) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = BRAND)
                    Spacer(Modifier.width(12.dp))
                    Text("Memproses...", style = MaterialTheme.typography.bodyMedium)
                }
            }

            msg?.let {
                Spacer(Modifier.height(8.dp))
                Card(colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Text(it, modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    // Dialog password
    if (showPwdDialog) {
        PasswordDialog(
            title = if (pendingMode == "backup") "Password Backup"
            else "Password Restore",
            deskripsi = if (pendingMode == "backup")
                "Password ini dipakai untuk enkripsi file. Simpan di tempat aman!"
            else "Masukkan password yang dipakai saat backup.",
            confirmLabel = if (pendingMode == "backup") "Backup" else "Restore",
            konfirmasi = pendingMode == "backup",
            onDismiss = { showPwdDialog = false; pendingUri = null },
            onConfirm = { pwd ->
                showPwdDialog = false
                val uri = pendingUri ?: return@PasswordDialog
                busy = true; msg = null

                scope.launch {
                    val result = if (pendingMode == "backup") {
                        try {
                            val os = ctx.contentResolver.openOutputStream(uri)
                                ?: return@launch
                            val r = BackupService.backup(ctx, pwd, os)
                            os.close()
                            if (r.isSuccess) {
                                val ts = System.currentTimeMillis()
                                app.settingRepo.setLastBackupTimestamp(ts)
                                app.settingRepo.setLastBackupName(
                                    uri.lastPathSegment ?: "backup.iyonz")
                                lastTs = ts
                                Session.current?.let { u ->
                                    app.userRepo.log(u.id, u.nama, "BACKUP",
                                        keterangan = "Backup ke file")
                                }
                            }
                            r
                        } catch (e: Exception) {
                            Result.failure(e)
                        }
                    } else {
                        try {
                            val ins = ctx.contentResolver.openInputStream(uri)
                                ?: return@launch
                            val r = BackupService.restore(ctx, pwd, ins)
                            ins.close()
                            if (r.isSuccess) {
                                Session.current?.let { u ->
                                    app.userRepo.log(u.id, u.nama, "RESTORE",
                                        keterangan = "Restore dari file")
                                }
                            }
                            r
                        } catch (e: Exception) {
                            Result.failure(e)
                        }
                    }
                    busy = false
                    pendingUri = null

                    result.onSuccess {
                        if (pendingMode == "backup") {
                            msg = "✅ Backup berhasil! File disimpan."
                        } else {
                            msg = "✅ Restore berhasil! Aplikasi akan restart..."
                            kotlinx.coroutines.delay(1500)
                            BackupService.restartApp(ctx)
                        }
                    }.onFailure { e ->
                        msg = "❌ Gagal: ${e.message ?: "Tidak diketahui"}"
                    }
                }
            }
        )
    }
}

// state sementara
private var pendingUri: android.net.Uri? = null

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PasswordDialog(
    title: String,
    deskripsi: String,
    confirmLabel: String,
    konfirmasi: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var pwd by remember { mutableStateOf("") }
    var pwd2 by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(deskripsi, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = pwd, onValueChange = { pwd = it; err = null },
                    label = { Text("Password (min 6 karakter)") },
                    singleLine = true,
                    visualTransformation = if (show) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { show = !show }) {
                            Icon(if (show) Icons.Default.VisibilityOff
                            else Icons.Default.Visibility, null)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                if (konfirmasi) {
                    OutlinedTextField(
                        value = pwd2, onValueChange = { pwd2 = it; err = null },
                        label = { Text("Ulangi password") },
                        singleLine = true,
                        visualTransformation = if (show) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                err?.let {
                    Text(it, color = DANGER,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    pwd.length < 6 -> err = "Password minimal 6 karakter"
                    konfirmasi && pwd != pwd2 -> err = "Password tidak sama"
                    else -> onConfirm(pwd)
                }
            }) { Text(confirmLabel, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}
