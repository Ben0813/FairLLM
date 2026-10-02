package be.itspace.fairllm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class EngineService : Service() {
    companion object {
        const val START = "be.itspace.fairllm.START_ENGINE"
        const val SUBSCRIBE = 1
        const val STATE = 2
        private const val CHANNEL = "local-engine"
    }
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val started = AtomicBoolean(false)
    private val stopping = AtomicBoolean(false)
    private var subscriber: Messenger? = null
    private var state = "offline"
    private var detail = "Moteur arrêté."
    private var modelIndex = -1
    private lateinit var store: ModelStore
    private val api = LlamaApi()
    private val messenger = Messenger(Handler(Looper.getMainLooper()) { msg ->
        if (msg.what == SUBSCRIBE) { subscriber = msg.replyTo; publish() }
        true
    })

    override fun onCreate() {
        super.onCreate()
        store = ModelStore(this)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Moteur local", NotificationManager.IMPORTANCE_LOW))
    }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // A foreground-service start must promote immediately, even on repeated taps.
        foreground(detail, downloading = state != "ready")
        if (intent?.action != START || !started.compareAndSet(false, true)) return START_NOT_STICKY
        modelIndex = intent.getIntExtra("model", -1)
        val models = try { ModelCatalog(this).models() } catch (e: Exception) {
            update("error", "Impossible de lire les modèles : ${e.message}")
            return START_NOT_STICKY
        }
        if (modelIndex !in models.indices) {
            update("error", "Modèle inconnu.")
            return START_NOT_STICKY
        }
        update("loading", "Préparation du modèle…")
        worker.execute {
            try {
                val model = models[modelIndex]
                val file = store.prepare(model) { update("loading", it) }
                if (stopping.get()) return@execute
                update("loading", "Chargement du moteur local…")
                main.post { foreground("Chargement du moteur local…", downloading = false) }
                val running = AtomicBoolean(true)
                val watcher = Thread {
                    while (running.get() && !stopping.get()) {
                        val health = runBlocking { api.health(LOCAL_SERVER_URL) }
                        if (health == ServerStatus.Ready) {
                            update("ready", "Moteur prêt • ${model.title} • CPU")
                            break
                        }
                        try { Thread.sleep(500) } catch (_: InterruptedException) { break }
                    }
                }
                watcher.start()
                try {
                    val code = NativeEngine.run(arrayOf(
                        "-m", file.absolutePath, "-ngl", "0", "-c", model.contextSize.toString(),
                        "--parallel", "1", "--host", "127.0.0.1", "--port", "18080",
                        "--alias", "local",
                        "--api-key", engineApiKey(this), "--no-webui", "--jinja"
                    ), File(filesDir, "engine.log").absolutePath)
                    if (!stopping.get()) throw IllegalStateException("Le moteur s'est arrêté (code $code).")
                } finally { running.set(false); watcher.interrupt() }
            } catch (e: Throwable) {
                if (!stopping.get()) {
                    val log = File(filesDir, "engine.log").takeIf { it.exists() }?.let {
                        java.io.RandomAccessFile(it, "r").use { input ->
                            val length = minOf(input.length(), 4000L).toInt()
                            input.seek(input.length() - length)
                            ByteArray(length).also(input::readFully).toString(Charsets.UTF_8)
                        }
                    }.orEmpty()
                    update("error", (e.message ?: "Impossible de démarrer le moteur.") + if (log.isBlank()) "" else "\n\n$log")
                    main.post { stopForeground(STOP_FOREGROUND_REMOVE) }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun foreground(text: String, downloading: Boolean) {
        val type = if (Build.VERSION.SDK_INT >= 34) {
            if (downloading) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, 1, notification(text), type)
    }

    private fun notification(text: String): android.app.Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("FairLLM").setContentText(text).setContentIntent(open).setOngoing(true).build()
    }

    private fun update(next: String, message: String) {
        main.post {
            if (stopping.get()) return@post
            state = next; detail = message; publish()
            if (next == "ready") foreground(message, downloading = false)
            else if (next == "loading") getSystemService(NotificationManager::class.java).notify(1,
                notification(message))
        }
    }

    private fun publish() {
        try { subscriber?.send(Message.obtain(null, STATE).apply {
            data = Bundle().apply { putString("state", state); putString("detail", detail); putInt("model", modelIndex) }
        }) } catch (_: Exception) { subscriber = null }
    }

    override fun onDestroy() {
        stopping.set(true)
        store.cancel()
        worker.shutdownNow()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
        // Only the private :engine process exits; the chat activity stays alive.
        Process.killProcess(Process.myPid())
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        store.cancel()
        update("error", "Android a interrompu le téléchargement. Appuie sur Démarrer pour le reprendre.")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
}
