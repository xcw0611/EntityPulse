package com.entitypulse.app

import android.app.Application
import com.entitypulse.app.network.NodeManager
import com.entitypulse.app.sync.SyncWorker

class EntityPulseApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val nodeManager = NodeManager(this)
        // 按照用户配置的轮询周期（默认30分钟）启动 WorkManager 后台定时同步
        SyncWorker.schedulePeriodicSync(this, nodeManager.syncIntervalMinutes.toLong())
    }
}
