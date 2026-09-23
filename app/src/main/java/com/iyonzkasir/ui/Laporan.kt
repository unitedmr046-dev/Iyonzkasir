package com.iyonzkasir.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.iyonzkasir.*
import com.iyonzkasir.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

// ═══════════════════════════════════════════════════════════
// PERIODE
// ═══════════════════════════════════════════════════════════
enum class Periode(val id: String, val label: String) {
    HARI_INI("hari", "Hari Ini"),
    MINGGU("minggu", "7 Hari"),
    BULAN("bulan", "30 Hari"),
    SEMUA("semua", "Semua");

    companion object {
        fun fromId(id: String) = values().firstOrNull { it.id == id } ?: HARI_INI
    }
}

// ═══════════════════════════════════════════════════════════
// VIEWMODEL
// ═══════════════════════════════════════════════════════════
class LaporanViewModel(private val repo: PosRepository) : ViewModel() {
    var periode by mutableStateOf(Periode.HARI_INI)
        private set

    private val _labaList = MutableStateFlow<List<LabaProduk>>(emptyList())
    val labaList: StateFlow<List<LabaProduk>> = _labaList.asStateFlow()

    private val _grafikData = MutableStateFlow<List<HariPenjualan>>(emptyList())
    val grafikData: StateFlow<List<HariPenjualan>> = _grafikData.asStateFlow()

    private val _ringkasan = MutableStateFlow(Ringkasan())
    val ringkasan: StateFlow<Ringkasan> = _ringkasan.asStateFlow()

    data class Ringkasan(
        val totalTransaksi: Int = 0,
        val totalOmzet: Int = 0,
        val totalHpp: Int = 0,
        val totalLaba: Int = 0,
        val totalDiskon: Int = 0,
        val totalPajak: Int = 0
    ) {
        val marginPersen: Int
            get() = if (totalOmzet > 0) totalLaba * 100 / totalOmzet else 0
    }

    init { load() }

    fun gantiPeriode(p: Periode) {
        periode = p
        load()
    }

    private fun rangeFor(p: Periode): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startToday = cal.timeInMillis
        val endOfToday = startToday + 24L * 60 * 60 * 1000 - 1

