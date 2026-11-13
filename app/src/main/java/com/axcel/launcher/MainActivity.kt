package com.axcel.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

data class PhoneAppInfo(
    val label: String,
    val packageName: String,
    val icon: ImageBitmap,
    val dominantColor: Color
)

enum class AmbientTheme(val title: String, val primary: Color) {
    DYNAMIC("Adaptive", Color(0xFF2563EB)),
    ROYAL_FELT("Emerald", Color(0xFF059669)),
    MIDNIGHT("Midnight", Color(0xFF334155)),
    CRIMSON("Crimson", Color(0xFFE11D48))
}

enum class RobotType(val title: String, val subtitle: String, val accent: Color) {
    ASTRO("Axcel-Bot Astro", "Companion Bot Bulat", Color(0xFF38BDF8)),
    SCORPIO("Scorpio SC-7", "Mecha Kalajengking", Color(0xFFF97316)),
    XGODUCK("XGO-Duck", "Mecha Bebek Servo", Color(0xFFFACC15)),
    CRAWLER("Cyber Crawler", "Laba-Laba 6 Kaki", Color(0xFFA855F7)),
    TITAN("Mecha Titan", "Guardian Robot", Color(0xFFEF4444)),
    NEKO("Neko-Bot", "Kucing Cyber Neon", Color(0xFFEC4899)),
    DRONE("Orb Drone", "Giroskop Melayang", Color(0xFF10B981))
}

enum class DeckFilter(val label: String, val suit: String) {
    ALL("Semua", "♠♥♦♣"),
    FAVORITES("Favorit", "♥"),
    SOCIAL("Sosial & Chat", "♦"),
    TOOLS("Utilitas", "♣")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BackHandler(enabled = true) {}
            AxcelPhoneLauncherScreen()
        }
    }
}

