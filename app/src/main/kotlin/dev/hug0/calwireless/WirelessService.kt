package dev.hug0.calwireless

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import dev.hug0.calwireless.saf.SafInboxStore
import java.net.Socket

class WirelessService : Service() {

    @Volatile private var stopped = false
    private var worker: Thread? = null
    @Volatile private var currentSocket: Socket? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        createChannel()
        startForeground(NOTIF_ID, buildNotification())
        stopped = false
        DeviceState.running.value = true

        val auto = intent?.getBooleanExtra(EXTRA_AUTO, true) ?: true
        val host = intent?.getStringExtra(EXTRA_HOST).orEmpty()
        val port = intent?.getIntExtra(EXTRA_PORT, 0) ?: 0
        val password = intent?.getStringExtra(EXTRA_PASSWORD).orEmpty().ifEmpty { null }
        val deviceName = intent?.getStringExtra(EXTRA_NAME).orEmpty().ifEmpty { "CalibreWireless" }
        val treeUriStr = intent?.getStringExtra(EXTRA_TREE)
        val tree = treeUriStr?.let { Uri.parse(it) }

        if (tree == null) {
            DeviceState.status.value = Status.IDLE
            DeviceState.log(R.string.log_no_folder, kind = LogKind.ERR)
            stopSelf()
            return START_NOT_STICKY
        }

        val config = DeviceConfig(
            deviceKind = android.os.Build.MODEL,
            deviceName = "$deviceName (${android.os.Build.MODEL})",
        )
        worker = Thread {
            val address: () -> Pair<String, Int>? = when {
                auto -> { { Discover.hello()?.let { it.address to it.tcpPort } } }
                else -> { { host to port } }
            }
            WirelessDevice(
                store = SafInboxStore(applicationContext, tree),
                config = config,
                password = password,
                addressProvider = address,
                emit = ::onEvent,
                socketFactory = { h, p ->
                    Socket().also { s ->
                        currentSocket = s
                        s.connect(java.net.InetSocketAddress(h, p), 5000)
                        s.tcpNoDelay = true
                    }
                },
            ).runLoop { stopped }
        }.also { it.isDaemon = true; it.start() }
        return START_STICKY
    }

    private fun onEvent(e: WirelessEvent) {
        when (e) {
            is WirelessEvent.Connecting -> { DeviceState.status.value = Status.CONNECTING; DeviceState.log(R.string.st_connecting) }
            is WirelessEvent.Connected -> {
                DeviceState.status.value = Status.CONNECTED
                DeviceState.library.value = e.libraryName
                e.deviceUuid?.let { DeviceState.deviceUuid.value = it }
                DeviceState.log(R.string.ev_connected, e.libraryName ?: "—", LogKind.OK)
            }
            is WirelessEvent.BookReceived -> DeviceState.log(R.string.ev_received, e.lpath, LogKind.OK)
            is WirelessEvent.BookServed -> DeviceState.log(R.string.ev_served, e.lpath, LogKind.OK)
            is WirelessEvent.BookDeleted -> DeviceState.log(R.string.ev_deleted, e.lpath, LogKind.OK)
            is WirelessEvent.PasswordRejected -> { DeviceState.status.value = Status.PASSWORD; DeviceState.log(R.string.ev_password, kind = LogKind.ERR) }
            is WirelessEvent.Busy -> { DeviceState.status.value = Status.BUSY; DeviceState.log(R.string.ev_busy, e.otherDevice, LogKind.WARN) }
            is WirelessEvent.Ejected -> { DeviceState.status.value = Status.EJECTED; DeviceState.log(R.string.ev_ejected, kind = LogKind.WARN) }
            is WirelessEvent.Disconnected -> { DeviceState.status.value = Status.RETRY; DeviceState.log(R.string.ev_disconnected, e.cause, LogKind.WARN) }
            is WirelessEvent.Log -> DeviceState.logRaw(e.message)
        }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIF_ID, buildNotification())
    }

    private fun buildNotification(): Notification = Notification.Builder(this, CHANNEL)
        .setContentTitle(getString(R.string.app_name))
        .setContentText(statusText(this, DeviceState.status.value))
        .setSmallIcon(android.R.drawable.ic_popup_sync)
        .setContentIntent(
            PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        )
        .setOngoing(true)
        .build()

    private fun createChannel() {
        NotificationChannel(CHANNEL, getString(R.string.chan_name), NotificationManager.IMPORTANCE_LOW).let {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(it)
        }
    }

    override fun onDestroy() {
        stopped = true
        try { currentSocket?.close() } catch (e: Exception) {}
        worker?.interrupt()
        DeviceState.running.value = false
        DeviceState.status.value = Status.IDLE
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "dev.hug0.calwireless.STOP"
        const val EXTRA_AUTO = "auto"
        const val EXTRA_HOST = "host"
        const val EXTRA_PORT = "port"
        const val EXTRA_PASSWORD = "password"
        const val EXTRA_NAME = "name"
        const val EXTRA_TREE = "tree"
        private const val CHANNEL = "calibre-wireless"
        private const val NOTIF_ID = 1
    }
}
