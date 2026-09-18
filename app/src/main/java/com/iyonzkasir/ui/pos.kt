package com.iyonzkasir.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
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
import coil.compose.AsyncImage
import com.iyonzkasir.*
import com.iyonzkasir.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// ═══════════════════════════════════════════════════════════
// VIEWMODELS
// ═══════════════════════════════════════════════════════════
data class CartLine(
    val key: String, val menu: MenuItem, val qty: Int, val catatan: String = ""
) { val subtotal: Int get() = menu.harga * qty }

class KasirViewModel(private val repo: PosRepository) : ViewModel() {
    val menu: StateFlow<List<MenuItem>> = repo.menu
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var nomorMeja by mutableStateOf("")
    var tipeOrder by mutableStateOf(TipeOrder.DINE_IN.id)
    var namaPelanggan by mutableStateOf("")
    var editingOrderId by mutableStateOf<Long?>(null)
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
        nomorMeja = ""; tipeOrder = TipeOrder.DINE_IN.id; namaPelanggan = ""
        editingOrderId = null; clearCart()
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

    fun simpanOpenBill(onDone: () -> Unit) {
        val lines = cart.value
        if (lines.isEmpty()) return
        viewModelScope.launch {
            val totalInt = lines.sumOf { it.subtotal }
            val items = lines.map {
                OrderItem(0, 0, it.menu.id, it.menu.nama, it.menu.harga, it.qty, it.catatan)
            }
            val existingId = editingOrderId
            if (existingId != null) {
                repo.updateOrderWithItems(Order(
                    id = existingId, timestamp = System.currentTimeMillis(),
                    nomorMeja = nomorMeja, tipeOrder = tipeOrder,
                    namaPelanggan = namaPelanggan, total = totalInt,
                    status = OrderStatus.OPEN.id
                ), items)
            } else {
                repo.simpanOrder(Order(
                    timestamp = System.currentTimeMillis(),
                    nomorMeja = nomorMeja, tipeOrder = tipeOrder,
                    namaPelanggan = namaPelanggan, total = totalInt,
                    status = OrderStatus.OPEN.id
                ), items)
            }
            resetOrder(); onDone()
        }
    }

