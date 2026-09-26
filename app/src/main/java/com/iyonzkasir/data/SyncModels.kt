package com.iyonzkasir.data

/**
 * Data class untuk Firestore.
 * Struktur: stores/{storeId}/...
 */

// ─── INFO TOKO ───────────────────────────────────────────────
data class StoreInfoFirestore(
    val storeId: String = "",
    val nama: String = "",
    val alamat: String = "",
    val teleponWA: String = "",
    val logoUrl: String = "",
    val heroImageUrl: String = "",
    val jamBuka: String = "08:00",
    val jamTutup: String = "22:00",
    val temaWarna: String = "#C8341A",
    val pajakPersen: Int = 0,
    val biayaLayananPersen: Int = 0,
    val active: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)

// ─── MENU ────────────────────────────────────────────────────
data class MenuItemFirestore(
    val id: String = "",
    val nama: String = "",
    val deskripsi: String = "",
    val kategori: String = "",
    val kategoriId: Long = 0L,
    val harga: Int = 0,
    val hargaAsli: Int = 0,
    val fotoUrl: String = "",
    val tersedia: Boolean = true,
    val trackStok: Boolean = false,
    val stok: Int = 0,
    val badges: List<String> = emptyList(),
    val rating: Double = 0.0,
    val reviewCount: Int = 0,
    val estimasi: String = "",
    val urutan: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
)

// ─── BUNDLE / PAKET ──────────────────────────────────────────
data class BundleFirestore(
    val id: String = "",
    val nama: String = "",
    val deskripsi: String = "",
    val hargaBundle: Int = 0,
    val hargaAsli: Int = 0,
    val tipe: String = "FIXED",
    val fotoUrl: String = "",
    val tersedia: Boolean = true,
    val aktif: Boolean = true,
    val urutan: Int = 0,
    val groups: List<BundleGroupFirestore> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class BundleGroupFirestore(
    val nama: String = "",
    val minPilih: Int = 1,
    val maxPilih: Int = 1,
    val wajib: Boolean = true,
    val items: List<BundleItemFirestore> = emptyList()
)

data class BundleItemFirestore(
    val menuId: String = "",
    val nama: String = "",
    val qty: Int = 1,
    val hargaExtra: Int = 0,
    val isDefaultPick: Boolean = false
)

// ─── ORDER (dari web) ────────────────────────────────────────
data class WebOrder(
    val id: String = "",
    val customerNama: String = "",
    val customerTelepon: String = "",
    val meja: String = "",
    val catatan: String = "",
    val items: List<WebOrderItem> = emptyList(),
    val subtotal: Int = 0,
    val voucherKode: String = "",
    val voucherDiskon: Int = 0,
    val pajak: Int = 0,
    val biayaLayanan: Int = 0,
    val total: Int = 0,
    val status: String = "PENDING",  // PENDING / PROCESSED / PAID / CANCELLED
    val source: String = "WEB",
    val timestamp: Long = 0L,
    val processedAt: Long = 0L,
    val processedBy: String = ""
)

data class WebOrderItem(
    val menuId: String = "",
    val nama: String = "",
    val harga: Int = 0,
    val qty: Int = 1,
    val totalPrice: Int = 0,
    val variant: String = ""
)

// ─── STATUS CONSTANT ─────────────────────────────────────────
object OrderStatusFirestore {
    const val PENDING = "PENDING"
    const val PROCESSED = "PROCESSED"
    const val PAID = "PAID"
    const val CANCELLED = "CANCELLED"
}