@Composable
fun AxcelPhoneLauncherScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("axcel_phone_prefs", Context.MODE_PRIVATE) }

    var allApps by remember { mutableStateOf<List<PhoneAppInfo>>(emptyList()) }
    var pinnedPackages by remember {
        mutableStateOf(prefs.getStringSet("pinned_apps", emptySet())?.toList() ?: emptyList())
    }
    var dockPackages by remember {
        mutableStateOf(prefs.getStringSet("dock_apps", emptySet())?.toList() ?: emptyList())
    }
    var selectedTheme by remember {
        mutableStateOf(
            AmbientTheme.entries.find { it.name == prefs.getString("ambient_theme", "DYNAMIC") }
                ?: AmbientTheme.DYNAMIC
        )
    }
    var selectedRobot by remember {
        mutableStateOf(
            RobotType.entries.find { it.name == prefs.getString("selected_robot", "ASTRO") }
                ?: RobotType.ASTRO
        )
    }
    var activeFilter by remember { mutableStateOf(DeckFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var contextMenuApp by remember { mutableStateOf<PhoneAppInfo?>(null) }
    var showRobotPicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val loaded = loadPhoneApps(context)
        allApps = loaded
        if (pinnedPackages.isEmpty() && loaded.isNotEmpty()) {
            val defaults = loaded.take(6).map { it.packageName }
            pinnedPackages = defaults
            prefs.edit().putStringSet("pinned_apps", defaults.toSet()).apply()
        }
        if (dockPackages.isEmpty() && loaded.isNotEmpty()) {
            val dockDefaults = loaded.filter {
                it.packageName.contains("dialer") ||
                        it.packageName.contains("contacts") ||
                        it.packageName.contains("messaging") ||
                        it.packageName.contains("whatsapp") ||
                        it.packageName.contains("chrome") ||
                        it.packageName.contains("camera")
            }.take(4).map { it.packageName }.ifEmpty { loaded.take(4).map { it.packageName } }
            dockPackages = dockDefaults
            prefs.edit().putStringSet("dock_apps", dockDefaults.toSet()).apply()
        }
    }

    val filteredApps = remember(allApps, activeFilter, pinnedPackages, searchQuery) {
        var list = when (activeFilter) {
            DeckFilter.ALL -> allApps
            DeckFilter.FAVORITES -> allApps.filter { pinnedPackages.contains(it.packageName) }
            DeckFilter.SOCIAL -> allApps.filter {
                val p = it.packageName.lowercase()
                p.contains("whatsapp") || p.contains("telegram") || p.contains("instagram") ||
                        p.contains("facebook") || p.contains("tiktok") || p.contains("twitter") ||
                        p.contains("youtube") || p.contains("discord") || p.contains("messenger")
            }.ifEmpty { allApps.take(10) }
            DeckFilter.TOOLS -> allApps.filter {
                val p = it.packageName.lowercase()
                p.contains("settings") || p.contains("camera") || p.contains("gallery") ||
                        p.contains("photos") || p.contains("chrome") || p.contains("maps") ||
                        p.contains("calculator") || p.contains("clock") || p.contains("files")
            }.ifEmpty { allApps.takeLast(10) }
        }

        if (searchQuery.isNotBlank()) {
            list = list.filter { it.label.contains(searchQuery, ignoreCase = true) }
        }
        list
    }

    val dockApps = remember(allApps, dockPackages) {
        dockPackages.mapNotNull { pkg -> allApps.find { it.packageName == pkg } }.take(4)
    }

    val listState = rememberLazyListState()

    // Deteksi kartu yang berada paling tengah layar saat user menggeser (swipe)
    val centerCardIndex by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) 0
            else {
                val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                visibleItems.minByOrNull { item ->
                    abs((item.offset + item.size / 2) - viewportCenter)
                }?.index ?: 0
            }
        }
    }

    val safeCenterIndex = centerCardIndex.coerceIn(0, (filteredApps.size - 1).coerceAtLeast(0))
    val activeCardApp = filteredApps.getOrNull(safeCenterIndex)

    // Hitung arah geseran & step untuk animasi robot
    val scrollOffsetFraction by remember {
        derivedStateOf {
            val item = listState.layoutInfo.visibleItemsInfo.find { it.index == safeCenterIndex }
            if (item == null || item.size == 0) 0f
            else {
                val viewportCenter = (listState.layoutInfo.viewportStartOffset + listState.layoutInfo.viewportEndOffset) / 2f
                val itemCenter = item.offset + item.size / 2f
                ((itemCenter - viewportCenter) / item.size).coerceIn(-1f, 1f)
            }
        }
    }

    val targetGlowColor = when (selectedTheme) {
        AmbientTheme.DYNAMIC -> activeCardApp?.dominantColor ?: Color(0xFF2563EB)
        else -> selectedTheme.primary
    }

    val animatedGlowColor by animateColorAsState(
        targetValue = targetGlowColor,
        animationSpec = tween(550),
        label = "bgGlow"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF06080E))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        animatedGlowColor.copy(alpha = 0.45f),
                        Color(0xFF0A0F1C).copy(alpha = 0.92f),
                        Color(0xFF05070B)
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. HEADER SMARTPHONE: Jam Besar, Baterai, & Quick Pills
            PhoneTopHeader(
                currentTheme = selectedTheme,
                currentRobot = selectedRobot,
                onOpenRobotPicker = { showRobotPicker = true },
                onCycleTheme = {
                    val next = (selectedTheme.ordinal + 1) % AmbientTheme.entries.size
                    selectedTheme = AmbientTheme.entries[next]
                    prefs.edit().putString("ambient_theme", selectedTheme.name).apply()
                }
            )

            // 2. PANGGUNG ROBOT PENDAMPING + INFO KARTU AKTIF
            RobotCompanionStage(
                app = activeCardApp,
                cardNumber = if (filteredApps.isEmpty()) 0 else safeCenterIndex + 1,
                totalCards = filteredApps.size,
                isPinned = activeCardApp != null && pinnedPackages.contains(activeCardApp.packageName),
                robotType = selectedRobot,
                lookX = scrollOffsetFraction,
                stepTrigger = safeCenterIndex,
                onRobotClick = { showRobotPicker = true },
                onOpenActiveApp = {
                    activeCardApp?.let { launchPhoneApp(context, it.packageName) }
                }
            )

            // 3. TAB FILTER SUIT KARTU REMI (♠ ♥ ♦ ♣)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DeckFilter.entries.forEach { filter ->
                    val selected = filter == activeFilter
                    Box(modifier = Modifier.weight(1f)) {
                        SuitFilterChip(
                            suit = filter.suit,
                            label = filter.label,
                            selected = selected,
                            onClick = { activeFilter = filter }
                        )
                    }
                }
            }

            // 4. KIPAS JAJARAN KARTU REMI (SWIPEABLE PLAYING CARD CAROUSEL)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (filteredApps.isEmpty()) {
                    Text(
                        text = "Tidak ada kartu aplikasi ditemukan",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 15.sp
                    )
                } else {
                    LazyRow(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 96.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy((-32).dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        itemsIndexed(filteredApps, key = { _, app -> "card_${app.packageName}" }) { index, app ->
                            val isSelected = index == safeCenterIndex
                            val distance = index - safeCenterIndex

                            PhonePlayingCardItem(
                                app = app,
                                index = index,
                                isSelected = isSelected,
                                distanceFromCenter = distance,
                                isPinned = pinnedPackages.contains(app.packageName),
                                onClick = { launchPhoneApp(context, app.packageName) },
                                onLongClick = { contextMenuApp = app }
                            )
                        }
                    }
                }
            }

            // 5. SEARCH BAR & BOTTOM GLASS DOCK (KHAS SMARTPHONE)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            "Cari kartu aplikasi...",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 13.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Hapus",
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = CircleShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White.copy(alpha = 0.6f),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.16f),
                        focusedContainerColor = Color.White.copy(alpha = 0.12f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.07f)
                    )
                )

                // Glassmorphic Dock Bawah (4 Aplikasi Utama)
                if (dockApps.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(26.dp))
                            .background(Color.White.copy(alpha = 0.10f))
                            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(26.dp))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        dockApps.forEach { app ->
                            DockAppIcon(
                                app = app,
                                onClick = { launchPhoneApp(context, app.packageName) },
                                onLongClick = { contextMenuApp = app }
                            )
                        }
                    }
                }
            }
        }

        // Dialog Pilih Robot
        if (showRobotPicker) {
            RobotPickerDialog(
                selectedRobot = selectedRobot,
                onSelect = { robot ->
                    selectedRobot = robot
                    prefs.edit().putString("selected_robot", robot.name).apply()
                    showRobotPicker = false
                },
                onDismiss = { showRobotPicker = false }
            )
        }

        // Dialog Menu Konteks (Tekan Lama Kartu)
        contextMenuApp?.let { targetApp ->
            val isPinned = pinnedPackages.contains(targetApp.packageName)
            val isDocked = dockPackages.contains(targetApp.packageName)
            AppContextDialog(
                app = targetApp,
                isPinned = isPinned,
                isDocked = isDocked,
                onDismiss = { contextMenuApp = null },
                onTogglePin = {
                    val updated = if (isPinned) pinnedPackages - targetApp.packageName
                    else pinnedPackages + targetApp.packageName
                    pinnedPackages = updated
                    prefs.edit().putStringSet("pinned_apps", updated.toSet()).apply()
                    contextMenuApp = null
                },
                onToggleDock = {
                    val updated = if (isDocked) dockPackages - targetApp.packageName
                    else (dockPackages + targetApp.packageName).takeLast(4)
                    dockPackages = updated
                    prefs.edit().putStringSet("dock_apps", updated.toSet()).apply()
                    contextMenuApp = null
                },
                onAppInfo = {
                    openAppDetails(context, targetApp.packageName)
                    contextMenuApp = null
                },
                onUninstall = {
                    uninstallApp(context, targetApp.packageName)
                    contextMenuApp = null
                }
            )
        }
    }
}

