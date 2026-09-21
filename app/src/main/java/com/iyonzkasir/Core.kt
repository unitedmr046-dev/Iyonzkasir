package com.iyonzkasir

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

// ═══════ TEMA MODE ═══════
enum class ThemeMode(val id: String, val label: String) {
    SYSTEM("system", "Ikut Sistem"),
    LIGHT("light", "Terang"),
    DARK("dark", "Gelap");

    companion object {
        fun fromId(id: String) = values().firstOrNull { it.id == id } ?: SYSTEM
    }
}

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
    RETAIL("retail", "Retail Lengkap", "🛒", "Barcode, grosir, potong stok"),
    FNB("fnb", "F&B / Cafe / Resto", "🍽️", "Nomor meja, kitchen print, modifier"),
    LAUNDRY("laundry", "Laundry", "👕", "Status tracking, DP, estimasi"),
    TOKO_BANGUNAN("bangunan", "Toko Bangunan", "🏗️", "Grosir, satuan, retur"),
    JASA("jasa", "Jasa Umum", "🔧", "Jadwal servis, reminder"),
    CUSTOM("custom", "Custom", "⚙️", "Atur sendiri fitur yang mau dipakai");

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
    VOID_REFUND("pos_void_refund", "Void / Refund", "POS", "Batalkan transaksi"),
    HOLD_ORDER("pos_hold_order", "Hold Order", "POS", "Tahan pesanan sementara"),

    // Inventaris
    POTONG_STOK("inv_potong_stok", "Potong Stok Otomatis", "Inventaris", "Kurangi stok saat jual"),
    LOW_STOCK_ALERT("inv_low_stock", "Alert Stok Menipis", "Inventaris", "Notifikasi stok minim"),
    RESEP("inv_resep", "Resep / Bahan Baku", "Inventaris", "Potong bahan saat jual"),
    PURCHASE_ORDER("inv_purchase_order", "Purchase Order", "Inventaris", "Order ke supplier"),

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
            FeatureKey.OPEN_BILL, FeatureKey.DISKON, FeatureKey.VOID_REFUND,
            FeatureKey.LAPORAN_HARIAN, FeatureKey.PRINTER_BT
        )
        BusinessType.RETAIL -> setOf(
            FeatureKey.BARCODE, FeatureKey.HARGA_GROSIR, FeatureKey.POTONG_STOK,
            FeatureKey.LOW_STOCK_ALERT, FeatureKey.PURCHASE_ORDER, FeatureKey.HUTANG_SUPPLIER,
            FeatureKey.DISKON, FeatureKey.VOID_REFUND, FeatureKey.HOLD_ORDER,
            FeatureKey.LAPORAN_HARIAN, FeatureKey.LABA_PER_PRODUK, FeatureKey.PRINTER_BT,
            FeatureKey.EXPORT_EXCEL
        )
        BusinessType.FNB -> setOf(
            FeatureKey.NOMOR_MEJA, FeatureKey.KITCHEN_PRINT, FeatureKey.MODIFIER,
            FeatureKey.SPLIT_BILL, FeatureKey.MERGE_BILL, FeatureKey.OPEN_BILL,
            FeatureKey.DISKON, FeatureKey.PAJAK, FeatureKey.VOID_REFUND,
            FeatureKey.HOLD_ORDER, FeatureKey.STATUS_DAPUR, FeatureKey.RESEP,
            FeatureKey.LAPORAN_HARIAN, FeatureKey.LABA_PER_PRODUK,
            FeatureKey.PRINTER_BT, FeatureKey.SHIFT_KASIR
        )
        BusinessType.LAUNDRY -> setOf(
            FeatureKey.STATUS_LAUNDRY, FeatureKey.ESTIMASI_SELESAI,
            FeatureKey.DP_PEMBAYARAN, FeatureKey.PICKUP_DELIVERY, FeatureKey.OPEN_BILL,
            FeatureKey.WHATSAPP_INTENT, FeatureKey.LAPORAN_HARIAN, FeatureKey.PRINTER_BT
        )
        BusinessType.TOKO_BANGUNAN -> setOf(
            FeatureKey.BARCODE, FeatureKey.HARGA_GROSIR, FeatureKey.POTONG_STOK,
            FeatureKey.LOW_STOCK_ALERT, FeatureKey.PURCHASE_ORDER, FeatureKey.HUTANG_SUPPLIER,
            FeatureKey.DISKON, FeatureKey.VOID_REFUND, FeatureKey.HOLD_ORDER,
            FeatureKey.OPEN_BILL, FeatureKey.LAPORAN_HARIAN, FeatureKey.LABA_PER_PRODUK,
            FeatureKey.EXPORT_EXCEL, FeatureKey.PRINTER_BT, FeatureKey.SHIFT_KASIR
        )
        BusinessType.JASA -> setOf(
            FeatureKey.JADWAL_SERVIS, FeatureKey.REMINDER, FeatureKey.WHATSAPP_INTENT,
            FeatureKey.LAPORAN_HARIAN, FeatureKey.PRINTER_BT
        )
        BusinessType.CUSTOM -> emptySet()
    }

