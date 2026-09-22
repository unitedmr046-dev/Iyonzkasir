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

@Entity(tableName = "kategori")
data class Kategori(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nama: String = "",
    val warnaHex: String = "#FF6B35",
    val urutan: Int = 0,
    val aktif: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "menu_items")
data class MenuItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nama: String = "",
    val harga: Int = 0,
    val hargaBeli: Int = 0,
    val kategori: String = "Umum",
    val kategoriId: Long = 0,
    val fotoUri: String? = null,
    val tersedia: Boolean = true,
    val trackStok: Boolean = false,
    val stok: Int = 0,
    val stokMinimal: Int = 5
)

@Entity(tableName = "stock_movements")
data class StockMovement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val menuId: Long = 0,
    val namaMenu: String = "",
    val tipe: String = StockMovementType.ADJUST.id,
    val qty: Int = 0,
    val stokSebelum: Int = 0,
    val stokSesudah: Int = 0,
    val keterangan: String = "",
    val userId: String = "",
    val userName: String = "",
    val orderId: Long = 0,
    val timestamp: Long = System.currentTimeMillis()
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
    val voucherKode: String = "",
    val voucherAmount: Int = 0,
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
    val shiftId: Long = 0,
    val memberId: Long = 0,
    val memberNama: String = "",
    val poinDidapat: Int = 0,
    val stokDipotong: Boolean = false
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

@Entity(tableName = "members")
data class Member(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nama: String = "",
    val telepon: String = "",
    val email: String = "",
    val alamat: String = "",
    val poin: Int = 0,
    val totalBelanja: Int = 0,
    val tier: String = MemberTier.BASIC.id,
    val hutang: Int = 0,
    val aktif: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val catatan: String = ""
)

@Entity(tableName = "vouchers")
data class Voucher(
    @PrimaryKey val kode: String = "",
    val nama: String = "",
    val tipe: String = "NOMINAL",
    val value: Int = 0,
    val minBelanja: Int = 0,
    val maxDiskon: Int = 0,
    val kuota: Int = 0,
    val terpakai: Int = 0,
    val tglMulai: Long = 0,
    val tglAkhir: Long = 0,
    val aktif: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "member_transactions")
data class MemberTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val memberId: Long = 0,
    val orderId: Long = 0,
    val tipe: String = "POIN_EARN",
    val poinDelta: Int = 0,
    val hutangDelta: Int = 0,
    val saldoPoinSetelah: Int = 0,
    val saldoHutangSetelah: Int = 0,
    val keterangan: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

// ═══════ LAPORAN DATA CLASS ═══════

data class LabaProduk(
    val menuId: Long, val namaMenu: String,
    val totalQty: Int, val totalOmzet: Int, val totalHpp: Int
) {
    val laba: Int get() = totalOmzet - totalHpp
    val marginPersen: Int get() = if (totalOmzet > 0) laba * 100 / totalOmzet else 0
}

data class HariPenjualan(
    val label: String, val omzet: Int, val transaksi: Int
)

data class MemberStat(
    val memberId: Long, val totalOrder: Int, val totalOmzet: Int
)

data class MenuTerlaris(
    val menuId: Long, val namaMenu: String,
    val totalQty: Int, val totalOmzet: Int
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
interface KategoriDao {
    @Query("SELECT * FROM kategori ORDER BY urutan ASC, nama ASC")
    fun observeAll(): Flow<List<Kategori>>
    @Query("SELECT * FROM kategori ORDER BY urutan ASC, nama ASC")
    suspend fun getAll(): List<Kategori>
    @Query("SELECT * FROM kategori WHERE nama = :nama LIMIT 1")
    suspend fun getByNama(nama: String): Kategori?
    @Query("SELECT * FROM kategori WHERE id = :id")
    suspend fun getById(id: Long): Kategori?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(k: Kategori): Long
    @Update
    suspend fun update(k: Kategori)
    @Delete
    suspend fun delete(k: Kategori)
    @Query("SELECT COALESCE(MAX(urutan), 0) FROM kategori")
    suspend fun maxUrutan(): Int
}

@Dao
interface MenuDao {
    @Query("SELECT * FROM menu_items ORDER BY kategori, nama")
    fun observeAll(): Flow<List<MenuItem>>
    @Query("SELECT * FROM menu_items WHERE trackStok = 1 ORDER BY kategori, nama")
    fun observeTrackStok(): Flow<List<MenuItem>>
    @Query("SELECT * FROM menu_items WHERE id = :id")
    suspend fun getById(id: Long): MenuItem?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: MenuItem): Long
    @Update
    suspend fun update(item: MenuItem)
    @Delete
    suspend fun delete(item: MenuItem)
    @Query("UPDATE menu_items SET tersedia = :tersedia WHERE id = :id")
    suspend fun setTersedia(id: Long, tersedia: Boolean)
    @Query("UPDATE menu_items SET stok = :stok WHERE id = :id")
    suspend fun updateStok(id: Long, stok: Int)
    @Query("SELECT COUNT(*) FROM menu_items WHERE trackStok = 1 AND stok <= stokMinimal")
    fun countLowStock(): Flow<Int>
    @Query("SELECT * FROM menu_items WHERE trackStok = 1 AND stok <= stokMinimal ORDER BY stok ASC")
    fun observeLowStock(): Flow<List<MenuItem>>
}