@Composable
fun PhoneTopHeader(
    currentTheme: AmbientTheme,
    currentRobot: RobotType,
    onOpenRobotPicker: () -> Unit,
    onCycleTheme: () -> Unit
) {
    val context = LocalContext.current
    var currentTime by remember { mutableStateOf("") }
    var currentDate by remember { mutableStateOf("") }
    var batteryPct by remember { mutableIntStateOf(100) }
    var networkLabel by remember { mutableStateOf("Online") }

    LaunchedEffect(Unit) {
        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFmt = SimpleDateFormat("EEEE, d MMM", Locale("id", "ID"))
        while (true) {
            val now = Date()
            currentTime = timeFmt.format(now)
            currentDate = dateFmt.format(now)
            batteryPct = getBatteryLevel(context)
            networkLabel = getNetworkLabel(context)
            delay(5000L)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = currentTime,
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )
            Text(
                text = "$currentDate • $networkLabel • $batteryPct%",
                color = Color.White.copy(alpha = 0.68f),
                fontSize = 12.sp
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallGlassButton(
                icon = Icons.Rounded.SmartToy,
                label = "Robot",
                tint = currentRobot.accent,
                onClick = onOpenRobotPicker
            )
            SmallGlassButton(
                icon = Icons.Rounded.Palette,
                label = currentTheme.title,
                tint = Color.White,
                onClick = onCycleTheme
            )
            SmallGlassButton(
                icon = Icons.Rounded.Home,
                label = "Default",
                tint = Color(0xFFFBBF24),
                onClick = { openDefaultLauncherSettings(context) }
            )
            SmallGlassButton(
                icon = Icons.Rounded.Settings,
                label = "",
                tint = Color.White,
                onClick = { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SmallGlassButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.11f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
            .combinedClickable(onClick = onClick)
            .padding(horizontal = if (label.isEmpty()) 10.dp else 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(16.dp))
        if (label.isNotEmpty()) {
            Text(text = label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RobotCompanionStage(
    app: PhoneAppInfo?,
    cardNumber: Int,
    totalCards: Int,
    isPinned: Boolean,
    robotType: RobotType,
    lookX: Float,
    stepTrigger: Int,
    onRobotClick: () -> Unit,
    onOpenActiveApp: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        (app?.dominantColor ?: Color(0xFF1E293B)).copy(alpha = 0.52f),
                        Color(0xFF111625).copy(alpha = 0.88f)
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Info Kartu Tengah yang Sedang Terangkat
            Column(
                modifier = Modifier
                    .weight(1f)
                    .combinedClickable(onClick = onOpenActiveApp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "KARTU $cardNumber/$totalCards",
                        color = Color(0xFFFDE68A),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (isPinned) {
                        Text(
                            text = "• ♥ FAVORIT",
                            color = Color(0xFFF87171),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    text = app?.label ?: "Axcel Launcher",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Geser kartu di bawah • Tap buka • Tahan opsi",
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 11.sp
                )
            }

            // Robot Pendamping Interaktif (Tap untuk ganti jenis robot)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(18.dp))
                    .combinedClickable(onClick = onRobotClick)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                InteractiveRobotCanvas(
                    robotType = robotType,
                    accentColor = app?.dominantColor ?: robotType.accent,
                    lookX = lookX,
                    lookY = 0.4f,
                    stepTrigger = stepTrigger,
                    modifier = Modifier.size(78.dp)
                )
                Text(
                    text = robotType.title,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SuitFilterChip(
    suit: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Color.White else Color.White.copy(alpha = 0.08f))
            .border(
                1.dp,
                if (selected) Color.White else Color.White.copy(alpha = 0.14f),
                RoundedCornerShape(14.dp)
            )
            .combinedClickable(onClick = onClick)
            .padding(vertical = 7.dp, horizontal = 4.dp)
    ) {
        Text(
            text = suit,
            color = if (selected) Color(0xFFE11D48) else Color(0xFFFDE68A),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            color = if (selected) Color.Black else Color.White.copy(alpha = 0.85f),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhonePlayingCardItem(
    app: PhoneAppInfo,
    index: Int,
    isSelected: Boolean,
    distanceFromCenter: Int,
    isPinned: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val suits = listOf("♠", "♥", "♦", "♣")
    val suitSymbol = if (isPinned) "♥" else suits[index % suits.size]
    val isRedSuit = suitSymbol == "♥" || suitSymbol == "♦"
    val ranks = listOf("A", "K", "Q", "J", "10", "9", "8", "7", "6", "5", "4", "3", "2")
    val rankLabel = if (isPinned) "A" else ranks[index % ranks.size]

    // Efek angkat kartu remi saat berada di tengah layar HP
    val liftOffsetY by animateFloatAsState(
        targetValue = if (isSelected) -28f else (abs(distanceFromCenter).coerceAtMost(3) * 10f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "cardLift"
    )

    val cardScale by animateFloatAsState(
        targetValue = if (isSelected) 1.15f else 0.92f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "cardScale"
    )

    // Efek kipas kartu remi (kiri miring kiri, kanan miring kanan, tengah tegak 0°)
    val targetRotation = when {
        isSelected -> 0f
        distanceFromCenter < 0 -> (distanceFromCenter * 5.5f).coerceAtLeast(-16f)
        else -> (distanceFromCenter * 5.5f).coerceAtMost(16f)
    }
    val cardRotation by animateFloatAsState(
        targetValue = targetRotation,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "cardRotation"
    )

    val spreadOffsetX by animateFloatAsState(
        targetValue = when {
            isSelected -> 0f
            distanceFromCenter < 0 -> -14f
            else -> 14f
        },
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "spreadX"
    )

    val zOrder = if (isSelected) 50f else (20f - abs(distanceFromCenter).coerceAtMost(19))

    Box(
        modifier = Modifier
            .zIndex(zOrder)
            .graphicsLayer {
                translationY = liftOffsetY
                translationX = spreadOffsetX
                scaleX = cardScale
                scaleY = cardScale
                rotationZ = cardRotation
            }
            .width(172.dp)
            .height(254.dp)
            .shadow(
                elevation = if (isSelected) 28.dp else 8.dp,
                shape = RoundedCornerShape(22.dp),
                ambientColor = if (isSelected) Color(0xFFFBBF24) else Color.Black,
                spotColor = if (isSelected) Color.White else Color.Black
            )
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.verticalGradient(
                    colors = if (isSelected) {
                        listOf(
                            Color(0xFF1E293B),
                            app.dominantColor.copy(alpha = 0.88f),
                            Color(0xFF0F172A)
                        )
                    } else {
                        listOf(
                            Color(0xFF161E31),
                            Color(0xFF0D121E)
                        )
                    }
                )
            )
            .border(
                border = if (isSelected) {
                    BorderStroke(
                        3.dp,
                        Brush.linearGradient(
                            listOf(Color(0xFFFDE68A), Color.White, Color(0xFFF59E0B))
                        )
                    )
                } else {
                    BorderStroke(1.2.dp, Color.White.copy(alpha = 0.22f))
                },
                shape = RoundedCornerShape(22.dp)
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(14.dp)
    ) {
        // Sudut Kiri Atas Kartu Remi
        Column(
            modifier = Modifier.align(Alignment.TopStart),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = rankLabel,
                color = if (isSelected) Color(0xFFFDE68A) else Color.White.copy(alpha = 0.85f),
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 16.sp
            )
            Text(
                text = suitSymbol,
                color = if (isRedSuit) Color(0xFFF87171) else Color(0xFF93C5FD),
                fontSize = 16.sp,
                lineHeight = 16.sp
            )
        }

        // Bagian Tengah: Ikon Aplikasi & Nama
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(86.dp)
                    .shadow(if (isSelected) 16.dp else 4.dp, RoundedCornerShape(22.dp))
                    .clip(RoundedCornerShape(22.dp))
                    .background(app.dominantColor.copy(alpha = 0.35f))
                    .border(
                        1.5.dp,
                        if (isSelected) Color.White.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.16f),
                        RoundedCornerShape(22.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = app.icon,
                    contentDescription = app.label,
                    modifier = Modifier
                        .size(70.dp)
                        .clip(RoundedCornerShape(17.dp))
                )
            }

            Text(
                text = app.label,
                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.82f),
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                maxLines = 2,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp
            )
        }

        // Sudut Kanan Bawah Kartu Remi (Terbalik 180 derajat)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .graphicsLayer { rotationZ = 180f },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = rankLabel,
                color = if (isSelected) Color(0xFFFDE68A) else Color.White.copy(alpha = 0.85f),
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 16.sp
            )
            Text(
                text = suitSymbol,
                color = if (isRedSuit) Color(0xFFF87171) else Color(0xFF93C5FD),
                fontSize = 16.sp,
                lineHeight = 16.sp
            )
        }

        // Kilau kartu foil saat disorot
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.24f),
                                Color.Transparent,
                                Color(0xFFFDE68A).copy(alpha = 0.12f)
                            )
                        )
                    )
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DockAppIcon(
    app: PhoneAppInfo,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(4.dp)
    ) {
        Image(
            bitmap = app.icon,
            contentDescription = app.label,
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
        )
    }
}

// --- 7 ANIMASI ROBOT PROSEDURAL ---

@Composable
fun InteractiveRobotCanvas(
    robotType: RobotType,
    accentColor: Color,
    lookX: Float,
    lookY: Float,
    stepTrigger: Int,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "robotIdle")
    val idleFloat by infiniteTransition.animateFloat(
        initialValue = -3.5f,
        targetValue = 3.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idleFloat"
    )
    val legSwing by infiniteTransition.animateFloat(
        initialValue = -14f,
        targetValue = 14f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "legSwing"
    )

    var hopState by remember { mutableStateOf(false) }
    LaunchedEffect(stepTrigger) {
        hopState = true
        delay(150)
        hopState = false
    }

    val hopOffset by animateFloatAsState(
        targetValue = if (hopState) -12f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "hopOffset"
    )
    val strideAngle by animateFloatAsState(
        targetValue = if (stepTrigger % 2 == 0) 18f else -18f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
        label = "strideAngle"
    )
    val smoothLookX by animateFloatAsState(
        targetValue = lookX,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "lookX"
    )
    val smoothLookY by animateFloatAsState(
        targetValue = lookY,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "lookY"
    )

    val ledColor by animateColorAsState(
        targetValue = accentColor,
        animationSpec = tween(400),
        label = "ledColor"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f + idleFloat + hopOffset

        drawOval(
            color = Color.Black.copy(alpha = 0.42f),
            topLeft = Offset(cx - w * 0.28f, h - 10f),
            size = Size(w * 0.56f, 8f)
        )

        rotate(degrees = smoothLookX * 10f, pivot = Offset(cx, cy)) {
            when (robotType) {
                RobotType.ASTRO -> drawAstroBot(cx, cy, w, h, smoothLookX, smoothLookY, ledColor, hopState)
                RobotType.SCORPIO -> drawScorpioBot(cx, cy, w, h, smoothLookX, strideAngle, ledColor)
                RobotType.XGODUCK -> drawXgoDuckBot(cx, cy, w, h, smoothLookX, smoothLookY, strideAngle, ledColor)
                RobotType.CRAWLER -> drawCrawlerBot(cx, cy, w, h, smoothLookX, legSwing + strideAngle, ledColor)
                RobotType.TITAN -> drawTitanBot(cx, cy, w, h, smoothLookX, smoothLookY, strideAngle, ledColor)
                RobotType.NEKO -> drawNekoBot(cx, cy, w, h, smoothLookX, smoothLookY, ledColor, hopState)
                RobotType.DRONE -> drawDroneBot(cx, cy, w, h, smoothLookX, smoothLookY, legSwing, ledColor)
            }
        }
    }
}

private fun DrawScope.drawAstroBot(
    cx: Float, cy: Float, w: Float, h: Float,
    lookX: Float, lookY: Float, led: Color, boosting: Boolean
) {
    if (boosting) {
        drawCircle(color = led.copy(alpha = 0.75f), radius = w * 0.14f, center = Offset(cx, cy + h * 0.34f))
    }
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(Color.White, Color(0xFFCBD5E1))),
        topLeft = Offset(cx - w * 0.22f, cy + h * 0.04f),
        size = Size(w * 0.44f, h * 0.28f),
        cornerRadius = CornerRadius(20f, 20f)
    )
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(Color.White, Color(0xFFE2E8F0))),
        topLeft = Offset(cx - w * 0.28f, cy - h * 0.30f),
        size = Size(w * 0.56f, h * 0.33f),
        cornerRadius = CornerRadius(28f, 28f)
    )
    drawRoundRect(
        color = Color(0xFF0F172A),
        topLeft = Offset(cx - w * 0.22f, cy - h * 0.24f),
        size = Size(w * 0.44f, h * 0.21f),
        cornerRadius = CornerRadius(18f, 18f)
    )
    val ex = lookX * 8f
    val ey = lookY * 4f
    drawCircle(color = led, radius = 4.5f, center = Offset(cx - 9f + ex, cy - h * 0.13f + ey))
    drawCircle(color = led, radius = 4.5f, center = Offset(cx + 9f + ex, cy - h * 0.13f + ey))
}

