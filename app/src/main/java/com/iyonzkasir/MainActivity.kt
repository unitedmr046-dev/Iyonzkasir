package com.iyonzkasir

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.room.*
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

// ═══════════════════════════════════════════════════════════
// HELPERS
// ═══════════════════════════════════════════════════════════
fun Int.rupiah(): String =
    "Rp " + NumberFormat.getNumberInstance(Locale("in", "ID")).format(this)

fun Long.tanggal(): String =
    SimpleDateFormat("dd MMM yyyy HH:mm", Locale("in", "ID")).format(Date(this))

// ═══════════════════════════════════════════════════════════
// DATA LAYER
// ═══════════════════════════════════════════════════════════
@Entity(tableName = "menu_items")
data class MenuItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nama: String,
    val harga: Int,
    val kategori: String = "Umum",
    val fotoUri: String? = null,
    val tersedia: Boolean = true
)

@Entity(tableName = "orders")
data class Order(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val total: Int,
    val metodeBayar: String,
    val dibayar: Int,
    val kembalian: Int,
    val status: String = "PAID"
)

@Entity(tableName = "order_items")
data class OrderItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: Long,
    val menuId: Long,
    val namaMenu: String,
    val hargaSatuan: Int,
    val qty: Int
) { val subtotal: Int get() = hargaSatuan * qty }

@Dao
interface MenuDao {
    @Query("SELECT * FROM menu_items ORDER BY kategori, nama")
    fun observeAll(): Flow<List<MenuItem>>
    @Query("SELECT * FROM menu_items WHERE id = :id")
    suspend fun getById(id: Long): MenuItem?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: MenuItem): Long
    @Delete
    suspend fun delete(item: MenuItem)
    @Query("UPDATE menu_items SET tersedia = :tersedia WHERE id = :id")
    suspend fun setTersedia(id: Long, tersedia: Boolean)
}

@Dao
interface OrderDao {
    @Insert suspend fun insertOrder(order: Order): Long
    @Insert suspend fun insertItems(items: List<OrderItem>)
    @Transaction
    suspend fun simpanOrder(order: Order, items: List<OrderItem>): Long {
        val id = insertOrder(order)
        insertItems(items.map { it.copy(orderId = id) })
        return id
    }
    @Query("SELECT * FROM orders ORDER BY timestamp DESC")
    fun observeOrders(): Flow<List<Order>>
    @Query("SELECT * FROM order_items WHERE orderId = :orderId")
    suspend fun itemsOf(orderId: Long): List<OrderItem>
}

@Database(
    entities = [MenuItem::class, Order::class, OrderItem::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun menuDao(): MenuDao
    abstract fun orderDao(): OrderDao
    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "iyonzkasir.db"
                ).build().also { INSTANCE = it }
            }
    }
}

class PosRepository(private val menuDao: MenuDao, private val orderDao: OrderDao) {
    val menu: Flow<List<MenuItem>> = menuDao.observeAll()
    val orders: Flow<List<Order>> = orderDao.observeOrders()
    suspend fun upsertMenu(item: MenuItem) = menuDao.upsert(item)
    suspend fun deleteMenu(item: MenuItem) = menuDao.delete(item)
    suspend fun setTersedia(id: Long, v: Boolean) = menuDao.setTersedia(id, v)
    suspend fun getMenu(id: Long) = menuDao.getById(id)
    suspend fun simpanOrder(order: Order, items: List<OrderItem>) =
        orderDao.simpanOrder(order, items)
    suspend fun itemsOf(orderId: Long) = orderDao.itemsOf(orderId)
}

// ═══════════════════════════════════════════════════════════
// VIEWMODELS
// ═══════════════════════════════════════════════════════════
data class CartLine(val menu: MenuItem, val qty: Int) {
    val subtotal: Int get() = menu.harga * qty
}