@Dao
interface StockDao {
    @Query("SELECT * FROM stock_movements ORDER BY timestamp DESC LIMIT 500")
    fun observeRecent(): Flow<List<StockMovement>>
    @Query("SELECT * FROM stock_movements WHERE menuId = :menuId ORDER BY timestamp DESC LIMIT 200")
    fun observeForMenu(menuId: Long): Flow<List<StockMovement>>
    @Query("SELECT * FROM stock_movements ORDER BY timestamp DESC LIMIT 500")
    suspend fun getRecent(): List<StockMovement>
    @Insert
    suspend fun insert(m: StockMovement): Long
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
    @Query("UPDATE orders SET stokDipotong = :v WHERE id = :id")
    suspend fun setStokDipotong(id: Long, v: Boolean)

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

    @Query("""
        SELECT oi.menuId AS menuId, oi.namaMenu AS namaMenu,
               SUM(oi.qty) AS totalQty,
               SUM(oi.hargaSatuan * oi.qty) AS totalOmzet,
               SUM(oi.qty * COALESCE(m.hargaBeli, 0)) AS totalHpp
        FROM order_items oi
        JOIN orders o ON o.id = oi.orderId
        LEFT JOIN menu_items m ON m.id = oi.menuId
        WHERE o.status = 'PAID' AND o.timestamp >= :start AND o.timestamp <= :end
        GROUP BY oi.menuId, oi.namaMenu
        ORDER BY (SUM(oi.hargaSatuan * oi.qty) - SUM(oi.qty * COALESCE(m.hargaBeli, 0))) DESC
    """)
    suspend fun labaPerProduk(start: Long, end: Long): List<LabaProduk>

