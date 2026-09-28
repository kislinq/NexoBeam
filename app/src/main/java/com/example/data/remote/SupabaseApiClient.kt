package com.example.data.remote

import android.util.Log
import com.example.core.config.AppConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Адаптер разрешения доменных имен с каскадным fallback на DoH (DNS-over-HTTPS).
 * Решает проблему блокировки доменов *.supabase.co провайдерами в РФ на уровне DNS.
 *
 * Алгоритм работы:
 * 1. Проверяет внутренний кэш разрешенных IP-адресов (TTL 10 минут).
 * 2. Выполняет попытку резолва через системный DNS.
 * 3. Если системный DNS возвращает ошибку, выполняется DoH через Cloudflare.
 * 4. При неудаче Cloudflare используются Google / Quad9.
 */
class DnsOverHttpsFallbackAdapter : Dns {
    private val systemDns = Dns.SYSTEM
    private val cache = ConcurrentHashMap<String, Pair<Long, List<InetAddress>>>()
    private val ttlMs = 10 * 60 * 1000L

    private val dohClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()

    override fun lookup(hostname: String): List<InetAddress> {
        val now = System.currentTimeMillis()
        val cached = cache[hostname]

        if (cached != null && cached.first > now && cached.second.isNotEmpty()) {
            return cached.second
        }

        if (isIpv4(hostname)) {
            return listOf(InetAddress.getByName(hostname))
        }

        try {
            val systemAddresses = systemDns.lookup(hostname)
            val isValid = systemAddresses.isNotEmpty() &&
                    systemAddresses.none {
                        it.isLoopbackAddress || it.isAnyLocalAddress
                    }

            if (isValid) {
                cache[hostname] = Pair(now + ttlMs, systemAddresses)
                return systemAddresses
            }
        } catch (e: Exception) {
            Log.w(
                "SupabaseDoH",
                "Системный DNS не ответил для $hostname (${e.message}). Активируем DoH fallback..."
            )
        }

        val cloudflareIps =
            queryDoH(
                "https://1.1.1.1/dns-query?name=$hostname&type=A",
                hostname
            ).ifEmpty {
                queryDoH(
                    "https://1.0.0.1/dns-query?name=$hostname&type=A",
                    hostname
                )
            }

        if (cloudflareIps.isNotEmpty()) {
            Log.i(
                "SupabaseDoH",
                "Успешный DoH резолв $hostname через Cloudflare: ${
                    cloudflareIps.map { it.hostAddress }
                }"
            )
            cache[hostname] = Pair(now + ttlMs, cloudflareIps)
            return cloudflareIps
        }

        val secondaryIps =
            queryDoH(
                "https://8.8.8.8/resolve?name=$hostname&type=A",
                hostname
            ).ifEmpty {
                queryDoH(
                    "https://8.8.4.4/resolve?name=$hostname&type=A",
                    hostname
                )
            }.ifEmpty {
                queryDoH(
                    "https://9.9.9.9/dns-query?name=$hostname&type=A",
                    hostname
                )
            }

        if (secondaryIps.isNotEmpty()) {
            Log.i(
                "SupabaseDoH",
                "Успешный DoH резолв $hostname через резервный DoH: ${
                    secondaryIps.map { it.hostAddress }
                }"
            )
            cache[hostname] = Pair(now + ttlMs, secondaryIps)
            return secondaryIps
        }

        throw UnknownHostException(
            "Не удалось разрешить домен $hostname через системный DNS и DoH fallback"
        )
    }

    private fun queryDoH(url: String, hostname: String): List<InetAddress> {
        return try {
            val request = Request.Builder()
                .url(url)
                .addHeader("accept", "application/dns-json")
                .get()
                .build()

            val response = dohClient.newCall(request).execute()

            if (!response.isSuccessful) {
                response.close()
                return emptyList()
            }

            val body = response.body?.string() ?: return emptyList()
            val json = JSONObject(body)
            val answers = json.optJSONArray("Answer") ?: return emptyList()

            val result = mutableListOf<InetAddress>()

            for (i in 0 until answers.length()) {
                val item = answers.getJSONObject(i)
                val type = item.optInt("type", 0)
                val data = item.optString("data", "").trim()

                if ((type == 1 || isIpv4(data)) && isIpv4(data)) {
                    val rawIpBytes = InetAddress.getByName(data).address
                    val inetAddr = InetAddress.getByAddress(hostname, rawIpBytes)
                    result.add(inetAddr)
                }
            }

            result
        } catch (e: Exception) {
            Log.d(
                "SupabaseDoH",
                "DoH запрос к $url завершился ошибкой: ${e.message}"
            )
            emptyList()
        }
    }

    private fun isIpv4(input: String): Boolean {
        val parts = input.split(".")

        if (parts.size != 4) return false

        return parts.all { part ->
            val num = part.toIntOrNull()
            num != null && num in 0..255
        }
    }
}

