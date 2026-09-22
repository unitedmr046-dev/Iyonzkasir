package com.iyonzkasir.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.iyonzkasir.*
import com.iyonzkasir.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// ═══════════════════════════════════════════════════════════
// STOCK VIEWMODEL
// ═══════════════════════════════════════════════════════════
class StockViewModel(private val repo: StockRepository) : ViewModel() {
    val menus: StateFlow<List<MenuItem>> = repo.trackStokMenus
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val movements: StateFlow<List<StockMovement>> = repo.movements
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val lowStockCount: StateFlow<Int> = repo.lowStockCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    var searchQuery by mutableStateOf("")
    var showOnlyLow by mutableStateOf(false)

    fun filtered(): List<MenuItem> {
        val q = searchQuery.trim()
        val list = menus.value.filter { m ->
            q.isBlank() || m.nama.contains(q, ignoreCase = true)
        }
        return if (showOnlyLow) list.filter { it.stok <= it.stokMinimal }
        else list
    }

    fun tambahStok(menuId: Long, jumlah: Int, ket: String, onDone: () -> Unit = {}) =
        viewModelScope.launch {
            repo.tambahStok(menuId, jumlah, ket)
            onDone()
        }

    fun adjustStok(menuId: Long, newStok: Int, ket: String, onDone: () -> Unit = {}) =
        viewModelScope.launch {
            repo.adjustStock(menuId, newStok, ket, StockMovementType.OPNAME)
            onDone()
        }

    fun observeMovementsForMenu(menuId: Long) = repo.observeMovementsForMenu(menuId)
}

class StockVMFactory(private val repo: StockRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = StockViewModel(repo) as T
}

// ═══════════════════════════════════════════════════════════
// KATEGORI VIEWMODEL
// ═══════════════════════════════════════════════════════════
class KategoriViewModel(
    private val kategoriRepo: KategoriRepository,
    private val posRepo: PosRepository
) : ViewModel() {
    val kategori: StateFlow<List<Kategori>> = kategoriRepo.all
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val menu: StateFlow<List<MenuItem>> = posRepo.menu
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun save(k: Kategori, onDone: () -> Unit = {}) = viewModelScope.launch {
        kategoriRepo.save(k)
        onDone()
    }

    fun delete(k: Kategori, onDone: () -> Unit = {}) = viewModelScope.launch {
        kategoriRepo.delete(k)
        onDone()
    }

    fun moveUp(k: Kategori) = viewModelScope.launch {
        val list = kategori.value.sortedBy { it.urutan }
        val idx = list.indexOfFirst { it.id == k.id }
        if (idx <= 0) return@launch
        val prev = list[idx - 1]
        kategoriRepo.update(k.copy(urutan = prev.urutan))
        kategoriRepo.update(prev.copy(urutan = k.urutan))
    }

    fun moveDown(k: Kategori) = viewModelScope.launch {
        val list = kategori.value.sortedBy { it.urutan }
        val idx = list.indexOfFirst { it.id == k.id }
        if (idx < 0 || idx >= list.size - 1) return@launch
        val next = list[idx + 1]
        kategoriRepo.update(k.copy(urutan = next.urutan))
        kategoriRepo.update(next.copy(urutan = k.urutan))
    }

    fun countMenuInKategori(nama: String): Int =
        menu.value.count { it.kategori == nama }
}

class KategoriVMFactory(
    private val kategoriRepo: KategoriRepository,
    private val posRepo: PosRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        KategoriViewModel(kategoriRepo, posRepo) as T
}

// ═══════════════════════════════════════════════════════════
// ROUTES
// ═══════════════════════════════════════════════════════════
@Composable
fun InventarisRoute(app: IyonzApp, nav: NavHostController) {
    val factory = remember { StockVMFactory(app.stockRepo) }
    val vm: StockViewModel = viewModel(factory = factory)
    StockScreen(vm)
}

@Composable
fun KategoriRoute(app: IyonzApp, nav: NavHostController) {
    val factory = remember { KategoriVMFactory(app.kategoriRepo, app.posRepo) }
    val vm: KategoriViewModel = viewModel(factory = factory)
    KategoriScreen(vm)
}