    fun checkout(metode: String, dibayar: Int, onDone: (Long) -> Unit) {
        val lines = cart.value
        if (lines.isEmpty()) return
        viewModelScope.launch {
            val totalInt = lines.sumOf { it.subtotal }
            val items = lines.map {
                OrderItem(0, 0, it.menu.id, it.menu.nama, it.menu.harga, it.qty, it.catatan)
            }
            val u = Session.current
            val existingId = editingOrderId
            val id: Long
            if (existingId != null) {
                repo.updateOrderWithItems(Order(
                    id = existingId, timestamp = System.currentTimeMillis(),
                    nomorMeja = nomorMeja, tipeOrder = tipeOrder,
                    namaPelanggan = namaPelanggan, total = totalInt,
                    metodeBayar = metode, dibayar = dibayar,
                    kembalian = (dibayar - totalInt).coerceAtLeast(0),
                    status = OrderStatus.PAID.id,
                    kasirId = u?.id ?: "", kasirNama = u?.nama ?: ""
                ), items)
                id = existingId
            } else {
                id = repo.simpanOrder(Order(
                    timestamp = System.currentTimeMillis(),
                    nomorMeja = nomorMeja, tipeOrder = tipeOrder,
                    namaPelanggan = namaPelanggan, total = totalInt,
                    metodeBayar = metode, dibayar = dibayar,
                    kembalian = (dibayar - totalInt).coerceAtLeast(0),
                    status = OrderStatus.PAID.id,
                    kasirId = u?.id ?: "", kasirNama = u?.nama ?: ""
                ), items)
            }
            resetOrder(); onDone(id)
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

class DashboardViewModel(private val repo: PosRepository) : ViewModel() {
    private val startOfDay: Long = run {
        val c = java.util.Calendar.getInstance()
        c.set(java.util.Calendar.HOUR_OF_DAY, 0)
        c.set(java.util.Calendar.MINUTE, 0)
        c.set(java.util.Calendar.SECOND, 0)
        c.set(java.util.Calendar.MILLISECOND, 0)
        c.timeInMillis
    }
    val transaksiHariIni: StateFlow<Int> = repo.countPaidSince(startOfDay)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val omzetHariIni: StateFlow<Int> = repo.sumPaidSince(startOfDay)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val transaksiTerakhir: StateFlow<List<Order>> = repo.observePaidSince(startOfDay)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

class PosVMFactory(private val repo: PosRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(KasirViewModel::class.java) -> KasirViewModel(repo) as T
        modelClass.isAssignableFrom(MenuViewModel::class.java) -> MenuViewModel(repo) as T
        modelClass.isAssignableFrom(OpenBillViewModel::class.java) -> OpenBillViewModel(repo) as T
        modelClass.isAssignableFrom(RiwayatViewModel::class.java) -> RiwayatViewModel(repo) as T
        modelClass.isAssignableFrom(DashboardViewModel::class.java) -> DashboardViewModel(repo) as T
        else -> error("VM tidak dikenal: ${modelClass.name}")
    }
}

// ═══════════════════════════════════════════════════════════
// ROUTES
// ═══════════════════════════════════════════════════════════
@Composable
fun PosRoute(app: IyonzApp, nav: NavHostController, innerNav: NavHostController) {
    val factory = remember { PosVMFactory(app.posRepo) }
    val vm: KasirViewModel = viewModel(factory = factory)
    PosScreen(vm,
        onOpenKeranjang = { nav.navigate(Routes.KERANJANG) },
        onBayar = { nav.navigate(Routes.BAYAR) })
}

@Composable
fun KeranjangRoute(app: IyonzApp, nav: NavHostController) {
    val factory = remember { PosVMFactory(app.posRepo) }
    val vm: KasirViewModel = viewModel(factory = factory)
    KeranjangScreen(vm,
        onBack = { nav.popBackStack() },
        onBayar = { nav.navigate(Routes.BAYAR) })
}

@Composable
fun BayarRoute(app: IyonzApp, nav: NavHostController) {
    val factory = remember { PosVMFactory(app.posRepo) }
    val vm: KasirViewModel = viewModel(factory = factory)
    BayarScreen(vm,
        onBack = { nav.popBackStack() },
        onSelesai = { nav.popBackStack(Routes.MAIN, inclusive = false) })
}

@Composable
fun OpenBillRoute(app: IyonzApp, nav: NavHostController, innerNav: NavHostController) {
    val factory = remember { PosVMFactory(app.posRepo) }
    val vm: OpenBillViewModel = viewModel(factory = factory)
    val kasirVm: KasirViewModel = viewModel(factory = factory)
    OpenBillScreen(vm) { orderId ->
        kasirVm.loadOpenBill(orderId)
        nav.navigate(Routes.BAYAR)
    }
}

@Composable
fun MenuRoute(app: IyonzApp, nav: NavHostController, innerNav: NavHostController) {
    val factory = remember { PosVMFactory(app.posRepo) }
    val vm: MenuViewModel = viewModel(factory = factory)
    MenuScreen(vm) { id ->
        nav.navigate(if (id == null) Routes.EDIT_MENU else "${Routes.EDIT_MENU}/$id")
    }
}

@Composable
fun EditMenuRoute(app: IyonzApp, nav: NavHostController, menuId: Long?) {
    val factory = remember { PosVMFactory(app.posRepo) }
    val vm: MenuViewModel = viewModel(factory = factory)
    EditMenuScreen(vm, menuId) { nav.popBackStack() }
}

@Composable
fun RiwayatRoute(app: IyonzApp) {
    val factory = remember { PosVMFactory(app.posRepo) }
    val vm: RiwayatViewModel = viewModel(factory = factory)
    RiwayatScreen(vm)
}

@Composable
fun DashboardRoute(app: IyonzApp) {
    val factory = remember { PosVMFactory(app.posRepo) }
    val vm: DashboardViewModel = viewModel(factory = factory)
    DashboardScreen(vm)
}

// ═══════════════════════════════════════════════════════════
// POS SCREEN (adaptive)
// ═══════════════════════════════════════════════════════════
@Composable
fun PosScreen(vm: KasirViewModel, onOpenKeranjang: () -> Unit, onBayar: () -> Unit) {
    BoxWithConstraints {
        val isTablet = maxWidth >= 720.dp
        if (isTablet) {
            Row(Modifier.fillMaxSize()) {
                MenuPane(vm, Modifier.weight(1f), isTablet = true)
                VerticalDivider()
                CartPane(vm, Modifier.width(380.dp), onBayar)
            }
        } else {
            Box(Modifier.fillMaxSize()) {
                MenuPane(vm, Modifier.fillMaxSize(), isTablet = false)
                val cart by vm.cart.collectAsState()
                val total by vm.total.collectAsState()
                if (cart.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                        shadowElevation = 8.dp
                    ) {
                        Row(Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${cart.sumOf { it.qty }} item",
                                    style = MaterialTheme.typography.bodySmall)
                                Text(total.rupiah(), fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleLarge, color = BRAND)
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
                        Text("iyonzkasir", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold, color = BRAND)
                        Text(
                            if (vm.nomorMeja.isBlank()) "Belum pilih meja"
                            else "Meja " + vm.nomorMeja,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OrderMetaBar(vm)
            OutlinedTextField(
                value = vm.searchQuery, onValueChange = { vm.searchQuery = it },
                placeholder = { Text("Cari menu") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            )
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(kategoriList.size) { i ->
                    val k = kategoriList[i]
                    FilterChip(selected = k == vm.kategoriFilter,
                        onClick = { vm.kategoriFilter = k },
                        label = { Text(k ?: "Semua") })
                }
            }
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Menu tidak ditemukan")
                }
            } else {
                val kolom = if (isTablet) 3 else 2
                LazyVerticalGrid(columns = GridCells.Fixed(kolom),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        AssistChip(onClick = { showDialog = true },
            leadingIcon = { Icon(Icons.Default.TableRestaurant, null, Modifier.size(18.dp)) },
            label = { Text(if (vm.nomorMeja.isBlank()) "Pilih Meja" else "Meja ${vm.nomorMeja}") })
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(TipeOrder.values().toList()) { t ->
                FilterChip(selected = vm.tipeOrder == t.id,
                    onClick = { vm.tipeOrder = t.id },
                    label = { Text(t.label, style = MaterialTheme.typography.bodySmall) })
            }
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
                    OutlinedTextField(tempMeja,
                        { tempMeja = it.filter { c -> c.isDigit() } },
                        label = { Text("Nomor meja") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(tempNama, { tempNama = it },
                        label = { Text("Nama pelanggan (opsional)") },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.nomorMeja = tempMeja; vm.namaPelanggan = tempNama
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
            Box(Modifier.fillMaxWidth().height(100.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant)) {
                m.fotoUri?.let { uri ->
                    AsyncImage(model = uri, contentDescription = m.nama,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize())
                }
                if (!enabled) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center) {
                        Text("Habis", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Column(Modifier.padding(8.dp)) {
                Text(m.nama, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(2.dp))
                Text(m.harga.rupiah(), color = BRAND, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// CART PANE
// ═══════════════════════════════════════════════════════════
@Composable
fun CartPane(vm: KasirViewModel, modifier: Modifier = Modifier, onBayar: () -> Unit) {
    val cart by vm.cart.collectAsState()
    val total by vm.total.collectAsState()
    var noteFor by remember { mutableStateOf<CartLine?>(null) }

    Column(modifier.fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant)) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Keranjang", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                Text(
                    buildString {
                        append(if (vm.nomorMeja.isBlank()) "Tanpa meja" else "Meja ${vm.nomorMeja}")
                        append(" • ")
                        append(TipeOrder.fromId(vm.tipeOrder).label)
                        if (vm.namaPelanggan.isNotBlank()) {
                            append(" • "); append(vm.namaPelanggan)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        HorizontalDivider()

        if (cart.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.ShoppingCart, null, Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("Keranjang kosong",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(cart, key = { it.key }) { line ->
                    CartLineItem(line,
                        onAdd = { vm.add(line.menu, line.catatan) },
                        onDecrease = { vm.decrease(line.key) },
                        onRemove = { vm.remove(line.key) },
                        onEditNote = { noteFor = line })
                }
            }
        }

        HorizontalDivider()
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Row {
                    Text("Total", Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium)
                    Text(total.rupiah(), fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge, color = BRAND)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.simpanOpenBill { } },
                        enabled = cart.isNotEmpty(),
                        modifier = Modifier.weight(1f)) { Text("Open Bill") }
                    Button(onClick = onBayar, enabled = cart.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = BRAND)) {
                        Text("Bayar")
                    }
                }
            }
        }
    }

    noteFor?.let { line ->
        NoteDialog(
            initial = line.catatan, menuName = line.menu.nama,
            onDismiss = { noteFor = null },
            onSave = { newNote ->
                val oldQty = line.qty
                vm.remove(line.key)
                repeat(oldQty) { vm.add(line.menu, newNote) }
                noteFor = null
            }
        )
    }
}

@Composable
private fun CartLineItem(
    line: CartLine, onAdd: () -> Unit, onDecrease: () -> Unit,
    onRemove: () -> Unit, onEditNote: () -> Unit
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
                    Text("Catatan: ${line.catatan}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall)
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
    initial: String, menuName: String,
    onDismiss: () -> Unit, onSave: (String) -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Catatan untuk $menuName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(text, { text = it },
                    label = { Text("Catatan") },
                    placeholder = { Text("cth: pedas, tanpa bawang") },
                    maxLines = 3, modifier = Modifier.fillMaxWidth())
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf("Pedas", "Tanpa bawang", "Extra nasi",
                        "Tanpa sambal", "Goreng kering")) { t ->
                        AssistChip(onClick = {
                            text = if (text.isBlank()) t else "$text, $t"
                        }, label = { Text(t, style = MaterialTheme.typography.bodySmall) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text.trim()) }) { Text("Simpan") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

// ═══════════════════════════════════════════════════════════
// KERANJANG (mobile)
// ═══════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeranjangScreen(vm: KasirViewModel, onBack: () -> Unit, onBayar: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Keranjang") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                })
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            CartPane(vm, Modifier.fillMaxSize(), onBayar)
        }
    }
}

// ═══════════════════════════════════════════════════════════
// BAYAR
// ═══════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BayarScreen(vm: KasirViewModel, onBack: () -> Unit, onSelesai: () -> Unit) {
    val total by vm.total.collectAsState()
    var metode by remember { mutableStateOf(PaymentMethod.CASH.id) }
    var dibayarText by remember { mutableStateOf("") }
    val dibayar = dibayarText.toIntOrNull() ?: 0
    val kembalian = (dibayar - total).coerceAtLeast(0)
    val cukup = if (metode == PaymentMethod.CASH.id) dibayar >= total else true

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Pembayaran") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                })
        }
    ) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(colors = CardDefaults.cardColors(containerColor = BRAND_LIGHT)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Total tagihan", style = MaterialTheme.typography.bodyMedium)
                    Text(total.rupiah(), style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold, color = BRAND)
                    if (vm.nomorMeja.isNotBlank() || vm.namaPelanggan.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(buildString {
                            if (vm.nomorMeja.isNotBlank()) append("Meja ${vm.nomorMeja}")
                            if (vm.namaPelanggan.isNotBlank()) {
                                if (isNotEmpty()) append(" • ")
                                append(vm.namaPelanggan)
                            }
                        }, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Text("Metode bayar", fontWeight = FontWeight.SemiBold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(PaymentMethod.values().toList()) { m ->
                    FilterChip(selected = metode == m.id, onClick = { metode = m.id },
                        label = { Text(m.label) })
                }
            }

            if (metode == PaymentMethod.CASH.id) {
                OutlinedTextField(dibayarText,
                    { dibayarText = it.filter { c -> c.isDigit() } },
                    label = { Text("Uang diterima") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                QuickCash(total) { dibayarText = it.toString() }
                Row {
                    Text("Kembalian", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Text(kembalian.rupiah(), fontWeight = FontWeight.Bold, color = BRAND)
                }
            }

            Spacer(Modifier.weight(1f))
            Button(
                onClick = {
                    vm.checkout(metode, if (metode == PaymentMethod.CASH.id) dibayar else total) {
                        onSelesai()
                    }
                },
                enabled = cukup,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BRAND)
            ) { Text("Konfirmasi Bayar", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun QuickCash(total: Int, onPick: (Int) -> Unit) {
    val suggestions = remember(total) {
        val rounded = ((total + 4999) / 5000) * 5000
        listOf(total, rounded, 50000, 100000).distinct().filter { it >= total }
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(suggestions.take(4)) { s ->
            AssistChip(onClick = { onPick(s) }, label = { Text(s.rupiah()) })
        }
    }
}

// ═══════════════════════════════════════════════════════════
// OPEN BILL
// ═══════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenBillScreen(vm: OpenBillViewModel, onPick: (Long) -> Unit) {
    val bills by vm.openBills.collectAsState()
    Scaffold(topBar = { TopAppBar(title = { Text("Open Bill") }) }) { pad ->
        if (bills.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.ReceiptLong, null, Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("Belum ada open bill")
                }
            }
        } else {
            LazyColumn(Modifier.padding(pad).fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(bills, key = { it.id }) { o ->
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                        Row(Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (o.nomorMeja.isNotBlank()) "Meja ${o.nomorMeja}"
                                    else "Tanpa meja",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium)
                                Text(buildString {
                                    append(TipeOrder.fromId(o.tipeOrder).label)
                                    if (o.namaPelanggan.isNotBlank()) {
                                        append(" • "); append(o.namaPelanggan)
                                    }
                                }, style = MaterialTheme.typography.bodySmall)
                                Text(o.timestamp.tanggal(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(o.total.rupiah(), fontWeight = FontWeight.Bold, color = BRAND)
                                Spacer(Modifier.height(6.dp))
                                Button(onClick = { onPick(o.id) },
                                    colors = ButtonDefaults.buttonColors(containerColor = BRAND),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                                    Text("Bayar", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// KELOLA MENU
// ═══════════════════════════════════════════════════════════
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
                text = { Text("Menu baru") },
                containerColor = BRAND
            )
        }
    ) { pad ->
        if (menu.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Belum ada menu")
            }
        } else {
            LazyColumn(Modifier.padding(pad).fillMaxSize(),
                contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 100.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(menu, key = { it.id }) { m ->
                    MenuRow(m, onToggle = { vm.toggle(m) },
                        onEdit = { onEdit(m.id) }, onDelete = { vm.delete(m) })
                }
            }
        }
    }
}

@Composable
private fun MenuRow(
    m: MenuItem, onToggle: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit
) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (m.fotoUri != null) {
                AsyncImage(model = m.fotoUri, contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)))
            } else {
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center) { Text("🍽️") }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(m.nama, fontWeight = FontWeight.SemiBold)
                Text(m.harga.rupiah(), color = BRAND)
                Text(m.kategori, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            try {
                ctx.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            fotoUri = uri.toString()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(if (menuId == null) "Menu Baru" else "Edit Menu") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                })
        }
    ) { pad ->
        if (!loaded) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(Modifier.padding(pad).padding(16.dp).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                fotoUri?.let { uri ->
                    AsyncImage(model = uri, contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(180.dp)
                            .clip(RoundedCornerShape(12.dp)))
                }
                OutlinedButton(onClick = { picker.launch(arrayOf("image/*")) },
                    modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Photo, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (fotoUri == null) "Pilih Foto dari Galeri" else "Ganti Foto")
                }
                OutlinedTextField(nama, { nama = it },
                    label = { Text("Nama menu") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(hargaText,
                    { hargaText = it.filter { c -> c.isDigit() } },
                    label = { Text("Harga") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(kategori, { kategori = it },
                    label = { Text("Kategori") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Tersedia", Modifier.weight(1f))
                    Switch(checked = tersedia, onCheckedChange = { tersedia = it })
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        val harga = hargaText.toIntOrNull() ?: 0
                        if (nama.isBlank() || harga <= 0) return@Button
                        vm.save(MenuItem(
                            id = menuId ?: 0,
                            nama = nama.trim(), harga = harga,
                            kategori = kategori.trim().ifBlank { "Umum" },
                            fotoUri = fotoUri, tersedia = tersedia
                        )) { onBack() }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BRAND)
                ) { Text("Simpan") }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// RIWAYAT
// ═══════════════════════════════════════════════════════════
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
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(orders, key = { it.id }) { o ->
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                        Row(Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(buildString {
                                    if (o.nomorMeja.isNotBlank()) append("Meja ${o.nomorMeja} • ")
                                    append(PaymentMethod.fromId(o.metodeBayar).label)
                                }, fontWeight = FontWeight.SemiBold)
                                Text(buildString {
                                    append(o.timestamp.tanggal())
                                    if (o.kasirNama.isNotBlank()) {
                                        append(" • "); append(o.kasirNama)
                                    }
                                }, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
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
// DASHBOARD
// ═══════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(vm: DashboardViewModel) {
    val trx by vm.transaksiHariIni.collectAsState()
    val omzet by vm.omzetHariIni.collectAsState()
    val recent by vm.transaksiTerakhir.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Dashboard") }) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Hari Ini", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Transaksi", trx.toString(), Icons.Default.Receipt, Modifier.weight(1f))
                StatCard("Omzet", omzet.rupiah(), Icons.Default.AttachMoney, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Text("Transaksi Terakhir", fontWeight = FontWeight.SemiBold)
            if (recent.isEmpty()) {
                Text("Belum ada transaksi hari ini",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(recent.take(20), key = { it.id }) { o ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(o.jamPendek() + " • " + PaymentMethod.fromId(o.metodeBayar).label,
                                        fontWeight = FontWeight.SemiBold)
                                    Text(
                                        if (o.nomorMeja.isNotBlank()) "Meja ${o.nomorMeja}"
                                        else "Tanpa meja",
                                        style = MaterialTheme.typography.bodySmall)
                                }
                                Text(o.total.rupiah(), fontWeight = FontWeight.Bold, color = BRAND)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    label: String, value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = BRAND_LIGHT)) {
        Column(Modifier.padding(16.dp)) {
            Icon(icon, null, tint = BRAND)
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold, color = BRAND)
        }
    }
}
