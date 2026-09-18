package com.iyonzkasir

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

// ═══════ BRAND & WARNA ═══════
val BRAND = Color(0xFFFF6B35)
val BRAND_LIGHT = Color(0xFFFFE4D6)
val BRAND_DARK = Color(0xFFC44E1F)
val SUCCESS = Color(0xFF2E7D32)
val WARNING = Color(0xFFF57C00)
val DANGER = Color(0xFFD32F2F)

// ═══════ EXTENSIONS ═══════
fun Int.rupiah(): String =
    "Rp " + NumberFormat.getNumberInstance(Locale("in", "ID")).format(this)

fun Long.tanggal(): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("in", "ID")).format(Date(this))

fun Long.tanggalPendek(): String =
    SimpleDateFormat("dd/MM/yyyy", Locale("in", "ID")).format(Date(this))

fun Long.jamPendek(): String =
    SimpleDateFormat("HH:mm", Locale("in", "ID")).format(Date(this))

// ═══════ ENUMS ═══════
enum class UserRole(val id: String, val label: String, val pinMin: Int) {
    OWNER("owner", "Pemilik", 6),
    SUPERVISOR("supervisor", "Supervisor", 4),
    KASIR("kasir", "Kasir", 4);

    companion object {
        fun fromId(id: String) = values().firstOrNull { it.id == id } ?: KASIR
    }
}

enum class BusinessType(
    val id: String, val label: String, val emoji: String, val deskripsi: String
) {
    WARUNG("warung", "Warung / Retail", "🏪", "Jualan harian, cepat & simpel"),
    RETAIL("retail", "Retail Lengkap", "🛒", "Barcode, grosir, supplier"),
    FNB("fnb", "F&B / Cafe / Resto", "🍽️", "Nomor meja, kitchen print"),
    LAUNDRY("laundry", "Laundry", "👕", "Status tracking, DP, estimasi"),
    JASA("jasa", "Jasa Umum", "🔧", "Jadwal servis, reminder");

    companion object {
        fun fromId(id: String) = values().firstOrNull { it.id == id } ?: WARUNG
    }
}

enum class TipeOrder(val id: String, val label: String) {
    DINE_IN("DINE_IN", "Dine-in"),
    TAKE_AWAY("TAKE_AWAY", "Take-away"),
    DELIVERY("DELIVERY", "Delivery"),
    PICKUP("PICKUP", "Pickup");

    companion object {
        fun fromId(id: String) = values().firstOrNull { it.id == id } ?: DINE_IN
    }
}

enum class OrderStatus(val id: String, val label: String) {
    OPEN("OPEN", "Open Bill"),
    PAID("PAID", "Lunas"),
    VOID("VOID", "Dibatalkan"),
    REFUND("REFUND", "Refund");

    companion object {
        fun fromId(id: String) = values().firstOrNull { it.id == id } ?: PAID
    }
}

enum class PaymentMethod(val id: String, val label: String) {
    CASH("CASH", "Tunai"),
    QRIS("QRIS", "QRIS"),
    DEBIT("DEBIT", "Debit"),
    EWALLET("EWALLET", "E-Wallet"),
    TRANSFER("TRANSFER", "Transfer"),
    HUTANG("HUTANG", "Hutang");

    companion object {
        fun fromId(id: String) = values().firstOrNull { it.id == id } ?: CASH
    }
}

