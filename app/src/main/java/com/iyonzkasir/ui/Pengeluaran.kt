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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
import java.io.File
import java.util.Calendar

// ═══════════════════════════════════════════════════════════
// VIEWMODEL
// ═══════════════════════════════════════════════════════════
class PengeluaranViewModel(private val repo: ExpenseRepository) : ViewModel() {

    var periode by mutableStateOf(Periode.HARI_INI)
        private set
    var searchQuery by mutableStateOf("")
    var filterKategoriId by mutableStateOf<Long?>(null)

    val categories: StateFlow<List<ExpenseCategory>> = repo.categories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allExpenses: StateFlow<List<Expense>> = repo.expenses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _expenses = MutableStateFlow<List<Expense>>(emptyList())
    val expenses: StateFlow<List<Expense>> = _expenses.asStateFlow()

    private val _total = MutableStateFlow(0)
    val total: StateFlow<Int> = _total.asStateFlow()

    init { load() }

    fun rangeFor(p: Periode): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val todayStart = cal.timeInMillis
        val todayEnd = todayStart + 24L * 60 * 60 * 1000 - 1
        return when (p) {
            Periode.HARI_INI -> todayStart to todayEnd
            Periode.MINGGU -> (todayStart - 6L * 24 * 60 * 60 * 1000) to todayEnd
            Periode.BULAN -> (todayStart - 29L * 24 * 60 * 60 * 1000) to todayEnd
            Periode.SEMUA -> 0L to Long.MAX_VALUE
        }
    }

    fun gantiPeriode(p: Periode) {
        periode = p
        load()
    }

    fun load() {
        viewModelScope.launch {
            val (start, end) = rangeFor(periode)
            val raw = if (periode == Periode.SEMUA) allExpenses.value
            else allExpenses.value.filter { it.tanggal in start..end }

            val filtered = raw.filter { e ->
                val matchQ = searchQuery.isBlank() ||
                        e.keterangan.contains(searchQuery, true) ||
                        e.kategoriNama.contains(searchQuery, true)
                val matchK = filterKategoriId == null || e.kategoriId == filterKategoriId
                matchQ && matchK
            }
            _expenses.value = filtered.sortedByDescending { it.tanggal }
            _total.value = filtered.sumOf { it.jumlah }
        }
    }

    fun save(e: Expense, onDone: () -> Unit = {}) = viewModelScope.launch {
        repo.saveExpense(e)
        load()
        onDone()
    }

    fun delete(e: Expense) = viewModelScope.launch {
        repo.deleteExpense(e)
        load()
    }

    fun saveCategory(c: ExpenseCategory, onDone: () -> Unit = {}) = viewModelScope.launch {
        repo.saveCategory(c)
        onDone()
    }

    fun deleteCategory(c: ExpenseCategory) = viewModelScope.launch {
        repo.deleteCategory(c)
    }
}

class PengeluaranVMFactory(private val repo: ExpenseRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        PengeluaranViewModel(repo) as T
}

// ═══════════════════════════════════════════════════════════
// ROUTE
// ═══════════════════════════════════════════════════════════
@Composable
fun PengeluaranRoute(app: IyonzApp, nav: NavHostController) {
    val factory = remember { PengeluaranVMFactory(app.expenseRepo) }
    val vm: PengeluaranViewModel = viewModel(factory = factory)
    PengeluaranScreen(vm, nav)
}