private fun DrawScope.drawScorpioBot(
    cx: Float, cy: Float, w: Float, h: Float,
    lookX: Float, stride: Float, led: Color
) {
    val tailPath = Path().apply {
        moveTo(cx, cy + 4f)
        cubicTo(cx - 20f, cy - 32f, cx + 14f + lookX * 10f, cy - 38f, cx + 8f + lookX * 14f, cy - 18f)
    }
    drawPath(tailPath, color = Color(0xFF475569), style = Stroke(width = 7f, cap = StrokeCap.Round))
    drawCircle(color = led, radius = 4.5f, center = Offset(cx + 8f + lookX * 14f, cy - 17f))
    for (i in -1..1 step 2) {
        rotate(stride * i, pivot = Offset(cx + i * 12f, cy + 6f)) {
            drawLine(Color(0xFF94A3B8), Offset(cx + i * 10f, cy + 6f), Offset(cx + i * 24f, cy + 22f), 4.5f, StrokeCap.Round)
        }
    }
    drawRoundRect(Color(0xFF1E293B), Offset(cx - 19f, cy - 5f), Size(38f, 17f), CornerRadius(8f, 8f))
    drawRoundRect(led, Offset(cx - 12f + lookX * 6f, cy), Size(24f, 6f), CornerRadius(3f, 3f))
}

