package com.iyonzkasir.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.iyonzkasir.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID

// ═══════ ENTITIES ═══════

@Entity(tableName = "users")
data class User(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val nama: String = "",
    val username: String = "",
    val pinHash: String = "",
    val pinSalt: String = "",
    val role: String = UserRole.KASIR.id,
    val fotoUri: String? = null,
    val aktif: Boolean = true,
    val biometricEnabled: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastLoginAt: Long = 0
)

@Entity(tableName = "user_permissions", primaryKeys = ["userId", "permissionKey"])
data class UserPermission(
    val userId: String,
    val permissionKey: String,
    val allowed: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "audit_log")
data class AuditLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String = "",
    val userName: String = "",
    val aksi: String = "",
    val targetId: String = "",
    val keterangan: String = "",
    val diotorisasiOleh: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "app_settings")
data class AppSetting(
    @PrimaryKey val key: String,
    val value: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "feature_toggles")
data class FeatureToggleEntity(
    @PrimaryKey val featureKey: String,
    val enabled: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "menu_items")
data class MenuItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nama: String = "",
    val harga: Int = 0,
    val hargaBeli: Int = 0,   // HPP / modal
    val kategori: String = "Umum",
    val fotoUri: String? = null,
    val tersedia: Boolean = true
)

@Entity(tableName = "orders")
data class Order(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = 0,
    val nomorMeja: String = "",
    val tipeOrder: String = TipeOrder.DINE_IN.id,
    val namaPelanggan: String = "",
    val subtotal: Int = 0,
    val diskonTipe: String = "NONE",
    val diskonValue: Int = 0,
    val diskonAmount: Int = 0,
    val pajakPersen: Int = 0,
    val pajakAmount: Int = 0,
    val total: Int = 0,
    val metodeBayar: String = "",
    val dibayar: Int = 0,
    val kembalian: Int = 0,
    val status: String = OrderStatus.PAID.id,
    val catatan: String = "",
    val kasirId: String = "",
    val kasirNama: String = "",
    val voidReason: String = "",
    val shiftId: Long = 0
)

@Entity(tableName = "order_items")
data class OrderItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: Long = 0,
    val menuId: Long = 0,
    val namaMenu: String = "",
    val hargaSatuan: Int = 0,
    val qty: Int = 1,
    val catatan: String = ""
) { val subtotal: Int get() = hargaSatuan * qty }

@Entity(tableName = "shifts")
data class Shift(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kasirId: String = "",
    val kasirNama: String = "",
    val bukaTimestamp: Long = 0,
    val tutupTimestamp: Long = 0,
    val modalAwal: Int = 0,
    val totalTransaksi: Int = 0,
    val totalOmzet: Int = 0,
    val totalTunai: Int = 0,
    val totalNonTunai: Int = 0,
    val totalDiskon: Int = 0,
    val totalPajak: Int = 0,
    val uangFisik: Int = 0,
    val selisih: Int = 0,
    val catatan: String = "",
    val status: String = "OPEN"
)

// ═══════ DATA CLASS UNTUK LAPORAN ═══════

data class LabaProduk(
    val menuId: Long,
    val namaMenu: String,
    val totalQty: Int,
    val totalOmzet: Int,
    val totalHpp: Int
) {
    val laba: Int get() = totalOmzet - totalHpp
    val marginPersen: Int get() = if (totalOmzet > 0) laba * 100 / totalOmzet else 0
}

data class HariPenjualan(
    val label: String,
    val omzet: Int,
    val transaksi: Int
)

// ═══════ DAOs ═══════

@Dao
interface UserDao {
    @Query("SELECT * FROM users ORDER BY createdAt")
    fun observeAll(): Flow<List<User>>
    @Query("SELECT * FROM users WHERE aktif = 1 ORDER BY createdAt")
    fun observeActive(): Flow<List<User>>
    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun getById(id: String): User?
    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun getByUsername(username: String): User?
    @Query("SELECT COUNT(*) FROM users WHERE role = 'owner'")
    suspend fun ownerCount(): Int
    @Query("SELECT COUNT(*) FROM users")
    suspend fun totalCount(): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(user: User)
    @Delete
    suspend fun delete(user: User)
    @Query("UPDATE users SET lastLoginAt = :ts WHERE id = :id")
    suspend fun updateLastLogin(id: String, ts: Long = System.currentTimeMillis())
}

