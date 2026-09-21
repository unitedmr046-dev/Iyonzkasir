package com.iyonzkasir.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iyonzkasir.*
import com.iyonzkasir.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// ═══════════════════════════════════════════════════════════
// VIEWMODEL
// ═══════════════════════════════════════════════════════════
class ShiftViewModel(private val repo: ShiftRepository) : ViewModel() {
    val activeShift: StateFlow<Shift?> = repo.activeShift
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val allShifts: StateFlow<List<Shift>> = repo.allShifts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun bukaShift(modalAwal: Int, onDone: () -> Unit) {
        val u = Session.current ?: return
        viewModelScope.launch {
            repo.bukaShift(u.id, u.nama, modalAwal)
            onDone()
        }
    }

    fun tutupShift(id: Long, uangFisik: Int, catatan: String, onDone: (Shift?) -> Unit) {
        viewModelScope.launch {
            val s = repo.tutupShift(id, uangFisik, catatan)
            onDone(s)
        }
    }
}

class ShiftVMFactory(private val repo: ShiftRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ShiftViewModel(repo) as T
}

// ═══════════════════════════════════════════════════════════
// ROUTE
// ═══════════════════════════════════════════════════════════
@Composable
fun ShiftRoute(app: IyonzApp) {
    val factory = remember { ShiftVMFactory(app.shiftRepo) }
    val vm: ShiftViewModel = viewModel(factory = factory)
    ShiftScreen(vm)
}

// ═══════════════════════════════════════════════════════════
// SCREEN
// ═══════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftScreen(vm: ShiftViewModel) {
    val active by vm.activeShift.collectAsState()
    val all by vm.allShifts.collectAsState()

    var showBuka by remember { mutableStateOf(false) }
    var showTutup by remember { mutableStateOf(false) }
    var lastClosed by remember { mutableStateOf<Shift?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Shift Kasir") }) }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (active == null) {
                Card(
                    Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = BRAND_LIGHT)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LockOpen, null, tint = BRAND,
                                modifier = Modifier.size(32.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Shift Belum Dibuka",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold)
                                Text("Buka shift dulu sebelum mulai jualan",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { showBuka = true },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BRAND)
                        ) {
                            Icon(Icons.Default.PlayArrow, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Buka Shift", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                val s = active!!
                Card(
                    Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = BRAND_LIGHT)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, null, tint = BRAND,
                                modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Shift Aktif", style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold)
                                Text("#${s.id} • ${s.kasirNama}",
                                    style = MaterialTheme.typography.bodySmall)
                                Text("Buka: ${s.bukaTimestamp.tanggal()}",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(12.dp))
                        Row {
                            Text("Modal awal", Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium)
                            Text(s.modalAwal.rupiah(), fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { showTutup = true },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DANGER)
                        ) {
                            Icon(Icons.Default.Lock, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Tutup Shift", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Text("Riwayat Shift",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 8.dp))

            if (all.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Belum ada riwayat shift",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(all, key = { it.id }) { s ->
                        ShiftCard(s)
                    }
                }
            }
        }
    }

    if (showBuka) {
        BukaShiftDialog(
            onDismiss = { showBuka = false },
            onConfirm = { modal ->
                vm.bukaShift(modal) { showBuka = false }
            }
        )
    }

    if (showTutup && active != null) {
        TutupShiftDialog(
            shift = active!!,
            onDismiss = { showTutup = false },
            onConfirm = { uang, catatan ->
                vm.tutupShift(active!!.id, uang, catatan) { closed ->
                    showTutup = false
                    lastClosed = closed
                }
            }
        )
    }

    lastClosed?.let { s ->
        LaporanShiftDialog(s) { lastClosed = null }
    }
}

