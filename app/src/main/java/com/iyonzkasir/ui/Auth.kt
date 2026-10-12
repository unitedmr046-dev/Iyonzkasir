package com.iyonzkasir.ui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.iyonzkasir.*
import com.iyonzkasir.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

// ═══════════════════════════════════════════════════════════
// PIN HASHER
// ═══════════════════════════════════════════════════════════
object PinHasher {
    private val random = SecureRandom()
    fun generateSalt(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes)
    }
    fun hash(pin: String, salt: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt.toByteArray())
        return Base64.getEncoder().encodeToString(md.digest(pin.toByteArray()))
    }
    fun verify(pin: String, salt: String, hash: String): Boolean = hash(pin, salt) == hash
}

// ═══════════════════════════════════════════════════════════
// SPLASH SCREEN
// ═══════════════════════════════════════════════════════════
@Composable
fun SplashScreen(
    settingRepo: SettingRepository,
    userRepo: UserRepository,
    onNeedOnboarding: () -> Unit,
    onNeedLogin: () -> Unit
) {
    LaunchedEffect(Unit) {
        delay(700)
        val done = settingRepo.isOnboardingDone()
        val ownerCount = userRepo.ownerCount()
        if (!done || ownerCount == 0) onNeedOnboarding() else onNeedLogin()
    }
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(BRAND, BRAND_DARK))
        ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f))
                    .border(1.5.dp, Color.White.copy(alpha = 0.25f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PointOfSale, null,
                    Modifier.size(64.dp),
                    tint = Color.White
                )
            }
            Spacer(Modifier.height(Sp.xl))
            Text(
                "iyonzkasir",
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.height(Sp.sm))
            Text(
                "Kasir serbaguna untuk semua usaha",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.85f)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════
// ONBOARDING
// ═══════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(app: IyonzApp, onDone: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val totalSteps = 4
    var showRestoreDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Setup Awal") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BRAND,
                    titleContentColor = Color.White
                )
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            // Progress bar
            Box(
                Modifier.fillMaxWidth().height(6.dp)
                    .background(BRAND.copy(alpha = 0.15f))
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth((step + 1f) / totalSteps)
                        .background(BRAND)
                )
            }

            Box(Modifier.weight(1f)) {
                when (step) {
                    0 -> OnbWelcome(
                        onNext = { step++ },
                        onRestore = { showRestoreDialog = true }
                    )
                    1 -> OnbSetupToko(app.settingRepo) { step++ }
                    2 -> OnbPilihBisnis(app) { step++ }
                    3 -> OnbBuatOwner(app) { onDone() }
                }
            }

            Surface(
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(Sp.lg),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { if (step > 0) step-- },
                        enabled = step > 0
                    ) {
                        Icon(Icons.Default.ArrowBack, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Kembali")
                    }
                    Text(
                        "${step + 1} / $totalSteps",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(60.dp))
                }
            }
        }
    }

    if (showRestoreDialog) {
        OnbRestoreDialog(
            app = app,
            onDismiss = { showRestoreDialog = false }
        )
    }
}

