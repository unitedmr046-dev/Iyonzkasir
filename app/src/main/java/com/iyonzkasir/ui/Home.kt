package com.iyonzkasir.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.iyonzkasir.*
import com.iyonzkasir.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// ═══════════════════════════════════════════════════════════
// HOME VIEWMODEL
// ═══════════════════════════════════════════════════════════
class HomeViewModel(
    private val posRepo: PosRepository,
    private val stockRepo: StockRepository,
    private val settingRepo: SettingRepository,
    private val expenseRepo: ExpenseRepository
) : ViewModel() {

    private val startOfDay: Long = run {
        val c = java.util.Calendar.getInstance()
        c.set(java.util.Calendar.HOUR_OF_DAY, 0)
        c.set(java.util.Calendar.MINUTE, 0)
        c.set(java.util.Calendar.SECOND, 0)
        c.set(java.util.Calendar.MILLISECOND, 0)
        c.timeInMillis
    }

    val transaksiHariIni: StateFlow<Int> = posRepo.countPaidSince(startOfDay)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val omzetHariIni: StateFlow<Int> = posRepo.sumPaidSince(startOfDay)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val pengeluaranHariIni: StateFlow<Int> = expenseRepo.observeSumInRange(
        startOfDay, startOfDay + 24L * 60 * 60 * 1000 - 1
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _labaHariIni = MutableStateFlow(0)
    val labaHariIni: StateFlow<Int> = _labaHariIni.asStateFlow()

    private val _hppHariIni = MutableStateFlow(0)
    val hppHariIni: StateFlow<Int> = _hppHariIni.asStateFlow()

    private val _namaToko = MutableStateFlow("")
    val namaToko: StateFlow<String> = _namaToko.asStateFlow()

    private val _alamatToko = MutableStateFlow("")
    val alamatToko: StateFlow<String> = _alamatToko.asStateFlow()

    val lowStock: StateFlow<List<MenuItem>> = stockRepo.lowStock
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _topMenu = MutableStateFlow<List<MenuTerlaris>>(emptyList())
    val topMenu: StateFlow<List<MenuTerlaris>> = _topMenu.asStateFlow()

    init { loadExtras() }

    fun loadExtras() {
        viewModelScope.launch {
            _namaToko.value = settingRepo.getNamaToko()
            _alamatToko.value = settingRepo.getAlamatToko()

            try {
                val laba = posRepo.labaPerProduk(startOfDay, System.currentTimeMillis())
                val totalHpp = laba.sumOf { it.totalHpp }
                val totalOmzet = laba.sumOf { it.totalOmzet }
                _hppHariIni.value = totalHpp
                _labaHariIni.value = totalOmzet - totalHpp
            } catch (_: Exception) {
                _hppHariIni.value = 0
                _labaHariIni.value = 0
            }

            try {
                val top = posRepo.menuTerlaris(
                    startOfDay, System.currentTimeMillis(), 3
                )
                _topMenu.value = top
            } catch (_: Exception) {}
        }
    }

    fun refresh() { loadExtras() }
}

class HomeVMFactory(
    private val posRepo: PosRepository,
    private val stockRepo: StockRepository,
    private val settingRepo: SettingRepository,
    private val expenseRepo: ExpenseRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        HomeViewModel(posRepo, stockRepo, settingRepo, expenseRepo) as T
}

// ═══════════════════════════════════════════════════════════
// HOME ROUTE
// ═══════════════════════════════════════════════════════════
@Composable
fun HomeRoute(app: IyonzApp, nav: NavHostController, innerNav: NavHostController) {
    val factory = remember {
        HomeVMFactory(app.posRepo, app.stockRepo, app.settingRepo, app.expenseRepo)
    }
    val vm: HomeViewModel = viewModel(factory = factory)
    HomeScreen(vm, innerNav, nav)
}

// ═══════════════════════════════════════════════════════════
// SHORTCUT DATA
// ═══════════════════════════════════════════════════════════
data class HomeShortcut(
    val label: String,
    val icon: ImageVector,
    val color: Color,
    val route: String,
    val feature: FeatureKey? = null,
    val permission: PermissionKey? = null
)

// ═══════════════════════════════════════════════════════════
// HOME SCREEN
// ═══════════════════════════════════════════════════════════
@Composable
fun HomeScreen(
    vm: HomeViewModel,
    innerNav: NavHostController,
    nav: NavHostController
) {
    val user by Session.currentState
    val namaToko by vm.namaToko.collectAsState()
    val alamatToko by vm.alamatToko.collectAsState()
    val omzet by vm.omzetHariIni.collectAsState()
    val trx by vm.transaksiHariIni.collectAsState()
    val laba by vm.labaHariIni.collectAsState()
    val pengeluaran by vm.pengeluaranHariIni.collectAsState()
    val lowStock by vm.lowStock.collectAsState()
    val topMenu by vm.topMenu.collectAsState()
    val enabled by FeatureManager.enabled.collectAsState()

    val greeting = remember {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        when (hour) {
            in 0..10 -> "Selamat Pagi"
            in 11..14 -> "Selamat Siang"
            in 15..17 -> "Selamat Sore"
            else -> "Selamat Malam"
        }
    }

    val shortcuts = remember(enabled) {
        listOf(
            HomeShortcut("Menu", Icons.Default.Restaurant, Color(0xFF8E24AA),
                Routes.TAB_MENU, permission = PermissionKey.KELOLA_MENU),
            HomeShortcut("Open Bill", Icons.Default.ReceiptLong, Color(0xFFFB8C00),
                Routes.TAB_OPEN_BILL,
                feature = FeatureKey.OPEN_BILL, permission = PermissionKey.OPEN_BILL),
            HomeShortcut("Riwayat", Icons.Default.History, Color(0xFF1E88E5),
                Routes.TAB_RIWAYAT, permission = PermissionKey.LIHAT_RIWAYAT),
            HomeShortcut("Pengeluaran", Icons.Default.Payments, Color(0xFF43A047),
                Routes.PENGELUARAN, feature = FeatureKey.PENGELUARAN,
                permission = PermissionKey.KELOLA_PENGELUARAN),
            HomeShortcut("Kategori", Icons.Default.Category, Color(0xFFE53935),
                Routes.KATEGORI, feature = FeatureKey.KATEGORI_MGMT,
                permission = PermissionKey.KELOLA_KATEGORI),
            HomeShortcut("CRM", Icons.Default.People, Color(0xFF00897B),
                Routes.CRM, feature = FeatureKey.MEMBER,
                permission = PermissionKey.KELOLA_MEMBER),
            HomeShortcut("Laporan", Icons.Default.Analytics, Color(0xFF3949AB),
                Routes.LAPORAN, feature = FeatureKey.LABA_PER_PRODUK,
                permission = PermissionKey.LIHAT_LAPORAN),
            HomeShortcut("Shift", Icons.Default.Schedule, Color(0xFF6D4C41),
                Routes.TAB_SHIFT, feature = FeatureKey.SHIFT_KASIR,
                permission = PermissionKey.JUAL),
            HomeShortcut("Antrian", Icons.Default.ConfirmationNumber, Color(0xFFD81B60),
                Routes.ANTRIAN, feature = FeatureKey.NOMOR_ANTRIAN),
            HomeShortcut("Printer", Icons.Default.Print, Color(0xFF546E7A),
                Routes.PRINTER, feature = FeatureKey.PRINTER_BT),
            HomeShortcut("Template Struk", Icons.Default.Receipt, Color(0xFF9C27B0),
                Routes.TEMPLATE_STRUK, feature = FeatureKey.PRINTER_BT),
            HomeShortcut("Tema Warna", Icons.Default.Palette, Color(0xFFF57C00),
                Routes.TEMA_WARNA),
            HomeShortcut("Backup", Icons.Default.Backup, Color(0xFF607D8B),
                Routes.BACKUP, permission = PermissionKey.BACKUP_RESTORE),
        ).filter { sc ->
            val featOk = sc.feature == null || sc.feature in enabled
            val permOk = sc.permission == null || Session.can(sc.permission)
            featOk && permOk
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val isTablet = maxWidth >= 720.dp

        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                // ═══ HEADER GRADIENT ═══
                item {
                    Box(
                        Modifier.fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(BRAND, BRAND_DARK)
                                )
                            )
                    ) {
                        Column(
                            Modifier.fillMaxWidth()
                                .widthIn(max = 900.dp)
                                .align(Alignment.Center)
                                .padding(horizontal = 20.dp, vertical = 20.dp)
                        ) {
                            // Greeting + user
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("$greeting,",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White.copy(alpha = 0.9f))
                                    Text(user?.nama ?: "Pengguna",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White)
                                }
                                Box(
                                    Modifier.size(48.dp).clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (user?.fotoUri != null) {
                                        AsyncImage(
                                            model = user!!.fotoUri,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Text(user?.nama?.take(1)?.uppercase() ?: "?",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White)
                                    }
                                }
                            }

                            Spacer(Modifier.height(8.dp))
                            Text(namaToko.ifBlank { "Toko Saya" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White)
                            if (alamatToko.isNotBlank()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.LocationOn, null,
                                        tint = Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(alamatToko,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.85f),
                                        maxLines = 1)
                                }
                            }

                            Spacer(Modifier.height(16.dp))

                            // Card Laporan Hari Ini
                            Card(
                                Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color.White.copy(alpha = 0.15f)
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Assessment, null,
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Laporan Hari Ini",
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            style = MaterialTheme.typography.titleSmall)
                                        Spacer(Modifier.weight(1f))
                                        TextButton(
                                            onClick = {
                                                innerNav.navigate(Routes.TAB_DASHBOARD) {
                                                    popUpTo(innerNav.graph.startDestinationId) {
                                                        saveState = true
                                                    }
                                                    launchSingleTop = true
                                                }
                                            },
                                            contentPadding = PaddingValues(
                                                horizontal = 8.dp, vertical = 0.dp
                                            )
                                        ) {
                                            Text("Lihat",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))

                                    // Grid 2x2 atau 4x1
                                    if (isTablet) {
                                        Row {
                                            HomeStat("Omzet", omzet.rupiah(),
                                                Modifier.weight(1f))
                                            HomeStat("Transaksi", "${trx}x",
                                                Modifier.weight(1f))
                                            HomeStat("Laba Kotor", laba.rupiah(),
                                                Modifier.weight(1f))
                                            HomeStat("Pengeluaran",
                                                if (pengeluaran > 0) "- ${pengeluaran.rupiah()}"
                                                else "Rp 0",
                                                Modifier.weight(1f))
                                        }
                                    } else {
                                        Row {
                                            HomeStat("Omzet", omzet.rupiah(),
                                                Modifier.weight(1f))
                                            HomeStat("Transaksi", "${trx}x",
                                                Modifier.weight(1f))
                                        }
                                        Spacer(Modifier.height(6.dp))
                                        Row {
                                            HomeStat("Laba Kotor", laba.rupiah(),
                                                Modifier.weight(1f))
                                            HomeStat("Pengeluaran",
                                                if (pengeluaran > 0) "- ${pengeluaran.rupiah()}"
                                                else "Rp 0",
                                                Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ═══ ALERT STOK MENIPIS ═══
                if (lowStock.isNotEmpty()) {
                    item {
                        Column(
                            Modifier.fillMaxWidth()
                                .widthIn(max = 900.dp)
                        ) {
                            Card(
                                Modifier.fillMaxWidth().padding(16.dp, 16.dp, 16.dp, 0.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = DANGER.copy(alpha = 0.1f)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Warning, null,
                                            tint = DANGER,
                                            modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Stok Menipis (${lowStock.size})",
                                            fontWeight = FontWeight.Bold,
                                            color = DANGER,
                                            style = MaterialTheme.typography.titleSmall)
                                        Spacer(Modifier.weight(1f))
                                        TextButton(onClick = {
                                            innerNav.navigate(Routes.TAB_INVENTARIS) {
                                                popUpTo(innerNav.graph.startDestinationId) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                            }
                                        }) {
                                            Text("Lihat", color = DANGER,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                    lowStock.take(3).forEach { m ->
                                        Row(
                                            Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                        ) {
                                            Text("• ${m.nama}",
                                                Modifier.weight(1f),
                                                style = MaterialTheme.typography.bodySmall)
                                            Text("sisa ${m.stok}",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = DANGER)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ═══ GRID SHORTCUT ═══
                item {
                    Column(
                        Modifier.fillMaxWidth()
                            .widthIn(max = 900.dp)
                            .padding(16.dp)
                    ) {
                        Text("Menu Cepat",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))

                        val cols = if (isTablet) 6 else 4
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(cols),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 600.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            userScrollEnabled = false
                        ) {
                            items(shortcuts, key = { it.route }) { sc ->
                                ShortcutItem(sc) {
                                    if (sc.route.startsWith("tab_")) {
                                        innerNav.navigate(sc.route) {
                                            popUpTo(innerNav.graph.startDestinationId) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    } else {
                                        nav.navigate(sc.route)
                                    }
                                }
                            }
                        }
                    }
                }

                // ═══ TOP MENU ═══
                if (topMenu.isNotEmpty()) {
                    item {
                        Column(
                            Modifier.fillMaxWidth()
                                .widthIn(max = 900.dp)
                        ) {
                            Card(
                                Modifier.fillMaxWidth().padding(16.dp, 0.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("🏆", style = MaterialTheme.typography.titleMedium)
                                        Spacer(Modifier.width(6.dp))
                                        Text("Top Menu Hari Ini",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    topMenu.forEachIndexed { idx, m ->
                                        Row(
                                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                color = when (idx) {
                                                    0 -> Color(0xFFFFD54F)
                                                    1 -> Color(0xFFCFD8DC)
                                                    else -> Color(0xFFFFAB91)
                                                },
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Box(Modifier.size(26.dp),
                                                    contentAlignment = Alignment.Center) {
                                                    Text("${idx + 1}",
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF424242),
                                                        style = MaterialTheme.typography.labelMedium)
                                                }
                                            }
                                            Spacer(Modifier.width(10.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(m.namaMenu,
                                                    fontWeight = FontWeight.SemiBold,
                                                    style = MaterialTheme.typography.bodyMedium)
                                                Text("${m.totalQty}x terjual",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Text(m.totalOmzet.rupiah(),
                                                fontWeight = FontWeight.Bold, color = BRAND,
                                                style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(20.dp)) }
            }

            // ═══ TOMBOL STICKY ═══
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                shadowElevation = 12.dp,
                color = Color.Transparent
            ) {
                Box(
                    Modifier.fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.surface
                                )
                            )
                        )
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = {
                            innerNav.navigate(Routes.TAB_POS) {
                                popUpTo(innerNav.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 900.dp)
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BRAND),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.ShoppingCart, null, Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("MULAI TRANSAKSI",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.85f))
        Text(value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1)
    }
}

@Composable
private fun ShortcutItem(sc: HomeShortcut, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 2.dp)
    ) {
        Box(
            Modifier.size(52.dp).clip(RoundedCornerShape(14.dp))
                .background(sc.color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(sc.icon, null, tint = sc.color, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(sc.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            textAlign = TextAlign.Center)
    }
}
