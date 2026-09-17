package com.iyonzkasir

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.room.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

// ═══════════════════════════════════════════════════════════
// KONSTANTA & HELPER
// ═══════════════════════════════════════════════════════════

private val rupiahFormat: NumberFormat by lazy {
    NumberFormat.getNumberInstance(Locale("in", "ID"))
}

private val dateFormatLocal = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue() = SimpleDateFormat("dd MMM HH:mm", Locale("in", "ID"))
}

fun Int.rupiah(): String = "Rp " + rupiahFormat.format(this)

fun Long.tanggal(): String = dateFormatLocal.get()!!.format(Date(this))

fun String.toRupiahOrNull(): Int? =
    filter { it.isDigit() }.takeIf { it.isNotEmpty() }?.toIntOrNull()

val BRAND = Color(0xFFFF6B35)
val BRAND_LIGHT = Color(0xFFFFE4D6)
val BRAND_DARK = Color(0xFF7A2E10)

enum class TipeOrder(val label: String) {
    DINE_IN("Dine-in"),
    TAKE_AWAY("Take-away"),
    DELIVERY("Delivery");

    companion object {
        fun fromKey(k: String) = entries.firstOrNull { it.name == k } ?: DINE_IN
    }
}

enum class MetodeBayar(val label: String) {
    CASH("Cash"), QRIS("QRIS"), DEBIT("Debit")
}

enum class StatusOrder { OPEN, PAID }

// ═══════════════════════════════════════════════════════════
// IMAGE HELPERS — simpan ke internal storage, bukan content://
// ═══════════════════════════════════════════════════════════

object MenuImageStore {

    private const val DIR_NAME = "menu_images"

    private fun dir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { mkdirs() }

    /**
     * Copy content Uri (dari galeri) ke internal storage app.
     * Return absolute path file lokal, atau null kalau gagal.
     */
    fun copyFromUri(context: Context, uri: Uri): String? = runCatching {
        val target = File(dir(context), "menu_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        if (target.length() > 0) target.absolutePath else null
    }.getOrNull()

    /**
     * Hapus file gambar lokal. Return true kalau file ada & terhapus.
     * Aman dipanggil berkali-kali.
     */
    fun delete(path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        return runCatching {
            val f = File(path)
            f.exists() && f.delete()
        }.getOrDefault(false)
    }

    /** Cek file masih ada (buat fallback UI). */
    fun exists(path: String?): Boolean =
        !path.isNullOrBlank() && File(path).exists()
}

// ═══════════════════════════════════════════════════════════
// DATA LAYER
// ═══════════════════════════════════════════════════════════

@Entity(
    tableName = "menu_items",
    indices = [Index("kategori"), Index("nama")]
)
data class MenuItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nama: String,
    val harga: Int,
    val kategori: String = "Umum",
    val fotoUri: String? = null, // sekarang: absolute path file lokal
    val tersedia: Boolean = true
)

@Entity(
    tableName = "orders",
    indices = [Index("status"), Index("timestamp")]
)
data class Order(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = 0,
    val nomorMeja: String = "",
    val tipeOrder: String = TipeOrder.DINE_IN.name,
    val namaPelanggan: String = "",
    val total: Int = 0,
    val metodeBayar: String = "",
    val dibayar: Int = 0,
    val kembalian: Int = 0,
    val status: String = StatusOrder.OPEN.name
)

