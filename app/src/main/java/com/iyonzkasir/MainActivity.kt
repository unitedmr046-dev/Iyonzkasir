package com.iyonzkasir

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.iyonzkasir.data.*
import com.iyonzkasir.ui.*

// ═══════════════════════════════════════════════════════════
// APPLICATION
// ═══════════════════════════════════════════════════════════
class IyonzApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.get(this) }

    val userRepo: UserRepository by lazy {
        UserRepository(database.userDao(), database.permissionDao(), database.auditDao())
    }
    val settingRepo: SettingRepository by lazy { SettingRepository(database.settingDao()) }
    val featureRepo: FeatureRepository by lazy { FeatureRepository(database.featureDao()) }
    val kategoriRepo: KategoriRepository by lazy { KategoriRepository(database.kategoriDao()) }
    val posRepo: PosRepository by lazy {
        PosRepository(database.menuDao(), database.orderDao(), database.shiftDao())
    }
    val stockRepo: StockRepository by lazy {
        StockRepository(database.menuDao(), database.stockDao(), userRepo)
    }
    val shiftRepo: ShiftRepository by lazy {
        ShiftRepository(database.shiftDao(), database.orderDao())
    }
    val crmRepo: CrmRepository by lazy {
        CrmRepository(database.memberDao(), database.voucherDao(), database.memberTxDao())
    }
}

// ═══════════════════════════════════════════════════════════
// SESSION
// ═══════════════════════════════════════════════════════════
object Session {
    private val _current = mutableStateOf<User?>(null)
    val current: User? get() = _current.value
    val currentState: State<User?> get() = _current

    private val _permissions = mutableStateOf<Set<PermissionKey>>(emptySet())
    val permissions: Set<PermissionKey> get() = _permissions.value
    val permissionsState: State<Set<PermissionKey>> get() = _permissions

    fun login(user: User, perms: Set<PermissionKey>) {
        _current.value = user; _permissions.value = perms
    }
    fun logout() { _current.value = null; _permissions.value = emptySet() }
    fun isOwner() = _current.value?.role == UserRole.OWNER.id

    fun can(p: PermissionKey): Boolean {
        val u = _current.value ?: return false
        if (u.role == UserRole.OWNER.id) return true
        return p in _permissions.value
    }
    fun updatePermissions(perms: Set<PermissionKey>) { _permissions.value = perms }
}

// ═══════════════════════════════════════════════════════════
// ROUTES
// ═══════════════════════════════════════════════════════════
object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val LOGIN = "login"
    const val MAIN = "main"

    // Bottom Nav Tabs (5)
    const val TAB_HOME = "tab_home"
    const val TAB_POS = "tab_pos"
    const val TAB_INVENTARIS = "tab_inventaris"
    const val TAB_DASHBOARD = "tab_dashboard"
    const val TAB_SETTINGS = "tab_settings"

    // Akses via Home shortcut
    const val TAB_MENU = "tab_menu"
    const val TAB_OPEN_BILL = "tab_openbill"
    const val TAB_RIWAYAT = "tab_riwayat"
    const val TAB_SHIFT = "tab_shift"

    // Fullscreen
    const val KERANJANG = "keranjang"
    const val BAYAR = "bayar"
    const val EDIT_MENU = "edit_menu"
    const val KELOLA_USER = "kelola_user"
    const val FEATURE_TOGGLE = "feature_toggle"
    const val PROFIL_TOKO = "profil_toko"
    const val TEMA = "tema"
    const val PRINTER = "printer"
    const val LAPORAN = "laporan"
    const val BACKUP = "backup"
    const val CRM = "crm"
    const val INVENTARIS = "inventaris"
    const val KATEGORI = "kategori"
    const val KEUANGAN = "keuangan"
    const val BARCODE_INFO = "barcode_info"
    const val TENTANG = "tentang"
}

// ═══════════════════════════════════════════════════════════
// MAIN ACTIVITY
// ═══════════════════════════════════════════════════════════
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as IyonzApp
        setContent { IyonzTheme { AppRoot(app) } }
    }
}

