package com.iyonzkasir.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.platform.LocalContext
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
import java.text.SimpleDateFormat
import java.util.*

// ═══════════════════════════════════════════════════════════
// VIEWMODEL
// ═══════════════════════════════════════════════════════════
class CrmViewModel(private val repo: CrmRepository) : ViewModel() {

    val members: StateFlow<List<Member>> = repo.members
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val vouchers: StateFlow<List<Voucher>> = repo.vouchers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var searchQuery by mutableStateOf("")

    fun searchResult(): List<Member> {
        val q = searchQuery.trim()
        if (q.isBlank()) return members.value
        return members.value.filter {
            it.nama.contains(q, ignoreCase = true) ||
            it.telepon.contains(q) ||
            it.email.contains(q, ignoreCase = true)
        }
    }

    fun saveMember(m: Member, onDone: () -> Unit = {}) = viewModelScope.launch {
        repo.saveMember(m); onDone()
    }
    fun deleteMember(m: Member) = viewModelScope.launch { repo.deleteMember(m) }
    fun saveVoucher(v: Voucher, onDone: () -> Unit = {}) = viewModelScope.launch {
        repo.saveVoucher(v); onDone()
    }
    fun deleteVoucher(v: Voucher) = viewModelScope.launch { repo.deleteVoucher(v) }
    suspend fun getMember(id: Long) = repo.getMember(id)
    suspend fun statMember(id: Long) = repo.statMember(id)
    suspend fun ordersForMember(id: Long) = repo.ordersForMember(id)
    fun observeTx(memberId: Long) = repo.observeMemberTx(memberId)
    fun bayarHutang(memberId: Long, jumlah: Int, ket: String = "") = viewModelScope.launch {
        repo.bayarHutang(memberId, jumlah, ket)
    }
    fun redeemPoin(memberId: Long, poin: Int, onDone: (Int) -> Unit) = viewModelScope.launch {
        val rupiah = repo.redeemPoin(memberId, poin)
        onDone(rupiah)
    }
    fun adjust(memberId: Long, poinDelta: Int, hutangDelta: Int, ket: String) = viewModelScope.launch {
        repo.adjust(memberId, poinDelta, hutangDelta, ket)
    }
}

class CrmVMFactory(private val repo: CrmRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = CrmViewModel(repo) as T
}

// ═══════════════════════════════════════════════════════════
// ROUTE — CRM Main (tab Member + Voucher)
// ═══════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrmRoute(app: IyonzApp, nav: NavHostController) {
    val factory = remember { CrmVMFactory(app.crmRepo) }
    val vm: CrmViewModel = viewModel(factory = factory)

    var tab by remember { mutableIntStateOf(0) }
    var editMember by remember { mutableStateOf<Member?>(null) }
    var showAddMember by remember { mutableStateOf(false) }
    var editVoucher by remember { mutableStateOf<Voucher?>(null) }
    var showAddVoucher by remember { mutableStateOf(false) }
    var detailMember by remember { mutableStateOf<Member?>(null) }

    val canEditMember = Session.can(PermissionKey.KELOLA_MEMBER)
    val canEditVoucher = Session.can(PermissionKey.KELOLA_VOUCHER)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CRM") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                }
            )
        },
        floatingActionButton = {
            when (tab) {
                0 -> if (canEditMember) {
                    ExtendedFloatingActionButton(
                        onClick = { showAddMember = true },
                        icon = { Icon(Icons.Default.PersonAdd, null) },
                        text = { Text("Member baru") },
                        containerColor = BRAND
                    )
                }
                1 -> if (canEditVoucher) {
                    ExtendedFloatingActionButton(
                        onClick = { showAddVoucher = true },
                        icon = { Icon(Icons.Default.Add, null) },
                        text = { Text("Voucher baru") },
                        containerColor = BRAND
                    )
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            // Tab selector
            TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface) {
                Tab(selected = tab == 0, onClick = { tab = 0 },
                    text = { Text("Member") },
                    icon = { Icon(Icons.Default.People, null, Modifier.size(20.dp)) })
                Tab(selected = tab == 1, onClick = { tab = 1 },
                    text = { Text("Voucher") },
                    icon = { Icon(Icons.Default.LocalOffer, null, Modifier.size(20.dp)) })
            }

            when (tab) {
                0 -> MemberList(
                    vm = vm,
                    canEdit = canEditMember,
                    onOpenDetail = { detailMember = it },
                    onEdit = { editMember = it },
                    onDelete = { vm.deleteMember(it) }
                )
                1 -> VoucherList(
                    vm = vm,
                    canEdit = canEditVoucher,
                    onEdit = { editVoucher = it },
                    onDelete = { vm.deleteVoucher(it) }
                )
            }
        }
    }

    if (showAddMember || editMember != null) {
        MemberEditorDialog(
            initial = editMember,
            onDismiss = { showAddMember = false; editMember = null },
            onSave = { m ->
                vm.saveMember(m) { showAddMember = false; editMember = null }
            }
        )
    }

    if (showAddVoucher || editVoucher != null) {
        VoucherEditorDialog(
            initial = editVoucher,
            onDismiss = { showAddVoucher = false; editVoucher = null },
            onSave = { v ->
                vm.saveVoucher(v) { showAddVoucher = false; editVoucher = null }
            }
        )
    }

    detailMember?.let { m ->
        MemberDetailDialog(
            memberId = m.id,
            vm = vm,
            onDismiss = { detailMember = null },
            onEdit = { editMember = m; detailMember = null }
        )
    }
}