@Composable
private fun ShiftCard(s: Shift) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Badge(
                    containerColor = if (s.status == "OPEN") SUCCESS else
                        MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(if (s.status == "OPEN") "AKTIF" else "SELESAI",
                        color = if (s.status == "OPEN") Color.White
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text("#${s.id} • ${s.kasirNama}",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                Text(s.bukaTimestamp.tanggal(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            Row {
                Text("Omzet", Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall)
                Text(s.totalOmzet.rupiah(), fontWeight = FontWeight.Bold, color = BRAND)
            }
            if (s.status == "CLOSED") {
                Row {
                    Text("Selisih", Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall)
                    Text(
                        (if (s.selisih >= 0) "+ " else "- ") + kotlin.math.abs(s.selisih).rupiah(),
                        fontWeight = FontWeight.Bold,
                        color = if (s.selisih == 0) SUCCESS
                        else if (s.selisih > 0) WARNING else DANGER
                    )
                }
            }
        }
    }
}

@Composable
private fun BukaShiftDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var text by remember { mutableStateOf("") }
    val nilai = text.toIntOrNull() ?: 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Buka Shift") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Masukkan modal awal (uang di laci kasir):",
                    style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter { c -> c.isDigit() }.take(9) },
                    label = { Text("Modal awal (Rp)") },
                    placeholder = { Text("cth: 200000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(100000, 200000, 500000, 1000000).forEach { v ->
                        AssistChip(
                            onClick = { text = v.toString() },
                            label = { Text((v / 1000).toString() + "rb",
                                style = MaterialTheme.typography.bodySmall) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(nilai) },
                enabled = nilai > 0
            ) { Text("Buka Shift") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

@Composable
private fun TutupShiftDialog(
    shift: Shift,
    onDismiss: () -> Unit,
    onConfirm: (Int, String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var catatan by remember { mutableStateOf("") }
    val uangFisik = text.toIntOrNull() ?: 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tutup Shift #${shift.id}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.heightIn(max = 500.dp)) {
                Card(colors = CardDefaults.cardColors(containerColor = BRAND_LIGHT)) {
                    Column(Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row {
                            Text("Modal awal", Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall)
                            Text(shift.modalAwal.rupiah(),
                                style = MaterialTheme.typography.bodySmall)
                        }
                        Text("Hitung uang fisik di laci, lalu masukkan:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter { c -> c.isDigit() }.take(10) },
                    label = { Text("Uang fisik di laci (Rp)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = catatan,
                    onValueChange = { catatan = it },
                    label = { Text("Catatan (opsional)") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(uangFisik, catatan.trim()) },
                enabled = uangFisik >= 0 && text.isNotBlank()
            ) { Text("Tutup Shift", color = DANGER) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

@Composable
private fun LaporanShiftDialog(shift: Shift, onDismiss: () -> Unit) {
    val selisihWarna = when {
        shift.selisih == 0 -> SUCCESS
        shift.selisih > 0 -> WARNING
        else -> DANGER
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Laporan Shift #${shift.id}") },
        text = {
            Column(Modifier.heightIn(max = 500.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row {
                    Text("Kasir", Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall)
                    Text(shift.kasirNama, style = MaterialTheme.typography.bodySmall)
                }
                Row {
                    Text("Buka", Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall)
                    Text(shift.bukaTimestamp.tanggal(),
                        style = MaterialTheme.typography.bodySmall)
                }
                Row {
                    Text("Tutup", Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall)
                    Text(shift.tutupTimestamp.tanggal(),
                        style = MaterialTheme.typography.bodySmall)
                }
                HorizontalDivider(Modifier.padding(vertical = 6.dp))

                Baris("Jumlah transaksi", shift.totalTransaksi.toString())
                Baris("Omzet", shift.totalOmzet.rupiah())
                Baris("Tunai", shift.totalTunai.rupiah())
                Baris("Non-tunai", shift.totalNonTunai.rupiah())
                Baris("Total diskon", "- " + shift.totalDiskon.rupiah(), DANGER)
                Baris("Total pajak", shift.totalPajak.rupiah())

                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Baris("Modal awal", shift.modalAwal.rupiah())
                Baris("Seharusnya di laci",
                    (shift.modalAwal + shift.totalTunai).rupiah())
                Baris("Uang fisik", shift.uangFisik.rupiah())

                Spacer(Modifier.height(4.dp))
                Row {
                    Text("Selisih", Modifier.weight(1f),
                        fontWeight = FontWeight.Bold)
                    Text(
                        (if (shift.selisih >= 0) "+ " else "- ") +
                            kotlin.math.abs(shift.selisih).rupiah(),
                        fontWeight = FontWeight.Bold, color = selisihWarna
                    )
                }

                if (shift.catatan.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Catatan: ${shift.catatan}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Tutup") }
        }
    )
}

@Composable
private fun Baris(label: String, value: String, color: Color = Color.Unspecified) {
    Row {
        Text(label, Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall)
        Text(value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface
            else color)
    }
}