private fun DrawScope.drawXgoDuckBot(
    cx: Float, cy: Float, w: Float, h: Float,
    lookX: Float, lookY: Float, stride: Float, led: Color
) {
    rotate(stride, pivot = Offset(cx - 7f, cy + 12f)) {
        drawLine(Color(0xFFF59E0B), Offset(cx - 7f, cy + 12f), Offset(cx - 9f, cy + 26f), 4.5f, StrokeCap.Round)
    }
    rotate(-stride, pivot = Offset(cx + 7f, cy + 12f)) {
        drawLine(Color(0xFFF59E0B), Offset(cx + 7f, cy + 12f), Offset(cx + 9f, cy + 26f), 4.5f, StrokeCap.Round)
    }
    drawRoundRect(Color(0xFFFACC15), Offset(cx - 16f, cy - 3f), Size(32f, 17f), CornerRadius(10f, 10f))
    drawCircle(Color.White, 13f, Offset(cx + lookX * 4f, cy - 14f + lookY * 3f))
    drawRoundRect(Color(0xFFF97316), Offset(cx - 8f + lookX * 8f, cy - 12f + lookY * 3f), Size(16f, 6f), CornerRadius(4f, 4f))
    drawCircle(led, 3f, Offset(cx - 4f + lookX * 6f, cy - 16f))
    drawCircle(led, 3f, Offset(cx + 4f + lookX * 6f, cy - 16f))
}

