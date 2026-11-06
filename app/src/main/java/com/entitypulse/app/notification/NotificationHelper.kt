package com.entitypulse.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.entitypulse.app.MainActivity
import com.entitypulse.app.R
import com.entitypulse.app.data.model.EntityRecord
import com.entitypulse.app.data.model.FeedItemRecord
import com.entitypulse.app.data.model.SourceChannelRecord
import com.entitypulse.app.network.NodeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

class NotificationHelper(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val nodeManager = NodeManager(context)

    companion object {
        const val CHANNEL_ID_VIP = "channel_entity_vip"
        const val CHANNEL_ID_NORMAL = "channel_entity_normal"
        const val EXTRA_POST_ID = "extra_post_id"
        const val EXTRA_ENTITY_ID = "extra_entity_id"
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val vipChannel = NotificationChannel(
                CHANNEL_ID_VIP,
                context.getString(R.string.notification_channel_vip),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_vip_desc)
                enableVibration(true)
                enableLights(true)
            }

            val normalChannel = NotificationChannel(
                CHANNEL_ID_NORMAL,
                context.getString(R.string.notification_channel_normal),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notification_channel_normal_desc)
            }

            notificationManager.createNotificationChannels(listOf(vipChannel, normalChannel))
        }
    }

    fun isDndActive(): Boolean {
        if (!nodeManager.isDndEnabled) return false
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val start = nodeManager.dndStartHour
        val end = nodeManager.dndEndHour
        return if (start > end) {
            // 跨午夜，如 23:00 至 08:00
            currentHour >= start || currentHour < end
        } else {
            currentHour in start until end
        }
    }

    suspend fun showFeedNotification(
        entity: EntityRecord,
        channel: SourceChannelRecord,
        item: FeedItemRecord
    ) = withContext(Dispatchers.IO) {
        if (!entity.isNotifyEnabled) return@withContext
        if (isDndActive()) return@withContext

        val channelId = if (entity.isVip) CHANNEL_ID_VIP else CHANNEL_ID_NORMAL
        val notificationId = (item.guid.hashCode() and 0x7FFFFFFF)

        // 1. 点击通知主体：打开应用并定位至动态详情
        val appIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_POST_ID, item.id)
            putExtra(EXTRA_ENTITY_ID, entity.id)
        }
        val appPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 2. 快捷按钮：在外部官方应用或浏览器中打开原网页
        val openExternalIntent = Intent(Intent.ACTION_VIEW, Uri.parse(item.originalUrl)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val openExternalPendingIntent = PendingIntent.getActivity(
            context,
            notificationId + 1,
            openExternalIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "【${entity.name} · ${channel.platformType.displayName}】"
        val snippet = item.contentPlainText.take(120).ifBlank { item.title }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(item.title)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(title)
                    .bigText(if (item.title != snippet) "${item.title}\n\n$snippet" else snippet)
            )
            .setContentIntent(appPendingIntent)
            .setAutoCancel(true)
            .setPriority(if (entity.isVip) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .addAction(0, "在应用中查看", appPendingIntent)

        if (item.originalUrl.isNotBlank()) {
            builder.addAction(0, "打开原平台", openExternalPendingIntent)
        }

        // 异步尝试下载封面大图
        if (!item.thumbnailUrl.isNullOrBlank()) {
            val bitmap = loadBitmap(context, item.thumbnailUrl)
            if (bitmap != null) {
                builder.setLargeIcon(bitmap)
                builder.setStyle(
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(bitmap)
                        .setBigContentTitle(title)
                        .setSummaryText(snippet)
                )
            }
        }

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (_: SecurityException) {
            // Android 13+ 若未授予 POST_NOTIFICATIONS 权限则静默忽略
        }
    }

    private suspend fun loadBitmap(context: Context, url: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val loader = ImageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .build()
            val result = loader.execute(request)
            if (result is SuccessResult) {
                (result.drawable as? BitmapDrawable)?.bitmap
            } else null
        } catch (_: Exception) {
            null
        }
    }
}
