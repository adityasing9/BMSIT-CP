package com.continueo.autologin.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object ShareUtils {

    const val APP_WEBSITE_URL = "https://bmsit-cp.vercel.app"
    const val GITHUB_RELEASES_URL = "https://github.com/adityasing9/BMSIT-CP/releases/latest"
    const val EXTENSION_URL = "https://github.com/adityasing9/CP-Ext"

    fun getShareMessage(): String {
        return """
            🚀 Hey! Check out ContinueO Auto Login for BMSIT Student Portal.
            
            ✨ Features:
            • One-tap instant portal login & CAPTCHA auto-fill
            • In-app file downloads & attendance viewer
            • Multi-account support with military-grade Keystore AES-256 encryption
            • Dark, Light & AMOLED themes
            
            📲 Download Android App:
            $APP_WEBSITE_URL
            
            📦 GitHub Releases (Direct APK):
            $GITHUB_RELEASES_URL
            
            🧩 Chrome Extension:
            $EXTENSION_URL
        """.trimIndent()
    }

    /**
     * Shares directly to WhatsApp (com.whatsapp or com.whatsapp.w4b).
     * Automatically falls back to standard system share chooser if WhatsApp is not installed.
     */
    fun shareViaWhatsApp(context: Context) {
        val shareMessage = getShareMessage()
        try {
            // Check for standard WhatsApp
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareMessage)
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(sendIntent)
        } catch (_: Exception) {
            // Try WhatsApp Business
            try {
                val businessIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareMessage)
                    setPackage("com.whatsapp.w4b")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(businessIntent)
            } catch (_: Exception) {
                // Try via wa.me intent URL
                try {
                    val encoded = Uri.encode(shareMessage)
                    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?text=$encoded")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(webIntent)
                } catch (_: Exception) {
                    // Fall back to system share chooser
                    Toast.makeText(context, "WhatsApp not found. Opening share options...", Toast.LENGTH_SHORT).show()
                    shareAppChooser(context)
                }
            }
        }
    }

    /**
     * Standard Android system share sheet for sharing to any app.
     */
    fun shareAppChooser(context: Context) {
        val shareMessage = getShareMessage()
        try {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "ContinueO Auto Login for BMSIT")
                putExtra(Intent.EXTRA_TEXT, shareMessage)
            }
            val chooser = Intent.createChooser(sendIntent, "Share ContinueO with Friends").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open share sheet: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
