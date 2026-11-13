package com.axcel.launcher

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

class LockScreenActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            km.requestDismissKeyguard(this, null)
        }
        setContent {
            BackHandler(enabled = true) {}
            BabyAxcelLockScreen(onUnlock = { finish() })
        }
    }
}

@Composable
fun BabyAxcelLockScreen(onUnlock: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("axcel_phone_prefs", Context.MODE_PRIVATE) }
    val selectedRobot = remember {
        RobotType.entries.find { it.name == prefs.getString("selected_robot", "BABY_PHOTO") }
            ?: RobotType.BABY_PHOTO
    }
    val customPhoto = remember { loadSavedCustomAvatar(context) }

    var currentTime by remember { mutableStateOf("") }
    var currentDate by remember { mutableStateOf("") }
    var batteryPct by remember { mutableIntStateOf(100) }
    var hopTrigger by remember { mutableIntStateOf(0) }
    var torchOn by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFmt = SimpleDateFormat("EEEE, d MMMM yyyy", Locale("id", "ID"))
        while (true) {
            val now = Date()
            currentTime = timeFmt.format(now)
            currentDate = dateFmt.format(now)
            batteryPct = getBatteryLevel(context)
            delay(3000L)
        }
    }

    val notifications = NotificationStore.activeNotifications

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF134E4A),
                        Color(0xFF0A1F24),
                        Color(0xFF04090C)
                    )
                )
            )
    ) {
        BabyAxcelAmbientBackground(
            accentMint = Color(0xFF6EE7B7),
            warmStar = Color(0xFFFDE68A),
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = Color(0xFF6EE7B7),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "AXCEL LOCK • $batteryPct%",
                        color = Color(0xFFA7F3D0),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                }
                Text(
                    text = currentTime,
                    color = Color.White,
                    fontSize = 66.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-2).sp
                )
                Text(
                    text = currentDate,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0xFF0E292B).copy(alpha = 0.55f))
                    .border(1.5.dp, Color(0xFF6EE7B7).copy(alpha = 0.45f), RoundedCornerShape(28.dp))
                    .clickable { hopTrigger += 1 }
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                InteractiveRobotCanvas(
                    robotType = selectedRobot,
                    customPhoto = customPhoto,
                    accentColor = Color(0xFF6EE7B7),
                    lookX = 0f,
                    lookY = 0f,
                    stepTrigger = hopTrigger,
                    modifier = Modifier.size(106.dp)
                )
                Text(
                    text = "Tap Baby Axcel • Geser Bawah untuk Buka",
                    color = Color(0xFFA7F3D0),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 10.dp)
            ) {
                if (notifications.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Tidak ada notifikasi baru",
                            color = Color.White.copy(alpha = 0.45f),
                            fontSize = 13.sp
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(notifications.take(4), key = { it.key }) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(Color(0xFF0D2426).copy(alpha = 0.82f))
                                    .border(1.dp, Color(0xFF6EE7B7).copy(alpha = 0.32f), RoundedCornerShape(18.dp))
                                    .clickable {
                                        try {
                                            item.contentIntent?.send()
                                        } catch (_: Exception) {
                                        }
                                        onUnlock()
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                UnifiedAppBadgeRaw(
                                    icon = item.appIcon,
                                    dominantColor = item.dominantColor,
                                    size = 42.dp
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = item.appName,
                                            color = Color(0xFF6EE7B7),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = item.timeText,
                                            color = Color.White.copy(alpha = 0.5f),
                                            fontSize = 10.sp
                                        )
                                    }
                                    Text(
                                        text = item.title,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (item.text.isNotEmpty()) {
                                        Text(
                                            text = item.text,
                                            color = Color.White.copy(alpha = 0.78f),
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(if (torchOn) Color(0xFFFDE68A) else Color(0xFF0E292B).copy(alpha = 0.8f))
                        .border(1.dp, Color(0xFF6EE7B7).copy(alpha = 0.4f), CircleShape)
                        .clickable {
                            torchOn = !torchOn
                            toggleFlashlight(context, torchOn)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Star,
                        contentDescription = "Senter",
                        tint = if (torchOn) Color.Black else Color.White
                    )
                }

                SlideToUnlockPill(
                    modifier = Modifier.weight(1f),
                    onUnlocked = onUnlock
                )

                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0E292B).copy(alpha = 0.8f))
                        .border(1.dp, Color(0xFF6EE7B7).copy(alpha = 0.4f), CircleShape)
                        .clickable {
                            try {
                                val camIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(camIntent)
                                onUnlock()
                            } catch (_: Exception) {
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PhotoCamera,
                        contentDescription = "Kamera",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun SlideToUnlockPill(
    modifier: Modifier = Modifier,
    onUnlocked: () -> Unit
) {
    val maxDragPx = with(LocalDensity.current) { 150.dp.toPx() }
    var offsetX by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .height(54.dp)
            .clip(CircleShape)
            .background(Color(0xFF0E292B).copy(alpha = 0.85f))
            .border(1.5.dp, Color(0xFF6EE7B7).copy(alpha = 0.55f), CircleShape)
            .padding(4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = "Geser Buka Kunci  >>>",
            color = Color(0xFFA7F3D0).copy(alpha = 0.85f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Center)
        )

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .size(46.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(listOf(Color(0xFF6EE7B7), Color(0xFFFDE68A)))
                )
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        offsetX = (offsetX + delta).coerceIn(0f, maxDragPx)
                    },
                    onDragStopped = {
                        if (offsetX > maxDragPx * 0.72f) {
                            onUnlocked()
                        } else {
                            offsetX = 0f
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = "Unlock",
                tint = Color(0xFF022C22),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

fun toggleFlashlight(context: Context, enable: Boolean) {
    try {
        val camManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = camManager.cameraIdList.firstOrNull() ?: return
        camManager.setTorchMode(cameraId, enable)
    } catch (_: Exception) {
    }
}