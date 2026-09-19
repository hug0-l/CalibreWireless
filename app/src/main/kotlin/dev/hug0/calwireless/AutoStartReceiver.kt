package dev.hug0.calwireless

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 開機 / 網路變化時：若勾了自動啟動且區域網路找得到 calibre，就拉起服務。 */
class AutoStartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Thread {
            try {
                val s = readSettings(context)
                if (s.autoStart && s.ready() && !DeviceState.running.value) {
                    if (Discover.hello(1200) != null) {
                        try {
                            context.startForegroundService(serviceIntent(context, s))
                            DeviceState.log(R.string.ev_autostart)
                        } catch (e: Exception) {
                            DeviceState.logRaw("auto-start 被系統擋下: ${e.message}", LogKind.WARN)
                        }
                    }
                }
            } catch (e: Exception) {
            } finally {
                pending.finish()
            }
        }.start()
    }
}