// ═══════ FEATURE KEYS ═══════
enum class FeatureKey(
    val key: String, val label: String, val kategori: String, val deskripsi: String = ""
) {
    // POS
    NOMOR_MEJA("pos_nomor_meja", "Nomor Meja", "POS", "Kelola nomor meja untuk F&B"),
    KITCHEN_PRINT("pos_kitchen_print", "Kitchen Print", "POS", "Cetak pesanan ke dapur"),
    MODIFIER("pos_modifier", "Catatan / Modifier Item", "POS", "Catatan per item"),
    SPLIT_BILL("pos_split_bill", "Split Bill", "POS", "Pisah tagihan"),
    MERGE_BILL("pos_merge_bill", "Merge Bill", "POS", "Gabung tagihan"),
    OPEN_BILL("pos_open_bill", "Open Bill", "POS", "Simpan pesanan, bayar nanti"),
    BARCODE("pos_barcode", "Barcode Scanner", "POS", "Scan barcode produk"),
    HARGA_GROSIR("pos_harga_grosir", "Harga Grosir", "POS", "Harga khusus pembelian banyak"),
    DISKON("pos_diskon", "Diskon", "POS", "Diskon nominal atau persen"),
    PAJAK("pos_pajak", "Pajak / PPN", "POS", "Hitung pajak otomatis"),
    SERVICE_CHARGE("pos_service_charge", "Service Charge", "POS", "Biaya layanan"),
    TIP("pos_tip", "Tip", "POS", "Tip untuk pelayan"),
    VOID_REFUND("pos_void_refund", "Void / Refund", "POS", "Batalkan transaksi"),
    HOLD_ORDER("pos_hold_order", "Hold Order", "POS", "Tahan pesanan sementara"),

    // Inventaris
    POTONG_STOK("inv_potong_stok", "Potong Stok Otomatis", "Inventaris", "Kurangi stok saat jual"),
    LOW_STOCK_ALERT("inv_low_stock", "Alert Stok Menipis", "Inventaris", "Notifikasi stok minim"),
    RESEP("inv_resep", "Resep / Bahan Baku", "Inventaris", "Potong bahan saat jual"),
    BATCH_EXPIRED("inv_batch_expired", "Batch & Expired", "Inventaris", "Track batch & expired"),
    SUPPLIER("inv_supplier", "Database Supplier", "Inventaris", "Kelola supplier"),
    PURCHASE_ORDER("inv_purchase_order", "Purchase Order", "Inventaris", "Order ke supplier"),
    MULTI_GUDANG("inv_multi_gudang", "Multi Gudang", "Inventaris", "Stok di beberapa lokasi"),

    // Laporan
    LAPORAN_HARIAN("rep_harian", "Laporan Harian", "Laporan", "Ringkasan penjualan"),
    LABA_PER_PRODUK("rep_laba_produk", "Laba per Produk", "Laporan", "Analisa margin"),
    SHIFT_KASIR("rep_shift", "Shift Kasir", "Laporan", "Buka & tutup shift"),
    LACI_KASIR("rep_laci_kasir", "Manajemen Laci Kasir", "Laporan", "Hitung uang fisik"),
    EXPORT_EXCEL("rep_export_excel", "Export Excel / CSV", "Laporan", "Export ke Excel"),
    EXPORT_PDF("rep_export_pdf", "Export PDF", "Laporan", "Cetak PDF"),

    // CRM
    MEMBER("crm_member", "Member & Poin", "CRM", "Sistem membership"),
    VOUCHER("crm_voucher", "Voucher", "CRM", "Kode voucher"),
    HUTANG_PELANGGAN("crm_hutang_pelanggan", "Hutang Pelanggan", "CRM", "Catat hutang"),
    HUTANG_SUPPLIER("crm_hutang_supplier", "Hutang Supplier", "CRM", "Hutang ke supplier"),

    // Hardware
    PRINTER_BT("hw_printer_bt", "Printer Bluetooth", "Hardware", "Cetak struk via BT"),
    PRINTER_USB("hw_printer_usb", "Printer USB", "Hardware", "Cetak struk via USB"),
    CASH_DRAWER("hw_cash_drawer", "Cash Drawer", "Hardware", "Buka laci otomatis"),
    SCANNER_HW("hw_scanner", "Scanner Hardware", "Hardware", "Scanner barcode fisik"),

    // F&B / Laundry / Jasa
    STATUS_DAPUR("fnb_status_dapur", "Status Dapur", "F&B", "Track status pesanan"),
    STATUS_LAUNDRY("laundry_status", "Status Laundry", "Laundry", "Tracking cucian"),
    ESTIMASI_SELESAI("laundry_estimasi", "Estimasi Selesai", "Laundry", "Tanggal selesai"),
    DP_PEMBAYARAN("laundry_dp", "DP / Down Payment", "Laundry", "Terima DP"),
    PICKUP_DELIVERY("laundry_pickup", "Pickup / Delivery", "Laundry", "Antar jemput"),
    JADWAL_SERVIS("jasa_jadwal", "Jadwal Servis", "Jasa", "Atur jadwal"),
    REMINDER("jasa_reminder", "Reminder Berkala", "Jasa", "Ingatkan pelanggan"),

    // Backup & Komunikasi & Keamanan
    BACKUP_MANUAL("bk_manual", "Backup Manual", "Backup", "Backup ke storage"),
    BACKUP_OTOMATIS("bk_auto", "Backup Otomatis", "Backup", "Backup terjadwal"),
    BACKUP_CLOUD("bk_cloud", "Backup Cloud", "Backup", "Backup ke cloud"),
    WHATSAPP_INTENT("comm_wa", "Kirim via WhatsApp", "Komunikasi", "Kirim nota via WA"),
    AUDIT_LOG("sec_audit_log", "Audit Log", "Keamanan", "Catat aktivitas penting");

    companion object {
        fun fromKey(k: String) = values().firstOrNull { it.key == k }
        fun byKategori(): Map<String, List<FeatureKey>> = values().groupBy { it.kategori }
    }
}

