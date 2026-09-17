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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
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
    SimpleDateFormat("dd MMM HH:mm", Locale("in", "ID")).format(Date(this))

val BRAND = Color(0xFFFF6B35)
val BRAND_LIGHT = Color(0xFFFFE4D6)

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
    val timestamp: Long = 0,
    val nomorMeja: String = "",
    val tipeOrder: String = "DINE_IN",
    val namaPelanggan: String = "",
    val total: Int = 0,
    val metodeBayar: String = "",
    val dibayar: Int = 0,
    val kembalian: Int = 0,
    val status: String = "OPEN" // OPEN | PAID
)

@Entity(tableName = "order_items")
data class OrderItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: Long = 0,
    val menuId: Long = 0,
    val namaMenu: String = "",
    val hargaSatuan: Int = 0,
    val qty: Int = 1,
    val catatan: String = ""
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
    @Update suspend fun updateOrder(order: Order)
    @Query("DELETE FROM order_items WHERE orderId = :orderId")
    suspend fun deleteItems(orderId: Long)

    @Transaction
    suspend fun simpanOrder(order: Order, items: List<OrderItem>): Long {
        val id = insertOrder(order)
        insertItems(items.map { it.copy(orderId = id) })
        return id
    }

    @Transaction
    suspend fun updateOrderWithItems(order: Order, items: List<OrderItem>) {
        updateOrder(order)
        deleteItems(order.id)
        insertItems(items.map { it.copy(orderId = order.id) })
    }

    @Query("SELECT * FROM orders WHERE status = 'OPEN' ORDER BY timestamp DESC")
    fun observeOpenBills(): Flow<List<Order>>

    @Query("SELECT * FROM orders WHERE status = 'PAID' ORDER BY timestamp DESC")
    fun observePaid(): Flow<List<Order>>

    @Query("SELECT * FROM orders WHERE id = :id")
    suspend fun getOrder(id: Long): Order?

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
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}

class PosRepository(private val menuDao: MenuDao, private val orderDao: OrderDao) {
    val menu: Flow<List<MenuItem>> = menuDao.observeAll()
    val openBills: Flow<List<Order>> = orderDao.observeOpenBills()
    val paidOrders: Flow<List<Order>> = orderDao.observePaid()

    suspend fun upsertMenu(item: MenuItem) = menuDao.upsert(item)
    suspend fun deleteMenu(item: MenuItem) = menuDao.delete(item)
    suspend fun setTersedia(id: Long, v: Boolean) = menuDao.setTersedia(id, v)
    suspend fun getMenu(id: Long) = menuDao.getById(id)

    suspend fun simpanOrder(order: Order, items: List<OrderItem>) =
        orderDao.simpanOrder(order, items)
    suspend fun updateOrderWithItems(order: Order, items: List<OrderItem>) =
        orderDao.updateOrderWithItems(order, items)
    suspend fun getOrder(id: Long) = orderDao.getOrder(id)
    suspend fun itemsOf(orderId: Long) = orderDao.itemsOf(orderId)
}

// ═══════════════════════════════════════════════════════════
// VIEWMODELS
// ═══════════════════════════════════════════════════════════
data class CartLine(
    val key: String, // menuId + "|" + catatan
    val menu: MenuItem,
    val qty: Int,
    val catatan: String = ""
) { val subtotal: Int get() = menu.harga * qty }

class KasirViewModel(private val repo: PosRepository) : ViewModel() {

    val menu: StateFlow<List<MenuItem>> = repo.menu
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Meta info order
    var nomorMeja by mutableStateOf("")
    var tipeOrder by mutableStateOf("DINE_IN")
    var namaPelanggan by mutableStateOf("")
    var editingOrderId by mutableStateOf<Long?>(null) // kalau lagi edit open bill

    // Search
    var searchQuery by mutableStateOf("")
    var kategoriFilter by mutableStateOf<String?>(null)