    @Query("""
        SELECT oi.menuId AS menuId, oi.namaMenu AS namaMenu,
               SUM(oi.qty) AS totalQty,
               SUM(oi.hargaSatuan * oi.qty) AS totalOmzet
        FROM order_items oi
        JOIN orders o ON o.id = oi.orderId
        WHERE o.status = 'PAID' AND o.timestamp >= :start AND o.timestamp <= :end
        GROUP BY oi.menuId, oi.namaMenu
        ORDER BY SUM(oi.qty) DESC
        LIMIT :limit
    """)
    suspend fun menuTerlaris(start: Long, end: Long, limit: Int = 5): List<MenuTerlaris>

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

@Dao
interface MemberDao {
    @Query("SELECT * FROM members ORDER BY nama ASC")
    fun observeAll(): Flow<List<Member>>
    @Query("SELECT * FROM members WHERE aktif = 1 ORDER BY nama ASC")
    fun observeActive(): Flow<List<Member>>
    @Query("SELECT * FROM members WHERE id = :id")
    suspend fun getById(id: Long): Member?
    @Query("SELECT * FROM members WHERE telepon = :telp LIMIT 1")
    suspend fun getByTelepon(telp: String): Member?
    @Query("SELECT * FROM members WHERE nama LIKE :q OR telepon LIKE :q ORDER BY nama ASC LIMIT 30")
    suspend fun search(q: String): List<Member>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(m: Member): Long
    @Update
    suspend fun update(m: Member)
    @Delete
    suspend fun delete(m: Member)
    @Query("SELECT COUNT(*) FROM members WHERE aktif = 1")
    fun countActive(): Flow<Int>
}

@Dao
interface VoucherDao {
    @Query("SELECT * FROM vouchers ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Voucher>>
    @Query("SELECT * FROM vouchers WHERE kode = :kode LIMIT 1")
    suspend fun getByKode(kode: String): Voucher?
    @Query("SELECT * FROM vouchers WHERE aktif = 1 AND (tglMulai = 0 OR tglMulai <= :now) AND (tglAkhir = 0 OR tglAkhir >= :now) ORDER BY createdAt DESC")
    suspend fun getActive(now: Long = System.currentTimeMillis()): List<Voucher>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(v: Voucher)
    @Update
    suspend fun update(v: Voucher)
    @Delete
    suspend fun delete(v: Voucher)
}

@Dao
interface MemberTxDao {
    @Query("SELECT * FROM member_transactions WHERE memberId = :memberId ORDER BY timestamp DESC LIMIT 200")
    fun observeForMember(memberId: Long): Flow<List<MemberTransaction>>
    @Insert
    suspend fun insert(tx: MemberTransaction)
    @Query("SELECT * FROM orders WHERE memberId = :memberId AND status = 'PAID' ORDER BY timestamp DESC LIMIT 50")
    suspend fun ordersForMember(memberId: Long): List<Order>
    @Query("""
        SELECT o.memberId AS memberId, COUNT(o.id) AS totalOrder,
               COALESCE(SUM(o.total), 0) AS totalOmzet
        FROM orders o WHERE o.memberId = :memberId AND o.status = 'PAID'
        GROUP BY o.memberId
    """)
    suspend fun statForMember(memberId: Long): MemberStat?
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

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE orders ADD COLUMN voucherKode TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE orders ADD COLUMN voucherAmount INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE orders ADD COLUMN memberId INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE orders ADD COLUMN memberNama TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE orders ADD COLUMN poinDidapat INTEGER NOT NULL DEFAULT 0")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `members` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `nama` TEXT NOT NULL DEFAULT '',
                `telepon` TEXT NOT NULL DEFAULT '',
                `email` TEXT NOT NULL DEFAULT '',
                `alamat` TEXT NOT NULL DEFAULT '',
                `poin` INTEGER NOT NULL DEFAULT 0,
                `totalBelanja` INTEGER NOT NULL DEFAULT 0,
                `tier` TEXT NOT NULL DEFAULT 'BASIC',
                `hutang` INTEGER NOT NULL DEFAULT 0,
                `aktif` INTEGER NOT NULL DEFAULT 1,
                `createdAt` INTEGER NOT NULL DEFAULT 0,
                `catatan` TEXT NOT NULL DEFAULT ''
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `vouchers` (
                `kode` TEXT PRIMARY KEY NOT NULL,
                `nama` TEXT NOT NULL DEFAULT '',
                `tipe` TEXT NOT NULL DEFAULT 'NOMINAL',
                `value` INTEGER NOT NULL DEFAULT 0,
                `minBelanja` INTEGER NOT NULL DEFAULT 0,
                `maxDiskon` INTEGER NOT NULL DEFAULT 0,
                `kuota` INTEGER NOT NULL DEFAULT 0,
                `terpakai` INTEGER NOT NULL DEFAULT 0,
                `tglMulai` INTEGER NOT NULL DEFAULT 0,
                `tglAkhir` INTEGER NOT NULL DEFAULT 0,
                `aktif` INTEGER NOT NULL DEFAULT 1,
                `createdAt` INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `member_transactions` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `memberId` INTEGER NOT NULL DEFAULT 0,
                `orderId` INTEGER NOT NULL DEFAULT 0,
                `tipe` TEXT NOT NULL DEFAULT 'POIN_EARN',
                `poinDelta` INTEGER NOT NULL DEFAULT 0,
                `hutangDelta` INTEGER NOT NULL DEFAULT 0,
                `saldoPoinSetelah` INTEGER NOT NULL DEFAULT 0,
                `saldoHutangSetelah` INTEGER NOT NULL DEFAULT 0,
                `keterangan` TEXT NOT NULL DEFAULT '',
                `timestamp` INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Menu stok fields
        db.execSQL("ALTER TABLE menu_items ADD COLUMN kategoriId INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE menu_items ADD COLUMN trackStok INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE menu_items ADD COLUMN stok INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE menu_items ADD COLUMN stokMinimal INTEGER NOT NULL DEFAULT 5")

        // Order stok flag
        db.execSQL("ALTER TABLE orders ADD COLUMN stokDipotong INTEGER NOT NULL DEFAULT 0")

        // Kategori table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `kategori` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `nama` TEXT NOT NULL DEFAULT '',
                `warnaHex` TEXT NOT NULL DEFAULT '#FF6B35',
                `urutan` INTEGER NOT NULL DEFAULT 0,
                `aktif` INTEGER NOT NULL DEFAULT 1,
                `createdAt` INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())

