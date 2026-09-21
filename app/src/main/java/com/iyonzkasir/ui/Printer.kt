package com.iyonzkasir.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import com.iyonzkasir.*
import com.iyonzkasir.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.lang.reflect.Method
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

// ═══════════════════════════════════════════════════════════
// ESC/POS COMMANDS
// ═══════════════════════════════════════════════════════════
object ESC {
    val INIT = byteArrayOf(0x1B, 0x40)
    val ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00)
    val ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01)
    val ALIGN_RIGHT = byteArrayOf(0x1B, 0x61, 0x02)
    val BOLD_ON = byteArrayOf(0x1B, 0x45, 0x01)
    val BOLD_OFF = byteArrayOf(0x1B, 0x45, 0x00)
    val SIZE_NORMAL = byteArrayOf(0x1D, 0x21, 0x00)
    val SIZE_DOUBLE = byteArrayOf(0x1D, 0x21, 0x11)
    val SIZE_BIG = byteArrayOf(0x1D, 0x21, 0x22)
    val FEED = byteArrayOf(0x0A)
    val CUT = byteArrayOf(0x1D, 0x56, 0x00)
    val DRAWER = byteArrayOf(0x1B, 0x70, 0x00, 0x19, 0xFA.toByte())
}

// ═══════════════════════════════════════════════════════════
// PRINTER SERVICE
// ═══════════════════════════════════════════════════════════
object PrinterService {
    private var socket: BluetoothSocket? = null
    private var output: OutputStream? = null
    private var connectedMac: String? = null

    var lastError: String? = null
        private set
    var lastStep: String = ""
        private set

    fun isConnected(): Boolean = socket?.isConnected == true

    private val STANDARD_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val ALT_UUIDS = listOf(
        "00001101-0000-1000-8000-0002EE000002",
        "00001110-0000-1000-8000-00805F9B34FB",
        "0000ff01-0000-1000-8000-00805F9B34FB",
        "e7810a71-73ae-499d-8c15-faa9aef0c3f2",
        "8ce255c0-200a-11e0-ac64-0800200c9a66"
    )

    suspend fun connect(context: Context, mac: String): Boolean = withContext(Dispatchers.IO) {
        try {
            disconnect()
            lastError = null
            lastStep = ""

            lastStep = "Cek Bluetooth"
            val adapter = getAdapter(context)
            if (adapter == null) {
                lastError = "Bluetooth tidak tersedia"
                return@withContext false
            }
            if (!adapter.isEnabled) {
                lastError = "Bluetooth belum dinyalakan"
                return@withContext false
            }

            lastStep = "Cek izin"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val granted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) {
                    lastError = "Izin Bluetooth belum diberikan"
                    return@withContext false
                }
            }

            lastStep = "Ambil device"
            val device = try {
                adapter.getRemoteDevice(mac)
            } catch (e: Exception) {
                lastError = "Alamat printer tidak valid"
                return@withContext false
            }

            lastStep = "Persiapan"
            try { adapter.cancelDiscovery() } catch (_: Exception) {}
            delay(500)

            // #1 Secure
            lastStep = "Connect #1 (secure)"
            var s = trySecure(device, STANDARD_UUID)
            if (s != null) return@withContext finalize(s, mac)

            delay(700)
            // #2 Secure retry
            lastStep = "Connect #2 (secure retry)"
            s = trySecure(device, STANDARD_UUID)
            if (s != null) return@withContext finalize(s, mac)

            delay(700)
            // #3 Insecure
            lastStep = "Connect #3 (insecure)"
            s = tryInsecure(device, STANDARD_UUID)
            if (s != null) return@withContext finalize(s, mac)

            // #4 Alternative UUIDs
            for ((i, u) in ALT_UUIDS.withIndex()) {
                delay(500)
                lastStep = "UUID alt #${i + 1}"
                s = trySecure(device, UUID.fromString(u))
                if (s != null) return@withContext finalize(s, mac)
                s = tryInsecure(device, UUID.fromString(u))
                if (s != null) return@withContext finalize(s, mac)
            }

            delay(500)
            // #5 Reflection channel 1
            lastStep = "Reflection channel 1"
            s = tryReflection(device, 1)
            if (s != null) return@withContext finalize(s, mac)

