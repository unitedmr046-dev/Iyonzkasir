package com.iyonzkasir.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
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
// SPLASH
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
    Box(Modifier.fillMaxSize().background(BRAND), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.PointOfSale, null, Modifier.size(96.dp), tint = Color.White)
            Spacer(Modifier.height(16.dp))
            Text("iyonzkasir", style = MaterialTheme.typography.headlineLarge,
                color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Kasir serbaguna untuk semua usaha",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.9f))
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Setup Awal") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BRAND, titleContentColor = Color.White
                )
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            LinearProgressIndicator(
                progress = { (step + 1f) / totalSteps },
                modifier = Modifier.fillMaxWidth(), color = BRAND
            )
            Box(Modifier.weight(1f)) {
                when (step) {
                    0 -> OnbWelcome { step++ }
                    1 -> OnbSetupToko(app.settingRepo) { step++ }
                    2 -> OnbPilihBisnis(app) { step++ }
                    3 -> OnbBuatOwner(app) { onDone() }
                }
            }
            Surface(shadowElevation = 4.dp) {
                Row(Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { if (step > 0) step-- }, enabled = step > 0) {
                        Text("Kembali")
                    }
                    Text("${step + 1} / $totalSteps",
                        style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.width(60.dp))
                }
            }
        }
    }
}