// ═══════════════════════════════════════════════════════════
// STOCK SCREEN
// ═══════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockScreen(vm: StockViewModel) {
    val lowCount by vm.lowStockCount.collectAsState()
    val filtered = remember(vm.searchQuery, vm.showOnlyLow, vm.menus.value) {
        vm.filtered()
    }

    var selectedMenu by remember { mutableStateOf<MenuItem?>(null) }
    var showTambah by remember { mutableStateOf(false) }
    var showAdjust by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Inventaris / Stok") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BRAND, titleContentColor = Color.White
                )
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {

            // Ringkasan
            Card(
                Modifier.fillMaxWidth().padding(12.dp),
                colors = CardDefaults.cardColors(containerColor = BRAND_LIGHT)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Inventory, null, tint = BRAND,
                        modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${vm.menus.value.size} menu di-track",
                            fontWeight = FontWeight.Bold)
                        Text(
                            if (lowCount > 0) "$lowCount menu stok menipis"
                            else "Semua stok aman",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (lowCount > 0) DANGER
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (lowCount > 0) {
                        Badge(containerColor = DANGER) {
                            Text("$lowCount", color = Color.White)
                        }
                    }
                }
            }

            // Search + filter
            OutlinedTextField(
                value = vm.searchQuery, onValueChange = { vm.searchQuery = it },
                placeholder = { Text("Cari menu...",
                    style = MaterialTheme.typography.bodySmall) },
                leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(18.dp)) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .height(52.dp)
            )

            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = vm.showOnlyLow,
                    onClick = { vm.showOnlyLow = !vm.showOnlyLow },
                    label = { Text("Hanya stok menipis",
                        style = MaterialTheme.typography.bodySmall) },
                    leadingIcon = {
                        Icon(Icons.Default.Warning, null, Modifier.size(16.dp))
                    }
                )
            }

            // List
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Inventory2, null, Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (vm.menus.value.isEmpty())
                                "Belum ada menu yang di-track stoknya.\nAktifkan di Edit Menu → Track Stok"
                            else if (vm.showOnlyLow) "Nggak ada menu stok menipis 👍"
                            else "Nggak ada yang cocok",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = { it.id }) { m ->
                        StockRow(
                            menu = m,
                            onTambah = { selectedMenu = m; showTambah = true },
                            onAdjust = { selectedMenu = m; showAdjust = true },
                            onHistory = { selectedMenu = m; showHistory = true }
                        )
                    }
                }
            }
        }
    }

    // Dialog Tambah Stok
    if (showTambah && selectedMenu != null) {
        TambahStokDialog(
            menu = selectedMenu!!,
            onDismiss = { showTambah = false; selectedMenu = null },
            onConfirm = { jumlah, ket ->
                vm.tambahStok(selectedMenu!!.id, jumlah, ket) {
                    showTambah = false; selectedMenu = null
                }
            }
        )
    }

    // Dialog Adjust / Opname
    if (showAdjust && selectedMenu != null) {
        AdjustStokDialog(
            menu = selectedMenu!!,
            onDismiss = { showAdjust = false; selectedMenu = null },
            onConfirm = { newStok, ket ->
                vm.adjustStok(selectedMenu!!.id, newStok, ket) {
                    showAdjust = false; selectedMenu = null
                }
            }
        )
    }

    // Dialog History
    if (showHistory && selectedMenu != null) {
        StockHistoryDialog(
            menu = selectedMenu!!,
            vm = vm,
            onDismiss = { showHistory = false; selectedMenu = null }
        )
    }
}

@Composable
private fun StockRow(
    menu: MenuItem,
    onTambah: () -> Unit,
    onAdjust: () -> Unit,
    onHistory: () -> Unit
) {
    val isLow = menu.stok <= menu.stokMinimal
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                        .background(if (isLow) DANGER.copy(alpha = 0.15f)
                        else BRAND_LIGHT),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${menu.stok}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isLow) DANGER else BRAND
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(menu.nama, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${menu.kategori} • ${menu.harga.rupiah()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isLow) {
                        Text("⚠️ Stok menipis (min ${menu.stokMinimal})",
                            style = MaterialTheme.typography.labelSmall,
                            color = DANGER, fontWeight = FontWeight.Bold)
                    }
                }
                IconButton(onClick = onHistory) {
                    Icon(Icons.Default.History, null, tint = BRAND)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onTambah,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = BRAND),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Tambah Stok", style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(
                    onClick = onAdjust,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Edit, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Koreksi", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// DIALOG TAMBAH STOK
