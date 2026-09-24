package com.iyonzkasir.ui

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
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
import org.json.JSONArray
import org.json.JSONObject

// ═══════════════════════════════════════════════════════════
// CART MODELS
// ═══════════════════════════════════════════════════════════
data class BundlePick(
    val itemId: Long,
    val groupId: Long,
    val groupNama: String,
    val menuId: Long,
    val menuNama: String,
    val menuHarga: Int,
    val qty: Int = 1,
    val hargaExtra: Int = 0
)

data class CartLine(
    val key: String,
    val menu: MenuItem?,                       // null kalau bundle
    val qty: Int,
    val catatan: String = "",
    val bundle: MenuBundle? = null,            // null kalau item biasa
    val bundlePicks: List<BundlePick> = emptyList(),
    val bundleExtra: Int = 0
) {
    val isBundle: Boolean get() = bundle != null
    val nama: String get() = bundle?.nama ?: menu?.nama ?: ""
    val hargaSatuan: Int get() = if (isBundle) {
        (bundle!!.hargaBundle + bundleExtra)
    } else menu?.harga ?: 0
    val subtotal: Int get() = hargaSatuan * qty
}

// ═══════════════════════════════════════════════════════════
// KASIR VIEWMODEL
// ═══════════════════════════════════════════════════════════
class KasirViewModel(
    private val repo: PosRepository,
    private val crmRepo: CrmRepository,
    private val stockRepo: StockRepository,
    private val settingRepo: SettingRepository,
    private val bundleRepo: MenuBundleRepository
) : ViewModel() {
    val menu: StateFlow<List<MenuItem>> = repo.menu
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bundles: StateFlow<List<MenuBundle>> = bundleRepo.bundles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var nomorMeja by mutableStateOf("")
    var tipeOrder by mutableStateOf(TipeOrder.DINE_IN.id)
    var namaPelanggan by mutableStateOf("")
    var editingOrderId by mutableStateOf<Long?>(null)
    var searchQuery by mutableStateOf("")
    var kategoriFilter by mutableStateOf<String?>(null)

    var diskonTipe by mutableStateOf("NONE")
    var diskonValue by mutableStateOf(0)
    var pajakPersen by mutableStateOf(0)

    var selectedMember by mutableStateOf<Member?>(null)
    var voucherKode by mutableStateOf("")
    var voucherAmount by mutableStateOf(0)
    var voucherError by mutableStateOf<String?>(null)
    var poinDipakai by mutableStateOf(0)

    var barcodeMessage by mutableStateOf<String?>(null)

    var soundEnabled by mutableStateOf(true)
        private set
    var metodeAktif by mutableStateOf<Set<String>>(emptySet())
        private set
    var antrianEnabled by mutableStateOf(false)
        private set

    private val _cart = MutableStateFlow<Map<String, CartLine>>(emptyMap())
    val cart: StateFlow<List<CartLine>> = _cart.map { it.values.toList() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val subtotal: StateFlow<Int> = cart.map { it.sumOf { l -> l.subtotal } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    init {
        viewModelScope.launch {
            try {
                val ppn = settingRepo.getPajakDefault()
                if (ppn > 0 && pajakPersen == 0) pajakPersen = ppn
                soundEnabled = settingRepo.isSoundEnabled()
                metodeAktif = settingRepo.getMetodeAktif()
                antrianEnabled = settingRepo.isAntrianEnabled()
            } catch (_: Exception) {}
        }
    }

    fun refreshSettings() {
        viewModelScope.launch {
            try {
                soundEnabled = settingRepo.isSoundEnabled()
                metodeAktif = settingRepo.getMetodeAktif()
                antrianEnabled = settingRepo.isAntrianEnabled()
            } catch (_: Exception) {}
        }
    }

    fun hitungDiskon(sub: Int): Int = when (diskonTipe) {
        "NOMINAL" -> diskonValue.coerceIn(0, sub)
        "PERSEN" -> sub * diskonValue.coerceIn(0, 100) / 100
        else -> 0
    }
    fun hitungPajak(afterDiskon: Int): Int =
        afterDiskon * pajakPersen.coerceIn(0, 100) / 100

    fun hitungTotal(sub: Int): Int {
        val diskon = hitungDiskon(sub)
        val afterDiskon = (sub - diskon).coerceAtLeast(0)
        val afterVoucher = (afterDiskon - voucherAmount).coerceAtLeast(0)
        val afterPoin = (afterVoucher - poinKeRupiah(poinDipakai)).coerceAtLeast(0)
        return afterPoin + hitungPajak(afterPoin)
    }

    fun poinKeRupiah(poin: Int) = LoyaltyConfig.poinKeRupiah(poin)

    // ⬇️ PATCH 3A: tambah method loadBundleFull
    suspend fun loadBundleFull(id: Long) = bundleRepo.loadFull(id)
    // ⬆️ END PATCH 3A

    // ── Add menu biasa ──
    fun add(menu: MenuItem, catatan: String = "") {
        val key = "${menu.id}|$catatan"
        _cart.update { map ->
            val e = map[key]
            if (e == null) map + (key to CartLine(key, menu, 1, catatan))
            else map + (key to e.copy(qty = e.qty + 1))
        }
        if (soundEnabled) SoundHelper.playClick()
    }

    // ── Add bundle ──
    fun addBundle(bundle: MenuBundle, picks: List<BundlePick>) {
        val pickKey = picks.map { "${it.itemId}:${it.qty}" }.sorted().joinToString(",")
        val key = "bundle:${bundle.id}:$pickKey"
        val extra = picks.sumOf { it.hargaExtra * it.qty }
        _cart.update { map ->
            val existing = map[key]
            if (existing == null) {
                map + (key to CartLine(
                    key = key,
                    menu = null,
                    qty = 1,
                    bundle = bundle,
                    bundlePicks = picks,
                    bundleExtra = extra
                ))
            } else {
                map + (key to existing.copy(qty = existing.qty + 1))
            }
        }
        if (soundEnabled) SoundHelper.playClick()
    }

    fun decrease(key: String) = _cart.update { map ->
        val e = map[key] ?: return@update map
        if (e.qty <= 1) map - key else map + (key to e.copy(qty = e.qty - 1))
    }
    fun remove(key: String) { _cart.update { it - key } }
    fun clearCart() { _cart.value = emptyMap() }

    fun resetOrder() {
        nomorMeja = ""; tipeOrder = TipeOrder.DINE_IN.id; namaPelanggan = ""
        editingOrderId = null
        diskonTipe = "NONE"; diskonValue = 0
        viewModelScope.launch {
            try { pajakPersen = settingRepo.getPajakDefault() }
            catch (_: Exception) { pajakPersen = 0 }
        }
        selectedMember = null
        voucherKode = ""; voucherAmount = 0; voucherError = null
        poinDipakai = 0
        barcodeMessage = null
        clearCart()
    }

    fun addByBarcode(barcode: String, onFound: () -> Unit = {}) {
        viewModelScope.launch {
            barcodeMessage = null
            val code = barcode.trim()
            if (code.isBlank()) return@launch
            val found = repo.getMenuByBarcode(code)
            if (found == null) {
                barcodeMessage = "Barcode '$code' nggak ditemukan"
                if (soundEnabled) SoundHelper.playError()
            } else if (!found.tersedia) {
                barcodeMessage = "${found.nama} sedang tidak tersedia"
                if (soundEnabled) SoundHelper.playError()
            } else {
                add(found)
                barcodeMessage = "✓ ${found.nama} ditambahkan"
                onFound()
            }
        }
    }

    fun loadOpenBill(orderId: Long) {
        viewModelScope.launch {
            val order = repo.getOrder(orderId) ?: return@launch
            val items = repo.itemsOf(orderId)
            nomorMeja = order.nomorMeja
            tipeOrder = order.tipeOrder
            namaPelanggan = order.namaPelanggan
            editingOrderId = order.id
            diskonTipe = order.diskonTipe
            diskonValue = order.diskonValue
            pajakPersen = order.pajakPersen
            voucherKode = order.voucherKode
            voucherAmount = order.voucherAmount
            if (order.memberId > 0) selectedMember = crmRepo.getMember(order.memberId)

            // Rebuild cart dari order items
            val newCart = mutableMapOf<String, CartLine>()
            val groupedBundles = items.filter { it.bundleId > 0L }
                .groupBy { it.bundleId }
            val nonBundle = items.filter { it.bundleId == 0L }

            nonBundle.forEach { it ->
                val key = "${it.menuId}|${it.catatan}"
                val menuItem = repo.getMenu(it.menuId) ?: MenuItem(
                    id = it.menuId, nama = it.namaMenu, harga = it.hargaSatuan
                )
                newCart[key] = CartLine(key, menuItem, it.qty, it.catatan)
            }

            groupedBundles.forEach { (bundleId, bundleItems) ->
                val mainItem = bundleItems.firstOrNull { it.menuId == 0L } ?: return@forEach
                val subItems = bundleItems.filter { it.menuId != 0L }
                val bundle = bundleRepo.getById(bundleId) ?: return@forEach

                val picks = subItems.map { sub ->
                    BundlePick(
                        itemId = 0,
                        groupId = 0,
                        groupNama = "",
                        menuId = sub.menuId,
                        menuNama = sub.namaMenu.trimStart('•', ' '),
                        menuHarga = 0,
                        qty = if (mainItem.qty > 0) sub.qty / mainItem.qty else sub.qty,
                        hargaExtra = sub.hargaSatuan
                    )
                }
                val extra = picks.sumOf { it.hargaExtra * it.qty }
                val key = "bundle:${bundle.id}:loaded"
                newCart[key] = CartLine(
                    key = key,
                    menu = null,
                    qty = mainItem.qty,
                    bundle = bundle,
                    bundlePicks = picks,
                    bundleExtra = extra
                )
            }
            _cart.value = newCart
        }
    }

    fun setMember(m: Member?) {
        selectedMember = m
        if (m == null || poinDipakai > m.poin) poinDipakai = 0
    }

    fun applyVoucher(kode: String, sub: Int) {
        viewModelScope.launch {
            voucherError = null
            val k = kode.trim().uppercase()
            if (k.isBlank()) {
                voucherKode = ""; voucherAmount = 0; return@launch
            }
            val v = crmRepo.getVoucher(k)
            if (v == null) {
                voucherError = "Kode nggak ditemukan"
                voucherKode = ""; voucherAmount = 0
                if (soundEnabled) SoundHelper.playError()
                return@launch
            }
            val now = System.currentTimeMillis()
            when {
                !v.aktif -> {
                    voucherError = "Voucher tidak aktif"
                    if (soundEnabled) SoundHelper.playError(); return@launch
                }
                v.kuota > 0 && v.terpakai >= v.kuota -> {
                    voucherError = "Kuota habis"
                    if (soundEnabled) SoundHelper.playError(); return@launch
                }
                v.tglAkhir > 0 && v.tglAkhir < now -> {
                    voucherError = "Voucher kadaluarsa"
                    if (soundEnabled) SoundHelper.playError(); return@launch
                }
                v.tglMulai > 0 && v.tglMulai > now -> {
                    voucherError = "Voucher belum berlaku"
                    if (soundEnabled) SoundHelper.playError(); return@launch
                }
                sub < v.minBelanja -> {
                    voucherError = "Min belanja ${v.minBelanja.rupiah()}"
                    if (soundEnabled) SoundHelper.playError()
                    return@launch
                }
            }
            val amount = if (v.tipe == "PERSEN") {
                val raw = sub * v.value / 100
                if (v.maxDiskon > 0) raw.coerceAtMost(v.maxDiskon) else raw
            } else {
                v.value.coerceAtMost(sub)
            }
            voucherKode = k
            voucherAmount = amount
        }
    }

    fun clearVoucher() {
        voucherKode = ""; voucherAmount = 0; voucherError = null
    }

    fun simpanOpenBill(onDone: () -> Unit) {
        val lines = cart.value
        if (lines.isEmpty()) return
        viewModelScope.launch {
            val sub = lines.sumOf { it.subtotal }
            val diskon = hitungDiskon(sub)
            val afterDiskon = (sub - diskon).coerceAtLeast(0)
            val afterVoucher = (afterDiskon - voucherAmount).coerceAtLeast(0)
            val afterPoin = (afterVoucher - poinKeRupiah(poinDipakai)).coerceAtLeast(0)
            val pajak = hitungPajak(afterPoin)
            val totalInt = afterPoin + pajak
            val items = buildOrderItems(lines)
            val existingId = editingOrderId
            val shiftId = repo.getActiveShiftId() ?: 0L
            val order = Order(
                id = existingId ?: 0,
                timestamp = System.currentTimeMillis(),
                nomorMeja = nomorMeja, tipeOrder = tipeOrder,
                namaPelanggan = namaPelanggan,
                subtotal = sub,
                diskonTipe = diskonTipe, diskonValue = diskonValue,
                diskonAmount = diskon,
                voucherKode = voucherKode, voucherAmount = voucherAmount,
                pajakPersen = pajakPersen, pajakAmount = pajak,
                total = totalInt,
                status = OrderStatus.OPEN.id,
                shiftId = shiftId,
                memberId = selectedMember?.id ?: 0,
                memberNama = selectedMember?.nama ?: "",
                stokDipotong = false
            )
            if (existingId != null) repo.updateOrderWithItems(order, items)
            else repo.simpanOrder(order, items)
            resetOrder(); onDone()
        }
    }

    fun checkout(metode: String, dibayar: Int, onDone: suspend (Long) -> Unit) {
        val lines = cart.value
        if (lines.isEmpty()) return
        viewModelScope.launch {
            val sub = lines.sumOf { it.subtotal }
            val diskon = hitungDiskon(sub)
            val afterDiskon = (sub - diskon).coerceAtLeast(0)
            val afterVoucher = (afterDiskon - voucherAmount).coerceAtLeast(0)
            val afterPoin = (afterVoucher - poinKeRupiah(poinDipakai)).coerceAtLeast(0)
            val pajak = hitungPajak(afterPoin)
            val totalInt = afterPoin + pajak
            val items = buildOrderItems(lines)
            val u = Session.current
            val existingId = editingOrderId
            val shiftId = repo.getActiveShiftId() ?: 0L
            val member = selectedMember
            val poinDidapat = if (member != null)
                LoyaltyConfig.hitungPoinDidapat(totalInt) else 0
            val potongStok = FeatureManager.isEnabled(FeatureKey.POTONG_STOK)

            // HPP: hitung dari menu + sub-bundle
            val allMenu = menu.value.associateBy { it.id }
            val totalHpp = lines.sumOf { line ->
                if (line.isBundle) {
                    line.bundlePicks.sumOf { pick ->
                        (allMenu[pick.menuId]?.hargaBeli ?: 0) * pick.qty * line.qty
                    }
                } else {
                    (line.menu?.hargaBeli ?: 0) * line.qty
                }
            }

            val nomorAntrian = if (antrianEnabled) {
                try { repo.nextNomorAntrian() } catch (_: Exception) { 0 }
            } else 0

            val order = Order(
                id = existingId ?: 0,
                timestamp = System.currentTimeMillis(),
                nomorMeja = nomorMeja, tipeOrder = tipeOrder,
                namaPelanggan = if (namaPelanggan.isBlank() && member != null)
                    member.nama else namaPelanggan,
                subtotal = sub,
                diskonTipe = diskonTipe, diskonValue = diskonValue,
                diskonAmount = diskon,
                voucherKode = voucherKode, voucherAmount = voucherAmount,
                pajakPersen = pajakPersen, pajakAmount = pajak,
                total = totalInt,
                metodeBayar = metode, dibayar = dibayar,
                kembalian = (dibayar - totalInt).coerceAtLeast(0),
                status = OrderStatus.PAID.id,
                kasirId = u?.id ?: "", kasirNama = u?.nama ?: "",
                shiftId = shiftId,
                memberId = member?.id ?: 0,
                memberNama = member?.nama ?: "",
                poinDidapat = poinDidapat,
                stokDipotong = false,
                nomorAntrian = nomorAntrian,
                totalHpp = totalHpp
            )
            val id: Long = if (existingId != null) {
                repo.updateOrderWithItems(order, items); existingId
            } else repo.simpanOrder(order, items)

            if (potongStok) {
                try {
                    items.filter { it.bundleId == 0L || it.menuId > 0L }
                        .forEach { it ->
                            if (it.menuId > 0L)
                                stockRepo.potongStokPenjualan(it.menuId, it.qty, id)
                        }
                    repo.markStokDipotong(id, true)
                } catch (_: Exception) {}
            }

            if (member != null) {
                try {
                    if (poinDipakai > 0) crmRepo.redeemPoin(member.id, poinDipakai)
                    crmRepo.processOrder(member.id, order.copy(id = id))
                    if (voucherKode.isNotBlank()) crmRepo.markVoucherUsed(voucherKode)
                } catch (_: Exception) {}
            }

            if (soundEnabled) SoundHelper.playSuccess()
            resetOrder()
            onDone(id)
        }
    }

    // ── Konversi CartLine ke OrderItem list ──
    private fun buildOrderItems(lines: List<CartLine>): List<OrderItem> {
        val result = mutableListOf<OrderItem>()
        lines.forEach { line ->
            if (line.isBundle) {
                val b = line.bundle!!
                // Baris utama (nama paket)
                result += OrderItem(
                    orderId = 0, menuId = 0L,
                    namaMenu = b.nama,
                    hargaSatuan = b.hargaBundle + line.bundleExtra,
                    qty = line.qty,
                    catatan = "",
                    bundleId = b.id,
                    bundleNama = b.nama,
                    pilihanJson = encodePicks(line.bundlePicks)
                )
                // Baris sub-item (tanpa harga, hanya info)
                line.bundlePicks.forEach { pick ->
                    result += OrderItem(
                        orderId = 0, menuId = pick.menuId,
                        namaMenu = "  - ${pick.menuNama}",
                        hargaSatuan = 0,
                        qty = pick.qty * line.qty,
                        catatan = "",
                        bundleId = b.id,
                        bundleNama = b.nama,
                        pilihanJson = ""
                    )
                }
            } else {
                val m = line.menu ?: return@forEach
                result += OrderItem(
                    orderId = 0, menuId = m.id,
                    namaMenu = m.nama,
                    hargaSatuan = m.harga,
                    qty = line.qty,
                    catatan = line.catatan
                )
            }
        }
        return result
    }

    private fun encodePicks(picks: List<BundlePick>): String {
        return try {
            val arr = JSONArray()
            picks.forEach { p ->
                val obj = JSONObject()
                obj.put("menuId", p.menuId)
                obj.put("nama", p.menuNama)
                obj.put("qty", p.qty)
                obj.put("extra", p.hargaExtra)
                obj.put("group", p.groupNama)
                arr.put(obj)
            }
            arr.toString()
        } catch (_: Exception) { "[]" }
    }
}

// ═══════════════════════════════════════════════════════════
// MENU VIEWMODEL
// ═══════════════════════════════════════════════════════════
class MenuViewModel(
    private val repo: PosRepository,
    private val kategoriRepo: KategoriRepository
) : ViewModel() {
    val menu: StateFlow<List<MenuItem>> = repo.menu
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val kategori: StateFlow<List<Kategori>> = kategoriRepo.all
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun save(item: MenuItem, onDone: () -> Unit = {}) = viewModelScope.launch {
        repo.upsertMenu(item); onDone()
    }
    fun delete(item: MenuItem) = viewModelScope.launch {
        MenuPhotoManager.deleteByPath(item.fotoUri)
        repo.deleteMenu(item)
    }
    fun toggle(item: MenuItem) = viewModelScope.launch {
        repo.setTersedia(item.id, !item.tersedia)
    }
    suspend fun get(id: Long) = repo.getMenu(id)
    suspend fun getByBarcode(barcode: String) = repo.getMenuByBarcode(barcode)
}

// ═══════════════════════════════════════════════════════════
// OPEN BILL VIEWMODEL
// ═══════════════════════════════════════════════════════════
class OpenBillViewModel(repo: PosRepository) : ViewModel() {
    val openBills: StateFlow<List<Order>> = repo.openBills
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

// ═══════════════════════════════════════════════════════════
// RIWAYAT VIEWMODEL
// ═══════════════════════════════════════════════════════════
class RiwayatViewModel(
    private val repo: PosRepository,
    private val stockRepo: StockRepository
) : ViewModel() {
    val orders: StateFlow<List<Order>> = repo.paidOrders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun voidOrder(id: Long, reason: String, onDone: () -> Unit = {}) =
        viewModelScope.launch {
            val order = repo.getOrder(id)
            val items = repo.itemsOf(id)
            repo.voidOrder(id, reason)
            if (order?.stokDipotong == true) {
                try {
                    items.forEach { it ->
                        if (it.menuId > 0L)
                            stockRepo.kembalikanStok(it.menuId, it.qty, id,
                                "Void order #$id: $reason")
                    }
                    repo.markStokDipotong(id, false)
                } catch (_: Exception) {}
            }
            onDone()
        }

    fun refundOrder(id: Long, reason: String, onDone: () -> Unit = {}) =
        viewModelScope.launch {
            val order = repo.getOrder(id)
            val items = repo.itemsOf(id)
            repo.refundOrder(id, reason)
            if (order?.stokDipotong == true) {
                try {
                    items.forEach { it ->
                        if (it.menuId > 0L)
                            stockRepo.kembalikanStok(it.menuId, it.qty, id,
                                "Refund order #$id: $reason")
                    }
                    repo.markStokDipotong(id, false)
                } catch (_: Exception) {}
            }
            onDone()
        }

    suspend fun itemsOf(orderId: Long) = repo.itemsOf(orderId)
    suspend fun getOrder(id: Long) = repo.getOrder(id)
}

// ═══════════════════════════════════════════════════════════
// DASHBOARD VIEWMODEL
// ═══════════════════════════════════════════════════════════
class DashboardViewModel(
    private val repo: PosRepository,
    private val expenseRepo: ExpenseRepository
) : ViewModel() {
    private val startOfDay: Long = run {
        val c = java.util.Calendar.getInstance()
        c.set(java.util.Calendar.HOUR_OF_DAY, 0)
        c.set(java.util.Calendar.MINUTE, 0)
        c.set(java.util.Calendar.SECOND, 0)
        c.set(java.util.Calendar.MILLISECOND, 0)
        c.timeInMillis
    }
    private val endOfDay: Long = startOfDay + 24L * 60 * 60 * 1000 - 1

    val transaksiHariIni: StateFlow<Int> = repo.countPaidSince(startOfDay)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val omzetHariIni: StateFlow<Int> = repo.sumPaidSince(startOfDay)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val diskonHariIni: StateFlow<Int> = repo.sumDiskonSince(startOfDay)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val pajakHariIni: StateFlow<Int> = repo.sumPajakSince(startOfDay)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val transaksiTerakhir: StateFlow<List<Order>> = repo.observePaidSince(startOfDay)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pengeluaranHariIni: StateFlow<Int> = expenseRepo.observeSumInRange(
        startOfDay, endOfDay
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _hppHariIni = MutableStateFlow(0)
    val hppHariIni: StateFlow<Int> = _hppHariIni.asStateFlow()

    private val _grafik = MutableStateFlow<List<HariPenjualan>>(emptyList())
    val grafik: StateFlow<List<HariPenjualan>> = _grafik.asStateFlow()

    private val _topMenu = MutableStateFlow<List<MenuTerlaris>>(emptyList())
    val topMenu: StateFlow<List<MenuTerlaris>> = _topMenu.asStateFlow()

    private val _topKategori = MutableStateFlow<List<KategoriTerlaris>>(emptyList())
    val topKategori: StateFlow<List<KategoriTerlaris>> = _topKategori.asStateFlow()

    private val _metodeStat = MutableStateFlow<List<MetodeBayarStat>>(emptyList())
    val metodeStat: StateFlow<List<MetodeBayarStat>> = _metodeStat.asStateFlow()

    init { loadExtras() }

    fun loadExtras() {
        viewModelScope.launch {
            try {
                _hppHariIni.value = repo.sumHppInRange(startOfDay, System.currentTimeMillis())
            } catch (_: Exception) { _hppHariIni.value = 0 }

            val cal = java.util.Calendar.getInstance()
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            val startToday = cal.timeInMillis
            val start = startToday - 6L * 24 * 60 * 60 * 1000
            val end = startToday + 24L * 60 * 60 * 1000 - 1
            val orders = repo.ordersInRange(start, end)
            val fmt = java.text.SimpleDateFormat("dd/MM", java.util.Locale("id"))
            val map = mutableMapOf<Long, Pair<Int, Int>>()
            for (i in 0..6) map[start + i * 24L * 60 * 60 * 1000] = 0 to 0
            orders.forEach { o ->
                val c = java.util.Calendar.getInstance()
                c.timeInMillis = o.timestamp
                c.set(java.util.Calendar.HOUR_OF_DAY, 0)
                c.set(java.util.Calendar.MINUTE, 0)
                c.set(java.util.Calendar.SECOND, 0)
                c.set(java.util.Calendar.MILLISECOND, 0)
                val dayStart = c.timeInMillis
                val prev = map[dayStart] ?: (0 to 0)
                map[dayStart] = (prev.first + 1) to (prev.second + o.total)
            }
            _grafik.value = map.entries.sortedBy { it.key }.map { (day, pair) ->
                HariPenjualan(
                    label = fmt.format(java.util.Date(day)),
                    omzet = pair.second, transaksi = pair.first
                )
            }
            _topMenu.value = repo.menuTerlaris(start, end, 5)
            _topKategori.value = repo.kategoriTerlaris(start, end, 5)
            _metodeStat.value = repo.metodeBayarStat(start, end, 5)
        }
    }
}

// ═══════════════════════════════════════════════════════════
// VM FACTORY
// ═══════════════════════════════════════════════════════════
class PosVMFactory(
    private val repo: PosRepository,
    private val crmRepo: CrmRepository,
    private val stockRepo: StockRepository,
    private val kategoriRepo: KategoriRepository,
    private val settingRepo: SettingRepository,
    private val expenseRepo: ExpenseRepository,
    private val bundleRepo: MenuBundleRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(KasirViewModel::class.java) ->
            KasirViewModel(repo, crmRepo, stockRepo, settingRepo, bundleRepo) as T
        modelClass.isAssignableFrom(MenuViewModel::class.java) ->
            MenuViewModel(repo, kategoriRepo) as T
        modelClass.isAssignableFrom(OpenBillViewModel::class.java) -> OpenBillViewModel(repo) as T
        modelClass.isAssignableFrom(RiwayatViewModel::class.java) ->
            RiwayatViewModel(repo, stockRepo) as T
        modelClass.isAssignableFrom(DashboardViewModel::class.java) ->
            DashboardViewModel(repo, expenseRepo) as T
        else -> error("VM tidak dikenal: ${modelClass.name}")
    }
}


// ═══════════════════════════════════════════════════════════
// ROUTES
// ═══════════════════════════════════════════════════════════
@Composable
private fun activityOwner(): ComponentActivity =
    LocalContext.current as ComponentActivity

@Composable
private fun posFactory(app: IyonzApp) = remember {
    PosVMFactory(
        app.posRepo, app.crmRepo, app.stockRepo,
        app.kategoriRepo, app.settingRepo, app.expenseRepo,
        app.bundleRepo
    )
}

@Composable
fun PosRoute(app: IyonzApp, nav: NavHostController, innerNav: NavHostController) {
    val factory = posFactory(app)
    val owner = activityOwner()
    val vm: KasirViewModel = viewModel(viewModelStoreOwner = owner, factory = factory)
    LaunchedEffect(Unit) { vm.refreshSettings() }
    PosScreen(vm,
        onOpenKeranjang = { nav.navigate(Routes.KERANJANG) },
        onBayar = { nav.navigate(Routes.BAYAR) },
        onBundleEdit = { id -> nav.navigate("${Routes.BUNDLING_EDIT}/$id") })
}

@Composable
fun KeranjangRoute(app: IyonzApp, nav: NavHostController) {
    val factory = posFactory(app)
    val owner = activityOwner()
    val vm: KasirViewModel = viewModel(viewModelStoreOwner = owner, factory = factory)
    KeranjangScreen(vm,
        onBack = { nav.popBackStack() },
        onBayar = { nav.navigate(Routes.BAYAR) })
}

@Composable
fun BayarRoute(app: IyonzApp, nav: NavHostController) {
    val factory = posFactory(app)
    val owner = activityOwner()
    val vm: KasirViewModel = viewModel(viewModelStoreOwner = owner, factory = factory)
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    var showSuccess by remember { mutableStateOf(false) }
    var postOrder by remember { mutableStateOf<Order?>(null) }
    var postItems by remember { mutableStateOf<List<OrderItem>>(emptyList()) }
    var showPreview by remember { mutableStateOf(false) }
    var strukText by remember { mutableStateOf("") }

    BayarScreen(vm,
        onBack = { nav.popBackStack() },
        onSelesai = { orderId ->
            scope.launch {
                val order = app.posRepo.getOrder(orderId)
                val items = app.posRepo.itemsOf(orderId)
                postOrder = order
                postItems = items

                if (app.settingRepo.isAutoPrint()) {
                    try {
                        if (!PrinterService.isConnected()) {
                            val mac = app.settingRepo.getPrinterMac()
                            if (mac.isNotBlank()) PrinterService.connect(ctx, mac)
                        }
                        if (order != null) cetakStruk(app.settingRepo, order, items)
                    } catch (_: Exception) {}
                }
                showSuccess = true
            }
        })

    if (showSuccess && postOrder != null) {
        SuccessScreen(
            order = postOrder!!,
            onPrint = {
                scope.launch {
                    try {
                        if (!PrinterService.isConnected()) {
                            val mac = app.settingRepo.getPrinterMac()
                            if (mac.isNotBlank()) PrinterService.connect(ctx, mac)
                        }
                        cetakStruk(app.settingRepo, postOrder!!, postItems)
                    } catch (_: Exception) {}
                }
            },
            onPreview = {
                scope.launch {
                    strukText = buildStrukText(app.settingRepo, postOrder!!, postItems)
                    showPreview = true
                }
            },
            onShare = {
                scope.launch {
                    val text = buildStrukText(app.settingRepo, postOrder!!, postItems)
                    shareStrukText(ctx, text)
                }
            },
            onNewTransaction = {
                showSuccess = false
                nav.popBackStack(Routes.MAIN, inclusive = false)
            },
            onDone = {
                showSuccess = false
                nav.popBackStack(Routes.MAIN, inclusive = false)
            }
        )
    }

    if (showPreview && postOrder != null) {
        StrukPreviewDialog(
            strukText = strukText,
            onPrint = {
                scope.launch {
                    try {
                        if (!PrinterService.isConnected()) {
                            val mac = app.settingRepo.getPrinterMac()
                            if (mac.isNotBlank()) PrinterService.connect(ctx, mac)
                        }
                        cetakStruk(app.settingRepo, postOrder!!, postItems)
                    } catch (_: Exception) {}
                }
            },
            onShareWa = {
                scope.launch {
                    val text = buildStrukText(app.settingRepo, postOrder!!, postItems)
                    shareStrukText(ctx, text)
                }
            },
            onDismiss = { showPreview = false }
        )
    }
}

@Composable
fun OpenBillRoute(app: IyonzApp, nav: NavHostController, innerNav: NavHostController) {
    val factory = posFactory(app)
    val vm: OpenBillViewModel = viewModel(factory = factory)
    val owner = activityOwner()
    val kasirVm: KasirViewModel = viewModel(viewModelStoreOwner = owner, factory = factory)
    OpenBillScreen(vm) { orderId ->
        kasirVm.loadOpenBill(orderId)
        nav.navigate(Routes.BAYAR)
    }
}

@Composable
fun MenuRoute(app: IyonzApp, nav: NavHostController, innerNav: NavHostController) {
    val factory = posFactory(app)
    val vm: MenuViewModel = viewModel(factory = factory)
    MenuScreen(vm) { id ->
        nav.navigate(if (id == null) Routes.EDIT_MENU else "${Routes.EDIT_MENU}/$id")
    }
}

@Composable
fun EditMenuRoute(app: IyonzApp, nav: NavHostController, menuId: Long?) {
    val factory = posFactory(app)
    val vm: MenuViewModel = viewModel(factory = factory)
    EditMenuScreen(vm, menuId) { nav.popBackStack() }
}

@Composable
fun RiwayatRoute(app: IyonzApp) {
    val factory = posFactory(app)
    val vm: RiwayatViewModel = viewModel(factory = factory)
    RiwayatScreen(vm)
}

@Composable
fun DashboardRoute(app: IyonzApp) {
    val factory = posFactory(app)
    val vm: DashboardViewModel = viewModel(factory = factory)
    DashboardScreen(vm)
}

// ═══════════════════════════════════════════════════════════
// POS SCREEN
// ═══════════════════════════════════════════════════════════
@Composable
fun PosScreen(
    vm: KasirViewModel,
    onOpenKeranjang: () -> Unit,
    onBayar: () -> Unit,
    onBundleEdit: (Long) -> Unit = {}
) {
    BoxWithConstraints {
        val isTablet = maxWidth >= 720.dp
        if (isTablet) {
            Row(Modifier.fillMaxSize()) {
                MenuPane(vm, Modifier.weight(1f), isTablet = true,
                    onBundleEdit = onBundleEdit)
                VerticalDivider()
                CartPane(vm, Modifier.width(380.dp), onBayar)
            }
        } else {
            Box(Modifier.fillMaxSize()) {
                MenuPane(vm, Modifier.fillMaxSize(), isTablet = false,
                    onBundleEdit = onBundleEdit)
                val cart by vm.cart.collectAsState()
                val subtotal by vm.subtotal.collectAsState()
                val total = vm.hitungTotal(subtotal)
                if (cart.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                        shadowElevation = 8.dp
                    ) {
                        Row(Modifier.fillMaxWidth().padding(Sp.md),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${cart.sumOf { it.qty }} item",
                                    style = MaterialTheme.typography.bodySmall)
                                Text(total.rupiah(), fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleLarge, color = BRAND)
                            }
                            Button(onClick = onOpenKeranjang,
                                colors = ButtonDefaults.buttonColors(containerColor = BRAND)) {
                                Icon(Icons.Default.ShoppingCart, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Keranjang")
                            }
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// MENU PANE — grid menu + bundle
// ═══════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuPane(
    vm: KasirViewModel,
    modifier: Modifier = Modifier,
    isTablet: Boolean,
    onBundleEdit: (Long) -> Unit = {}
) {
    var showBarcode by remember { mutableStateOf(false) }
    var tabIndex by remember { mutableIntStateOf(0) } // 0=Menu, 1=Paket
    var pickerBundle by remember { mutableStateOf<MenuBundle?>(null) }

    val barcodeEnabled = FeatureManager.isEnabled(FeatureKey.BARCODE)
    val bundlingEnabled = FeatureManager.isEnabled(FeatureKey.BUNDLING)

    val menu by vm.menu.collectAsState()
    val bundles by vm.bundles.collectAsState()

    val kategoriList = remember(menu) {
        listOf(null) + menu.map { it.kategori }.distinct()
    }
    val filteredMenu = remember(menu, vm.searchQuery, vm.kategoriFilter) {
        menu.filter {
            (vm.kategoriFilter == null || it.kategori == vm.kategoriFilter) &&
            (vm.searchQuery.isBlank() || it.nama.contains(vm.searchQuery, ignoreCase = true))
        }
    }
    val filteredBundles = remember(bundles, vm.searchQuery) {
        bundles.filter {
            vm.searchQuery.isBlank() ||
            it.nama.contains(vm.searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("iyonzkasir",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold, color = BRAND)
                        Text(
                            if (vm.nomorMeja.isBlank()) "Belum pilih meja"
                            else "Meja " + vm.nomorMeja,
                            style = MaterialTheme.typography.bodySmall)
                    }
                },
                actions = {
                    if (barcodeEnabled) {
                        IconButton(onClick = { showBarcode = true }) {
                            Icon(Icons.Default.QrCodeScanner, null, tint = BRAND)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (FeatureManager.isEnabled(FeatureKey.NOMOR_MEJA)) {
                OrderMetaBar(vm)
            }

            // ── Tab Menu / Paket ──
            if (bundlingEnabled && bundles.isNotEmpty()) {
                TabRow(
                    selectedTabIndex = tabIndex,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Tab(
                        selected = tabIndex == 0,
                        onClick = { tabIndex = 0 },
                        text = { Text("Menu") },
                        icon = { Icon(Icons.Default.RestaurantMenu, null,
                            Modifier.size(20.dp)) }
                    )
                    Tab(
                        selected = tabIndex == 1,
                        onClick = { tabIndex = 1 },
                        text = { Text("Paket (${bundles.size})") },
                        icon = { Icon(Icons.Default.LocalOffer, null,
                            Modifier.size(20.dp)) }
                    )
                }
            }

            OutlinedTextField(
                value = vm.searchQuery, onValueChange = { vm.searchQuery = it },
                placeholder = { Text("Cari menu", style = MaterialTheme.typography.bodySmall) },
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
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = Sp.md, vertical = Sp.xs)
                    .height(52.dp)
            )

            vm.barcodeMessage?.let { msg ->
                Surface(
                    color = if (msg.startsWith("✓")) BRAND_LIGHT
                    else MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Sp.md)
                ) {
                    Row(Modifier.padding(Sp.sm),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (msg.startsWith("✓")) Icons.Default.CheckCircle
                            else Icons.Default.Error,
                            null,
                            tint = if (msg.startsWith("✓")) SUCCESS else DANGER,
                            modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(msg, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f))
                        IconButton(onClick = { vm.barcodeMessage = null },
                            modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, null, Modifier.size(14.dp))
                        }
                    }
                }
            }

            // ── Konten tab ──
            if (tabIndex == 0 || !bundlingEnabled || bundles.isEmpty()) {
                // ─── MENU BIASA ───
                if (kategoriList.size > 1) {
                    LazyRow(
                        contentPadding = PaddingValues(
                            horizontal = Sp.md, vertical = Sp.xs
                        ),
                        horizontalArrangement = Arrangement.spacedBy(Sp.xs)
                    ) {
                        items(kategoriList.size) { i ->
                            val k = kategoriList[i]
                            FilterChip(
                                selected = k == vm.kategoriFilter,
                                onClick = { vm.kategoriFilter = k },
                                label = { Text(k ?: "Semua",
                                    style = MaterialTheme.typography.bodySmall) },
                                modifier = Modifier.height(32.dp))
                        }
                    }
                }

                if (filteredMenu.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(if (menu.isEmpty()) "Belum ada menu"
                            else "Menu tidak ditemukan")
                    }
                } else {
                    val kolom = if (isTablet) 3 else 2
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(kolom),
                        contentPadding = PaddingValues(Sp.md),
                        verticalArrangement = Arrangement.spacedBy(Sp.sm),
                        horizontalArrangement = Arrangement.spacedBy(Sp.sm)
                    ) {
                        items(filteredMenu, key = { it.id }) { m ->
                            MenuCard(m, enabled = m.tersedia) { vm.add(m) }
                        }
                    }
                }
            } else {
                // ─── BUNDLE ───
                if (filteredBundles.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Belum ada paket bundling",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    val kolom = if (isTablet) 3 else 2
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(kolom),
                        contentPadding = PaddingValues(Sp.md),
                        verticalArrangement = Arrangement.spacedBy(Sp.sm),
                        horizontalArrangement = Arrangement.spacedBy(Sp.sm)
                    ) {
                        items(filteredBundles, key = { it.id }) { b ->
                            BundleMenuCard(
                                bundle = b,
                                onTap = {
                                    val tipe = BundleTipe.fromId(b.tipe)
                                    if (tipe == BundleTipe.FIXED) {
                                        // Auto-pick untuk FIXED
                                        val autoPicks = mutableListOf<BundlePick>()
                                        // Karena FIXED datanya di group & items, ini butuh load
                                        // Untuk MVP, FIXED buka dialog juga biar konsisten
                                        pickerBundle = b
                                    } else {
                                        pickerBundle = b
                                    }
                                },
                                onLongPress = {
                                    if (Session.can(PermissionKey.KELOLA_MENU))
                                        onBundleEdit(b.id)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showBarcode) {
        BarcodeScannerDialog(
            title = "Scan Menu",
            hintText = "Scan barcode produk untuk tambah ke keranjang",
            onResult = { code ->
                vm.addByBarcode(code)
                showBarcode = false
            },
            onDismiss = { showBarcode = false }
        )
    }

    pickerBundle?.let { b ->
        BundlePickerDialog(
            bundle = b,
            menuList = menu,
            vm = vm,
            onDismiss = { pickerBundle = null },
            onConfirm = { picks ->
                vm.addBundle(b, picks)
                pickerBundle = null
            }
        )
    }
}

// ═══════════════════════════════════════════════════════════
// ORDER META BAR
// ═══════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrderMetaBar(vm: KasirViewModel) {
    var showDialog by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = Sp.md, vertical = Sp.xs),
        horizontalArrangement = Arrangement.spacedBy(Sp.sm),
        verticalAlignment = Alignment.CenterVertically) {
        AssistChip(onClick = { showDialog = true },
            leadingIcon = { Icon(Icons.Default.TableRestaurant, null, Modifier.size(16.dp)) },
            label = { Text(if (vm.nomorMeja.isBlank()) "Pilih Meja" else "Meja ${vm.nomorMeja}",
                style = MaterialTheme.typography.bodySmall) },
            modifier = Modifier.height(32.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Sp.xs)) {
            items(TipeOrder.values().toList()) { t ->
                FilterChip(selected = vm.tipeOrder == t.id,
                    onClick = { vm.tipeOrder = t.id },
                    label = { Text(t.label, style = MaterialTheme.typography.bodySmall) },
                    modifier = Modifier.height(32.dp))
            }
        }
    }

    if (showDialog) {
        var tempMeja by remember { mutableStateOf(vm.nomorMeja) }
        var tempNama by remember { mutableStateOf(vm.namaPelanggan) }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Info Meja & Pelanggan") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Sp.sm)) {
                    OutlinedTextField(tempMeja,
                        { tempMeja = it.filter { c -> c.isDigit() } },
                        label = { Text("Nomor meja") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(tempNama, { tempNama = it },
                        label = { Text("Nama pelanggan (opsional)") },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.nomorMeja = tempMeja; vm.namaPelanggan = tempNama
                    showDialog = false
                }) { Text("Simpan") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Batal") }
            }
        )
    }
}

// ═══════════════════════════════════════════════════════════
// MENU CARD (item biasa)
// ═══════════════════════════════════════════════════════════
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MenuCard(m: MenuItem, enabled: Boolean, onClick: () -> Unit) {
    val stokHabis = m.trackStok && m.stok <= 0
    val efektifEnabled = enabled && !stokHabis
    Card(
        modifier = Modifier.fillMaxWidth().height(175.dp)
            .clickable(enabled = efektifEnabled, onClick = onClick),
        shape = RoundedCornerShape(Rd.md)
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(100.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant)) {
                m.fotoUri?.let { uri ->
                    AsyncImage(model = uri, contentDescription = m.nama,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize())
                }
                if (!efektifEnabled) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center) {
                        Text(if (stokHabis) "Stok Habis" else "Habis",
                            color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
                if (m.trackStok && !stokHabis) {
                    Surface(
                        modifier = Modifier.padding(6.dp).align(Alignment.TopEnd),
                        color = if (m.stok <= m.stokMinimal) DANGER else BRAND,
                        shape = RoundedCornerShape(Rd.xs)
                    ) {
                        Text("${m.stok}",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold)
                    }
                }
            }
            Column(Modifier.padding(Sp.sm)) {
                Text(m.nama, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(2.dp))
                Text(m.harga.rupiah(), color = BRAND, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// BUNDLE MENU CARD
// ═══════════════════════════════════════════════════════════
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BundleMenuCard(
    bundle: MenuBundle,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    val tipe = BundleTipe.fromId(bundle.tipe)
    val badgeColor = when (tipe) {
        BundleTipe.FIXED -> Color(0xFFFF8C42)
        BundleTipe.PILIHAN -> Color(0xFF8E24AA)
        BundleTipe.MIX -> Color(0xFF1E88E5)
    }

    Card(
        modifier = Modifier.fillMaxWidth().height(185.dp)
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPress
            ),
        shape = RoundedCornerShape(Rd.md),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column {
            Box(
                Modifier.fillMaxWidth().height(100.dp)
                    .background(badgeColor.copy(alpha = 0.12f))
            ) {
                if (bundle.fotoUri != null) {
                    AsyncImage(
                        model = bundle.fotoUri,
                        contentDescription = bundle.nama,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center) {
                        Text(tipe.emoji,
                            style = MaterialTheme.typography.displaySmall)
                    }
                }

                // Badge tipe
                Surface(
                    modifier = Modifier.padding(6.dp).align(Alignment.TopStart),
                    color = badgeColor,
                    shape = RoundedCornerShape(Rd.xs)
                ) {
                    Text(
                        "${tipe.emoji} ${if (tipe == BundleTipe.FIXED) "PAKET" else "PILIHAN"}",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Badge hemat
                if (bundle.hargaAsli > bundle.hargaBundle) {
                    Surface(
                        modifier = Modifier.padding(6.dp).align(Alignment.TopEnd),
                        color = SUCCESS,
                        shape = RoundedCornerShape(Rd.xs)
                    ) {
                        Text(
                            "Hemat ${(bundle.hargaAsli - bundle.hargaBundle).rupiah()}",
                            modifier = Modifier.padding(
                                horizontal = 6.dp, vertical = 2.dp
                            ),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Column(Modifier.padding(Sp.sm)) {
                Text(bundle.nama, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(bundle.hargaBundle.rupiah(),
                        color = BRAND, fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium)
                    if (bundle.hargaAsli > bundle.hargaBundle) {
                        Spacer(Modifier.width(4.dp))
                        Text(bundle.hargaAsli.rupiah(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textDecoration =
                                androidx.compose.ui.text.style.TextDecoration.LineThrough)
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// BUNDLE PICKER DIALOG
// ═══════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BundlePickerDialog(
    bundle: MenuBundle,
    menuList: List<MenuItem>,
    vm: KasirViewModel,
    onDismiss: () -> Unit,
    onConfirm: (List<BundlePick>) -> Unit
) {
    val scope = rememberCoroutineScope()
    var groups by remember { mutableStateOf<List<MenuBundleGroup>>(emptyList()) }
    var itemsMap by remember { mutableStateOf<Map<Long, List<MenuBundleItem>>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }

    // Pilihan user: groupId -> list of (itemId)
    var picks by remember { mutableStateOf<Map<Long, Set<Long>>>(emptyMap()) }

    val menuById = remember(menuList) { menuList.associateBy { it.id } }

    LaunchedEffect(bundle.id) {
        loading = true
        // ⬇️ PATCH 3B: pakai vm.loadBundleFull() bukan vm.bundleRepoLocal
        val (_, gs, im) = vm.loadBundleFull(bundle.id)
        // ⬆️ END PATCH 3B
        groups = gs
        itemsMap = im

        // Pre-select default picks
        val initial = mutableMapOf<Long, Set<Long>>()
        gs.forEach { g ->
            val items = im[g.id] ?: emptyList()
            val defaults = items.filter { it.isDefaultPick }.map { it.id }.toSet()
            if (defaults.isNotEmpty()) {
                initial[g.id] = defaults
            } else if (g.minPilih > 0) {
    // Auto-pick first minPilih items kalau nggak ada default
    initial[g.id] = items.take(g.minPilih).map { it.id }.toSet()
}
        }
        picks = initial
        loading = false
    }

    val extraTotal = remember(picks, itemsMap) {
        var sum = 0
        picks.forEach { (gid, itemIds) ->
            val items = itemsMap[gid] ?: emptyList()
            itemIds.forEach { iid ->
                val it = items.firstOrNull { it.id == iid }
                if (it != null) sum += it.hargaExtra * it.qty
            }
        }
        sum
    }

    val totalHarga = bundle.hargaBundle + extraTotal
    val valid = groups.all { g ->
        val selected = picks[g.id]?.size ?: 0
        if (g.wajib) {
            selected in g.minPilih..g.maxPilih
        } else {
            selected <= g.maxPilih
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(bundle.nama, fontWeight = FontWeight.Bold)
                Text("Base: ${bundle.hargaBundle.rupiah()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        text = {
            if (loading) {
                Box(Modifier.fillMaxWidth().padding(Sp.xl),
                    contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Column(
                    Modifier.heightIn(max = 500.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Sp.md)
                ) {
                    groups.forEachIndexed { gIdx, g ->
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(color = BRAND, shape = CircleShape) {
                                    Text("${gIdx + 1}",
                                        modifier = Modifier.padding(
                                            horizontal = 8.dp, vertical = 2.dp
                                        ),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelMedium)
                                }
                                Spacer(Modifier.width(Sp.sm))
                                Column(Modifier.weight(1f)) {
                                    Text(g.nama,
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        if (g.wajib)
                                            "Wajib pilih ${g.minPilih}-${g.maxPilih}"
                                        else "Opsional (maks ${g.maxPilih})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(Modifier.height(Sp.xs))

                            val items = itemsMap[g.id] ?: emptyList()
                            val currentPicks = picks[g.id] ?: emptySet()
                            val isSingle = g.maxPilih == 1

                            items.forEach { item ->
                                val menu = menuById[item.menuId]
                                val selected = item.id in currentPicks
                                Row(
                                    Modifier.fillMaxWidth()
                                        .clickable {
                                            val newSet = if (isSingle) {
                                                if (selected) emptySet()
                                                else setOf(item.id)
                                            } else {
                                                if (selected) currentPicks - item.id
                                                else {
                                                    if (currentPicks.size >= g.maxPilih)
                                                        currentPicks
                                                    else currentPicks + item.id
                                                }
                                            }
                                            picks = picks + (g.id to newSet)
                                        }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selected,
                                        onClick = null
                                    )
                                    Spacer(Modifier.width(Sp.sm))
                                    Column(Modifier.weight(1f)) {
                                        Text(menu?.nama ?: "Menu #${item.menuId}",
                                            fontWeight = FontWeight.Medium,
                                            style = MaterialTheme.typography.bodyMedium)
                                        if (item.qty > 1) {
                                            Text("x${item.qty}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    if (item.hargaExtra > 0) {
                                        Text("+ ${item.hargaExtra.rupiah()}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SUCCESS,
                                            fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider()

                    // Total
                    Card(colors = CardDefaults.cardColors(containerColor = BRAND_LIGHT)) {
                        Column(Modifier.padding(Sp.md)) {
                            Row {
                                Text("Base", Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall)
                                Text(bundle.hargaBundle.rupiah(),
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            if (extraTotal > 0) {
                                Row {
                                    Text("Tambahan", Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SUCCESS)
                                    Text("+ ${extraTotal.rupiah()}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SUCCESS,
                                        fontWeight = FontWeight.Bold)
                                }
                            }
                            HorizontalDivider(Modifier.padding(vertical = 4.dp))
                            Row {
                                Text("Total", Modifier.weight(1f),
                                    fontWeight = FontWeight.Bold)
                                Text(totalHarga.rupiah(),
                                    fontWeight = FontWeight.Bold,
                                    color = BRAND,
                                    style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // Build picks list
                    val result = mutableListOf<BundlePick>()
                    picks.forEach { (gid, itemIds) ->
                        val g = groups.firstOrNull { it.id == gid } ?: return@forEach
                        val items = itemsMap[gid] ?: emptyList()
                        itemIds.forEach { iid ->
                            val item = items.firstOrNull { it.id == iid } ?: return@forEach
                            val menu = menuById[item.menuId]
                            result += BundlePick(
                                itemId = item.id,
                                groupId = gid,
                                groupNama = g.nama,
                                menuId = item.menuId,
                                menuNama = menu?.nama ?: "Menu",
                                menuHarga = menu?.harga ?: 0,
                                qty = item.qty,
                                hargaExtra = item.hargaExtra
                            )
                        }
                    }
                    onConfirm(result)
                },
                enabled = valid && !loading
            ) { Text("Tambah Paket", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

// ═══════════════════════════════════════════════════════════
// CART PANE
// ═══════════════════════════════════════════════════════════
@Composable
fun CartPane(vm: KasirViewModel, modifier: Modifier = Modifier, onBayar: () -> Unit) {
    val cart by vm.cart.collectAsState()
    val subtotal by vm.subtotal.collectAsState()
    val diskonAmount = vm.hitungDiskon(subtotal)
    val afterDiskon = (subtotal - diskonAmount).coerceAtLeast(0)
    val afterVoucher = (afterDiskon - vm.voucherAmount).coerceAtLeast(0)
    val poinRupiah = vm.poinKeRupiah(vm.poinDipakai)
    val afterPoin = (afterVoucher - poinRupiah).coerceAtLeast(0)
    val pajakAmount = vm.hitungPajak(afterPoin)
    val total = afterPoin + pajakAmount

    var noteFor by remember { mutableStateOf<CartLine?>(null) }
    var showDiskon by remember { mutableStateOf(false) }
    var showPajak by remember { mutableStateOf(false) }

    val modifierEnabled = FeatureManager.isEnabled(FeatureKey.MODIFIER)
    val diskonEnabled = FeatureManager.isEnabled(FeatureKey.DISKON)
            && Session.can(PermissionKey.DISKON)
    val pajakEnabled = FeatureManager.isEnabled(FeatureKey.PAJAK)
    val openBillEnabled = FeatureManager.isEnabled(FeatureKey.OPEN_BILL)
            && Session.can(PermissionKey.OPEN_BILL)

    Column(modifier.fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant)) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.fillMaxWidth().padding(Sp.lg)) {
                Text("Keranjang", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                Text(
                    buildString {
                        if (FeatureManager.isEnabled(FeatureKey.NOMOR_MEJA)) {
                            append(if (vm.nomorMeja.isBlank()) "Tanpa meja"
                            else "Meja ${vm.nomorMeja}")
                            append(" • ")
                        }
                        append(TipeOrder.fromId(vm.tipeOrder).label)
                        if (vm.selectedMember != null) {
                            append(" • ⭐ ${vm.selectedMember!!.nama}")
                        } else if (vm.namaPelanggan.isNotBlank()) {
                            append(" • "); append(vm.namaPelanggan)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        HorizontalDivider()

        if (cart.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.ShoppingCart, null, Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(Sp.sm))
                    Text("Keranjang kosong",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(Sp.md),
                verticalArrangement = Arrangement.spacedBy(Sp.sm)) {
                items(cart, key = { it.key }) { line ->
                    CartLineItem(line,
                        onAdd = {
                            if (line.isBundle) {
                                vm.addBundle(line.bundle!!, line.bundlePicks)
                            } else {
                                vm.add(line.menu!!, line.catatan)
                            }
                        },
                        onDecrease = { vm.decrease(line.key) },
                        onRemove = { vm.remove(line.key) },
                        onEditNote = { if (!line.isBundle) noteFor = line },
                        showNote = modifierEnabled)
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(Sp.sm),
                        modifier = Modifier.fillMaxWidth()) {
                        if (diskonEnabled) {
                            OutlinedButton(
                                onClick = { showDiskon = true },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 8.dp)) {
                                Icon(Icons.Default.LocalOffer, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    if (vm.diskonTipe == "NONE") "Diskon"
                                    else if (vm.diskonTipe == "PERSEN") "Disc ${vm.diskonValue}%"
                                    else "Disc ${vm.diskonValue.rupiah()}",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (pajakEnabled) {
                            OutlinedButton(
                                onClick = { showPajak = true },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 8.dp)) {
                                Icon(Icons.Default.Receipt, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    if (vm.pajakPersen == 0) "Pajak"
                                    else "PPN ${vm.pajakPersen}%",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider()
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().padding(Sp.lg)) {
                if (subtotal > 0) {
                    Row {
                        Text("Subtotal", Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall)
                        Text(subtotal.rupiah(),
                            style = MaterialTheme.typography.bodySmall)
                    }
                    if (diskonAmount > 0) {
                        Row {
                            Text("Diskon", Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall, color = DANGER)
                            Text("- ${diskonAmount.rupiah()}",
                                style = MaterialTheme.typography.bodySmall, color = DANGER)
                        }
                    }
                    if (vm.voucherAmount > 0) {
                        Row {
                            Text("Voucher ${vm.voucherKode}", Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall, color = DANGER)
                            Text("- ${vm.voucherAmount.rupiah()}",
                                style = MaterialTheme.typography.bodySmall, color = DANGER)
                        }
                    }
                    if (poinRupiah > 0) {
                        Row {
                            Text("Poin ${vm.poinDipakai}", Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall, color = DANGER)
                            Text("- ${poinRupiah.rupiah()}",
                                style = MaterialTheme.typography.bodySmall, color = DANGER)
                        }
                    }
                    if (pajakAmount > 0) {
                        Row {
                            Text("Pajak ${vm.pajakPersen}%", Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall)
                            Text(pajakAmount.rupiah(),
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(Modifier.height(Sp.xs))
                }
                Row {
                    Text("Total", Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                    Text(total.rupiah(), fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge, color = BRAND)
                }
                Spacer(Modifier.height(Sp.md))
                Row(horizontalArrangement = Arrangement.spacedBy(Sp.sm)) {
                    if (openBillEnabled) {
                        OutlinedButton(onClick = { vm.simpanOpenBill { } },
                            enabled = cart.isNotEmpty(),
                            modifier = Modifier.weight(1f)) {
                            Text("Open Bill", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Button(onClick = onBayar, enabled = cart.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = BRAND)) {
                        Text("Bayar")
                    }
                }
            }
        }
    }

    noteFor?.let { line ->
        NoteDialog(
            initial = line.catatan, menuName = line.menu?.nama ?: "",
            onDismiss = { noteFor = null },
            onSave = { newNote ->
                val oldQty = line.qty
                vm.remove(line.key)
                repeat(oldQty) { vm.add(line.menu!!, newNote) }
                noteFor = null
            })
    }
    if (showDiskon) DiskonDialog(vm, subtotal) { showDiskon = false }
    if (showPajak) PajakDialog(vm) { showPajak = false }
}

// ═══════════════════════════════════════════════════════════
// CART LINE ITEM — support bundle & item
// ═══════════════════════════════════════════════════════════
@Composable
private fun CartLineItem(
    line: CartLine,
    onAdd: () -> Unit,
    onDecrease: () -> Unit,
    onRemove: () -> Unit,
    onEditNote: () -> Unit,
    showNote: Boolean
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        Modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(Rd.md),
        elevation = CardDefaults.cardElevation(defaultElevation = El.card)
    ) {
        Column(Modifier.padding(Sp.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (line.isBundle) {
                    Surface(
                        color = Color(0xFFFF8C42),
                        shape = RoundedCornerShape(Rd.xs)
                    ) {
                        Text(
                            "${BundleTipe.fromId(line.bundle!!.tipe).emoji} PAKET",
                            modifier = Modifier.padding(
                                horizontal = 6.dp, vertical = 2.dp
                            ),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.width(Sp.xs))
                }
                Column(Modifier.weight(1f)) {
                    Text(line.nama, fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium)
                    Text(line.hargaSatuan.rupiah(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (line.isBundle && line.bundlePicks.isNotEmpty()) {
                    IconButton(onClick = { expanded = !expanded },
                        modifier = Modifier.size(28.dp)) {
                        Icon(
                            if (expanded) Icons.Default.ExpandLess
                            else Icons.Default.ExpandMore,
                            null,
                            Modifier.size(18.dp),
                            tint = BRAND
                        )
                    }
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, null, Modifier.size(16.dp))
                }
            }

            // Note untuk item biasa
            if (!line.isBundle && line.catatan.isNotBlank()) {
                Spacer(Modifier.height(Sp.xs))
                Surface(color = BRAND_LIGHT, shape = RoundedCornerShape(Rd.xs)) {
                    Text("Catatan: ${line.catatan}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall)
                }
            }

            // Expand bundle sub-items
            AnimatedVisibility(visible = expanded && line.isBundle) {
                Column(
                    Modifier.fillMaxWidth()
                        .padding(top = Sp.xs)
                        .animateContentSize(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    line.bundlePicks.forEach { pick ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("  ▸ ", color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall)
                            Text(pick.menuNama,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f))
                            Text("×${pick.qty}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (pick.hargaExtra > 0) {
                                Spacer(Modifier.width(Sp.xs))
                                Text("+${pick.hargaExtra.rupiah()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SUCCESS,
                                    fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(Sp.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDecrease, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Remove, null, Modifier.size(16.dp))
                }
                Text(line.qty.toString(), fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = Sp.sm))
                IconButton(onClick = onAdd, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                }
                Spacer(Modifier.weight(1f))
                if (showNote && !line.isBundle) {
                    IconButton(onClick = onEditNote, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Edit, null, Modifier.size(16.dp), tint = BRAND)
                    }
                }
                Text(line.subtotal.rupiah(), fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// DISKON DIALOG
// ═══════════════════════════════════════════════════════════
@Composable
private fun DiskonDialog(vm: KasirViewModel, subtotal: Int, onDismiss: () -> Unit) {
    var tipe by remember { mutableStateOf(vm.diskonTipe) }
    var valueText by remember {
        mutableStateOf(if (vm.diskonValue > 0) vm.diskonValue.toString() else "")
    }
    val value = valueText.toIntOrNull() ?: 0
    val preview = when (tipe) {
        "NOMINAL" -> value.coerceIn(0, subtotal)
        "PERSEN" -> subtotal * value.coerceIn(0, 100) / 100
        else -> 0
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Atur Diskon") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Sp.sm)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Sp.xs)) {
                    FilterChip(selected = tipe == "NONE",
                        onClick = { tipe = "NONE"; valueText = "" },
                        label = { Text("Tanpa Diskon",
                            style = MaterialTheme.typography.bodySmall) })
                    FilterChip(selected = tipe == "NOMINAL",
                        onClick = { tipe = "NOMINAL" },
                        label = { Text("Rp", style = MaterialTheme.typography.bodySmall) })
                    FilterChip(selected = tipe == "PERSEN",
                        onClick = { tipe = "PERSEN" },
                        label = { Text("%", style = MaterialTheme.typography.bodySmall) })
                }
                if (tipe != "NONE") {
                    OutlinedTextField(valueText,
                        { valueText = it.filter { c -> c.isDigit() }.take(7) },
                        label = { Text(if (tipe == "PERSEN") "Persen (0-100)"
                        else "Nominal (Rp)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (tipe == "PERSEN") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(Sp.xs)) {
                            items(listOf(5, 10, 15, 20, 25, 50)) { p ->
                                AssistChip(onClick = { valueText = p.toString() },
                                    label = { Text("$p%",
                                        style = MaterialTheme.typography.bodySmall) })
                            }
                        }
                    }
                }
                if (preview > 0) {
                    Card(colors = CardDefaults.cardColors(containerColor = BRAND_LIGHT)) {
                        Column(Modifier.padding(Sp.sm)) {
                            Text("Diskon: - ${preview.rupiah()}",
                                color = DANGER, fontWeight = FontWeight.SemiBold)
                            Text("Harga jadi: ${(subtotal - preview).rupiah()}",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (tipe == "NONE" || value <= 0) {
                    vm.diskonTipe = "NONE"; vm.diskonValue = 0
                } else {
                    vm.diskonTipe = tipe
                    vm.diskonValue = if (tipe == "PERSEN") value.coerceIn(0, 100) else value
                }
                onDismiss()
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } })
}

// ═══════════════════════════════════════════════════════════
// PAJAK DIALOG
// ═══════════════════════════════════════════════════════════
@Composable
private fun PajakDialog(vm: KasirViewModel, onDismiss: () -> Unit) {
    var valueText by remember {
        mutableStateOf(if (vm.pajakPersen > 0) vm.pajakPersen.toString() else "")
    }
    val value = valueText.toIntOrNull() ?: 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Atur Pajak / PPN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Sp.sm)) {
                OutlinedTextField(valueText,
                    { valueText = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("Persen PPN (0-100)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Sp.xs)) {
                    items(listOf(0, 5, 10, 11, 12, 15)) { p ->
                        AssistChip(
                            onClick = { valueText = if (p == 0) "" else p.toString() },
                            label = { Text(if (p == 0) "Tanpa PPN" else "$p%",
                                style = MaterialTheme.typography.bodySmall) })
                    }
                }
                Text("Pajak dihitung dari harga setelah diskon.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.pajakPersen = value.coerceIn(0, 100); onDismiss()
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } })
}

// ═══════════════════════════════════════════════════════════
// NOTE DIALOG
// ═══════════════════════════════════════════════════════════
@Composable
private fun NoteDialog(
    initial: String, menuName: String,
    onDismiss: () -> Unit, onSave: (String) -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Catatan untuk $menuName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Sp.sm)) {
                OutlinedTextField(text, { text = it },
                    label = { Text("Catatan") },
                    placeholder = { Text("cth: pedas, tanpa bawang") },
                    maxLines = 3, modifier = Modifier.fillMaxWidth())
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Sp.xs)) {
                    items(listOf("Pedas", "Tanpa bawang", "Extra nasi",
                        "Tanpa sambal", "Goreng kering")) { t ->
                        AssistChip(onClick = {
                            text = if (text.isBlank()) t else "$text, $t"
                        }, label = { Text(t,
                            style = MaterialTheme.typography.bodySmall) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text.trim()) }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } })
}

// ═══════════════════════════════════════════════════════════
// KERANJANG SCREEN (full screen untuk mobile)
// ═══════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeranjangScreen(vm: KasirViewModel, onBack: () -> Unit, onBayar: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Keranjang") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                })
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            CartPane(vm, Modifier.fillMaxSize(), onBayar)
        }
    }
}

// ═══════════════════════════════════════════════════════════
// BAYAR SCREEN
// ═══════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BayarScreen(
    vm: KasirViewModel,
    onBack: () -> Unit,
    onSelesai: (Long) -> Unit
) {
    val ctx = LocalContext.current
    val subtotal by vm.subtotal.collectAsState()
    val diskonAmount = vm.hitungDiskon(subtotal)
    val afterDiskon = (subtotal - diskonAmount).coerceAtLeast(0)
    val afterVoucher = (afterDiskon - vm.voucherAmount).coerceAtLeast(0)
    val poinRupiah = vm.poinKeRupiah(vm.poinDipakai)
    val afterPoin = (afterVoucher - poinRupiah).coerceAtLeast(0)
    val pajakAmount = vm.hitungPajak(afterPoin)
    val total = afterPoin + pajakAmount

    var metode by remember { mutableStateOf(PaymentMethod.CASH.id) }
    var dibayarText by remember { mutableStateOf("") }
    var voucherInput by remember { mutableStateOf("") }
    var showMemberPicker by remember { mutableStateOf(false) }
    var showPoinDialog by remember { mutableStateOf(false) }

    val dibayar = dibayarText.toIntOrNull() ?: 0
    val kembalian = (dibayar - total).coerceAtLeast(0)
    val cukup = if (metode == PaymentMethod.CASH.id) dibayar >= total else true

    val crmEnabled = FeatureManager.isEnabled(FeatureKey.MEMBER) ||
            FeatureManager.isEnabled(FeatureKey.HUTANG_PELANGGAN)
    val voucherEnabled = FeatureManager.isEnabled(FeatureKey.VOUCHER)

    val activeMethods = remember(vm.metodeAktif, vm.selectedMember) {
        val base = if (vm.metodeAktif.isEmpty()) PaymentMethod.values().toList()
        else PaymentMethod.values().filter { it.id in vm.metodeAktif }
        if (vm.selectedMember == null || !crmEnabled) base.filter { it != PaymentMethod.HUTANG }
        else base
    }

    LaunchedEffect(activeMethods) {
        if (activeMethods.none { it.id == metode } && activeMethods.isNotEmpty()) {
            metode = activeMethods.first().id
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Pembayaran") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                })
        }
    ) { pad ->
        Column(Modifier.padding(pad).padding(Sp.lg).fillMaxSize()
            .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Sp.md)) {

            if (crmEnabled) {
                Card(colors = CardDefaults.cardColors(
                    containerColor = if (vm.selectedMember != null) BRAND_LIGHT
                    else MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(Modifier.fillMaxWidth().padding(Sp.md),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, null, tint = BRAND)
                        Spacer(Modifier.width(Sp.md))
                        Column(Modifier.weight(1f)) {
                            if (vm.selectedMember == null) {
                                Text("Tanpa Member", fontWeight = FontWeight.SemiBold)
                                Text("Tap untuk pilih member",
                                    style = MaterialTheme.typography.bodySmall)
                            } else {
                                val m = vm.selectedMember!!
                                Text(m.nama, fontWeight = FontWeight.SemiBold)
                                Text("⭐ ${m.poin} poin • ${MemberTier.fromId(m.tier).label}",
                                    style = MaterialTheme.typography.bodySmall)
                                if (m.hutang > 0) {
                                    Text("💳 Hutang: ${m.hutang.rupiah()}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = DANGER)
                                }
                            }
                        }
                        if (vm.selectedMember != null) {
                            IconButton(onClick = { vm.setMember(null) }) {
                                Icon(Icons.Default.Close, null)
                            }
                        }
                        TextButton(onClick = { showMemberPicker = true }) {
                            Text(if (vm.selectedMember == null) "Pilih" else "Ganti")
                        }
                    }
                }
            }

            if (voucherEnabled) {
                Card {
                    Column(Modifier.padding(Sp.md)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocalOffer, null, tint = BRAND,
                                modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(Sp.sm))
                            Text("Voucher", fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(Sp.sm))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = if (vm.voucherKode.isNotBlank()) vm.voucherKode
                                else voucherInput,
                                onValueChange = {
                                    voucherInput = it.uppercase().filter { c ->
                                        c.isLetterOrDigit()
                                    }.take(20)
                                },
                                placeholder = { Text("Kode voucher") },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium,
                                isError = vm.voucherError != null,
                                modifier = Modifier.weight(1f))
                            Spacer(Modifier.width(Sp.sm))
                            if (vm.voucherKode.isBlank()) {
                                Button(
                                    onClick = { vm.applyVoucher(voucherInput, subtotal) },
                                    colors = ButtonDefaults.buttonColors(containerColor = BRAND),
                                    enabled = voucherInput.isNotBlank()
                                ) { Text("Pakai") }
                            } else {
                                OutlinedButton(onClick = {
                                    vm.clearVoucher(); voucherInput = ""
                                }) { Text("Batal") }
                            }
                        }
                        vm.voucherError?.let {
                            Spacer(Modifier.height(Sp.xs))
                            Text(it, color = DANGER,
                                style = MaterialTheme.typography.bodySmall)
                        }
                        if (vm.voucherAmount > 0) {
                            Spacer(Modifier.height(Sp.xs))
                            Text("Diskon voucher: - ${vm.voucherAmount.rupiah()}",
                                color = DANGER,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            if (vm.selectedMember != null && vm.selectedMember!!.poin > 0) {
                Card(colors = CardDefaults.cardColors(containerColor = BRAND_LIGHT)) {
                    Row(Modifier.fillMaxWidth().padding(Sp.md),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, null, tint = BRAND)
                        Spacer(Modifier.width(Sp.sm))
                        Column(Modifier.weight(1f)) {
                            Text("Tukar Poin", fontWeight = FontWeight.SemiBold)
                            Text(
                                if (vm.poinDipakai > 0)
                                    "${vm.poinDipakai} poin = ${poinRupiah.rupiah()}"
                                else "Kamu punya ${vm.selectedMember!!.poin} poin",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { showPoinDialog = true }) {
                            Text(if (vm.poinDipakai > 0) "Ubah" else "Pakai")
                        }
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = BRAND_LIGHT)) {
                Column(Modifier.padding(Sp.lg),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (subtotal > 0) RingkasRow("Subtotal", subtotal.rupiah())
                    if (diskonAmount > 0)
                        RingkasRow("Diskon", "- ${diskonAmount.rupiah()}", DANGER)
                    if (vm.voucherAmount > 0)
                        RingkasRow("Voucher", "- ${vm.voucherAmount.rupiah()}", DANGER)
                    if (poinRupiah > 0)
                        RingkasRow("Poin", "- ${poinRupiah.rupiah()}", DANGER)
                    if (pajakAmount > 0)
                        RingkasRow("PPN ${vm.pajakPersen}%", pajakAmount.rupiah())
                    HorizontalDivider(Modifier.padding(vertical = Sp.xs))
                    Row {
                        Text("Total tagihan", Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                        Text(total.rupiah(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold, color = BRAND)
                    }
                }
            }

            Text("Metode bayar", fontWeight = FontWeight.SemiBold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Sp.sm)) {
                items(activeMethods) { m ->
                    FilterChip(selected = metode == m.id,
                        onClick = { metode = m.id },
                        label = { Text(m.label,
                            style = MaterialTheme.typography.bodySmall) })
                }
            }
            if (metode == PaymentMethod.HUTANG.id && vm.selectedMember == null) {
                Text("Pilih member dulu untuk metode hutang",
                    color = DANGER, style = MaterialTheme.typography.bodySmall)
            }

            if (metode == PaymentMethod.CASH.id) {
                OutlinedTextField(dibayarText,
                    { dibayarText = it.filter { c -> c.isDigit() } },
                    label = { Text("Uang diterima") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                QuickCash(total) { dibayarText = it.toString() }
                Row {
                    Text("Kembalian", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Text(kembalian.rupiah(), fontWeight = FontWeight.Bold, color = BRAND)
                }
            }

            Spacer(Modifier.height(Sp.sm))
            Button(
                onClick = {
                    vm.checkout(metode,
                        if (metode == PaymentMethod.CASH.id) dibayar else total
                    ) { id -> onSelesai(id) }
                },
                enabled = cukup && !(metode == PaymentMethod.HUTANG.id
                        && vm.selectedMember == null) && activeMethods.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BRAND)
            ) { Text("Konfirmasi Bayar", fontWeight = FontWeight.Bold) }
        }
    }

    if (showMemberPicker) {
        MemberPickerDialog(
            crmRepo = (ctx.applicationContext as IyonzApp).crmRepo,
            onDismiss = { showMemberPicker = false },
            onPick = { m -> vm.setMember(m); showMemberPicker = false })
    }

    if (showPoinDialog && vm.selectedMember != null) {
        PoinPakaiDialog(
            member = vm.selectedMember!!,
            currentPoin = vm.poinDipakai,
            maxPoin = minOf(
                vm.selectedMember!!.poin,
                LoyaltyConfig.rupiahKePoin(afterVoucher)),
            onDismiss = { showPoinDialog = false },
            onConfirm = { p -> vm.poinDipakai = p; showPoinDialog = false })
    }
}

@Composable
private fun RingkasRow(label: String, value: String,
                        color: Color = Color.Unspecified) {
    Row {
        Text(label, Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface
            else color)
        Text(value, style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface
            else color)
    }
}

@Composable
private fun QuickCash(total: Int, onPick: (Int) -> Unit) {
    val suggestions = remember(total) {
        val rounded = ((total + 4999) / 5000) * 5000
        listOf(total, rounded, 50000, 100000).distinct().filter { it >= total }
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(Sp.sm)) {
        items(suggestions.take(4)) { s ->
            AssistChip(onClick = { onPick(s) }, label = { Text(s.rupiah()) })
        }
    }
}

// ═══════════════════════════════════════════════════════════
// MEMBER PICKER
// ═══════════════════════════════════════════════════════════
@Composable
private fun MemberPickerDialog(
    crmRepo: CrmRepository,
    onDismiss: () -> Unit,
    onPick: (Member) -> Unit
) {
    val scope = rememberCoroutineScope()
    var search by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Member>>(emptyList()) }
    var showAddMember by remember { mutableStateOf(false) }

    LaunchedEffect(search) {
        results = if (search.isBlank()) crmRepo.activeMembers.first()
        else crmRepo.searchMember(search)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pilih Member") },
        text = {
            Column(Modifier.heightIn(max = 450.dp),
                verticalArrangement = Arrangement.spacedBy(Sp.sm)) {
                OutlinedTextField(
                    value = search, onValueChange = { search = it },
                    placeholder = { Text("Cari nama / telepon...",
                        style = MaterialTheme.typography.bodySmall) },
                    leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(18.dp)) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().height(52.dp))
                OutlinedButton(
                    onClick = { showAddMember = true },
                    modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.PersonAdd, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Member Baru")
                }
                if (results.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(Sp.xl),
                        contentAlignment = Alignment.Center) {
                        Text(if (search.isBlank()) "Belum ada member"
                            else "Nggak ada yang cocok",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(Sp.xs)) {
                        items(results, key = { it.id }) { m ->
                            Card(Modifier.fillMaxWidth().clickable { onPick(m) }) {
                                Row(Modifier.padding(Sp.sm),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(36.dp).clip(CircleShape)
                                        .background(BRAND_LIGHT),
                                        contentAlignment = Alignment.Center) {
                                        Text(m.nama.take(1).uppercase(),
                                            color = BRAND, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(Modifier.width(Sp.sm))
                                    Column(Modifier.weight(1f)) {
                                        Text(m.nama, fontWeight = FontWeight.SemiBold,
                                            style = MaterialTheme.typography.bodyMedium)
                                        Text(buildString {
                                            if (m.telepon.isNotBlank())
                                                append("${m.telepon} • ")
                                            append("⭐ ${m.poin}")
                                        }, style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } })
    if (showAddMember) {
        MemberQuickAddDialog(
            onDismiss = { showAddMember = false },
            onSave = { newMember ->
                scope.launch {
                    crmRepo.saveMember(newMember)
                    showAddMember = false
                }
            })
    }
}

@Composable
private fun MemberQuickAddDialog(
    onDismiss: () -> Unit,
    onSave: (Member) -> Unit
) {
    var nama by remember { mutableStateOf("") }
    var telepon by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Member Baru") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Sp.sm)) {
                OutlinedTextField(nama, { nama = it },
                    label = { Text("Nama *") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(telepon,
                    { telepon = it.filter { c -> c.isDigit() || c == '+' || c == '-' } },
                    label = { Text("Telepon / WhatsApp") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                err?.let { Text(it, color = DANGER,
                    style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (nama.isBlank()) { err = "Nama wajib"; return@TextButton }
                onSave(Member(nama = nama.trim(), telepon = telepon.trim()))
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } })
}

@Composable
private fun PoinPakaiDialog(
    member: Member, currentPoin: Int, maxPoin: Int,
    onDismiss: () -> Unit, onConfirm: (Int) -> Unit
) {
    var poinText by remember {
        mutableStateOf(if (currentPoin > 0) currentPoin.toString() else "")
    }
    val poin = poinText.toIntOrNull() ?: 0
    val rupiah = LoyaltyConfig.poinKeRupiah(poin)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tukar Poin") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Sp.sm)) {
                Text("Poin tersedia: ${member.poin}",
                    style = MaterialTheme.typography.bodySmall)
                Text("Maks dipakai order ini: $maxPoin",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("1 poin = ${LoyaltyConfig.RUPIAH_PER_POIN.rupiah()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(poinText,
                    { poinText = it.filter { c -> c.isDigit() }.take(6) },
                    label = { Text("Jumlah poin") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(Sp.xs)) {
                    listOf(0, 10, 50, maxPoin).distinct()
                        .filter { it in 0..maxPoin }.take(4).forEach { p ->
                        AssistChip(
                            onClick = { poinText = if (p == 0) "" else p.toString() },
                            label = { Text(if (p == 0) "Nol" else "$p",
                                style = MaterialTheme.typography.bodySmall) })
                    }
                }
                if (poin > 0) {
                    Card(colors = CardDefaults.cardColors(containerColor = BRAND_LIGHT)) {
                        Column(Modifier.padding(Sp.sm)) {
                            Text("Diskon poin: ${rupiah.rupiah()}",
                                fontWeight = FontWeight.Bold, color = BRAND)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(poin.coerceIn(0, maxPoin)) },
                enabled = poin <= maxPoin
            ) { Text("Pakai") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } })
}

// ═══════════════════════════════════════════════════════════
// OPEN BILL SCREEN
// ═══════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenBillScreen(vm: OpenBillViewModel, onPick: (Long) -> Unit) {
    val bills by vm.openBills.collectAsState()
    Scaffold(topBar = { TopAppBar(title = { Text("Open Bill") }) }) { pad ->
        if (bills.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize()) {
                EmptyState(
                    icon = Icons.Default.ReceiptLong,
                    title = "Belum ada open bill",
                    subtitle = "Pesanan yang belum dibayar akan muncul di sini"
                )
            }
        } else {
            LazyColumn(Modifier.padding(pad).fillMaxSize(),
                contentPadding = PaddingValues(Sp.md),
                verticalArrangement = Arrangement.spacedBy(Sp.sm)) {
                items(bills, key = { it.id }) { o ->
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = Rd.card,
                        elevation = CardDefaults.cardElevation(defaultElevation = El.card)
                    ) {
                        Row(Modifier.padding(Sp.lg),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (o.nomorMeja.isNotBlank()) "Meja ${o.nomorMeja}"
                                    else "Tanpa meja",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium)
                                Text(buildString {
                                    append(TipeOrder.fromId(o.tipeOrder).label)
                                    if (o.memberNama.isNotBlank()) {
                                        append(" • ⭐ ${o.memberNama}")
                                    } else if (o.namaPelanggan.isNotBlank()) {
                                        append(" • "); append(o.namaPelanggan)
                                    }
                                }, style = MaterialTheme.typography.bodySmall)
                                Text(o.timestamp.tanggal(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(o.total.rupiah(), fontWeight = FontWeight.Bold,
                                    color = BRAND)
                                Spacer(Modifier.height(Sp.xs))
                                Button(onClick = { onPick(o.id) },
                                    colors = ButtonDefaults.buttonColors(containerColor = BRAND),
                                    contentPadding = PaddingValues(
                                        horizontal = Sp.md, vertical = 4.dp)) {
                                    Text("Bayar", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// KELOLA MENU
// ═══════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuScreen(vm: MenuViewModel, onEdit: (Long?) -> Unit) {
    val menu by vm.menu.collectAsState()
    val canEdit = Session.can(PermissionKey.KELOLA_MENU)
    Scaffold(
        topBar = { TopAppBar(title = { Text("Kelola Menu") }) },
        floatingActionButton = {
            if (canEdit) {
                ExtendedFloatingActionButton(
                    onClick = { onEdit(null) },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("Menu baru") },
                    containerColor = BRAND)
            }
        }
    ) { pad ->
        if (menu.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize()) {
                EmptyState(
                    icon = Icons.Default.RestaurantMenu,
                    title = "Belum ada menu",
                    subtitle = "Tambahkan menu pertama untuk mulai jualan",
                    ctaLabel = if (canEdit) "Tambah Menu" else null,
                    onCta = if (canEdit) { { onEdit(null) } } else null
                )
            }
        } else {
            LazyColumn(Modifier.padding(pad).fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Sp.md, end = Sp.md,
                    top = Sp.md, bottom = 100.dp
                ),
                verticalArrangement = Arrangement.spacedBy(Sp.sm)) {
                items(menu, key = { it.id }) { m ->
                    MenuRow(m, onToggle = { vm.toggle(m) },
                        onEdit = { if (canEdit) onEdit(m.id) },
                        onDelete = { if (canEdit) vm.delete(m) },
                        canEdit = canEdit)
                }
            }
        }
    }
}

@Composable
private fun MenuRow(
    m: MenuItem, onToggle: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit,
    canEdit: Boolean
) {
    val stokHabis = m.trackStok && m.stok <= 0
    val stokLow = m.trackStok && m.stok in 1..m.stokMinimal
    Card(
        Modifier.fillMaxWidth(),
        shape = Rd.card,
        elevation = CardDefaults.cardElevation(defaultElevation = El.card)
    ) {
        Row(Modifier.padding(Sp.md), verticalAlignment = Alignment.CenterVertically) {
            if (m.fotoUri != null) {
                AsyncImage(model = m.fotoUri, contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(Rd.sm)))
            } else {
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(Rd.sm))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center) { Text("🍽️") }
            }
            Spacer(Modifier.width(Sp.md))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(m.nama, fontWeight = FontWeight.SemiBold)
                    if (m.trackStok) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            color = when {
                                stokHabis -> DANGER
                                stokLow -> WARNING
                                else -> BRAND
                            },
                            shape = RoundedCornerShape(Rd.xs)
                        ) {
                            Text("${m.stok}",
                                modifier = Modifier.padding(
                                    horizontal = 5.dp, vertical = 1.dp
                                ),
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Text(m.harga.rupiah(), color = BRAND)
                Text(m.kategori + if (m.barcode.isNotBlank()) " • 🔖" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = m.tersedia, onCheckedChange = { onToggle() }, enabled = canEdit)
            if (canEdit) {
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, null) }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null) }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// EDIT MENU SCREEN
// ═══════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditMenuScreen(vm: MenuViewModel, menuId: Long?, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val kategoriList by vm.kategori.collectAsState()

    var nama by remember { mutableStateOf("") }
    var hargaText by remember { mutableStateOf("") }
    var hargaBeliText by remember { mutableStateOf("") }
    var kategoriNama by remember { mutableStateOf("Umum") }
    var kategoriId by remember { mutableStateOf(0L) }
    var fotoUri by remember { mutableStateOf<String?>(null) }
    var originalPhotoUri by remember { mutableStateOf<String?>(null) }
    var tersedia by remember { mutableStateOf(true) }
    var barcode by remember { mutableStateOf("") }
    var trackStok by remember { mutableStateOf(false) }
    var stokText by remember { mutableStateOf("0") }
    var stokMinimalText by remember { mutableStateOf("5") }
    var loaded by remember { mutableStateOf(menuId == null) }
    var showKategoriDropdown by remember { mutableStateOf(false) }
    var showBarcodeScan by remember { mutableStateOf(false) }
    var barcodeError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    val barcodeEnabled = FeatureManager.isEnabled(FeatureKey.BARCODE)
    val harga = hargaText.toIntOrNull() ?: 0
    val formValid = nama.isNotBlank() && harga > 0
    val formError = when {
        nama.isBlank() -> "Nama menu wajib diisi"
        hargaText.isBlank() -> "Harga jual wajib diisi"
        harga <= 0 -> "Harga harus lebih dari 0"
        else -> null
    }

    LaunchedEffect(menuId) {
        if (menuId != null) {
            vm.get(menuId)?.let {
                nama = it.nama; hargaText = it.harga.toString()
                hargaBeliText = if (it.hargaBeli > 0) it.hargaBeli.toString() else ""
                kategoriNama = it.kategori; kategoriId = it.kategoriId
                fotoUri = it.fotoUri; tersedia = it.tersedia
                originalPhotoUri = it.fotoUri
                trackStok = it.trackStok
                stokText = it.stok.toString()
                stokMinimalText = it.stokMinimal.toString()
                barcode = it.barcode
            }
            loaded = true
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                if (MenuPhotoManager.isInternalPath(fotoUri)) {
                    MenuPhotoManager.deleteByPath(fotoUri)
                }
                val internal = MenuPhotoManager.copyToInternal(ctx, uri, menuId ?: 0L)
                fotoUri = internal ?: uri.toString()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(if (menuId == null) "Menu Baru" else "Edit Menu") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                })
        }
    ) { pad ->
        if (!loaded) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(Modifier.padding(pad).padding(Sp.lg).fillMaxSize()
                .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Sp.md)) {
                fotoUri?.let { uri ->
                    AsyncImage(model = uri, contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(180.dp)
                            .clip(RoundedCornerShape(Rd.md)))
                }
                OutlinedButton(onClick = { picker.launch(arrayOf("image/*")) },
                    modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Photo, null)
                    Spacer(Modifier.width(Sp.sm))
                    Text(if (fotoUri == null) "Pilih Foto dari Galeri" else "Ganti Foto")
                }
                OutlinedTextField(nama, { nama = it },
                    label = { Text("Nama menu *") },
                    isError = nama.isBlank() && hargaText.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                Box {
                    OutlinedTextField(
                        value = kategoriNama, onValueChange = {}, readOnly = true,
                        label = { Text("Kategori") },
                        trailingIcon = {
                            IconButton(onClick = { showKategoriDropdown = true }) {
                                Icon(Icons.Default.ArrowDropDown, null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth())
                    Box(Modifier.matchParentSize().clickable { showKategoriDropdown = true })
                }
                DropdownMenu(expanded = showKategoriDropdown,
                    onDismissRequest = { showKategoriDropdown = false }) {
                    if (kategoriList.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("Belum ada kategori — bikin dulu di Setelan") },
                            onClick = { showKategoriDropdown = false })
                    } else {
                        kategoriList.forEach { k ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(14.dp).clip(CircleShape)
                                            .background(k.warnaHex.toColorSafe(BRAND)))
                                        Spacer(Modifier.width(Sp.sm))
                                        Text(k.nama)
                                    }
                                },
                                onClick = {
                                    kategoriNama = k.nama
                                    kategoriId = k.id
                                    showKategoriDropdown = false
                                })
                        }
                    }
                }
                OutlinedTextField(hargaText,
                    { hargaText = it.filter { c -> c.isDigit() } },
                    label = { Text("Harga jual *") },
                    isError = hargaText.isNotBlank() && harga <= 0,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(hargaBeliText,
                    { hargaBeliText = it.filter { c -> c.isDigit() } },
                    label = { Text("Harga beli / HPP (opsional)") },
                    supportingText = { Text("Dipakai untuk hitung laba di Laporan",
                        style = MaterialTheme.typography.labelSmall) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(), singleLine = true)

                if (barcodeEnabled) {
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Sp.sm),
                        verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = barcode,
                            onValueChange = {
                                barcode = it.filter { c -> c.isLetterOrDigit() }.take(50)
                                barcodeError = null
                            },
                            label = { Text("Barcode / SKU (opsional)") },
                            placeholder = { Text("Scan atau ketik manual") },
                            singleLine = true,
                            isError = barcodeError != null,
                            modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = { showBarcodeScan = true },
                            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(Rd.sm))
                                .background(BRAND)) {
                            Icon(Icons.Default.QrCodeScanner, null, tint = Color.White)
                        }
                    }
                }

                Card(colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(Sp.md),
                        verticalArrangement = Arrangement.spacedBy(Sp.sm)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Track Stok", fontWeight = FontWeight.SemiBold)
                                Text("Pantau & potong stok otomatis saat jual",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = trackStok, onCheckedChange = { trackStok = it })
                        }
                        if (trackStok) {
                            Row(horizontalArrangement = Arrangement.spacedBy(Sp.sm)) {
                                OutlinedTextField(
                                    value = stokText,
                                    onValueChange = {
                                        stokText = it.filter { c -> c.isDigit() }.take(7)
                                    },
                                    label = { Text("Stok") },
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Number),
                                    singleLine = true, modifier = Modifier.weight(1f))
                                OutlinedTextField(
                                    value = stokMinimalText,
                                    onValueChange = {
                                        stokMinimalText = it.filter { c -> c.isDigit() }.take(5)
                                    },
                                    label = { Text("Stok min") },
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Number),
                                    singleLine = true, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Tersedia", Modifier.weight(1f))
                    Switch(checked = tersedia, onCheckedChange = { tersedia = it })
                }

                formError?.let {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(Rd.sm),
                        modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(Sp.sm),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(it, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }

                Spacer(Modifier.height(Sp.sm))
                Button(
                    onClick = {
                        if (!formValid || saving) return@Button
                        saving = true
                        val hargaBeli = hargaBeliText.toIntOrNull() ?: 0
                        val stok = if (trackStok) stokText.toIntOrNull() ?: 0 else 0
                        val stokMin = if (trackStok) stokMinimalText.toIntOrNull() ?: 5 else 5
                        if (menuId != null && originalPhotoUri != null
                            && originalPhotoUri != fotoUri
                            && MenuPhotoManager.isInternalPath(originalPhotoUri)) {
                            MenuPhotoManager.deleteByPath(originalPhotoUri)
                        }
                        vm.save(MenuItem(
                            id = menuId ?: 0,
                            nama = nama.trim(), harga = harga,
                            hargaBeli = hargaBeli,
                            kategori = kategoriNama.trim().ifBlank { "Umum" },
                            kategoriId = kategoriId,
                            fotoUri = fotoUri, tersedia = tersedia,
                            trackStok = trackStok, stok = stok,
                            stokMinimal = stokMin, barcode = barcode.trim()
                        )) { onBack() }
                    },
                    enabled = formValid && !saving,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BRAND,
                        disabledContainerColor = BRAND.copy(alpha = 0.4f))
                ) {
                    if (saving) {
                        CircularProgressIndicator(Modifier.size(20.dp),
                            color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Simpan", fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(Sp.lg))
            }
        }
    }

    if (showBarcodeScan) {
        BarcodeScannerDialog(
            title = "Scan Barcode Produk",
            hintText = "Arahkan kamera ke barcode produk",
            onResult = { code ->
                barcode = code; barcodeError = null; showBarcodeScan = false
            },
            onDismiss = { showBarcodeScan = false })
    }
}

// ═══════════════════════════════════════════════════════════
// RIWAYAT
// ═══════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RiwayatScreen(vm: RiwayatViewModel) {
    val orders by vm.orders.collectAsState()
    var selected by remember { mutableStateOf<Order?>(null) }
    Scaffold(topBar = { TopAppBar(title = { Text("Riwayat Transaksi") }) }) { pad ->
        if (orders.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize()) {
                EmptyState(
                    icon = Icons.Default.History,
                    title = "Belum ada transaksi",
                    subtitle = "Transaksi yang sudah dibayar akan muncul di sini"
                )
            }
        } else {
            LazyColumn(
                Modifier.padding(pad).fillMaxSize(),
                contentPadding = PaddingValues(Sp.md),
                verticalArrangement = Arrangement.spacedBy(Sp.sm)
            ) {
                items(orders, key = { it.id }) { o ->
                    RiwayatCard(o) { selected = o }
                }
            }
        }
    }
    selected?.let { order ->
        OrderDetailDialog(order = order, vm = vm, onDismiss = { selected = null })
    }
}

@Composable
private fun RiwayatCard(o: Order, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = Rd.card,
        elevation = CardDefaults.cardElevation(defaultElevation = El.card)
    ) {
        Column(Modifier.padding(Sp.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(buildString {
                            if (o.nomorAntrian > 0) append("#${o.nomorAntrian} • ")
                            if (o.nomorMeja.isNotBlank()) append("Meja ${o.nomorMeja} • ")
                            append(PaymentMethod.fromId(o.metodeBayar).label)
                        }, fontWeight = FontWeight.SemiBold)
                        if (o.status == OrderStatus.VOID.id) {
                            Spacer(Modifier.width(6.dp))
                            Surface(color = DANGER, shape = RoundedCornerShape(Rd.xs)) {
                                Text("VOID",
                                    modifier = Modifier.padding(
                                        horizontal = 6.dp, vertical = 2.dp
                                    ),
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold)
                            }
                        } else if (o.status == OrderStatus.REFUND.id) {
                            Spacer(Modifier.width(6.dp))
                            Surface(color = WARNING, shape = RoundedCornerShape(Rd.xs)) {
                                Text("REFUND",
                                    modifier = Modifier.padding(
                                        horizontal = 6.dp, vertical = 2.dp
                                    ),
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Text(buildString {
                        append(o.timestamp.tanggal())
                        if (o.memberNama.isNotBlank()) {
                            append(" • ⭐ "); append(o.memberNama)
                        } else if (o.kasirNama.isNotBlank()) {
                            append(" • "); append(o.kasirNama)
                        }
                    }, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(o.total.rupiah(), fontWeight = FontWeight.Bold,
                    color = if (o.status == OrderStatus.PAID.id) BRAND
                    else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// ORDER DETAIL DIALOG
// ═══════════════════════════════════════════════════════════
@Composable
private fun OrderDetailDialog(
    order: Order, vm: RiwayatViewModel, onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<OrderItem>>(emptyList()) }
    var showVoidDialog by remember { mutableStateOf(false) }
    var showRefundDialog by remember { mutableStateOf(false) }
    var showPreview by remember { mutableStateOf(false) }
    var strukText by remember { mutableStateOf("") }

    LaunchedEffect(order.id) { items = vm.itemsOf(order.id) }

    val canVoid = FeatureManager.isEnabled(FeatureKey.VOID_REFUND)
            && Session.can(PermissionKey.VOID_REFUND)
            && order.status == OrderStatus.PAID.id

    val app = ctx.applicationContext as IyonzApp
    val printerEnabled = FeatureManager.isEnabled(FeatureKey.PRINTER_BT)
    val waEnabled = FeatureManager.isEnabled(FeatureKey.WHATSAPP_INTENT)

    // Kelompokkan bundle
    val grouped = remember(items) {
        val bundleItems = mutableMapOf<Long, MutableList<OrderItem>>()
        val normalItems = mutableListOf<OrderItem>()
        items.forEach { it ->
            if (it.bundleId > 0L) {
                bundleItems.getOrPut(it.bundleId) { mutableListOf() }.add(it)
            } else {
                normalItems.add(it)
            }
        }
        normalItems to bundleItems
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Detail Transaksi #${order.id}")
                Text(
                    when (order.status) {
                        OrderStatus.VOID.id -> "VOID — ${order.voidReason}"
                        OrderStatus.REFUND.id -> "REFUND — ${order.voidReason}"
                        else -> order.timestamp.tanggal()
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        text = {
            Column(Modifier.heightIn(max = 500.dp)
                .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {

                // ── Item biasa ──
                grouped.first.forEach { it ->
                    Row {
                        Text("${it.qty}x", Modifier.width(36.dp),
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodySmall)
                        Column(Modifier.weight(1f)) {
                            Text(it.namaMenu, style = MaterialTheme.typography.bodyMedium)
                            if (it.catatan.isNotBlank()) {
                                Text(it.catatan,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text(it.subtotal.rupiah(),
                            style = MaterialTheme.typography.bodySmall)
                    }
                }

                // ── Bundle ──
                grouped.second.forEach { (bundleId, bundleItems) ->
                    val mainItem = bundleItems.firstOrNull { it.menuId == 0L }
                    if (mainItem != null) {
                        val subs = bundleItems.filter { it.menuId != 0L }
                        Column(Modifier.padding(vertical = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("📦", style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.width(4.dp))
                                Text("${mainItem.qty}x ${mainItem.namaMenu}",
                                    Modifier.weight(1f),
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyMedium)
                                Text(mainItem.subtotal.rupiah(),
                                    fontWeight = FontWeight.Bold,
                                    color = BRAND,
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            subs.forEach { sub ->
                                Row(Modifier.padding(start = 24.dp)) {
                                    Text(sub.namaMenu.trimStart('•', ' '),
                                        Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("×${sub.qty}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Row {
                    Text("Total", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text(order.total.rupiah(), fontWeight = FontWeight.Bold, color = BRAND)
                }
                Row {
                    Text("Metode", Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall)
                    Text(PaymentMethod.fromId(order.metodeBayar).label,
                        style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(Sp.md))
                Row(horizontalArrangement = Arrangement.spacedBy(Sp.sm),
                    modifier = Modifier.fillMaxWidth()) {
                    if (printerEnabled) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    try {
                                        if (!PrinterService.isConnected()) {
                                            val mac = app.settingRepo.getPrinterMac()
                                            if (mac.isNotBlank()) PrinterService.connect(ctx, mac)
                                        }
                                        cetakStruk(app.settingRepo, order, items)
                                    } catch (_: Exception) {}
                                }
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 8.dp)) {
                            Icon(Icons.Default.Print, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Cetak", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                strukText = buildStrukText(app.settingRepo, order, items)
                                showPreview = true
                            }
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 8.dp)) {
                        Icon(Icons.Default.Receipt, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Preview", style = MaterialTheme.typography.bodySmall)
                    }
                    if (waEnabled) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val text = buildStrukText(app.settingRepo, order, items)
                                    shareStrukText(ctx, text)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 8.dp)) {
                            Icon(Icons.Default.Share, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Share", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (canVoid) {
                    Spacer(Modifier.height(Sp.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(Sp.sm)) {
                        OutlinedButton(
                            onClick = { showVoidDialog = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = DANGER)) {
                            Icon(Icons.Default.Cancel, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Void", style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(
                            onClick = { showRefundDialog = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = WARNING)) {
                            Icon(Icons.Default.Replay, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Refund", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } })
    if (showPreview) {
        StrukPreviewDialog(
            strukText = strukText,
            onPrint = if (printerEnabled) {
                {
                    scope.launch {
                        try {
                            if (!PrinterService.isConnected()) {
                                val mac = app.settingRepo.getPrinterMac()
                                if (mac.isNotBlank()) PrinterService.connect(ctx, mac)
                            }
                            cetakStruk(app.settingRepo, order, items)
                        } catch (_: Exception) {}
                    }
                }
            } else null,
            onShareWa = if (waEnabled) {
                {
                    scope.launch {
                        val text = buildStrukText(app.settingRepo, order, items)
                        shareStrukText(ctx, text)
                    }
                }
            } else null,
            onDismiss = { showPreview = false })
    }
    if (showVoidDialog) {
        AlasanDialog("Alasan Void",
            onConfirm = { reason ->
                scope.launch {
                    vm.voidOrder(order.id, reason)
                    showVoidDialog = false; onDismiss()
                }
            },
            onDismiss = { showVoidDialog = false })
    }
    if (showRefundDialog) {
        AlasanDialog("Alasan Refund",
            onConfirm = { reason ->
                scope.launch {
                    vm.refundOrder(order.id, reason)
                    showRefundDialog = false; onDismiss()
                }
            },
            onDismiss = { showRefundDialog = false })
    }
}

@Composable
private fun AlasanDialog(
    title: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    val quickReasons = listOf("Salah input", "Pelanggan batal", "Barang habis",
        "Salah hitung", "Komplain pelanggan")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Sp.sm)) {
                OutlinedTextField(text, { text = it },
                    label = { Text("Alasan (wajib)") },
                    placeholder = { Text("cth: salah input") },
                    maxLines = 3, modifier = Modifier.fillMaxWidth())
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Sp.xs)) {
                    items(quickReasons) { r ->
                        AssistChip(onClick = { text = r },
                            label = { Text(r,
                                style = MaterialTheme.typography.bodySmall) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text.trim()) },
                enabled = text.isNotBlank()) { Text("Konfirmasi", color = DANGER) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } })
}

// ═══════════════════════════════════════════════════════════
// DASHBOARD
// ═══════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(vm: DashboardViewModel) {
    val trx by vm.transaksiHariIni.collectAsState()
    val omzet by vm.omzetHariIni.collectAsState()
    val diskon by vm.diskonHariIni.collectAsState()
    val pajak by vm.pajakHariIni.collectAsState()
    val hpp by vm.hppHariIni.collectAsState()
    val pengeluaran by vm.pengeluaranHariIni.collectAsState()
    val recent by vm.transaksiTerakhir.collectAsState()
    val grafik by vm.grafik.collectAsState()
    val topMenu by vm.topMenu.collectAsState()
    val topKategori by vm.topKategori.collectAsState()
    val metodeStat by vm.metodeStat.collectAsState()

    val labaKotor = omzet - hpp
    val labaBersih = labaKotor - pengeluaran

    Scaffold(topBar = { TopAppBar(title = { Text("Dashboard") }) }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(Sp.lg),
            verticalArrangement = Arrangement.spacedBy(Sp.md)) {

            item {
                Text("Hari Ini", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold)
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Sp.md)) {
                    StatCard("Transaksi", trx.toString(),
                        Icons.Default.Receipt, Modifier.weight(1f))
                    StatCard("Omzet", omzet.rupiah(),
                        Icons.Default.AttachMoney, Modifier.weight(1f))
                }
            }

            item {
                Card(
                    shape = Rd.cardLg,
                    elevation = CardDefaults.cardElevation(defaultElevation = El.card),
                    colors = CardDefaults.cardColors(
                        containerColor = if (labaBersih >= 0)
                            SUCCESS.copy(alpha = 0.12f)
                        else DANGER.copy(alpha = 0.12f)
                    )
                ) {
                    Column(Modifier.padding(Sp.lg),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.TrendingUp, null,
                                tint = if (labaBersih >= 0) SUCCESS else DANGER)
                            Spacer(Modifier.width(Sp.sm))
                            Text("Laba Bersih Hari Ini",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall)
                        }
                        Text(labaBersih.rupiah(),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (labaBersih >= 0) SUCCESS else DANGER)
                        Text("Omzet − HPP − Pengeluaran",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item {
                Card(
                    shape = Rd.card,
                    elevation = CardDefaults.cardElevation(defaultElevation = El.card)
                ) {
                    Column(Modifier.padding(Sp.lg),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Rincian", fontWeight = FontWeight.SemiBold)
                        RingkasLine("Omzet", omzet.rupiah())
                        RingkasLine("HPP (Modal)", "- ${hpp.rupiah()}", DANGER)
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        RingkasLine("Laba Kotor", labaKotor.rupiah(), SUCCESS)
                        RingkasLine("Pengeluaran", "- ${pengeluaran.rupiah()}", DANGER)
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        RingkasLine("Laba Bersih", labaBersih.rupiah(),
                            if (labaBersih >= 0) SUCCESS else DANGER)
                        if (diskon > 0 || pajak > 0) {
                            Spacer(Modifier.height(Sp.xs))
                            if (diskon > 0) RingkasLine("Diskon", diskon.rupiah())
                            if (pajak > 0) RingkasLine("PPN", pajak.rupiah())
                        }
                    }
                }
            }

            if (FeatureManager.isEnabled(FeatureKey.GRAFIK)) {
                item {
                    Card(
                        shape = Rd.card,
                        elevation = CardDefaults.cardElevation(defaultElevation = El.card)
                    ) {
                        Column(Modifier.padding(Sp.lg)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.BarChart, null, tint = BRAND)
                                Spacer(Modifier.width(Sp.sm))
                                Text("Penjualan 7 Hari",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(Sp.md))
                            BarChart7Hari(grafik)
                        }
                    }
                }
                if (topMenu.isNotEmpty()) {
                    item {
                        TopList("🏆 Top 5 Menu", topMenu.map {
                            Triple(it.namaMenu, "${it.totalQty}x", it.totalOmzet)
                        })
                    }
                }
                if (topKategori.isNotEmpty()) {
                    item {
                        TopList("📂 Top Kategori", topKategori.map {
                            Triple(it.kategori, "${it.totalQty}x", it.totalOmzet)
                        })
                    }
                }
                if (metodeStat.isNotEmpty()) {
                    item {
                        TopList("💳 Metode Bayar", metodeStat.map {
                            Triple(PaymentMethod.fromId(it.metode).label,
                                "${it.totalTransaksi}x", it.totalOmzet)
                        })
                    }
                }
            }

            item {
                Spacer(Modifier.height(Sp.sm))
                Text("Transaksi Terakhir", fontWeight = FontWeight.SemiBold)
            }
            if (recent.isEmpty()) {
                item {
                    Text("Belum ada transaksi hari ini",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                items(recent.take(20), key = { it.id }) { order ->
                    Card(
                        shape = Rd.card,
                        elevation = CardDefaults.cardElevation(defaultElevation = El.card)
                    ) {
                        Row(Modifier.padding(Sp.md),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    order.timestamp.jamPendek() + " • " +
                                    PaymentMethod.fromId(order.metodeBayar).label,
                                    fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (order.memberNama.isNotBlank())
                                        "⭐ ${order.memberNama}"
                                    else if (order.nomorMeja.isNotBlank())
                                        "Meja ${order.nomorMeja}"
                                    else "Tanpa meja",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            Text(order.total.rupiah(),
                                fontWeight = FontWeight.Bold, color = BRAND)
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(Sp.xxl)) }
        }
    }
}

@Composable
private fun RingkasLine(label: String, value: String,
                         color: Color = Color.Unspecified) {
    Row {
        Text(label, Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface
            else color)
        Text(value, style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface
            else color)
    }
}

@Composable
private fun TopList(title: String, items: List<Triple<String, String, Int>>) {
    Card(
        shape = Rd.card,
        elevation = CardDefaults.cardElevation(defaultElevation = El.card)
    ) {
        Column(Modifier.padding(Sp.lg)) {
            Text(title, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(Sp.md))
            items.forEachIndexed { idx, (nama, qty, omzet) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = when (idx) {
                            0 -> Color(0xFFFFD54F)
                            1 -> Color(0xFFCFD8DC)
                            2 -> Color(0xFFFFAB91)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = RoundedCornerShape(Rd.xs)
                    ) {
                        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                            Text("${idx + 1}", fontWeight = FontWeight.Bold,
                                color = Color(0xFF424242))
                        }
                    }
                    Spacer(Modifier.width(Sp.md))
                    Column(Modifier.weight(1f)) {
                        Text(nama, fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium)
                        Text(qty, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(omzet.rupiah(),
                        fontWeight = FontWeight.Bold, color = BRAND,
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// BAR CHART 7 HARI
// ═══════════════════════════════════════════════════════════
@Composable
private fun BarChart7Hari(data: List<HariPenjualan>) {
    if (data.isEmpty() || data.all { it.omzet == 0 }) {
        Box(Modifier.fillMaxWidth().height(120.dp),
            contentAlignment = Alignment.Center) {
            Text("Belum ada data penjualan",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val maxOmzet = data.maxOf { it.omzet }.coerceAtLeast(1)
    val barColor = BRAND
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column {
        Canvas(Modifier.fillMaxWidth().height(160.dp)) {
            val paddingLeft = 8f
            val paddingBottom = 24f
            val chartH = size.height - paddingBottom
            val barW = (size.width - paddingLeft * 2) / (data.size * 2f - 1f)
            val gap = barW
            for (i in 0..3) {
                val y = chartH * i / 3f
                drawLine(color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y), strokeWidth = 1f)
            }
            data.forEachIndexed { idx, item ->
                val x = paddingLeft + idx * (barW + gap)
                val h = (item.omzet.toFloat() / maxOmzet) * (chartH - 8f)
                val y = chartH - h
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(x, y),
                    size = Size(barW, h),
                    cornerRadius = CornerRadius(6f, 6f))
            }
        }
        Row(Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween) {
            data.forEach {
                Text(it.label, style = MaterialTheme.typography.labelSmall,
                    color = labelColor)
            }
        }
        Spacer(Modifier.height(Sp.sm))
        Text("Tertinggi: ${maxOmzet.rupiah()}",
            style = MaterialTheme.typography.labelSmall, color = labelColor)
    }
}

// ═══════════════════════════════════════════════════════════
// STAT CARD
// ═══════════════════════════════════════════════════════════
@Composable
private fun StatCard(
    label: String, value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier,
        shape = Rd.card,
        elevation = CardDefaults.cardElevation(defaultElevation = El.card),
        colors = CardDefaults.cardColors(containerColor = BRAND_LIGHT)
    ) {
        Column(Modifier.padding(Sp.lg)) {
            Icon(icon, null, tint = BRAND)
            Spacer(Modifier.height(Sp.sm))
            Text(label, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold, color = BRAND)
        }
    }
}
