package com.iyonzkasir.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.iyonzkasir.*
import com.iyonzkasir.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ═══════════════════════════════════════════════════════════
// VIEWMODEL
// ═══════════════════════════════════════════════════════════
class SyncWebViewModel(
    private val app: IyonzApp
) : ViewModel() {

    private val _loggedIn = MutableStateFlow(FirebaseManager.isLoggedIn)
    val loggedIn: StateFlow<Boolean> = _loggedIn.asStateFlow()

    private val _email = MutableStateFlow(FirebaseManager.userEmail ?: "")
    val email: StateFlow<String> = _email.asStateFlow()

    private val _storeId = MutableStateFlow("")
    val storeId: StateFlow<String> = _storeId.asStateFlow()

    private val _lastSyncTs = MutableStateFlow(0L)
    val lastSyncTs: StateFlow<Long> = _lastSyncTs.asStateFlow()

    private val _imgbbKey = MutableStateFlow("")
    val imgbbKey: StateFlow<String> = _imgbbKey.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _progress = MutableStateFlow<SyncService.SyncProgress?>(null)
    val progress: StateFlow<SyncService.SyncProgress?> = _progress.asStateFlow()

    private val _lastResult = MutableStateFlow<String?>(null)
    val lastResult: StateFlow<String?> = _lastResult.asStateFlow()

    init {
        viewModelScope.launch {
            _storeId.value = app.settingRepo.getStoreId()
            _lastSyncTs.value = app.settingRepo.getLastSyncTimestamp()
            _imgbbKey.value = app.settingRepo.getImgbbApiKey()
        }
    }

    fun login(email: String, password: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val result = FirebaseManager.loginOwner(email, password)
            result.onSuccess {
                _loggedIn.value = true
                _email.value = it.email ?: ""
                onResult(true, null)
            }.onFailure {
                onResult(false, it.message ?: "Login gagal")
            }
        }
    }

    fun logout() {
        FirebaseManager.logout()
        _loggedIn.value = false
        _email.value = ""
    }

    fun saveStoreId(id: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            app.settingRepo.setStoreId(id)
            _storeId.value = id
            onDone()
        }
    }

    fun saveImgbbKey(key: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            app.settingRepo.setImgbbApiKey(key)
            _imgbbKey.value = key
            onDone()
        }
    }

    fun doSync() {
        if (_syncing.value) return
        val sid = _storeId.value.trim()
        if (sid.isBlank()) {
            _lastResult.value = "❌ Store ID kosong. Isi dulu."
            return
        }

        viewModelScope.launch {
            _syncing.value = true
            _lastResult.value = null
            _progress.value = SyncService.SyncProgress(0, 1, "Memulai…")

            val service = SyncService(
                context = app.applicationContext,
                posRepo = app.posRepo,
                bundleRepo = app.bundleRepo,
                settingRepo = app.settingRepo
            )

            val result = service.syncAll(sid) { p ->
                _progress.value = p
            }

            _syncing.value = false

            when (result) {
                is SyncService.SyncResult.Success -> {
                    _lastResult.value = buildString {
                        append("✅ Sync berhasil!\n")
                        append("📋 ${result.totalMenu} menu\n")
                        append("🎁 ${result.totalBundle} paket\n")
                        if (result.totalPhotoUploaded > 0)
                            append("📷 ${result.totalPhotoUploaded} foto diupload\n")
                        if (result.totalPhotoSkipped > 0)
                            append("⚠️ ${result.totalPhotoSkipped} foto di-skip")
                    }
                    _lastSyncTs.value = System.currentTimeMillis()
                }
                is SyncService.SyncResult.Error -> {
                    _lastResult.value = "❌ Sync gagal: ${result.message}"
                }
            }
            _progress.value = null
        }
    }

    fun clearResult() { _lastResult.value = null }
}

class SyncWebVMFactory(private val app: IyonzApp) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        SyncWebViewModel(app) as T
}

// ═══════════════════════════════════════════════════════════
// ROUTE
// ═══════════════════════════════════════════════════════════
@Composable
fun SyncWebRoute(app: IyonzApp, nav: NavHostController) {
    val factory = remember { SyncWebVMFactory(app) }
    val vm: SyncWebViewModel = viewModel(factory = factory)
    SyncWebScreen(vm, nav)
}

