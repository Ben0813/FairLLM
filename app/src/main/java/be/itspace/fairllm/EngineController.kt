package be.itspace.fairllm

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import androidx.core.content.ContextCompat

class EngineController(private val context: Context, private val update: (String, String, Int) -> Unit) {
    private var bound = false
    private val receiver = Messenger(Handler(Looper.getMainLooper()) { message ->
        if (message.what == EngineService.STATE) {
            val state = message.data.getString("state", "offline")
            update(state, message.data.getString("detail", ""), message.data.getInt("model", -1))
            if (state == "error") stop()
        }
        true
    })
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            try {
                Messenger(binder).send(Message.obtain(null, EngineService.SUBSCRIBE).apply { replyTo = receiver })
            } catch (_: Exception) { update("error", "Connexion au moteur interrompue. Réessaie.", -1) }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            update("offline", "Moteur arrêté.", -1)
        }
        override fun onBindingDied(name: ComponentName?) { close(); update("offline", "Moteur arrêté.", -1) }
    }

    fun connect() {
        if (!bound) bound = context.bindService(Intent(context, EngineService::class.java), connection, Context.BIND_AUTO_CREATE)
    }

    fun start(modelIndex: Int) {
        ContextCompat.startForegroundService(context, Intent(context, EngineService::class.java)
            .setAction(EngineService.START).putExtra("model", modelIndex))
        connect()
    }

    fun stop() { close(); context.stopService(Intent(context, EngineService::class.java)) }
    fun close() { if (bound) { context.unbindService(connection); bound = false } }
}
