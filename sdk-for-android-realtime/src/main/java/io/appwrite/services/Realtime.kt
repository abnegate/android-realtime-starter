package io.appwrite.services

import io.appwrite.Client
import io.appwrite.exceptions.AppwriteException
import io.appwrite.extensions.forEachAsync
import io.appwrite.extensions.fromJson
import io.appwrite.extensions.jsonCast
import io.appwrite.models.*
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.internal.concurrent.TaskRunner
import okhttp3.internal.ws.RealWebSocket
import java.util.*
import kotlin.coroutines.CoroutineContext

class Realtime(client: Client) : Service(client), CoroutineScope {

    private val job = Job()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    private companion object {
        private const val TYPE_ERROR = "error"
        private const val TYPE_EVENT = "event"

        private const val DEBOUNCE_MILLIS = 1L

        private var socket: RealWebSocket? = null
        private var channelCallbacks = mutableMapOf<String, MutableCollection<RealtimeCallback>>()
        private var errorCallbacks = mutableSetOf<(AppwriteException) -> Unit>()

        private var subCallDepth = 0
    }

    private fun createSocket() {
        val queryParamBuilder = StringBuilder()
            .append("project=${client.config["project"]}")

        channelCallbacks.keys.forEach {
            queryParamBuilder
                .append("&channels[]=$it")
        }

        val request = Request.Builder()
            .url("${client.endPointRealtime}/realtime?$queryParamBuilder")
            .build()

        if (socket != null) {
            closeSocket()
        }

        socket = RealWebSocket(
            taskRunner = TaskRunner.INSTANCE,
            originalRequest = request,
            listener = AppwriteWebSocketListener(),
            random = Random(),
            pingIntervalMillis = client.http.pingIntervalMillis.toLong(),
            extensions = null,
            minimumDeflateSize = client.http.minWebSocketMessageToCompress
        )

        socket!!.connect(client.http)
    }

    private fun closeSocket() {
        socket?.close(RealtimeCode.POLICY_VIOLATION.value, null)
    }

    fun subscribe(
        vararg channels: String,
        callback: (RealtimeResponseEvent<Any>) -> Unit,
    ) = subscribe(
        channels = channels,
        Any::class.java,
        callback
    )

    fun <T> subscribe(
        vararg channels: String,
        payloadType: Class<T>,
        callback: (RealtimeResponseEvent<T>) -> Unit,
    ): RealtimeSubscription {
        channels.forEach {
            if (!channelCallbacks.containsKey(it)) {
                channelCallbacks[it] = mutableListOf(
                    RealtimeCallback(
                        payloadType,
                        callback as (RealtimeResponseEvent<*>) -> Unit
                    )
                )
                return@forEach
            }
            channelCallbacks[it]?.add(
                RealtimeCallback(payloadType, callback as (RealtimeResponseEvent<*>) -> Unit)
            )
        }

        launch {
            subCallDepth++
            delay(DEBOUNCE_MILLIS)
            if (subCallDepth == 1) {
                createSocket()
            }
            subCallDepth--
        }

        return RealtimeSubscription { unsubscribe(*channels) }
    }

    fun unsubscribe(vararg channels: String) {
        channels.forEach {
            channelCallbacks[it] = mutableListOf()
        }
        if (channelCallbacks.all { it.value.isEmpty() }) {
            errorCallbacks = mutableSetOf()
            closeSocket()
        }
    }

    fun doOnError(callback: (AppwriteException) -> Unit) {
        errorCallbacks.add(callback)
    }

    private inner class AppwriteWebSocketListener : WebSocketListener() {

        override fun onMessage(webSocket: WebSocket, text: String) {
            super.onMessage(webSocket, text)

            launch(IO) {
                val message = text.fromJson<RealtimeResponse>()
                when (message.type) {
                    TYPE_ERROR -> handleResponseError(message)
                    TYPE_EVENT -> handleResponseEvent(message)
                }
            }
        }

        private fun handleResponseError(message: RealtimeResponse) {
            val error = message.data.jsonCast<AppwriteException>()
            errorCallbacks.forEach { it.invoke(error) }
        }

        private suspend fun handleResponseEvent(message: RealtimeResponse) {
            val event = message.data.jsonCast<RealtimeResponseEvent<Any>>()
            event.channels.forEachAsync { channel ->
                channelCallbacks[channel]?.forEachAsync { callbackWrapper ->
                    event.payload = event.payload.jsonCast(callbackWrapper.payloadClass)
                    callbackWrapper.callback.invoke(event)
                }
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            super.onClosing(webSocket, code, reason)
            if (code == RealtimeCode.POLICY_VIOLATION.value) {
                return
            }
            launch {
                delay(1000)
                createSocket()
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            super.onFailure(webSocket, t, response)
            t.printStackTrace()
        }
    }
}