// ═══════════════════════════════════════════════════════════
// SCREEN
// ═══════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PengeluaranScreen(vm: PengeluaranViewModel, nav: NavHostController) {
    val expenses by vm.expenses.collectAsState()
    val total by vm.total.collectAsState()
    val categories by vm.categories.collectAsState()

    var showForm by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Expense?>(null) }
    var showKategori by remember { mutableStateOf(false) }
    var showLaporan by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<Expense?>(null) }

    val canEdit = Session.can(PermissionKey.KELOLA_PENGELUARAN)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pengeluaran") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                },
                actions = {
                    IconButton(onClick = { showLaporan = true }) {
                        Icon(Icons.Default.Analytics, null)
                    }
                    IconButton(onClick = { showKategori = true }) {
                        Icon(Icons.Default.Category, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BRAND, titleContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            if (canEdit) {
                ExtendedFloatingActionButton(
                    onClick = { editing = null; showForm = true },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("Catat") },
                    containerColor = BRAND
                )
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {

            // Total card
            Card(
                Modifier.fillMaxWidth().padding(12.dp),
                colors = CardDefaults.cardColors(containerColor = BRAND_LIGHT)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Payments, null, tint = BRAND,
                            modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Total Pengeluaran",
                                style = MaterialTheme.typography.bodySmall)
                            Text(total.rupiah(),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold, color = BRAND)
                        }
                        Text("${expenses.size} catatan",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Periode chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(Periode.values().toList()) { p ->
                    FilterChip(
                        selected = vm.periode == p,
                        onClick = { vm.gantiPeriode(p) },
                        label = { Text(p.label,
                            style = MaterialTheme.typography.bodySmall) }
                    )
                }
            }

            // Search
            OutlinedTextField(
                value = vm.searchQuery,
                onValueChange = { vm.searchQuery = it; vm.load() },
                placeholder = { Text("Cari keterangan...",
                    style = MaterialTheme.typography.bodySmall) },
                leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(18.dp)) },
                trailingIcon = {
                    if (vm.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { vm.searchQuery = ""; vm.load() },
                            modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, null, Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .height(52.dp)
            )

            // Kategori filter chips
            if (categories.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item {
                        FilterChip(
                            selected = vm.filterKategoriId == null,
                            onClick = { vm.filterKategoriId = null; vm.load() },
                            label = { Text("Semua Kategori",
                                style = MaterialTheme.typography.bodySmall) }
                        )
                    }
                    items(categories, key = { it.id }) { c ->
                        FilterChip(
                            selected = vm.filterKategoriId == c.id,
                            onClick = {
                                vm.filterKategoriId = if (vm.filterKategoriId == c.id) null else c.id
                                vm.load()
                            },
                            label = { Text("${c.iconName} ${c.nama}",
                                style = MaterialTheme.typography.bodySmall) }
                        )
                    }
                }
            }

            // List
            if (expenses.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ReceiptLong, null, Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text("Belum ada pengeluaran",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Tap tombol + untuk catat",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp, 4.dp, 12.dp, 100.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(expenses, key = { it.id }) { e ->
                        ExpenseRow(e,
                            canEdit = canEdit,
                            onEdit = { editing = e; showForm = true },
                            onDelete = { confirmDelete = e }
                        )
                    }
                }
            }
        }
    }

    // Dialog form
    if (showForm) {
        ExpenseEditorDialog(
            initial = editing,
            categories = categories,
            onDismiss = { showForm = false; editing = null },
            onSave = { e -> vm.save(e) { showForm = false; editing = null } }
        )
    }

    // Dialog kategori
    if (showKategori) {
        KategoriPengeluaranDialog(
            vm = vm,
            onDismiss = { showKategori = false }
        )
    }

    // Dialog laporan
    if (showLaporan) {
        LaporanPengeluaranDialog(
            vm = vm,
            categories = categories,
            onDismiss = { showLaporan = false }
        )
    }

    // Confirm delete
    confirmDelete?.let { e ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Hapus Pengeluaran?") },
            text = { Text("Hapus catatan ${e.kategoriNama} ${e.jumlah.rupiah()}?") },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(e)
                    confirmDelete = null
                }) { Text("Hapus", color = DANGER) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Batal") }
            }
        )
    }
}