@Composable
private fun OnbWelcome(onNext: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Storefront, null, Modifier.size(80.dp), tint = BRAND)
        Spacer(Modifier.height(24.dp))
        Text("Selamat Datang di iyonzkasir",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text("Aplikasi kasir serbaguna untuk warung, retail, cafe, laundry, toko bangunan, dan jasa.\n\nSetup 2 menit, langsung bisa jualan.",
            style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(32.dp))
        Button(onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BRAND)) {
            Text("Mulai Setup", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun OnbSetupToko(settingRepo: SettingRepository, onNext: () -> Unit) {
    val scope = rememberCoroutineScope()
    var nama by remember { mutableStateOf("") }
    var alamat by remember { mutableStateOf("") }
    var telepon by remember { mutableStateOf("") }
    var footer by remember { mutableStateOf("Terima kasih sudah berbelanja") }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Info Toko Kamu", style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("Data ini muncul di struk & dashboard",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(nama, { nama = it },
            label = { Text("Nama toko *") },
            placeholder = { Text("cth: Warung Bu Iyong") },
            modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(alamat, { alamat = it },
            label = { Text("Alamat") }, modifier = Modifier.fillMaxWidth(), maxLines = 2)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(telepon,
            { telepon = it.filter { c -> c.isDigit() || c == '+' } },
            label = { Text("No. WhatsApp / Telp") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(footer, { footer = it },
            label = { Text("Footer struk") }, modifier = Modifier.fillMaxWidth(), maxLines = 2)
        Spacer(Modifier.weight(1f))
        Button(
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
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BRAND)
        ) { Text("Lanjut", fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun OnbPilihBisnis(app: IyonzApp, onNext: () -> Unit) {
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<BusinessType?>(null) }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Pilih Jenis Usaha", style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("Fitur aplikasi disesuaikan otomatis",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 16.dp)) {
            items(BusinessType.values().toList()) { type ->
                val isSelected = selected == type
                Card(
                    Modifier.fillMaxWidth().clickable { selected = type },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) BRAND_LIGHT
                        else MaterialTheme.colorScheme.surface
                    ),
                    border = if (isSelected) BorderStroke(2.dp, BRAND) else null
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(type.emoji, style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(type.label, fontWeight = FontWeight.SemiBold)
                            Text(type.deskripsi,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (isSelected) Icon(Icons.Default.CheckCircle, null, tint = BRAND)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                val t = selected ?: return@Button
                scope.launch {
                    app.settingRepo.set(SettingRepository.KEY_BUSINESS_TYPE, t.id)
                    if (t == BusinessType.CUSTOM) {
                        app.featureRepo.enableAll() // user atur manual nanti
                    } else {
                        app.featureRepo.applyPreset(t)
                    }
                    onNext()
                }
            },
            enabled = selected != null,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BRAND)
        ) { Text("Lanjut", fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun OnbBuatOwner(app: IyonzApp, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var nama by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Akun Pemilik", style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("Akun ini punya akses penuh",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(nama, { nama = it }, label = { Text("Nama lengkap *") },
            modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(username,
            { username = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' } },
            label = { Text("Username *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(pin, { pin = it.filter { c -> c.isDigit() }.take(8) },
            label = { Text("PIN (min 6 digit) *") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(pin2, { pin2 = it.filter { c -> c.isDigit() }.take(8) },
            label = { Text("Ulangi PIN *") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(), singleLine = true)
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = DANGER, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.weight(1f))
        Button(
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
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BRAND)
        ) {
            if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White)
            else Text("Selesai & Masuk", fontWeight = FontWeight.Bold)
        }
    }
}

// ═══════════════════════════════════════════════════════════
// LOGIN
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
            TopAppBar(title = { Text("iyonzkasir") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BRAND, titleContentColor = Color.White))
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            val u = selectedUser
            if (u == null) {
                Text("Pilih Pengguna", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("Tap nama kamu untuk login",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(20.dp))
                if (users.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Belum ada user. Restart aplikasi.")
                    }
                } else {
                    LazyVerticalGrid(columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(users, key = { it.id }) { usr ->
                            UserCard(usr) { selectedUser = usr; pin = ""; error = null }
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(12.dp))
                Box(Modifier.size(80.dp).clip(CircleShape).background(BRAND_LIGHT),
                    contentAlignment = Alignment.Center) {
                    if (u.fotoUri != null) {
                        AsyncImage(u.fotoUri, null, contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize())
                    } else {
                        Text(u.nama.take(1).uppercase(),
                            style = MaterialTheme.typography.headlineLarge,
                            color = BRAND, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(u.nama, style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold)
                Text(UserRole.fromId(u.role).label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(maxOf(6, pin.length)) { i ->
                        Box(Modifier.size(16.dp).clip(CircleShape).background(
                            if (i < pin.length) BRAND
                            else MaterialTheme.colorScheme.surfaceVariant))
                    }
                }
                error?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = DANGER, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(24.dp))
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
                                        // Ensure permission di-seed
                                        userRepo.seedPermissionsIfEmpty(u.id, UserRole.fromId(u.role))
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
                TextButton(onClick = { selectedUser = null; pin = ""; error = null }) {
                    Text("Ganti pengguna", color = BRAND)
                }
            }
        }
    }
}

@Composable
private fun UserCard(u: User, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(BRAND_LIGHT),
                contentAlignment = Alignment.Center) {
                if (u.fotoUri != null) {
                    AsyncImage(u.fotoUri, null, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize())
                } else {
                    Text(u.nama.take(1).uppercase(),
                        style = MaterialTheme.typography.headlineMedium,
                        color = BRAND, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(u.nama, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(UserRole.fromId(u.role).label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PinPad(onDigit: (String) -> Unit, onBackspace: () -> Unit, onClear: () -> Unit) {
    val rows = listOf(
        listOf("1", "2", "3"), listOf("4", "5", "6"),
        listOf("7", "8", "9"), listOf("CLR", "0", "⌫")
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { key ->
                    PinKey(key) {
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
private fun PinKey(label: String, onClick: () -> Unit) {
    val isAction = label == "CLR" || label == "⌫"
    Surface(
        modifier = Modifier.size(72.dp).clip(CircleShape).clickable(onClick = onClick),
        color = if (isAction) MaterialTheme.colorScheme.surfaceVariant
        else MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label,
                style = if (isAction) MaterialTheme.typography.bodyMedium
                else MaterialTheme.typography.headlineSmall,
                fontWeight = if (isAction) FontWeight.Normal else FontWeight.SemiBold,
                color = if (isAction) MaterialTheme.colorScheme.onSurfaceVariant else BRAND)
        }
    }
}

// ═══════════════════════════════════════════════════════════
// KELOLA USER + IZIN
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
            TopAppBar(title = { Text("Kelola Pengguna") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Default.PersonAdd, null) },
                text = { Text("Tambah User") },
                containerColor = BRAND
            )
        }
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 100.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(users, key = { it.id }) { u ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(48.dp).clip(CircleShape).background(BRAND_LIGHT),
                                contentAlignment = Alignment.Center) {
                                if (u.fotoUri != null) {
                                    AsyncImage(u.fotoUri, null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize())
                                } else {
                                    Text(u.nama.take(1).uppercase(),
                                        color = BRAND, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(u.nama, fontWeight = FontWeight.SemiBold)
                                Text("${UserRole.fromId(u.role).label} • @${u.username}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (!u.aktif) Text("Nonaktif", color = DANGER,
                                    style = MaterialTheme.typography.labelSmall)
                            }
                            IconButton(onClick = { editing = u }) {
                                Icon(Icons.Default.Edit, null)
                            }
                            if (u.role != UserRole.OWNER.id || ownerCount > 1) {
                                IconButton(onClick = {
                                    scope.launch {
                                        app.userRepo.delete(u)
                                        Session.current?.let { cu ->
                                            app.userRepo.log(cu.id, cu.nama, "DELETE_USER",
                                                targetId = u.id, keterangan = "Hapus ${u.username}")
                                        }
                                    }
                                }) {
                                    Icon(Icons.Default.Delete, null, tint = DANGER)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row {
                            OutlinedButton(
                                onClick = { editingPerm = u },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Lock, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Atur Izin", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
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
        title = { Text(if (user == null) "Tambah User" else "Edit User") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(nama, { nama = it },
                    label = { Text("Nama") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(username,
                    { username = it.lowercase().filter { c -> c.isLetterOrDigit() } },
                    label = { Text("Username") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                Text("Role:", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    UserRole.values().forEach { r ->
                        FilterChip(selected = role == r, onClick = { role = r },
                            label = { Text(r.label, style = MaterialTheme.typography.bodySmall) })
                    }
                }
                OutlinedTextField(pin,
                    { pin = it.filter { c -> c.isDigit() }.take(8) },
                    label = { Text(if (user == null) "PIN" else "PIN baru (kosongkan jika tetap)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Aktif", Modifier.weight(1f))
                    Switch(checked = aktif, onCheckedChange = { aktif = it })
                }
                error?.let { Text(it, color = DANGER, style = MaterialTheme.typography.bodySmall) }
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
            }) { Text("Simpan") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

// ═══════════════════════════════════════════════════════════
// DIALOG IZIN GRANULAR PER-USER
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

    // state lokal (biar bisa toggle sebelum simpan)
    var draft by remember { mutableStateOf<Map<PermissionKey, Boolean>>(emptyMap()) }

    LaunchedEffect(saved) {
        // sync draft dari DB kalau belum diisi
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
                Text("Izin — ${user.nama}")
                Text(UserRole.fromId(user.role).label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        text = {
            Column(Modifier.heightIn(max = 420.dp)) {
                // Info
                Text("Centang izin yang boleh dilakukan user ini.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))

                // Tombol preset
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(
                        onClick = {
                            val presets = UserRole.fromId(user.role).defaultPermissions
                            draft = PermissionKey.values().associateWith { it in presets }
                        },
                        label = { Text("Preset ${UserRole.fromId(user.role).label}",
                            style = MaterialTheme.typography.bodySmall) }
                    )
                    AssistChip(
                        onClick = {
                            draft = PermissionKey.values().associateWith { true }
                        },
                        label = { Text("Semua", style = MaterialTheme.typography.bodySmall) }
                    )
                    AssistChip(
                        onClick = {
                            draft = PermissionKey.values().associateWith { false }
                        },
                        label = { Text("Kosong", style = MaterialTheme.typography.bodySmall) }
                    )
                }

                Spacer(Modifier.height(8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    grouped.forEach { (kategori, items) ->
                        item {
                            Text(kategori,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = BRAND,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                        }
                        items(items, key = { it.first.key }) { (perm, allowed) ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable {
                                        draft = draft + (perm to !allowed)
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = allowed,
                                    onCheckedChange = { c ->
                                        draft = draft + (perm to c)
                                    }
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(perm.label,
                                        style = MaterialTheme.typography.bodyMedium)
                                    if (perm.deskripsi.isNotBlank()) {
                                        Text(perm.deskripsi,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    // Kalau yang diedit user sendiri, refresh session
                    if (Session.current?.id == user.id) {
                        Session.updatePermissions(draft.filterValues { it }.keys)
                    }
                    onDismiss()
                }
            }) { Text("Simpan") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}