// ═══════════════════════════════════════════════════════════
// MEMBER LIST
// ═══════════════════════════════════════════════════════════
@Composable
private fun MemberList(
    vm: CrmViewModel,
    canEdit: Boolean,
    onOpenDetail: (Member) -> Unit,
    onEdit: (Member) -> Unit,
    onDelete: (Member) -> Unit
) {
    val filtered = remember(vm.searchQuery, vm.members.value) { vm.searchResult() }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = vm.searchQuery, onValueChange = { vm.searchQuery = it },
            placeholder = { Text("Cari nama / telepon...",
                style = MaterialTheme.typography.bodySmall) },
            leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(18.dp)) },
            trailingIcon = {
                if (vm.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { vm.searchQuery = "" },
                        modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, null, Modifier.size(16.dp))
                    }
                }
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .height(52.dp)
        )

        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.PeopleOutline, null, Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text(if (vm.members.value.isEmpty()) "Belum ada member"
                        else "Nggak ada yang cocok",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp, 0.dp, 12.dp, 100.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { it.id }) { m ->
                    MemberCard(m, canEdit,
                        onClick = { onOpenDetail(m) },
                        onEdit = { onEdit(m) },
                        onDelete = { onDelete(m) })
                }
            }
        }
    }
}

@Composable
private fun MemberCard(
    m: Member, canEdit: Boolean,
    onClick: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit
) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(BRAND_LIGHT),
                contentAlignment = Alignment.Center
            ) {
                Text(m.nama.take(1).uppercase(),
                    color = BRAND, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(m.nama, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(6.dp))
                    TierBadge(MemberTier.fromId(m.tier))
                }
                if (m.telepon.isNotBlank()) {
                    Text("📞 ${m.telepon}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row {
                    Text("⭐ ${m.poin} poin",
                        style = MaterialTheme.typography.labelSmall)
                    if (m.hutang > 0) {
                        Spacer(Modifier.width(8.dp))
                        Text("💳 Hutang ${m.hutang.rupiah()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = DANGER, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (canEdit) {
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, null, Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, null, Modifier.size(18.dp), tint = DANGER)
                }
            }
        }
    }
}

@Composable
private fun TierBadge(tier: MemberTier) {
    val (bg, fg) = when (tier) {
        MemberTier.BASIC -> Color(0xFFE0E0E0) to Color(0xFF616161)
        MemberTier.SILVER -> Color(0xFFCFD8DC) to Color(0xFF455A64)
        MemberTier.GOLD -> Color(0xFFFFECB3) to Color(0xFFF57F17)
        MemberTier.PLATINUM -> Color(0xFFD1C4E9) to Color(0xFF4527A0)
    }
    Surface(color = bg, shape = RoundedCornerShape(4.dp)) {
        Text(tier.label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = fg, fontWeight = FontWeight.Bold)
    }
}

