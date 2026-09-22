package com.iyonzkasir.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

// ═══════════════════════════════════════════════════════════
// HOME VIEWMODEL
// ═══════════════════════════════════════════════════════════
class HomeViewModel(
    private val posRepo: PosRepository,
    private val stockRepo: StockRepository,
    private val settingRepo: SettingRepository
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

    private val _labaHariIni = MutableStateFlow(0)
    val labaHariIni: StateFlow<Int> = _labaHariIni.asStateFlow()

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

            // Laba = omzet - HPP dari laba per produk hari ini
            try {
                val laba = posRepo.labaPerProduk(startOfDay, System.currentTimeMillis())
                _labaHariIni.value = laba.sumOf { it.laba }
            } catch (_: Exception) {
                _labaHariIni.value = 0
            }

            // Top 3 menu hari ini
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
    private val settingRepo: SettingRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        HomeViewModel(posRepo, stockRepo, settingRepo) as T
}

// ═══════════════════════════════════════════════════════════
// HOME ROUTE
// ═══════════════════════════════════════════════════════════
@Composable
fun HomeRoute(app: IyonzApp, nav: NavHostController, innerNav: NavHostController) {
    val factory = remember {
        HomeVMFactory(app.posRepo, app.stockRepo, app.settingRepo)
    }
    val vm: HomeViewModel = viewModel(factory = factory)
    HomeScreen(vm, innerNav, nav)
}

// ═══════════════════════════════════════════════════════════
// HOME SCREEN
// ═══════════════════════════════════════════════════════════
data class HomeShortcut(
    val label: String,
    val icon: ImageVector,
    val color: Color,
    val route: String,
    val feature: FeatureKey? = null,
    val permission: PermissionKey? = null
)

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

    // Shortcut grid
    val shortcuts = remember(enabled) {
        listOf(
            HomeShortcut("Menu", Icons.Default.Restaurant, Color(0xFF8E24AA),
                Routes.TAB_MENU, permission = PermissionKey.KELOLA_MENU),
            HomeShortcut("Stok", Icons.Default.Inventory, Color(0xFF00897B),
                Routes.TAB_INVENTARIS,
                feature = FeatureKey.LOW_STOCK_ALERT, permission = PermissionKey.LIHAT_STOK),
            HomeShortcut("Open Bill", Icons.Default.ReceiptLong, Color(0xFFFB8C00),
                Routes.TAB_OPEN_BILL,
                feature = FeatureKey.OPEN_BILL, permission = PermissionKey.OPEN_BILL),
            HomeShortcut("Riwayat", Icons.Default.History, Color(0xFF1E88E5),
                Routes.TAB_RIWAYAT, permission = PermissionKey.LIHAT_RIWAYAT),
            HomeShortcut("Kategori", Icons.Default.Category, Color(0xFFE53935),
                Routes.KATEGORI, feature = FeatureKey.KATEGORI_MGMT,
                permission = PermissionKey.KELOLA_KATEGORI),
            HomeShortcut("CRM", Icons.Default.People, Color(0xFF43A047),
                Routes.CRM, feature = FeatureKey.MEMBER,
                permission = PermissionKey.KELOLA_MEMBER),
            HomeShortcut("Laporan", Icons.Default.Analytics, Color(0xFF3949AB),
                Routes.LAPORAN, feature = FeatureKey.LABA_PER_PRODUK,
                permission = PermissionKey.LIHAT_LAPORAN),
            HomeShortcut("Shift", Icons.Default.Schedule, Color(0xFF6D4C41),
                Routes.TAB_SHIFT, feature = FeatureKey.SHIFT_KASIR,
                permission = PermissionKey.JUAL),
            HomeShortcut("Printer", Icons.Default.Print, Color(0xFFD81B60),
                Routes.PRINTER, feature = FeatureKey.PRINTER_BT),
            HomeShortcut("Backup", Icons.Default.Backup, Color(0xFF546E7A),
                Routes.BACKUP, permission = PermissionKey.BACKUP_RESTORE),
            HomeShortcut("Setelan", Icons.Default.Settings, Color(0xFF757575),
                Routes.TAB_SETTINGS),
        ).filter { sc ->
            val featOk = sc.feature == null || sc.feature in enabled
            val permOk = sc.permission == null || Session.can(sc.permission)
            featOk && permOk
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            // ══════ HEADER GRADIENT ══════
            item {
                Box(
                    Modifier.fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(BRAND, BRAND_DARK)
                            )
                        )
                ) {
                    Column(Modifier.fillMaxWidth().padding(20.dp)) {
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
                            // Avatar
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
                                    color = Color.White.copy(alpha = 0.85f))
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // ══════ CARD LAPORAN HARI INI ══════
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
                                        tint = Color.White, modifier = Modifier.size(20.dp))
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
                                Row {
                                    Column(Modifier.weight(1f)) {
                                        Text("Omzet",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.85f))
                                        Text(omzet.rupiah(),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White)
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text("Transaksi",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.85f))
                                        Text("${trx}x",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White)
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text("Laba",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.85f))
                                        Text(laba.rupiah(),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ══════ ALERT STOK MENIPIS ══════
            if (lowStock.isNotEmpty()) {
                item {
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
                                    tint = DANGER, modifier = Modifier.size(20.dp))
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
                                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
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

            // ══════ SHORTCUT GRID ══════
            item {
                Column(Modifier.padding(16.dp)) {
                    Text("Menu Cepat",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))

                    // Grid manual dengan Column + Row biar fleksibel
                    val cols = 4
                    val rows = (shortcuts.size + cols - 1) / cols
                    for (r in 0 until rows) {
                        Row(Modifier.fillMaxWidth()) {
                            for (c in 0 until cols) {
                                val idx = r * cols + c
                                if (idx < shortcuts.size) {
                                    val sc = shortcuts[idx]
                                    Box(Modifier.weight(1f)) {
                                        ShortcutItem(sc) {
                                            if (sc.route.startsWith("tab_")) {
                                                // Tab di bottom nav
                                                innerNav.navigate(sc.route) {
                                                    popUpTo(innerNav.graph.startDestinationId) {
                                                        saveState = true
                                                    }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            } else {
                                                // Fullscreen route
                                                nav.navigate(sc.route)
                                            }
                                        }
                                    }
                                } else {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }

            // ══════ TOP MENU ══════
            if (topMenu.isNotEmpty()) {
                item {
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

            // Spacer
            item { Spacer(Modifier.height(20.dp)) }
        }

        // ══════ TOMBOL STICKY "MULAI TRANSAKSI" ══════
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
                    .padding(16.dp)
            ) {
                Button(
                    onClick = {
                        innerNav.navigate(Routes.TAB_POS) {
                            popUpTo(innerNav.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
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

@Composable
private fun ShortcutItem(sc: HomeShortcut, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
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
            maxLines = 1)
    }
}