@Dao
interface PermissionDao {
    @Query("SELECT * FROM user_permissions WHERE userId = :userId")
    fun observeForUser(userId: String): Flow<List<UserPermission>>
    @Query("SELECT * FROM user_permissions WHERE userId = :userId")
    suspend fun getForUser(userId: String): List<UserPermission>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(perm: UserPermission)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(perms: List<UserPermission>)
    @Query("DELETE FROM user_permissions WHERE userId = :userId")
    suspend fun clearForUser(userId: String)
}

@Dao
interface AuditDao {
    @Query("SELECT * FROM audit_log ORDER BY timestamp DESC LIMIT 500")
    fun observeRecent(): Flow<List<AuditLog>>
    @Insert
    suspend fun insert(log: AuditLog)
}

@Dao
interface SettingDao {
    @Query("SELECT * FROM app_settings WHERE key = :key")
    suspend fun get(key: String): AppSetting?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(setting: AppSetting)
}

@Dao
interface FeatureDao {
    @Query("SELECT * FROM feature_toggles")
    fun observeAll(): Flow<List<FeatureToggleEntity>>
    @Query("SELECT * FROM feature_toggles")
    suspend fun getAllSync(): List<FeatureToggleEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(toggle: FeatureToggleEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(toggles: List<FeatureToggleEntity>)
    @Query("DELETE FROM feature_toggles")
    suspend fun clear()
}

@Dao
interface MenuDao {
    @Query("SELECT * FROM menu_items ORDER BY kategori, nama")
    fun observeAll(): Flow<List<MenuItem>>
    @Query("SELECT * FROM menu_items WHERE id = :id")
    suspend fun getById(id: Long): MenuItem?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: MenuItem): Long
    @Delete
    suspend fun delete(item: MenuItem)
    @Query("UPDATE menu_items SET tersedia = :tersedia WHERE id = :id")
    suspend fun setTersedia(id: Long, tersedia: Boolean)
}

@Dao
interface OrderDao {
    @Insert suspend fun insertOrder(order: Order): Long
    @Insert suspend fun insertItems(items: List<OrderItem>)
    @Update suspend fun updateOrder(order: Order)
    @Query("DELETE FROM order_items WHERE orderId = :orderId")
    suspend fun deleteItems(orderId: Long)

    @Transaction
    suspend fun simpanOrder(order: Order, items: List<OrderItem>): Long {
        val id = insertOrder(order)
        insertItems(items.map { it.copy(orderId = id) })
        return id
    }

    @Transaction
    suspend fun updateOrderWithItems(order: Order, items: List<OrderItem>) {
        updateOrder(order)
        deleteItems(order.id)
        insertItems(items.map { it.copy(orderId = order.id) })
    }

