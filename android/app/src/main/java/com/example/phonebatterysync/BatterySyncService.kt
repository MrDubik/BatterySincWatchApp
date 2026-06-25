package com.example.phonebatterysync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.IBinder
import android.util.Log
import com.huawei.hms.wearengine.MessageClient
import com.huawei.hms.wearengine.WearEngine
import com.huawei.hms.wearengine.client.WearEngineClient
import com.huawei.hms.wearengine.common.WearEngineException
import com.huawei.hms.wearengine.message.SendMessageCallback
import com.huawei.hms.wearengine.message.MessageListener
import org.json.JSONObject

class BatterySyncService : Service() {

    companion object {
        const val CHANNEL_ID = "battery_sync_channel"
        const val NOTIFICATION_ID = 1
        const val TAG = "BatterySyncService"
        const val PATH_REQUEST = "/request_battery"
        const val PATH_RESPONSE = "/battery_response"
    }

    private lateinit var wearClient: WearEngineClient
    private var messageListenerRegistered = false

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Сервис создан")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        // Инициализация Wear Engine
        WearEngine.initialize(applicationContext) { result ->
            Log.d(TAG, "Инициализация Wear Engine: $result")
        }
        wearClient = WearEngine.getClient(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand, регистрируем слушатель сообщений")
        registerMessageListenerIfNeeded()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun registerMessageListenerIfNeeded() {
        if (messageListenerRegistered) {
            Log.d(TAG, "Слушатель уже зарегистрирован")
            return
        }
        val listener: MessageListener = { messageEvent ->
            val path = messageEvent.path
            if (path == PATH_REQUEST) {
                Log.d(TAG, "Получен запрос батареи с часов")
                respondWithBatteryInfo(messageEvent.sourceNodeId)
            }
        }
        wearClient.registerMessageListener(listener).addOnSuccessListener {
            messageListenerRegistered = true
            Log.d(TAG, "Слушатель сообщений зарегистрирован")
        }.addOnFailureListener { e ->
            Log.e(TAG, "Ошибка регистрации слушателя: $e")
        }
    }

    /**
     * Считывает текущее состояние батареи и отправляет ответ на часы.
     */
    private fun respondWithBatteryInfo(targetNodeId: String) {
        val batteryStatus = getBatteryInfo()
        val json = JSONObject().apply {
            put("level", batteryStatus.level)
            put("charging", batteryStatus.charging)
        }
        val data = json.toString().toByteArray(Charsets.UTF_8)

        wearClient.sendMessage(targetNodeId, PATH_RESPONSE, data, object : SendMessageCallback {
            override fun onSuccess(result: Void?) {
                Log.d(TAG, "Ответ отправлен успешно")
            }

            override fun onFailure(e: WearEngineException) {
                Log.e(TAG, "Ошибка отправки ответа: ${e.message}")
            }
        })
    }

    data class BatteryInfo(val level: Int, val charging: Boolean)

    private fun getBatteryInfo(): BatteryInfo {
        val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val levelPercent = (level * 100 / scale).coerceIn(0, 100)

        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        return BatteryInfo(levelPercent, charging)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Синхронизация заряда",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Канал для уведомления foreground-сервиса"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Синхронизация заряда с часами")
            .setContentText("Служба активна")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