private fun DrawScope.drawCrawlerBot(
    cx: Float, cy: Float, w: Float, h: Float,
    lookX: Float, legPhase: Float, led: Color
) {
    val angles = listOf(-40f, 0f, 40f)
    angles.forEachIndexed { index, baseAngle ->
        val swing = if (index % 2 == 0) legPhase else -legPhase
        rotate(baseAngle + swing, pivot = Offset(cx - 14f, cy + 4f)) {
            drawLine(Color(0xFFA855F7), Offset(cx - 14f, cy + 4f), Offset(cx - 30f, cy + 18f), 4f, StrokeCap.Round)
        }
        rotate(-baseAngle - swing, pivot = Offset(cx + 14f, cy + 4f)) {
            drawLine(Color(0xFFA855F7), Offset(cx + 14f, cy + 4f), Offset(cx + 30f, cy + 18f), 4f, StrokeCap.Round)
        }
    }
    drawCircle(Color(0xFF1E1B4B), 17f, Offset(cx, cy))
    drawCircle(led, 4.5f, Offset(cx + lookX * 7f, cy - 2f))
    drawCircle(led, 2.5f, Offset(cx - 8f + lookX * 5f, cy))
    drawCircle(led, 2.5f, Offset(cx + 8f + lookX * 5f, cy))
}