        return when (p) {
            Periode.HARI_INI -> startToday to endOfToday
            Periode.MINGGU -> (startToday - 6L * 24 * 60 * 60 * 1000) to endOfToday
            Periode.BULAN -> (startToday - 29L * 24 * 60 * 60 * 1000) to endOfToday
            Periode.SEMUA -> 0L to endOfToday
        }
    }

    fun load() {
        viewModelScope.launch {
            val (start, end) = rangeFor(periode)

            // Laba per produk
            val laba = repo.labaPerProduk(start, end)
            _labaList.value = laba

            // Ringkasan
            val totalOmzet = laba.sumOf { it.totalOmzet }
            val totalHpp = laba.sumOf { it.totalHpp }
            val orders = repo.ordersInRange(start, end)
            _ringkasan.value = Ringkasan(
                totalTransaksi = orders.size,
                totalOmzet = totalOmzet,
                totalHpp = totalHpp,
                totalLaba = totalOmzet - totalHpp,
                totalDiskon = orders.sumOf { it.diskonAmount },
                totalPajak = orders.sumOf { it.pajakAmount }
            )

            // Grafik 7 hari terakhir (fix 7 hari, apapun periode)
            _grafikData.value = buildGrafik7Hari()
        }
    }

    private suspend fun buildGrafik7Hari(): List<HariPenjualan> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startToday = cal.timeInMillis
        val start = startToday - 6L * 24 * 60 * 60 * 1000
        val end = startToday + 24L * 60 * 60 * 1000 - 1

        val orders = repo.ordersInRange(start, end)
        val fmt = SimpleDateFormat("dd/MM", Locale("id"))

        // Group by day
        val map = mutableMapOf<Long, Pair<Int, Int>>() // dayStart -> (count, omzet)
        // init 7 hari kosong
        for (i in 0..6) {
            val dayStart = start + i * 24L * 60 * 60 * 1000
            map[dayStart] = 0 to 0
        }
        orders.forEach { o ->
            val c = Calendar.getInstance()
            c.timeInMillis = o.timestamp
            c.set(Calendar.HOUR_OF_DAY, 0)
            c.set(Calendar.MINUTE, 0)
            c.set(Calendar.SECOND, 0)
            c.set(Calendar.MILLISECOND, 0)
            val dayStart = c.timeInMillis
            val (prevCount, prevOmzet) = map[dayStart] ?: (0 to 0)
            map[dayStart] = (prevCount + 1) to (prevOmzet + o.total)
        }

        return map.entries.sortedBy { it.key }.map { (day, pair) ->
            HariPenjualan(
                label = fmt.format(Date(day)),
                omzet = pair.second,
                transaksi = pair.first
            )
        }
    }

    /** Bangun CSV dari data laba + order range. */
    suspend fun buildCsv(): String {
        val (start, end) = rangeFor(periode)
        val orders = repo.ordersInRange(start, end)
        val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("id"))
        val fmtDate = SimpleDateFormat("dd-MM-yyyy", Locale("id"))
        val sb = StringBuilder()

        // Header summary
        sb.append("Laporan iyonzkasir\n")
        sb.append("Periode,${periode.label}\n")
        sb.append("Tanggal Export,${fmtDate.format(Date())}\n")
        sb.append("\n")

        // Section 1: Ringkasan
        val r = _ringkasan.value
        sb.append("RINGKASAN\n")
        sb.append("Total Transaksi,${r.totalTransaksi}\n")
        sb.append("Total Omzet,${r.totalOmzet}\n")
        sb.append("Total HPP,${r.totalHpp}\n")
        sb.append("Total Laba,${r.totalLaba}\n")
        sb.append("Margin %,${r.marginPersen}\n")
        sb.append("Total Diskon,${r.totalDiskon}\n")
        sb.append("Total Pajak,${r.totalPajak}\n")
        sb.append("\n")

        // Section 2: Laba per Produk
        sb.append("LABA PER PRODUK\n")
        sb.append("Nama Menu,Qty,Omzet,HPP,Laba,Margin %\n")
        _labaList.value.forEach { p ->
            sb.append("${escCsv(p.namaMenu)},${p.totalQty},${p.totalOmzet},${p.totalHpp},${p.laba},${p.marginPersen}\n")
        }
        sb.append("\n")

        // Section 3: Detail Transaksi
        sb.append("DETAIL TRANSAKSI\n")
        sb.append("ID,Tanggal,Meja,Tipe,Kasir,Metode,Subtotal,Diskon,Pajak,Total\n")
        orders.forEach { o ->
            sb.append(
                "${o.id},${escCsv(fmt.format(Date(o.timestamp)))}," +
                "${escCsv(o.nomorMeja)},${escCsv(TipeOrder.fromId(o.tipeOrder).label)}," +
                "${escCsv(o.kasirNama)},${escCsv(PaymentMethod.fromId(o.metodeBayar).label)}," +
                "${o.subtotal},${o.diskonAmount},${o.pajakAmount},${o.total}\n"
            )
        }
        return sb.toString()
    }

    private fun escCsv(s: String): String {
        val needQuote = s.contains(',') || s.contains('"') || s.contains('\n')
        return if (needQuote) "\"${s.replace("\"", "\"\"")}\"" else s
    }
}

class LaporanVMFactory(private val repo: PosRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        LaporanViewModel(repo) as T
}

// ═══════════════════════════════════════════════════════════
// ROUTE
// ═══════════════════════════════════════════════════════════
@Composable
fun LaporanRoute(app: IyonzApp, nav: NavHostController) {
    val factory = remember { LaporanVMFactory(app.posRepo) }
    val vm: LaporanViewModel = viewModel(factory = factory)
    LaporanScreen(vm, nav)
}