// ═══════════════════════════════════════════════════════════
// ONB WELCOME
// ═══════════════════════════════════════════════════════════
@Composable
private fun OnbWelcome(onNext: () -> Unit, onRestore: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(Sp.xxl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(BRAND.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Storefront, null,
                Modifier.size(56.dp),
                tint = BRAND
            )
        }
        Spacer(Modifier.height(Sp.xxl))
        Text(
            "Selamat Datang di iyonzkasir",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(Sp.md))
        Text(
            "Aplikasi kasir serbaguna untuk warung, retail, cafe, laundry, toko bangunan, dan jasa.\n\nSetup 2 menit, langsung bisa jualan.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Sp.xxxl))

        AppButton(
            text = "Mulai Setup Baru",
            onClick = onNext,
            variant = AppBtnVariant.PRIMARY,
            size = AppBtnSize.LARGE,
            icon = Icons.Default.RocketLaunch,
            fullWidth = true
        )

        Spacer(Modifier.height(Sp.lg))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            HorizontalDivider(Modifier.weight(1f))
            Text(
                "  atau  ",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(Modifier.weight(1f))
        }

        Spacer(Modifier.height(Sp.lg))

        AppButton(
            text = "Restore dari Backup",
            onClick = onRestore,
            variant = AppBtnVariant.OUTLINE,
            size = AppBtnSize.LARGE,
            icon = Icons.Default.SettingsBackupRestore,
            fullWidth = true
        )
        Spacer(Modifier.height(Sp.sm))
        Text(
            "Punya HP baru? Pulihkan data dari file backup .iyonz",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// ═══════════════════════════════════════════════════════════
// ONB RESTORE DIALOG
// ═══════════════════════════════════════════════════════════
@Composable
private fun OnbRestoreDialog(app: IyonzApp, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var selectedUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var showPwdDialog by remember { mutableStateOf(false) }

    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedUri = uri
            showPwdDialog = true
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconCircle(
                    Icons.Default.SettingsBackupRestore,
                    tint = BRAND,
                    size = 40.dp,
                    iconSize = 20.dp
                )
                Spacer(Modifier.width(Sp.sm))
                Text("Restore dari Backup")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Sp.sm)) {
                Text(
                    "Pulihkan data (menu, setting, user, riwayat) dari file backup .iyonz.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (busy) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            Modifier.size(20.dp),
                            color = BRAND,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(Sp.md))
                        Text("Memproses...", style = MaterialTheme.typography.bodySmall)
                    }
                }

                msg?.let {
                    AppCard(
                        color = when {
                            it.startsWith("✅") -> BRAND.copy(alpha = 0.08f)
                            it.startsWith("❌") -> MaterialTheme.colorScheme.errorContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        outlined = true
                    ) {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (it.startsWith("❌"))
                                MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null,
                        tint = DANGER, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Restore akan menimpa semua data saat ini.",
                        style = MaterialTheme.typography.labelSmall,
                        color = DANGER
                    )
                }
            }
        },
        confirmButton = {
            AppButton(
                text = "Pilih File",
                onClick = { openLauncher.launch(arrayOf("*/*")) },
                enabled = !busy,
                variant = AppBtnVariant.PRIMARY,
                size = AppBtnSize.MEDIUM,
                icon = Icons.Default.Folder
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Batal") }
        }
    )

    if (showPwdDialog && selectedUri != null) {
        var pwd by remember { mutableStateOf("") }
        var pwdShow by remember { mutableStateOf(false) }
        var err by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { if (!busy) showPwdDialog = false },
            title = { Text("Password Backup") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Sp.sm)) {
                    Text(
                        "Masukkan password yang dipakai saat backup.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = pwd,
                        onValueChange = { pwd = it; err = null },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = if (pwdShow) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { pwdShow = !pwdShow }) {
                                Icon(
                                    if (pwdShow) Icons.Default.VisibilityOff
                                    else Icons.Default.Visibility, null
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = Rd.button
                    )
                    err?.let {
                        Text(it, color = DANGER, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (pwd.length < 6) {
                        err = "Password minimal 6 karakter"
                        return@TextButton
                    }
                    busy = true
                    scope.launch {
                        val r = try {
                            val ins = ctx.contentResolver.openInputStream(selectedUri!!)
                                ?: return@launch
                            val result = BackupService.restore(ctx, pwd, ins)
                            ins.close()
                            result
                        } catch (e: Exception) {
                            Result.failure(e)
                        }
                        busy = false
                        r.onSuccess {
                            msg = "✅ Restore berhasil! Restart app..."
                            delay(1200)
                            BackupService.restartApp(ctx)
                        }.onFailure { e ->
                            msg = "❌ Gagal: ${e.message ?: "Tidak diketahui"}"
                            showPwdDialog = false
                        }
                    }
                }) { Text("Restore", fontWeight = FontWeight.Bold, color = BRAND) }
            },
            dismissButton = {
                TextButton(onClick = { showPwdDialog = false }, enabled = !busy) {
                    Text("Batal")
                }
            }
        )
    }
}