// Preset feature per jenis usaha
val BusinessType.defaultFeatures: Set<FeatureKey>
    get() = when (this) {
        BusinessType.WARUNG -> setOf(
            FeatureKey.LAPORAN_HARIAN, FeatureKey.PRINTER_BT
        )
        BusinessType.RETAIL -> setOf(
            FeatureKey.BARCODE, FeatureKey.HARGA_GROSIR, FeatureKey.POTONG_STOK,
            FeatureKey.LOW_STOCK_ALERT, FeatureKey.BATCH_EXPIRED, FeatureKey.SUPPLIER,
            FeatureKey.PURCHASE_ORDER, FeatureKey.HUTANG_SUPPLIER, FeatureKey.DISKON,
            FeatureKey.VOID_REFUND, FeatureKey.HOLD_ORDER, FeatureKey.LAPORAN_HARIAN,
            FeatureKey.LABA_PER_PRODUK, FeatureKey.PRINTER_BT, FeatureKey.EXPORT_EXCEL
        )
        BusinessType.FNB -> setOf(
            FeatureKey.NOMOR_MEJA, FeatureKey.KITCHEN_PRINT, FeatureKey.MODIFIER,
            FeatureKey.SPLIT_BILL, FeatureKey.MERGE_BILL, FeatureKey.OPEN_BILL,
            FeatureKey.DISKON, FeatureKey.SERVICE_CHARGE, FeatureKey.TIP,
            FeatureKey.VOID_REFUND, FeatureKey.HOLD_ORDER, FeatureKey.STATUS_DAPUR,
            FeatureKey.RESEP, FeatureKey.LAPORAN_HARIAN, FeatureKey.LABA_PER_PRODUK,
            FeatureKey.PRINTER_BT, FeatureKey.SHIFT_KASIR
        )
        BusinessType.LAUNDRY -> setOf(
            FeatureKey.STATUS_LAUNDRY, FeatureKey.ESTIMASI_SELESAI,
            FeatureKey.DP_PEMBAYARAN, FeatureKey.PICKUP_DELIVERY, FeatureKey.OPEN_BILL,
            FeatureKey.WHATSAPP_INTENT, FeatureKey.LAPORAN_HARIAN, FeatureKey.PRINTER_BT
        )
        BusinessType.JASA -> setOf(
            FeatureKey.JADWAL_SERVIS, FeatureKey.REMINDER, FeatureKey.WHATSAPP_INTENT,
            FeatureKey.LAPORAN_HARIAN, FeatureKey.PRINTER_BT
        )
    }

// ═══════ FEATURE MANAGER ═══════
object FeatureManager {
    private val _enabled = MutableStateFlow<Set<FeatureKey>>(emptySet())
    val enabled: StateFlow<Set<FeatureKey>> = _enabled.asStateFlow()

    fun isEnabled(feature: FeatureKey): Boolean = _enabled.value.contains(feature)
    fun update(features: Set<FeatureKey>) { _enabled.value = features }
}

// ═══════ TEMA ═══════
@Composable
fun IyonzTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) {
        darkColorScheme(
            primary = BRAND, onPrimary = Color.White,
            secondary = BRAND_DARK, surfaceVariant = Color(0xFF2A2A2A)
        )
    } else {
        lightColorScheme(
            primary = BRAND, onPrimary = Color.White,
            secondary = BRAND_DARK, surfaceVariant = Color(0xFFF5F5F5)
        )
    }
    MaterialTheme(colorScheme = colors, content = content)
}
