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
        UserRepository(database.userDao(), database.auditDao())
    }
    val settingRepo: SettingRepository by lazy {
        SettingRepository(database.settingDao())
    }
    val featureRepo: FeatureRepository by lazy {
        FeatureRepository(database.featureDao())
    }
    val posRepo: PosRepository by lazy {
        PosRepository(database.menuDao(), database.orderDao())
    }
}

// ═══════════════════════════════════════════════════════════
// SESSION
// ═══════════════════════════════════════════════════════════
object Session {
    private val _current = mutableStateOf<User?>(null)
    val current: User? get() = _current.value
    val currentState: State<User?> get() = _current

    fun login(user: User) { _current.value = user }
    fun logout() { _current.value = null }
    fun isOwner() = _current.value?.role == UserRole.OWNER.id

    fun can(feature: FeatureKey): Boolean {
        val u = _current.value ?: return false
        return when (u.role) {
            UserRole.OWNER.id -> true
            UserRole.SUPERVISOR.id -> true
            UserRole.KASIR.id -> feature in kasirAllowed
            else -> false
        }
    }

    private val kasirAllowed = setOf(FeatureKey.LAPORAN_HARIAN)
}

// ═══════════════════════════════════════════════════════════
// ROUTES
// ═══════════════════════════════════════════════════════════
object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val LOGIN = "login"
    const val MAIN = "main"

    const val TAB_POS = "tab_pos"
    const val TAB_OPEN_BILL = "tab_openbill"
    const val TAB_MENU = "tab_menu"
    const val TAB_RIWAYAT = "tab_riwayat"
    const val TAB_DASHBOARD = "tab_dashboard"
    const val TAB_SETTINGS = "tab_settings"

    const val KERANJANG = "keranjang"
    const val BAYAR = "bayar"
    const val EDIT_MENU = "edit_menu"
    const val KELOLA_USER = "kelola_user"
    const val FEATURE_TOGGLE = "feature_toggle"
    const val PROFIL_TOKO = "profil_toko"
    const val TENTANG = "tentang"
}

// ═══════════════════════════════════════════════════════════
// MAIN ACTIVITY
// ═══════════════════════════════════════════════════════════
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as IyonzApp
        setContent {
            IyonzTheme { AppRoot(app) }
        }
    }
}

@Composable
fun AppRoot(app: IyonzApp) {
    val nav = rememberNavController()

    LaunchedEffect(Unit) { app.featureRepo.refreshCache() }

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
                }
            )
        }
        composable(Routes.ONBOARDING) {
            OnboardingScreen(app) {
                nav.navigate(Routes.LOGIN) {
                    popUpTo(Routes.ONBOARDING) { inclusive = true }
                }
            }
        }
        composable(Routes.LOGIN) {
            LoginScreen(app.userRepo) { user ->
                Session.login(user)
                nav.navigate(Routes.MAIN) {
                    popUpTo(Routes.LOGIN) { inclusive = true }
                }
            }
        }
        composable(Routes.MAIN) { MainShell(app, nav) }
        composable(Routes.KERANJANG) { KeranjangRoute(app, nav) }
        composable(Routes.BAYAR) { BayarRoute(app, nav) }
        composable(Routes.EDIT_MENU) { EditMenuRoute(app, nav, null) }
        composable("${Routes.EDIT_MENU}/{id}") { entry ->
            EditMenuRoute(app, nav, entry.arguments?.getString("id")?.toLongOrNull())
        }
        composable(Routes.KELOLA_USER) { KelolaUserRoute(app, nav) }
        composable(Routes.FEATURE_TOGGLE) { FeatureToggleRoute(app, nav) }
        composable(Routes.PROFIL_TOKO) { ProfilTokoRoute(app, nav) }
        composable(Routes.TENTANG) { TentangRoute(nav) }
    }
}

private data class NavTab(
    val route: String, val label: String, val icon: ImageVector,
    val feature: FeatureKey? = null
)

@Composable
fun MainShell(app: IyonzApp, nav: NavHostController) {
    val innerNav = rememberNavController()
    val user by Session.currentState
    val enabledFeatures by FeatureManager.enabled.collectAsState()

    val allTabs = listOf(
        NavTab(Routes.TAB_POS, "Kasir", Icons.Default.PointOfSale),
        NavTab(Routes.TAB_OPEN_BILL, "Open Bill", Icons.Default.ReceiptLong, FeatureKey.OPEN_BILL),
        NavTab(Routes.TAB_MENU, "Menu", Icons.Default.Restaurant),
        NavTab(Routes.TAB_RIWAYAT, "Riwayat", Icons.Default.History),
        NavTab(Routes.TAB_DASHBOARD, "Dashboard", Icons.Default.Dashboard, FeatureKey.LAPORAN_HARIAN),
        NavTab(Routes.TAB_SETTINGS, "Setelan", Icons.Default.Settings)
    )

    val visibleTabs = allTabs.filter { tab ->
        val featOk = tab.feature == null || tab.feature in enabledFeatures
        val roleOk = when (user?.role) {
            UserRole.KASIR.id -> tab.route in setOf(
                Routes.TAB_POS, Routes.TAB_MENU, Routes.TAB_RIWAYAT
            )
            else -> true
        }
        featOk && roleOk
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
                        label = { Text(t.label, style = MaterialTheme.typography.labelSmall) },
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
        NavHost(innerNav, startDestination = Routes.TAB_POS,
            modifier = Modifier.padding(pad)) {
            composable(Routes.TAB_POS) { PosRoute(app, nav, innerNav) }
            composable(Routes.TAB_OPEN_BILL) { OpenBillRoute(app, nav, innerNav) }
            composable(Routes.TAB_MENU) { MenuRoute(app, nav, innerNav) }
            composable(Routes.TAB_RIWAYAT) { RiwayatRoute(app) }
            composable(Routes.TAB_DASHBOARD) { DashboardRoute(app) }
            composable(Routes.TAB_SETTINGS) { SettingsRoute(app, nav) }
        }
    }
}