// ═══════════════════════════════════════════════════════════
// ONB SETUP TOKO
// ═══════════════════════════════════════════════════════════
@Composable
private fun OnbSetupToko(settingRepo: SettingRepository, onNext: () -> Unit) {
    val scope = rememberCoroutineScope()
    var nama by remember { mutableStateOf("") }
    var alamat by remember { mutableStateOf("") }
    var telepon by remember { mutableStateOf("") }
    var footer by remember { mutableStateOf("Terima kasih sudah berbelanja") }

    Column(Modifier.fillMaxSize().padding(Sp.xl)) {
        Text(
            "Info Toko Kamu",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(Sp.xs))
        Text(
            "Data ini muncul di struk & dashboard",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Sp.xl))

        OutlinedTextField(
            nama, { nama = it },
            label = { Text("Nama toko *") },
            placeholder = { Text("cth: Warung Bu Iyong") },
            leadingIcon = { Icon(Icons.Default.Store, null) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = Rd.button
        )
        Spacer(Modifier.height(Sp.md))
        OutlinedTextField(
            alamat, { alamat = it },
            label = { Text("Alamat") },
            leadingIcon = { Icon(Icons.Default.LocationOn, null) },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 2,
            shape = Rd.button
        )
        Spacer(Modifier.height(Sp.md))
        OutlinedTextField(
            telepon,
            { telepon = it.filter { c -> c.isDigit() || c == '+' } },
            label = { Text("No. WhatsApp / Telp") },
            leadingIcon = { Icon(Icons.Default.Phone, null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = Rd.button
        )
        Spacer(Modifier.height(Sp.md))
        OutlinedTextField(
            footer, { footer = it },
            label = { Text("Footer struk") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 2,
            shape = Rd.button
        )

        Spacer(Modifier.weight(1f))

        AppButton(
            text = "Lanjut",
            onClick = {
                scope.launch {
                    settingRepo.set(SettingRepository.KEY_NAMA_TOKO, nama.trim())
                    settingRepo.set(SettingRepository.KEY_ALAMAT, alamat.trim())
                    settingRepo.set(SettingRepository.KEY_TELEPON, telepon.trim())
                    settingRepo.set(SettingRepository.KEY_FOOTER, footer.trim())
                    onNext()
                }
            },
            enabled = nama.isNotBlank(),
            variant = AppBtnVariant.PRIMARY,
            size = AppBtnSize.LARGE,
            fullWidth = true
        )
    }
}

// ═══════════════════════════════════════════════════════════
// ONB PILIH BISNIS
// ═══════════════════════════════════════════════════════════
@Composable
private fun OnbPilihBisnis(app: IyonzApp, onNext: () -> Unit) {
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<BusinessType?>(null) }

    Column(Modifier.fillMaxSize().padding(Sp.xl)) {
        Text(
            "Pilih Jenis Usaha",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(Sp.xs))
        Text(
            "Fitur aplikasi disesuaikan otomatis",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Sp.lg))

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Sp.sm),
            contentPadding = PaddingValues(bottom = Sp.md)
        ) {
            items(BusinessType.values().toList()) { type ->
                val isSelected = selected == type
                AppCard(
                    onClick = { selected = type },
                    outlined = !isSelected,
                    color = if (isSelected) BRAND.copy(alpha = 0.08f)
                    else MaterialTheme.colorScheme.surface
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(52.dp).clip(RoundedCornerShape(Rd.md))
                                .background(
                                    if (isSelected) BRAND.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.surfaceVariant
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                type.emoji,
                                style = MaterialTheme.typography.headlineMedium
                            )
                        }
                        Spacer(Modifier.width(Sp.md))
                        Column(Modifier.weight(1f)) {
                            Text(
                                type.label,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                type.deskripsi,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (isSelected) {
                            Icon(
                                Icons.Default.CheckCircle, null,
                                tint = BRAND,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(Sp.md))
        AppButton(
            text = "Lanjut",
            onClick = {
                val t = selected ?: return@AppButton
                scope.launch {
                    app.settingRepo.set(SettingRepository.KEY_BUSINESS_TYPE, t.id)
                    if (t == BusinessType.CUSTOM) {
                        app.featureRepo.enableAll()
                    } else {
                        app.featureRepo.applyPreset(t)
                    }
                    onNext()
                }
            },
            enabled = selected != null,
            variant = AppBtnVariant.PRIMARY,
            size = AppBtnSize.LARGE,
            fullWidth = true
        )
    }
}

// ═══════════════════════════════════════════════════════════
// ONB BUAT OWNER
// ═══════════════════════════════════════════════════════════
@Composable
private fun OnbBuatOwner(app: IyonzApp, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var nama by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(Sp.xl)) {
        Text(
            "Akun Pemilik",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(Sp.xs))
        Text(
            "Akun ini punya akses penuh",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Sp.xl))

        OutlinedTextField(
            nama, { nama = it },
            label = { Text("Nama lengkap *") },
            leadingIcon = { Icon(Icons.Default.Person, null) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = Rd.button
        )
        Spacer(Modifier.height(Sp.md))
        OutlinedTextField(
            username,
            { username = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' } },
            label = { Text("Username *") },
            leadingIcon = { Icon(Icons.Default.AlternateEmail, null) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = Rd.button
        )
        Spacer(Modifier.height(Sp.md))
        OutlinedTextField(
            pin, { pin = it.filter { c -> c.isDigit() }.take(8) },
            label = { Text("PIN (min 6 digit) *") },
            leadingIcon = { Icon(Icons.Default.Lock, null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = Rd.button
        )
        Spacer(Modifier.height(Sp.md))
        OutlinedTextField(
            pin2, { pin2 = it.filter { c -> c.isDigit() }.take(8) },
            label = { Text("Ulangi PIN *") },
            leadingIcon = { Icon(Icons.Default.Lock, null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = Rd.button
        )
        error?.let {
            Spacer(Modifier.height(Sp.md))
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(Rd.sm),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(Sp.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Error, null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(Sp.sm))
                    Text(it, color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(Modifier.weight(1f))

        AppButton(
            text = "Selesai & Masuk",
            onClick = {
                when {
                    nama.isBlank() -> error = "Nama wajib diisi"
                    username.isBlank() -> error = "Username wajib diisi"
                    pin.length < 6 -> error = "PIN minimal 6 digit"
                    pin != pin2 -> error = "PIN tidak sama"
                    else -> {
                        loading = true
                        scope.launch {
                            val existing = app.userRepo.getByUsername(username)
                            if (existing != null) {
                                error = "Username sudah dipakai"
                                loading = false
                            } else {
                                val salt = PinHasher.generateSalt()
                                val hash = PinHasher.hash(pin, salt)
                                val user = User(
                                    nama = nama.trim(),
                                    username = username.trim(),
                                    pinHash = hash, pinSalt = salt,
                                    role = UserRole.OWNER.id
                                )
                                app.userRepo.upsert(user)
                                app.userRepo.applyRolePreset(user.id, UserRole.OWNER)
                                app.settingRepo.setOnboardingDone()
                                app.userRepo.log("system", "system", "ONBOARDING_COMPLETE",
                                    keterangan = "Owner $username dibuat")
                                loading = false
                                onDone()
                            }
                        }
                    }
                }
            },
            enabled = !loading,
            loading = loading,
            variant = AppBtnVariant.PRIMARY,
            size = AppBtnSize.LARGE,
            fullWidth = true
        )
    }
}

// ═══════════════════════════════════════════════════════════
// LOGIN SCREEN
// ═══════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    userRepo: UserRepository,
    onLoggedIn: (User, Set<PermissionKey>) -> Unit
) {
    val scope = rememberCoroutineScope()
    val users by userRepo.activeUsers.collectAsState(initial = emptyList())
    var selectedUser by remember { mutableStateOf<User?>(null) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var failedAttempts by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("iyonzkasir") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BRAND,
                    titleContentColor = Color.White
                )
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().padding(Sp.xl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val u = selectedUser
            if (u == null) {
                Text(
                    "Pilih Pengguna",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(Sp.xs))
                Text(
                    "Tap nama kamu untuk login",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Sp.xl))

                if (users.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(
                            icon = Icons.Default.Person,
                            title = "Belum ada user",
                            subtitle = "Restart aplikasi untuk memulai setup"
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(Sp.md),
                        verticalArrangement = Arrangement.spacedBy(Sp.md)
                    ) {
                        items(users, key = { it.id }) { usr ->
                            UserCardNew(usr) {
                                selectedUser = usr
                                pin = ""
                                error = null
                            }
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(Sp.md))
                // Avatar besar
                Box(
                    Modifier.size(100.dp).clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    BRAND.copy(alpha = 0.15f),
                                    BRAND.copy(alpha = 0.08f)
                                )
                            )
                        )
                        .border(2.dp, BRAND.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (u.fotoUri != null) {
                        AsyncImage(
                            u.fotoUri, null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            u.nama.take(1).uppercase(),
                            style = MaterialTheme.typography.displaySmall,
                            color = BRAND,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.height(Sp.lg))
                Text(
                    u.nama,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    UserRole.fromId(u.role).label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Sp.xxl))

                // PIN dots
                Row(horizontalArrangement = Arrangement.spacedBy(Sp.md)) {
                    repeat(maxOf(6, pin.length)) { i ->
                        Box(
                            Modifier
                                .size(if (i < pin.length) 18.dp else 14.dp)
                                .clip(CircleShape)
                                .background(
                                    if (i < pin.length) BRAND
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                        )
                    }
                }

                error?.let {
                    Spacer(Modifier.height(Sp.lg))
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(Rd.sm)
                    ) {
                        Row(
                            Modifier.padding(horizontal = Sp.md, vertical = Sp.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Error, null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(it, color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                Spacer(Modifier.height(Sp.xxl))

                PinPad(
                    onDigit = { d ->
                        if (pin.length < 8) {
                            pin += d
                            error = null
                            if (pin.length >= UserRole.fromId(u.role).pinMin) {
                                if (PinHasher.verify(pin, u.pinSalt, u.pinHash)) {
                                    scope.launch {
                                        userRepo.updateLastLogin(u.id)
                                        userRepo.log(u.id, u.nama, "LOGIN")
                                        userRepo.seedPermissionsIfEmpty(u.id,
                                            UserRole.fromId(u.role))
                                        val perms = userRepo.getPermissions(u.id)
                                            .filter { it.allowed }
                                            .mapNotNull { PermissionKey.fromKey(it.permissionKey) }
                                            .toSet()
                                        onLoggedIn(u, perms)
                                    }
                                } else if (pin.length >= 8) {
                                    failedAttempts++
                                    error = "PIN salah ($failedAttempts)"
                                    pin = ""
                                }
                            }
                        }
                    },
                    onBackspace = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
                    onClear = { pin = "" }
                )

                Spacer(Modifier.weight(1f))

                TextButton(
                    onClick = {
                        selectedUser = null
                        pin = ""
                        error = null
                    },
                    modifier = Modifier.padding(vertical = Sp.md)
                ) {
                    Icon(Icons.Default.ArrowBack, null,
                        tint = BRAND, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Ganti pengguna", color = BRAND, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// USER CARD
// ═══════════════════════════════════════════════════════════
@Composable
private fun UserCardNew(u: User, onClick: () -> Unit) {
    AppCard(onClick = onClick) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(72.dp).clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                BRAND.copy(alpha = 0.18f),
                                BRAND.copy(alpha = 0.1f)
                            )
                        )
                    )
                    .border(1.5.dp, BRAND.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (u.fotoUri != null) {
                    AsyncImage(
                        u.fotoUri, null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        u.nama.take(1).uppercase(),
                        style = MaterialTheme.typography.headlineMedium,
                        color = BRAND,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(Sp.sm))
            Text(
                u.nama,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                UserRole.fromId(u.role).label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════
// PIN PAD — touch target gede buat semua umur
// ═══════════════════════════════════════════════════════════
@Composable
private fun PinPad(
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("CLR", "0", "⌫")
    )
    Column(
        verticalArrangement = Arrangement.spacedBy(Sp.md),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Sp.md)) {
                row.forEach { key ->
                    PinKeyNew(key) {
                        when (key) {
                            "CLR" -> onClear()
                            "⌫" -> onBackspace()
                            else -> onDigit(key)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PinKeyNew(label: String, onClick: () -> Unit) {
    val isAction = label == "CLR" || label == "⌫"
    var pressed by remember { mutableStateOf(false) }
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = androidx.compose.animation.core.tween(100),
        label = "pinScale"
    )
    Surface(
        onClick = { onClick() },
        modifier = Modifier.size(76.dp).scale(scale),
        shape = CircleShape,
        color = if (isAction) MaterialTheme.colorScheme.surfaceVariant
        else MaterialTheme.colorScheme.surface,
        shadowElevation = if (isAction) 0.dp else 3.dp,
        border = if (!isAction) BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        ) else null
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                style = if (isAction) MaterialTheme.typography.labelLarge
                else MaterialTheme.typography.headlineSmall,
                fontWeight = if (isAction) FontWeight.SemiBold
                else FontWeight.Bold,
                color = if (isAction) MaterialTheme.colorScheme.onSurfaceVariant
                else BRAND
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════
// KELOLA USER
// ═══════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KelolaUserRoute(app: IyonzApp, nav: NavHostController) {
    val scope = rememberCoroutineScope()
    val users by app.userRepo.users.collectAsState(initial = emptyList())
    var editing by remember { mutableStateOf<User?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var editingPerm by remember { mutableStateOf<User?>(null) }
    var ownerCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(users) { ownerCount = app.userRepo.ownerCount() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kelola Pengguna") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BRAND, titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Default.PersonAdd, null) },
                text = { Text("Tambah User") },
                containerColor = BRAND,
                contentColor = Color.White
            )
        }
    ) { pad ->
        if (users.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize()) {
                EmptyState(
                    icon = Icons.Default.People,
                    title = "Belum ada user",
                    subtitle = "Tap tombol + untuk menambah user pertama",
                    ctaLabel = "Tambah User",
                    onCta = { showAdd = true }
                )
            }
        } else {
            LazyColumn(
                Modifier.padding(pad).fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Sp.md, end = Sp.md,
                    top = Sp.md, bottom = 100.dp
                ),
                verticalArrangement = Arrangement.spacedBy(Sp.sm)
            ) {
                items(users, key = { it.id }) { u ->
                    UserRowNew(
                        u = u,
                        ownerCount = ownerCount,
                        onEdit = { editing = u },
                        onDelete = {
                            scope.launch {
                                app.userRepo.delete(u)
                                Session.current?.let { cu ->
                                    app.userRepo.log(cu.id, cu.nama, "DELETE_USER",
                                        targetId = u.id, keterangan = "Hapus ${u.username}")
                                }
                            }
                        },
                        onEditPerm = { editingPerm = u }
                    )
                }
            }
        }
    }

    if (showAdd || editing != null) {
        UserEditorDialog(
            user = editing,
            onDismiss = { showAdd = false; editing = null },
            onSave = { user, roleChanged ->
                scope.launch {
                    val isNew = editing == null
                    app.userRepo.upsert(user)
                    if (isNew || roleChanged) {
                        app.userRepo.applyRolePreset(user.id, UserRole.fromId(user.role))
                    }
                    app.userRepo.log(
                        Session.current?.id ?: "system",
                        Session.current?.nama ?: "system",
                        if (isNew) "ADD_USER" else "EDIT_USER",
                        targetId = user.id, keterangan = "User ${user.username}"
                    )
                    showAdd = false
                    editing = null
                }
            }
        )
    }

    editingPerm?.let { target ->
        PermissionDialog(
            user = target,
            userRepo = app.userRepo,
            onDismiss = { editingPerm = null }
        )
    }
}

// ═══════════════════════════════════════════════════════════
// USER ROW
// ═══════════════════════════════════════════════════════════
@Composable
private fun UserRowNew(
    u: User,
    ownerCount: Int,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onEditPerm: () -> Unit
) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).clip(CircleShape)
                    .background(BRAND.copy(alpha = 0.12f))
                    .border(1.dp, BRAND.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (u.fotoUri != null) {
                    AsyncImage(
                        u.fotoUri, null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        u.nama.take(1).uppercase(),
                        color = BRAND,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
            Spacer(Modifier.width(Sp.md))
            Column(Modifier.weight(1f)) {
                Text(
                    u.nama,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppBadge(
                        UserRole.fromId(u.role).label,
                        color = when (UserRole.fromId(u.role)) {
                            UserRole.OWNER -> BRAND
                            UserRole.SUPERVISOR -> Color(0xFF1E88E5)
                            UserRole.KASIR -> Color(0xFF43A047)
                        },
                        filled = false
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "@${u.username}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!u.aktif) {
                    Spacer(Modifier.height(2.dp))
                    Text("Nonaktif", color = DANGER,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold)
                }
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Edit, null, tint = BRAND)
            }
            if (u.role != UserRole.OWNER.id || ownerCount > 1) {
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, null, tint = DANGER)
                }
            }
        }
        Spacer(Modifier.height(Sp.md))
        AppButton(
            text = "Atur Izin",
            onClick = onEditPerm,
            variant = AppBtnVariant.OUTLINE,
            size = AppBtnSize.SMALL,
            icon = Icons.Default.Lock,
            fullWidth = true
        )
    }
}

// ═══════════════════════════════════════════════════════════
// USER EDITOR DIALOG
// ═══════════════════════════════════════════════════════════
@Composable
private fun UserEditorDialog(
    user: User?,
    onDismiss: () -> Unit,
    onSave: (User, Boolean) -> Unit
) {
    var nama by remember { mutableStateOf(user?.nama ?: "") }
    var username by remember { mutableStateOf(user?.username ?: "") }
    var role by remember { mutableStateOf(UserRole.fromId(user?.role ?: UserRole.KASIR.id)) }
    var pin by remember { mutableStateOf("") }
    var aktif by remember { mutableStateOf(user?.aktif ?: true) }
    var error by remember { mutableStateOf<String?>(null) }
    val originalRole = remember { user?.role }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (user == null) "Tambah User" else "Edit User",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Sp.sm)) {
                OutlinedTextField(
                    nama, { nama = it },
                    label = { Text("Nama") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = Rd.button
                )
                OutlinedTextField(
                    username,
                    { username = it.lowercase().filter { c -> c.isLetterOrDigit() } },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = Rd.button
                )

                Text(
                    "Role",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Sp.xs)) {
                    UserRole.values().forEach { r ->
                        FilterChip(
                            selected = role == r,
                            onClick = { role = r },
                            label = {
                                Text(r.label,
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        )
                    }
                }

                OutlinedTextField(
                    pin,
                    { pin = it.filter { c -> c.isDigit() }.take(8) },
                    label = {
                        Text(if (user == null) "PIN"
                        else "PIN baru (kosongkan jika tetap)")
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword
                    ),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = Rd.button
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Aktif", fontWeight = FontWeight.Medium)
                        Text(
                            "User bisa login kalau aktif",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = aktif, onCheckedChange = { aktif = it })
                }

                error?.let {
                    Text(it, color = DANGER,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    nama.isBlank() || username.isBlank() -> error = "Nama & username wajib"
                    user == null && pin.length < role.pinMin ->
                        error = "PIN minimal ${role.pinMin} digit"
                    else -> {
                        val salt = if (pin.isNotBlank()) PinHasher.generateSalt()
                        else (user?.pinSalt ?: PinHasher.generateSalt())
                        val hash = if (pin.isNotBlank()) PinHasher.hash(pin, salt)
                        else (user?.pinHash ?: "")
                        val roleChanged = originalRole != null && originalRole != role.id
                        onSave(
                            (user ?: User()).copy(
                                nama = nama.trim(), username = username.trim(),
                                role = role.id, pinSalt = salt, pinHash = hash, aktif = aktif
                            ),
                            roleChanged
                        )
                    }
                }
            }) { Text("Simpan", color = BRAND, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

// ═══════════════════════════════════════════════════════════
// PERMISSION DIALOG
// ═══════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionDialog(
    user: User,
    userRepo: UserRepository,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val saved by userRepo.observePermissions(user.id)
        .collectAsState(initial = emptyList())

    var draft by remember { mutableStateOf<Map<PermissionKey, Boolean>>(emptyMap()) }

    LaunchedEffect(saved) {
        if (draft.isEmpty()) {
            val map = PermissionKey.values().associateWith { key ->
                saved.firstOrNull { it.permissionKey == key.key }?.allowed ?: false
            }
            draft = map
        }
    }

    val grouped = remember(draft) {
        PermissionKey.byKategori().mapValues { entry ->
            entry.value.map { it to (draft[it] ?: false) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Izin — ${user.nama}", fontWeight = FontWeight.Bold)
                Text(
                    UserRole.fromId(user.role).label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(Modifier.heightIn(max = 420.dp)) {
                Text(
                    "Centang izin yang boleh dilakukan user ini.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Sp.sm))

                Row(horizontalArrangement = Arrangement.spacedBy(Sp.xs)) {
                    FilterChip(
                        onClick = {
                            val presets = UserRole.fromId(user.role).defaultPermissions
                            draft = PermissionKey.values().associateWith { it in presets }
                        },
                        label = {
                            Text("Preset ${UserRole.fromId(user.role).label}",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    )
                    FilterChip(
                        onClick = {
                            draft = PermissionKey.values().associateWith { true }
                        },
                        label = {
                            Text("Semua",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    )
                    FilterChip(
                        onClick = {
                            draft = PermissionKey.values().associateWith { false }
                        },
                        label = {
                            Text("Kosong",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    )
                }

                Spacer(Modifier.height(Sp.sm))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    grouped.forEach { (kategori, items) ->
                        item {
                            Text(
                                kategori,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = BRAND,
                                modifier = Modifier.padding(top = Sp.sm, bottom = Sp.xs)
                            )
                        }
                        items(items, key = { it.first.key }) { (perm, allowed) ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(Rd.sm))
                                    .clickable {
                                        draft = draft + (perm to !allowed)
                                    }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = allowed,
                                    onCheckedChange = { c ->
                                        draft = draft + (perm to c)
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = BRAND
                                    )
                                )
                                Spacer(Modifier.width(Sp.sm))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        perm.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (perm.deskripsi.isNotBlank()) {
                                        Text(
                                            perm.deskripsi,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    userRepo.setPermissionsBatch(user.id, draft)
                    Session.current?.let { cu ->
                        userRepo.log(cu.id, cu.nama, "EDIT_PERMISSIONS",
                            targetId = user.id,
                            keterangan = "Update izin ${user.username}")
                    }
                    if (Session.current?.id == user.id) {
                        Session.updatePermissions(draft.filterValues { it }.keys)
                    }
                    onDismiss()
                }
            }) { Text("Simpan", color = BRAND, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}