class KasirViewModel(private val repo: PosRepository) : ViewModel() {
    val menu: StateFlow<List<MenuItem>> = repo.menu
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _cart = MutableStateFlow<Map<Long, CartLine>>(emptyMap())
    val cart: StateFlow<List<CartLine>> = _cart.map { it.values.toList() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val total: StateFlow<Int> = cart.map { it.sumOf { l -> l.subtotal } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun add(menu: MenuItem) = _cart.update { map ->
        val e = map[menu.id]
        if (e == null) map + (menu.id to CartLine(menu, 1))
        else map + (menu.id to e.copy(qty = e.qty + 1))
    }
    fun decrease(menuId: Long) = _cart.update { map ->
        val e = map[menuId] ?: return@update map
        if (e.qty <= 1) map - menuId else map + (menuId to e.copy(qty = e.qty - 1))
    }
    fun remove(menuId: Long) { _cart.update { it - menuId } }
    fun clearCart() { _cart.value = emptyMap() }

    fun checkout(metode: String, dibayar: Int, onDone: (Long) -> Unit) {
        val lines = cart.value
        if (lines.isEmpty()) return
        viewModelScope.launch {
            val totalInt = lines.sumOf { it.subtotal }
            val order = Order(
                timestamp = System.currentTimeMillis(),
                total = totalInt,
                metodeBayar = metode,
                dibayar = dibayar,
                kembalian = (dibayar - totalInt).coerceAtLeast(0)
            )
            val items = lines.map {
                OrderItem(
                    orderId = 0, menuId = it.menu.id, namaMenu = it.menu.nama,
                    hargaSatuan = it.menu.harga, qty = it.qty
                )
            }
            val id = repo.simpanOrder(order, items)
            clearCart()
            onDone(id)
        }
    }
}

class MenuViewModel(private val repo: PosRepository) : ViewModel() {
    val menu: StateFlow<List<MenuItem>> = repo.menu
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun save(item: MenuItem, onDone: () -> Unit = {}) = viewModelScope.launch {
        repo.upsertMenu(item); onDone()
    }
    fun delete(item: MenuItem) = viewModelScope.launch { repo.deleteMenu(item) }
    fun toggle(item: MenuItem) = viewModelScope.launch {
        repo.setTersedia(item.id, !item.tersedia)
    }
    suspend fun get(id: Long) = repo.getMenu(id)
}

class RiwayatViewModel(repo: PosRepository) : ViewModel() {
    val orders: StateFlow<List<Order>> = repo.orders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

class VMFactory(private val repo: PosRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(KasirViewModel::class.java) -> KasirViewModel(repo) as T
        modelClass.isAssignableFrom(MenuViewModel::class.java) -> MenuViewModel(repo) as T
        modelClass.isAssignableFrom(RiwayatViewModel::class.java) -> RiwayatViewModel(repo) as T
        else -> error("VM tidak dikenal: ${modelClass.name}")
    }
}

// ═══════════════════════════════════════════════════════════
// SCREENS
// ═══════════════════════════════════════════════════════════

// ---------- KASIR ----------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KasirScreen(vm: KasirViewModel, onBukaKeranjang: () -> Unit) {
    val menu by vm.menu.collectAsState()
    val cart by vm.cart.collectAsState()
    val total by vm.total.collectAsState()
    var kategori by remember { mutableStateOf<String?>(null) }

    val kategoriList = remember(menu) { listOf(null) + menu.map { it.kategori }.distinct() }
    val filtered = if (kategori == null) menu else menu.filter { it.kategori == kategori }

    Scaffold(
        topBar = { TopAppBar(title = { Text("iyonzkasir") }) },
        bottomBar = {
            if (cart.isNotEmpty()) {
                Surface(shadowElevation = 8.dp) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("${cart.sumOf { it.qty }} item",
                                style = MaterialTheme.typography.bodySmall)
                            Text(total.rupiah(),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge)
                        }
                        Button(onClick = onBukaKeranjang) { Text("Keranjang") }
                    }
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(kategoriList.size) { i ->
                    val k = kategoriList[i]
                    FilterChip(
                        selected = k == kategori,
                        onClick = { kategori = k },
                        label = { Text(k ?: "Semua") }
                    )
                }
            }
            if (menu.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Belum ada menu. Tambah di tab Menu ya bro 🍽️")
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filtered, key = { it.id }) { m ->
                        MenuCard(m, enabled = m.tersedia) { vm.add(m) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuCard(m: MenuItem, enabled: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().height(200.dp)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(120.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant)) {
                m.fotoUri?.let {
                    AsyncImage(
                        model = File(it), contentDescription = m.nama,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                    )
                }
                if (!enabled) {
                    Box(Modifier.fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) { Text("Habis", color = MaterialTheme.colorScheme.onPrimary) }
                }
            }
            Column(Modifier.padding(8.dp)) {
                Text(m.nama, maxLines = 1, fontWeight = FontWeight.SemiBold)
                Text(m.harga.rupiah(), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

// ---------- KERANJANG ----------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeranjangScreen(vm: KasirViewModel, onBack: () -> Unit, onBayar: () -> Unit) {
    val cart by vm.cart.collectAsState()
    val total by vm.total.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Keranjang") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                }
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Column(Modifier.padding(16.dp)) {
                    Row {
                        Text("Total", Modifier.weight(1f))
                        Text(total.rupiah(), fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onBayar,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = cart.isNotEmpty()
                    ) { Text("Bayar") }
                }
            }
        }
    ) { pad ->
        if (cart.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Keranjang kosong")
            }
        } else {
            LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(12.dp)) {
                items(cart, key = { it.menu.id }) { line ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(line.menu.nama, fontWeight = FontWeight.SemiBold)
                                Text(line.menu.harga.rupiah())
                            }
                            IconButton(onClick = { vm.decrease(line.menu.id) }) {
                                Icon(Icons.Default.Remove, null)
                            }
                            Text(line.qty.toString(), fontWeight = FontWeight.Bold)
                            IconButton(onClick = { vm.add(line.menu) }) {
                                Icon(Icons.Default.Add, null)
                            }
                            IconButton(onClick = { vm.remove(line.menu.id) }) {
                                Icon(Icons.Default.Delete, null)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------- BAYAR ----------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BayarScreen(vm: KasirViewModel, onBack: () -> Unit, onSelesai: () -> Unit) {
    val total by vm.total.collectAsState()
    var metode by remember { mutableStateOf("CASH") }
    var dibayarText by remember { mutableStateOf("") }
    val dibayar = dibayarText.toIntOrNull() ?: 0
    val kembalian = (dibayar - total).coerceAtLeast(0)
    val cukup = if (metode == "CASH") dibayar >= total else true

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pembayaran") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("Total tagihan", style = MaterialTheme.typography.bodyMedium)
                    Text(total.rupiah(), style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold)
                }
            }
            Text("Metode bayar")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("CASH", "QRIS", "DEBIT").forEach { m ->
                    FilterChip(selected = metode == m, onClick = { metode = m },
                        label = { Text(m) })
                }
            }
            if (metode == "CASH") {
                OutlinedTextField(
                    value = dibayarText,
                    onValueChange = { dibayarText = it.filter { c -> c.isDigit() } },
                    label = { Text("Uang diterima") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                QuickCash(total) { dibayarText = it.toString() }
                Row {
                    Text("Kembalian", Modifier.weight(1f))
                    Text(kembalian.rupiah(), fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = {
                    vm.checkout(metode, if (metode == "CASH") dibayar else total) { onSelesai() }
                },
                enabled = cukup,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Konfirmasi Bayar") }
        }
    }
}

@Composable
private fun QuickCash(total: Int, onPick: (Int) -> Unit) {
    val suggestions = remember(total) {
        val rounded = ((total + 4999) / 5000) * 5000
        listOf(total, rounded, 50000, 100000).distinct().filter { it >= total }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        suggestions.take(4).forEach { s ->
            AssistChip(onClick = { onPick(s) }, label = { Text(s.rupiah()) })
        }
    }
}

// ---------- MENU ----------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuScreen(vm: MenuViewModel, onEdit: (Long?) -> Unit) {
    val menu by vm.menu.collectAsState()
    Scaffold(
        topBar = { TopAppBar(title = { Text("Kelola Menu") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onEdit(null) },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Menu baru") }
            )
        }
    ) { pad ->
        if (menu.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Belum ada menu")
            }
        } else {
            LazyColumn(
                Modifier.padding(pad).fillMaxSize(),
                contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 100.dp)
            ) {
                items(menu, key = { it.id }) { m ->
                    MenuRow(
                        m = m,
                        onToggle = { vm.toggle(m) },
                        onEdit = { onEdit(m.id) },
                        onDelete = { vm.delete(m) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MenuRow(m: MenuItem, onToggle: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (m.fotoUri != null) {
                AsyncImage(
                    model = File(m.fotoUri), contentDescription = null,
                    contentScale = ContentScale.Crop, modifier = Modifier.size(64.dp)
                )
            } else {
                Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) { Text("🍽️") }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(m.nama, fontWeight = FontWeight.SemiBold)
                Text(m.harga.rupiah())
                Text(m.kategori, style = MaterialTheme.typography.labelSmall)
            }
            Switch(checked = m.tersedia, onCheckedChange = { onToggle() })
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, null) }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null) }
        }
    }
}

// ---------- EDIT MENU ----------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditMenuScreen(vm: MenuViewModel, menuId: Long?, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var nama by remember { mutableStateOf("") }
    var hargaText by remember { mutableStateOf("") }
    var kategori by remember { mutableStateOf("Umum") }
    var fotoUri by remember { mutableStateOf<String?>(null) }
    var tersedia by remember { mutableStateOf(true) }
    var loaded by remember { mutableStateOf(menuId == null) }

    LaunchedEffect(menuId) {
        if (menuId != null) {
            vm.get(menuId)?.let {
                nama = it.nama; hargaText = it.harga.toString()
                kategori = it.kategori; fotoUri = it.fotoUri; tersedia = it.tersedia
            }
            loaded = true
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            ctx.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            fotoUri = uri.toString()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (menuId == null) "Menu Baru" else "Edit Menu") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                }
            )
        }
    ) { pad ->
        if (!loaded) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                Modifier.padding(pad).padding(16.dp).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                fotoUri?.let {
                    AsyncImage(
                        model = File(it), contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(180.dp)
                    )
                }
                OutlinedButton(onClick = { picker.launch(arrayOf("image/*")) }) {
                    Text(if (fotoUri == null) "Pilih Foto dari Galeri" else "Ganti Foto")
                }
                OutlinedTextField(value = nama, onValueChange = { nama = it },
                    label = { Text("Nama menu") }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true)
                OutlinedTextField(value = hargaText,
                    onValueChange = { hargaText = it.filter { c -> c.isDigit() } },
                    label = { Text("Harga") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = kategori, onValueChange = { kategori = it },
                    label = { Text("Kategori") }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Tersedia", Modifier.weight(1f))
                    Switch(checked = tersedia, onCheckedChange = { tersedia = it })
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        val harga = hargaText.toIntOrNull() ?: 0
                        if (nama.isBlank() || harga <= 0) return@Button
                        vm.save(
                            MenuItem(
                                id = menuId ?: 0,
                                nama = nama.trim(), harga = harga,
                                kategori = kategori.trim().ifBlank { "Umum" },
                                fotoUri = fotoUri, tersedia = tersedia
                            )
                        ) { onBack() }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Simpan") }
            }
        }
    }
}

