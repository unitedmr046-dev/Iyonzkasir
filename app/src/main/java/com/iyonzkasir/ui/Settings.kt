package com.iyonzkasir.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.iyonzkasir.*
import com.iyonzkasir.data.*
import kotlinx.coroutines.launch

// ═══════════════════════════════════════════════════════════
// SETTINGS ITEM
// ═══════════════════════════════════════════════════════════
private data class SettingItem(
    val key: String, val title: String, val subtitle: String,
    val icon: ImageVector, val color: Color, val onClick: () -> Unit
)

// ═══════════════════════════════════════════════════════════
// SETTINGS MAIN
// ═══════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(app: IyonzApp, nav: NavHostController) {
    val scope = rememberCoroutineScope()
    val user by Session.currentState
    var namaToko by remember { mutableStateOf("") }
    var businessType by remember { mutableStateOf(BusinessType.WARUNG) }
    var memberCount by remember { mutableIntStateOf(0) }
    var lowStockCount by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    val enabled by FeatureManager.enabled.collectAsState()

    LaunchedEffect(Unit) {
        namaToko = app.settingRepo.getNamaToko()
        businessType = app.settingRepo.getBusinessType()
    }
    LaunchedEffect(Unit) {
        app.crmRepo.memberCount.collect { memberCount = it }
    }
    LaunchedEffect(Unit) {
        app.stockRepo.lowStockCount.collect { lowStockCount = it }
    }

    val crmEnabled = FeatureKey.MEMBER in enabled ||
            FeatureKey.VOUCHER in enabled || FeatureKey.HUTANG_PELANGGAN in enabled
    val stockEnabled = FeatureKey.LOW_STOCK_ALERT in enabled ||
            FeatureKey.POTONG_STOK in enabled || FeatureKey.STOCK_OPNAME in enabled
    val kategoriEnabled = FeatureKey.KATEGORI_MGMT in enabled
    val barcodeEnabled = FeatureKey.BARCODE in enabled
    val pengeluaranEnabled = FeatureKey.PENGELUARAN in enabled
    val antrianEnabled = FeatureKey.NOMOR_ANTRIAN in enabled
    val printerEnabled = FeatureKey.PRINTER_BT in enabled

    val groups = buildList {
        // TOKO
        val toko = mutableListOf<SettingItem>()
        toko.add(SettingItem(
            key = "profil",
            title = "Profil Toko",
            subtitle = "Nama, alamat, telepon, footer struk",
            icon = Icons.Default.Store,
            color = Color(0xFFFF6B35),
            onClick = {
                if (Session.can(PermissionKey.PROFIL_TOKO))
                    nav.navigate(Routes.PROFIL_TOKO)
            }
        ))
        if (kategoriEnabled && Session.can(PermissionKey.KELOLA_KATEGORI)) {
            toko.add(SettingItem(
                key = "kategori",
                title = "Kelola Kategori",
                subtitle = "Tambah/edit kategori & warna",
                icon = Icons.Default.Category,
                color = Color(0xFF8E24AA),
                onClick = { nav.navigate(Routes.KATEGORI) }
            ))
        }
        if (Session.can(PermissionKey.KELOLA_USER)) {
            toko.add(SettingItem(
                key = "user",
                title = "Kelola Pengguna",
                subtitle = "Tambah user & atur izin",
                icon = Icons.Default.ManageAccounts,
                color = Color(0xFF1E88E5),
                onClick = { nav.navigate(Routes.KELOLA_USER) }
            ))
        }
        if (toko.isNotEmpty()) add("TOKO" to toko)

        // TRANSAKSI
        val trx = mutableListOf<SettingItem>()
        trx.add(SettingItem(
            key = "pajak",
            title = "Keuangan & Pajak",
            subtitle = "PPN default, metode bayar, suara",
            icon = Icons.Default.Payments,
            color = Color(0xFF43A047),
            onClick = { nav.navigate(Routes.KEUANGAN) }
        ))
        if (pengeluaranEnabled && Session.can(PermissionKey.KELOLA_PENGELUARAN)) {
            trx.add(SettingItem(
                key = "pengeluaran",
                title = "Pengeluaran",
                subtitle = "Catat biaya operasional harian",
                icon = Icons.Default.AccountBalanceWallet,
                color = Color(0xFFE53935),
                onClick = { nav.navigate(Routes.PENGELUARAN) }
            ))
        }
        if (antrianEnabled) {
            trx.add(SettingItem(
                key = "antrian",
                title = "Nomor Antrian",
                subtitle = "Aktifkan & atur prefix antrian",
                icon = Icons.Default.ConfirmationNumber,
                color = Color(0xFFD81B60),
                onClick = { nav.navigate(Routes.ANTRIAN) }
            ))
        }
        if (stockEnabled && Session.can(PermissionKey.LIHAT_STOK)) {
            trx.add(SettingItem(
                key = "stok",
                title = "Kelola Stok",
                subtitle = if (lowStockCount > 0) "⚠️ $lowStockCount menu stok menipis"
                else "Stok, opname, riwayat pergerakan",
                icon = Icons.Default.Inventory,
                color = Color(0xFF00897B),
                onClick = { nav.navigate(Routes.INVENTARIS) }
            ))
        }
        if (Session.can(PermissionKey.KELOLA_FITUR)) {
            trx.add(SettingItem(
                key = "fitur",
                title = "Kelola Fitur",
                subtitle = "Aktifkan / matikan fitur",
                icon = Icons.Default.Tune,
                color = Color(0xFFFB8C00),
                onClick = { nav.navigate(Routes.FEATURE_TOGGLE) }
            ))
        }
        if (crmEnabled) {
            trx.add(SettingItem(
                key = "crm",
                title = "Member & Voucher",
                subtitle = if (memberCount > 0) "$memberCount member terdaftar"
                else "Kelola member, poin & voucher",
                icon = Icons.Default.People,
                color = Color(0xFF6D4C41),
                onClick = { nav.navigate(Routes.CRM) }
            ))
        }
        add("TRANSAKSI" to trx)

        // OUTPUT
        val out = mutableListOf<SettingItem>()
        out.add(SettingItem(
            key = "laporan",
            title = "Laporan & Laba",
            subtitle = "Laporan lengkap, grafik, export CSV",
            icon = Icons.Default.Analytics,
            color = Color(0xFF3949AB),
            onClick = {
                if (Session.can(PermissionKey.LIHAT_LAPORAN))
                    nav.navigate(Routes.LAPORAN)
            }
        ))
        if (printerEnabled) {
            out.add(SettingItem(
                key = "printer",
                title = "Printer & Struk",
                subtitle = "Sambungkan printer, ukuran kertas",
                icon = Icons.Default.Print,
                color = Color(0xFF546E7A),
                onClick = { nav.navigate(Routes.PRINTER) }
            ))
            out.add(SettingItem(
                key = "template_struk",
                title = "Template Struk",
                subtitle = "Atur field apa saja di struk",
                icon = Icons.Default.Receipt,
                color = Color(0xFF9C27B0),
                onClick = { nav.navigate(Routes.TEMPLATE_STRUK) }
            ))
        }
        if (barcodeEnabled) {
            out.add(SettingItem(
                key = "barcode",
                title = "Barcode Scanner",
                subtitle = "Scan dari kamera / galeri",
                icon = Icons.Default.QrCodeScanner,
                color = Color(0xFF607D8B),
                onClick = { nav.navigate(Routes.BARCODE_INFO) }
            ))
        }
        add("OUTPUT" to out)

        // TAMPILAN & SISTEM
        val sys = mutableListOf<SettingItem>()
        sys.add(SettingItem(
            key = "tema",
            title = "Tema Terang/Gelap",
            subtitle = "Terang / Gelap / Ikut Sistem",
            icon = Icons.Default.DarkMode,
            color = Color(0xFF37474F),
            onClick = { nav.navigate(Routes.TEMA) }
        ))
        sys.add(SettingItem(
            key = "tema_warna",
            title = "Warna Brand",
            subtitle = "Ganti warna utama aplikasi",
            icon = Icons.Default.Palette,
            color = Color(0xFFF57C00),
            onClick = { nav.navigate(Routes.TEMA_WARNA) }
        ))
        if (Session.can(PermissionKey.BACKUP_RESTORE)) {
            sys.add(SettingItem(
                key = "backup",
                title = "Backup & Restore",
                subtitle = "Simpan / pulihkan data ke file",
                icon = Icons.Default.Backup,
                color = Color(0xFF607D8B),
                onClick = { nav.navigate(Routes.BACKUP) }
            ))
        }
        add("TAMPILAN & SISTEM" to sys)

        // TENTANG
        add("TENTANG" to listOf(
            SettingItem(
                key = "tentang",
                title = "Tentang Aplikasi",
                subtitle = "iyonzkasir v0.8.0",
                icon = Icons.Default.Info,
                color = Color(0xFF546E7A),
                onClick = { nav.navigate(Routes.TENTANG) }
            )
        ))
    }

    val filteredGroups = if (searchQuery.isBlank()) groups
    else groups.mapNotNull { (title, items) ->
        val f = items.filter {
            it.title.contains(searchQuery, true) ||
            it.subtitle.contains(searchQuery, true)
        }
        if (f.isEmpty()) null else title to f
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
            item {
                Card(
                    Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = BRAND_LIGHT)
                ) {
                    Row(Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(56.dp).clip(CircleShape).background(BRAND),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.Storefront, null, tint = Color.White) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(namaToko.ifBlank { "Toko Saya" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold)
                            Text("${businessType.emoji} ${businessType.label}",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        if (Session.can(PermissionKey.PROFIL_TOKO)) {
                            IconButton(onClick = { nav.navigate(Routes.PROFIL_TOKO) }) {
                                Icon(Icons.Default.Edit, null, tint = BRAND)
                            }
                        }
                    }
                }
            }

            user?.let { u ->
                item {
                    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Row(Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
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

            item {
                OutlinedTextField(
                    value = searchQuery, onValueChange = { searchQuery = it },
                    placeholder = { Text("Cari pengaturan...",
                        style = MaterialTheme.typography.bodySmall) },
                    leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" },
                                modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, null, Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp).height(52.dp)
                )
            }

            if (filteredGroups.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(48.dp),
                        contentAlignment = Alignment.Center) {
                        Text("Nggak ada yang cocok",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                filteredGroups.forEach { (title, items) ->
                    item {
                        Text(title,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold, color = BRAND,
                            modifier = Modifier.padding(
                                start = 20.dp, top = 16.dp, bottom = 6.dp
                            ))
                    }
                    items(items, key = { it.key }) { SettingRow(it) }
                }
            }

            item {
                Box(Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center) {
                    Text("iyonzkasir v0.8.0 • Made with ❤️",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SettingRow(item: SettingItem) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = item.onClick),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                    .background(item.color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) { Icon(item.icon, null, Modifier.size(22.dp), item.color) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, fontWeight = FontWeight.Medium)
                if (item.subtitle.isNotBlank()) {
                    Text(item.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2)
                }
            }
            Icon(Icons.Default.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    HorizontalDivider(Modifier.padding(start = 70.dp), thickness = 0.5.dp)
}

// ═══════════════════════════════════════════════════════════
// TEMA WARNA BRAND
// ═══════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemaWarnaRoute(app: IyonzApp, nav: NavHostController) {
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf(AppTheme.ORANGE) }

    LaunchedEffect(Unit) { selected = app.settingRepo.getAppTheme() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Warna Brand") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BRAND, titleContentColor = Color.White
                )
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = BRAND_LIGHT)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Warna Aktif",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(40.dp).clip(CircleShape)
                                    .background(BrandColors.primary)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(BrandColors.theme.label,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Pilih warna di bawah — semua tombol, header, & aksen berubah otomatis",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            items(AppTheme.values().toList()) { theme ->
                val isSelected = selected == theme
                Card(
                    Modifier.fillMaxWidth().clickable {
                        selected = theme
                        ThemeManager.updateAppTheme(theme)
                        scope.launch { app.settingRepo.setAppTheme(theme) }
                    },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) Color(theme.lightHex)
                        else MaterialTheme.colorScheme.surface
                    ),
                    border = if (isSelected)
                        androidx.compose.foundation.BorderStroke(2.dp, Color(theme.primaryHex))
                    else null
                ) {
                    Row(Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(72.dp).height(40.dp)) {
                            Box(Modifier.size(32.dp).clip(CircleShape)
                                .background(Color(theme.lightHex))
                                .align(Alignment.BottomStart))
                            Box(Modifier.size(36.dp).clip(CircleShape)
                                .background(Color(theme.primaryHex))
                                .align(Alignment.Center))
                            Box(Modifier.size(28.dp).clip(CircleShape)
                                .background(Color(theme.darkHex))
                                .align(Alignment.TopEnd))
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${theme.emoji} ${theme.label}",
                                fontWeight = FontWeight.SemiBold)
                            Text("Primary color",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        RadioButton(selected = isSelected, onClick = null)
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// TEMPLATE STRUK
// ═══════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateStrukRoute(app: IyonzApp, nav: NavHostController) {
    val scope = rememberCoroutineScope()
    var showKasir by remember { mutableStateOf(true) }
    var showMeja by remember { mutableStateOf(true) }
    var showPelanggan by remember { mutableStateOf(true) }
    var showCatatan by remember { mutableStateOf(true) }
    var showAntrian by remember { mutableStateOf(true) }
    var showPoin by remember { mutableStateOf(true) }
    var showAlamat by remember { mutableStateOf(true) }
    var showTelepon by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        showKasir = app.settingRepo.isStrukShowKasir()
        showMeja = app.settingRepo.isStrukShowMeja()
        showPelanggan = app.settingRepo.isStrukShowPelanggan()
        showCatatan = app.settingRepo.isStrukShowCatatan()
        showAntrian = app.settingRepo.isStrukShowAntrian()
        showPoin = app.settingRepo.isStrukShowPoin()
        showAlamat = app.settingRepo.isStrukShowAlamat()
        showTelepon = app.settingRepo.isStrukShowTelepon()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Template Struk") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BRAND, titleContentColor = Color.White
                )
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = BRAND_LIGHT)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Receipt, null,
                                tint = BRAND, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Custom Struk", fontWeight = FontWeight.Bold)
                                Text("Atur field apa saja yang muncul di struk",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            item {
                Text("Info Toko", fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge, color = BRAND)
            }
            item {
                ToggleRow("Tampilkan Alamat",
                    "Alamat toko di bagian atas", showAlamat) {
                    showAlamat = it
                    scope.launch { app.settingRepo.setStrukShowAlamat(it) }
                }
            }
            item {
                ToggleRow("Tampilkan Telepon",
                    "Nomor telepon / WA di header", showTelepon) {
                    showTelepon = it
                    scope.launch { app.settingRepo.setStrukShowTelepon(it) }
                }
            }

            item { Text("Info Pesanan", fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge, color = BRAND) }

            item {
                ToggleRow("Tampilkan Nama Kasir",
                    "Siapa yang melayani transaksi", showKasir) {
                    showKasir = it
                    scope.launch { app.settingRepo.setStrukShowKasir(it) }
                }
            }
            item {
                ToggleRow("Tampilkan Nomor Meja",
                    "Meja untuk Dine-in / F&B", showMeja) {
                    showMeja = it
                    scope.launch { app.settingRepo.setStrukShowMeja(it) }
                }
            }
            item {
                ToggleRow("Tampilkan Nama Pelanggan",
                    "Nama & member pelanggan", showPelanggan) {
                    showPelanggan = it
                    scope.launch { app.settingRepo.setStrukShowPelanggan(it) }
                }
            }
            item {
                ToggleRow("Tampilkan Nomor Antrian",
                    "Nomor antrian pesanan", showAntrian) {
                    showAntrian = it
                    scope.launch { app.settingRepo.setStrukShowAntrian(it) }
                }
            }
            item {
                ToggleRow("Tampilkan Catatan Item",
                    "Catatan per item (pedas, dll)", showCatatan) {
                    showCatatan = it
                    scope.launch { app.settingRepo.setStrukShowCatatan(it) }
                }
            }
            item {
                ToggleRow("Tampilkan Poin Member",
                    "Poin yang didapat dari order", showPoin) {
                    showPoin = it
                    scope.launch { app.settingRepo.setStrukShowPoin(it) }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String, subtitle: String, value: Boolean,
    onChange: (Boolean) -> Unit
) {
    Card {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium)
                Text(subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = value, onCheckedChange = onChange)
        }
    }
}

// ═══════════════════════════════════════════════════════════
// NOMOR ANTRIAN
// ═══════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AntrianRoute(app: IyonzApp, nav: NavHostController) {
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(false) }
    var prefix by remember { mutableStateOf("") }
    var nextNumber by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        enabled = app.settingRepo.isAntrianEnabled()
        prefix = app.settingRepo.getAntrianPrefix()
        nextNumber = try { app.posRepo.nextNomorAntrian() }
        catch (_: Exception) { 1 }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nomor Antrian") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BRAND, titleContentColor = Color.White
                )
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = BRAND_LIGHT)) {
                    Row(Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ConfirmationNumber, null,
                            tint = BRAND, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Nomor Antrian Otomatis",
                                fontWeight = FontWeight.Bold)
                            Text("Auto-generate untuk setiap order",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = enabled, onCheckedChange = {
                            enabled = it
                            scope.launch { app.settingRepo.setAntrianEnabled(it) }
                        })
                    }
                }
            }

            if (enabled) {
                item {
                    Card {
                        Column(Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Prefix (Opsional)", fontWeight = FontWeight.SemiBold)
                            OutlinedTextField(
                                value = prefix,
                                onValueChange = {
                                    prefix = it.take(5)
                                    scope.launch { app.settingRepo.setAntrianPrefix(prefix) }
                                },
                                label = { Text("cth: A-, ORD-") },
                                placeholder = { Text("Kosongin kalau nggak pakai") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text("Nomor akan jadi: ${prefix}${nextNumber}",
                                style = MaterialTheme.typography.bodySmall,
                                color = BRAND, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                item {
                    Card {
                        Column(Modifier.padding(16.dp)) {
                            Text("Contoh Tampilan", fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("${prefix}${nextNumber}",
                                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                    style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.Bold, color = BRAND,
                                    textAlign = TextAlign.Center)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text("Nomor reset otomatis setiap hari",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// KEUANGAN & PAJAK
// ═══════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeuanganRoute(app: IyonzApp, nav: NavHostController) {
    val scope = rememberCoroutineScope()
    var pajakDefault by remember { mutableIntStateOf(0) }
    var soundEnabled by remember { mutableStateOf(true) }
    var metodeAktif by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(Unit) {
        pajakDefault = app.settingRepo.getPajakDefault()
        soundEnabled = app.settingRepo.isSoundEnabled()
        metodeAktif = app.settingRepo.getMetodeAktif()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Keuangan & Pajak") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BRAND, titleContentColor = Color.White
                )
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF43A047).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Receipt, null,
                                    Modifier.size(22.dp), Color(0xFF43A047))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("PPN Default", fontWeight = FontWeight.Bold)
                                Text("Otomatis diterapkan saat transaksi",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0, 5, 10, 11, 12, 15).forEach { p ->
                                FilterChip(
                                    selected = pajakDefault == p,
                                    onClick = {
                                        pajakDefault = p
                                        scope.launch { app.settingRepo.setPajakDefault(p) }
                                    },
                                    label = { Text(if (p == 0) "Off" else "$p%",
                                        style = MaterialTheme.typography.bodySmall) }
                                )
                            }
                        }
                    }
                }
            }

            item {
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF1E88E5).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.CreditCard, null,
                                    Modifier.size(22.dp), Color(0xFF1E88E5))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Metode Pembayaran", fontWeight = FontWeight.Bold)
                                Text("Pilih metode yang tersedia di kasir",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        PaymentMethod.values().forEach { m ->
                            val aktif = m.id in metodeAktif
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable {
                                        val newSet = if (aktif) metodeAktif - m.id
                                        else metodeAktif + m.id
                                        metodeAktif = newSet
                                        scope.launch { app.settingRepo.setMetodeAktif(newSet) }
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = aktif,
                                    onCheckedChange = { c ->
                                        val newSet = if (c) metodeAktif + m.id
                                        else metodeAktif - m.id
                                        metodeAktif = newSet
                                        scope.launch { app.settingRepo.setMetodeAktif(newSet) }
                                    }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(m.label)
                            }
                        }
                    }
                }
            }

            item {
                Card {
                    Row(Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF9C27B0).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.VolumeUp, null,
                                Modifier.size(22.dp), Color(0xFF9C27B0))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Suara", fontWeight = FontWeight.Bold)
                            Text("Bunyi saat transaksi berhasil",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = soundEnabled, onCheckedChange = {
                            soundEnabled = it
                            scope.launch { app.settingRepo.setSoundEnabled(it) }
                        })
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// BARCODE INFO
// ═══════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeInfoRoute(app: IyonzApp, nav: NavHostController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Barcode Scanner") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BRAND, titleContentColor = Color.White
                )
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = BRAND_LIGHT)) {
                    Row(Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.QrCodeScanner, null,
                            Modifier.size(40.dp), BRAND)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Barcode Aktif",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium)
                            Text("Scan dari kamera / galeri",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            item {
                Card {
                    Column(Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Cara Pakai", fontWeight = FontWeight.Bold)
                        Text("1. Edit menu → isi field Barcode",
                            style = MaterialTheme.typography.bodySmall)
                        Text("2. Buka Kasir → tap ikon scan di kanan atas",
                            style = MaterialTheme.typography.bodySmall)
                        Text("3. Arahkan kamera → auto masuk ke keranjang",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("💡 Format Didukung",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodySmall)
                        Text("EAN-13, EAN-8, UPC, Code-128, Code-39, QR, Data Matrix, PDF417, Aztec",
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// TEMA TERANG/GELAP
// ═══════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemaRoute(app: IyonzApp, nav: NavHostController) {
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf(ThemeManager.mode) }

    LaunchedEffect(Unit) { selected = app.settingRepo.getThemeMode() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tema") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Pilih mode terang / gelap",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            ThemeMode.values().forEach { mode ->
                val isSelected = selected == mode
                Card(
                    Modifier.fillMaxWidth().clickable {
                        selected = mode
                        ThemeManager.update(mode)
                        scope.launch { app.settingRepo.setThemeMode(mode) }
                    },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) BRAND_LIGHT
                        else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(when (mode) {
                            ThemeMode.SYSTEM -> Icons.Default.SettingsBrightness
                            ThemeMode.LIGHT -> Icons.Default.LightMode
                            ThemeMode.DARK -> Icons.Default.DarkMode
                        }, null, tint = BRAND)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(mode.label, fontWeight = FontWeight.SemiBold)
                            Text(when (mode) {
                                ThemeMode.SYSTEM -> "Ikut pengaturan HP"
                                ThemeMode.LIGHT -> "Selalu terang"
                                ThemeMode.DARK -> "Selalu gelap"
                            }, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        RadioButton(selected = isSelected, onClick = null)
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// FEATURE TOGGLE
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
                    key.label.contains(searchQuery, true) ||
                    key.key.contains(searchQuery, true)
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
                placeholder = { Text("Cari fitur...",
                    style = MaterialTheme.typography.bodySmall) },
                leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(18.dp)) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().padding(12.dp).height(52.dp)
            )
            LazyColumn(Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)) {
                grouped.forEach { (kategori, items) ->
                    item {
                        Text("$kategori (${items.count { it.second }}/${items.size})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold, color = BRAND,
                            modifier = Modifier.padding(
                                start = 20.dp, top = 16.dp, bottom = 6.dp
                            ))
                    }
                    items(items, key = { it.first.key }) { (feature, enabled) ->
                        Surface(Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surface) {
                            Row(Modifier.fillMaxWidth().padding(16.dp, 12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(feature.label, fontWeight = FontWeight.Medium)
                                    if (feature.deskripsi.isNotBlank()) {
                                        Text(feature.deskripsi,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Switch(checked = enabled, onCheckedChange = { newValue ->
                                    scope.launch {
                                        app.featureRepo.setEnabled(feature, newValue)
                                        Session.current?.let { u ->
                                            app.userRepo.log(u.id, u.nama,
                                                if (newValue) "FEATURE_ON" else "FEATURE_OFF",
                                                targetId = feature.key,
                                                keterangan = feature.label)
                                        }
                                    }
                                })
                            }
                        }
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset ke Preset?") },
            text = { Text("Semua fitur akan dikembalikan ke default sesuai jenis usaha kamu.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val bt = app.settingRepo.getBusinessType()
                        if (bt == BusinessType.CUSTOM) app.featureRepo.enableAll()
                        else app.featureRepo.applyPreset(bt)
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

// ═══════════════════════════════════════════════════════════
// PROFIL TOKO
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
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
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
            LazyColumn(
                Modifier.padding(pad).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Column(Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally) {
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
                        Spacer(Modifier.height(8.dp))
                        Text("Tap logo untuk ganti",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item {
                    OutlinedTextField(nama, { nama = it },
                        label = { Text("Nama toko *") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
                item {
                    OutlinedTextField(alamat, { alamat = it },
                        label = { Text("Alamat") },
                        modifier = Modifier.fillMaxWidth(), maxLines = 2)
                }
                item {
                    OutlinedTextField(telepon,
                        { telepon = it.filter { c -> c.isDigit() || c == '+' } },
                        label = { Text("No. WhatsApp / Telp") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
                item {
                    OutlinedTextField(footer, { footer = it },
                        label = { Text("Footer struk") },
                        modifier = Modifier.fillMaxWidth(), maxLines = 2)
                }
                item {
                    Text("Jenis Usaha", fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp))
                }
                items(BusinessType.values().toList()) { bt ->
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
                                Text(bt.deskripsi,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            RadioButton(selected = selected,
                                onClick = { businessType = bt })
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
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
        Column(Modifier.padding(pad).fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(24.dp))
            Box(Modifier.size(96.dp).clip(RoundedCornerShape(24.dp)).background(BRAND),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Default.PointOfSale, null,
                    tint = Color.White, modifier = Modifier.size(56.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text("iyonzkasir", style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold, color = BRAND)
            Text("v0.8.0", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            Text("Aplikasi kasir serbaguna untuk semua jenis usaha:\n" +
                 "warung, retail, cafe, restoran, laundry, toko bangunan, dan jasa.\n\n" +
                 "Offline by default, simple by default, aman by design.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center)
            Spacer(Modifier.weight(1f))
            Text("© 2025 iyonzkasir",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