// ═══════════════════════════════════════════════════════════
// MEMBER EDITOR
// ═══════════════════════════════════════════════════════════
@Composable
private fun MemberEditorDialog(
    initial: Member?,
    onDismiss: () -> Unit,
    onSave: (Member) -> Unit
) {
    var nama by remember { mutableStateOf(initial?.nama ?: "") }
    var telepon by remember { mutableStateOf(initial?.telepon ?: "") }
    var email by remember { mutableStateOf(initial?.email ?: "") }
    var alamat by remember { mutableStateOf(initial?.alamat ?: "") }
    var catatan by remember { mutableStateOf(initial?.catatan ?: "") }
    var aktif by remember { mutableStateOf(initial?.aktif ?: true) }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Member Baru" else "Edit Member") },
        text = {
            Column(Modifier.heightIn(max = 500.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(nama, { nama = it },
                    label = { Text("Nama *") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(telepon,
                    { telepon = it.filter { c -> c.isDigit() || c == '+' || c == '-' } },
                    label = { Text("Telepon / WhatsApp") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(email, { email = it },
                    label = { Text("Email (opsional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(alamat, { alamat = it },
                    label = { Text("Alamat (opsional)") },
                    maxLines = 2, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(catatan, { catatan = it },
                    label = { Text("Catatan (opsional)") },
                    maxLines = 2, modifier = Modifier.fillMaxWidth())
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
                val m = (initial ?: Member()).copy(
                    nama = nama.trim(),
                    telepon = telepon.trim(),
                    email = email.trim(),
                    alamat = alamat.trim(),
                    catatan = catatan.trim(),
                    aktif = aktif
                )
                onSave(m)
            }) { Text("Simpan") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

// ═══════════════════════════════════════════════════════════
// MEMBER DETAIL
// ═══════════════════════════════════════════════════════════
@Composable
private fun MemberDetailDialog(
    memberId: Long,
    vm: CrmViewModel,
    onDismiss: () -> Unit,
    onEdit: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var member by remember { mutableStateOf<Member?>(null) }
    var stat by remember { mutableStateOf<MemberStat?>(null) }
    var showRedeem by remember { mutableStateOf(false) }
    var showBayarHutang by remember { mutableStateOf(false) }

    val txList by vm.observeTx(memberId).collectAsState(initial = emptyList())

    LaunchedEffect(memberId) {
        member = vm.getMember(memberId)
        stat = vm.statMember(memberId)
    }

    // Refresh member saat txList berubah
    LaunchedEffect(txList) {
        member = vm.getMember(memberId)
    }

    val m = member ?: return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(m.nama, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    TierBadge(MemberTier.fromId(m.tier))
                }
                if (m.telepon.isNotBlank())
                    Text("📞 ${m.telepon}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        text = {
            Column(Modifier.heightIn(max = 500.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {

                // Kartu poin & hutang
                Card(colors = CardDefaults.cardColors(containerColor = BRAND_LIGHT)) {
                    Row(Modifier.padding(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("Poin", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${m.poin}", fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge, color = BRAND)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Hutang", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(m.hutang.rupiah(), fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge,
                                color = if (m.hutang > 0) DANGER else SUCCESS)
                        }
                    }
                }

                // Tombol aksi
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showRedeem = true },
                        enabled = m.poin > 0,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.CardGiftcard, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Tukar Poin", style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(
                        onClick = { showBayarHutang = true },
                        enabled = m.hutang > 0,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Payments, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Bayar Hutang", style = MaterialTheme.typography.bodySmall)
                    }
                }

                OutlinedButton(
                    onClick = {
                        val telp = m.telepon.ifBlank { "" }
                        if (telp.isNotBlank()) sendWa(ctx, telp, buatPesanWa(m, stat))
                    },
                    enabled = m.telepon.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Chat, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Kirim WhatsApp", style = MaterialTheme.typography.bodySmall)
                }

                // Statistik
                stat?.let { s ->
                    Card {
                        Column(Modifier.padding(12.dp)) {
                            Text("Statistik", fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium)
                            Row {
                                Text("Total order", Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall)
                                Text("${s.totalOrder}x", fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            Row {
                                Text("Total belanja", Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall)
                                Text(s.totalOmzet.rupiah(), fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                // Riwayat transaksi
                Text("Riwayat", fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium)
                if (txList.isEmpty()) {
                    Text("Belum ada riwayat",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(txList.take(50), key = { it.id }) { tx ->
                            Row(verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    SimpleDateFormat("dd/MM HH:mm", Locale("id"))
                                        .format(Date(tx.timestamp)),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.width(80.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(tx.keterangan.ifBlank { tx.tipe },
                                        style = MaterialTheme.typography.bodySmall)
                                    if (tx.poinDelta != 0) {
                                        Text(
                                            (if (tx.poinDelta > 0) "+" else "") + "${tx.poinDelta} poin",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (tx.poinDelta > 0) SUCCESS else DANGER)
                                    }
                                    if (tx.hutangDelta != 0) {
                                        Text(
                                            (if (tx.hutangDelta > 0) "+" else "") +
                                                tx.hutangDelta.rupiah(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (tx.hutangDelta > 0) DANGER else SUCCESS)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Edit")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Tutup") }
        }
    )

    if (showRedeem) {
        RedeemPoinDialog(
            member = m,
            onDismiss = { showRedeem = false },
            onConfirm = { poin ->
                vm.redeemPoin(m.id, poin) { rupiah ->
                    showRedeem = false
                    // toast via snackbar bisa ditambahin nanti
                }
            }
        )
    }
    if (showBayarHutang) {
        BayarHutangDialog(
            member = m,
            onDismiss = { showBayarHutang = false },
            onConfirm = { jumlah ->
                vm.bayarHutang(m.id, jumlah)
                showBayarHutang = false
            }
        )
    }
}

@Composable
private fun RedeemPoinDialog(
    member: Member,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var poinText by remember { mutableStateOf("") }
    val poin = poinText.toIntOrNull() ?: 0
    val rupiah = LoyaltyConfig.poinKeRupiah(poin)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tukar Poin") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Poin tersedia: ${member.poin}",
                    style = MaterialTheme.typography.bodySmall)
                Text("1 poin = ${LoyaltyConfig.RUPIAH_PER_POIN.rupiah()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = poinText,
                    onValueChange = { poinText = it.filter { c -> c.isDigit() }.take(6) },
                    label = { Text("Jumlah poin ditukar") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(10, 50, 100, member.poin).distinct()
                        .filter { it in 1..member.poin }.take(4).forEach { p ->
                        AssistChip(
                            onClick = { poinText = p.toString() },
                            label = { Text("$p",
                                style = MaterialTheme.typography.bodySmall) }
                        )
                    }
                }
                if (poin > 0) {
                    Card(colors = CardDefaults.cardColors(containerColor = BRAND_LIGHT)) {
                        Column(Modifier.padding(10.dp)) {
                            Text("Dapat diskon: ${rupiah.rupiah()}",
                                fontWeight = FontWeight.Bold, color = BRAND)
                            Text("Poin sisa: ${member.poin - poin}",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(poin) },
                enabled = poin > 0 && poin <= member.poin
            ) { Text("Tukar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

@Composable
private fun BayarHutangDialog(
    member: Member,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var text by remember { mutableStateOf("") }
    val jumlah = text.toIntOrNull() ?: 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bayar Hutang") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Hutang: ${member.hutang.rupiah()}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold, color = DANGER)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter { c -> c.isDigit() }.take(9) },
                    label = { Text("Jumlah bayar") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(onClick = { text = member.hutang.toString() },
                        label = { Text("Lunas semua",
                            style = MaterialTheme.typography.bodySmall) })
                    listOf(50000, 100000, 200000).forEach { v ->
                        if (v <= member.hutang) {
                            AssistChip(onClick = { text = v.toString() },
                                label = { Text(v.rupiah(),
                                    style = MaterialTheme.typography.bodySmall) })
                        }
                    }
                }
                if (jumlah > 0) {
                    Text("Sisa hutang: ${(member.hutang - jumlah).coerceAtLeast(0).rupiah()}",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(jumlah) },
                enabled = jumlah > 0
            ) { Text("Bayar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

// ═══════════════════════════════════════════════════════════
// VOUCHER LIST
// ═══════════════════════════════════════════════════════════
@Composable
private fun VoucherList(
    vm: CrmViewModel,
    canEdit: Boolean,
    onEdit: (Voucher) -> Unit,
    onDelete: (Voucher) -> Unit
) {
    val vouchers by vm.vouchers.collectAsState()
    val now = System.currentTimeMillis()

    if (vouchers.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.LocalOffer, null, Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text("Belum ada voucher",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 100.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(vouchers, key = { it.kode }) { v ->
                VoucherCard(v, now, canEdit,
                    onEdit = { onEdit(v) },
                    onDelete = { onDelete(v) })
            }
        }
    }
}

@Composable
private fun VoucherCard(
    v: Voucher, now: Long, canEdit: Boolean,
    onEdit: () -> Unit, onDelete: () -> Unit
) {
    val expired = v.tglAkhir > 0 && v.tglAkhir < now
    val habis = v.kuota > 0 && v.terpakai >= v.kuota
    val invalid = expired || habis || !v.aktif

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
                    .background(if (invalid) MaterialTheme.colorScheme.surfaceVariant
                    else BRAND_LIGHT),
                contentAlignment = Alignment.Center
            ) {
                Text(v.tipe.let {
                    if (it == "PERSEN") "${v.value}%" else "Rp"
                }, fontWeight = FontWeight.Bold, color = BRAND)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(v.kode, fontWeight = FontWeight.Bold)
                Text(v.nama, style = MaterialTheme.typography.bodySmall)
                Text(
                    buildString {
                        if (v.minBelanja > 0) append("Min ${v.minBelanja.rupiah()} • ")
                        if (v.kuota > 0) append("${v.terpakai}/${v.kuota} • ")
                        if (v.tglAkhir > 0) append("s/d " + v.tglAkhir.tanggalPendek())
                        else append("Tanpa batas")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (invalid) {
                    Text(if (expired) "EXPIRED" else if (habis) "HABIS" else "NONAKTIF",
                        style = MaterialTheme.typography.labelSmall,
                        color = DANGER, fontWeight = FontWeight.Bold)
                }
            }
            if (canEdit) {
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, null, Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, null, Modifier.size(18.dp), tint = DANGER)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// VOUCHER EDITOR
// ═══════════════════════════════════════════════════════════
@Composable
private fun VoucherEditorDialog(
    initial: Voucher?,
    onDismiss: () -> Unit,
    onSave: (Voucher) -> Unit
) {
    var kode by remember { mutableStateOf(initial?.kode ?: "") }
    var nama by remember { mutableStateOf(initial?.nama ?: "") }
    var tipe by remember { mutableStateOf(initial?.tipe ?: "NOMINAL") }
    var valueText by remember {
        mutableStateOf(if ((initial?.value ?: 0) > 0) initial!!.value.toString() else "")
    }
    var minText by remember {
        mutableStateOf(if ((initial?.minBelanja ?: 0) > 0) initial!!.minBelanja.toString() else "")
    }
    var maxText by remember {
        mutableStateOf(if ((initial?.maxDiskon ?: 0) > 0) initial!!.maxDiskon.toString() else "")
    }
    var kuotaText by remember {
        mutableStateOf(if ((initial?.kuota ?: 0) > 0) initial!!.kuota.toString() else "")
    }
    var hariText by remember { mutableStateOf("30") }
    var aktif by remember { mutableStateOf(initial?.aktif ?: true) }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Voucher Baru" else "Edit Voucher") },
        text = {
            Column(Modifier.heightIn(max = 500.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(kode,
                    { kode = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(20) },
                    label = { Text("Kode *") },
                    placeholder = { Text("cth: HEMAT10") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(nama, { nama = it },
                    label = { Text("Nama voucher") },
                    placeholder = { Text("cth: Promo Kemerdekaan") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())

                Text("Tipe diskon:", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = tipe == "NOMINAL", onClick = { tipe = "NOMINAL" },
                        label = { Text("Nominal (Rp)") })
                    FilterChip(selected = tipe == "PERSEN", onClick = { tipe = "PERSEN" },
                        label = { Text("Persen (%)") })
                }
                OutlinedTextField(valueText,
                    { valueText = it.filter { c -> c.isDigit() }.take(7) },
                    label = { Text(if (tipe == "PERSEN") "Nilai (%)" else "Nilai (Rp)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(minText,
                    { minText = it.filter { c -> c.isDigit() }.take(9) },
                    label = { Text("Min belanja (opsional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                if (tipe == "PERSEN") {
                    OutlinedTextField(maxText,
                        { maxText = it.filter { c -> c.isDigit() }.take(9) },
                        label = { Text("Maks diskon (opsional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                OutlinedTextField(kuotaText,
                    { kuotaText = it.filter { c -> c.isDigit() }.take(6) },
                    label = { Text("Kuota total (0 = unlimited)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                if (initial == null) {
                    OutlinedTextField(hariText,
                        { hariText = it.filter { c -> c.isDigit() }.take(3) },
                        label = { Text("Berlaku berapa hari? (0 = tanpa batas)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, modifier = Modifier.fillMaxWidth())
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
                when {
                    kode.isBlank() -> err = "Kode wajib diisi"
                    valueText.toIntOrNull() == null || valueText.toInt() <= 0 ->
                        err = "Nilai diskon harus > 0"
                    tipe == "PERSEN" && valueText.toInt() > 100 ->
                        err = "Persen maks 100"
                    else -> {
                        val now = System.currentTimeMillis()
                        val days = hariText.toIntOrNull() ?: 0
                        val tglAkhir = if (initial == null) {
                            if (days > 0) now + days * 24L * 60 * 60 * 1000 else 0L
                        } else initial.tglAkhir
                        onSave((initial ?: Voucher()).copy(
                            kode = kode.trim(),
                            nama = nama.trim(),
                            tipe = tipe,
                            value = valueText.toInt(),
                            minBelanja = minText.toIntOrNull() ?: 0,
                            maxDiskon = maxText.toIntOrNull() ?: 0,
                            kuota = kuotaText.toIntOrNull() ?: 0,
                            tglMulai = initial?.tglMulai ?: now,
                            tglAkhir = tglAkhir,
                            aktif = aktif
                        ))
                    }
                }
            }) { Text("Simpan") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

// ═══════════════════════════════════════════════════════════
// WHATSAPP HELPER
// ═══════════════════════════════════════════════════════════
fun sendWa(context: android.content.Context, telepon: String, pesan: String) {
    val cleaned = telepon.replace("+", "").replace("-", "").replace(" ", "")
    val nomor = if (cleaned.startsWith("0")) "62${cleaned.drop(1)}"
    else if (cleaned.startsWith("62")) cleaned else "62$cleaned"
    val url = "https://wa.me/$nomor?text=${Uri.encode(pesan)}"
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    } catch (_: Exception) {}
}

fun buatPesanWa(m: Member, stat: MemberStat?): String = buildString {
    append("Halo ${m.nama}! 👋\n\n")
    if (stat != null) {
        append("Terima kasih udah belanja ")
        append("${stat.totalOrder}x di toko kami.\n")
        append("Total belanja: ${stat.totalOmzet.rupiah()}\n\n")
    }
    append("⭐ Poin kamu saat ini: ${m.poin}\n")
    if (m.hutang > 0) {
        append("💳 Hutang: ${m.hutang.rupiah()}\n")
    }
    append("\nSalam,\n")
    append("iyonzkasir")
}
