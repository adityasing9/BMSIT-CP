package com.continueo.autologin.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.continueo.autologin.R
import java.io.File
import java.io.FileOutputStream

object ShareUtils {

    const val APP_WEBSITE_URL = "https://bmsit-cp.vercel.app"
    const val DOWNLOAD_PAGE_URL = "https://bmsit-cp.vercel.app/download"
    const val GITHUB_RELEASES_URL = "https://github.com/adityasing9/BMSIT-CP/releases/latest"
    const val EXTENSION_URL = "https://bmsit-cp.vercel.app/extension"

    fun openWebsite(context: Context) {
        UpdateManager.openInBrowser(context, APP_WEBSITE_URL)
    }

    fun openExtensionPage(context: Context) {
        UpdateManager.openInBrowser(context, EXTENSION_URL)
    }

    fun getShareMessage(): String {
        return """
            🚀 Hey! Check out ContinueO Auto Login for BMSIT Student Portal.
            
            ✨ Key Highlights:
            • 1-Tap instant login & verification
            • Full in-app file downloads (notes, syllabus & marks)
            • Local-only AES-256 encrypted multi-account storage
            • Dark, Light & AMOLED themes
            
            📷 Scan the attached QR Code to download or tap below:
            $DOWNLOAD_PAGE_URL
            
            🌐 Official Website:
            $APP_WEBSITE_URL
            
            📦 GitHub Releases:
            $GITHUB_RELEASES_URL
            
            🧩 Desktop Chrome Extension:
            $EXTENSION_URL
        """.trimIndent()
    }

    /**
     * Extracts the branded QR Share Card to cacheDir and returns its FileProvider content URI.
     */
    fun getQrImageUri(context: Context): Uri? {
        return try {
            val sharesDir = File(context.cacheDir, "shares").apply { mkdirs() }
            val shareFile = File(sharesDir, "continueo_qr_download.png")

            // Always write fresh copy
            context.resources.openRawResource(R.drawable.continueo_share_card).use { input ->
                FileOutputStream(shareFile).use { output ->
                    input.copyTo(output)
                }
            }

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                shareFile
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Shares directly to WhatsApp (com.whatsapp or com.whatsapp.w4b) with QR code image attached.
     * Automatically falls back to standard system share chooser if WhatsApp is not installed.
     */
    fun shareViaWhatsApp(context: Context) {
        val shareMessage = getShareMessage()
        val qrUri = getQrImageUri(context)

        // Try standard WhatsApp
        if (launchWhatsAppIntent(context, "com.whatsapp", qrUri, shareMessage)) return

        // Try WhatsApp Business
        if (launchWhatsAppIntent(context, "com.whatsapp.w4b", qrUri, shareMessage)) return

        // Fall back to system share chooser
        Toast.makeText(context, "Opening share options...", Toast.LENGTH_SHORT).show()
        shareAppChooser(context)
    }

    private fun launchWhatsAppIntent(
        context: Context,
        packageName: String,
        qrUri: Uri?,
        shareMessage: String
    ): Boolean {
        return try {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                if (qrUri != null) {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, qrUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } else {
                    type = "text/plain"
                }
                putExtra(Intent.EXTRA_TEXT, shareMessage)
                setPackage(packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(sendIntent)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Standard Android system share sheet for sharing to any app (with QR image attached).
     */
    fun shareAppChooser(context: Context) {
        val shareMessage = getShareMessage()
        val qrUri = getQrImageUri(context)
        try {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                if (qrUri != null) {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, qrUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } else {
                    type = "text/plain"
                }
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