    private val _cart = MutableStateFlow<Map<String, CartLine>>(emptyMap())
    val cart: StateFlow<List<CartLine>> = _cart.map { it.values.toList() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val total: StateFlow<Int> = cart.map { it.sumOf { l -> l.subtotal } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun add(menu: MenuItem, catatan: String = "") {
        val key = "${menu.id}|$catatan"
        _cart.update { map ->
            val e = map[key]
            if (e == null) map + (key to CartLine(key, menu, 1, catatan))
            else map + (key to e.copy(qty = e.qty + 1))
        }
    }

    fun decrease(key: String) = _cart.update { map ->
        val e = map[key] ?: return@update map
        if (e.qty <= 1) map - key else map + (key to e.copy(qty = e.qty - 1))
    }

    fun remove(key: String) { _cart.update { it - key } }
    fun clearCart() { _cart.value = emptyMap() }

    fun resetOrder() {
        nomorMeja = ""
        tipeOrder = "DINE_IN"
        namaPelanggan = ""
        editingOrderId = null
        clearCart()
    }

    fun loadOpenBill(orderId: Long) {
        viewModelScope.launch {
            val order = repo.getOrder(orderId) ?: return@launch
            val items = repo.itemsOf(orderId)
            nomorMeja = order.nomorMeja
            tipeOrder = order.tipeOrder
            namaPelanggan = order.namaPelanggan
            editingOrderId = order.id
            _cart.value = items.associate { it ->
                val key = "${it.menuId}|${it.catatan}"
                val menuItem = repo.getMenu(it.menuId) ?: MenuItem(
                    id = it.menuId, nama = it.namaMenu, harga = it.hargaSatuan
                )
                key to CartLine(key, menuItem, it.qty, it.catatan)
            }
        }
    }

    /** Simpan sebagai open bill (belum bayar). */
    fun simpanOpenBill(onDone: () -> Unit) {
        val lines = cart.value
        if (lines.isEmpty()) return
        viewModelScope.launch {
            val totalInt = lines.sumOf { it.subtotal }
            val items = lines.map {
                OrderItem(
                    orderId = 0, menuId = it.menu.id, namaMenu = it.menu.nama,
                    hargaSatuan = it.menu.harga, qty = it.qty, catatan = it.catatan
                )
            }
            val existingId = editingOrderId
            if (existingId != null) {
                val order = Order(
                    id = existingId, timestamp = System.currentTimeMillis(),
                    nomorMeja = nomorMeja, tipeOrder = tipeOrder,
                    namaPelanggan = namaPelanggan, total = totalInt,
                    metodeBayar = "", dibayar = 0, kembalian = 0, status = "OPEN"
                )
                repo.updateOrderWithItems(order, items)
            } else {
                val order = Order(
                    timestamp = System.currentTimeMillis(),
                    nomorMeja = nomorMeja, tipeOrder = tipeOrder,
                    namaPelanggan = namaPelanggan, total = totalInt,
                    metodeBayar = "", dibayar = 0, kembalian = 0, status = "OPEN"
                )
                repo.simpanOrder(order, items)
            }
            resetOrder()
            onDone()
        }
    }

    /** Bayar sekarang (langsung PAID). */
    fun checkout(metode: String, dibayar: Int, onDone: (Long) -> Unit) {
        val lines = cart.value
        if (lines.isEmpty()) return
        viewModelScope.launch {
            val totalInt = lines.sumOf { it.subtotal }
            val items = lines.map {
                OrderItem(
                    orderId = 0, menuId = it.menu.id, namaMenu = it.menu.nama,
                    hargaSatuan = it.menu.harga, qty = it.qty, catatan = it.catatan
                )
            }
            val existingId = editingOrderId
            val id: Long
            if (existingId != null) {
                val order = Order(
                    id = existingId, timestamp = System.currentTimeMillis(),
                    nomorMeja = nomorMeja, tipeOrder = tipeOrder,
                    namaPelanggan = namaPelanggan, total = totalInt,
                    metodeBayar = metode, dibayar = dibayar,
                    kembalian = (dibayar - totalInt).coerceAtLeast(0), status = "PAID"
                )
                repo.updateOrderWithItems(order, items)
                id = existingId
            } else {
                val order = Order(
                    timestamp = System.currentTimeMillis(),
                    nomorMeja = nomorMeja, tipeOrder = tipeOrder,
                    namaPelanggan = namaPelanggan, total = totalInt,
                    metodeBayar = metode, dibayar = dibayar,
                    kembalian = (dibayar - totalInt).coerceAtLeast(0), status = "PAID"
                )
                id = repo.simpanOrder(order, items)
            }
            resetOrder()
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

class OpenBillViewModel(repo: PosRepository) : ViewModel() {
    val openBills: StateFlow<List<Order>> = repo.openBills
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

class RiwayatViewModel(repo: PosRepository) : ViewModel() {
    val orders: StateFlow<List<Order>> = repo.paidOrders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

class VMFactory(private val repo: PosRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(KasirViewModel::class.java) -> KasirViewModel(repo) as T
        modelClass.isAssignableFrom(MenuViewModel::class.java) -> MenuViewModel(repo) as T
        modelClass.isAssignableFrom(OpenBillViewModel::class.java) -> OpenBillViewModel(repo) as T
        modelClass.isAssignableFrom(RiwayatViewModel::class.java) -> RiwayatViewModel(repo) as T
        else -> error("VM tidak dikenal: ${modelClass.name}")
    }
}

// ═══════════════════════════════════════════════════════════
// SCREENS
// ═══════════════════════════════════════════════════════════

// ---------- KASIR (adaptive) ----------
@Composable
fun KasirScreen(
    vm: KasirViewModel,
    onOpenKeranjang: () -> Unit,
    onBayar: () -> Unit
) {
    BoxWithConstraints {
        val isTablet = maxWidth >= 720.dp
        if (isTablet) {
            Row(Modifier.fillMaxSize()) {
                MenuPane(vm, Modifier.weight(1f), isTablet = true)
                VerticalDivider()
                CartPane(
                    vm = vm,
                    modifier = Modifier.width(380.dp),
                    onBayar = onBayar
                )
            }
        } else {
            MenuPane(vm, Modifier.fillMaxSize(), isTablet = false)
            // Bottom bar cart (mobile)
            val cart by vm.cart.collectAsState()
            val total by vm.total.collectAsState()
            if (cart.isNotEmpty()) {
                Box(Modifier.fillMaxSize()) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth(),
                        shadowElevation = 8.dp,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("${cart.sumOf { it.qty }} item",
                                    style = MaterialTheme.typography.bodySmall)
                                Text(total.rupiah(), fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleLarge)
                            }
                            Button(onClick = onOpenKeranjang,
                                colors = ButtonDefaults.buttonColors(containerColor = BRAND)) {
                                Icon(Icons.Default.ShoppingCart, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Keranjang")
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuPane(vm: KasirViewModel, modifier: Modifier = Modifier, isTablet: Boolean) {
    val menu by vm.menu.collectAsState()
    val kategoriList = remember(menu) { listOf(null) + menu.map { it.kategori }.distinct() }
    val filtered = remember(menu, vm.searchQuery, vm.kategoriFilter) {
        menu.filter {
            (vm.kategoriFilter == null || it.kategori == vm.kategoriFilter) &&
            (vm.searchQuery.isBlank() || it.nama.contains(vm.searchQuery, ignoreCase = true))
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("iyonzkasir",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = BRAND)
                        Text(
                            if (vm.nomorMeja.isBlank()) "Belum pilih meja"
                            else "Meja ${vm.nomorMeja} • ${tipeOrderLabel(vm.tipeOrder)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { /* TODO dialog meja */ }) {
                        Icon(Icons.Default.TableRestaurant, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            // Meja + tipe order
            OrderMetaBar(vm)

            // Search
            OutlinedTextField(
                value = vm.searchQuery,
                onValueChange = { vm.searchQuery = it },
                placeholder = { Text("Cari menu...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (vm.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { vm.searchQuery = "" }) {
                            Icon(Icons.Default.Close, null)
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            )

            // Kategori
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(kategoriList.size) { i ->
                    val k = kategoriList[i]
                    FilterChip(
                        selected = k == vm.kategoriFilter,
                        onClick = { vm.kategoriFilter = k },
                        label = {