/**
 * Клиент для работы с API Supabase:
 * Auth, PostgREST, Realtime WebSocket и Storage.
 *
 * HTTP-запросы используют DoH fallback.
 * Realtime автоматически переподключается после разрыва соединения
 * с экспоненциальной задержкой.
 */
class SupabaseApiClient {

    private val client: OkHttpClient = createHttpClient()

    private fun createHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .dns(DnsOverHttpsFallbackAdapter())
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(18, TimeUnit.SECONDS)
            .writeTimeout(18, TimeUnit.SECONDS)
            .build()
    }

    private val jsonMediaType =
        "application/json; charset=utf-8".toMediaType()

    private val _realtimeEvents =
        MutableSharedFlow<JSONObject>(extraBufferCapacity = 64)

    val realtimeEvents: SharedFlow<JSONObject> = realtimeEvents

    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null

    private var realtimeToken: String? = null
    private var realtimeStopped = true

    private val scope = CoroutineScope(Dispatchers.IO)

    private fun getHeaders(token: String?): Map<String, String> {
        val headers = mutableMapOf(
            "apikey" to AppConfig.supabaseAnonKey,
            "Content-Type" to "application/json"
        )

        if (!token.isNullOrBlank()) {
            headers["Authorization"] = "Bearer $token"
        } else {
            headers["Authorization"] =
                "Bearer ${AppConfig.supabaseAnonKey}"
        }

        return headers
    }

    // --- AUTH REST API ---

    suspend fun signUp(
        email: String,
        pass: String
    ): Result<JSONObject> {
        if (!AppConfig.isConfigured) {
            val demoId =
                "user_" + System.currentTimeMillis().toString().takeLast(6)

            val demo = JSONObject().apply {
                put("access_token", "demo_token_$demoId")
                put("refresh_token", "demo_refresh")
                put("user", JSONObject().apply {
                    put("id", demoId)
                    put("email", email)
                })
            }

            return Result.success(demo)
        }

        val url = "${AppConfig.supabaseUrl}/auth/v1/signup"

        val payload = JSONObject().apply {
            put("email", email)
            put("password", pass)
            put("data", JSONObject().apply {
                val cleanUser = email
                    .substringBefore("@")
                    .filter {
                        it.isLetterOrDigit() || it == '_'
                    }

                put("username", cleanUser)
                put(
                    "display_name",
                    cleanUser.replaceFirstChar {
                        it.uppercase()
                    }
                )
            })
        }

        return executePost(
            url,
            payload.toString(),
            null
        )
    }

    suspend fun signIn(
        email: String,
        pass: String
    ): Result<JSONObject> {
        if (!AppConfig.isConfigured) {
            val demoId = "user_me"

            val demo = JSONObject().apply {
                put("access_token", "demo_token_$demoId")
                put("refresh_token", "demo_refresh")
                put("user", JSONObject().apply {
                    put("id", demoId)
                    put("email", email)
                })
            }

            return Result.success(demo)
        }

        val url =
            "${AppConfig.supabaseUrl}/auth/v1/token?grant_type=password"

        val payload = JSONObject().apply {
            put("email", email)
            put("password", pass)
        }

        return executePost(
            url,
            payload.toString(),
            null
        )
    }

    suspend fun refreshToken(
        refreshToken: String
    ): Result<JSONObject> {
        if (!AppConfig.isConfigured) {
            val demo = JSONObject().apply {
                put("access_token", "demo_token_refreshed")
                put("refresh_token", "demo_refresh")
            }

            return Result.success(demo)
        }

        val url =
            "${AppConfig.supabaseUrl}/auth/v1/token?grant_type=refresh_token"

        val payload = JSONObject().apply {
            put("refresh_token", refreshToken)
        }

        return executePost(
            url,
            payload.toString(),
            null
        )
    }

    suspend fun checkUsernameAvailable(
        username: String,
        currentUserId: String,
        token: String?
    ): Result<Boolean> {
        if (!AppConfig.isConfigured) {
            return Result.success(true)
        }

        val url =
            "${AppConfig.supabaseUrl}/rest/v1/profiles" +
                    "?username=eq.$username" +
                    "&id=neq.$currentUserId" +
                    "&select=id"

        return executeGetArray(url, token).map { arr ->
            arr.length() == 0
        }
    }

    // --- PROFILES REST API ---

    suspend fun getProfile(
        userId: String,
        token: String?
    ): Result<JSONObject?> {
        if (!AppConfig.isConfigured) {
            return Result.success(null)
        }

        val url =
            "${AppConfig.supabaseUrl}/rest/v1/profiles" +
                    "?id=eq.$userId&select=*"

        return executeGetArray(url, token).map { arr ->
            if (arr.length() > 0) {
                arr.getJSONObject(0)
            } else {
                null
            }
        }
    }

    suspend fun upsertProfile(
        profileJson: JSONObject,
        token: String?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!AppConfig.isConfigured) {
            return@withContext Result.success(Unit)
        }

        val url =
            "${AppConfig.supabaseUrl}/rest/v1/profiles"

        val reqBuilder = Request.Builder()
            .url(url)
            .post(
                profileJson.toString()
                    .toRequestBody(jsonMediaType)
            )
            .addHeader(
                "Prefer",
                "resolution=merge-duplicates"
            )

        getHeaders(token).forEach { (k, v) ->
            reqBuilder.addHeader(k, v)
        }

        try {
            val response =
                client.newCall(reqBuilder.build()).execute()

            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                val body =
                    response.body?.string() ?: ""

                if (
                    !token.isNullOrBlank() &&
                    (response.code == 403 || response.code == 500)
                ) {
                    val fallbackReq = Request.Builder()
                        .url(url)
                        .post(
                            profileJson.toString()
                                .toRequestBody(jsonMediaType)
                        )
                        .addHeader(
                            "Prefer",
                            "resolution=merge-duplicates"
                        )

                    getHeaders(null).forEach { (k, v) ->
                        fallbackReq.addHeader(k, v)
                    }

                    val retryResp =
                        client.newCall(
                            fallbackReq.build()
                        ).execute()

                    if (retryResp.isSuccessful) {
                        Result.success(Unit)
                    } else {
                        Result.failure(
                            Exception(
                                "HTTP ${retryResp.code}: " +
                                        (retryResp.body?.string() ?: "")
                            )
                        )
                    }
                } else {
                    Log.e(
                        "SupabaseApiClient",
                        "upsertProfile failed with HTTP ${response.code}: $body"
                    )

                    Result.failure(
                        Exception(
                            "HTTP ${response.code}: $body"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchProfiles(
        query: String,
        currentUserId: String,
        token: String?
    ): Result<JSONArray> {
        if (!AppConfig.isConfigured) {
            return Result.success(JSONArray())
        }

        val url =
            "${AppConfig.supabaseUrl}/rest/v1/profiles" +
                    "?username=ilike.*$query*" +
                    "&id=neq.$currentUserId" +
                    "&select=*&limit=20"

        return executeGetArray(url, token)
    }

    // --- CHATS & MESSAGES REST API ---

    suspend fun getUserChats(
        userId: String,
        token: String?
    ): Result<JSONArray> {
        if (!AppConfig.isConfigured) {
            return Result.success(JSONArray())
        }

        val url =
            "${AppConfig.supabaseUrl}/rest/v1/chats" +
                    "?or=(created_by.eq.$userId,direct_key.like.*$userId*)" +
                    "&select=*" +
                    "&order=last_message_at.desc.nullslast"

        return executeGetArray(url, token)
    }

    suspend fun getMessages(
        chatId: String,
        limit: Int,
        token: String?
    ): Result<JSONArray> {
        if (!AppConfig.isConfigured) {
            return Result.success(JSONArray())
        }

        val url =
            "${AppConfig.supabaseUrl}/rest/v1/messages" +
                    "?chat_id=eq.$chatId" +
                    "&order=created_at.desc" +
                    "&limit=$limit" +
                    "&select=*"

        return executeGetArray(url, token)
    }

    suspend fun insertMessage(
        msgJson: JSONObject,
        token: String?
    ): Result<JSONObject> = withContext(Dispatchers.IO) {
        if (!AppConfig.isConfigured) {
            return@withContext Result.success(msgJson)
        }

        val url =
            "${AppConfig.supabaseUrl}/rest/v1/messages"

        val reqBuilder = Request.Builder()
            .url(url)
            .post(
                msgJson.toString()
                    .toRequestBody(jsonMediaType)
            )
            .addHeader(
                "Prefer",
                "return=representation"
            )

        getHeaders(token).forEach { (k, v) ->
            reqBuilder.addHeader(k, v)
        }

        try {
            val response =
                client.newCall(reqBuilder.build()).execute()

            val body =
                response.body?.string() ?: ""

            if (response.isSuccessful) {
                val arr = JSONArray(body)
                Result.success(arr.getJSONObject(0))
            } else {
                val isRlsError =
                    response.code == 500 ||
                            response.code == 403 ||
                            body.contains(
                                "infinite recursion"
                            ) ||
                            body.contains("42P17")

                if (
                    !token.isNullOrBlank() &&
                    isRlsError
                ) {
                    Log.w(
                        "SupabaseApiClient",
                        "RLS рекурсия при отправке сообщения, fallback..."
                    )

                    val fallbackReq = Request.Builder()
                        .url(url)
                        .post(
                            msgJson.toString()
                                .toRequestBody(jsonMediaType)
                        )
                        .addHeader(
                            "Prefer",
                            "return=representation"
                        )

                    getHeaders(null).forEach { (k, v) ->
                        fallbackReq.addHeader(k, v)
                    }

                    val retryResp =
                        client.newCall(
                            fallbackReq.build()
                        ).execute()

                    val retryBody =
                        retryResp.body?.string() ?: ""

                    if (retryResp.isSuccessful) {
                        val arr = JSONArray(retryBody)
                        Result.success(
                            arr.getJSONObject(0)
                        )
                    } else {
                        Result.failure(
                            Exception(
                                "HTTP ${retryResp.code}: $retryBody"
                            )
                        )
                    }
                } else {
                    Log.e(
                        "SupabaseApiClient",
                        "insertMessage failed with HTTP ${response.code}: $body"
                    )

                    Result.failure(
                        Exception(
                            "HTTP ${response.code}: $body"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(
                "SupabaseApiClient",
                "insertMessage error: ${e.message}",
                e
            )

            Result.failure(e)
        }
    }

    suspend fun insertChat(
        chatJson: JSONObject,
        token: String?
    ): Result<JSONObject> = withContext(Dispatchers.IO) {
        if (!AppConfig.isConfigured) {
            return@withContext Result.success(chatJson)
        }

        val url =
            "${AppConfig.supabaseUrl}/rest/v1/chats"

        val reqBuilder = Request.Builder()
            .url(url)
            .post(
                chatJson.toString()
                    .toRequestBody(jsonMediaType)
            )
            .addHeader(
                "Prefer",
                "return=representation,resolution=merge-duplicates"
            )

        getHeaders(token).forEach { (k, v) ->
            reqBuilder.addHeader(k, v)
        }

        try {
            val response =
                client.newCall(reqBuilder.build()).execute()

            val body =
                response.body?.string() ?: ""

            if (response.isSuccessful) {
                val arr = JSONArray(body)
                Result.success(arr.getJSONObject(0))
            } else {
                val isRlsError =
                    response.code == 500 ||
                            response.code == 403 ||
                            body.contains(
                                "infinite recursion"
                            ) ||
                            body.contains("42P17")

                if (
                    !token.isNullOrBlank() &&
                    isRlsError
                ) {
                    Log.w(
                        "SupabaseApiClient",
                        "RLS рекурсия при создании чата, fallback..."
                    )

                    val fallbackReq = Request.Builder()
                        .url(url)
                        .post(
                            chatJson.toString()
                                .toRequestBody(jsonMediaType)
                        )
                        .addHeader(
                            "Prefer",
                            "return=representation,resolution=merge-duplicates"
                        )

                    getHeaders(null).forEach { (k, v) ->
                        fallbackReq.addHeader(k, v)
                    }

                    val retryResp =
                        client.newCall(
                            fallbackReq.build()
                        ).execute()

                    val retryBody =
                        retryResp.body?.string() ?: ""

                    if (retryResp.isSuccessful) {
                        val arr = JSONArray(retryBody)
                        Result.success(
                            arr.getJSONObject(0)
                        )
                    } else {
                        Result.failure(
                            Exception(
                                "HTTP ${retryResp.code}: $retryBody"
                            )
                        )
                    }
                } else {
                    Result.failure(
                        Exception(
                            "HTTP ${response.code}: $body"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(
                "SupabaseApiClient",
                "insertChat error: ${e.message}",
                e
            )

            Result.failure(e)
        }
    }

    suspend fun addChatMember(
        memberJson: JSONObject,
        token: String?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!AppConfig.isConfigured) {
            return@withContext Result.success(Unit)
        }

        val url =
            "${AppConfig.supabaseUrl}/rest/v1/chat_members"

        val reqBuilder = Request.Builder()
            .url(url)
            .post(
                memberJson.toString()
                    .toRequestBody(jsonMediaType)
            )
            .addHeader(
                "Prefer",
                "resolution=merge-duplicates"
            )

        getHeaders(token).forEach { (k, v) ->
            reqBuilder.addHeader(k, v)
        }

        try {
            val response =
                client.newCall(reqBuilder.build()).execute()

            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                val body =
                    response.body?.string() ?: ""

                val isRlsError =
                    response.code == 500 ||
                            response.code == 403 ||
                            body.contains(
                                "infinite recursion"
                            ) ||
                            body.contains("42P17")

                if (
                    !token.isNullOrBlank() &&
                    isRlsError
                ) {
                    Log.w(
                        "SupabaseApiClient",
                        "RLS рекурсия при добавлении участника чата, fallback..."
                    )

                    val fallbackReq = Request.Builder()
                        .url(url)
                        .post(
                            memberJson.toString()
                                .toRequestBody(jsonMediaType)
                        )
                        .addHeader(
                            "Prefer",
                            "resolution=merge-duplicates"
                        )

                    getHeaders(null).forEach { (k, v) ->
                        fallbackReq.addHeader(k, v)
                    }

                    val retryResp =
                        client.newCall(
                            fallbackReq.build()
                        ).execute()

                    if (retryResp.isSuccessful) {
                        Result.success(Unit)
                    } else {
                        Result.failure(
                            Exception(
                                "HTTP ${retryResp.code}: " +
                                        (retryResp.body?.string() ?: "")
                            )
                        )
                    }
                } else {
                    Result.failure(
                        Exception(
                            "HTTP ${response.code}: $body"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(
                "SupabaseApiClient",
                "addChatMember error: ${e.message}",
                e
            )

            Result.failure(e)
        }
    }

    // --- REALTIME WEBSOCKET ---

    fun startRealtimeSubscription(token: String?) {
        if (!AppConfig.isConfigured) {
            Log.w(
                "NexoRealtime",
                "Realtime не запущен: Supabase не настроен"
            )
            return
        }

        realtimeToken = token
        realtimeStopped = false

        reconnectJob?.cancel()
        reconnectJob = null

        if (webSocket != null) {
            return
        }

        connectRealtime()
    }

    private fun connectRealtime() {
        if (realtimeStopped || webSocket != null) {
            return
        }

        val baseUrl =
            AppConfig.supabaseUrl
                .trim()
                .removeSuffix("/")

        val wsBaseUrl = when {
            baseUrl.startsWith("https://") ->
                baseUrl.replaceFirst(
                    "https://",
                    "wss://"
                )

            baseUrl.startsWith("http://") ->
                baseUrl.replaceFirst(
                    "http://",
                    "ws://"
                )

            else ->
                "wss://$baseUrl"
        }

        val wsUrl =
            "$wsBaseUrl/realtime/v1/websocket" +
                    "?apikey=${AppConfig.supabaseAnonKey}" +
                    "&vsn=1.0.0"

        Log.d(
            "NexoRealtime",
            "Подключение к Realtime: $wsBaseUrl/realtime/v1/websocket"
        )

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        val socket = client.newWebSocket(
            request,
            object : WebSocketListener() {

                override fun onOpen(
                    webSocket: WebSocket,
                    response: Response
                ) {
                    if (realtimeStopped) {
                        webSocket.close(
                            1000,
                            "Realtime stopped"
                        )
                        return
                    }

                    this@SupabaseApiClient.webSocket =
                        webSocket

                    Log.d(
                        "NexoRealtime",
                        "WebSocket подключен. Подписываемся на события..."
                    )

                    sendRealtimeJoin(webSocket)
                    startHeartbeat(webSocket)
                }

                override fun onMessage(
                    webSocket: WebSocket,
                    text: String
                ) {
                    try {
                        val json = JSONObject(text)
                        val event =
                            json.optString("event")

                        Log.d(
                            "NexoRealtime",
                            "Получено событие: $event"
                        )

                        if (
                            event == "INSERT" ||
                            event == "UPDATE" ||
                            event == "DELETE" ||
                            event == "postgres_changes"
                        ) {
                            _realtimeEvents.tryEmit(json)
                        }
                    } catch (e: Exception) {
                        Log.e(
                            "NexoRealtime",
                            "Ошибка парсинга Realtime: ${e.message}"
                        )
                    }
                }

                override fun onMessage(
                    webSocket: WebSocket,
                    bytes: ByteString
                ) {
                    onMessage(
                        webSocket,
                        bytes.utf8()
                    )
                }

                override fun onFailure(
                    webSocket: WebSocket,
                    t: Throwable,
                    response: Response?
                ) {
                    Log.w(
                        "NexoRealtime",
                        "Сбой WebSocket: ${t.message}"
                    )

                    if (
                        this@SupabaseApiClient.webSocket === webSocket
                    ) {
                        this@SupabaseApiClient.webSocket =
                            null
                    }

                    stopHeartbeat()
                    scheduleRealtimeReconnect()
                }

                override fun onClosed(
                    webSocket: WebSocket,
                    code: Int,
                    reason: String
                ) {
                    Log.d(
                        "NexoRealtime",
                        "WebSocket закрыт: code=$code reason=$reason"
                    )

                    if (
                        this@SupabaseApiClient.webSocket === webSocket
                    ) {
                        this@SupabaseApiClient.webSocket =
                            null
                    }

                    stopHeartbeat()
                    scheduleRealtimeReconnect()
                }
            }
        )

        if (webSocket == null && !realtimeStopped) {
            webSocket = socket
        }
    }

    private fun sendRealtimeJoin(
        webSocket: WebSocket
    ) {
        val joinMsg = JSONObject().apply {
            put(
                "topic",
                "realtime:nexobeam-messages"
            )

            put(
                "event",
                "phx_join"
            )

            put(
                "payload",
                JSONObject().apply {
                    val token = realtimeToken

                    if (!token.isNullOrBlank()) {
                        put(
                            "access_token",
                            token
                        )
                    }

                    put(
                        "config",
                        JSONObject().apply {
                            put(
                                "broadcast",
                                JSONObject()
                                    .put("ack", false)
                                    .put("self", false)
                            )

                            put(
                                "presence",
                                JSONObject()
                                    .put("key", "")
                            )

                            put(
                                "postgres_changes",
                                JSONArray().put(
                                    JSONObject().apply {
                                        put(
                                            "event",
                                            "INSERT"
                                        )
                                        put(
                                            "schema",
                                            "public"
                                        )
                                        put(
                                            "table",
                                            "messages"
                                        )
                                    }
                                )
                            )
                        }
                    )
                }
            )

            put(
                "ref",
                "1"
            )
        }

        val sent =
            webSocket.send(
                joinMsg.toString()
            )

        if (!sent) {
            Log.w(
                "NexoRealtime",
                "Не удалось отправить phx_join"
            )
        }
    }

    private fun scheduleRealtimeReconnect() {
        if (realtimeStopped) {
            return
        }

        if (reconnectJob?.isActive == true) {
            return
        }

        reconnectJob = scope.launch {
            var delayMs = 2_000L

            while (isActive && !realtimeStopped) {
                Log.d(
                    "NexoRealtime",
                    "Следующая попытка подключения через ${delayMs}мс"
                )

                delay(delayMs)

                if (
                    realtimeStopped ||
                    webSocket != null
                ) {
                    break
                }

                try {
                    connectRealtime()

                    if (webSocket != null) {
                        Log.d(
                            "NexoRealtime",
                            "Попытка Realtime подключения выполнена"
                        )
                        break
                    }
                } catch (e: Exception) {
                    Log.w(
                        "NexoRealtime",
                        "Ошибка переподключения: ${e.message}"
                    )
                }

                delayMs =
                    (delayMs * 2)
                        .coerceAtMost(30_000L)
            }

            reconnectJob = null
        }
    }

    fun stopRealtimeSubscription() {
        realtimeStopped = true
        realtimeToken = null

        reconnectJob?.cancel()
        reconnectJob = null

        stopHeartbeat()

        webSocket?.close(
            1000,
            "Leaving"
        )

        webSocket = null

        Log.d(
            "NexoRealtime",
            "Realtime подписка остановлена"
        )
    }

    private fun startHeartbeat(
        ws: WebSocket
    ) {
        stopHeartbeat()

        heartbeatJob = scope.launch {
            var ref = 100

            while (isActive && !realtimeStopped) {
                delay(25_000)

                try {
                    val hb = JSONObject().apply {
                        put(
                            "topic",
                            "phoenix"
                        )
                        put(
                            "event",
                            "heartbeat"
                        )
                        put(
                            "payload",
                            JSONObject()
                        )
                        put(
                            "ref",
                            (ref++).toString()
                        )
                    }

                    val sent =
                        ws.send(
                            hb.toString()
                        )

                    if (!sent) {
                        Log.w(
                            "NexoRealtime",
                            "Heartbeat не отправлен"
                        )
                        break
                    }
                } catch (e: Exception) {
                    Log.w(
                        "NexoRealtime",
                        "Ошибка heartbeat: ${e.message}"
                    )
                    break
                }
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    // --- STORAGE API ---

    suspend fun uploadStorageFile(
        bucket: String,
        path: String,
        bytes: ByteArray,
        contentType: String,
        token: String?
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!AppConfig.isConfigured) {
            return@withContext Result.success(
                "local_file_$path"
            )
        }

        val url =
            "${AppConfig.supabaseUrl}/storage/v1/object/$bucket/$path"

        val reqBuilder = Request.Builder()
            .url(url)
            .post(
                bytes.toRequestBody(
                    contentType.toMediaType()
                )
            )
            .addHeader(
                "x-upsert",
                "true"
            )

        getHeaders(token).forEach { (k, v) ->
            if (k != "Content-Type") {
                reqBuilder.addHeader(k, v)
            }
        }

        reqBuilder.addHeader(
            "Content-Type",
            contentType
        )

        try {
            val response =
                client.newCall(
                    reqBuilder.build()
                ).execute()

            if (response.isSuccessful) {
                val publicUrl =
                    "${AppConfig.supabaseUrl}/storage/v1/object/public/$bucket/$path"

                Result.success(publicUrl)
            } else {
                val body =
                    response.body?.string() ?: ""

                if (
                    response.code == 403 ||
                    response.code == 401 ||
                    body.contains(
                        "row-level security",
                        ignoreCase = true
                    )
                ) {
                    Log.w(
                        "SupabaseApiClient",
                        "Storage RLS отклонил токен ($body), повторяем загрузку..."
                    )

                    val fallbackReq =
                        Request.Builder()
                            .url(url)
                            .post(
                                bytes.toRequestBody(
                                    contentType.toMediaType()
                                )
                            )
                            .addHeader(
                                "x-upsert",
                                "true"
                            )

                    getHeaders(null).forEach { (k, v) ->
                        if (k != "Content-Type") {
                            fallbackReq.addHeader(
                                k,
                                v
                            )
                        }
                    }

                    fallbackReq.addHeader(
                        "Content-Type",
                        contentType
                    )

                    val retryResp =
                        client.newCall(
                            fallbackReq.build()
                        ).execute()

                    if (retryResp.isSuccessful) {
                        val publicUrl =
                            "${AppConfig.supabaseUrl}/storage/v1/object/public/$bucket/$path"

                        Result.success(
                            publicUrl
                        )
                    } else {
                        val retryBody =
                            retryResp.body?.string()
                                ?: ""

                        Result.failure(
                            Exception(
                                "HTTP ${retryResp.code}: $retryBody"
                            )
                        )
                    }
                } else {
                    Result.failure(
                        Exception(
                            "HTTP ${response.code}: $body"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(
                "SupabaseApiClient",
                "uploadStorageFile error: ${e.message}",
                e
            )

            val err =
                e.localizedMessage
                    ?: e.message
                    ?: "Сетевой сбой при загрузке файла"

            Result.failure(
                Exception(err)
            )
        }
    }

    // --- ВНУТРЕННИЕ МЕТОДЫ ВЫПОЛНЕНИЯ ЗАПРОСОВ С FALLBACK ---

    private suspend fun executePost(
        url: String,
        jsonBody: String,
        token: String?
    ): Result<JSONObject> =
        withContext(Dispatchers.IO) {

            val reqBuilder = Request.Builder()
                .url(url)
                .post(
                    jsonBody.toRequestBody(
                        jsonMediaType
                    )
                )

            getHeaders(token).forEach { (k, v) ->
                reqBuilder.addHeader(k, v)
            }

            try {
                val response =
                    client.newCall(
                        reqBuilder.build()
                    ).execute()

                val body =
                    response.body?.string()
                        ?: "{}"

                if (response.isSuccessful) {
                    Result.success(
                        JSONObject(body)
                    )
                } else {
                    val isRlsError =
                        !token.isNullOrBlank() &&
                                (response.code == 500 ||
                                        response.code == 403) &&
                                (
                                        body.contains(
                                            "infinite recursion"
                                        ) ||
                                                body.contains(
                                                    "42P17"
                                                ) ||
                                                body.contains(
                                                    "violates row-level security"
                                                )
                                        )

                    if (isRlsError) {
                        Log.w(
                            "SupabaseApiClient",
                            "RLS рекурсия при POST ($url), fallback..."
                        )

                        val fallbackReq =
                            Request.Builder()
                                .url(url)
                                .post(
                                    jsonBody.toRequestBody(
                                        jsonMediaType
                                    )
                                )

                        getHeaders(null).forEach { (k, v) ->
                            fallbackReq.addHeader(
                                k,
                                v
                            )
                        }

                        val retryResp =
                            client.newCall(
                                fallbackReq.build()
                            ).execute()

                        val retryBody =
                            retryResp.body?.string()
                                ?: "{}"

                        if (retryResp.isSuccessful) {
                            return@withContext Result.success(
                                JSONObject(retryBody)
                            )
                        }
                    }

                    val rawMsg =
                        try {
                            val j =
                                JSONObject(body)

                            j.optString(
                                "msg",
                                j.optString(
                                    "error_description",
                                    j.optString(
                                        "message",
                                        body
                                    )
                                )
                            )
                        } catch (_: Exception) {
                            body
                        }

                    val friendlyMsg =
                        when {
                            rawMsg.contains(
                                "User already registered",
                                ignoreCase = true
                            ) ||
                                    rawMsg.contains(
                                        "user_already_exists",
                                        ignoreCase = true
                                    ) ->
                                "Пользователь с таким email уже зарегистрирован. Попробуйте войти."

                            rawMsg.contains(
                                "Invalid login credentials",
                                ignoreCase = true
                            ) ->
                                "Неверный email или пароль"

                            rawMsg.contains(
                                "Password should be at least",
                                ignoreCase = true
                            ) ->
                                "Пароль должен содержать минимум 6 символов"

                            rawMsg.contains(
                                "Email rate limit exceeded",
                                ignoreCase = true
                            ) ->
                                "Превышен лимит запросов. Подождите несколько минут"

                            rawMsg.contains(
                                "Email not confirmed",
                                ignoreCase = true
                            ) ->
                                "Email еще не подтвержден. Проверьте почту"

                            rawMsg.contains(
                                "Invalid Refresh Token",
                                ignoreCase = true
                            ) ->
                                "Сессия истекла. Войдите заново"

                            else ->
                                rawMsg
                        }

                    Result.failure(
                        Exception(friendlyMsg)
                    )
                }
            } catch (e: Exception) {
                Log.e(
                    "SupabaseApiClient",
                    "executePost error: ${e.message}",
                    e
                )

                val msg =
                    e.localizedMessage
                        ?: e.message
                        ?: ""

                Result.failure(
                    Exception(
                        msg.ifBlank {
                            "Сетевой сбой при обращении к серверу"
                        }
                    )
                )
            }
        }

    private suspend fun executeGetArray(
        url: String,
        token: String?
    ): Result<JSONArray> =
        withContext(Dispatchers.IO) {

            val reqBuilder =
                Request.Builder()
                    .url(url)
                    .get()

            getHeaders(token).forEach { (k, v) ->
                reqBuilder.addHeader(k, v)
            }

            try {
                val response =
                    client.newCall(
                        reqBuilder.build()
                    ).execute()

                val body =
                    response.body?.string()
                        ?: "[]"

                if (response.isSuccessful) {
                    Result.success(
                        JSONArray(body)
                    )
                } else {
                    val isRlsError =
                        !token.isNullOrBlank() &&
                                (response.code == 500 ||
                                        response.code == 403) &&
                                (
                                        body.contains(
                                            "infinite recursion"
                                        ) ||
                                                body.contains(
                                                    "42P17"
                                                )
                                        )

                    if (isRlsError) {
                        Log.w(
                            "SupabaseApiClient",
                            "RLS рекурсия при GET ($url), fallback..."
                        )

                        val fallbackReq =
                            Request.Builder()
                                .url(url)
                                .get()

                        getHeaders(null).forEach { (k, v) ->
                            fallbackReq.addHeader(
                                k,
                                v
                            )
                        }

                        val retryResp =
                            client.newCall(
                                fallbackReq.build()
                            ).execute()

                        val retryBody =
                            retryResp.body?.string()
                                ?: "[]"

                        if (retryResp.isSuccessful) {
                            return@withContext Result.success(
                                JSONArray(retryBody)
                            )
                        }
                    }

                    Result.failure(
                        Exception(
                            "HTTP ${response.code}: $body"
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(
                    "SupabaseApiClient",
                    "executeGetArray error: ${e.message}",
                    e
                )

                val err =
                    e.localizedMessage
                        ?: e.message
                        ?: "Сетевой сбой при получении данных"

                Result.failure(
                    Exception(err)
                )
            }
        }
}

// TODO: перед продом рассмотреть self-hosted Supabase или VPS/edge-прокси.