@Composable
fun AppRoot(app: IyonzApp) {
    val nav = rememberNavController()

    LaunchedEffect(Unit) {
        ThemeManager.update(app.settingRepo.getThemeMode())
        app.featureRepo.ensureInitialized()
        BackupScheduler.scheduleDaily(app)
        BackupNotifier.maybeRemind(app, app.settingRepo)
    }

    NavHost(navController = nav, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) {
            SplashScreen(app.settingRepo, app.userRepo,
                onNeedOnboarding = {
                    nav.navigate(Routes.ONBOARDING) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
                onNeedLogin = {
                    nav.navigate(Routes.LOGIN) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                })
        }
        composable(Routes.ONBOARDING) {
            OnboardingScreen(app) {
                nav.navigate(Routes.LOGIN) {
                    popUpTo(Routes.ONBOARDING) { inclusive = true }
                }
            }
        }
        composable(Routes.LOGIN) {
            LoginScreen(app.userRepo) { user, perms ->
                Session.login(user, perms)
                nav.navigate(Routes.MAIN) {
                    popUpTo(Routes.LOGIN) { inclusive = true }
                }
            }
        }
        composable(Routes.MAIN) { MainShell(app, nav) }

        // Fullscreen routes
        composable(Routes.KERANJANG) { KeranjangRoute(app, nav) }
        composable(Routes.BAYAR) { BayarRoute(app, nav) }
        composable(Routes.EDIT_MENU) { EditMenuRoute(app, nav, null) }
        composable("${Routes.EDIT_MENU}/{id}") { entry ->
            EditMenuRoute(app, nav, entry.arguments?.getString("id")?.toLongOrNull())
        }
        composable(Routes.KELOLA_USER) { KelolaUserRoute(app, nav) }
        composable(Routes.FEATURE_TOGGLE) { FeatureToggleRoute(app, nav) }
        composable(Routes.PROFIL_TOKO) { ProfilTokoRoute(app, nav) }
        composable(Routes.TEMA) { TemaRoute(app, nav) }
        composable(Routes.PRINTER) { PrinterRoute(app, nav) }
        composable(Routes.LAPORAN) { LaporanRoute(app, nav) }
        composable(Routes.BACKUP) { BackupRoute(app, nav) }
        composable(Routes.CRM) { CrmRoute(app, nav) }
        composable(Routes.INVENTARIS) { InventarisRoute(app, nav) }
        composable(Routes.KATEGORI) { KategoriRoute(app, nav) }
        composable(Routes.KEUANGAN) { KeuanganRoute(app, nav) }
        composable(Routes.BARCODE_INFO) { BarcodeInfoRoute(app, nav) }
        composable(Routes.TENTANG) { TentangRoute(nav) }
    }
}

// ═══════════════════════════════════════════════════════════
// MAIN SHELL (Bottom Navigation - 5 Tab)
// ═══════════════════════════════════════════════════════════
private data class NavTab(
    val route: String, val label: String, val icon: ImageVector,
    val feature: FeatureKey? = null,
    val permission: PermissionKey? = null
)

@Composable
fun MainShell(app: IyonzApp, nav: NavHostController) {
    val innerNav = rememberNavController()
    val enabledFeatures by FeatureManager.enabled.collectAsState()

    val allTabs = listOf(
        NavTab(Routes.TAB_HOME, "Home", Icons.Default.Home),
        NavTab(Routes.TAB_POS, "Kasir", Icons.Default.PointOfSale,
            permission = PermissionKey.JUAL),
        NavTab(Routes.TAB_INVENTARIS, "Stok", Icons.Default.Inventory,
            feature = FeatureKey.LOW_STOCK_ALERT, permission = PermissionKey.LIHAT_STOK),
        NavTab(Routes.TAB_DASHBOARD, "Laporan", Icons.Default.BarChart,
            feature = FeatureKey.LAPORAN_HARIAN, permission = PermissionKey.LIHAT_DASHBOARD),
        NavTab(Routes.TAB_SETTINGS, "Setelan", Icons.Default.Settings)
    )

    val visibleTabs = allTabs.filter { tab ->
        val featOk = tab.feature == null || tab.feature in enabledFeatures
        val permOk = tab.permission == null || Session.can(tab.permission)
        featOk && permOk
    }

    val innerBackStack by innerNav.currentBackStackEntryAsState()
    val currentRoute = innerBackStack?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                visibleTabs.forEach { t ->
                    NavigationBarItem(
                        selected = currentRoute == t.route,
                        onClick = {
                            innerNav.navigate(t.route) {
                                popUpTo(innerNav.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(t.icon, null) },
                        label = { Text(t.label,
                            style = MaterialTheme.typography.labelSmall) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = BRAND,
                            selectedTextColor = BRAND,
                            indicatorColor = BRAND_LIGHT
                        )
                    )
                }
            }
        }
    ) { pad ->
        NavHost(innerNav, startDestination = Routes.TAB_HOME,
            modifier = Modifier.padding(pad)) {
            // 5 Tab Utama
            composable(Routes.TAB_HOME) { HomeRoute(app, nav, innerNav) }
            composable(Routes.TAB_POS) { PosRoute(app, nav, innerNav) }
            composable(Routes.TAB_INVENTARIS) { InventarisRoute(app, nav) }
            composable(Routes.TAB_DASHBOARD) { DashboardRoute(app) }
            composable(Routes.TAB_SETTINGS) { SettingsRoute(app, nav) }

            // Akses via Home shortcut
            composable(Routes.TAB_MENU) { MenuRoute(app, nav, innerNav) }
            composable(Routes.TAB_OPEN_BILL) { OpenBillRoute(app, nav, innerNav) }
            composable(Routes.TAB_RIWAYAT) { RiwayatRoute(app) }
            composable(Routes.TAB_SHIFT) { ShiftRoute(app) }
        }
    }
}
