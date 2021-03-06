package io.github.jan.supacompose.realtime

import io.github.aakira.napier.Napier
import io.github.jan.supacompose.SupabaseClient
import io.github.jan.supacompose.SupabaseClientBuilder
import io.github.jan.supacompose.auth.auth
import io.github.jan.supacompose.plugins.SupacomposePlugin
import io.github.jan.supacompose.supabaseJson
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.sendSerialized
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.buildJsonObject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

sealed interface Realtime {

    val status: StateFlow<Status>
    val subscriptions: Map<String, RealtimeChannel>

    suspend fun connect()
    fun disconnect()

    fun onStatusChange(callback: (Status) -> Unit)

    class Config(
        var websocketConfig: WebSockets.Config.() -> Unit = {},
        var secure: Boolean = true,
        var heartbeatInterval: Duration = 30000.milliseconds
    )

    companion object : SupacomposePlugin<Config, Realtime> {

        override val key = "realtime"

        override fun createConfig(init: Config.() -> Unit) = Config().apply(init)
        override fun setup(builder: SupabaseClientBuilder, config: Config) {
            builder.httpConfig {
                install(WebSockets) {
                    contentConverter = KotlinxWebsocketSerializationConverter(supabaseJson)
                    config.websocketConfig(this)
                }
            }
        }

        override fun create(supabaseClient: SupabaseClient, config: Config): Realtime = RealtimeImpl(supabaseClient, config)

    }

    enum class Status {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
    }

}

internal class RealtimeImpl(val supabaseClient: SupabaseClient, private val realtimeConfig: Realtime.Config) :
    Realtime {

    lateinit var ws: DefaultClientWebSocketSession
    private val _status = MutableStateFlow(Realtime.Status.DISCONNECTED)
    override val status: StateFlow<Realtime.Status> = _status.asStateFlow()
    private val _subscriptions = mutableMapOf<String, RealtimeChannel>()
    override val subscriptions: Map<String, RealtimeChannel>
        get() = _subscriptions.toMap()
    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private val statusListeners = mutableListOf<(Realtime.Status) -> Unit>()
    private var ref = 0

    override fun onStatusChange(callback: (Realtime.Status) -> Unit) {
        statusListeners.add(callback)
    }

    override suspend fun connect() {
        supabaseClient.auth.onSessionChange { new, old ->
            if (status.value == Realtime.Status.CONNECTED) {
                if (new == null) {
                    disconnect()
                } else {
                    updateJwt(new.accessToken)
                }
            }
        }
        if (status.value == Realtime.Status.CONNECTED) throw IllegalStateException("Websocket already connected")
        val prefix = if (realtimeConfig.secure) "wss://" else "ws://"
        statusListeners.forEach { it(Realtime.Status.CONNECTING) }
        _status.value = Realtime.Status.CONNECTING
        ws =
            supabaseClient.httpClient.webSocketSession(prefix + supabaseClient.supabaseUrl + ("/realtime/v1/websocket?apikey=${supabaseClient.supabaseKey}"))
        _status.value = Realtime.Status.CONNECTED
        statusListeners.forEach { it(Realtime.Status.CONNECTED) }
        Napier.i { "Connected to realtime websocket!" }
        scope.launch {
            for (frame in ws.incoming) {
                val message = frame as? Frame.Text ?: continue
                onMessage(message.readText())
            }
            _status.value = Realtime.Status.DISCONNECTED
            Napier.i { "Disconnected from realtime websocket!" }
        }
        scope.launch {
            while (isActive) {
                delay(realtimeConfig.heartbeatInterval)
                sendHeartbeat()
            }
        }
    }

    override fun disconnect() {
        ws.cancel()
        scope.cancel()
        statusListeners.forEach { it(Realtime.Status.DISCONNECTED) }
        _status.value = Realtime.Status.DISCONNECTED
    }

    private fun onMessage(stringMessage: String) {
        val message = supabaseJson.decodeFromString<RealtimeMessage>(stringMessage)
        println(message)
        val channel = subscriptions[message.topic] as? RealtimeChannelImpl
        channel?.onMessage(message)
    }

    private fun updateJwt(jwt: String) {

    }

    private suspend fun sendHeartbeat() {
        if (ref != 0) {
            ref = 0
            Napier.w { "Heartbeat timeout. Trying to reconnect" }
            disconnect()
            scope.launch {
                connect()
            }
            return
        }
        Napier.d { "Sending heartbeat" }
        ws.sendSerialized(RealtimeMessage("phoenix", "heartbeat", buildJsonObject { }, (++ref).toString()))
    }

}

inline fun Realtime.createChannel(builder: RealtimeChannelBuilder.() -> Unit): RealtimeChannel {
    return RealtimeChannelBuilder(this as RealtimeImpl).apply(builder).build()
}

val SupabaseClient.realtime: Realtime
    get() = plugins.getOrElse("realtime") {
        throw IllegalStateException("Realtime plugin not installed")
    } as? Realtime ?: throw IllegalStateException("Realtime plugin not installed")