// ---------- RIWAYAT ----------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RiwayatScreen(vm: RiwayatViewModel) {
    val orders by vm.orders.collectAsState()
    Scaffold(topBar = { TopAppBar(title = { Text("Riwayat Transaksi") }) }) { pad ->
        if (orders.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Belum ada transaksi")
            }
        } else {
            LazyColumn(Modifier.padding(pad).fillMaxSize(),
                contentPadding = PaddingValues(12.dp)) {
                items(orders, key = { it.id }) { o ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Row(Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("#${o.id} • ${o.metodeBayar}",
                                    fontWeight = FontWeight.SemiBold)
                                Text(o.timestamp.tanggal(),
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            Text(o.total.rupiah(), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// APPLICATION + ACTIVITY + NAVIGATION
// ═══════════════════════════════════════════════════════════
class KasirApp : Application() {
    val repository: PosRepository by lazy {
        val db = AppDatabase.get(this)
        PosRepository(db.menuDao(), db.orderDao())
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = (application as KasirApp).repository
        setContent {
            MaterialTheme { AppRoot(repo) }
        }
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

@Composable
fun AppRoot(repo: PosRepository) {
    val nav = rememberNavController()
    val factory = remember(repo) { VMFactory(repo) }

    val tabs = listOf(
        Tab("kasir", "Kasir", Icons.Default.Home),
        Tab("menu", "Menu", Icons.Default.Restaurant),
        Tab("riwayat", "Riwayat", Icons.Default.List),
    )
    val current by nav.currentBackStackEntryAsState()
    val currentRoute = current?.destination?.route
    val showBar = tabs.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = currentRoute == t.route,
                            onClick = {
                                nav.navigate(t.route) {
                                    popUpTo(nav.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(t.icon, null) },
                            label = { Text(t.label) }
                        )
                    }
                }
            }
        }
    ) { pad ->
        NavHost(navController = nav, startDestination = "kasir",
            modifier = Modifier.padding(pad)) {
            composable("kasir") {
                val vm: KasirViewModel = viewModel(factory = factory)
                KasirScreen(vm) { nav.navigate("keranjang") }
            }
            composable("keranjang") {
                val vm: KasirViewModel = viewModel(factory = factory)
                KeranjangScreen(vm, onBack = { nav.popBackStack() },
                    onBayar = { nav.navigate("bayar") })
            }
            composable("bayar") {
                val vm: KasirViewModel = viewModel(factory = factory)
                BayarScreen(vm, onBack = { nav.popBackStack() }) {
                    nav.popBackStack("kasir", inclusive = false)
                }
            }
            composable("menu") {
                val vm: MenuViewModel = viewModel(factory = factory)
                MenuScreen(vm) { id ->
                    nav.navigate(if (id == null) "menu/edit" else "menu/edit/$id")
                }
            }
            composable("menu/edit") {
                val vm: MenuViewModel = viewModel(factory = factory)
                EditMenuScreen(vm, menuId = null, onBack = { nav.popBackStack() })
            }
            composable("menu/edit/{id}") { entry ->
                val vm: MenuViewModel = viewModel(factory = factory)
                val id = entry.arguments?.getString("id")?.toLongOrNull()
                EditMenuScreen(vm, menuId = id, onBack = { nav.popBackStack() })
            }
            composable("riwayat") {
                val vm: RiwayatViewModel = viewModel(factory = factory)
                RiwayatScreen(vm)
            }
        }
    }
}
