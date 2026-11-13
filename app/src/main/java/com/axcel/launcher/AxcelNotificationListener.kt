package com.axcel.launcher

import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.*

data class UnifiedNotifItem(
    val key: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val timeText: String,
    val appIcon: ImageBitmap,
    val dominantColor: Color,
    val contentIntent: PendingIntent?,
    val isClearable: Boolean
)

object NotificationStore {
    val activeNotifications = mutableStateListOf<UnifiedNotifItem>()
    var listenerInstance: AxcelNotificationListener? = null

    fun isPermissionGranted(context: Context): Boolean {
        val cn = ComponentName(context, AxcelNotificationListener::class.java)
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(cn.flattenToString())
    }

    fun dismissNotification(key: String) {
        try {
            listenerInstance?.cancelNotification(key)
        } catch (_: Exception) {
        }
        activeNotifications.removeAll { it.key == key }
    }

    fun clearAllClearable() {
        try {
            listenerInstance?.cancelAllNotifications()
        } catch (_: Exception) {
        }
        activeNotifications.removeAll { it.isClearable }
    }
}

class AxcelNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        NotificationStore.listenerInstance = this
        refreshAllNotifications()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        NotificationStore.listenerInstance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        refreshAllNotifications()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        refreshAllNotifications()
    }

    fun refreshAllNotifications() {
        try {
            val sbns = activeNotifications ?: return
            val pm = packageManager
            val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
            val mapped = ArrayList<UnifiedNotifItem>()

            for (sbn in sbns) {
                if (sbn.packageName == packageName) continue
                val extras = sbn.notification.extras
                val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
                val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
                if (title.isEmpty() && text.isEmpty()) continue

                val appInfo = try {
                    pm.getApplicationInfo(sbn.packageName, 0)
                } catch (_: Exception) {
                    null
                }
                val appLabel = appInfo?.let { pm.getApplicationLabel(it).toString() } ?: sbn.packageName
                val rawDrawable = appInfo?.let { pm.getApplicationIcon(it) }
                    ?: ContextCompat.getDrawable(this, android.R.drawable.sym_def_app_icon)
                if (rawDrawable != null) {
                    val bmp = drawableToBitmap(rawDrawable)
                    val dominant = extractDominantColor(bmp)
                    mapped.add(
                        UnifiedNotifItem(
                            key = sbn.key,
                            packageName = sbn.packageName,
                            appName = appLabel,
                            title = title.ifEmpty { appLabel },
                            text = text,
                            timeText = timeFmt.format(Date(sbn.postTime)),
                            appIcon = bmp.asImageBitmap(),
                            dominantColor = dominant,
                            contentIntent = sbn.notification.contentIntent,
                            isClearable = sbn.isClearable
                        )
                    )
                }
            }

            NotificationStore.activeNotifications.clear()
            NotificationStore.activeNotifications.addAll(mapped.distinctBy { it.key })
        } catch (_: Exception) {
        }
    }
}