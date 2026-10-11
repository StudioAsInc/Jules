package dev.therealashik.jules

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.isSuccess
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class JulesApiException(val statusCode: Int, message: String) : Exception(message)

class JulesApiClient(private val apiKey: String) {
    private val baseUrl = "https://jules.googleapis.com/v1alpha"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
    private val http = HttpClient(Android) {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) { requestTimeoutMillis = 30_000; connectTimeoutMillis = 15_000; socketTimeoutMillis = 30_000 }
        expectSuccess = false
    }

    private fun sessionId(id: String): String = id.substringAfterLast('/').also {
        require(it.isNotBlank() && !it.contains(':')) { "Invalid session identifier" }
    }
    private suspend inline fun <reified T> HttpResponse.decode(): T {
        if (!status.isSuccess()) throw JulesApiException(status.value, "Jules API request failed (${status.value})")
        return body<T>()
    }
    private fun headers(builder: io.ktor.client.request.HttpRequestBuilder) {
        builder.header("x-goog-api-key", apiKey)
        builder.contentType(ContentType.Application.Json)
    }

    suspend fun listSessions(pageSize: Int, pageToken: String?): ListSessionsResponse {
        val response = http.get("$baseUrl/sessions") {
            headers(this); parameter("pageSize", pageSize.coerceIn(1, 100)); pageToken?.let { parameter("pageToken", it) }
        }
        return response.decode()
    }
    suspend fun getSession(id: String): Session = http.get("$baseUrl/sessions/${sessionId(id)}") { headers(this) }.decode()
    suspend fun listActivities(id: String, pageSize: Int, pageToken: String?): ListActivitiesResponse {
        val response = http.get("$baseUrl/sessions/${sessionId(id)}/activities") {
            headers(this); parameter("pageSize", pageSize.coerceIn(1, 100)); pageToken?.let { parameter("pageToken", it) }
        }
        return response.decode()
    }
    suspend fun listSources(pageSize: Int, pageToken: String?): ListSourcesResponse {
        val response = http.get("$baseUrl/sources") {
            headers(this); parameter("pageSize", pageSize.coerceIn(1, 100)); pageToken?.let { parameter("pageToken", it) }
        }
        return response.decode()
    }
    suspend fun createSession(request: CreateSessionRequest): Session =
        http.post("$baseUrl/sessions") { headers(this); setBody(request) }.decode()
    suspend fun sendMessage(id: String, prompt: String) {
        val response = http.post("$baseUrl/sessions/${sessionId(id)}:sendMessage") {
            headers(this); setBody(SendMessageRequest(prompt))
        }
        if (!response.status.isSuccess()) throw JulesApiException(response.status.value, "Jules API request failed (${response.status.value})")
    }
    suspend fun approvePlan(id: String) {
        val response = http.post("$baseUrl/sessions/${sessionId(id)}:approvePlan") { headers(this); setBody(emptyMap<String, String>()) }
        if (!response.status.isSuccess()) throw JulesApiException(response.status.value, "Jules API request failed (${response.status.value})")
    }
    suspend fun deleteSession(id: String) {
        val response = http.delete("$baseUrl/sessions/${sessionId(id)}") { headers(this) }
        if (!response.status.isSuccess()) throw JulesApiException(response.status.value, "Jules API request failed (${response.status.value})")
    }
    fun close() = http.close()
}

class JulesRepository(initialKey: String) {
    private var client: JulesApiClient? = initialKey.takeIf { it.isNotBlank() }?.let(::JulesApiClient)
    val hasKey: Boolean get() = client != null
    private fun api() = client ?: throw IllegalStateException("Add your Jules API key in Settings to continue.")

    suspend fun validateAndSetKey(key: String): List<Source> {
        val candidate = JulesApiClient(key)
        return try {
            val result = candidate.listSources(1, null)
            val old = client
            client = candidate
            old?.close()
            result.sources
        } catch (t: Throwable) {
            candidate.close()
            throw t
        }
    }
    fun removeKey() { client?.close(); client = null }
    suspend fun listSessions(pageSize: Int, pageToken: String?) = api().listSessions(pageSize, pageToken)
    suspend fun listSources(pageSize: Int = 100, pageToken: String? = null) = api().listSources(pageSize, pageToken)
    suspend fun getSession(id: String) = api().getSession(id)
    suspend fun listActivities(id: String, pageSize: Int, pageToken: String?) = api().listActivities(id, pageSize, pageToken)
    suspend fun createSession(request: CreateSessionRequest) = api().createSession(request)
    suspend fun sendMessage(id: String, prompt: String) = api().sendMessage(id, prompt)
    suspend fun approvePlan(id: String) = api().approvePlan(id)
    suspend fun deleteSession(id: String) = api().deleteSession(id)
}