@Composable
private fun ExpenseRow(
    e: Expense,
    canEdit: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape)
                    .background(BRAND_LIGHT),
                contentAlignment = Alignment.Center
            ) {
                Text(e.kategoriNama.take(1).uppercase(),
                    fontWeight = FontWeight.Bold, color = BRAND)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(e.kategoriNama, fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium)
                if (e.keterangan.isNotBlank()) {
                    Text(e.keterangan, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2)
                }
                Text(e.tanggal.tanggal() +
                    if (e.userName.isNotBlank()) " • ${e.userName}" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("- ${e.jumlah.rupiah()}",
                    fontWeight = FontWeight.Bold, color = DANGER,
                    style = MaterialTheme.typography.bodyMedium)
                if (canEdit) {
                    Row {
                        IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Edit, null, Modifier.size(16.dp), tint = BRAND)
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Delete, null, Modifier.size(16.dp), tint = DANGER)
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// EDITOR DIALOG
// ═══════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseEditorDialog(
    initial: Expense?,
    categories: List<ExpenseCategory>,
    onDismiss: () -> Unit,
    onSave: (Expense) -> Unit
) {
    val ctx = LocalContext.current
    var jumlahText by remember { mutableStateOf(
        if ((initial?.jumlah ?: 0) > 0) initial!!.jumlah.toString() else ""
    ) }
    var keterangan by remember { mutableStateOf(initial?.keterangan ?: "") }
    var kategoriId by remember { mutableStateOf(initial?.kategoriId ?: (categories.firstOrNull()?.id ?: 0L)) }
    var kategoriNama by remember { mutableStateOf(initial?.kategoriNama ?: (categories.firstOrNull()?.nama ?: "")) }
    var buktiFoto by remember { mutableStateOf(initial?.buktiFoto) }
    var showKategoriDropdown by remember { mutableStateOf(false) }

    val jumlah = jumlahText.toIntOrNull() ?: 0
    val formValid = jumlah > 0 && kategoriId > 0

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            buktiFoto = uri.toString()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Catat Pengeluaran" else "Edit Pengeluaran") },
        text = {
            Column(
                Modifier.heightIn(max = 550.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Kategori dropdown
                Box {
                    OutlinedTextField(
                        value = kategoriNama.ifBlank { "Pilih kategori" },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Kategori *") },
                        trailingIcon = {
                            IconButton(onClick = { showKategoriDropdown = true }) {
                                Icon(Icons.Default.ArrowDropDown, null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(Modifier.matchParentSize().clickable { showKategoriDropdown = true })
                }
                DropdownMenu(
                    expanded = showKategoriDropdown,
                    onDismissRequest = { showKategoriDropdown = false }
                ) {
                    categories.forEach { c ->
                        DropdownMenuItem(
                            text = { Text("${c.iconName} ${c.nama}") },
                            onClick = {
                                kategoriId = c.id
                                kategoriNama = c.nama
                                showKategoriDropdown = false
                            }
                        )
                    }
                }

                // Jumlah
                OutlinedTextField(
                    value = jumlahText,
                    onValueChange = { jumlahText = it.filter { c -> c.isDigit() }.take(10) },
                    label = { Text("Jumlah (Rp) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick amount
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf(10000, 25000, 50000, 100000, 200000, 500000)) { v ->
                        AssistChip(
                            onClick = { jumlahText = v.toString() },
                            label = { Text(v.rupiah(),
                                style = MaterialTheme.typography.bodySmall) }
                        )
                    }
                }

                // Keterangan
                OutlinedTextField(
                    value = keterangan,
                    onValueChange = { keterangan = it },
                    label = { Text("Keterangan (opsional)") },
                    placeholder = { Text("cth: beli gas, bayar listrik") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                // Bukti foto (opsional)
                buktiFoto?.let {
                    Box(
                        Modifier.fillMaxWidth().height(120.dp)
                            .clip(RoundedCornerShape(8.dp))
                    ) {
                        AsyncImage(
                            model = File(it),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                OutlinedButton(
                    onClick = { picker.launch(arrayOf("image/*")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Photo, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (buktiFoto == null) "Tambah Bukti Foto" else "Ganti Foto",
                        style = MaterialTheme.typography.bodySmall)
                }

                if (!formValid) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            "Isi kategori & jumlah dulu ya",
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (!formValid) return@TextButton
                    val e = (initial ?: Expense()).copy(
                        tanggal = initial?.tanggal ?: System.currentTimeMillis(),
                        kategoriId = kategoriId,
                        kategoriNama = kategoriNama,
                        jumlah = jumlah,
                        keterangan = keterangan.trim(),
                        buktiFoto = buktiFoto,
                        userId = initial?.userId ?: (Session.current?.id ?: ""),
                        userName = initial?.userName ?: (Session.current?.nama ?: "")
                    )
                    onSave(e)
                },
                enabled = formValid
            ) { Text("Simpan") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

// ═══════════════════════════════════════════════════════════
// KELOLA KATEGORI PENGELUARAN
// ═══════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KategoriPengeluaranDialog(
    vm: PengeluaranViewModel,
    onDismiss: () -> Unit
) {
    val categories by vm.categories.collectAsState()
    var editing by remember { mutableStateOf<ExpenseCategory?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<ExpenseCategory?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kategori Pengeluaran") },
        text = {
            Column(Modifier.heightIn(max = 500.dp)) {
                OutlinedButton(
                    onClick = { showAdd = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Tambah Kategori")
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(categories, key = { it.id }) { c ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(36.dp).clip(CircleShape)
                                        .background(c.warnaHex.toColorSafe(BRAND)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(c.iconName,
                                        style = MaterialTheme.typography.titleMedium)
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(c.nama, fontWeight = FontWeight.SemiBold)
                                    if (c.isDefault) {
                                        Text("Bawaan",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                IconButton(onClick = { editing = c },
                                    modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Edit, null,
                                        Modifier.size(16.dp), tint = BRAND)
                                }
                                if (!c.isDefault) {
                                    IconButton(onClick = { confirmDelete = c },
                                        modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Default.Delete, null,
                                            Modifier.size(16.dp), tint = DANGER)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Tutup") }
        }
    )

    if (showAdd || editing != null) {
        KategoriEditorDialog(
            initial = editing,
            onDismiss = { showAdd = false; editing = null },
            onSave = { c -> vm.saveCategory(c) { showAdd = false; editing = null } }
        )
    }

    confirmDelete?.let { c ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Hapus Kategori?") },
            text = { Text("Hapus kategori '${c.nama}'? Pengeluaran lama tetap aman.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteCategory(c)
                    confirmDelete = null
                }) { Text("Hapus", color = DANGER) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Batal") }
            }
        )
    }
}

@Composable
private fun KategoriEditorDialog(
    initial: ExpenseCategory?,
    onDismiss: () -> Unit,
    onSave: (ExpenseCategory) -> Unit
) {
    var nama by remember { mutableStateOf(initial?.nama ?: "") }
    var iconName by remember { mutableStateOf(initial?.iconName ?: "📝") }
    var warnaHex by remember { mutableStateOf(initial?.warnaHex ?: "#FF6B35") }
    var err by remember { mutableStateOf<String?>(null) }

    val iconPreset = listOf(
        "📦", "👥", "🏠", "💡", "🚗", "🔧",
        "🍔", "📱", "📝", "💰", "🛠️", "📊",
        "🎁", "🏥", "🎓", "☕", "🍽️", "🧾"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Kategori Baru" else "Edit Kategori") },
        text = {
            Column(
                Modifier.heightIn(max = 500.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = nama, onValueChange = { nama = it; err = null },
                    label = { Text("Nama kategori *") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )

                Text("Icon", fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.bodySmall)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    modifier = Modifier.height(140.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(iconPreset) { ic ->
                        val selected = iconName == ic
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) BRAND_LIGHT
                                else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { iconName = ic },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(ic, style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }

                Text("Warna", fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.bodySmall)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    modifier = Modifier.height(90.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(KATEGORI_WARNA_PRESET) { hex ->
                        val selected = warnaHex == hex
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(hex.toColorSafe())
                                .clickable { warnaHex = hex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Icon(Icons.Default.Check, null, tint = Color.White,
                                    modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }

                err?.let {
                    Text(it, color = DANGER, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (nama.isBlank()) { err = "Nama wajib diisi"; return@TextButton }
                onSave(
                    (initial ?: ExpenseCategory()).copy(
                        nama = nama.trim(),
                        iconName = iconName,
                        warnaHex = warnaHex
                    )
                )
            }) { Text("Simpan") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

// ═══════════════════════════════════════════════════════════
// LAPORAN PENGELUARAN
// ═══════════════════════════════════════════════════════════
@Composable
private fun LaporanPengeluaranDialog(
    vm: PengeluaranViewModel,
    categories: List<ExpenseCategory>,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var stats by remember { mutableStateOf<List<ExpenseStat>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val (start, end) = vm.rangeFor(vm.periode)

    LaunchedEffect(vm.periode) {
        loading = true
        try {
            stats = vm.let {
                val repo = it // gunakan repo via vm
                // akses langsung — nanti di-inject
                emptyList()
            }
        } catch (_: Exception) {}
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Laporan Pengeluaran") },
        text = {
            Column(Modifier.heightIn(max = 500.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Periode: ${vm.periode.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)

                Card(colors = CardDefaults.cardColors(containerColor = BRAND_LIGHT)) {
                    Row(Modifier.padding(12.dp)) {
                        Text("Total", Modifier.weight(1f),
                            fontWeight = FontWeight.Bold)
                        Text(vm.total.value.rupiah(),
                            fontWeight = FontWeight.Bold, color = BRAND)
                    }
                }

                Text("Per Kategori", fontWeight = FontWeight.SemiBold)

                val grouped = vm.expenses.value
                    .groupBy { it.kategoriNama }
                    .mapValues { it.value.sumOf { e -> e.jumlah } }
                    .toList().sortedByDescending { it.second }

                if (grouped.isEmpty()) {
                    Text("Belum ada data",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(grouped, key = { it.first }) { (nama, total) ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(nama, fontWeight = FontWeight.Medium)
                                }
                                Text(total.rupiah(),
                                    fontWeight = FontWeight.Bold,
                                    color = if (total > 0) DANGER
                                    else MaterialTheme.colorScheme.onSurface)
                            }
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