// ═══════════════════════════════════════════════════════════
// SCREEN
// ═══════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncWebScreen(vm: SyncWebViewModel, nav: NavHostController) {
    val loggedIn by vm.loggedIn.collectAsState()
    val email by vm.email.collectAsState()
    val storeId by vm.storeId.collectAsState()
    val lastSyncTs by vm.lastSyncTs.collectAsState()
    val imgbbKey by vm.imgbbKey.collectAsState()
    val syncing by vm.syncing.collectAsState()
    val progress by vm.progress.collectAsState()
    val lastResult by vm.lastResult.collectAsState()

    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var loginErr by remember { mutableStateOf<String?>(null) }
    var loginLoading by remember { mutableStateOf(false) }

    var storeIdInput by remember { mutableStateOf(storeId) }
    var imgbbInput by remember { mutableStateOf(imgbbKey) }
    var showImgbbEditor by remember { mutableStateOf(false) }

    LaunchedEffect(storeId) { storeIdInput = storeId }
    LaunchedEffect(imgbbKey) { imgbbInput = imgbbKey }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sinkronisasi Web") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BRAND, titleContentColor = Color.White
                )
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Sp.lg),
            verticalArrangement = Arrangement.spacedBy(Sp.md)
        ) {

            // ═══ STATUS CARD ═══
            Card(
                shape = Rd.cardLg,
                colors = CardDefaults.cardColors(
                    containerColor = if (loggedIn) BRAND.copy(alpha = 0.12f)
                    else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(Sp.lg),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(48.dp).clip(CircleShape)
                            .background(if (loggedIn) BRAND else Color.Gray),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (loggedIn) Icons.Default.CloudDone
                            else Icons.Default.CloudOff,
                            null, tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.width(Sp.md))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (loggedIn) "Owner Login" else "Belum Login",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            if (loggedIn) email else "Login untuk sync ke web",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (loggedIn) {
                        TextButton(onClick = { vm.logout() }) {
                            Text("Logout", color = DANGER)
                        }
                    }
                }
            }

            // ═══ LOGIN FORM (kalau belum login) ═══
            if (!loggedIn) {
                Card(shape = Rd.card) {
                    Column(Modifier.padding(Sp.lg),
                        verticalArrangement = Arrangement.spacedBy(Sp.sm)) {
                        Text("Login Owner",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                        Text("Pakai email owner yang terdaftar di Firebase",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)

                        OutlinedTextField(
                            value = emailInput,
                            onValueChange = { emailInput = it; loginErr = null },
                            label = { Text("Email") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it; loginErr = null },
                            label = { Text("Password") },
                            singleLine = true,
                            visualTransformation = if (passwordVisible)
                                VisualTransformation.None
                            else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        if (passwordVisible) Icons.Default.VisibilityOff
                                        else Icons.Default.Visibility, null
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        loginErr?.let {
                            Text(it, color = DANGER,
                                style = MaterialTheme.typography.bodySmall)
                        }

                        Button(
                            onClick = {
                                if (emailInput.isBlank() || passwordInput.isBlank()) {
                                    loginErr = "Email & password wajib diisi"
                                    return@Button
                                }
                                loginLoading = true
                                vm.login(emailInput, passwordInput) { ok, err ->
                                    loginLoading = false
                                    if (ok) {
                                        emailInput = ""
                                        passwordInput = ""
                                    } else {
                                        loginErr = err
                                    }
                                }
                            },
                            enabled = !loginLoading,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BRAND)
                        ) {
                            if (loginLoading) {
                                CircularProgressIndicator(
                                    Modifier.size(20.dp),
                                    color = Color.White, strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Login, null,
                                    Modifier.size(18.dp))
                                Spacer(Modifier.width(Sp.xs))
                                Text("Login", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // ═══ SETUP STORE ID ═══
            if (loggedIn) {
                Card(shape = Rd.card) {
                    Column(Modifier.padding(Sp.lg),
                        verticalArrangement = Arrangement.spacedBy(Sp.sm)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Store, null, tint = BRAND)
                            Spacer(Modifier.width(Sp.sm))
                            Text("Store ID",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium)
                        }
                        Text("ID unik toko kamu. Format: huruf kecil + tanda hubung.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = storeIdInput,
                                onValueChange = {
                                    storeIdInput = it.lowercase()
                                        .filter { c -> c.isLetterOrDigit() || c == '-' }
                                        .take(40)
                                },
                                label = { Text("Store ID") },
                                placeholder = { Text("cth: iyonz-warung") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                enabled = storeId.isBlank()
                            )
                            if (storeId.isNotBlank()) {
                                IconButton(onClick = {
                                    val cm = ctx.getSystemService(
                                        Context.CLIPBOARD_SERVICE
                                    ) as ClipboardManager
                                    cm.setPrimaryClip(
                                        ClipData.newPlainText("storeId", storeId)
                                    )
                                }) {
                                    Icon(Icons.Default.ContentCopy, null)
                                }
                            }
                        }

                        if (storeId.isBlank()) {
                            Button(
                                onClick = {
                                    if (storeIdInput.isBlank()) return@Button
                                    vm.saveStoreId(storeIdInput)
                                },
                                enabled = storeIdInput.isNotBlank(),
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = BRAND
                                )
                            ) {
                                Text("Simpan Store ID")
                            }
                            Text("⚠️ Store ID nggak bisa diubah setelah dipakai.",
                                style = MaterialTheme.typography.labelSmall,
                                color = DANGER)
                        } else {
                            Text("✅ Store ID aktif: $storeId",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF2EBD59),
                                fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // ═══ SYNC CARD ═══
            if (loggedIn && storeId.isNotBlank()) {
                Card(shape = Rd.card) {
                    Column(Modifier.padding(Sp.lg),
                        verticalArrangement = Arrangement.spacedBy(Sp.sm)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudUpload, null, tint = BRAND)
                            Spacer(Modifier.width(Sp.sm))
                            Text("Sync Data",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium)
                        }

                        // Last sync
                        Text(
                            if (lastSyncTs > 0) {
                                "Terakhir sync: " + SimpleDateFormat(
                                    "dd MMM yyyy • HH:mm",
                                    Locale("id")
                                ).format(Date(lastSyncTs))
                            } else "Belum pernah sync",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Progress
                        if (syncing && progress != null) {
                            val p = progress!!
                            Column {
                                LinearProgressIndicator(
                                    progress = {
                                        if (p.total > 0) p.current.toFloat() / p.total
                                        else 0f
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = BRAND
                                )
                                Spacer(Modifier.height(Sp.xs))
                                Text(p.message,
                                    style = MaterialTheme.typography.bodySmall)
                                Text("${p.current} / ${p.total}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        // Result
                        lastResult?.let { res ->
                            Surface(
                                color = if (res.startsWith("✅"))
                                    Color(0xFF2EBD59).copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(Rd.sm),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    Modifier.padding(Sp.sm),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(res,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (res.startsWith("✅"))
                                            Color(0xFF2EBD59)
                                        else MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.weight(1f))
                                    IconButton(
                                        onClick = { vm.clearResult() },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(Icons.Default.Close, null,
                                            Modifier.size(14.dp))
                                    }
                                }
                            }
                        }

                        // Sync button
                        Button(
                            onClick = { vm.doSync() },
                            enabled = !syncing,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BRAND
                            )
                        ) {
                            if (syncing) {
                                CircularProgressIndicator(
                                    Modifier.size(20.dp),
                                    color = Color.White, strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(Sp.sm))
                                Text("Syncing…")
                            } else {
                                Icon(Icons.Default.CloudSync, null,
                                    Modifier.size(20.dp))
                                Spacer(Modifier.width(Sp.sm))
                                Text("Sync Sekarang",
                                    fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // ═══ IMGBB SETUP ═══
            if (loggedIn) {
                Card(shape = Rd.card) {
                    Column(Modifier.padding(Sp.lg),
                        verticalArrangement = Arrangement.spacedBy(Sp.sm)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Image, null, tint = BRAND)
                            Spacer(Modifier.width(Sp.sm))
                            Text("ImgBB API Key",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = {
                                showImgbbEditor = !showImgbbEditor
                            }) {
                                Text(if (showImgbbEditor) "Tutup" else "Ubah")
                            }
                        }

                        Text(
                            if (imgbbKey.isBlank()) "⚠️ Belum diisi — foto nggak akan upload"
                            else "✅ Terisi (${imgbbKey.take(6)}••••${imgbbKey.takeLast(4)})",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (imgbbKey.isBlank()) DANGER
                            else Color(0xFF2EBD59)
                        )

                        if (showImgbbEditor) {
                            OutlinedTextField(
                                value = imgbbInput,
                                onValueChange = { imgbbInput = it.trim() },
                                label = { Text("API Key") },
                                placeholder = { Text("dari api.imgbb.com") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = {
                                    vm.saveImgbbKey(imgbbInput)
                                    showImgbbEditor = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = BRAND
                                )
                            ) {
                                Text("Simpan API Key")
                            }
                        }
                    }
                }
            }

            // ═══ INFO CARD ═══
            Card(
                shape = Rd.card,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(Modifier.padding(Sp.lg),
                    verticalArrangement = Arrangement.spacedBy(Sp.xs)) {
                    Text("💡 Info",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium)
                    Text("• Sync mengirim menu, paket, & info toko ke web",
                        style = MaterialTheme.typography.bodySmall)
                    Text("• Foto menu diupload ke ImgBB (gratis)",
                        style = MaterialTheme.typography.bodySmall)
                    Text("• Butuh internet — kalau offline, tunggu online",
                        style = MaterialTheme.typography.bodySmall)
                    Text("• Limit ImgBB: 32 foto/jam. Kalau lebih, dicicil.",
                        style = MaterialTheme.typography.bodySmall, color = WARNING)
                }
            }

            Spacer(Modifier.height(Sp.xxl))
        }
    }
}
