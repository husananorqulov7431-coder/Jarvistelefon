package com.example.jarvisphone.core

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import java.util.Locale

class AppLauncher(private val context: Context) {

    fun openApp(name: String): Boolean {
        val target = name.trim().lowercase(Locale.ROOT)

        val resolved = findInstalledApp(target)
        if (resolved != null) {
            context.startActivity(resolved.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return true
        }

        return when (target) {
            "settings", "sozlamalar" -> openSettings()
            "chrome", "browser" -> openUrl("https://www.google.com")
            "youtube" -> openUrl("https://m.youtube.com")
            "telegram" -> openUrl("https://t.me/")
            "whatsapp" -> openUrl("https://wa.me/")
            else -> false
        }
    }

    fun openSettings(): Boolean {
        return try {
            val intent = Intent(android.provider.Settings.ACTION_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun openTermux(): Boolean {
        return openApp("com.termux")
    }

    fun openUrl(url: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun searchWeb(query: String): Boolean {
        val url = "https://www.google.com/search?q=" + Uri.encode(query)
        return openUrl(url)
    }

    fun dial(number: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun sendSms(number: String, message: String): Boolean {
        return try {
            val smsUri = Uri.parse("smsto:$number")
            val intent = Intent(Intent.ACTION_SENDTO, smsUri)
            intent.putExtra("sms_body", message)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun notify(title: String, text: String) {
        Toast.makeText(context, "$title: $text", Toast.LENGTH_LONG).show()
    }

    fun toast(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    fun vibrate(durationMs: Long = 200L) {
        try {
            val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(durationMs)
            }
        } catch (_: Exception) {
        }
    }

    fun setClipboard(text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("jarvis", text))
    }

    fun getClipboard(): String {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip ?: return ""
        if (clip.itemCount <= 0) return ""
        return clip.getItemAt(0).coerceToText(context)?.toString().orEmpty()
    }

    private fun findInstalledApp(query: String): Intent? {
        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(query)
        if (launchIntent != null) return launchIntent

        val main = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(main, PackageManager.MATCH_ALL)

        val match = apps.firstOrNull { info ->
            val label = info.loadLabel(pm)?.toString().orEmpty().lowercase(Locale.ROOT)
            val pkg = info.activityInfo.packageName.lowercase(Locale.ROOT)
            label.contains(query) || pkg.contains(query)
        } ?: return null

        return pm.getLaunchIntentForPackage(match.activityInfo.packageName)
    }
}