private fun DrawScope.drawTitanBot(
    cx: Float, cy: Float, w: Float, h: Float,
    lookX: Float, lookY: Float, stride: Float, led: Color
) {
    drawLine(Color.White, Offset(cx, cy - 24f), Offset(cx + lookX * 4f, cy - 34f), 3f, StrokeCap.Round)
    drawCircle(led, 3.5f, Offset(cx + lookX * 4f, cy - 35f))
    drawRoundRect(Color(0xFF334155), Offset(cx - 25f, cy - 6f), Size(50f, 22f), CornerRadius(8f, 8f))
    drawCircle(led, 6f, Offset(cx, cy + 5f))
    drawRoundRect(Color(0xFF94A3B8), Offset(cx - 14f + lookX * 3f, cy - 24f), Size(28f, 16f), CornerRadius(4f, 4f))
    drawRect(led, Offset(cx - 9f + lookX * 6f, cy - 18f + lookY * 2f), Size(18f, 4.5f))
}

private fun DrawScope.drawNekoBot(
    cx: Float, cy: Float, w: Float, h: Float,
    lookX: Float, lookY: Float, led: Color, happy: Boolean
) {
    val leftEar = Path().apply {
        moveTo(cx - 18f, cy - 12f)
        lineTo(cx - 13f, cy - 30f)
        lineTo(cx - 3f, cy - 16f)
        close()
    }
    val rightEar = Path().apply {
        moveTo(cx + 18f, cy - 12f)
        lineTo(cx + 13f, cy - 30f)
        lineTo(cx + 3f, cy - 16f)
        close()
    }
    drawPath(leftEar, color = Color.White)
    drawPath(rightEar, color = Color.White)
    drawRoundRect(Color.White, Offset(cx - 19f, cy - 18f), Size(38f, 28f), CornerRadius(15f, 15f))
    drawRoundRect(Color(0xFF0F172A), Offset(cx - 14f, cy - 13f), Size(28f, 19f), CornerRadius(10f, 10f))
    val ex = lookX * 6f
    val ey = lookY * 3f
    drawCircle(led, if (happy) 4.5f else 3.5f, Offset(cx - 7f + ex, cy - 4f + ey))
    drawCircle(led, if (happy) 4.5f else 3.5f, Offset(cx + 7f + ex, cy - 4f + ey))
}