// ═══════ PERMISSION KEYS (izin granular per-user) ═══════
enum class PermissionKey(
    val key: String, val label: String, val kategori: String, val deskripsi: String = ""
) {
    LIHAT_DASHBOARD("perm_dashboard", "Lihat Dashboard", "Umum", "Akses ringkasan penjualan"),
    LIHAT_RIWAYAT("perm_riwayat", "Lihat Riwayat", "Umum", "Lihat transaksi masa lalu"),
    BUKA_SETELAN("perm_setelan", "Buka Setelan", "Umum", "Akses menu pengaturan"),

    KELOLA_MENU("perm_menu", "Kelola Menu", "Menu", "Tambah/edit/hapus menu"),
    UBAH_HARGA("perm_harga", "Ubah Harga", "Menu", "Ubah harga produk"),

    JUAL("perm_jual", "Jual / Buat Order", "POS", "Buat transaksi baru"),
    DISKON("perm_diskon", "Beri Diskon", "POS", "Beri diskon ke pelanggan"),
    VOID_REFUND("perm_void", "Void / Refund", "POS", "Batalkan atau refund transaksi"),
    OPEN_BILL("perm_open_bill", "Open Bill", "POS", "Simpan order tanpa bayar"),

    STOCK_OPNAME("perm_opname", "Stock Opname", "Inventaris", "Sesuaikan stok fisik"),
    KELOLA_STOK("perm_stok", "Kelola Stok", "Inventaris", "Tambah/kurangi stok manual"),

    LIHAT_LAPORAN("perm_laporan", "Lihat Laporan", "Laporan", "Akses semua laporan"),
    EXPORT_DATA("perm_export", "Export Data", "Laporan", "Export ke Excel/PDF"),

    KELOLA_USER("perm_user", "Kelola Pengguna", "Admin", "Tambah/edit user & izin"),
    KELOLA_FITUR("perm_fitur", "Kelola Fitur", "Admin", "Aktif/matikan fitur"),
    PROFIL_TOKO("perm_profil", "Profil Toko", "Admin", "Ubah info toko"),
    BACKUP_RESTORE("perm_backup", "Backup / Restore", "Admin", "Backup & pulihkan data"),
    AUDIT_LOG("perm_audit", "Lihat Audit Log", "Admin", "Riwayat aktivitas");

    companion object {
        fun fromKey(k: String) = values().firstOrNull { it.key == k }
        fun byKategori(): Map<String, List<PermissionKey>> = values().groupBy { it.kategori }
    }
}

// Preset permission per role
val UserRole.defaultPermissions: Set<PermissionKey>
    get() = when (this) {
        UserRole.OWNER -> PermissionKey.values().toSet()
        UserRole.SUPERVISOR -> setOf(
            PermissionKey.LIHAT_DASHBOARD, PermissionKey.LIHAT_RIWAYAT, PermissionKey.BUKA_SETELAN,
            PermissionKey.KELOLA_MENU, PermissionKey.UBAH_HARGA,
            PermissionKey.JUAL, PermissionKey.DISKON, PermissionKey.VOID_REFUND,
            PermissionKey.OPEN_BILL, PermissionKey.STOCK_OPNAME, PermissionKey.KELOLA_STOK,
            PermissionKey.LIHAT_LAPORAN, PermissionKey.EXPORT_DATA
        )
        UserRole.KASIR -> setOf(
            PermissionKey.LIHAT_RIWAYAT,
            PermissionKey.JUAL, PermissionKey.OPEN_BILL
        )
    }

// ═══════ FEATURE MANAGER ═══════
object FeatureManager {
    private val _enabled = MutableStateFlow<Set<FeatureKey>>(emptySet())
    val enabled: StateFlow<Set<FeatureKey>> = _enabled.asStateFlow()

    fun isEnabled(feature: FeatureKey): Boolean = _enabled.value.contains(feature)
    fun update(features: Set<FeatureKey>) { _enabled.value = features }
}

// ═══════ THEME MANAGER ═══════
object ThemeManager {
    var mode by mutableStateOf(ThemeMode.SYSTEM)
        private set

    fun update(m: ThemeMode) { mode = m }
}

// ═══════ TEMA COMPOSABLE ═══════
@Composable
fun IyonzTheme(
    darkTheme: Boolean = when (ThemeManager.mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    },
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) {
        darkColorScheme(
            primary = BRAND, onPrimary = Color.White,
            secondary = BRAND_DARK,
            surfaceVariant = Color(0xFF2A2A2A),
            background = Color(0xFF121212),
            surface = Color(0xFF1E1E1E)
        )
    } else {
        lightColorScheme(
            primary = BRAND, onPrimary = Color.White,
            secondary = BRAND_DARK,
            surfaceVariant = Color(0xFFF5F5F5)
        )
    }
    MaterialTheme(colorScheme = colors, content = content)
}
