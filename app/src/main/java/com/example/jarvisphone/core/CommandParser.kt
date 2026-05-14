package com.example.jarvisphone.core

import android.content.Context
import java.util.Locale

class CommandParser(private val context: Context) {

    fun parse(input: String): CommandAction? {
        val t = input.trim().lowercase(Locale.ROOT)

        if (t.isBlank()) return null

        if (t in setOf("exit", "quit", "stop", "chiq")) {
            return CommandAction.Speak("To'xtatildi")
        }

        when {
            t == "settings och" || t == "sozlamani och" -> return CommandAction.OpenSettings
            t == "termux och" -> return CommandAction.TermuxOpen
            t == "clipboard get" -> return CommandAction.ClipboardGet
            t.startsWith("clipboard set ") -> return CommandAction.ClipboardSet(input.substringAfter("clipboard set ").trim())
            t == "battery" -> return CommandAction.Speak("Battery info uchun keyingi bosqichda action qo'shiladi")
            t.startsWith("notify ") -> return CommandAction.Notify("Jarvis", input.substringAfter("notify ").trim())
            t.startsWith("toast ") -> return CommandAction.Toast(input.substringAfter("toast ").trim())
            t.startsWith("vibrate") -> return CommandAction.Vibrate()
        }

        val openMatch = Regex("""^(?:open|och)\s+(.+)$""", RegexOption.IGNORE_CASE).find(input)
        if (openMatch != null) {
            return CommandAction.OpenApp(openMatch.groupValues[1].trim())
        }

        val searchMatch = Regex("""^(?:qidir|search)\s+(.+)$""", RegexOption.IGNORE_CASE).find(input)
        if (searchMatch != null) {
            return CommandAction.SearchWeb(searchMatch.groupValues[1].trim())
        }

        val smsMatch = Regex("""^(?:sms)\s+(\+?\d[\d\s-]{6,}\d)\s+(.+)$""", RegexOption.IGNORE_CASE).find(input)
        if (smsMatch != null) {
            return CommandAction.SendSms(
                number = smsMatch.groupValues[1].replace(" ", "").replace("-", ""),
                message = smsMatch.groupValues[2].trim()
            )
        }

        val callMatch = Regex("""^(?:call|qongiroq)\s+(\+?\d[\d\s-]{6,}\d)$""", RegexOption.IGNORE_CASE).find(input)
        if (callMatch != null) {
            return CommandAction.Dial(callMatch.groupValues[1].replace(" ", "").replace("-", ""))
        }

        return null
    }
}