    @Query("SELECT * FROM orders WHERE status = 'OPEN' ORDER BY timestamp DESC")
    fun observeOpenBills(): Flow<List<Order>>
    @Query("SELECT * FROM orders WHERE status = 'PAID' ORDER BY timestamp DESC")
    fun observePaid(): Flow<List<Order>>
    @Query("SELECT * FROM orders WHERE status = 'PAID' AND timestamp >= :start ORDER BY timestamp DESC")
    fun observePaidSince(start: Long): Flow<List<Order>>
    @Query("SELECT * FROM orders WHERE id = :id")
    suspend fun getOrder(id: Long): Order?
    @Query("SELECT * FROM order_items WHERE orderId = :orderId")
    suspend fun itemsOf(orderId: Long): List<OrderItem>
    @Query("SELECT COUNT(*) FROM orders WHERE status = 'PAID' AND timestamp >= :start")
    fun countPaidSince(start: Long): Flow<Int>
    @Query("SELECT COALESCE(SUM(total), 0) FROM orders WHERE status = 'PAID' AND timestamp >= :start")
    fun sumPaidSince(start: Long): Flow<Int>
    @Query("SELECT COALESCE(SUM(diskonAmount), 0) FROM orders WHERE status = 'PAID' AND timestamp >= :start")
    fun sumDiskonSince(start: Long): Flow<Int>
    @Query("SELECT COALESCE(SUM(pajakAmount), 0) FROM orders WHERE status = 'PAID' AND timestamp >= :start")
    fun sumPajakSince(start: Long): Flow<Int>
    @Query("UPDATE orders SET status = :status, voidReason = :reason WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, reason: String)

    // ── Shift queries ──
    @Query("SELECT COUNT(*) FROM orders WHERE shiftId = :shiftId AND status = 'PAID'")
    suspend fun countByShift(shiftId: Long): Int
    @Query("SELECT COALESCE(SUM(total), 0) FROM orders WHERE shiftId = :shiftId AND status = 'PAID'")
    suspend fun sumTotalByShift(shiftId: Long): Int
    @Query("SELECT COALESCE(SUM(total), 0) FROM orders WHERE shiftId = :shiftId AND status = 'PAID' AND metodeBayar = 'CASH'")
    suspend fun sumCashByShift(shiftId: Long): Int
    @Query("SELECT COALESCE(SUM(diskonAmount), 0) FROM orders WHERE shiftId = :shiftId AND status = 'PAID'")
    suspend fun sumDiskonByShift(shiftId: Long): Int
    @Query("SELECT COALESCE(SUM(pajakAmount), 0) FROM orders WHERE shiftId = :shiftId AND status = 'PAID'")
    suspend fun sumPajakByShift(shiftId: Long): Int

    // ── Laporan laba per produk ──
    @Query("""
        SELECT oi.menuId AS menuId,
               oi.namaMenu AS namaMenu,
               SUM(oi.qty) AS totalQty,
               SUM(oi.subtotal) AS totalOmzet,
               SUM(oi.qty * COALESCE(m.hargaBeli, 0)) AS totalHpp
        FROM order_items oi
        JOIN orders o ON o.id = oi.orderId
        LEFT JOIN menu_items m ON m.id = oi.menuId
        WHERE o.status = 'PAID' AND o.timestamp >= :start AND o.timestamp <= :end
        GROUP BY oi.menuId, oi.namaMenu
        ORDER BY (SUM(oi.subtotal) - SUM(oi.qty * COALESCE(m.hargaBeli, 0))) DESC
    """)
    suspend fun labaPerProduk(start: Long, end: Long): List<LabaProduk>

    @Query("SELECT * FROM orders WHERE status = 'PAID' AND timestamp >= :start AND timestamp <= :end ORDER BY timestamp ASC")
    suspend fun ordersInRange(start: Long, end: Long): List<Order>
}

@Dao
interface ShiftDao {
    @Query("SELECT * FROM shifts WHERE status = 'OPEN' ORDER BY bukaTimestamp DESC LIMIT 1")
    suspend fun getActiveShift(): Shift?
    @Query("SELECT * FROM shifts WHERE status = 'OPEN' ORDER BY bukaTimestamp DESC LIMIT 1")
    fun observeActiveShift(): Flow<Shift?>
    @Query("SELECT * FROM shifts ORDER BY bukaTimestamp DESC LIMIT 100")
    fun observeAll(): Flow<List<Shift>>
    @Query("SELECT * FROM shifts WHERE id = :id")
    suspend fun getById(id: Long): Shift?
    @Insert
    suspend fun insert(shift: Shift): Long
    @Update
    suspend fun update(shift: Shift)
}

// ═══════ MIGRATIONS ═══════

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `user_permissions` (
                `userId` TEXT NOT NULL,
                `permissionKey` TEXT NOT NULL,
                `allowed` INTEGER NOT NULL DEFAULT 0,
                `updatedAt` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`userId`, `permissionKey`)
            )
        """.trimIndent())
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE orders ADD COLUMN subtotal INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE orders ADD COLUMN diskonTipe TEXT NOT NULL DEFAULT 'NONE'")
        db.execSQL("ALTER TABLE orders ADD COLUMN diskonValue INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE orders ADD COLUMN diskonAmount INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE orders ADD COLUMN pajakPersen INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE orders ADD COLUMN pajakAmount INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE orders SET subtotal = total WHERE subtotal = 0")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `shifts` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `kasirId` TEXT NOT NULL DEFAULT '',
                `kasirNama` TEXT NOT NULL DEFAULT '',
                `bukaTimestamp` INTEGER NOT NULL DEFAULT 0,
                `tutupTimestamp` INTEGER NOT NULL DEFAULT 0,
                `modalAwal` INTEGER NOT NULL DEFAULT 0,
                `totalTransaksi` INTEGER NOT NULL DEFAULT 0,
                `totalOmzet` INTEGER NOT NULL DEFAULT 0,
                `totalTunai` INTEGER NOT NULL DEFAULT 0,
                `totalNonTunai` INTEGER NOT NULL DEFAULT 0,
                `totalDiskon` INTEGER NOT NULL DEFAULT 0,
                `totalPajak` INTEGER NOT NULL DEFAULT 0,
                `uangFisik` INTEGER NOT NULL DEFAULT 0,
                `selisih` INTEGER NOT NULL DEFAULT 0,
                `catatan` TEXT NOT NULL DEFAULT '',
                `status` TEXT NOT NULL DEFAULT 'OPEN'
            )
        """.trimIndent())
        db.execSQL("ALTER TABLE orders ADD COLUMN shiftId INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE menu_items ADD COLUMN hargaBeli INTEGER NOT NULL DEFAULT 0")
    }
}