            lastError = "Semua metode gagal. Matikan printer 10 detik, nyalakan lagi."
            return@withContext false
        } catch (e: Exception) {
            lastError = "Error: ${e.message ?: e.javaClass.simpleName}"
            disconnect()
            return@withContext false
        }
    }

    private suspend fun finalize(s: BluetoothSocket, mac: String): Boolean {
        socket = s
        output = s.outputStream
        connectedMac = mac
        delay(800)
        lastStep = "Selesai"
        lastError = null
        return true
    }

    private fun trySecure(device: BluetoothDevice, uuid: UUID): BluetoothSocket? {
        return try {
            val s = device.createRfcommSocketToServiceRecord(uuid)
            s.connect()
            if (s.isConnected) s else {
                try { s.close() } catch (_: Exception) {}
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun tryInsecure(device: BluetoothDevice, uuid: UUID): BluetoothSocket? {
        return try {
            val m = device.javaClass.getMethod(
                "createInsecureRfcommSocketToServiceRecord", UUID::class.java
            )
            val s = m.invoke(device, uuid) as BluetoothSocket
            s.connect()
            if (s.isConnected) s else {
                try { s.close() } catch (_: Exception) {}
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun tryReflection(device: BluetoothDevice, channel: Int): BluetoothSocket? {
        return try {
            val m: Method = device.javaClass.getMethod(
                "createRfcommSocket", Int::class.javaPrimitiveType!!
            )
            val s = m.invoke(device, channel) as BluetoothSocket
            s.connect()
            if (s.isConnected) s else {
                try { s.close() } catch (_: Exception) {}
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun disconnect() {
        try { output?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        output = null; socket = null; connectedMac = null
    }

    suspend fun send(bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        try {
            if (socket?.isConnected != true) {
                lastError = "Printer terputus"
                return@withContext false
            }
            output?.write(bytes)
            output?.flush()
            lastError = null
            return@withContext true
        } catch (e: Exception) {
            lastError = "Kirim gagal: ${e.message}"
            return@withContext false
        }
    }

    suspend fun openDrawer(): Boolean = send(ESC.DRAWER)

    private fun getAdapter(context: Context): BluetoothAdapter? {
        val mgr = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return mgr?.adapter
    }
}

// ═══════════════════════════════════════════════════════════
// STRUK BUILDER
// ═══════════════════════════════════════════════════════════
class StrukBuilder(private val width: Int = 32) {
    private val out = mutableListOf<Byte>()
    private fun write(b: ByteArray) { b.forEach { out.add(it) } }
    private fun text(s: String) {
        val safe = s.map { c ->
            when {
                c.code in 32..126 -> c
                c == 'é' -> 'e'
                c == '•' -> '*'
                c == '—' -> '-'
                else -> if (c.code > 127) '?' else c
            }
        }.joinToString("")
        write(safe.toByteArray(Charsets.US_ASCII))
    }
    private fun line() = write((("-".repeat(width)) + "\n").toByteArray(Charsets.US_ASCII))
    private fun doubleLine() = write((("=".repeat(width)) + "\n").toByteArray(Charsets.US_ASCII))
    private fun nl() = write("\n".toByteArray())
    private fun padCenter(s: String): String {
        val t = s.take(width)
        if (t.length >= width) return t
        val left = (width - t.length) / 2
        return " ".repeat(left) + t
    }
    private fun padLR(left: String, right: String): String {
        val space = width - left.length - right.length
        return if (space > 0) left + " ".repeat(space) + right
        else left.take(width - right.length - 1) + " " + right
    }

    fun init(): StrukBuilder { write(ESC.INIT); return this }
    fun center(): StrukBuilder { write(ESC.ALIGN_CENTER); return this }
    fun left(): StrukBuilder { write(ESC.ALIGN_LEFT); return this }
    fun boldOn(): StrukBuilder { write(ESC.BOLD_ON); return this }
    fun boldOff(): StrukBuilder { write(ESC.BOLD_OFF); return this }
    fun bigText(): StrukBuilder { write(ESC.SIZE_DOUBLE); return this }
    fun normalText(): StrukBuilder { write(ESC.SIZE_NORMAL); return this }

    fun line(str: String): StrukBuilder { text(str); nl(); return this }
    fun centerLine(str: String): StrukBuilder { text(padCenter(str)); nl(); return this }
    fun lrLine(l: String, r: String): StrukBuilder { text(padLR(l, r)); nl(); return this }
    fun divider(): StrukBuilder { line(); return this }
    fun bigDivider(): StrukBuilder { doubleLine(); return this }
    fun feed(n: Int = 1): StrukBuilder { repeat(n) { nl() }; return this }
    fun cut(): StrukBuilder { feed(4); write(ESC.CUT); return this }
    fun build(): ByteArray = out.toByteArray()
}

// ═══════════════════════════════════════════════════════════
// STRUK TEXT BUILDER — SUSPEND (baca settings via repo)
// ═══════════════════════════════════════════════════════════
suspend fun buildStrukText(
    settingRepo: SettingRepository,
    order: Order,
    items: List<OrderItem>
): String {
    val width = if (settingRepo.getPaperWidth() == 80) 48 else 32
    val namaToko = settingRepo.getNamaToko()
    val alamat = settingRepo.getAlamatToko()
    val telepon = settingRepo.getTeleponToko()
    val footer = settingRepo.getFooterStruk()
    val fmt = NumberFormat.getNumberInstance(Locale("in", "ID"))
    fun rp(v: Int) = "Rp " + fmt.format(v)
    fun padCenter(s: String): String {
        val t = s.take(width)
        if (t.length >= width) return t
        return " ".repeat((width - t.length) / 2) + t
    }
    fun padLR(l: String, r: String): String {
        val sp = width - l.length - r.length
        return if (sp > 0) l + " ".repeat(sp) + r else "$l $r"
    }

    val sb = StringBuilder()
    sb.appendLine(padCenter(namaToko.uppercase()))
    if (alamat.isNotBlank()) sb.appendLine(padCenter(alamat))
    if (telepon.isNotBlank()) sb.appendLine(padCenter("Telp: $telepon"))
    sb.appendLine("-".repeat(width))
    sb.appendLine(SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("id")).format(Date(order.timestamp)))
    sb.appendLine(padLR("No: #${order.id}", "Kasir: ${order.kasirNama.take(12)}"))
    if (order.nomorMeja.isNotBlank()) sb.appendLine("Meja: ${order.nomorMeja}")
    sb.appendLine("Tipe: ${TipeOrder.fromId(order.tipeOrder).label}")
    if (order.memberNama.isNotBlank()) sb.appendLine("Member: ${order.memberNama}")
    else if (order.namaPelanggan.isNotBlank()) sb.appendLine("Pelanggan: ${order.namaPelanggan}")
    sb.appendLine("-".repeat(width))
    items.forEach { it ->
        sb.appendLine(it.namaMenu)
        sb.appendLine(padLR("  ${it.qty} x ${rp(it.hargaSatuan)}", rp(it.subtotal)))
        if (it.catatan.isNotBlank()) sb.appendLine("  * ${it.catatan}")
    }
    sb.appendLine("-".repeat(width))
    if (order.subtotal > 0) sb.appendLine(padLR("Subtotal", rp(order.subtotal)))
    if (order.diskonAmount > 0) sb.appendLine(padLR("Diskon", "-" + rp(order.diskonAmount)))
    if (order.voucherAmount > 0) sb.appendLine(padLR("Voucher", "-" + rp(order.voucherAmount)))
    if (order.pajakAmount > 0) sb.appendLine(padLR("PPN ${order.pajakPersen}%", rp(order.pajakAmount)))
    sb.appendLine(padLR("TOTAL", rp(order.total)))
    sb.appendLine(padLR(PaymentMethod.fromId(order.metodeBayar).label, rp(order.dibayar)))
    if (order.kembalian > 0) sb.appendLine(padLR("Kembali", rp(order.kembalian)))
    if (order.poinDidapat > 0) {
        sb.appendLine("-".repeat(width))
        sb.appendLine(padCenter("Poin didapat: +${order.poinDidapat}"))
    }
    sb.appendLine("-".repeat(width))
    sb.appendLine(padCenter(footer.ifBlank { "Terima kasih" }))
    sb.appendLine(padCenter("-- iyonzkasir --"))
    return sb.toString()
}

// ═══════════════════════════════════════════════════════════
// CETAK STRUK
// ═══════════════════════════════════════════════════════════
suspend fun cetakStruk(
    settingRepo: SettingRepository,
    order: Order,
    items: List<OrderItem>
): Boolean {
    val width = if (settingRepo.getPaperWidth() == 80) 48 else 32
    val namaToko = settingRepo.getNamaToko()
    val alamat = settingRepo.getAlamatToko()
    val telepon = settingRepo.getTeleponToko()
    val footer = settingRepo.getFooterStruk()
    val fmt = NumberFormat.getNumberInstance(Locale("in", "ID"))
    fun rp(v: Int) = "Rp " + fmt.format(v)

    val b = StrukBuilder(width).init()
    b.center().boldOn().bigText()
        .centerLine(namaToko.uppercase())
        .normalText().boldOff()
    if (alamat.isNotBlank()) b.centerLine(alamat)
    if (telepon.isNotBlank()) b.centerLine("Telp: $telepon")
    b.feed(1).divider().left()
    b.line(SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("id")).format(Date(order.timestamp)))
    b.lrLine("No: #${order.id}", "Kasir: ${order.kasirNama.take(12)}")
    if (order.nomorMeja.isNotBlank()) b.line("Meja: ${order.nomorMeja}")
    b.line("Tipe: ${TipeOrder.fromId(order.tipeOrder).label}")
    if (order.memberNama.isNotBlank()) b.line("Member: ${order.memberNama}")
    else if (order.namaPelanggan.isNotBlank()) b.line("Pelanggan: ${order.namaPelanggan}")
    b.divider()
    items.forEach { it ->
        b.line(it.namaMenu)
        b.lrLine("  ${it.qty} x ${rp(it.hargaSatuan)}", rp(it.subtotal))
        if (it.catatan.isNotBlank()) b.line("  * ${it.catatan}")
    }
    b.divider()
    if (order.subtotal > 0) b.lrLine("Subtotal", rp(order.subtotal))
    if (order.diskonAmount > 0) b.lrLine("Diskon", "-" + rp(order.diskonAmount))
    if (order.voucherAmount > 0) b.lrLine("Voucher", "-" + rp(order.voucherAmount))
    if (order.pajakAmount > 0) b.lrLine("PPN ${order.pajakPersen}%", rp(order.pajakAmount))
    b.boldOn().bigText()
        .lrLine("TOTAL", rp(order.total))
        .normalText().boldOff()
    b.lrLine(PaymentMethod.fromId(order.metodeBayar).label, rp(order.dibayar))
    if (order.kembalian > 0) b.lrLine("Kembali", rp(order.kembalian))
    if (order.poinDidapat > 0) {
        b.divider()
        b.centerLine("Poin didapat: +${order.poinDidapat}")
    }
    b.feed(1).divider()
    b.centerLine(footer.ifBlank { "Terima kasih" })
    b.centerLine("-- iyonzkasir --")
    b.cut()
    return PrinterService.send(b.build())
}

// ═══════════════════════════════════════════════════════════
// CETAK LAPORAN SHIFT
// ═══════════════════════════════════════════════════════════
suspend fun cetakLaporanShift(
    settingRepo: SettingRepository,
    shift: Shift
): Boolean {
    val width = if (settingRepo.getPaperWidth() == 80) 48 else 32
    val namaToko = settingRepo.getNamaToko()
    val fmt = NumberFormat.getNumberInstance(Locale("in", "ID"))
    fun rp(v: Int) = "Rp " + fmt.format(v)

    val b = StrukBuilder(width).init()
    b.center().boldOn().bigText()
        .centerLine("LAPORAN SHIFT")
        .normalText().boldOff()
        .centerLine(namaToko)
        .centerLine("#${shift.id} • ${shift.kasirNama}")
    b.feed(1).divider().left()
    b.lrLine("Buka", SimpleDateFormat("dd/MM HH:mm", Locale("id"))
        .format(Date(shift.bukaTimestamp)))
    b.lrLine("Tutup", SimpleDateFormat("dd/MM HH:mm", Locale("id"))
        .format(Date(shift.tutupTimestamp)))
    b.divider()
    b.lrLine("Transaksi", shift.totalTransaksi.toString())
    b.lrLine("Omzet", rp(shift.totalOmzet))
    b.lrLine("Tunai", rp(shift.totalTunai))
    b.lrLine("Non-tunai", rp(shift.totalNonTunai))
    if (shift.totalDiskon > 0) b.lrLine("Diskon", "-" + rp(shift.totalDiskon))
    if (shift.totalPajak > 0) b.lrLine("PPN", rp(shift.totalPajak))
    b.divider()
    b.lrLine("Modal awal", rp(shift.modalAwal))
    b.lrLine("Harusnya", rp(shift.modalAwal + shift.totalTunai))
    b.lrLine("Uang fisik", rp(shift.uangFisik))
    b.boldOn().lrLine("Selisih",
        (if (shift.selisih >= 0) "+ " else "- ") + rp(kotlin.math.abs(shift.selisih)))
        .boldOff()
    if (shift.catatan.isNotBlank()) {
        b.divider()
        b.line("Catatan: ${shift.catatan}")
    }
    b.feed(2).centerLine("-- iyonzkasir --").cut()
    return PrinterService.send(b.build())
}

// ═══════════════════════════════════════════════════════════
// CETAK TEST
// ═══════════════════════════════════════════════════════════
suspend fun cetakTest(settingRepo: SettingRepository): Boolean {
    val width = if (settingRepo.getPaperWidth() == 80) 48 else 32
    val b = StrukBuilder(width).init()
    b.center().boldOn().bigText()
        .centerLine("TEST PRINT")
        .normalText().boldOff()
        .centerLine(settingRepo.getNamaToko())
        .feed(1).divider().left()
        .line("Lebar kertas: ${width}mm")
        .line("Waktu: ${SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("id")).format(Date())}")
        .line("Status: OK")
        .divider()
        .centerLine("Printer siap digunakan")
        .feed(3).cut()
    return PrinterService.send(b.build())
}

// ═══════════════════════════════════════════════════════════
// SHARE
// ═══════════════════════════════════════════════════════════
fun shareStrukText(context: Context, strukText: String) {
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, strukText)
        }
        context.startActivity(Intent.createChooser(intent, "Bagikan struk ke..."))
    } catch (_: Exception) {}
}

fun shareKeWhatsApp(context: Context, noTelepon: String, strukText: String) {
    try {
        val cleaned = noTelepon.replace("+", "").replace("-", "").replace(" ", "")
        val nomor = if (cleaned.startsWith("0")) "62${cleaned.drop(1)}"
        else if (cleaned.startsWith("62")) cleaned else "62$cleaned"
        val url = "https://wa.me/$nomor?text=${Uri.encode(strukText)}"
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: Exception) {
        shareStrukText(context, strukText)
    }
}

// ═══════════════════════════════════════════════════════════
// PREVIEW STRUK DIALOG
// ═══════════════════════════════════════════════════════════
@Composable
fun StrukPreviewDialog(
    strukText: String,
    onPrint: (() -> Unit)? = null,
    onShareWa: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Receipt, null, tint = BRAND)
                Spacer(Modifier.width(8.dp))
                Text("Preview Struk")
            }
        },
        text = {
            Column(Modifier.heightIn(max = 500.dp)) {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Box(
                        Modifier.fillMaxWidth().padding(8.dp)
                            .horizontalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = strukText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Color.Black,
                            lineHeight = 14.sp
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onPrint != null) {
                        Button(
                            onClick = onPrint,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = BRAND)
                        ) {
                            Icon(Icons.Default.Print, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Cetak", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (onShareWa != null) {
                        OutlinedButton(
                            onClick = onShareWa,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Bagikan", style = MaterialTheme.typography.bodySmall)
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

// ═══════════════════════════════════════════════════════════
// PRINTER SETTINGS UI
// ═══════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrinterRoute(app: IyonzApp, nav: NavHostController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var printerMac by remember { mutableStateOf("") }
    var printerNama by remember { mutableStateOf("") }
    var paperWidth by remember { mutableIntStateOf(58) }
    var autoPrint by remember { mutableStateOf(false) }
    var connected by remember { mutableStateOf(PrinterService.isConnected()) }
    var message by remember { mutableStateOf<String?>(null) }
    var showPairList by remember { mutableStateOf(false) }
    var connecting by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        printerMac = app.settingRepo.getPrinterMac()
        printerNama = app.settingRepo.getPrinterNama()
        paperWidth = app.settingRepo.getPaperWidth()
        autoPrint = app.settingRepo.isAutoPrint()
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) showPairList = true
        else message = "Izin Bluetooth ditolak"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Printer Thermal") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(16.dp)
            .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (connected) BRAND_LIGHT
                    else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (connected) Icons.Default.BluetoothConnected
                        else Icons.Default.BluetoothDisabled,
                        null, tint = if (connected) BRAND
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (connecting) "Menghubungkan..."
                            else if (connected) "Terhubung"
                            else if (printerMac.isBlank()) "Belum ada printer"
                            else "Terputus",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            printerNama.ifBlank { "Pilih printer Bluetooth" },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val needPerms = mutableListOf<String>()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            if (ContextCompat.checkSelfPermission(ctx,
                                    Manifest.permission.BLUETOOTH_CONNECT)
                                != PackageManager.PERMISSION_GRANTED) {
                                needPerms.add(Manifest.permission.BLUETOOTH_CONNECT)
                            }
                            if (ContextCompat.checkSelfPermission(ctx,
                                    Manifest.permission.BLUETOOTH_SCAN)
                                != PackageManager.PERMISSION_GRANTED) {
                                needPerms.add(Manifest.permission.BLUETOOTH_SCAN)
                            }
                        }
                        if (needPerms.isEmpty()) showPairList = true
                        else permLauncher.launch(needPerms.toTypedArray())
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = BRAND)
                ) {
                    Icon(Icons.Default.Search, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Pilih Printer")
                }

                if (printerMac.isNotBlank()) {
                    OutlinedButton(
                        onClick = {
                            if (connecting) {
                                PrinterService.disconnect()
                                connecting = false
                                connected = false
                                message = "Dibatalkan"
                            } else if (connected) {
                                PrinterService.disconnect()
                                connected = false
                                message = "Terputus"
                            } else {
                                scope.launch {
                                    connecting = true
                                    connected = false
                                    message = "Menghubungkan... mohon tunggu"

                                    val timeoutJob = launch {
                                        delay(45_000)
                                        if (connecting) {
                                            PrinterService.disconnect()
                                            connecting = false
                                            message = "❌ Timeout 45 detik. Coba lagi."
                                        }
                                    }
                                    val ok = PrinterService.connect(ctx, printerMac)
                                    timeoutJob.cancel()
                                    connecting = false
                                    connected = ok
                                    message = if (ok) "✅ Terhubung ke printer"
                                    else "❌ ${PrinterService.lastError ?: "Gagal"} (step: ${PrinterService.lastStep})"
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        if (connecting) {
                            CircularProgressIndicator(
                                Modifier.size(16.dp), strokeWidth = 2.dp, color = BRAND)
                            Spacer(Modifier.width(6.dp))
                            Text("Batal")
                        } else if (connected) {
                            Icon(Icons.Default.Close, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Putus")
                        } else {
                            Icon(Icons.Default.Link, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Sambung")
                        }
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("💡 Tips Connect Printer",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall)
                    Text("1. Printer NYALA + kertas terpasang",
                        style = MaterialTheme.typography.labelSmall)
                    Text("2. Sudah di-pair di Setelan HP (PIN 0000/1234)",
                        style = MaterialTheme.typography.labelSmall)
                    Text("3. Tidak terhubung ke HP/aplikasi lain (tutup RawBT dulu)",
                        style = MaterialTheme.typography.labelSmall)
                    Text("4. Kalau gagal → matikan printer 10 detik, nyalakan lagi",
                        style = MaterialTheme.typography.labelSmall)
                    Text("5. HP Xiaomi/Realme: aktifkan 'Nearby devices' di izin app",
                        style = MaterialTheme.typography.labelSmall)
                }
            }

            Text("Ukuran Kertas", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(58, 80).forEach { w ->
                    FilterChip(
                        selected = paperWidth == w,
                        onClick = {
                            paperWidth = w
                            scope.launch { app.settingRepo.setPaperWidth(w) }
                        },
                        label = { Text("${w}mm") }
                    )
                }
            }

            Card {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Auto Print Struk", fontWeight = FontWeight.SemiBold)
                        Text("Cetak otomatis setelah bayar",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = autoPrint,
                        onCheckedChange = {
                            autoPrint = it
                            scope.launch { app.settingRepo.setAutoPrint(it) }
                        }
                    )
                }
            }

            OutlinedButton(
                onClick = {
                    scope.launch {
                        if (!PrinterService.isConnected()) {
                            message = "Sambungkan printer dulu"
                            return@launch
                        }
                        val ok = cetakTest(app.settingRepo)
                        message = if (ok) "✅ Test print terkirim"
                        else "❌ ${PrinterService.lastError ?: "Gagal cetak"}"
                    }
                },
                enabled = printerMac.isNotBlank() && connected,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Print, null)
                Spacer(Modifier.width(6.dp))
                Text("Test Print")
            }

            if (FeatureManager.isEnabled(FeatureKey.CASH_DRAWER)) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val ok = PrinterService.openDrawer()
                            message = if (ok) "Sinyal drawer terkirim"
                            else PrinterService.lastError
                        }
                    },
                    enabled = printerMac.isNotBlank() && connected,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PointOfSale, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Buka Cash Drawer")
                }
            }

            message?.let {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            it.startsWith("✅") || it.startsWith("Sinyal") -> BRAND_LIGHT
                            it.startsWith("❌") || it.contains("Gagal") || it.contains("Timeout") ->
                                MaterialTheme.colorScheme.errorContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Text(it, modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (it.startsWith("❌") || it.contains("Gagal") || it.contains("Timeout"))
                            MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }

    if (showPairList) {
        PairPickerDialog(
            onDismiss = { showPairList = false },
            onPicked = { device ->
                printerMac = device.address
                printerNama = device.name ?: "Printer"
                scope.launch {
                    app.settingRepo.setPrinterMac(device.address)
                    app.settingRepo.setPrinterNama(printerNama)
                    connecting = true
                    message = "Menghubungkan ke ${printerNama}..."

                    val timeoutJob = launch {
                        delay(45_000)
                        if (connecting) {
                            PrinterService.disconnect()
                            connecting = false
                            message = "❌ Timeout 45 detik. Coba lagi."
                        }
                    }
                    val ok = PrinterService.connect(ctx, device.address)
                    timeoutJob.cancel()
                    connecting = false
                    connected = ok
                    message = if (ok) "✅ Terhubung ke $printerNama"
                    else "❌ ${PrinterService.lastError ?: "Gagal connect"} (step: ${PrinterService.lastStep})"
                }
                showPairList = false
            }
        )
    }
}

@Composable
private fun PairPickerDialog(
    onDismiss: () -> Unit,
    onPicked: (BluetoothDevice) -> Unit
) {
    val ctx = LocalContext.current
    var devices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val mgr = ctx.getSystemService(Context.BLUETOOTH_SERVICE)
                    as? android.bluetooth.BluetoothManager
            val adapter = mgr?.adapter
            if (adapter == null) {
                error = "Bluetooth tidak tersedia"
                return@LaunchedEffect
            }
            if (!adapter.isEnabled) {
                error = "Nyalakan Bluetooth dulu"
                return@LaunchedEffect
            }
            devices = adapter.bondedDevices.toList().sortedBy { it.name ?: "" }
            if (devices.isEmpty())
                error = "Belum ada perangkat yang di-pair.\nBuka Setelan HP → Bluetooth → Pair printer dulu."
        } catch (e: SecurityException) {
            error = "Izin Bluetooth ditolak"
        } catch (e: Exception) {
            error = "Error: ${e.message}"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pilih Printer") },
        text = {
            Column(Modifier.heightIn(max = 400.dp)) {
                if (error != null) {
                    Text(error!!, color = DANGER,
                        style = MaterialTheme.typography.bodySmall)
                } else if (devices.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    Text("Pilih dari perangkat yang sudah di-pair:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(devices, key = { it.address }) { d ->
                            Card(Modifier.fillMaxWidth().clickable { onPicked(d) }) {
                                Row(Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Print, null, tint = BRAND)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(d.name ?: "Unknown",
                                            fontWeight = FontWeight.SemiBold)
                                        Text(d.address,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
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
}
