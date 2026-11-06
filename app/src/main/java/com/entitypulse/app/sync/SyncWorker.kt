package com.entitypulse.app.sync

import android.content.Context
import androidx.work.*
import com.entitypulse.app.data.AppDatabase
import com.entitypulse.app.network.NodeManager
import com.entitypulse.app.notification.NotificationHelper
import com.entitypulse.app.repository.FeedRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val database = AppDatabase.getInstance(applicationContext)
        val nodeManager = NodeManager(applicationContext)
        val repository = FeedRepository(database, nodeManager)
        val notificationHelper = NotificationHelper(applicationContext)

        try {
            val syncResults = repository.syncAllChannels()
            for ((channel, newItems) in syncResults) {
                val entity = database.entityDao().getById(channel.entityId) ?: continue
                if (!entity.isNotifyEnabled) continue

                // 为每条新动态发送通知（若同一批次过多，则只通知前3条以防打扰刷屏）
                for (item in newItems.take(3)) {
                    notificationHelper.showFeedNotification(entity, channel, item)
                }
            }
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "periodic_feed_sync_work"

        fun schedulePeriodicSync(context: Context, intervalMinutes: Long = 30) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            // WorkManager 最小周期限制为 15 分钟
            val actualInterval = intervalMinutes.coerceAtLeast(15)

            val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(actualInterval, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                syncRequest
            )
        }

        fun runOnceImmediately(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val singleRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueue(singleRequest)
        }
    }
}