// ═══════ DATABASE ═══════

@Database(
    entities = [
        User::class, UserPermission::class, AuditLog::class, AppSetting::class,
        FeatureToggleEntity::class, MenuItem::class, Order::class, OrderItem::class,
        Shift::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun permissionDao(): PermissionDao
    abstract fun auditDao(): AuditDao
    abstract fun settingDao(): SettingDao
    abstract fun featureDao(): FeatureDao
    abstract fun menuDao(): MenuDao
    abstract fun orderDao(): OrderDao
    abstract fun shiftDao(): ShiftDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "iyonzkasir.db"
                ).addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}

// ═══════ REPOSITORIES ═══════

class UserRepository(
    private val dao: UserDao,
    private val permDao: PermissionDao,
    private val auditDao: AuditDao
) {
    val users: Flow<List<User>> = dao.observeAll()
    val activeUsers: Flow<List<User>> = dao.observeActive()

    suspend fun getById(id: String) = dao.getById(id)
    suspend fun getByUsername(username: String) = dao.getByUsername(username)
    suspend fun upsert(user: User) = dao.upsert(user)
    suspend fun delete(user: User) {
        permDao.clearForUser(user.id); dao.delete(user)
    }
    suspend fun ownerCount() = dao.ownerCount()
    suspend fun totalCount() = dao.totalCount()
    suspend fun updateLastLogin(id: String) = dao.updateLastLogin(id)

    fun observePermissions(userId: String) = permDao.observeForUser(userId)
    suspend fun getPermissions(userId: String) = permDao.getForUser(userId)
    suspend fun setPermissionsBatch(userId: String, perms: Map<PermissionKey, Boolean>) {
        permDao.upsertAll(perms.map { UserPermission(userId, it.key.key, it.value) })
    }
    suspend fun applyRolePreset(userId: String, role: UserRole) {
        val defaults = role.defaultPermissions.map { it.key }.toSet()
        permDao.clearForUser(userId)
        permDao.upsertAll(PermissionKey.values().map {
            UserPermission(userId, it.key, it.key in defaults)
        })
    }
    suspend fun seedPermissionsIfEmpty(userId: String, role: UserRole) {
        if (permDao.getForUser(userId).isEmpty()) applyRolePreset(userId, role)
    }

    suspend fun log(
        userId: String, userName: String, aksi: String,
        targetId: String = "", keterangan: String = "", otorisasi: String = ""
    ) = auditDao.insert(
        AuditLog(
            userId = userId, userName = userName, aksi = aksi,
            targetId = targetId, keterangan = keterangan, diotorisasiOleh = otorisasi
        )
    )

    fun observeAuditLog() = auditDao.observeRecent()
}

