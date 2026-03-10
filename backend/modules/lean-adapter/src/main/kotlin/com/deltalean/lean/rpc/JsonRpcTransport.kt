package com.deltalean.lean.rpc

import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class JsonRpcNotification(
  val method: String,
  val params: JsonElement?
)

class JsonRpcTransport(
  private val input: InputStream,
  private val output: OutputStream,
  private val json: Json = Json { ignoreUnknownKeys = true }
) {
  private val nextId = AtomicInteger(1)
  private val running = AtomicBoolean(true)
  private val pending = ConcurrentHashMap<String, CompletableDeferred<JsonElement?>>()
  private val notificationsChannel = Channel<JsonRpcNotification>(capacity = Channel.BUFFERED)

  val notifications: Flow<JsonRpcNotification> = notificationsChannel.receiveAsFlow()

  private val readerThread = Thread(::readLoop, "lean-jsonrpc-reader").apply {
    isDaemon = true
    start()
  }

  suspend fun sendRequest(method: String, params: JsonElement? = null): JsonElement? {
    val id = nextId.getAndIncrement()
    val idKey = id.toString()
    val deferred = CompletableDeferred<JsonElement?>()
    pending[idKey] = deferred

    val payload = buildJsonObject {
      put("jsonrpc", JsonPrimitive("2.0"))
      put("id", JsonPrimitive(id))
      put("method", JsonPrimitive(method))
      if (params != null) {
        put("params", params)
      }
    }

    try {
      writeMessage(payload)
    } catch (t: Throwable) {
      pending.remove(idKey)
      deferred.completeExceptionally(t)
      throw t
    }

    return deferred.await()
  }

  fun sendNotification(method: String, params: JsonElement? = null) {
    val payload = buildJsonObject {
      put("jsonrpc", JsonPrimitive("2.0"))
      put("method", JsonPrimitive(method))
      if (params != null) {
        put("params", params)
      }
    }

    writeMessage(payload)
  }

  fun close() {
    running.set(false)
    readerThread.interrupt()
  }

  @Synchronized
  private fun writeMessage(payload: JsonObject) {
    val body = json.encodeToString(JsonObject.serializer(), payload).toByteArray(StandardCharsets.UTF_8)
    val header = "Content-Length: ${body.size}\r\n\r\n".toByteArray(StandardCharsets.US_ASCII)
    output.write(header)
    output.write(body)
    output.flush()
  }

  private fun readLoop() {
    try {
      while (running.get()) {
        val body = readBody() ?: break
        val message = json.parseToJsonElement(body).jsonObject
        routeMessage(message)
      }
    } catch (_: Throwable) {
      // Process shutdown is expected during teardown.
    } finally {
      running.set(false)
      val ex = IllegalStateException("JSON-RPC transport closed")
      pending.values.forEach { it.completeExceptionally(ex) }
      pending.clear()
      notificationsChannel.close()
    }
  }

  private fun routeMessage(message: JsonObject) {
    val method = message["method"]?.jsonPrimitive?.contentOrNull
    val idElement = message["id"]

    if (method != null && idElement == null) {
      val params = message["params"]
      notificationsChannel.trySend(JsonRpcNotification(method = method, params = params))
      return
    }

    if (idElement != null && method == null) {
      val idKey = jsonRpcIdKey(idElement)
      val deferred = pending.remove(idKey) ?: return
      val error = message["error"]
      if (error != null) {
        deferred.completeExceptionally(IllegalStateException(error.toString()))
      } else {
        deferred.complete(message["result"])
      }
    }
  }

  private fun readBody(): String? {
    val headers = readHeaders() ?: return null
    val length = headers["content-length"]?.toIntOrNull()
      ?: throw IllegalStateException("Missing Content-Length header")
    val bytes = ByteArray(length)
    readFully(bytes)
    return bytes.toString(StandardCharsets.UTF_8)
  }

  private fun readHeaders(): Map<String, String>? {
    val headers = mutableMapOf<String, String>()
    while (true) {
      val line = readAsciiLine() ?: return null
      if (line.isEmpty()) {
        return headers
      }

      val split = line.split(':', limit = 2)
      if (split.size == 2) {
        headers[split[0].trim().lowercase()] = split[1].trim()
      }
    }
  }

  private fun readAsciiLine(): String? {
    val buf = ArrayList<Byte>(64)
    while (true) {
      val b = input.read()
      if (b == -1) {
        return if (buf.isEmpty()) null else throw EOFException("Unexpected EOF")
      }

      if (b == '\n'.code) {
        break
      }

      if (b != '\r'.code) {
        buf.add(b.toByte())
      }
    }

    return buf.toByteArray().toString(StandardCharsets.US_ASCII)
  }

  private fun readFully(target: ByteArray) {
    var offset = 0
    while (offset < target.size) {
      val read = input.read(target, offset, target.size - offset)
      if (read == -1) {
        throw EOFException("Unexpected EOF while reading message body")
      }
      offset += read
    }
  }

  private fun jsonRpcIdKey(idElement: JsonElement): String {
    val primitive = idElement.jsonPrimitive
    return primitive.contentOrNull ?: primitive.toString()
  }
}