// ═══════════════════════════════════════════════════════════
@Composable
private fun TambahStokDialog(
    menu: MenuItem,
    onDismiss: () -> Unit,
    onConfirm: (Int, String) -> Unit
) {
    var jumlahText by remember { mutableStateOf("") }
    var ket by remember { mutableStateOf("") }
    val jumlah = jumlahText.toIntOrNull() ?: 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tambah Stok: ${menu.nama}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Card(colors = CardDefaults.cardColors(containerColor = BRAND_LIGHT)) {
                    Row(Modifier.padding(12.dp)) {
                        Text("Stok sekarang:", Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall)
                        Text("${menu.stok}", fontWeight = FontWeight.Bold, color = BRAND)
                    }
                }
                OutlinedTextField(
                    value = jumlahText,
                    onValueChange = { jumlahText = it.filter { c -> c.isDigit() }.take(6) },
                    label = { Text("Jumlah tambahan") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = ket, onValueChange = { ket = it },
                    label = { Text("Keterangan (opsional)") },
                    placeholder = { Text("cth: restock dari supplier") },
                    maxLines = 2, modifier = Modifier.fillMaxWidth()
                )
                if (jumlah > 0) {
                    Text("Stok setelah: ${menu.stok + jumlah}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold, color = SUCCESS)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(jumlah, ket.trim()) },
                enabled = jumlah > 0
            ) { Text("Tambah") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

// ═══════════════════════════════════════════════════════════
// DIALOG ADJUST / OPNAME
// ═══════════════════════════════════════════════════════════
@Composable
private fun AdjustStokDialog(
    menu: MenuItem,
    onDismiss: () -> Unit,
    onConfirm: (Int, String) -> Unit
) {
    var stokText by remember { mutableStateOf(menu.stok.toString()) }
    var ket by remember { mutableStateOf("") }
    val newStok = stokText.toIntOrNull() ?: 0
    val selisih = newStok - menu.stok

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Koreksi Stok: ${menu.nama}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Stok fisik sebenarnya berapa? Masukkan angka baru.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Card(colors = CardDefaults.cardColors(containerColor = BRAND_LIGHT)) {
                    Row(Modifier.padding(12.dp)) {
                        Text("Stok sistem:", Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall)
                        Text("${menu.stok}", fontWeight = FontWeight.Bold, color = BRAND)
                    }
                }
                OutlinedTextField(
                    value = stokText,
                    onValueChange = { stokText = it.filter { c -> c.isDigit() }.take(6) },
                    label = { Text("Stok baru (fisik)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = ket, onValueChange = { ket = it },
                    label = { Text("Alasan koreksi") },
                    placeholder = { Text("cth: stock opname, rusak, hilang") },
                    maxLines = 2, modifier = Modifier.fillMaxWidth()
                )
                if (selisih != 0) {
                    val warna = if (selisih > 0) SUCCESS else DANGER
                    Text(
                        "Selisih: ${if (selisih > 0) "+" else ""}$selisih",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold, color = warna
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(newStok, ket.trim().ifBlank { "Koreksi manual" }) },
                enabled = newStok >= 0 && stokText.isNotBlank()
            ) { Text("Simpan") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

// ═══════════════════════════════════════════════════════════
// DIALOG HISTORY
// ═══════════════════════════════════════════════════════════
@Composable
private fun StockHistoryDialog(
    menu: MenuItem,
    vm: StockViewModel,
    onDismiss: () -> Unit
) {
    val movements by vm.observeMovementsForMenu(menu.id)
        .collectAsState(initial = emptyList())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Riwayat Stok: ${menu.nama}") },
        text = {
            Column(Modifier.heightIn(max = 500.dp)) {
                Card(colors = CardDefaults.cardColors(containerColor = BRAND_LIGHT)) {
                    Row(Modifier.padding(12.dp)) {
                        Text("Stok sekarang:", Modifier.weight(1f))
                        Text("${menu.stok}", fontWeight = FontWeight.Bold, color = BRAND)
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (movements.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center) {
                        Text("Belum ada pergerakan stok",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(movements, key = { it.id }) { mv ->
                            HistoryItem(mv)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Tutup") }
        }
    )
}

@Composable
private fun HistoryItem(mv: StockMovement) {
    val tipeLabel = StockMovementType.fromId(mv.tipe).label
    val warna = when (mv.tipe) {
        StockMovementType.SALE.id -> DANGER
        StockMovementType.IN.id -> SUCCESS
        StockMovementType.VOID_RETURN.id -> SUCCESS
        StockMovementType.OPNAME.id -> WARNING
        StockMovementType.OUT.id -> DANGER
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(warna.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    when (mv.tipe) {
                        StockMovementType.SALE.id -> Icons.Default.ShoppingCart
                        StockMovementType.IN.id -> Icons.Default.ArrowDownward
                        StockMovementType.VOID_RETURN.id -> Icons.Default.Replay
                        StockMovementType.OPNAME.id -> Icons.Default.FactCheck
                        StockMovementType.OUT.id -> Icons.Default.ArrowUpward
                        else -> Icons.Default.SwapVert
                    }, null, tint = warna, modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row {
                    Text(tipeLabel, fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.weight(1f))
                    Text(
                        (if (mv.qty > 0) "+" else "") + "${mv.qty}",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall,
                        color = warna
                    )
                }
                Text("${mv.stokSebelum} → ${mv.stokSesudah}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    mv.timestamp.tanggal() +
                        if (mv.userName.isNotBlank()) " • ${mv.userName}" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (mv.keterangan.isNotBlank()) {
                    Text(mv.keterangan, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// KATEGORI SCREEN
// ═══════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KategoriScreen(vm: KategoriViewModel) {
    val kategori by vm.kategori.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Kategori?>(null) }
    var confirmDelete by remember { mutableStateOf<Kategori?>(null) }

    val canEdit = Session.can(PermissionKey.KELOLA_KATEGORI)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kelola Kategori") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BRAND, titleContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            if (canEdit) {
                ExtendedFloatingActionButton(
                    onClick = { showAdd = true },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("Kategori Baru") },
                    containerColor = BRAND
                )
            }
        }
    ) { pad ->
        if (kategori.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Category, null, Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("Belum ada kategori",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text("Tap tombol + untuk tambah",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                Modifier.padding(pad).fillMaxSize(),
                contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 100.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(kategori, key = { it.id }) { k ->
                    KategoriRow(
                        k = k,
                        jumlahMenu = vm.countMenuInKategori(k.nama),
                        canEdit = canEdit,
                        onEdit = { editing = k },
                        onDelete = { confirmDelete = k },
                        onUp = { vm.moveUp(k) },
                        onDown = { vm.moveDown(k) }
                    )
                }
            }
        }
    }

    if (showAdd) {
        KategoriEditorDialog(
            initial = null,
            onDismiss = { showAdd = false },
            onSave = { k -> vm.save(k) { showAdd = false } }
        )
    }
    editing?.let { k ->
        KategoriEditorDialog(
            initial = k,
            onDismiss = { editing = null },
            onSave = { updated -> vm.save(updated) { editing = null } }
        )
    }

    confirmDelete?.let { k ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Hapus Kategori?") },
            text = { Text("Hapus '${k.nama}'? Menu dengan kategori ini akan tetap ada tapi tanpa kategori.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(k) { confirmDelete = null }
                }) { Text("Hapus", color = DANGER) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Batal") }
            }
        )
    }
}

@Composable
private fun KategoriRow(
    k: Kategori,
    jumlahMenu: Int,
    canEdit: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit
) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // Reorder buttons
            if (canEdit) {
                Column {
                    IconButton(onClick = onUp, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.KeyboardArrowUp, null,
                            Modifier.size(20.dp), tint = BRAND)
                    }
                    IconButton(onClick = onDown, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.KeyboardArrowDown, null,
                            Modifier.size(20.dp), tint = BRAND)
                    }
                }
                Spacer(Modifier.width(4.dp))
            }

            // Color chip
            Box(
                Modifier.size(40.dp).clip(CircleShape)
                    .background(k.warnaHex.toColorSafe(BRAND))
            )

            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(k.nama, fontWeight = FontWeight.SemiBold)
                Text(
                    "$jumlahMenu menu" + if (!k.aktif) " • nonaktif" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (canEdit) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, null, tint = BRAND)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, null, tint = DANGER)
                }
            }
        }
    }
}

@Composable
private fun KategoriEditorDialog(
    initial: Kategori?,
    onDismiss: () -> Unit,
    onSave: (Kategori) -> Unit
) {
    var nama by remember { mutableStateOf(initial?.nama ?: "") }
    var warna by remember { mutableStateOf(initial?.warnaHex ?: KATEGORI_WARNA_PRESET.first()) }
    var aktif by remember { mutableStateOf(initial?.aktif ?: true) }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Kategori Baru" else "Edit Kategori") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.heightIn(max = 500.dp)) {
                OutlinedTextField(
                    value = nama, onValueChange = { nama = it; err = null },
                    label = { Text("Nama kategori *") },
                    placeholder = { Text("cth: Makanan, Minuman, Snack") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Warna kategori", fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.bodySmall)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    modifier = Modifier.height(140.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(KATEGORI_WARNA_PRESET) { hex ->
                        val selected = warna == hex
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(hex.toColorSafe(BRAND))
                                .border(
                                    width = if (selected) 3.dp else 0.dp,
                                    color = if (selected) MaterialTheme.colorScheme.onSurface
                                    else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { warna = hex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Icon(Icons.Default.Check, null, tint = Color.White,
                                    modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Aktif", Modifier.weight(1f))
                    Switch(checked = aktif, onCheckedChange = { aktif = it })
                }
                err?.let { Text(it, color = DANGER,
                    style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (nama.isBlank()) { err = "Nama wajib diisi"; return@TextButton }
                onSave(
                    (initial ?: Kategori()).copy(
                        nama = nama.trim(),
                        warnaHex = warna,
                        aktif = aktif
                    )
                )
            }) { Text("Simpan") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}