class SettingRepository(private val dao: SettingDao) {
    suspend fun get(key: String, default: String = ""): String =
        dao.get(key)?.value ?: default
    suspend fun set(key: String, value: String) = dao.set(AppSetting(key, value))

    suspend fun getNamaToko() = get(KEY_NAMA_TOKO, "Toko Saya")
    suspend fun getAlamatToko() = get(KEY_ALAMAT, "")
    suspend fun getTeleponToko() = get(KEY_TELEPON, "")
    suspend fun getLogoUri() = get(KEY_LOGO, "")
    suspend fun getFooterStruk() = get(KEY_FOOTER, "Terima kasih sudah berbelanja")
    suspend fun getBusinessType() = BusinessType.fromId(get(KEY_BUSINESS_TYPE, "warung"))
    suspend fun isOnboardingDone() = get(KEY_ONBOARDING_DONE, "0") == "1"
    suspend fun setOnboardingDone() = set(KEY_ONBOARDING_DONE, "1")
    suspend fun getThemeMode() = ThemeMode.fromId(get(KEY_TEMA_MODE, "system"))
    suspend fun setThemeMode(m: ThemeMode) = set(KEY_TEMA_MODE, m.id)

    // ── Printer ──
    suspend fun getPrinterMac() = get(KEY_PRINTER_MAC, "")
    suspend fun setPrinterMac(mac: String) = set(KEY_PRINTER_MAC, mac)
    suspend fun getPrinterNama() = get(KEY_PRINTER_NAMA, "")
    suspend fun setPrinterNama(nama: String) = set(KEY_PRINTER_NAMA, nama)
    suspend fun getPaperWidth() = get(KEY_PAPER_WIDTH, "58").toIntOrNull() ?: 58
    suspend fun setPaperWidth(w: Int) = set(KEY_PAPER_WIDTH, w.toString())
    suspend fun isAutoPrint() = get(KEY_AUTO_PRINT, "0") == "1"
    suspend fun setAutoPrint(v: Boolean) = set(KEY_AUTO_PRINT, if (v) "1" else "0")

    companion object {
        const val KEY_NAMA_TOKO = "toko_nama"
        const val KEY_ALAMAT = "toko_alamat"
        const val KEY_TELEPON = "toko_telepon"
        const val KEY_LOGO = "toko_logo"
        const val KEY_FOOTER = "struk_footer"
        const val KEY_BUSINESS_TYPE = "business_type"
        const val KEY_ONBOARDING_DONE = "onboarding_done"
        const val KEY_TEMA_MODE = "tema_mode"
        const val KEY_PRINTER_MAC = "printer_mac"
        const val KEY_PRINTER_NAMA = "printer_nama"
        const val KEY_PAPER_WIDTH = "paper_width"
        const val KEY_AUTO_PRINT = "auto_print"
    }
}

class FeatureRepository(private val dao: FeatureDao) {
    val toggles: Flow<List<FeatureToggleEntity>> = dao.observeAll()

    suspend fun setEnabled(feature: FeatureKey, enabled: Boolean) {
        dao.upsert(FeatureToggleEntity(feature.key, enabled)); refreshCache()
    }
    suspend fun enableAll() {
        dao.clear()
        dao.upsertAll(FeatureKey.values().map { FeatureToggleEntity(it.key, true) })
        refreshCache()
    }
    suspend fun setOnly(enabled: Set<FeatureKey>) {
        dao.clear()
        dao.upsertAll(FeatureKey.values().map { FeatureToggleEntity(it.key, it in enabled) })
        refreshCache()
    }
    suspend fun applyPreset(type: BusinessType) {
        if (type == BusinessType.CUSTOM) {
            dao.clear()
            dao.upsertAll(FeatureKey.values().map { FeatureToggleEntity(it.key, false) })
        } else setOnly(type.defaultFeatures)
        refreshCache()
    }
    suspend fun ensureInitialized() {
        if (dao.getAllSync().isEmpty()) enableAll() else refreshCache()
    }
    suspend fun refreshCache() {
        FeatureManager.update(dao.getAllSync().filter { it.enabled }
            .mapNotNull { FeatureKey.fromKey(it.featureKey) }.toSet())
    }
}

