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

@Entity(
    tableName = "user_permissions",
    primaryKeys = ["userId", "permissionKey"]
)
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
    val total: Int = 0,
    val metodeBayar: String = "",
    val dibayar: Int = 0,
    val kembalian: Int = 0,
    val status: String = OrderStatus.PAID.id,
    val catatan: String = "",
    val kasirId: String = "",
    val kasirNama: String = "",
    val voidReason: String = ""
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

    @Query("SELECT * FROM user_permissions")
    suspend fun getAll(): List<UserPermission>

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
}

// ═══════ MIGRATION v2 → v3 ═══════

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

// ═══════ DATABASE ═══════

@Database(
    entities = [
        User::class, UserPermission::class, AuditLog::class, AppSetting::class,
        FeatureToggleEntity::class, MenuItem::class, Order::class, OrderItem::class
    ],
    version = 3,
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

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "iyonzkasir.db"
                ).addMigrations(MIGRATION_2_3)
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
        permDao.clearForUser(user.id)
        dao.delete(user)
    }
    suspend fun ownerCount() = dao.ownerCount()
    suspend fun totalCount() = dao.totalCount()
    suspend fun updateLastLogin(id: String) = dao.updateLastLogin(id)

    // ── Permission ──
    fun observePermissions(userId: String) = permDao.observeForUser(userId)
    suspend fun getPermissions(userId: String) = permDao.getForUser(userId)
    suspend fun setPermission(userId: String, key: PermissionKey, allowed: Boolean) {
        permDao.upsert(UserPermission(userId, key.key, allowed))
    }
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
        val existing = permDao.getForUser(userId)
        if (existing.isEmpty()) applyRolePreset(userId, role)
    }
    suspend fun canUser(userId: String, key: PermissionKey): Boolean {
        val list = permDao.getForUser(userId)
        return list.firstOrNull { it.permissionKey == key.key }?.allowed ?: false
    }

    // ── Audit ──
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

    companion object {
        const val KEY_NAMA_TOKO = "toko_nama"
        const val KEY_ALAMAT = "toko_alamat"
        const val KEY_TELEPON = "toko_telepon"
        const val KEY_LOGO = "toko_logo"
        const val KEY_FOOTER = "struk_footer"
        const val KEY_BUSINESS_TYPE = "business_type"
        const val KEY_ONBOARDING_DONE = "onboarding_done"
        const val KEY_TEMA_MODE = "tema_mode"
    }
}

class FeatureRepository(private val dao: FeatureDao) {
    val toggles: Flow<List<FeatureToggleEntity>> = dao.observeAll()

    suspend fun setEnabled(feature: FeatureKey, enabled: Boolean) {
        dao.upsert(FeatureToggleEntity(feature.key, enabled))
        refreshCache()
    }

    suspend fun setBatch(map: Map<FeatureKey, Boolean>) {
        dao.upsertAll(map.map { FeatureToggleEntity(it.key.key, it.value) })
        refreshCache()
    }

    /** Aktifkan semua fitur. */
    suspend fun enableAll() {
        dao.clear()
        dao.upsertAll(FeatureKey.values().map {
            FeatureToggleEntity(it.key, enabled = true)
        })
        refreshCache()
    }

    /** Aktifkan hanya fitur-fitur tertentu. */
    suspend fun setOnly(enabled: Set<FeatureKey>) {
        dao.clear()
        dao.upsertAll(FeatureKey.values().map {
            FeatureToggleEntity(it.key, enabled = it in enabled)
        })
        refreshCache()
    }

    /** Terapkan preset sesuai business type. Custom = semua OFF. */
    suspend fun applyPreset(type: BusinessType) {
        if (type == BusinessType.CUSTOM) {
            dao.clear()
            dao.upsertAll(FeatureKey.values().map {
                FeatureToggleEntity(it.key, enabled = false)
            })
        } else {
            setOnly(type.defaultFeatures)
        }
        refreshCache()
    }

    /** Kalau DB toggle kosong, aktifkan semua. */
    suspend fun ensureInitialized() {
        if (dao.getAllSync().isEmpty()) {
            enableAll()
        } else {
            refreshCache()
        }
    }

    suspend fun refreshCache() {
        val list = dao.getAllSync()
        val enabled = list.filter { it.enabled }
            .mapNotNull { FeatureKey.fromKey(it.featureKey) }
            .toSet()
        FeatureManager.update(enabled)
    }
}

class PosRepository(
    private val menuDao: MenuDao,
    private val orderDao: OrderDao
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

    fun observePaidSince(start: Long) = orderDao.observePaidSince(start)
    fun countPaidSince(start: Long) = orderDao.countPaidSince(start)
    fun sumPaidSince(start: Long) = orderDao.sumPaidSince(start)
}