        // Stock movements table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `stock_movements` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `menuId` INTEGER NOT NULL DEFAULT 0,
                `namaMenu` TEXT NOT NULL DEFAULT '',
                `tipe` TEXT NOT NULL DEFAULT 'ADJUST',
                `qty` INTEGER NOT NULL DEFAULT 0,
                `stokSebelum` INTEGER NOT NULL DEFAULT 0,
                `stokSesudah` INTEGER NOT NULL DEFAULT 0,
                `keterangan` TEXT NOT NULL DEFAULT '',
                `userId` TEXT NOT NULL DEFAULT '',
                `userName` TEXT NOT NULL DEFAULT '',
                `orderId` INTEGER NOT NULL DEFAULT 0,
                `timestamp` INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
    }
}

// ═══════ DATABASE ═══════

@Database(
    entities = [
        User::class, UserPermission::class, AuditLog::class, AppSetting::class,
        FeatureToggleEntity::class, Kategori::class, MenuItem::class,
        StockMovement::class, Order::class, OrderItem::class, Shift::class,
        Member::class, Voucher::class, MemberTransaction::class
    ],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun permissionDao(): PermissionDao
    abstract fun auditDao(): AuditDao
    abstract fun settingDao(): SettingDao
    abstract fun featureDao(): FeatureDao
    abstract fun kategoriDao(): KategoriDao
    abstract fun menuDao(): MenuDao
    abstract fun stockDao(): StockDao
    abstract fun orderDao(): OrderDao
    abstract fun shiftDao(): ShiftDao
    abstract fun memberDao(): MemberDao
    abstract fun voucherDao(): VoucherDao
    abstract fun memberTxDao(): MemberTxDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "iyonzkasir.db"
                ).addMigrations(
                    MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                    MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8
                ).build().also { INSTANCE = it }
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

    suspend fun getPrinterMac() = get(KEY_PRINTER_MAC, "")
    suspend fun setPrinterMac(mac: String) = set(KEY_PRINTER_MAC, mac)
    suspend fun getPrinterNama() = get(KEY_PRINTER_NAMA, "")
    suspend fun setPrinterNama(nama: String) = set(KEY_PRINTER_NAMA, nama)
    suspend fun getPaperWidth() = get(KEY_PAPER_WIDTH, "58").toIntOrNull() ?: 58
    suspend fun setPaperWidth(w: Int) = set(KEY_PAPER_WIDTH, w.toString())
    suspend fun isAutoPrint() = get(KEY_AUTO_PRINT, "0") == "1"
    suspend fun setAutoPrint(v: Boolean) = set(KEY_AUTO_PRINT, if (v) "1" else "0")

    suspend fun getLastBackupTimestamp() =
        get(KEY_LAST_BACKUP, "0").toLongOrNull() ?: 0L
    suspend fun setLastBackupTimestamp(ts: Long) = set(KEY_LAST_BACKUP, ts.toString())
    suspend fun getLastBackupName() = get(KEY_LAST_BACKUP_NAME, "")
    suspend fun setLastBackupName(name: String) = set(KEY_LAST_BACKUP_NAME, name)

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
        const val KEY_LAST_BACKUP = "last_backup_ts"
        const val KEY_LAST_BACKUP_NAME = "last_backup_name"
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

class KategoriRepository(private val dao: KategoriDao) {
    val all: Flow<List<Kategori>> = dao.observeAll()

    suspend fun getAll() = dao.getAll()
    suspend fun getById(id: Long) = dao.getById(id)
    suspend fun getByNama(nama: String) = dao.getByNama(nama)

