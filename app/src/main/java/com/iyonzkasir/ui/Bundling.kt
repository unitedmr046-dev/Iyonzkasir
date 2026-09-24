package com.iyonzkasir.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
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
// VIEWMODEL
// ═══════════════════════════════════════════════════════════
class BundlingViewModel(
    private val repo: MenuBundleRepository,
    private val posRepo: PosRepository
) : ViewModel() {

    val bundles: StateFlow<List<MenuBundle>> = repo.bundles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val menuList: StateFlow<List<MenuItem>> = posRepo.menu
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveBundle(
        bundle: MenuBundle,
        groups: List<EditorGroup>,
        onDone: (Long) -> Unit
    ) = viewModelScope.launch {
        val bundleId = repo.save(bundle)

        if (bundle.id != 0L) {
            repo.deleteGroups(bundleId)
        }

        groups.forEachIndexed { gIdx, g ->
            val gId = repo.saveGroup(
                MenuBundleGroup(
                    bundleId = bundleId,
                    nama = g.nama.ifBlank {
                        if (bundle.tipe == BundleTipe.FIXED.id) "Isi Paket"
                        else "Grup ${gIdx + 1}"
                    },
                    minPilih = g.minPilih,
                    maxPilih = g.maxPilih,
                    urutan = gIdx,
                    wajib = g.wajib
                )
            )
            g.items.forEachIndexed { iIdx, item ->
                repo.saveItem(
                    MenuBundleItem(
                        groupId = gId,
                        menuId = item.menuId,
                        qty = item.qty,
                        hargaExtra = item.hargaExtra,
                        isDefaultPick = item.isDefaultPick,
                        urutan = iIdx
                    )
                )
            }
        }
        onDone(bundleId)
    }

    fun deleteBundle(b: MenuBundle) = viewModelScope.launch {
        repo.delete(b)
    }

    suspend fun loadFull(id: Long) = repo.loadFull(id)
}

class BundlingVMFactory(
    private val repo: MenuBundleRepository,
    private val posRepo: PosRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        BundlingViewModel(repo, posRepo) as T
}

// ═══════════════════════════════════════════════════════════
// EDITOR MODELS
// ═══════════════════════════════════════════════════════════
data class EditorItem(
    val id: Long = 0,
    val menuId: Long,
    val menuNama: String,
    val menuHarga: Int,
    val qty: Int = 1,
    val hargaExtra: Int = 0,
    val isDefaultPick: Boolean = false
)

data class EditorGroup(
    val id: Long = 0,
    val nama: String = "",
    val minPilih: Int = 1,
    val maxPilih: Int = 1,
    val wajib: Boolean = true,
    val items: List<EditorItem> = emptyList()
)

