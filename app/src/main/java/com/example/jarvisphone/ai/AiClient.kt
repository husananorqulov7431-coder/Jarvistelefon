package com.example.jarvisphone.ai

import android.content.Context
import com.example.jarvisphone.core.AiPlan
import com.example.jarvisphone.core.ChatMessage
import com.example.jarvisphone.core.CommandAction
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.util.concurrent.TimeUnit

class AiPlanner(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val providers = ProviderLoader(context).load()

    fun plan(input: String, memory: List<ChatMessage>): AiPlan? {
        for (provider in providers) {
            val key = provider.apiKey.orEmpty()
            if (key.isBlank()) continue

            try {
                val body = buildRequest(provider, input, memory)
                val req = Request.Builder()
                    .url(provider.baseUrl)
                    .addHeader("Authorization", "Bearer $key")
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build()

                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        if (resp.code == 401 || resp.code == 403 || resp.code == 429 || resp.code >= 500) {
                            continue
                        }
                        continue
                    }

                    val raw = resp.body?.string().orEmpty()
                    val content = extractContent(raw)
                    val plan = parsePlan(content)
                    if (plan != null) return plan.copy(providerName = provider.name)
                }
            } catch (_: Exception) {
                continue
            }
        }
        return null
    }

    private fun buildRequest(provider: Provider, input: String, memory: List<ChatMessage>): okhttp3.RequestBody {
        val messages = JSONArray()

        val system = JSONObject().apply {
            put("role", "system")
            put("content", loadSystemPrompt())
        }
        messages.put(system)

        memory.takeLast(8).forEach {
            messages.put(JSONObject().apply {
                put("role", if (it.role == "user") "user" else "assistant")
                put("content", it.text)
            })
        }

        messages.put(JSONObject().apply {
            put("role", "user")
            put("content", input)
        })

        val root = JSONObject().apply {
            put("model", provider.model)
            put("messages", messages)
            put("temperature", 0.2)
        }

        return root.toString().toRequestBody("application/json".toMediaType())
    }

    private fun extractContent(raw: String): String {
        val root = JSONObject(raw)
        val choices = root.optJSONArray("choices") ?: return raw
        if (choices.length() == 0) return raw
        val msg = choices.getJSONObject(0).optJSONObject("message") ?: return raw
        return msg.optString("content", raw)
    }

    private fun parsePlan(raw: String): AiPlan? {
        val cleaned = raw.trim().removePrefix("```json").removeSuffix("```").trim()
        return try {
            val obj = JSONObject(cleaned)
            val reply = obj.optString("reply", "")
            val actionsJson = obj.optJSONArray("actions") ?: JSONArray()
            val actions = buildList {
                for (i in 0 until actionsJson.length()) {
                    val item = actionsJson.getJSONObject(i)
                    val type = item.optString("type")
                    val payload = item.optJSONObject("payload") ?: JSONObject()
                    when (type) {
                        "open_app" -> add(CommandAction.OpenApp(payload.optString("name")))
                        "open_url" -> add(CommandAction.OpenUrl(payload.optString("url")))
                        "search_web" -> add(CommandAction.SearchWeb(payload.optString("query")))
                        "send_sms" -> add(CommandAction.SendSms(payload.optString("number"), payload.optString("message")))
                        "dial" -> add(CommandAction.Dial(payload.optString("number")))
                        "speak" -> add(CommandAction.Speak(payload.optString("text")))
                        "notify" -> add(CommandAction.Notify(payload.optString("title", "Jarvis"), payload.optString("text")))
                        "toast" -> add(CommandAction.Toast(payload.optString("text")))
                        "vibrate" -> add(CommandAction.Vibrate(payload.optLong("durationMs", 200L)))
                        "clipboard_set" -> add(CommandAction.ClipboardSet(payload.optString("text")))
                        "clipboard_get" -> add(CommandAction.ClipboardGet)
                        "open_settings" -> add(CommandAction.OpenSettings)
                        "open_termux" -> add(CommandAction.TermuxOpen)
                    }
                }
            }
            AiPlan(providerName = "unknown", reply = reply, actions = actions)
        } catch (_: Exception) {
            null
        }
    }

    private fun loadSystemPrompt(): String {
        return try {
            context.assets.open("system_prompt.txt").use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (_: Exception) {
            "You are Jarvis Phone. Return JSON only."
        }
    }
}

@Serializable
private data class ProviderFile(
    val name: String,
    @SerialName("baseUrl") val baseUrl: String,
    @SerialName("apiKey") val apiKey: String,
    val model: String,
    @SerialName("timeoutSec") val timeoutSec: Int = 45
)

private data class Provider(
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val timeoutSec: Int
) {
}

private class ProviderLoader(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): List<Provider> {
        val text = readAsset("providers.json")
            ?: readAsset("providers.json.example")
            ?: return emptyList()

        return try {
            val parsed = json.decodeFromString<List<ProviderFile>>(text)
            parsed.map {
                Provider(it.name, it.baseUrl, it.apiKey, it.model, it.timeoutSec)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun readAsset(name: String): String? {
        return try {
            context.assets.open(name).use(InputStream::readBytes).toString(Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }
}