// ═══════════════════════════════════════════════════════════
// SCREEN
// ═══════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LaporanScreen(vm: LaporanViewModel, nav: NavHostController) {
    val laba by vm.labaList.collectAsState()
    val grafik by vm.grafikData.collectAsState()
    val ringkas by vm.ringkasan.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var exportMsg by remember { mutableStateOf<String?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val csv = vm.buildCsv()
                    ctx.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(csv.toByteArray(Charsets.UTF_8))
                    }
                    exportMsg = "CSV berhasil disimpan ✅"
                } catch (e: Exception) {
                    exportMsg = "Gagal export: ${e.message}"
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Laporan & Laba") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val name = "iyonzkasir-${SimpleDateFormat("yyyyMMdd-HHmm", Locale("id")).format(Date())}.csv"
                        filePicker.launch(name)
                    }) {
                        Icon(Icons.Default.FileDownload, null)
                    }
                }
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Filter periode
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Periode.values().forEach { p ->
                        FilterChip(
                            selected = vm.periode == p,
                            onClick = { vm.gantiPeriode(p) },
                            label = { Text(p.label,
                                style = MaterialTheme.typography.bodySmall) }
                        )
                    }
                }
            }

            // Ringkasan
            item {
                Card(colors = CardDefaults.cardColors(containerColor = BRAND_LIGHT)) {
                    Column(Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Ringkasan", fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium)
                        RingkasRow("Transaksi", ringkas.totalTransaksi.toString())
                        RingkasRow("Omzet", ringkas.totalOmzet.rupiah())
                        RingkasRow("HPP (Modal)", "- " + ringkas.totalHpp.rupiah())
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        RingkasRow("LABA KOTOR", ringkas.totalLaba.rupiah(),
                            big = true, warna = BRAND)
                        Row {
                            Text("Margin", Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall)
                            Text("${ringkas.marginPersen}%",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodySmall)
                        }
                        if (ringkas.totalDiskon > 0)
                            RingkasRow("Diskon", "- " + ringkas.totalDiskon.rupiah())
                        if (ringkas.totalPajak > 0)
                            RingkasRow("PPN", ringkas.totalPajak.rupiah())
                    }
                }
            }

            // Grafik 7 hari
            item {
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text("Penjualan 7 Hari", fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(12.dp))
                        BarChart7Hari(grafik)
                    }
                }
            }

            // Laba per produk
            item {
                Text("Laba per Produk", fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp))
            }

            if (laba.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Box(Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                            Text("Belum ada data penjualan di periode ini",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                items(laba, key = { it.menuId }) { p ->
                    LabaRow(p)
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    exportMsg?.let {
        LaunchedEffect(it) {
            kotlinx.coroutines.delay(2500)
            exportMsg = null
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Surface(
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(it,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun RingkasRow(label: String, value: String,
                        big: Boolean = false, warna: Color = Color.Unspecified) {
    Row {
        Text(label, Modifier.weight(1f),
            style = if (big) MaterialTheme.typography.titleMedium
                    else MaterialTheme.typography.bodySmall,
            fontWeight = if (big) FontWeight.Bold else FontWeight.Normal)
        Text(value,
            style = if (big) MaterialTheme.typography.titleMedium
                    else MaterialTheme.typography.bodySmall,
            fontWeight = if (big) FontWeight.Bold else FontWeight.SemiBold,
            color = if (warna == Color.Unspecified) MaterialTheme.colorScheme.onSurface
                    else warna)
    }
}

@Composable
private fun LabaRow(p: LabaProduk) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(p.namaMenu, fontWeight = FontWeight.SemiBold)
                    Text("${p.totalQty} terjual • ${p.marginPersen}% margin",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(p.laba.rupiah(), fontWeight = FontWeight.Bold,
                    color = if (p.laba >= 0) SUCCESS else DANGER)
            }
            Spacer(Modifier.height(6.dp))
            Row {
                InfoKecil("Omzet", p.totalOmzet.rupiah())
                Spacer(Modifier.width(16.dp))
                InfoKecil("HPP", p.totalHpp.rupiah())
            }
        }
    }
}

@Composable
private fun InfoKecil(label: String, value: String) {
    Row {
        Text("$label: ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold)
    }
}

// ═══════════════════════════════════════════════════════════
// BAR CHART (Compose Canvas)
// ═══════════════════════════════════════════════════════════
@Composable
private fun BarChart7Hari(data: List<HariPenjualan>) {
    if (data.isEmpty()) {
        Text("Belum ada data", style = MaterialTheme.typography.bodySmall)
        return
    }
    val maxOmzet = data.maxOf { it.omzet }.coerceAtLeast(1)
    val barColor = BRAND
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column {
        Canvas(
            Modifier.fillMaxWidth().height(180.dp)
        ) {
            val paddingLeft = 8f
            val paddingBottom = 28f
            val chartH = size.height - paddingBottom
            val barW = (size.width - paddingLeft * 2) / (data.size * 2f - 1f)
            val gap = barW

            // Grid lines (3 garis)
            for (i in 0..3) {
                val y = chartH * i / 3f
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f
                )
            }

            // Bars
            data.forEachIndexed { idx, item ->
                val x = paddingLeft + idx * (barW + gap)
                val h = (item.omzet.toFloat() / maxOmzet) * (chartH - 8f)
                val y = chartH - h

                // Bar
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(x, y),
                    size = Size(barW, h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                )
            }
        }

        // Label tanggal di bawah
        Row(Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween) {
            data.forEach {
                Text(it.label, style = MaterialTheme.typography.labelSmall,
                    color = labelColor)
            }
        }

        // Info max
        Spacer(Modifier.height(8.dp))
        Text("Tertinggi: ${maxOmzet.rupiah()}",
            style = MaterialTheme.typography.labelSmall,
            color = labelColor)
    }
}
