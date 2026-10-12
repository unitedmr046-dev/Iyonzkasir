package com.iyonzkasir.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
    val bundlingEnabled = FeatureKey.BUNDLING in enabled

    val groups = buildList {
        // ═══ TOKO ═══
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
        if (bundlingEnabled && Session.can(PermissionKey.KELOLA_MENU)) {
            toko.add(SettingItem(
                key = "bundling",
                title = "Paket Bundling",
                subtitle = "Paket combo / bundling menu",
                icon = Icons.Default.Inventory,
                color = Color(0xFFFB8C00),
                onClick = { nav.navigate(Routes.BUNDLING) }
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

        // ═══ TRANSAKSI ═══
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
        if (Session.can(PermissionKey.KELOLA_FITUR)) {
            trx.add(SettingItem(
                key = "sync_web",
                title = "Sinkronisasi Web",
                subtitle = "Sync menu ke web menu digital",
                icon = Icons.Default.CloudSync,
                color = Color(0xFF00897B),
                onClick = { nav.navigate(Routes.SYNC_WEB) }
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

        // ═══ OUTPUT ═══
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

        // ═══ TAMPILAN & SISTEM ═══
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

        // ═══ TENTANG ═══
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
            contentPadding = PaddingValues(bottom = Sp.xxl)
        ) {
            // ═══ STORE HEADER CARD ═══
            item {
                Box(Modifier.padding(Sp.lg)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(Rd.cardLg)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(BRAND, BRAND_DARK)
                                )
                            )
                            .padding(Sp.lg)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(56.dp).clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.2f))
                                    .border(
                                        1.5.dp,
                                        Color.White.copy(alpha = 0.3f),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Storefront, null,
                                    tint = Color.White, modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(Modifier.width(Sp.md))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    namaToko.ifBlank { "Toko Saya" },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${businessType.emoji} ${businessType.label}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            }
                            if (Session.can(PermissionKey.PROFIL_TOKO)) {
                                IconButton(
                                    onClick = { nav.navigate(Routes.PROFIL_TOKO) }
                                ) {
                                    Icon(
                                        Icons.Default.Edit, null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ═══ USER CARD ═══
            user?.let { u ->
                item {
                    Box(Modifier.padding(horizontal = Sp.lg)) {
                        AppCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(48.dp).clip(CircleShape)
                                        .background(BRAND.copy(alpha = 0.12f))
                                        .border(1.dp, BRAND.copy(alpha = 0.2f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (u.fotoUri != null) {
                                        AsyncImage(
                                            model = u.fotoUri,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Text(
                                            u.nama.take(1).uppercase(),
                                            color = BRAND,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Spacer(Modifier.width(Sp.md))
                                Column(Modifier.weight(1f)) {
                                    Text(u.nama,
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        UserRole.fromId(u.role).label,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = {
                                    scope.launch {
                                        app.userRepo.log(u.id, u.nama, "LOGOUT")
                                        Session.logout()
                                        nav.navigate(Routes.LOGIN) {
                                            popUpTo(0) { inclusive = true }
                                        }
                                    }
                                }) {
                                    Icon(Icons.Default.Logout, null,
                                        tint = DANGER, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Keluar", color = DANGER,
                                        fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }

            // ═══ SEARCH ═══
            item {
                OutlinedTextField(
                    value = searchQuery, onValueChange = { searchQuery = it },
                    placeholder = { Text("Cari pengaturan…",
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
                    shape = Rd.button,
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = Sp.lg, vertical = Sp.md)
                        .height(52.dp)
                )
            }

            // ═══ LIST ═══
            if (filteredGroups.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(Sp.huge),
                        contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.SearchOff, null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(40.dp))
                            Spacer(Modifier.height(Sp.sm))
                            Text("Nggak ada yang cocok",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                filteredGroups.forEach { (title, items) ->
                    item {
                        SectionHeader(title = title)
                    }
                    items(items, key = { it.key }) { item ->
                        Box(Modifier.padding(horizontal = Sp.lg)) {
                            SettingRowNew(item)
                        }
                    }
                }
            }

            item {
                Box(Modifier.fillMaxWidth().padding(Sp.xxl),
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
private fun SettingRowNew(item: SettingItem) {
    Box(Modifier.padding(bottom = Sp.sm)) {
        AppCard(onClick = item.onClick) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(Rd.sm))
                        .background(item.color.copy(alpha = 0.12f))
                        .border(
                            1.dp,
                            item.color.copy(alpha = 0.15f),
                            RoundedCornerShape(Rd.sm)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(item.icon, null, Modifier.size(22.dp), item.color)
                }
                Spacer(Modifier.width(Sp.md))
                Column(Modifier.weight(1f)) {
                    Text(item.title, fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleSmall)
                    if (item.subtitle.isNotBlank()) {
                        Text(item.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis)
                    }
                }
                Icon(Icons.Default.ChevronRight, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp))
            }
        }
    }
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
                    containerColor = BRAND, titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(Sp.lg),
            verticalArrangement = Arrangement.spacedBy(Sp.md)
        ) {
            item {
                AppCard(
                    color = BRAND.copy(alpha = 0.08f),
                    outlined = true
                ) {
                    Text("Warna Aktif",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(Sp.sm))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(48.dp).clip(CircleShape)
                                .background(BrandColors.primary)
                                .border(2.dp, Color.White, CircleShape)
                        )
                        Spacer(Modifier.width(Sp.md))
                        Text(
                            BrandColors.theme.label,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(Modifier.height(Sp.sm))
                    Text(
                        "Pilih warna di bawah — semua tombol, header, & aksen berubah otomatis",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(AppTheme.values().toList()) { theme ->
                val isSelected = selected == theme
                AppCard(
                    onClick = {
                        selected = theme
                        ThemeManager.updateAppTheme(theme)
                        scope.launch { app.settingRepo.setAppTheme(theme) }
                    },
                    outlined = !isSelected,
                    color = if (isSelected) Color(theme.lightHex)
                    else MaterialTheme.colorScheme.surface
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(80.dp).height(44.dp)) {
                            Box(
                                Modifier.size(34.dp).clip(CircleShape)
                                    .background(Color(theme.lightHex))
                                    .align(Alignment.BottomStart)
                            )
                            Box(
                                Modifier.size(38.dp).clip(CircleShape)
                                    .background(Color(theme.primaryHex))
                                    .align(Alignment.Center)
                                    .border(2.dp, Color.White, CircleShape)
                            )
                            Box(
                                Modifier.size(30.dp).clip(CircleShape)
                                    .background(Color(theme.darkHex))
                                    .align(Alignment.TopEnd)
                            )
                        }
                        Spacer(Modifier.width(Sp.md))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${theme.emoji} ${theme.label}",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                "Primary color",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isSelected) {
                            Icon(
                                Icons.Default.CheckCircle, null,
                                tint = Color(theme.primaryHex),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(Sp.xxl)) }
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
                    containerColor = BRAND, titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(Sp.lg),
            verticalArrangement = Arrangement.spacedBy(Sp.sm)
        ) {
            item {
                AppCard(
                    color = BRAND.copy(alpha = 0.08f),
                    outlined = true
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconCircle(
                            Icons.Default.Receipt, tint = BRAND,
                            size = 48.dp, iconSize = 24.dp
                        )
                        Spacer(Modifier.width(Sp.md))
                        Column(Modifier.weight(1f)) {
                            Text("Custom Struk", fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall)
                            Text("Atur field apa saja yang muncul di struk",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            item {
                SectionHeader(title = "INFO TOKO")
            }
            item {
                ToggleRowNew(
                    "Tampilkan Alamat",
                    "Alamat toko di bagian atas",
                    showAlamat
                ) { showAlamat = it
                    scope.launch { app.settingRepo.setStrukShowAlamat(it) }
                }
            }
            item {
                ToggleRowNew(
                    "Tampilkan Telepon",
                    "Nomor telepon / WA di header",
                    showTelepon
                ) { showTelepon = it
                    scope.launch { app.settingRepo.setStrukShowTelepon(it) }
                }
            }

            item {
                SectionHeader(title = "INFO PESANAN")
            }
            item {
                ToggleRowNew(
                    "Tampilkan Nama Kasir",
                    "Siapa yang melayani transaksi",
                    showKasir
                ) { showKasir = it
                    scope.launch { app.settingRepo.setStrukShowKasir(it) }
                }
            }
            item {
                ToggleRowNew(
                    "Tampilkan Nomor Meja",
                    "Meja untuk Dine-in / F&B",
                    showMeja
                ) { showMeja = it
                    scope.launch { app.settingRepo.setStrukShowMeja(it) }
                }
            }
            item {
                ToggleRowNew(
                    "Tampilkan Nama Pelanggan",
                    "Nama & member pelanggan",
                    showPelanggan
                ) { showPelanggan = it
                    scope.launch { app.settingRepo.setStrukShowPelanggan(it) }
                }
            }
            item {
                ToggleRowNew(
                    "Tampilkan Nomor Antrian",
                    "Nomor antrian pesanan",
                    showAntrian
                ) { showAntrian = it
                    scope.launch { app.settingRepo.setStrukShowAntrian(it) }
                }
            }
            item {
                ToggleRowNew(
                    "Tampilkan Catatan Item",
                    "Catatan per item (pedas, dll)",
                    showCatatan
                ) { showCatatan = it
                    scope.launch { app.settingRepo.setStrukShowCatatan(it) }
                }
            }
            item {
                ToggleRowNew(
                    "Tampilkan Poin Member",
                    "Poin yang didapat dari order",
                    showPoin
                ) { showPoin = it
                    scope.launch { app.settingRepo.setStrukShowPoin(it) }
                }
            }

            item { Spacer(Modifier.height(Sp.xxl)) }
        }
    }
}

@Composable
private fun ToggleRowNew(
    title: String, subtitle: String, value: Boolean,
    onChange: (Boolean) -> Unit
) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall)
                Text(subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(Sp.sm))
            Switch(
                checked = value,
                onCheckedChange = onChange,
                colors = SwitchDefaults.colors(checkedTrackColor = BRAND)
            )
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
                    containerColor = BRAND, titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(Sp.lg),
            verticalArrangement = Arrangement.spacedBy(Sp.md)
        ) {
            item {
                AppCard(
                    color = BRAND.copy(alpha = 0.08f),
                    outlined = true
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconCircle(
                            Icons.Default.ConfirmationNumber,
                            tint = BRAND,
                            size = 52.dp,
                            iconSize = 26.dp
                        )
                        Spacer(Modifier.width(Sp.md))
                        Column(Modifier.weight(1f)) {
                            Text("Nomor Antrian Otomatis",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall)
                            Text("Auto-generate untuk setiap order",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = enabled,
                            onCheckedChange = {
                                enabled = it
                                scope.launch { app.settingRepo.setAntrianEnabled(it) }
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = BRAND)
                        )
                    }
                }
            }

            if (enabled) {
                item {
                    AppCard {
                        Text("Prefix (Opsional)",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(Sp.sm))
                        OutlinedTextField(
                            value = prefix,
                            onValueChange = {
                                prefix = it.take(5)
                                scope.launch { app.settingRepo.setAntrianPrefix(prefix) }
                            },
                            label = { Text("cth: A-, ORD-") },
                            placeholder = { Text("Kosongin kalau nggak pakai") },
                            singleLine = true,
                            shape = Rd.button,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(Sp.sm))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, null,
                                tint = BRAND, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Nomor akan jadi: ${prefix}${nextNumber}",
                                style = MaterialTheme.typography.bodySmall,
                                color = BRAND, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                item {
                    AppCard {
                        Text("Contoh Tampilan", fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(Sp.sm))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(Rd.md),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "${prefix}${nextNumber}",
                                modifier = Modifier.padding(Sp.xl).fillMaxWidth(),
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Black,
                                color = BRAND,
                                textAlign = TextAlign.Center
                            )
                        }
                        Spacer(Modifier.height(Sp.sm))
                        Text("Nomor reset otomatis setiap hari",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    containerColor = BRAND, titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(Sp.lg),
            verticalArrangement = Arrangement.spacedBy(Sp.md)
        ) {
            item {
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconCircle(
                            Icons.Default.Receipt,
                            tint = Color(0xFF43A047),
                            size = 48.dp,
                            iconSize = 24.dp
                        )
                        Spacer(Modifier.width(Sp.md))
                        Column(Modifier.weight(1f)) {
                            Text("PPN Default", fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall)
                            Text("Otomatis diterapkan saat transaksi",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(Sp.md))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Sp.sm)) {
                        items(listOf(0, 5, 10, 11, 12, 15)) { p ->
                            AppChip(
                                label = if (p == 0) "Off" else "$p%",
                                selected = pajakDefault == p,
                                onClick = {
                                    pajakDefault = p
                                    scope.launch { app.settingRepo.setPajakDefault(p) }
                                }
                            )
                        }
                    }
                }
            }

            item {
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconCircle(
                            Icons.Default.CreditCard,
                            tint = Color(0xFF1E88E5),
                            size = 48.dp,
                            iconSize = 24.dp
                        )
                        Spacer(Modifier.width(Sp.md))
                        Column(Modifier.weight(1f)) {
                            Text("Metode Pembayaran",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall)
                            Text("Pilih metode yang tersedia di kasir",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(Sp.md))
                    PaymentMethod.values().forEach { m ->
                        val aktif = m.id in metodeAktif
                        Row(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(Rd.sm))
                                .clickable {
                                    val newSet = if (aktif) metodeAktif - m.id
                                    else metodeAktif + m.id
                                    metodeAktif = newSet
                                    scope.launch {
                                        app.settingRepo.setMetodeAktif(newSet)
                                    }
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = aktif,
                                onCheckedChange = { c ->
                                    val newSet = if (c) metodeAktif + m.id
                                    else metodeAktif - m.id
                                    metodeAktif = newSet
                                    scope.launch {
                                        app.settingRepo.setMetodeAktif(newSet)
                                    }
                                },
                                colors = CheckboxDefaults.colors(checkedColor = BRAND)
                            )
                            Spacer(Modifier.width(Sp.sm))
                            Text(m.label,
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            item {
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconCircle(
                            Icons.Default.VolumeUp,
                            tint = Color(0xFF9C27B0),
                            size = 48.dp,
                            iconSize = 24.dp
                        )
                        Spacer(Modifier.width(Sp.md))
                        Column(Modifier.weight(1f)) {
                            Text("Suara", fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall)
                            Text("Bunyi saat transaksi berhasil",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = soundEnabled,
                            onCheckedChange = {
                                soundEnabled = it
                                scope.launch { app.settingRepo.setSoundEnabled(it) }
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = BRAND)
                        )
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
                    containerColor = BRAND, titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(Sp.lg),
            verticalArrangement = Arrangement.spacedBy(Sp.md)
        ) {
            item {
                AppCard(
                    color = BRAND.copy(alpha = 0.08f),
                    outlined = true
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconCircle(
                            Icons.Default.QrCodeScanner,
                            tint = BRAND,
                            size = 56.dp,
                            iconSize = 28.dp
                        )
                        Spacer(Modifier.width(Sp.md))
                        Column(Modifier.weight(1f)) {
                            Text("Barcode Aktif",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium)
                            Text("Scan dari kamera / galeri",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                AppCard {
                    Text("Cara Pakai", fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(Sp.sm))
                    StepRow(1, "Edit menu → isi field Barcode")
                    StepRow(2, "Buka Kasir → tap ikon scan di kanan atas")
                    StepRow(3, "Arahkan kamera → auto masuk ke keranjang")
                }
            }
            item {
                AppCard(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    outlined = true
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("💡", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.width(Sp.sm))
                        Text("Format Didukung",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(Modifier.height(Sp.xs))
                    Text(
                        "EAN-13, EAN-8, UPC, Code-128, Code-39, QR, Data Matrix, PDF417, Aztec",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun StepRow(num: Int, text: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(24.dp).clip(CircleShape)
                .background(BRAND),
            contentAlignment = Alignment.Center
        ) {
            Text(
                num.toString(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium
            )
        }
        Spacer(Modifier.width(Sp.md))
        Text(text, style = MaterialTheme.typography.bodyMedium)
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
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BRAND, titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().padding(Sp.lg),
            verticalArrangement = Arrangement.spacedBy(Sp.md)
        ) {
            Text("Pilih mode terang / gelap",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            ThemeMode.values().forEach { mode ->
                val isSelected = selected == mode
                AppCard(
                    onClick = {
                        selected = mode
                        ThemeManager.update(mode)
                        scope.launch { app.settingRepo.setThemeMode(mode) }
                    },
                    outlined = !isSelected,
                    color = if (isSelected) BRAND.copy(alpha = 0.08f)
                    else MaterialTheme.colorScheme.surface
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconCircle(
                            icon = when (mode) {
                                ThemeMode.SYSTEM -> Icons.Default.SettingsBrightness
                                ThemeMode.LIGHT -> Icons.Default.LightMode
                                ThemeMode.DARK -> Icons.Default.DarkMode
                            },
                            tint = BRAND,
                            size = 48.dp,
                            iconSize = 24.dp
                        )
                        Spacer(Modifier.width(Sp.md))
                        Column(Modifier.weight(1f)) {
                            Text(mode.label, fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.titleSmall)
                            Text(
                                when (mode) {
                                    ThemeMode.SYSTEM -> "Ikut pengaturan HP"
                                    ThemeMode.LIGHT -> "Selalu terang"
                                    ThemeMode.DARK -> "Selalu gelap"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isSelected) {
                            Icon(Icons.Default.CheckCircle, null,
                                tint = BRAND, modifier = Modifier.size(24.dp))
                        }
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
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BRAND, titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                value = searchQuery, onValueChange = { searchQuery = it },
                placeholder = { Text("Cari fitur…",
                    style = MaterialTheme.typography.bodySmall) },
                leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(18.dp)) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                shape = Rd.button,
                modifier = Modifier.fillMaxWidth()
                    .padding(Sp.md).height(52.dp)
            )
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = Sp.xxl)
            ) {
                grouped.forEach { (kategori, items) ->
                    item {
                        SectionHeader(
                            title = kategori.uppercase(),
                            subtitle = "${items.count { it.second }}/${items.size} aktif"
                        )
                    }
                    items(items, key = { it.first.key }) { (feature, enabled) ->
                        Box(Modifier.padding(horizontal = Sp.lg, vertical = 3.dp)) {
                            AppCard {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(feature.label,
                                            fontWeight = FontWeight.SemiBold,
                                            style = MaterialTheme.typography.titleSmall)
                                        if (feature.deskripsi.isNotBlank()) {
                                            Text(feature.deskripsi,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                    Spacer(Modifier.width(Sp.sm))
                                    Switch(
                                        checked = enabled,
                                        onCheckedChange = { newValue ->
                                            scope.launch {
                                                app.featureRepo.setEnabled(feature, newValue)
                                                Session.current?.let { u ->
                                                    app.userRepo.log(
                                                        u.id, u.nama,
                                                        if (newValue) "FEATURE_ON"
                                                        else "FEATURE_OFF",
                                                        targetId = feature.key,
                                                        keterangan = feature.label
                                                    )
                                                }
                                            }
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedTrackColor = BRAND
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(Sp.xxl)) }
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset ke Preset?", fontWeight = FontWeight.Bold) },
            text = { Text("Semua fitur akan dikembalikan ke default sesuai jenis usaha kamu.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val bt = app.settingRepo.getBusinessType()
                        if (bt == BusinessType.CUSTOM) app.featureRepo.enableAll()
                        else app.featureRepo.applyPreset(bt)
                        showResetDialog = false
                    }
                }) { Text("Reset", color = BRAND, fontWeight = FontWeight.Bold) }
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
    var saving by remember { mutableStateOf(false) }

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
                    TextButton(
                        onClick = {
                            saving = true
                            scope.launch {
                                app.settingRepo.set(
                                    SettingRepository.KEY_NAMA_TOKO, nama.trim())
                                app.settingRepo.set(
                                    SettingRepository.KEY_ALAMAT, alamat.trim())
                                app.settingRepo.set(
                                    SettingRepository.KEY_TELEPON, telepon.trim())
                                app.settingRepo.set(
                                    SettingRepository.KEY_FOOTER, footer.trim())
                                app.settingRepo.set(
                                    SettingRepository.KEY_LOGO, logoUri ?: "")
                                app.settingRepo.set(
                                    SettingRepository.KEY_BUSINESS_TYPE,
                                    businessType.id)
                                Session.current?.let { u ->
                                    app.userRepo.log(u.id, u.nama, "EDIT_PROFIL_TOKO")
                                }
                                saving = false
                                nav.popBackStack()
                            }
                        },
                        enabled = !saving
                    ) {
                        Text("Simpan", color = Color.White,
                            fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BRAND, titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { pad ->
        if (!loaded) {
            Box(Modifier.padding(pad).fillMaxSize(),
                contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BRAND)
            }
        } else {
            LazyColumn(
                Modifier.padding(pad).fillMaxSize(),
                contentPadding = PaddingValues(Sp.lg),
                verticalArrangement = Arrangement.spacedBy(Sp.md)
            ) {
                // Logo picker
                item {
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            Modifier.size(120.dp).clip(CircleShape)
                                .background(BRAND.copy(alpha = 0.1f))
                                .border(2.dp, BRAND.copy(alpha = 0.2f), CircleShape)
                                .clickable { picker.launch(arrayOf("image/*")) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (logoUri != null) {
                                AsyncImage(
                                    model = logoUri,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    Icons.Default.AddPhotoAlternate, null,
                                    tint = BRAND,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                            // Edit badge
                            Surface(
                                modifier = Modifier.align(Alignment.BottomEnd),
                                color = BRAND,
                                shape = CircleShape,
                                shadowElevation = 4.dp
                            ) {
                                Box(Modifier.size(32.dp),
                                    contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Edit, null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        Spacer(Modifier.height(Sp.sm))
                        Text("Tap logo untuk ganti",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                item {
                    SectionHeader(title = "INFO TOKO")
                }

                item {
                    OutlinedTextField(
                        nama, { nama = it },
                        label = { Text("Nama toko *") },
                        leadingIcon = { Icon(Icons.Default.Store, null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = Rd.button
                    )
                }
                item {
                    OutlinedTextField(
                        alamat, { alamat = it },
                        label = { Text("Alamat") },
                        leadingIcon = { Icon(Icons.Default.LocationOn, null) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2,
                        shape = Rd.button
                    )
                }
                item {
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
                }
                item {
                    OutlinedTextField(
                        footer, { footer = it },
                        label = { Text("Footer struk") },
                        leadingIcon = { Icon(Icons.Default.Receipt, null) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2,
                        shape = Rd.button
                    )
                }

                item {
                    SectionHeader(title = "JENIS USAHA")
                }

                items(BusinessType.values().toList()) { bt ->
                    val selected = businessType == bt
                    AppCard(
                        onClick = { businessType = bt },
                        outlined = !selected,
                        color = if (selected) BRAND.copy(alpha = 0.08f)
                        else MaterialTheme.colorScheme.surface
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(48.dp).clip(RoundedCornerShape(Rd.md))
                                    .background(
                                        if (selected) BRAND.copy(alpha = 0.15f)
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(bt.emoji,
                                    style = MaterialTheme.typography.titleLarge)
                            }
                            Spacer(Modifier.width(Sp.md))
                            Column(Modifier.weight(1f)) {
                                Text(bt.label, fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.titleSmall)
                                Text(bt.deskripsi,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis)
                            }
                            if (selected) {
                                Icon(Icons.Default.CheckCircle, null,
                                    tint = BRAND, modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(Sp.xxl)) }
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
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BRAND, titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().padding(Sp.xxl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(Sp.xxl))

            // App icon
            Box(
                Modifier.size(120.dp).clip(RoundedCornerShape(Rd.xl))
                    .background(
                        Brush.linearGradient(listOf(BRAND, BRAND_DARK))
                    )
                    .shadow(12.dp, RoundedCornerShape(Rd.xl)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PointOfSale, null,
                    tint = Color.White,
                    modifier = Modifier.size(64.dp)
                )
            }

            Spacer(Modifier.height(Sp.lg))
            Text(
                "iyonzkasir",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
                color = BRAND
            )
            Text(
                "v0.8.0",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(Sp.xxl))

            AppCard {
                Text(
                    "Aplikasi kasir serbaguna untuk semua jenis usaha: warung, retail, cafe, restoran, laundry, toko bangunan, dan jasa.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(Sp.md))
                AppDivider()
                Spacer(Modifier.height(Sp.md))
                Row(horizontalArrangement = Arrangement.spacedBy(Sp.sm)) {
                    StatMini("⚡", "Offline", "Jalan tanpa internet")
                }
                Spacer(Modifier.height(Sp.sm))
                StatMini("🔒", "Aman", "Backup terenkripsi")
                Spacer(Modifier.height(Sp.sm))
                StatMini("🎨", "Custom", "Fitur bisa diatur")
            }

            Spacer(Modifier.weight(1f))
            Text(
                "© 2025 iyonzkasir",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatMini(emoji: String, title: String, desc: String) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.width(Sp.md))
        Column {
            Text(title, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall)
            Text(desc, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