@Entity(
    tableName = "order_items",
    indices = [Index("orderId"), Index("menuId")],
    foreignKeys = [
        ForeignKey(
            entity = Order::class,
            parentColumns = ["id"],
            childColumns = ["orderId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class OrderItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: Long = 0,
    val menuId: Long = 0,
    val namaMenu: String = "",
    val hargaSatuan: Int = 0,
    val qty: Int = 1,
    val catatan: String = ""
) {
    val subtotal: Int get() = hargaSatuan * qty
}

data class OrderWithItems(
    @Embedded val order: Order,
    @Relation(parentColumn = "id", entityColumn = "orderId")
    val items: List<OrderItem>
)

// ─── DAO ───────────────────────────────────────────────────

@Dao
interface MenuDao {
    @Query("SELECT * FROM menu_items ORDER BY kategori, nama")
    fun observeAll(): Flow<List<MenuItem>>

    @Query("SELECT * FROM menu_items WHERE id = :id")
    suspend fun getById(id: Long): MenuItem?

    @Upsert
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

    @Transaction
    @Query("SELECT * FROM orders WHERE status = 'OPEN' ORDER BY timestamp DESC")
    fun observeOpenBills(): Flow<List<Order>>

    @Transaction
    @Query("SELECT * FROM orders WHERE status = 'PAID' ORDER BY timestamp DESC")
    fun observePaid(): Flow<List<Order>>

    @Transaction
    @Query("SELECT * FROM orders WHERE id = :id")
    suspend fun getOrderWithItems(id: Long): OrderWithItems?

    @Query("SELECT * FROM orders WHERE id = :id")
    suspend fun getOrder(id: Long): Order?

    @Query("SELECT COUNT(*) FROM orders WHERE status = 'OPEN'")
    fun observeOpenCount(): Flow<Int>
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
                ).fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}

// ─── Repository ────────────────────────────────────────────

sealed interface DataResult<out T> {
    data class Success<T>(val data: T) : DataResult<T>
    data class Error(val throwable: Throwable) : DataResult<Nothing>
}

suspend fun <T> safeCall(block: suspend () -> T): DataResult<T> = try {
    DataResult.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (t: Throwable) {
    DataResult.Error(t)
}

class PosRepository(
    private val menuDao: MenuDao,
    private val orderDao: OrderDao
) {
    val menu: Flow<List<MenuItem>> = menuDao.observeAll()
    val openBills: Flow<List<Order>> = orderDao.observeOpenBills()
    val paidOrders: Flow<List<Order>> = orderDao.observePaid()
    val openBillCount: Flow<Int> = orderDao.observeOpenCount()

    suspend fun upsertMenu(item: MenuItem): DataResult<Long> =
        safeCall { menuDao.upsert(item) }

    suspend fun deleteMenu(item: MenuItem): DataResult<Unit> =
        safeCall { menuDao.delete(item) }

    suspend fun setTersedia(id: Long, v: Boolean): DataResult<Unit> =
        safeCall { menuDao.setTersedia(id, v) }

    suspend fun getMenu(id: Long): MenuItem? = menuDao.getById(id)

    suspend fun simpanOrder(order: Order, items: List<OrderItem>): DataResult<Long> =
        safeCall { orderDao.simpanOrder(order, items) }

    suspend fun updateOrderWithItems(order: Order, items: List<OrderItem>): DataResult<Unit> =
        safeCall { orderDao.updateOrderWithItems(order, items) }

    suspend fun getOrder(id: Long): Order? = orderDao.getOrder(id)
    suspend fun getOrderWithItems(id: Long) = orderDao.getOrderWithItems(id)
}

// ═══════════════════════════════════════════════════════════
// VIEWMODELS
// ═══════════════════════════════════════════════════════════

sealed interface UiEvent {
    data class ShowMessage(val text: String) : UiEvent
}

data class CartLine(
    val key: String,
    val menu: MenuItem,
    val qty: Int,
    val catatan: String = ""
) {
    val subtotal: Int get() = menu.harga * qty
}

// ─── KasirViewModel ────────────────────────────────────────

class KasirViewModel(private val repo: PosRepository) : ViewModel() {

    val menu: StateFlow<List<MenuItem>> = repo.menu
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events: Flow<UiEvent> = _events.receiveAsFlow()

    var nomorMeja by mutableStateOf("")
        private set
    var tipeOrder by mutableStateOf(TipeOrder.DINE_IN)
        private set
    var namaPelanggan by mutableStateOf("")
        private set
    var editingOrderId by mutableStateOf<Long?>(null)
        private set

    var searchQuery by mutableStateOf("")
    var kategoriFilter by mutableStateOf<String?>(null)

    private val _cart = MutableStateFlow<Map<String, CartLine>>(emptyMap())
    val cart: StateFlow<List<CartLine>> = _cart
        .map { it.values.toList() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val total: StateFlow<Int> = _cart
        .map { map -> map.values.sumOf { it.subtotal } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val itemCount: StateFlow<Int> = _cart
        .map { map -> map.values.sumOf { it.qty } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    var isSaving by mutableStateOf(false)
        private set

    fun setNomorMeja(v: String) { nomorMeja = v.filter { it.isDigit() } }
    fun setTipeOrder(t: TipeOrder) { tipeOrder = t }
    fun setNamaPelanggan(v: String) { namaPelanggan = v }

    fun add(menu: MenuItem, catatan: String = "") {
        val key = cartKey(menu.id, catatan)
        _cart.update { map ->
            val existing = map[key]
            map + (key to if (existing == null) {
                CartLine(key, menu, 1, catatan)
            } else {
                existing.copy(qty = existing.qty + 1)
            })
        }
    }

    fun decrease(key: String) = _cart.update { map ->
        val e = map[key] ?: return@update map
        if (e.qty <= 1) map - key else map + (key to e.copy(qty = e.qty - 1))
    }

    fun remove(key: String) { _cart.update { it - key } }

    fun updateNote(oldKey: String, newNote: String) {
        _cart.update { map ->
            val line = map[oldKey] ?: return@update map
            if (line.catatan == newNote) return@update map
            val newKey = cartKey(line.menu.id, newNote)
            (map - oldKey) + (newKey to line.copy(key = newKey, catatan = newNote))
        }
    }

    fun clearCart() { _cart.value = emptyMap() }

    fun resetOrder() {
        nomorMeja = ""
        tipeOrder = TipeOrder.DINE_IN
        namaPelanggan = ""
        editingOrderId = null
        clearCart()
    }

    fun loadOpenBill(orderId: Long, onLoaded: () -> Unit = {}) {
        viewModelScope.launch {
            val order = repo.getOrder(orderId)
            if (order == null) {
                _events.send(UiEvent.ShowMessage("Order tidak ditemukan"))
                return@launch
            }
            val items = repo.getOrderWithItems(orderId)?.items.orEmpty()

            val newCart = items.associate { item ->
                val key = cartKey(item.menuId, item.catatan)
                val menuItem = repo.getMenu(item.menuId) ?: MenuItem(
                    id = item.menuId,
                    nama = item.namaMenu,
                    harga = item.hargaSatuan
                )
                key to CartLine(key, menuItem, item.qty, item.catatan)
            }

            nomorMeja = order.nomorMeja
            tipeOrder = TipeOrder.fromKey(order.tipeOrder)
            namaPelanggan = order.namaPelanggan
            editingOrderId = order.id
            _cart.value = newCart
            onLoaded()
        }
    }

    fun simpanOpenBill() {
        if (isSaving) return
        val lines = _cart.value.values.toList()
        if (lines.isEmpty()) {
            viewModelScope.launch { _events.send(UiEvent.ShowMessage("Keranjang kosong")) }
            return
        }
        viewModelScope.launch {
            isSaving = true
            try {
                when (val result = persist(lines, markAsPaid = false, metode = null, dibayar = 0)) {
                    is DataResult.Success -> {
                        resetOrder()
                        _events.send(UiEvent.ShowMessage("Open bill tersimpan"))
                    }
                    is DataResult.Error -> _events.send(
                        UiEvent.ShowMessage("Gagal simpan: ${result.throwable.message}")
                    )
                }
            } finally {
                isSaving = false
            }
        }
    }

    fun checkout(metode: MetodeBayar, dibayar: Int, onSuccess: (Long) -> Unit) {
        if (isSaving) return
        val lines = _cart.value.values.toList()
        if (lines.isEmpty()) {
            viewModelScope.launch { _events.send(UiEvent.ShowMessage("Keranjang kosong")) }
            return
        }
        viewModelScope.launch {
            isSaving = true
            try {
                when (val result = persist(lines, true, metode, dibayar)) {
                    is DataResult.Success -> {
                        resetOrder()
                        onSuccess(result.data)
                    }
                    is DataResult.Error -> _events.send(
                        UiEvent.ShowMessage("Gagal checkout: ${result.throwable.message}")
                    )
                }
            } finally {
                isSaving = false
            }
        }
    }

    private suspend fun persist(
        lines: List<CartLine>,
        markAsPaid: Boolean,
        metode: MetodeBayar?,
        dibayar: Int
    ): DataResult<Long> {
        val totalInt = lines.sumOf { it.subtotal }
        val items = lines.map {
            OrderItem(
                orderId = 0,
                menuId = it.menu.id,
                namaMenu = it.menu.nama,
                hargaSatuan = it.menu.harga,
                qty = it.qty,
                catatan = it.catatan
            )
        }
        val existingId = editingOrderId
        val orderBase = Order(
            id = existingId ?: 0,
            timestamp = System.currentTimeMillis(),
            nomorMeja = nomorMeja,
            tipeOrder = tipeOrder.name,
            namaPelanggan = namaPelanggan,
            total = totalInt,
            metodeBayar = metode?.name.orEmpty(),
            dibayar = if (markAsPaid) dibayar else 0,
            kembalian = if (markAsPaid) (dibayar - totalInt).coerceAtLeast(0) else 0,
            status = if (markAsPaid) StatusOrder.PAID.name else StatusOrder.OPEN.name
        )

        return if (existingId != null) {
            when (val r = repo.updateOrderWithItems(orderBase, items)) {
                is DataResult.Success -> DataResult.Success(existingId)
                is DataResult.Error -> r
            }
        } else {
            repo.simpanOrder(orderBase, items)
        }
    }

    private fun cartKey(menuId: Long, catatan: String) = "$menuId|$catatan"
}

// ─── MenuViewModel (dengan image lifecycle) ────────────────

class MenuViewModel(private val repo: PosRepository) : ViewModel() {

    val menu: StateFlow<List<MenuItem>> = repo.menu
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events: Flow<UiEvent> = _events.receiveAsFlow()

    /**
     * Simpan menu. Kalau oldImagePath != newImagePath (dan oldImagePath != null),
     * hapus file gambar lama biar gak numpuk di storage.
     */
    fun save(
        item: MenuItem,
        oldImagePath: String? = null,
        onDone: () -> Unit = {}
    ) = viewModelScope.launch {
        when (val result = repo.upsertMenu(item)) {
            is DataResult.Success -> {
                // Hapus file gambar lama kalau beda dari yang baru
                if (!oldImagePath.isNullOrBlank() && oldImagePath != item.fotoUri) {
                    MenuImageStore.delete(oldImagePath)
                }
                onDone()
            }
            is DataResult.Error -> _events.send(UiEvent.ShowMessage("Gagal simpan menu"))
        }
    }

    fun delete(item: MenuItem) = viewModelScope.launch {
        when (val result = repo.deleteMenu(item)) {
            is DataResult.Success -> {
                // Hapus file gambar dari storage juga
                MenuImageStore.delete(item.fotoUri)
                _events.send(UiEvent.ShowMessage("${item.nama} dihapus"))
            }
            is DataResult.Error -> _events.send(UiEvent.ShowMessage("Gagal hapus"))
        }
    }

    fun toggle(item: MenuItem) = viewModelScope.launch {
        repo.setTersedia(item.id, !item.tersedia)
    }

    suspend fun get(id: Long) = repo.getMenu(id)
}

// ─── OpenBill / Riwayat ────────────────────────────────────

class OpenBillViewModel(repo: PosRepository) : ViewModel() {
    val openBills: StateFlow<List<Order>> = repo.openBills
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

class RiwayatViewModel(repo: PosRepository) : ViewModel() {
    val orders: StateFlow<List<Order>> = repo.paidOrders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

// ─── Factory ───────────────────────────────────────────────

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
// REUSABLE COMPONENTS
// ═══════════════════════════════════════════════════════════

/**
 * Wrapper AsyncImage yang bener:
 * - Menerima path file lokal ATAU URL/content URI
 * - Placeholder + error state (biar gak blank)
 * - Crossfade biar smooth
 */
@Composable
fun MenuImage(
    path: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val ctx = LocalContext.current
    val hasImage = remember(path) { MenuImageStore.exists(path) }

    if (hasImage && path != null) {
        AsyncImage(
            model = ImageRequest.Builder(ctx)
                .data(File(path))    // file lokal (absolute path)
                .crossfade(true)
                .build(),
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier
        )
    } else {
        // Fallback emoji kalau gak ada gambar
        Box(
            modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text("🍽️", style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun EmptyState(
    emoji: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(8.dp))
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════
// SCREENS
// ═══════════════════════════════════════════════════════════

// ─── Kasir ─────────────────────────────────────────────────

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
                CartPane(vm, Modifier.width(380.dp), onBayar)
            }
        } else {
            MenuPane(vm, Modifier.fillMaxSize(), isTablet = false)
            MobileCartBar(vm, onOpenKeranjang)
        }
    }
}

@Composable
private fun MobileCartBar(vm: KasirViewModel, onOpenKeranjang: () -> Unit) {
    val cart by vm.cart.collectAsState()
    val total by vm.total.collectAsState()
    val itemCount by vm.itemCount.collectAsState()
    if (cart.isEmpty()) return

    Box(Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("$itemCount item", style = MaterialTheme.typography.bodySmall)
                    Text(
                        total.rupiah(),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                Button(
                    onClick = onOpenKeranjang,
                    colors = ButtonDefaults.buttonColors(containerColor = BRAND)
                ) {
                    Icon(Icons.Default.ShoppingCart, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Keranjang")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuPane(vm: KasirViewModel, modifier: Modifier = Modifier, isTablet: Boolean) {
    val menu by vm.menu.collectAsState()
    val kategoriList = remember(menu) {
        listOf<String?>(null) + menu.map { it.kategori }.distinct()
    }
    val filtered by remember(menu, vm.searchQuery, vm.kategoriFilter) {
        derivedStateOf {
            menu.filter {
                (vm.kategoriFilter == null || it.kategori == vm.kategoriFilter) &&
                        (vm.searchQuery.isBlank() ||
                                it.nama.contains(vm.searchQuery, ignoreCase = true))
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "iyonzkasir",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = BRAND
                        )
                        Text(
                            if (vm.nomorMeja.isBlank()) "Belum pilih meja"
                            else "Meja ${vm.nomorMeja} • ${vm.tipeOrder.label}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                actions = {
                    if (vm.editingOrderId != null) {
                        AssistChip(
                            onClick = { vm.resetOrder() },
                            label = { Text("Batal Edit") },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OrderMetaBar(vm)

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

            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(kategoriList.size) { i ->
                    val k = kategoriList[i]
                    FilterChip(
                        selected = k == vm.kategoriFilter,
                        onClick = { vm.kategoriFilter = k },
                        label = { Text(k ?: "Semua") }
                    )
                }
            }

            if (filtered.isEmpty()) {
                EmptyState(
                    emoji = if (menu.isEmpty()) "🍽️" else "🔍",
                    title = if (menu.isEmpty()) "Belum ada menu" else "Nggak ada yang cocok",
                    subtitle = if (menu.isEmpty()) "Tambah menu di tab Menu ya"
                    else "Coba kata kunci lain",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(if (isTablet) 3 else 2),
                    contentPadding = PaddingValues(12.dp, 4.dp, 12.dp, 100.dp),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrderMetaBar(vm: KasirViewModel) {
    var showDialog by remember { mutableStateOf(false) }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AssistChip(
            onClick = { showDialog = true },
            leadingIcon = { Icon(Icons.Default.TableRestaurant, null, Modifier.size(18.dp)) },
            label = { Text(if (vm.nomorMeja.isBlank()) "Pilih Meja" else "Meja ${vm.nomorMeja}") }
        )
        TipeOrder.entries.forEach { t ->
            FilterChip(
                selected = vm.tipeOrder == t,
                onClick = { vm.setTipeOrder(t) },
                label = { Text(t.label, style = MaterialTheme.typography.bodySmall) }
            )
        }
    }

    if (showDialog) {
        var tempMeja by remember { mutableStateOf(vm.nomorMeja) }
        var tempNama by remember { mutableStateOf(vm.namaPelanggan) }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Info Meja & Pelanggan") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = tempMeja,
                        onValueChange = { tempMeja = it.filter { c -> c.isDigit() } },
                        label = { Text("Nomor meja") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = tempNama,
                        onValueChange = { tempNama = it },
                        label = { Text("Nama pelanggan (opsional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.setNomorMeja(tempMeja)
                    vm.setNamaPelanggan(tempNama)
                    showDialog = false
                }) { Text("Simpan") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Batal") }
            }
        )
    }
}

@Composable
private fun MenuCard(m: MenuItem, enabled: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().height(170.dp)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(100.dp)) {
                MenuImage(
                    path = m.fotoUri,
                    contentDescription = m.nama,
                    modifier = Modifier.fillMaxSize()
                )
                if (!enabled) {
                    Box(
                        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Habis", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Column(Modifier.padding(8.dp)) {
                Text(
                    m.nama, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    m.harga.rupiah(), color = BRAND, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

// ─── Cart Pane ─────────────────────────────────────────────

@Composable
fun CartPane(vm: KasirViewModel, modifier: Modifier = Modifier, onBayar: () -> Unit) {
    val cart by vm.cart.collectAsState()
    val total by vm.total.collectAsState()
    var noteFor by remember { mutableStateOf<CartLine?>(null) }

    Column(modifier.fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant)) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    "Keranjang",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    buildString {
                        append(if (vm.nomorMeja.isBlank()) "Tanpa meja" else "Meja ${vm.nomorMeja}")
                        append(" • "); append(vm.tipeOrder.label)
                        if (vm.namaPelanggan.isNotBlank()) {
                            append(" • "); append(vm.namaPelanggan)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        HorizontalDivider()

        if (cart.isEmpty()) {
            EmptyState(
                "🛒", "Keranjang kosong", "Tambahkan menu dari daftar",
                Modifier.weight(1f).fillMaxWidth()
            )
        } else {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(cart, key = { it.key }) { line ->
                    CartLineItem(
                        line = line,
                        onAdd = { vm.add(line.menu, line.catatan) },
                        onDecrease = { vm.decrease(line.key) },
                        onRemove = { vm.remove(line.key) },
                        onEditNote = { noteFor = line }
                    )
                }
            }
        }

        HorizontalDivider()
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Row {
                    Text("Total", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Text(
                        total.rupiah(),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                        color = BRAND
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { vm.simpanOpenBill() },
                        enabled = cart.isNotEmpty() && !vm.isSaving,
                        modifier = Modifier.weight(1f)
                    ) { Text("Open Bill") }
                    Button(
                        onClick = onBayar,
                        enabled = cart.isNotEmpty() && !vm.isSaving,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = BRAND)
                    ) { Text("Bayar") }
                }
            }
        }
    }

    noteFor?.let { line ->
        NoteDialog(
            initial = line.catatan,
            menuName = line.menu.nama,
            onDismiss = { noteFor = null },
            onSave = { newNote ->
                vm.updateNote(line.key, newNote.trim())
                noteFor = null
            }
        )
    }
}

@Composable
private fun CartLineItem(
    line: CartLine,
    onAdd: () -> Unit,
    onDecrease: () -> Unit,
    onRemove: () -> Unit,
    onEditNote: () -> Unit
) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(line.menu.nama, fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium)
                    Text(line.menu.harga.rupiah(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Close, null, Modifier.size(18.dp))
                }
            }
            if (line.catatan.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Surface(color = BRAND_LIGHT, shape = RoundedCornerShape(6.dp)) {
                    Text(
                        "📝 ${line.catatan}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDecrease, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Remove, null, Modifier.size(18.dp))
                }
                Text(line.qty.toString(), fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp))
                IconButton(onClick = onAdd, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onEditNote) {
                    Icon(Icons.Default.Edit, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Catatan")
                }
                Text(line.subtotal.rupiah(), fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun NoteDialog(
    initial: String,
    menuName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    val presets = remember {
        listOf("Pedas", "Tanpa bawang", "Extra nasi", "Tanpa sambal", "Goreng kering")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Catatan untuk $menuName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Catatan") },
                    placeholder = { Text("cth: pedas, tanpa bawang") },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(presets) { t ->
                        AssistChip(
                            onClick = { text = if (text.isBlank()) t else "$text, $t" },
                            label = { Text(t, style = MaterialTheme.typography.bodySmall) }
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text.trim()) }) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

// ─── Keranjang Mobile ──────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeranjangScreen(vm: KasirViewModel, onBack: () -> Unit, onBayar: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Keranjang") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                }
            )
        }
    ) { pad ->
        CartPane(vm, Modifier.padding(pad).fillMaxSize(), onBayar)
    }
}

// ─── Bayar ─────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BayarScreen(vm: KasirViewModel, onBack: () -> Unit, onSelesai: () -> Unit) {
    val total by vm.total.collectAsState()
    var metode by remember { mutableStateOf(MetodeBayar.CASH) }
    var dibayarText by remember { mutableStateOf("") }
    val dibayar = dibayarText.toRupiahOrNull() ?: 0
    val kembalian = (dibayar - total).coerceAtLeast(0)
    val cukup = if (metode == MetodeBayar.CASH) dibayar >= total else true

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
            Card(colors = CardDefaults.cardColors(containerColor = BRAND_LIGHT)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Total tagihan", style = MaterialTheme.typography.bodyMedium,
                        color = BRAND_DARK)
                    Text(
                        total.rupiah(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = BRAND_DARK
                    )
                    if (vm.nomorMeja.isNotBlank() || vm.namaPelanggan.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            buildString {
                                if (vm.nomorMeja.isNotBlank()) append("Meja ${vm.nomorMeja}")
                                if (vm.namaPelanggan.isNotBlank()) {
                                    if (isNotEmpty()) append(" • ")
                                    append(vm.namaPelanggan)
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = BRAND_DARK
                        )
                    }
                }
            }

            Text("Metode bayar", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetodeBayar.entries.forEach { m ->
                    FilterChip(
                        selected = metode == m,
                        onClick = { metode = m },
                        label = { Text(m.label) }
                    )
                }
            }

            if (metode == MetodeBayar.CASH) {
                OutlinedTextField(
                    value = dibayarText,
                    onValueChange = { dibayarText = it.filter { c -> c.isDigit() } },
                    label = { Text("Uang diterima") },
                    prefix = { Text("Rp ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                QuickCash(total) { dibayarText = it.toString() }
                Row {
                    Text("Kembalian", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Text(kembalian.rupiah(), fontWeight = FontWeight.Bold, color = BRAND)
                }
            }

            Spacer(Modifier.weight(1f))
            Button(
                onClick = {
                    vm.checkout(metode, if (metode == MetodeBayar.CASH) dibayar else total) {
                        onSelesai()
                    }
                },
                enabled = cukup && !vm.isSaving,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BRAND)
            ) {
                if (vm.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text("Konfirmasi Bayar", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun QuickCash(total: Int, onPick: (Int) -> Unit) {
    val suggestions = remember(total) {
        val rounded5k = ((total + 4_999) / 5_000) * 5_000
        val rounded10k = ((total + 9_999) / 10_000) * 10_000
        listOf(total, rounded5k, rounded10k, 50_000, 100_000, 200_000)
            .distinct()
            .filter { it >= total }
            .take(4)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        suggestions.forEach { s ->
            AssistChip(onClick = { onPick(s) }, label = { Text(s.rupiah()) })
        }
    }
}

// ─── Open Bill ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenBillScreen(vm: OpenBillViewModel, onPick: (Long) -> Unit) {
    val bills by vm.openBills.collectAsState()
    Scaffold(topBar = { TopAppBar(title = { Text("Open Bill") }) }) { pad ->
        if (bills.isEmpty()) {
            EmptyState(
                "📋", "Belum ada open bill", "Bill yang disimpan akan muncul di sini",
                Modifier.padding(pad).fillMaxSize()
            )
        } else {
            LazyColumn(
                Modifier.padding(pad).fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(bills, key = { it.id }) { o ->
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                        Row(
                            Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (o.nomorMeja.isNotBlank()) "Meja ${o.nomorMeja}"
                                    else "Tanpa meja",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    buildString {
                                        append(TipeOrder.fromKey(o.tipeOrder).label)
                                        if (o.namaPelanggan.isNotBlank()) {
                                            append(" • "); append(o.namaPelanggan)
                                        }
                                    },
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    o.timestamp.tanggal(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(o.total.rupiah(), fontWeight = FontWeight.Bold, color = BRAND)
                                Spacer(Modifier.height(6.dp))
                                Button(
                                    onClick = { onPick(o.id) },
                                    colors = ButtonDefaults.buttonColors(containerColor = BRAND),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) { Text("Bayar", style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Kelola Menu ───────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuScreen(vm: MenuViewModel, onEdit: (Long?) -> Unit) {
    val menu by vm.menu.collectAsState()
    var toDelete by remember { mutableStateOf<MenuItem?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Kelola Menu") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onEdit(null) },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Menu baru") },
                containerColor = BRAND
            )
        }
    ) { pad ->
        if (menu.isEmpty()) {
            EmptyState(
                "🍽️", "Belum ada menu", "Tekan tombol + untuk menambahkan",
                Modifier.padding(pad).fillMaxSize()
            )
        } else {
            LazyColumn(
                Modifier.padding(pad).fillMaxSize(),
                contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 100.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(menu, key = { it.id }) { m ->
                    MenuRow(
                        m = m,
                        onToggle = { vm.toggle(m) },
                        onEdit = { onEdit(m.id) },
                        onDelete = { toDelete = m }
                    )
                }
            }
        }
    }

    toDelete?.let { m ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Hapus menu?") },
            text = { Text("\"${m.nama}\" akan dihapus permanen.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(m)
                    toDelete = null
                }) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) { Text("Batal") }
            }
        )
    }
}

@Composable
private fun MenuRow(
    m: MenuItem,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            MenuImage(
                path = m.fotoUri,
                contentDescription = m.nama,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(m.nama, fontWeight = FontWeight.SemiBold)
                Text(m.harga.rupiah(), color = BRAND)
                Text(
                    m.kategori,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = m.tersedia, onCheckedChange = { onToggle() })
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, null) }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditMenuScreen(vm: MenuViewModel, menuId: Long?, onBack: () -> Unit) {
    val ctx = LocalContext.current

    var nama by remember { mutableStateOf("") }
    var hargaText by remember { mutableStateOf("") }
    var kategori by remember { mutableStateOf("Umum") }
    // Path file lokal (absolute path), hasil dari copy
    var fotoPath by remember { mutableStateOf<String?>(null) }
    // Path gambar lama (untuk dihapus kalau diganti)
    var oldFotoPath by remember { mutableStateOf<String?>(null) }
    var tersedia by remember { mutableStateOf(true) }
    var loaded by remember { mutableStateOf(menuId == null) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(menuId) {
        if (menuId != null) {
            vm.get(menuId)?.let {
                nama = it.nama
                hargaText = it.harga.toString()
                kategori = it.kategori
                fotoPath = it.fotoUri
                oldFotoPath = it.fotoUri
                tersedia = it.tersedia
            }
            loaded = true
        }
    }

    // Picker: ambil dari galeri → copy ke internal → dapat path lokal
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val newPath = MenuImageStore.copyFromUri(ctx, uri)
        if (newPath != null) {
            // Kalau user ganti foto berulang sebelum save, hapus temp yang tadi
            // (foto lama tetap disimpan buat dibersihin pas save)
            if (fotoPath != null && fotoPath != oldFotoPath) {
                MenuImageStore.delete(fotoPath)
            }
            fotoPath = newPath
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
            val harga = hargaText.toRupiahOrNull() ?: 0
            val canSave = nama.isNotBlank() && harga > 0 && !isSaving

            Column(
                Modifier.padding(pad).padding(16.dp).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Preview foto
                Box(
                    Modifier.fillMaxWidth().height(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    MenuImage(
                        path = fotoPath,
                        contentDescription = "Preview",
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { picker.launch(arrayOf("image/*")) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Photo, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (fotoPath == null) "Pilih Foto" else "Ganti Foto")
                    }
                    if (fotoPath != null) {
                        OutlinedButton(
                            onClick = {
                                // Hapus file yang barusan di-copy (belum di-save)
                                if (fotoPath != oldFotoPath) {
                                    MenuImageStore.delete(fotoPath)
                                }
                                fotoPath = null
                            }
                        ) {
                            Icon(Icons.Default.Delete, null)
                        }
                    }
                }

                OutlinedTextField(
                    value = nama, onValueChange = { nama = it },
                    label = { Text("Nama menu") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                OutlinedTextField(
                    value = hargaText,
                    onValueChange = { hargaText = it.filter { c -> c.isDigit() } },
                    label = { Text("Harga") },
                    prefix = { Text("Rp ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                OutlinedTextField(
                    value = kategori, onValueChange = { kategori = it },
                    label = { Text("Kategori") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Tersedia", Modifier.weight(1f))
                    Switch(checked = tersedia, onCheckedChange = { tersedia = it })
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        if (!canSave) return@Button
                        isSaving = true
                        vm.save(
                            item = MenuItem(
                                id = menuId ?: 0,
                                nama = nama.trim(),
                                harga = harga,
                                kategori = kategori.trim().ifBlank { "Umum" },
                                fotoUri = fotoPath,
                                tersedia = tersedia
                            ),
                            oldImagePath = oldFotoPath
                        ) {
                            isSaving = false
                            onBack()
                        }
                    },
                    enabled = canSave,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BRAND)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Simpan")
                }
            }
        }
    }
}

// ─── Riwayat ───────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RiwayatScreen(vm: RiwayatViewModel) {
    val orders by vm.orders.collectAsState()
    Scaffold(topBar = { TopAppBar(title = { Text("Riwayat Transaksi") }) }) { pad ->
        if (orders.isEmpty()) {
            EmptyState(
                "🧾", "Belum ada transaksi", "Transaksi yang sudah dibayar muncul di sini",
                Modifier.padding(pad).fillMaxSize()
            )
        } else {
            LazyColumn(
                Modifier.padding(pad).fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(orders, key = { it.id }) { o ->
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                        Row(
                            Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    buildString {
                                        if (o.nomorMeja.isNotBlank())
                                            append("Meja ${o.nomorMeja} • ")
                                        append(o.metodeBayar)
                                    },
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    buildString {
                                        append(o.timestamp.tanggal())
                                        if (o.namaPelanggan.isNotBlank()) {
                                            append(" • "); append(o.namaPelanggan)
                                        }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(o.total.rupiah(), fontWeight = FontWeight.Bold, color = BRAND)
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// APP + ACTIVITY + NAVIGATION
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
            val dark = isSystemInDarkTheme()
            val colors = if (dark) darkColorScheme(
                primary = BRAND,
                onPrimary = Color.White,
                secondaryContainer = BRAND_DARK
            ) else lightColorScheme(
                primary = BRAND,
                onPrimary = Color.White,
                secondaryContainer = BRAND_LIGHT
            )
            MaterialTheme(colorScheme = colors) {
                AppRoot(repo)
            }
        }
    }
}

sealed class Route(val path: String) {
    data object Kasir : Route("kasir")
    data object Keranjang : Route("keranjang")
    data object Bayar : Route("bayar")
    data object OpenBill : Route("openbill")
    data object Menu : Route("menu")
    data object MenuEdit : Route("menu/edit?menuId={menuId}") {
        fun build(menuId: Long?): String = "menu/edit?menuId=${menuId ?: -1L}"
    }
    data object Riwayat : Route("riwayat")

    companion object {
        const val ARG_MENU_ID = "menuId"
    }
}

private data class Tab(val route: Route, val label: String, val icon: ImageVector)

@Composable
fun AppRoot(repo: PosRepository) {
    val nav = rememberNavController()
    val factory = remember(repo) { VMFactory(repo) }
    val kasirVm: KasirViewModel = viewModel(factory = factory)
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        kasirVm.events.collect { e ->
            when (e) {
                is UiEvent.ShowMessage -> snackbarHost.showSnackbar(e.text)
            }
        }
    }

    val tabs = remember {
        listOf(
            Tab(Route.Kasir, "Kasir", Icons.Default.Home),
            Tab(Route.OpenBill, "Open Bill", Icons.Default.ReceiptLong),
            Tab(Route.Menu, "Menu", Icons.Default.Restaurant),
            Tab(Route.Riwayat, "Riwayat", Icons.Default.List),
        )
    }
    val current by nav.currentBackStackEntryAsState()
    val currentRoute = current?.destination?.route
    val showBar = tabs.any { it.route.path == currentRoute }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        bottomBar = {
            if (showBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    tabs.forEach { t ->
                        val selected = currentRoute?.startsWith(t.route.path) == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                nav.navigate(t.route.path) {
                                    popUpTo(nav.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(t.icon, null) },
                            label = { Text(t.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = BRAND,
                                selectedTextColor = BRAND,
                                indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        )
                    }
                }
            }
        }
    ) { pad ->
        NavHost(
            navController = nav,
            startDestination = Route.Kasir.path,
            modifier = Modifier.padding(pad)
        ) {
            composable(Route.Kasir.path) {
                KasirScreen(
                    vm = kasirVm,
                    onOpenKeranjang = { nav.navigate(Route.Keranjang.path) },
                    onBayar = { nav.navigate(Route.Bayar.path) }
                )
            }
            composable(Route.Keranjang.path) {
                KeranjangScreen(
                    vm = kasirVm,
                    onBack = { nav.popBackStack() },
                    onBayar = { nav.navigate(Route.Bayar.path) }
                )
            }
            composable(Route.Bayar.path) {
                BayarScreen(
                    vm = kasirVm,
                    onBack = { nav.popBackStack() },
                    onSelesai = { nav.popBackStack(Route.Kasir.path, inclusive = false) }
                )
            }
            composable(Route.OpenBill.path) {
                val vm: OpenBillViewModel = viewModel(factory = factory)
                OpenBillScreen(vm) { orderId ->
                    kasirVm.loadOpenBill(orderId) {
                        nav.navigate(Route.Bayar.path) {
                            popUpTo(Route.Kasir.path) { inclusive = false }
                        }
                    }
                }
            }
            composable(Route.Menu.path) {
                val vm: MenuViewModel = viewModel(factory = factory)
                MenuScreen(vm) { id -> nav.navigate(Route.MenuEdit.build(id)) }
            }
            composable(
                route = Route.MenuEdit.path,
                arguments = listOf(
                    navArgument(Route.ARG_MENU_ID) {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                )
            ) { entry ->
                val vm: MenuViewModel = viewModel(factory = factory)
                val id = entry.arguments?.getLong(Route.ARG_MENU_ID)?.takeIf { it >= 0 }
                EditMenuScreen(vm, menuId = id, onBack = { nav.popBackStack() })
            }
            composable(Route.Riwayat.path) {
                val vm: RiwayatViewModel = viewModel(factory = factory)
                RiwayatScreen(vm)
            }
        }
    }
}
