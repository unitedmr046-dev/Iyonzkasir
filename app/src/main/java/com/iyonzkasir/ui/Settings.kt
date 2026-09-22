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
import kotlinx.coroutines.launch

// ═══════════════════════════════════════════════════════════
// SETTINGS ITEM DATA
// ═══════════════════════════════════════════════════════════
private data class SettingItem(
    val key: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val color: Color,
    val onClick: () -> Unit
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
            FeatureKey.VOUCHER in enabled ||
            FeatureKey.HUTANG_PELANGGAN in enabled
    val stockEnabled = FeatureKey.LOW_STOCK_ALERT in enabled ||
            FeatureKey.POTONG_STOK in enabled ||
            FeatureKey.STOCK_OPNAME in enabled
    val kategoriEnabled = FeatureKey.KATEGORI_MGMT in enabled
    val barcodeEnabled = FeatureKey.BARCODE in enabled

    val groups = buildList {
        // TOKO
        val tokoItems = mutableListOf<SettingItem>()
        tokoItems.add(SettingItem(
            "profil", "Profil Toko",
            "Nama, alamat, telepon, footer struk",
            Icons.Default.Store, Color(0xFFFF6B35)
        ) { if (Session.can(PermissionKey.PROFIL_TOKO)) nav.navigate(Routes.PROFIL_TOKO) })
        if (kategoriEnabled && Session.can(PermissionKey.KELOLA_KATEGORI)) {
            tokoItems.add(SettingItem(
                "kategori", "Kelola Kategori",
                "Tambah/edit kategori & warna",
                Icons.Default.Category, Color(0xFF8E24AA)
            ) { nav.navigate(Routes.KATEGORI) })
        }
        if (Session.can(PermissionKey.KELOLA_USER)) {
            tokoItems.add(SettingItem(
                "user", "Kelola Pengguna",
                "Tambah user & atur izin",
                Icons.Default.ManageAccounts, Color(0xFF1E88E5)
            ) { nav.navigate(Routes.KELOLA_USER) })
        }
        if (tokoItems.isNotEmpty()) add("TOKO" to tokoItems)

        // TRANSAKSI
        val trxItems = mutableListOf<SettingItem>()
        trxItems.add(SettingItem(
            "pajak", "Keuangan & Pajak",
            "PPN default, metode bayar, profit",
            Icons.Default.Payments, Color(0xFF43A047)
        ) { nav.navigate(Routes.KEUANGAN) })
        if (stockEnabled && Session.can(PermissionKey.LIHAT_STOK)) {
            trxItems.add(SettingItem(
                "stok", "Kelola Stok",
                if (lowStockCount > 0) "⚠️ $lowStockCount menu stok menipis"
                else "Stok, opname, riwayat pergerakan",
                Icons.Default.Inventory, Color(0xFF00897B)
            ) { nav.navigate(Routes.INVENTARIS) })
        }
        if (Session.can(PermissionKey.KELOLA_FITUR)) {
            trxItems.add(SettingItem(
                "fitur", "Kelola Fitur",
                "Aktifkan / matikan fitur sesuai kebutuhan",
                Icons.Default.Tune, Color(0xFFFB8C00)
            ) { nav.navigate(Routes.FEATURE_TOGGLE) })
        }
        if (crmEnabled) {
            trxItems.add(SettingItem(
                "crm", "Member & Voucher",
                if (memberCount > 0) "$memberCount member terdaftar"
                else "Kelola member, poin & voucher",
                Icons.Default.People, Color(0xFF6D4C41)
            ) { nav.navigate(Routes.CRM) })
        }
        add("TRANSAKSI" to trxItems)

        // OUTPUT
        val outItems = mutableListOf<SettingItem>()
        outItems.add(SettingItem(
            "laporan", "Laporan & Laba",
            "Laporan lengkap, grafik, export CSV",
            Icons.Default.Analytics, Color(0xFF3949AB)
        ) { if (Session.can(PermissionKey.LIHAT_LAPORAN)) nav.navigate(Routes.LAPORAN) })
        if (FeatureManager.isEnabled(FeatureKey.PRINTER_BT)) {
            outItems.add(SettingItem(
                "printer", "Printer & Struk",
                "Sambungkan printer, ukuran kertas, format struk",
                Icons.Default.Print, Color(0xFFD81B60)
            ) { nav.navigate(Routes.PRINTER) })
        }
        if (barcodeEnabled) {
            outItems.add(SettingItem(
                "barcode", "Barcode Scanner",
                "Scan dari kamera / galeri",
                Icons.Default.QrCodeScanner, Color(0xFFE53935)
            ) { nav.navigate(Routes.BARCODE_INFO) })
        }
        add("OUTPUT" to outItems)

        // TAMPILAN & SISTEM
        val sysItems = mutableListOf<SettingItem>()
        sysItems.add(SettingItem(
            "tema", "Tema",
            "Terang / Gelap / Ikut Sistem",
            Icons.Default.Palette, Color(0xFF9C27B0)
        ) { nav.navigate(Routes.TEMA) })
        if (Session.can(PermissionKey.BACKUP_RESTORE)) {
            sysItems.add(SettingItem(
                "backup", "Backup & Restore",
                "Simpan / pulihkan data ke file",
                Icons.Default.Backup, Color(0xFF546E7A)
            ) { nav.navigate(Routes.BACKUP) })
        }
        add("TAMPILAN & SISTEM" to sysItems)

        // TENTANG
        add("TENTANG" to listOf(
            SettingItem(
                "tentang", "Tentang Aplikasi",
                "iyonzkasir v0.7.0",
                Icons.Default.Info, Color(0xFF607D8B)
            ) { nav.navigate(Routes.TENTANG) }
        ))
    }

    val filteredGroups = if (searchQuery.isBlank()) groups
    else groups.mapNotNull { (title, items) ->
        val filtered = items.filter {
            it.title.contains(searchQuery, true) ||
            it.subtitle.contains(searchQuery, true)
        }
        if (filtered.isEmpty()) null else title to filtered
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

            item {
                OutlinedTextField(
                    value = searchQuery, onValueChange = { searchQuery = it },
                    placeholder = { Text("Cari pengaturan...",
                        style = MaterialTheme.typography.bodySmall) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, null, Modifier.size(18.dp))
                    },
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .height(52.dp)
                )
            }

            if (filteredGroups.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(48.dp),
                        contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.SearchOff, null,
                                Modifier.size(48.dp),
                                MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            Text("Nggak ada pengaturan yang cocok",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                filteredGroups.forEach { (groupTitle, items) ->
                    item {
                        Text(groupTitle,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = BRAND,
                            modifier = Modifier.padding(
                                start = 20.dp, top = 16.dp, bottom = 6.dp
                            ))
                    }
                    items(items, key = { it.key }) { item ->
                        SettingRow(item)
                    }
                }
            }

            item {
                Box(Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center) {
                    Text("iyonzkasir v0.7.0 • Made with ❤️",
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
            ) {
                Icon(item.icon, null, Modifier.size(22.dp), item.color)
            }
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
                            Box(
                                Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF43A047).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
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
                                        scope.launch {
                                            app.settingRepo.setPajakDefault(p)
                                        }
                                    },
                                    label = { Text(if (p == 0) "Off" else "$p%",
                                        style = MaterialTheme.typography.bodySmall) }
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (pajakDefault == 0) "PPN tidak otomatis diterapkan"
                            else "PPN ${pajakDefault}% otomatis di setiap transaksi",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF1E88E5).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
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
                                        scope.launch {
                                            app.settingRepo.setMetodeAktif(newSet)
                                        }
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
                                        scope.launch {
                                            app.settingRepo.setMetodeAktif(newSet)
                                        }
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
                        Box(
                            Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF9C27B0).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
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
                        Switch(
                            checked = soundEnabled,
                            onCheckedChange = {
                                soundEnabled = it
                                scope.launch { app.settingRepo.setSoundEnabled(it) }
                            }
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
                            Text("Scan barcode langsung dari kamera HP",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            item {
                InfoCard(
                    icon = Icons.Default.Restaurant,
                    title = "1. Setiap menu bisa punya barcode",
                    desc = "Edit menu → isi field Barcode (scan atau ketik manual)"
                )
            }
            item {
                InfoCard(
                    icon = Icons.Default.PointOfSale,
                    title = "2. Scan di layar Kasir",
                    desc = "Tap ikon scan di kanan atas Kasir → arahkan ke barcode → otomatis masuk keranjang"
                )
            }
            item {
                InfoCard(
                    icon = Icons.Default.Image,
                    title = "3. Scan dari galeri",
                    desc = "Punya foto barcode? Bisa scan dari galeri tanpa kamera"
                )
            }
            item {
                InfoCard(
                    icon = Icons.Default.QrCode,
                    title = "Format didukung",
                    desc = "EAN-13, EAN-8, UPC, Code-128, Code-39, QR Code, Data Matrix, PDF417, Aztec"
                )
            }
            item {
                Card(colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("💡 Tips",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(6.dp))
                        Text("• Barcode paling berguna untuk: toko retail, warung, toko bangunan\n" +
                             "• Untuk F&B: bisa pakai barcode custom (print sendiri atau beli label)\n" +
                             "• Untuk laundry/jasa: biasanya tidak perlu",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCard(icon: ImageVector, title: String, desc: String) {
    Card {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                    .background(BRAND_LIGHT),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, Modifier.size(22.dp), BRAND)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium)
                Text(desc, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// TEMA
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
            Text("Pilih tampilan yang nyaman buat kamu",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))

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
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            when (mode) {
                                ThemeMode.SYSTEM -> Icons.Default.SettingsBrightness
                                ThemeMode.LIGHT -> Icons.Default.LightMode
                                ThemeMode.DARK -> Icons.Default.DarkMode
                            }, null, tint = BRAND
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(mode.label, fontWeight = FontWeight.SemiBold)
                            Text(
                                when (mode) {
                                    ThemeMode.SYSTEM -> "Ikut pengaturan HP"
                                    ThemeMode.LIGHT -> "Selalu terang"
                                    ThemeMode.DARK -> "Selalu gelap"
                                },
                                style = MaterialTheme.typography.bodySmall,
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
                placeholder = { Text("Cari fitur...",
                    style = MaterialTheme.typography.bodySmall) },
                leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(18.dp)) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .height(52.dp)
            )

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                grouped.forEach { (kategori, items) ->
                    item {
                        Text("$kategori (${items.count { it.second }}/${items.size})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = BRAND,
                            modifier = Modifier.padding(
                                start = 20.dp, top = 16.dp, bottom = 6.dp
                            ))
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
                item { Spacer(Modifier.height(24.dp)) }
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
            LazyColumn(
                Modifier.padding(pad).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
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
                                Text(bt.deskripsi, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            RadioButton(selected = selected, onClick = { businessType = bt })
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
            Text("v0.7.0", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            Text(
                "Aplikasi kasir serbaguna untuk semua jenis usaha:\n" +
                "warung, retail, cafe, restoran, laundry, toko bangunan, dan jasa.\n\n" +
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