class PosRepository(
    private val menuDao: MenuDao,
    private val orderDao: OrderDao,
    private val shiftDao: ShiftDao
) {
    val menu: Flow<List<MenuItem>> = menuDao.observeAll()
    val openBills: Flow<List<Order>> = orderDao.observeOpenBills()
    val paidOrders: Flow<List<Order>> = orderDao.observePaid()

    suspend fun upsertMenu(item: MenuItem) = menuDao.upsert(item)
    suspend fun deleteMenu(item: MenuItem) = menuDao.delete(item)
    suspend fun setTersedia(id: Long, v: Boolean) = menuDao.setTersedia(id, v)
    suspend fun getMenu(id: Long) = menuDao.getById(id)

    suspend fun simpanOrder(order: Order, items: List<OrderItem>) =
        orderDao.simpanOrder(order, items)
    suspend fun updateOrderWithItems(order: Order, items: List<OrderItem>) =
        orderDao.updateOrderWithItems(order, items)
    suspend fun getOrder(id: Long) = orderDao.getOrder(id)
    suspend fun itemsOf(orderId: Long) = orderDao.itemsOf(orderId)

    suspend fun voidOrder(id: Long, reason: String) =
        orderDao.updateStatus(id, OrderStatus.VOID.id, reason)
    suspend fun refundOrder(id: Long, reason: String) =
        orderDao.updateStatus(id, OrderStatus.REFUND.id, reason)

    suspend fun getActiveShiftId(): Long? = shiftDao.getActiveShift()?.id

    fun observePaidSince(start: Long) = orderDao.observePaidSince(start)
    fun countPaidSince(start: Long) = orderDao.countPaidSince(start)
    fun sumPaidSince(start: Long) = orderDao.sumPaidSince(start)
    fun sumDiskonSince(start: Long) = orderDao.sumDiskonSince(start)
    fun sumPajakSince(start: Long) = orderDao.sumPajakSince(start)

    suspend fun labaPerProduk(start: Long, end: Long) = orderDao.labaPerProduk(start, end)
    suspend fun ordersInRange(start: Long, end: Long) = orderDao.ordersInRange(start, end)
}

class ShiftRepository(
    private val shiftDao: ShiftDao,
    private val orderDao: OrderDao
) {
    val activeShift: Flow<Shift?> = shiftDao.observeActiveShift()
    val allShifts: Flow<List<Shift>> = shiftDao.observeAll()

    suspend fun getActive(): Shift? = shiftDao.getActiveShift()
    suspend fun getById(id: Long) = shiftDao.getById(id)

    suspend fun bukaShift(kasirId: String, kasirNama: String, modalAwal: Int): Long =
        shiftDao.insert(Shift(
            kasirId = kasirId, kasirNama = kasirNama,
            bukaTimestamp = System.currentTimeMillis(),
            modalAwal = modalAwal, status = "OPEN"
        ))

    suspend fun tutupShift(id: Long, uangFisik: Int, catatan: String): Shift? {
        val shift = shiftDao.getById(id) ?: return null
        val totalTrx = orderDao.countByShift(id)
        val omzet = orderDao.sumTotalByShift(id)
        val tunai = orderDao.sumCashByShift(id)
        val nonTunai = (omzet - tunai).coerceAtLeast(0)
        val diskon = orderDao.sumDiskonByShift(id)
        val pajak = orderDao.sumPajakByShift(id)
        val expected = shift.modalAwal + tunai
        val selisih = uangFisik - expected
        val updated = shift.copy(
            tutupTimestamp = System.currentTimeMillis(),
            totalTransaksi = totalTrx, totalOmzet = omzet,
            totalTunai = tunai, totalNonTunai = nonTunai,
            totalDiskon = diskon, totalPajak = pajak,
            uangFisik = uangFisik, selisih = selisih,
            catatan = catatan, status = "CLOSED"
        )
        shiftDao.update(updated)
        return updated
    }
}
