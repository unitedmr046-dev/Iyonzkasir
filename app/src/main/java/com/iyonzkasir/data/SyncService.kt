package com.iyonzkasir.data

import android.content.Context
import android.net.Uri
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import java.io.File

/**
 * Service untuk sync data dari app kasir ke Firestore + ImgBB.
 *
 * Struktur Firestore:
 *   stores/{storeId}/info (document)
 *   stores/{storeId}/menu/{menuId} (document)
 *   stores/{storeId}/bundles/{bundleId} (document)
 */
class SyncService(
    private val context: Context,
    private val posRepo: PosRepository,
    private val bundleRepo: MenuBundleRepository,
    private val settingRepo: SettingRepository
) {

    // ═══ PROGRESS TRACKING ═══
    data class SyncProgress(
        val current: Int,
        val total: Int,
        val message: String
    )

    // ═══ RESULT ═══
    sealed class SyncResult {
        data class Success(
            val totalMenu: Int,
            val totalBundle: Int,
            val totalPhotoUploaded: Int,
            val totalPhotoSkipped: Int
        ) : SyncResult()
        data class Error(val message: String) : SyncResult()
    }

    /**
     * Sync semua data (info + menu + bundle) ke Firestore.
     */
    suspend fun syncAll(
        storeId: String,
        onProgress: (SyncProgress) -> Unit = {}
    ): SyncResult {
        return try {
            if (storeId.isBlank()) {
                return SyncResult.Error("Store ID kosong")
            }

            val db = FirebaseManager.db()
            val imgbbKey = settingRepo.getImgbbApiKey()

            // Ambil data lokal
            onProgress(SyncProgress(0, 1, "Membaca data lokal…"))
            val menuList = posRepo.menu.first()
            val bundleList = bundleRepo.bundles.first()

            // Hitung total untuk progress
            val totalSteps = 1 + menuList.size + bundleList.size
            var currentStep = 0
            var totalPhotoUploaded = 0
            var totalPhotoSkipped = 0

            // ═══ 1. SYNC INFO TOKO ═══
            onProgress(SyncProgress(currentStep, totalSteps, "Upload info toko…"))
            syncStoreInfo(storeId)
            currentStep++

            // ═══ 2. SYNC MENU ═══
            menuList.forEachIndexed { index, menu ->
                onProgress(SyncProgress(
                    currentStep,
                    totalSteps,
                    "Upload menu ${index + 1}/${menuList.size}: ${menu.nama}"
                ))

                // Cek apakah perlu upload foto
                val fotoUrl = menu.fotoUri?.let { uri ->
                    if (uri.startsWith("http")) {
                        // Sudah URL (dari Firebase lama)
                        uri
                    } else if (imgbbKey.isBlank()) {
                        // Nggak ada API key
                        totalPhotoSkipped++
                        ""
                    } else {
                        // Upload ke ImgBB
                        uploadPhotoToImgBB(uri, imgbbKey).also { result ->
                            if (result != null) totalPhotoUploaded++
                            else totalPhotoSkipped++
                        } ?: ""
                    }
                } ?: ""

                syncMenuItem(storeId, menu, fotoUrl, index)
                currentStep++
            }

            // ═══ 3. SYNC BUNDLE ═══
            bundleList.forEachIndexed { index, bundle ->
                onProgress(SyncProgress(
                    currentStep,
                    totalSteps,
                    "Upload paket ${index + 1}/${bundleList.size}: ${bundle.nama}"
                ))
                syncBundle(storeId, bundle, index)
                currentStep++
            }

            // ═══ 4. UPDATE METADATA ═══
            db.collection("stores")
                .document(storeId)
                .set(mapOf(
                    "lastSyncAt" to FieldValue.serverTimestamp(),
                    "lastSyncBy" to (FirebaseManager.userEmail ?: ""),
                    "totalMenu" to menuList.size,
                    "totalBundle" to bundleList.size
                ), com.google.firebase.firestore.SetOptions.merge())
                .await()

            // ═══ 5. SIMPAN TIMESTAMP LOKAL ═══
            settingRepo.setLastSyncTimestamp(System.currentTimeMillis())
            settingRepo.setStoreId(storeId)
            settingRepo.setSyncEnabled(true)

            onProgress(SyncProgress(totalSteps, totalSteps, "Selesai! ✅"))

            SyncResult.Success(
                totalMenu = menuList.size,
                totalBundle = bundleList.size,
                totalPhotoUploaded = totalPhotoUploaded,
                totalPhotoSkipped = totalPhotoSkipped
            )
        } catch (e: Exception) {
            SyncResult.Error(e.message ?: "Sync gagal tanpa pesan")
        }
    }

    // ═══════════════════════════════════════════════════════════
    // SYNC INFO TOKO
    // ═══════════════════════════════════════════════════════════
    private suspend fun syncStoreInfo(storeId: String) {
        val info = StoreInfoFirestore(
            storeId = storeId,
            nama = settingRepo.getNamaToko(),
            alamat = settingRepo.getAlamatToko(),
            teleponWA = normalizeWaNumber(settingRepo.getTeleponToko()),
            logoUrl = "",  // nanti kalau ada logo
            heroImageUrl = "",  // nanti
            jamBuka = "08:00",
            jamTutup = "22:00",
            temaWarna = "#C8341A",
            pajakPersen = settingRepo.getPajakDefault(),
            biayaLayananPersen = 0,
            active = true,
            updatedAt = System.currentTimeMillis()
        )

        FirebaseManager.db()
            .collection("stores")
            .document(storeId)
            .collection("info")
            .document("config")
            .set(info)
            .await()
    }

    // ═══════════════════════════════════════════════════════════
    // SYNC MENU
    // ═══════════════════════════════════════════════════════════
    private suspend fun syncMenuItem(
        storeId: String,
        menu: MenuItem,
        fotoUrl: String,
        urutan: Int
    ) {
        val data = MenuItemFirestore(
            id = menu.id.toString(),
            nama = menu.nama,
            deskripsi = "",
            kategori = menu.kategori,
            kategoriId = menu.kategoriId,
            harga = menu.harga,
            hargaAsli = if (menu.hargaBeli > 0) menu.harga * 2 else 0,  // dummy harga coret
            fotoUrl = fotoUrl,
            tersedia = menu.tersedia,
            trackStok = menu.trackStok,
            stok = menu.stok,
            badges = emptyList(),
            rating = 0.0,
            reviewCount = 0,
            estimasi = "",
            urutan = urutan,
            updatedAt = System.currentTimeMillis()
        )

        FirebaseManager.db()
            .collection("stores")
            .document(storeId)
            .collection("menu")
            .document(menu.id.toString())
            .set(data)
            .await()
    }

    // ═══════════════════════════════════════════════════════════
    // SYNC BUNDLE
    // ═══════════════════════════════════════════════════════════
    private suspend fun syncBundle(
        storeId: String,
        bundle: MenuBundle,
        urutan: Int
    ) {
        // Load groups + items
        val (_, groups, itemsMap) = bundleRepo.loadFull(bundle.id)

        val groupsFirestore = groups.map { group ->
            BundleGroupFirestore(
                nama = group.nama,
                minPilih = group.minPilih,
                maxPilih = group.maxPilih,
                wajib = group.wajib,
                items = (itemsMap[group.id] ?: emptyList()).map { item ->
                    BundleItemFirestore(
                        menuId = item.menuId.toString(),
                        nama = "",  // nanti di-resolve di web
                        qty = item.qty,
                        hargaExtra = item.hargaExtra,
                        isDefaultPick = item.isDefaultPick
                    )
                }
            )
        }

        val data = BundleFirestore(
            id = bundle.id.toString(),
            nama = bundle.nama,
            deskripsi = bundle.deskripsi,
            hargaBundle = bundle.hargaBundle,
            hargaAsli = bundle.hargaAsli,
            tipe = bundle.tipe,
            fotoUrl = bundle.fotoUri?.let { uri ->
                if (uri.startsWith("http")) uri else ""
            } ?: "",
            tersedia = bundle.tersedia,
            aktif = bundle.aktif,
            urutan = urutan,
            groups = groupsFirestore,
            updatedAt = System.currentTimeMillis()
        )

        FirebaseManager.db()
            .collection("stores")
            .document(storeId)
            .collection("bundles")
            .document(bundle.id.toString())
            .set(data)
            .await()
    }

    // ═══════════════════════════════════════════════════════════
    // UPLOAD FOTO KE IMGBB
    // ═══════════════════════════════════════════════════════════
    private suspend fun uploadPhotoToImgBB(
        localUri: String,
        apiKey: String
    ): String? {
        return try {
            val bytes = readPhotoBytes(localUri) ?: return null
            val result = ImgBBService.uploadBytes(bytes, apiKey)
            result.getOrNull()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Baca foto dari URI apapun (content:// atau file path).
     */
    private fun readPhotoBytes(uri: String): ByteArray? {
        return try {
            if (uri.startsWith("content://")) {
                context.contentResolver.openInputStream(Uri.parse(uri))?.use {
                    it.readBytes()
                }
            } else {
                val file = File(uri)
                if (file.exists() && file.isFile) file.readBytes() else null
            }
        } catch (e: Exception) {
            null
        }
    }

    // ═══════════════════════════════════════════════════════════
    // HELPER
    // ═══════════════════════════════════════════════════════════
    private fun normalizeWaNumber(input: String): String {
        val cleaned = input.replace("+", "").replace("-", "").replace(" ", "")
        return when {
            cleaned.startsWith("0") -> "62${cleaned.drop(1)}"
            cleaned.startsWith("62") -> cleaned
            cleaned.isBlank() -> ""
            else -> "62$cleaned"
        }
    }
}