    suspend fun save(k: Kategori): Long {
        val id = if (k.id == 0L) {
            // kategori baru — taruh di urutan paling akhir
            val next = dao.maxUrutan() + 1
            dao.upsert(k.copy(urutan = next))
        } else {
            dao.upsert(k)
        }
        return id
    }
    suspend fun delete(k: Kategori) = dao.delete(k)
    suspend fun update(k: Kategori) = dao.update(k)
}

class StockRepository(
    private val menuDao: MenuDao,
    private val stockDao: StockDao,
    private val userRepo: UserRepository
) {
    val movements: Flow<List<StockMovement>> = stockDao.observeRecent()
    val lowStock: Flow<List<MenuItem>> = menuDao.observeLowStock()
    val lowStockCount: Flow<Int> = menuDao.countLowStock()
    val trackStokMenus: Flow<List<MenuItem>> = menuDao.observeTrackStok()

    fun observeMovementsForMenu(menuId: Long) = stockDao.observeForMenu(menuId)

    /** Sesuaikan stok manual (opname / koreksi). */
    suspend fun adjustStock(
        menuId: Long,
        newStok: Int,
        keterangan: String = "",
        tipe: StockMovementType = StockMovementType.OPNAME
    ): Boolean {
        val menu = menuDao.getById(menuId) ?: return false
        if (!menu.trackStok) return false
        val sebelum = menu.stok
        val sesudah = newStok.coerceAtLeast(0)
        menuDao.updateStok(menuId, sesudah)
        val u = Session.current
        stockDao.insert(StockMovement(
            menuId = menuId,
            namaMenu = menu.nama,
            tipe = tipe.id,
            qty = sesudah - sebelum,
            stokSebelum = sebelum,
            stokSesudah = sesudah,
            keterangan = keterangan,
            userId = u?.id ?: "",
            userName = u?.nama ?: ""
        ))
        return true
    }

    /** Tambah stok (restock/pemasukan). */
    suspend fun tambahStok(
        menuId: Long, jumlah: Int, keterangan: String = ""
    ): Boolean {
        if (jumlah <= 0) return false
        val menu = menuDao.getById(menuId) ?: return false
        if (!menu.trackStok) return false
        val sebelum = menu.stok
        val sesudah = sebelum + jumlah
        menuDao.updateStok(menuId, sesudah)
        val u = Session.current
        stockDao.insert(StockMovement(
            menuId = menuId,
            namaMenu = menu.nama,
            tipe = StockMovementType.IN.id,
            qty = jumlah,
            stokSebelum = sebelum,
            stokSesudah = sesudah,
            keterangan = keterangan,
            userId = u?.id ?: "",
            userName = u?.nama ?: ""
        ))
        return true
    }

    /** Potong stok karena penjualan. */
    suspend fun potongStokPenjualan(
        menuId: Long, qty: Int, orderId: Long
    ): Boolean {
        if (qty <= 0) return false
        val menu = menuDao.getById(menuId) ?: return false
        if (!menu.trackStok) return false
        val sebelum = menu.stok
        val sesudah = (sebelum - qty).coerceAtLeast(0)
        menuDao.updateStok(menuId, sesudah)
        val u = Session.current
        stockDao.insert(StockMovement(
            menuId = menuId,
            namaMenu = menu.nama,
            tipe = StockMovementType.SALE.id,
            qty = -qty,
            stokSebelum = sebelum,
            stokSesudah = sesudah,
            keterangan = "Penjualan order #$orderId",
            userId = u?.id ?: "",
            userName = u?.nama ?: "",
            orderId = orderId
        ))
        return true
    }

    /** Kembalikan stok (saat void/refund). */
    suspend fun kembalikanStok(
        menuId: Long, qty: Int, orderId: Long, keterangan: String = "Retur void"
    ): Boolean {
        if (qty <= 0) return false
        val menu = menuDao.getById(menuId) ?: return false
        if (!menu.trackStok) return false
        val sebelum = menu.stok
        val sesudah = sebelum + qty
        menuDao.updateStok(menuId, sesudah)
        val u = Session.current
        stockDao.insert(StockMovement(
            menuId = menuId,
            namaMenu = menu.nama,
            tipe = StockMovementType.VOID_RETURN.id,
            qty = qty,
            stokSebelum = sebelum,
            stokSesudah = sesudah,
            keterangan = keterangan,
            userId = u?.id ?: "",
            userName = u?.nama ?: "",
            orderId = orderId
        ))
        return true
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

    suspend fun markStokDipotong(orderId: Long, v: Boolean) =
        orderDao.setStokDipotong(orderId, v)

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
    suspend fun menuTerlaris(start: Long, end: Long, limit: Int = 5) =
        orderDao.menuTerlaris(start, end, limit)
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

class CrmRepository(
    private val memberDao: MemberDao,
    private val voucherDao: VoucherDao,
    private val txDao: MemberTxDao
) {
    val members: Flow<List<Member>> = memberDao.observeAll()
    val activeMembers: Flow<List<Member>> = memberDao.observeActive()
    val vouchers: Flow<List<Voucher>> = voucherDao.observeAll()
    val memberCount: Flow<Int> = memberDao.countActive()

    suspend fun getMember(id: Long) = memberDao.getById(id)
    suspend fun findMemberByTelepon(telp: String) = memberDao.getByTelepon(telp)
    suspend fun searchMember(q: String) = memberDao.search("%$q%")
    suspend fun saveMember(m: Member): Long = memberDao.upsert(m)
    suspend fun deleteMember(m: Member) = memberDao.delete(m)

    suspend fun getVoucher(kode: String) = voucherDao.getByKode(kode)
    suspend fun getActiveVouchers() = voucherDao.getActive()
    suspend fun saveVoucher(v: Voucher) = voucherDao.upsert(v)
    suspend fun deleteVoucher(v: Voucher) = voucherDao.delete(v)

    fun observeMemberTx(memberId: Long) = txDao.observeForMember(memberId)
    suspend fun statMember(memberId: Long) = txDao.statForMember(memberId)
    suspend fun ordersForMember(memberId: Long) = txDao.ordersForMember(memberId)

    suspend fun processOrder(memberId: Long, order: Order) {
        val m = memberDao.getById(memberId) ?: return
        var newPoin = m.poin
        var newHutang = m.hutang
        var newTotalBelanja = m.totalBelanja

        val poinDidapat = LoyaltyConfig.hitungPoinDidapat(order.total)
        if (poinDidapat > 0) {
            newPoin += poinDidapat
            txDao.insert(MemberTransaction(
                memberId = memberId, orderId = order.id,
                tipe = "POIN_EARN", poinDelta = poinDidapat,
                saldoPoinSetelah = newPoin, saldoHutangSetelah = newHutang,
                keterangan = "Poin dari order #${order.id}"
            ))
        }

        if (order.metodeBayar == PaymentMethod.HUTANG.id) {
            newHutang += order.total
            txDao.insert(MemberTransaction(
                memberId = memberId, orderId = order.id,
                tipe = "HUTANG_ADD", hutangDelta = order.total,
                saldoPoinSetelah = newPoin, saldoHutangSetelah = newHutang,
                keterangan = "Hutang dari order #${order.id}"
            ))
        }

        newTotalBelanja += order.total
        val newTier = MemberTier.fromTotalBelanja(newTotalBelanja)

        memberDao.update(m.copy(
            poin = newPoin, hutang = newHutang,
            totalBelanja = newTotalBelanja, tier = newTier.id
        ))
    }

    suspend fun bayarHutang(memberId: Long, jumlah: Int, keterangan: String = "") {
        val m = memberDao.getById(memberId) ?: return
        if (jumlah <= 0) return
        val bayar = jumlah.coerceAtMost(m.hutang)
        val newHutang = m.hutang - bayar
        txDao.insert(MemberTransaction(
            memberId = memberId, tipe = "HUTANG_PAY",
            hutangDelta = -bayar,
            saldoPoinSetelah = m.poin, saldoHutangSetelah = newHutang,
            keterangan = keterangan.ifBlank { "Bayar hutang" }
        ))
        memberDao.update(m.copy(hutang = newHutang))
    }

    suspend fun redeemPoin(memberId: Long, poin: Int): Int {
        val m = memberDao.getById(memberId) ?: return 0
        if (poin <= 0 || poin > m.poin) return 0
        val rupiah = LoyaltyConfig.poinKeRupiah(poin)
        val newPoin = m.poin - poin
        txDao.insert(MemberTransaction(
            memberId = memberId, tipe = "POIN_REDEEM",
            poinDelta = -poin,
            saldoPoinSetelah = newPoin, saldoHutangSetelah = m.hutang,
            keterangan = "Tukar $poin poin = ${rupiah} rupiah"
        ))
        memberDao.update(m.copy(poin = newPoin))
        return rupiah
    }

    suspend fun adjust(memberId: Long, poinDelta: Int, hutangDelta: Int, ket: String) {
        val m = memberDao.getById(memberId) ?: return
        val newPoin = (m.poin + poinDelta).coerceAtLeast(0)
        val newHutang = (m.hutang + hutangDelta).coerceAtLeast(0)
        txDao.insert(MemberTransaction(
            memberId = memberId, tipe = "ADJUST",
            poinDelta = poinDelta, hutangDelta = hutangDelta,
            saldoPoinSetelah = newPoin, saldoHutangSetelah = newHutang,
            keterangan = ket
        ))
        memberDao.update(m.copy(poin = newPoin, hutang = newHutang))
    }

    suspend fun markVoucherUsed(kode: String) {
        val v = voucherDao.getByKode(kode) ?: return
        voucherDao.update(v.copy(terpakai = v.terpakai + 1))
    }
}
