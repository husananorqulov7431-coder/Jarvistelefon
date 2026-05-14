package com.example.jarvisphone.ai

import android.content.Context
import com.example.jarvisphone.core.AiPlan
import com.example.jarvisphone.core.ChatMessage
import com.example.jarvisphone.core.CommandAction
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AiPlanner(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val providers: List<Provider> = ProviderLoader(context).load()

    fun plan(input: String, memory: List<ChatMessage>): AiPlan? {
        for (provider in providers) {
            val key = provider.apiKey.trim()
            if (key.isBlank() || key.startsWith("PASTE_")) continue

            try {
                val req = Request.Builder()
                    .url(provider.baseUrl)
                    .addHeader("Authorization", "Bearer $key")
                    .addHeader("Content-Type", "application/json")
                    .post(buildRequest(provider, input, memory))
                    .build()

                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) continue

                    val raw = resp.body?.string().orEmpty()
                    val content = extractContent(raw)
                    val parsed = parsePlan(content)
                    if (parsed != null) return parsed.copy(providerName = provider.name)
                }
            } catch (_: Exception) {
                continue
            }
        }
        return null
    }

    private fun buildRequest(provider: Provider, input: String, memory: List<ChatMessage>): okhttp3.RequestBody {
        val messages = JSONArray()

        messages.put(JSONObject().apply {
            put("role", "system")
            put("content", loadSystemPrompt())
        })

        memory.takeLast(8).forEach { msg ->
            val role = if (msg.role == "user") "user" else "assistant"
            messages.put(JSONObject().apply {
                put("role", role)
                put("content", msg.text)
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
        return try {
            val root = JSONObject(raw)
            val choices = root.optJSONArray("choices") ?: return raw
            if (choices.length() == 0) return raw
            val message = choices.getJSONObject(0).optJSONObject("message") ?: return raw
            message.optString("content", raw)
        } catch (_: Exception) {
            raw
        }
    }

    private fun parsePlan(raw: String): AiPlan? {
        val cleaned = raw.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        return try {
            val obj = JSONObject(cleaned)
            val reply = obj.optString("reply", "")
            val actionsJson = obj.optJSONArray("actions") ?: JSONArray()
            val actions = mutableListOf<CommandAction>()

            for (i in 0 until actionsJson.length()) {
                val item = actionsJson.optJSONObject(i) ?: continue
                val type = item.optString("type")
                val payload = item.optJSONObject("payload") ?: JSONObject()

                when (type) {
                    "open_app" -> actions.add(CommandAction.OpenApp(payload.optString("name")))
                    "open_url" -> actions.add(CommandAction.OpenUrl(payload.optString("url")))
                    "search_web" -> actions.add(CommandAction.SearchWeb(payload.optString("query")))
                    "send_sms" -> actions.add(CommandAction.SendSms(payload.optString("number"), payload.optString("message")))
                    "dial" -> actions.add(CommandAction.Dial(payload.optString("number")))
                    "speak" -> actions.add(CommandAction.Speak(payload.optString("text")))
                    "notify" -> actions.add(CommandAction.Notify(payload.optString("title", "Jarvis"), payload.optString("text")))
                    "toast" -> actions.add(CommandAction.Toast(payload.optString("text")))
                    "vibrate" -> actions.add(CommandAction.Vibrate(payload.optLong("durationMs", 200L)))
                    "clipboard_set" -> actions.add(CommandAction.ClipboardSet(payload.optString("text")))
                    "clipboard_get" -> actions.add(CommandAction.ClipboardGet)
                    "open_settings" -> actions.add(CommandAction.OpenSettings)
                    "open_termux" -> actions.add(CommandAction.TermuxOpen)
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

private data class Provider(
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val timeoutSec: Int
)

private class ProviderLoader(private val context: Context) {

    fun load(): List<Provider> {
        val text = readAsset("providers.json")
            ?: readAsset("providers.json.example")
            ?: return emptyList()

        return try {
            val array = JSONArray(text)
            val result = mutableListOf<Provider>()

            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue

                result.add(
                    Provider(
                        name = item.optString("name"),
                        baseUrl = item.optString("baseUrl"),
                        apiKey = item.optString("apiKey"),
                        model = item.optString("model"),
                        timeoutSec = item.optInt("timeoutSec", 45)
                    )
                )
            }

            result.filter {
                it.name.isNotBlank() &&
                it.baseUrl.isNotBlank() &&
                it.model.isNotBlank()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun readAsset(name: String): String? {
        return try {
            context.assets.open(name).use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (_: Exception) {
            null
        }
    }
}
