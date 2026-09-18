package com.iyonzkasir.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.iyonzkasir.*
import com.iyonzkasir.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// ═══════════════════════════════════════════════════════════
// SETTINGS MAIN SCREEN
// ═══════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(app: IyonzApp, nav: NavHostController) {
    val scope = rememberCoroutineScope()
    val user by Session.currentState
    var namaToko by remember { mutableStateOf("") }
    var businessType by remember { mutableStateOf(BusinessType.WARUNG) }

    LaunchedEffect(Unit) {
        namaToko = app.settingRepo.getNamaToko()
        businessType = app.settingRepo.getBusinessType()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Setelan") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BRAND, titleContentColor = Color.White
                )
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // Header profil toko
            item {
                Card(
                    Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = BRAND_LIGHT)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(56.dp).clip(CircleShape).background(BRAND),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Storefront, null, tint = Color.White)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(namaToko.ifBlank { "Toko Saya" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold)
                            Text("${businessType.emoji} ${businessType.label}",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { nav.navigate(Routes.PROFIL_TOKO) }) {
                            Icon(Icons.Default.Edit, null, tint = BRAND)
                        }
                    }
                }
            }

            // User aktif
            user?.let { u ->
                item {
                    SectionHeader("Pengguna Aktif")
                    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(44.dp).clip(CircleShape).background(BRAND_LIGHT),
                                contentAlignment = Alignment.Center
                            ) {
                                if (u.fotoUri != null) {
                                    AsyncImage(model = u.fotoUri, contentDescription = null,
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
                                Text(UserRole.fromId(u.role).label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = {
                                scope.launch {
                                    app.userRepo.log(u.id, u.nama, "LOGOUT")
                                    Session.logout()
                                    nav.navigate(Routes.LOGIN) {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            }) { Text("Keluar", color = DANGER) }
                        }
                    }
                }
            }

            // Grup: Toko
            item {
                SectionHeader("Toko")
                SettingsItem("Profil Toko", Icons.Default.Store,
                    subtitle = "Nama, alamat, telepon, footer struk") {
                    nav.navigate(Routes.PROFIL_TOKO)
                }
                SettingsItem("Jenis Usaha", Icons.Default.Category,
                    subtitle = businessType.label) {
                    // Dialog pilih bisnis (implementasi di bawah)
                }
            }

            // Grup: Fitur
            item {
                SectionHeader("Fitur Aplikasi")
                SettingsItem("Kelola Fitur", Icons.Default.Tune,
                    subtitle = "Aktifkan / matikan fitur sesuai kebutuhan") {
                    nav.navigate(Routes.FEATURE_TOGGLE)
                }
            }

            // Grup: Pengguna
            if (user?.role == UserRole.OWNER.id) {
                item {
                    SectionHeader("Pengguna & Keamanan")
                    SettingsItem("Kelola Pengguna", Icons.Default.People,
                        subtitle = "Tambah, edit, nonaktifkan user") {
                        nav.navigate(Routes.KELOLA_USER)
                    }
                    SettingsItem("Audit Log", Icons.Default.History,
                        subtitle = "Riwayat aktivitas pengguna") {
                        // TODO: AuditLog screen
                    }
                }
            }

            // Grup: Hardware
            item {
                SectionHeader("Printer & Hardware")
                SettingsItem("Printer Bluetooth", Icons.Default.Print,
                    subtitle = "Cari & hubungkan printer struk") {
                    // TODO
                }
                SettingsItem("Ukuran Struk", Icons.Default.Receipt,
                    subtitle = "58mm / 80mm") {
                    // TODO
                }
            }

            // Grup: Backup
            item {
                SectionHeader("Backup & Restore")
                SettingsItem("Backup Manual", Icons.Default.CloudUpload,
                    subtitle = "Simpan data ke storage") {
                    // TODO
                }
                SettingsItem("Restore", Icons.Default.CloudDownload,
                    subtitle = "Pulihkan dari file backup") {
                    // TODO
                }
            }

            // Grup: Tentang
            item {
                SectionHeader("Tentang")
                SettingsItem("Tentang Aplikasi", Icons.Default.Info,
                    subtitle = "iyonzkasir v0.1.0") {
                    nav.navigate(Routes.TENTANG)
                }
            }

            // Versi
            item {
                Box(Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center) {
                    Text("iyonzkasir v0.1.0 • Made with ❤️",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = BRAND,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp))
}

@Composable
private fun SettingsItem(
    title: String, icon: ImageVector,
    subtitle: String = "", onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = BRAND, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium)
                if (subtitle.isNotBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(Icons.Default.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    HorizontalDivider(Modifier.padding(start = 60.dp), thickness = 0.5.dp)
}

// ═══════════════════════════════════════════════════════════
// FEATURE TOGGLE SCREEN
// ═══════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeatureToggleRoute(app: IyonzApp, nav: NavHostController) {
    val scope = rememberCoroutineScope()
    val toggles by app.featureRepo.toggles.collectAsState(initial = emptyList())
    var showResetDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val grouped = remember(toggles, searchQuery) {
        val map = mutableMapOf<String, MutableList<Pair<FeatureKey, Boolean>>>()
        FeatureKey.values().forEach { key ->
            val match = searchQuery.isBlank() ||
                    key.label.contains(searchQuery, ignoreCase = true) ||
                    key.key.contains(searchQuery, ignoreCase = true)
            if (!match) return@forEach
            val enabled = toggles.firstOrNull { it.featureKey == key.key }?.enabled ?: false
            map.getOrPut(key.kategori) { mutableListOf() }.add(key to enabled)
        }
        map
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kelola Fitur") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                },
                actions = {
                    IconButton(onClick = { showResetDialog = true }) {
                        Icon(Icons.Default.Refresh, null)
                    }
                }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                value = searchQuery, onValueChange = { searchQuery = it },
                placeholder = { Text("Cari fitur...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(12.dp)
            )

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                grouped.forEach { (kategori, items) ->
                    item {
                        SectionHeader("$kategori (${items.count { it.second }}/${items.size})")
                    }
                    items(items, key = { it.first.key }) { (feature, enabled) ->
                        FeatureToggleRow(
                            feature = feature,
                            enabled = enabled,
                            onToggle = { newValue ->
                                scope.launch {
                                    app.featureRepo.setEnabled(feature, newValue)
                                    Session.current?.let { u ->
                                        app.userRepo.log(
                                            u.id, u.nama,
                                            if (newValue) "FEATURE_ON" else "FEATURE_OFF",
                                            targetId = feature.key,
                                            keterangan = feature.label
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
                item {
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset ke Preset?") },
            text = { Text("Semua pengaturan fitur akan dikembalikan ke default sesuai jenis usaha kamu.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val bt = app.settingRepo.getBusinessType()
                        app.featureRepo.applyPreset(bt)
                        showResetDialog = false
                    }
                }) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Batal") }
            }
        )
    }
}

@Composable
private fun FeatureToggleRow(
    feature: FeatureKey, enabled: Boolean, onToggle: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(feature.label, fontWeight = FontWeight.Medium)
                if (feature.deskripsi.isNotBlank()) {
                    Text(feature.deskripsi, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
    HorizontalDivider(thickness = 0.5.dp)
}

// ═══════════════════════════════════════════════════════════
// PROFIL TOKO SCREEN
// ═══════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilTokoRoute(app: IyonzApp, nav: NavHostController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var nama by remember { mutableStateOf("") }
    var alamat by remember { mutableStateOf("") }
    var telepon by remember { mutableStateOf("") }
    var footer by remember { mutableStateOf("") }
    var logoUri by remember { mutableStateOf<String?>(null) }
    var businessType by remember { mutableStateOf(BusinessType.WARUNG) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        nama = app.settingRepo.getNamaToko()
        alamat = app.settingRepo.getAlamatToko()
        telepon = app.settingRepo.getTeleponToko()
        footer = app.settingRepo.getFooterStruk()
        logoUri = app.settingRepo.getLogoUri().ifBlank { null }
        businessType = app.settingRepo.getBusinessType()
        loaded = true
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            logoUri = uri.toString()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profil Toko") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                },
                actions = {
                    TextButton(onClick = {
                        scope.launch {
                            app.settingRepo.set(SettingRepository.KEY_NAMA_TOKO, nama.trim())
                            app.settingRepo.set(SettingRepository.KEY_ALAMAT, alamat.trim())
                            app.settingRepo.set(SettingRepository.KEY_TELEPON, telepon.trim())
                            app.settingRepo.set(SettingRepository.KEY_FOOTER, footer.trim())
                            app.settingRepo.set(SettingRepository.KEY_LOGO, logoUri ?: "")
                            app.settingRepo.set(SettingRepository.KEY_BUSINESS_TYPE, businessType.id)
                            Session.current?.let { u ->
                                app.userRepo.log(u.id, u.nama, "EDIT_PROFIL_TOKO")
                            }
                            nav.popBackStack()
                        }
                    }) { Text("Simpan", color = Color.White, fontWeight = FontWeight.Bold) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BRAND, titleContentColor = Color.White
                )
            )
        }
    ) { pad ->
        if (!loaded) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                Modifier.padding(pad).fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Logo
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(100.dp).clip(CircleShape).background(BRAND_LIGHT)
                            .clickable { picker.launch(arrayOf("image/*")) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (logoUri != null) {
                            AsyncImage(model = logoUri, contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize())
                        } else {
                            Icon(Icons.Default.AddPhotoAlternate, null,
                                tint = BRAND, modifier = Modifier.size(40.dp))
                        }
                    }
                }
                Text("Tap logo untuk ganti",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally))

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(nama, { nama = it },
                    label = { Text("Nama toko *") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(alamat, { alamat = it },
                    label = { Text("Alamat") },
                    modifier = Modifier.fillMaxWidth(), maxLines = 2)
                OutlinedTextField(telepon,
                    { telepon = it.filter { c -> c.isDigit() || c == '+' } },
                    label = { Text("No. WhatsApp / Telp") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(footer, { footer = it },
                    label = { Text("Footer struk") },
                    modifier = Modifier.fillMaxWidth(), maxLines = 2)

                Text("Jenis Usaha", fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp))

                BusinessType.values().forEach { bt ->
                    val selected = businessType == bt
                    Card(
                        Modifier.fillMaxWidth().clickable { businessType = bt },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected) BRAND_LIGHT
                            else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(bt.emoji, style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(bt.label, fontWeight = FontWeight.SemiBold)
                                Text(bt.deskripsi, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            RadioButton(selected = selected, onClick = { businessType = bt })
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// TENTANG
// ═══════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TentangRoute(nav: NavHostController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tentang") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))
            Box(
                Modifier.size(96.dp).clip(RoundedCornerShape(24.dp)).background(BRAND),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PointOfSale, null,
                    tint = Color.White, modifier = Modifier.size(56.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text("iyonzkasir", style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold, color = BRAND)
            Text("v0.1.0", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            Text(
                "Aplikasi kasir serbaguna untuk semua jenis usaha:\n" +
                "warung, retail, cafe, restoran, laundry, dan jasa.\n\n" +
                "Offline by default, simple by default, aman by design.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.weight(1f))
            Text("© 2025 iyonzkasir",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
