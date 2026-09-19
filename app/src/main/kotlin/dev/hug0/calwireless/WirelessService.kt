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
import android.os.PowerManager
import android.content.Context.WIFI_SERVICE
import android.net.wifi.WifiManager
import dev.hug0.calwireless.saf.SafInboxStore
import java.net.Socket

class WirelessService : Service() {

    @Volatile private var stopped = false
    private var worker: Thread? = null
    @Volatile private var currentSocket: Socket? = null
    @Volatile private var activeSession: Session? = null
    private var wakeLock: PowerManager.WakeLock? = null
    @Suppress("DEPRECATION") private var wifiLock: WifiManager.WifiLock? = null

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
        acquireLocks()
        DeviceState.deleteFun = { lp -> activeSession?.takeIf { it.alive }?.deleteBook(lp) ?: false }
        DeviceState.markFun = { lp, r ->
            activeSession?.takeIf { it.alive }?.let { it.markRead(lp, r) }?.also { pushBooks() } ?: false
        }
        DeviceState.resyncFun = { try { currentSocket?.close() } catch (e: Exception) {} }

        val auto = intent?.getBooleanExtra(EXTRA_AUTO, true) ?: true
        val host = intent?.getStringExtra(EXTRA_HOST).orEmpty()
        val port = intent?.getIntExtra(EXTRA_PORT, 0) ?: 0
        val password = intent?.getStringExtra(EXTRA_PASSWORD).orEmpty().ifEmpty { null }
        val deviceName = intent?.getStringExtra(EXTRA_NAME).orEmpty().ifEmpty { "CalibreWireless" }
        val readCol = intent?.getStringExtra(EXTRA_READ_COL).orEmpty().ifEmpty { null }
        val dateCol = intent?.getStringExtra(EXTRA_DATE_COL).orEmpty().ifEmpty { null }
        val formats = DeviceConfig.parseFormats(intent?.getStringExtra(EXTRA_FORMATS))
        val packet = (intent?.getIntExtra(EXTRA_PACKET, 65536) ?: 65536).coerceIn(1024, 1 shl 20)
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
            extensions = formats,
            maxPacketLen = packet,
            readSyncCol = readCol,
            readDateSyncCol = dateCol,
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
                onSession = { activeSession = it },
                coverSink = FileCoverSink(applicationContext),
            ).runLoop { stopped }
        }.also { it.isDaemon = true; it.start() }
        return START_STICKY
    }

    @Suppress("DEPRECATION")
    private fun acquireLocks() {
        if (wakeLock == null) {
            wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "calwireless:conn")
                .apply { setReferenceCounted(false); acquire() }
        }
        if (wifiLock == null) {
            val mode = if (android.os.Build.VERSION.SDK_INT >= 29)
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY else WifiManager.WIFI_MODE_FULL
            wifiLock = (applicationContext.getSystemService(WIFI_SERVICE) as WifiManager)
                .createWifiLock(mode, "calwireless:wifi")
                .apply { setReferenceCounted(false); acquire() }
        }
    }

    private fun releaseLocks() {
        try { wakeLock?.takeIf { it.isHeld }?.release() } catch (e: Exception) {}
        wakeLock = null
        try { wifiLock?.takeIf { it.isHeld }?.release() } catch (e: Exception) {}
        wifiLock = null
    }

    private fun pushBooks() {
        activeSession?.takeIf { it.alive }?.let { DeviceState.books.value = it.snapshot() }
    }

    private fun onEvent(e: WirelessEvent) {
        when (e) {
            is WirelessEvent.Connecting -> { DeviceState.status.value = Status.CONNECTING; DeviceState.log(R.string.st_connecting) }
            is WirelessEvent.Connected -> {
                DeviceState.status.value = Status.CONNECTED
                DeviceState.library.value = e.libraryName
                e.deviceUuid?.let { DeviceState.deviceUuid.value = it }
                DeviceState.log(R.string.ev_connected, e.libraryName ?: "—", LogKind.OK)
                pushBooks()
            }
            is WirelessEvent.BookReceived -> {
                DeviceState.log(R.string.ev_received, e.lpath, LogKind.OK)
                transferHeadsUp(statusText(this, DeviceState.status.value) + " · " + getString(R.string.ev_received, e.lpath))
                pushBooks()
            }
            is WirelessEvent.BookServed -> DeviceState.log(R.string.ev_served, e.lpath, LogKind.OK)
            is WirelessEvent.BookDeleted -> { DeviceState.log(R.string.ev_deleted, e.lpath, LogKind.OK); pushBooks() }
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
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.chan_name), NotificationManager.IMPORTANCE_LOW),
        )
        nm.createNotificationChannel(
            NotificationChannel(TRANSFER_CHANNEL, getString(R.string.chan_transfer), NotificationManager.IMPORTANCE_HIGH),
        )
    }

    private fun transferHeadsUp(text: String) {
        val n = Notification.Builder(this, TRANSFER_CHANNEL)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setAutoCancel(true)
            .build()
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIF_TRANSFER, n)
    }

    override fun onDestroy() {
        releaseLocks()
        DeviceState.deleteFun = null
        DeviceState.markFun = null
        DeviceState.resyncFun = null
        DeviceState.books.value = emptyList()
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
        private const val TRANSFER_CHANNEL = "calibre-transfer"
        private const val NOTIF_ID = 1
        private const val NOTIF_TRANSFER = 2
        const val EXTRA_FORMATS = "formats"
        const val EXTRA_PACKET = "packet"
        const val EXTRA_READ_COL = "read_col"
        const val EXTRA_DATE_COL = "date_col"
    }
}
