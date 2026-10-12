package com.iyonzkasir.ui

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
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
    val menu: MenuItem?,
    val qty: Int,
    val catatan: String = "",
    val bundle: MenuBundle? = null,
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
// KASIR VIEWMODEL — tidak berubah, preserve semua logic
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

    suspend fun loadBundleFull(id: Long) = bundleRepo.loadFull(id)

    fun add(menu: MenuItem, catatan: String = "") {
        val key = "${menu.id}|$catatan"
        _cart.update { map ->
            val e = map[key]
            if (e == null) map + (key to CartLine(key, menu, 1, catatan))
            else map + (key to e.copy(qty = e.qty + 1))
        }
        if (soundEnabled) SoundHelper.playClick()
    }

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

    private fun buildOrderItems(lines: List<CartLine>): List<OrderItem> {
        val result = mutableListOf<OrderItem>()
        lines.forEach { line ->
            if (line.isBundle) {
                val b = line.bundle!!
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