private fun DrawScope.drawDroneBot(
    cx: Float, cy: Float, w: Float, h: Float,
    lookX: Float, lookY: Float, spin: Float, led: Color
) {
    rotate(spin * 2f, pivot = Offset(cx, cy)) {
        drawOval(
            color = led.copy(alpha = 0.7f),
            topLeft = Offset(cx - 28f, cy - 8f),
            size = Size(56f, 16f),
            style = Stroke(width = 3f)
        )
    }
    drawCircle(Color(0xFF1E293B), 16f, Offset(cx, cy))
    drawCircle(Color.White, 16f, Offset(cx, cy), style = Stroke(1.8f))
    drawCircle(led, 6.5f, Offset(cx + lookX * 6f, cy + lookY * 4f))
    drawCircle(Color.White, 2.5f, Offset(cx + lookX * 6f - 1.5f, cy + lookY * 4f - 1.5f))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RobotPickerDialog(
    selectedRobot: RobotType,
    onSelect: (RobotType) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(Color(0xFF111622))
                .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(26.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Pilih Robot Pendamping",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            RobotType.entries.forEach { robot ->
                val isSelected = robot == selectedRobot
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSelected) robot.accent.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f))
                        .border(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) robot.accent else Color.White.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .combinedClickable(onClick = { onSelect(robot) })
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    InteractiveRobotCanvas(
                        robotType = robot,
                        accentColor = robot.accent,
                        lookX = 0.2f,
                        lookY = 0f,
                        stepTrigger = 1,
                        modifier = Modifier.size(44.dp)
                    )
                    Column {
                        Text(robot.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(robot.subtitle, color = Color.White.copy(0.6f), fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppContextDialog(
    app: PhoneAppInfo,
    isPinned: Boolean,
    isDocked: Boolean,
    onDismiss: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleDock: () -> Unit,
    onAppInfo: () -> Unit,
    onUninstall: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF141925))
                .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(24.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Image(
                    bitmap = app.icon,
                    contentDescription = app.label,
                    modifier = Modifier.size(46.dp).clip(RoundedCornerShape(12.dp))
                )
                Column {
                    Text(app.label, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(app.packageName, color = Color.White.copy(0.55f), fontSize = 11.sp, maxLines = 1)
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

            ContextMenuRow(
                icon = if (isPinned) Icons.Rounded.HeartBroken else Icons.Rounded.Favorite,
                text = if (isPinned) "Hapus dari Kartu Favorit (♥)" else "Jadikan Kartu Favorit (♥)",
                onClick = onTogglePin
            )
            ContextMenuRow(
                icon = Icons.Rounded.Dock,
                text = if (isDocked) "Hapus dari Dock Bawah" else "Pasang di Dock Bawah",
                onClick = onToggleDock
            )
            ContextMenuRow(
                icon = Icons.Rounded.Info,
                text = "Info Aplikasi & Penyimpanan",
                onClick = onAppInfo
            )
            ContextMenuRow(
                icon = Icons.Rounded.Delete,
                text = "Uninstall Aplikasi",
                isDestructive = true,
                onClick = onUninstall
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContextMenuRow(
    icon: ImageVector,
    text: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isDestructive) Color(0xFFF87171) else Color.White,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = text,
            color = if (isDestructive) Color(0xFFF87171) else Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// --- HELPER FUNCTIONS ---

suspend fun loadPhoneApps(context: Context): List<PhoneAppInfo> = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    val appMap = LinkedHashMap<String, PhoneAppInfo>()
    val intent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
    }
    val activities = pm.queryIntentActivities(intent, 0)
    for (resolve in activities) {
        val pkg = resolve.activityInfo.packageName
        if (pkg == context.packageName || appMap.containsKey(pkg)) continue
        val label = resolve.loadLabel(pm).toString()
        val iconBmp = drawableToBitmap(resolve.loadIcon(pm))
        val dominant = extractDominantColor(iconBmp)
        appMap[pkg] = PhoneAppInfo(
            label = label,
            packageName = pkg,
            icon = iconBmp.asImageBitmap(),
            dominantColor = dominant
        )
    }
    appMap.values.sortedBy { it.label.lowercase(Locale.getDefault()) }
}

fun drawableToBitmap(drawable: Drawable, width: Int = 144, height: Int = 144): Bitmap {
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
        return drawable.bitmap
    }
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}

fun extractDominantColor(bitmap: Bitmap): Color {
    return try {
        val palette = Palette.from(bitmap).generate()
        val colorInt = palette.getVibrantColor(palette.getDominantColor(0xFF2563EB.toInt()))
        Color(colorInt)
    } catch (e: Exception) {
        Color(0xFF2563EB)
    }
}

fun getBatteryLevel(context: Context): Int {
    return try {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    } catch (e: Exception) {
        100
    }
}

fun getNetworkLabel(context: Context): String {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork ?: return "Offline"
    val caps = cm.getNetworkCapabilities(network) ?: return "Offline"
    return when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Seluler"
        else -> "Online"
    }
}

fun launchPhoneApp(context: Context, packageName: String) {
    val intent = context.packageManager.getLaunchIntentForPackage(packageName)
    intent?.let {
        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(it)
    }
}

fun openAppDetails(context: Context, packageName: String) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

fun uninstallApp(context: Context, packageName: String) {
    val intent = Intent(Intent.ACTION_DELETE).apply {
        data = Uri.fromParts("package", packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
fun openDefaultLauncherSettings(context: Context) {
    try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as android.app.role.RoleManager
            if (roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME) &&
                !roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_HOME)
            ) {
                val intent = roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME)
                if (context is android.app.Activity) {
                    context.startActivityForResult(intent, 1001)
                    return
                }
            }
        }
        val homeSettings = Intent(Settings.ACTION_HOME_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(homeSettings)
    } catch (e: Exception) {
        val fallback = Intent(Settings.ACTION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(fallback)
    }
}