// ═══════════════════════════════════════════════════════════
// ROUTE — List Paket
// ═══════════════════════════════════════════════════════════
@Composable
fun BundlingRoute(app: IyonzApp, nav: NavHostController) {
    val factory = remember { BundlingVMFactory(app.bundleRepo, app.posRepo) }
    val vm: BundlingViewModel = viewModel(factory = factory)
    BundlingScreen(vm, nav)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BundlingScreen(vm: BundlingViewModel, nav: NavHostController) {
    val bundles by vm.bundles.collectAsState()
    var confirmDelete by remember { mutableStateOf<MenuBundle?>(null) }

    val canEdit = Session.can(PermissionKey.KELOLA_MENU)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Paket Bundling") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null)
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
                    onClick = { nav.navigate("${Routes.BUNDLING_EDIT}/0") },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("Buat Paket") },
                    containerColor = BRAND
                )
            }
        }
    ) { pad ->
        if (bundles.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize()) {
                EmptyState(
                    icon = Icons.Default.Inventory,
                    title = "Belum ada paket bundling",
                    subtitle = "Buat paket combo untuk tingkatkan penjualan",
                    ctaLabel = if (canEdit) "Buat Paket Pertama" else null,
                    onCta = if (canEdit) {
                        { nav.navigate("${Routes.BUNDLING_EDIT}/0") }
                    } else null
                )
            }
        } else {
            LazyColumn(
                Modifier.padding(pad).fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Sp.md, end = Sp.md,
                    top = Sp.md, bottom = 100.dp
                ),
                verticalArrangement = Arrangement.spacedBy(Sp.sm)
            ) {
                items(bundles, key = { it.id }) { b ->
                    BundleCard(
                        bundle = b,
                        canEdit = canEdit,
                        onEdit = { nav.navigate("${Routes.BUNDLING_EDIT}/${b.id}") },
                        onDelete = { confirmDelete = b }
                    )
                }
            }
        }
    }

    confirmDelete?.let { b ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Hapus Paket?") },
            text = { Text("Hapus paket '${b.nama}'? Tindakan ini tidak bisa dibatalkan.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteBundle(b)
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
private fun BundleCard(
    bundle: MenuBundle,
    canEdit: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val tipeEnum = BundleTipe.fromId(bundle.tipe)
    val badgeColor = when (tipeEnum) {
        BundleTipe.FIXED -> Color(0xFFFF8C42)
        BundleTipe.PILIHAN -> Color(0xFF8E24AA)
        BundleTipe.MIX -> Color(0xFF1E88E5)
    }

    Card(
        Modifier.fillMaxWidth(),
        shape = Rd.card,
        elevation = CardDefaults.cardElevation(defaultElevation = El.card)
    ) {
        Column(Modifier.padding(Sp.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(56.dp).clip(RoundedCornerShape(Rd.sm))
                        .background(badgeColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (bundle.fotoUri != null) {
                        AsyncImage(
                            model = bundle.fotoUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(tipeEnum.emoji,
                            style = MaterialTheme.typography.headlineMedium)
                    }
                }
                Spacer(Modifier.width(Sp.md))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(bundle.nama,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            modifier = Modifier.weight(1f, fill = false))
                        Spacer(Modifier.width(Sp.xs))
                        Surface(
                            color = badgeColor,
                            shape = RoundedCornerShape(Rd.xs)
                        ) {
                            Text(
                                "${tipeEnum.emoji} ${tipeEnum.label}",
                                modifier = Modifier.padding(
                                    horizontal = 6.dp, vertical = 2.dp
                                ),
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    if (bundle.deskripsi.isNotBlank()) {
                        Text(bundle.deskripsi,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1)
                    }
                    Spacer(Modifier.height(Sp.xs))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(bundle.hargaBundle.rupiah(),
                            fontWeight = FontWeight.Bold,
                            color = BRAND,
                            style = MaterialTheme.typography.titleMedium)
                        if (bundle.hargaAsli > bundle.hargaBundle) {
                            Spacer(Modifier.width(Sp.sm))
                            Text(bundle.hargaAsli.rupiah(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textDecoration = TextDecoration.LineThrough)
                            Spacer(Modifier.width(Sp.xs))
                            Surface(
                                color = SUCCESS.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(Rd.xs)
                            ) {
                                Text(
                                    "Hemat ${(bundle.hargaAsli - bundle.hargaBundle).rupiah()}",
                                    modifier = Modifier.padding(
                                        horizontal = 6.dp, vertical = 2.dp
                                    ),
                                    color = SUCCESS,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            if (canEdit) {
                Spacer(Modifier.height(Sp.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Sp.sm)) {
                    OutlinedButton(
                        onClick = onEdit,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Edit, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Edit", style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(
                        onClick = onDelete,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = DANGER
                        ),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Delete, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Hapus", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// ROUTE — Editor Paket
// ═══════════════════════════════════════════════════════════
@Composable
fun BundlingEditRoute(app: IyonzApp, nav: NavHostController, bundleId: Long?) {
    val factory = remember { BundlingVMFactory(app.bundleRepo, app.posRepo) }
    val vm: BundlingViewModel = viewModel(factory = factory)
    BundlingEditorScreen(vm, nav, bundleId)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BundlingEditorScreen(
    vm: BundlingViewModel,
    nav: NavHostController,
    bundleId: Long?
) {
    val isNew = bundleId == null || bundleId == 0L

    var nama by remember { mutableStateOf("") }
    var deskripsi by remember { mutableStateOf("") }
    var hargaText by remember { mutableStateOf("") }
    var hargaAsliText by remember { mutableStateOf("") }
    var tipe by remember { mutableStateOf(BundleTipe.FIXED) }
    var fotoUri by remember { mutableStateOf<String?>(null) }
    var tersedia by remember { mutableStateOf(true) }
    var groups by remember { mutableStateOf<List<EditorGroup>>(emptyList()) }
    var loaded by remember { mutableStateOf(isNew) }
    var saving by remember { mutableStateOf(false) }

    var showItemPicker by remember { mutableStateOf<Int?>(null) }
    var showGroupEditor by remember { mutableStateOf<Int?>(null) }

    val menuList by vm.menuList.collectAsState()

    LaunchedEffect(bundleId) {
        if (bundleId != null && bundleId != 0L) {
            val (b, gs, itemsMap) = vm.loadFull(bundleId)
            if (b != null) {
                nama = b.nama
                deskripsi = b.deskripsi
                hargaText = b.hargaBundle.toString()
                hargaAsliText = if (b.hargaAsli > 0) b.hargaAsli.toString() else ""
                tipe = BundleTipe.fromId(b.tipe)
                fotoUri = b.fotoUri
                tersedia = b.tersedia
                groups = gs.map { g ->
                    EditorGroup(
                        id = g.id,
                        nama = g.nama,
                        minPilih = g.minPilih,
                        maxPilih = g.maxPilih,
                        wajib = g.wajib,
                        items = (itemsMap[g.id] ?: emptyList()).map { item ->
                            val menu = menuList.firstOrNull { it.id == item.menuId }
                            EditorItem(
                                id = item.id,
                                menuId = item.menuId,
                                menuNama = menu?.nama ?: "Menu #${item.menuId}",
                                menuHarga = menu?.harga ?: 0,
                                qty = item.qty,
                                hargaExtra = item.hargaExtra,
                                isDefaultPick = item.isDefaultPick
                            )
                        }
                    )
                }
            }
            loaded = true
        }
    }

    LaunchedEffect(groups, tipe) {
        if (tipe == BundleTipe.FIXED && groups.isNotEmpty()) {
            val sum = groups.firstOrNull()?.items?.sumOf {
                it.menuHarga * it.qty
            } ?: 0
            if (sum > 0) hargaAsliText = sum.toString()
        }
    }

    LaunchedEffect(tipe) {
        if (tipe == BundleTipe.PILIHAN && groups.isEmpty()) {
            groups = listOf(
                EditorGroup(nama = "Pilih 1", minPilih = 1, maxPilih = 1)
            )
        }
    }

    val harga = hargaText.toIntOrNull() ?: 0
    val hargaAsli = hargaAsliText.toIntOrNull() ?: 0

    val formValid = nama.isNotBlank() && harga > 0 && groups.isNotEmpty() &&
            groups.any { it.items.isNotEmpty() }

    val formError = when {
        nama.isBlank() -> "Nama paket wajib diisi"
        hargaText.isBlank() -> "Harga paket wajib diisi"
        harga <= 0 -> "Harga harus lebih dari 0"
        groups.isEmpty() || groups.all { it.items.isEmpty() } ->
            "Tambahkan minimal 1 item ke paket"
        else -> null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "Buat Paket" else "Edit Paket") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            if (!formValid || saving) return@TextButton
                            saving = true
                            val bundle = MenuBundle(
                                id = bundleId ?: 0L,
                                nama = nama.trim(),
                                deskripsi = deskripsi.trim(),
                                hargaBundle = harga,
                                tipe = tipe.id,
                                hargaAsli = hargaAsli,
                                fotoUri = fotoUri,
                                tersedia = tersedia,
                                aktif = true
                            )
                            vm.saveBundle(bundle, groups) {
                                saving = false
                                nav.popBackStack()
                            }
                        },
                        enabled = formValid && !saving
                    ) {
                        if (saving) {
                            CircularProgressIndicator(
                                Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Simpan",
                                color = Color.White,
                                fontWeight = FontWeight.Bold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BRAND, titleContentColor = Color.White
                )
            )
        }
    ) { pad ->
        if (!loaded) {
            Box(Modifier.padding(pad).fillMaxSize(),
                contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                Modifier.padding(pad).fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(Sp.lg),
                verticalArrangement = Arrangement.spacedBy(Sp.md)
            ) {

                Text("Tipe Paket", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(Sp.sm)) {
                    BundleTipe.values().filter { it != BundleTipe.MIX }
                        .forEach { t ->
                            FilterChip(
                                selected = tipe == t,
                                onClick = {
                                    tipe = t
                                    if (t == BundleTipe.FIXED) {
                                        groups = listOf(
                                            EditorGroup(
                                                nama = "Isi Paket",
                                                minPilih = 0,
                                                maxPilih = 0,
                                                wajib = true,
                                                items = groups.firstOrNull()?.items
                                                    ?: emptyList()
                                            )
                                        )
                                    } else if (t == BundleTipe.PILIHAN &&
                                        groups.size == 1 &&
                                        groups[0].nama == "Isi Paket") {
                                        groups = listOf(
                                            EditorGroup(
                                                nama = "Pilih 1",
                                                minPilih = 1,
                                                maxPilih = 1,
                                                items = groups[0].items
                                            )
                                        )
                                    }
                                },
                                label = { Text("${t.emoji} ${t.label}",
                                    style = MaterialTheme.typography.bodySmall) }
                            )
                        }
                }

                OutlinedTextField(
                    value = nama,
                    onValueChange = { nama = it },
                    label = { Text("Nama paket *") },
                    placeholder = { Text("cth: Paket Hemat A") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = deskripsi,
                    onValueChange = { deskripsi = it },
                    label = { Text("Deskripsi (opsional)") },
                    placeholder = { Text("cth: Nasi + Ayam + Es Teh") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = hargaText,
                    onValueChange = {
                        hargaText = it.filter { c -> c.isDigit() }.take(10)
                    },
                    label = { Text("Harga jual paket *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = hargaAsliText,
                    onValueChange = {
                        hargaAsliText = it.filter { c -> c.isDigit() }.take(10)
                    },
                    label = { Text("Harga asli (untuk display hemat)") },
                    supportingText = {
                        Text("Kosongin kalau nggak mau tampil \"Hemat Rp X\"",
                            style = MaterialTheme.typography.labelSmall)
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (hargaAsli > harga && harga > 0) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = SUCCESS.copy(alpha = 0.1f)
                        )
                    ) {
                        Row(Modifier.padding(Sp.md),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocalOffer, null, tint = SUCCESS)
                            Spacer(Modifier.width(Sp.sm))
                            Text("Customer hemat ${(hargaAsli - harga).rupiah()}",
                                fontWeight = FontWeight.Bold, color = SUCCESS)
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Tersedia untuk dijual", Modifier.weight(1f))
                    Switch(checked = tersedia, onCheckedChange = { tersedia = it })
                }

                HorizontalDivider(Modifier.padding(vertical = Sp.sm))

                if (tipe == BundleTipe.FIXED) {
                    Text("Isi Paket", fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(Sp.xs))

                    val g0 = groups.firstOrNull() ?: EditorGroup(
                        nama = "Isi Paket", items = emptyList()
                    )
                    if (g0.items.isEmpty()) {
                        Card(
                            Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                Modifier.padding(Sp.xl).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.AddCircleOutline, null,
                                    Modifier.size(40.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(Sp.sm))
                                Text("Belum ada item",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        g0.items.forEachIndexed { idx, item ->
                            EditorItemRow(
                                item = item,
                                onQtyChange = { newQty ->
                                    groups = groups.mapIndexed { gi, g ->
                                        if (gi == 0) {
                                            g.copy(items = g.items.mapIndexed { ii, it ->
                                                if (ii == idx) it.copy(qty = newQty) else it
                                            })
                                        } else g
                                    }
                                },
                                onDelete = {
                                    groups = groups.mapIndexed { gi, g ->
                                        if (gi == 0) {
                                            g.copy(items = g.items.filterIndexed { ii, _ ->
                                                ii != idx
                                            })
                                        } else g
                                    }
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(Sp.sm))
                    OutlinedButton(
                        onClick = { showItemPicker = 0 },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Tambah Item ke Paket")
                    }

                } else {
                    Text("Grup Pilihan", fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(Sp.xs))

                    groups.forEachIndexed { gIdx, g ->
                        GroupCard(
                            group = g,
                            index = gIdx,
                            onEditInfo = { showGroupEditor = gIdx },
                            onAddItem = { showItemPicker = gIdx },
                            onDeleteItem = { itemIdx ->
                                groups = groups.mapIndexed { gi, gg ->
                                    if (gi == gIdx)
                                        gg.copy(items = gg.items.filterIndexed { ii, _ ->
                                            ii != itemIdx
                                        })
                                    else gg
                                }
                            },
                            onDeleteGroup = {
                                groups = groups.filterIndexed { gi, _ -> gi != gIdx }
                            }
                        )
                    }

                    Spacer(Modifier.height(Sp.sm))
                    OutlinedButton(
                        onClick = {
                            groups = groups + EditorGroup(
                                nama = "Pilih ${groups.size + 1}",
                                minPilih = 1,
                                maxPilih = 1,
                                wajib = true,
                                items = emptyList()
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Tambah Grup Pilihan")
                    }
                }

                formError?.let {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(Rd.sm),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(Sp.sm),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(Sp.xs))
                            Text(it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }

                Spacer(Modifier.height(Sp.xxl))
            }
        }
    }

    showItemPicker?.let { gIdx ->
        MenuPickerDialog(
            menuList = menuList,
            onDismiss = { showItemPicker = null },
            onPick = { menu, qty, extra, isDefault ->
                groups = groups.mapIndexed { gi, g ->
                    if (gi == gIdx) {
                        g.copy(items = g.items + EditorItem(
                            menuId = menu.id,
                            menuNama = menu.nama,
                            menuHarga = menu.harga,
                            qty = qty,
                            hargaExtra = extra,
                            isDefaultPick = isDefault
                        ))
                    } else g
                }
                showItemPicker = null
            }
        )
    }

    showGroupEditor?.let { gIdx ->
        val g = groups.getOrNull(gIdx)
        if (g != null) {
            GroupInfoDialog(
                initial = g,
                onDismiss = { showGroupEditor = null },
                onSave = { updated ->
                    groups = groups.mapIndexed { gi, gg ->
                        if (gi == gIdx) updated else gg
                    }
                    showGroupEditor = null
                }
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════
// EDITOR ITEM ROW
// ═══════════════════════════════════════════════════════════
@Composable
private fun EditorItemRow(
    item: EditorItem,
    onQtyChange: (Int) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = Rd.card,
        elevation = CardDefaults.cardElevation(defaultElevation = El.card)
    ) {
        Row(Modifier.padding(Sp.md), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.menuNama, fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium)
                Text(item.menuHarga.rupiah(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (item.hargaExtra > 0) {
                    Text("+ ${item.hargaExtra.rupiah()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = SUCCESS)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    if (item.qty > 1) onQtyChange(item.qty - 1)
                }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Remove, null, Modifier.size(18.dp))
                }
                Text("${item.qty}",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = Sp.sm))
                IconButton(onClick = { onQtyChange(item.qty + 1) },
                    modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete,
                    modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, null,
                        Modifier.size(18.dp), tint = DANGER)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// GROUP CARD
// ═══════════════════════════════════════════════════════════
@Composable
private fun GroupCard(
    group: EditorGroup,
    index: Int,
    onEditInfo: () -> Unit,
    onAddItem: () -> Unit,
    onDeleteItem: (Int) -> Unit,
    onDeleteGroup: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = Rd.card,
        elevation = CardDefaults.cardElevation(defaultElevation = El.card),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(Modifier.padding(Sp.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = BRAND,
                    shape = CircleShape
                ) {
                    Text("${index + 1}",
                        modifier = Modifier.padding(
                            horizontal = 8.dp, vertical = 2.dp
                        ),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.width(Sp.sm))
                Column(Modifier.weight(1f)) {
                    Text(group.nama,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (group.wajib)
                            "Wajib pilih ${group.minPilih}-${group.maxPilih}"
                        else "Opsional (maks ${group.maxPilih})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onEditInfo,
                    modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, null,
                        Modifier.size(18.dp), tint = BRAND)
                }
                IconButton(onClick = onDeleteGroup,
                    modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, null,
                        Modifier.size(18.dp), tint = DANGER)
                }
            }

            Spacer(Modifier.height(Sp.sm))

            if (group.items.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(Rd.sm),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Belum ada opsi",
                        modifier = Modifier.padding(Sp.md).fillMaxWidth(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                group.items.forEachIndexed { idx, item ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (item.isDefaultPick) {
                            Icon(Icons.Default.CheckCircle, null,
                                Modifier.size(16.dp), tint = SUCCESS)
                            Spacer(Modifier.width(4.dp))
                        } else {
                            Icon(Icons.Default.RadioButtonUnchecked, null,
                                Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(4.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(item.menuNama,
                                style = MaterialTheme.typography.bodyMedium)
                            if (item.hargaExtra > 0) {
                                Text("+ ${item.hargaExtra.rupiah()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SUCCESS)
                            }
                        }
                        IconButton(onClick = { onDeleteItem(idx) },
                            modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, null,
                                Modifier.size(16.dp), tint = DANGER)
                        }
                    }
                }
            }

            Spacer(Modifier.height(Sp.xs))
            OutlinedButton(
                onClick = onAddItem,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                Icon(Icons.Default.Add, null, Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Tambah Opsi", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// MENU PICKER DIALOG
// ═══════════════════════════════════════════════════════════
@Composable
private fun MenuPickerDialog(
    menuList: List<MenuItem>,
    onDismiss: () -> Unit,
    onPick: (MenuItem, Int, Int, Boolean) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<MenuItem?>(null) }

    val filtered = remember(search, menuList) {
        if (search.isBlank()) menuList
        else menuList.filter { it.nama.contains(search, ignoreCase = true) }
    }

    if (selected == null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Pilih Menu") },
            text = {
                Column(Modifier.heightIn(max = 500.dp)) {
                    OutlinedTextField(
                        value = search, onValueChange = { search = it },
                        placeholder = { Text("Cari menu...",
                            style = MaterialTheme.typography.bodySmall) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, null, Modifier.size(18.dp))
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    )
                    Spacer(Modifier.height(Sp.sm))
                    if (filtered.isEmpty()) {
                        Box(Modifier.fillMaxWidth().padding(Sp.xl),
                            contentAlignment = Alignment.Center) {
                            Text("Menu nggak ada",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(Sp.xs)
                        ) {
                            items(filtered, key = { it.id }) { m ->
                                Card(
                                    Modifier.fillMaxWidth().clickable {
                                        selected = m
                                    }
                                ) {
                                    Row(Modifier.padding(Sp.md),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(m.nama,
                                                fontWeight = FontWeight.SemiBold)
                                            Text(m.harga.rupiah(),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = BRAND)
                                        }
                                        Icon(Icons.Default.ChevronRight, null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("Batal") }
            }
        )
    } else {
        MenuItemConfigDialog(
            menu = selected!!,
            onDismiss = { selected = null },
            onBack = { selected = null },
            onConfirm = { qty, extra, isDefault ->
                onPick(selected!!, qty, extra, isDefault)
            }
        )
    }
}

@Composable
private fun MenuItemConfigDialog(
    menu: MenuItem,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    onConfirm: (Int, Int, Boolean) -> Unit
) {
    var qtyText by remember { mutableStateOf("1") }
    var extraText by remember { mutableStateOf("") }
    var isDefault by remember { mutableStateOf(false) }

    val qty = qtyText.toIntOrNull() ?: 1
    val extra = extraText.toIntOrNull() ?: 0   // ⬅️ PATCH FIX 1

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(menu.nama) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Sp.sm)) {
                Text("Harga: ${menu.harga.rupiah()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)

                OutlinedTextField(
                    value = qtyText,
                    onValueChange = {
                        qtyText = it.filter { c -> c.isDigit() }.take(3)
                    },
                    label = { Text("Jumlah (qty)") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = extraText,
                    onValueChange = {
                        extraText = it.filter { c -> c.isDigit() }.take(7)
                    },
                    label = { Text("Harga tambahan (opsional)") },
                    placeholder = { Text("0") },
                    supportingText = {
                        Text("Upcharge kalau customer pilih opsi ini",
                            style = MaterialTheme.typography.labelSmall)
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Pilihan Default",
                            style = MaterialTheme.typography.bodyMedium)
                        Text("Pre-select saat dialog dibuka",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = isDefault,
                        onCheckedChange = { isDefault = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(qty, extra, isDefault) }) {
                Text("Tambah")
            }
        },
        dismissButton = {
            TextButton(onClick = onBack) { Text("Kembali") }
        }
    )
}

// ═══════════════════════════════════════════════════════════
// GROUP INFO DIALOG
// ═══════════════════════════════════════════════════════════
@Composable
private fun GroupInfoDialog(
    initial: EditorGroup,
    onDismiss: () -> Unit,
    onSave: (EditorGroup) -> Unit
) {
    var nama by remember { mutableStateOf(initial.nama) }
    var minText by remember { mutableStateOf(initial.minPilih.toString()) }
    var maxText by remember { mutableStateOf(initial.maxPilih.toString()) }
    var wajib by remember { mutableStateOf(initial.wajib) }
    var err by remember { mutableStateOf<String?>(null) }

    val min = minText.toIntOrNull() ?: 0
    val max = maxText.toIntOrNull() ?: 1

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Atur Grup") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Sp.sm)) {
                OutlinedTextField(
                    value = nama, onValueChange = { nama = it; err = null },
                    label = { Text("Nama grup *") },
                    placeholder = { Text("cth: Pilih 1 Nasi") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(Sp.sm)) {
                    OutlinedTextField(
                        value = minText,
                        onValueChange = {
                            minText = it.filter { c -> c.isDigit() }.take(2)
                            err = null
                        },
                        label = { Text("Min pilih") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        ),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = maxText,
                        onValueChange = {
                            maxText = it.filter { c -> c.isDigit() }.take(2)
                            err = null
                        },
                        label = { Text("Maks pilih") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        ),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Text("Contoh: min 1 maks 1 = single choice. min 0 maks 5 = multi bebas.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Wajib dipilih",
                            style = MaterialTheme.typography.bodyMedium)
                        Text("Kalau off, grup jadi opsional",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = wajib, onCheckedChange = { wajib = it })
                }

                err?.let {
                    Text(it, color = DANGER,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    nama.isBlank() -> err = "Nama grup wajib diisi"
                    max <= 0 -> err = "Maks pilih minimal 1"
                    wajib && min < 1 -> err = "Kalau wajib, min pilih minimal 1"
                    min > max -> err = "Min nggak boleh lebih dari maks"
                    else -> onSave(
                        initial.copy(
                            nama = nama.trim(),
                            minPilih = min,
                            maxPilih = max,
                            wajib = wajib
                        )
                    )
                }
            }) { Text("Simpan") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}
