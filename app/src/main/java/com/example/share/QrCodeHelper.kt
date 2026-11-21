package com.example.share

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import javax.inject.Inject
import javax.inject.Singleton

data class PairingPayload(
    val deviceId: String,
    val displayName: String,
    val hostAddress: String,
    val port: Int,
    val pairingPin: String,
    val sessionKeyHex: String
)

@Singleton
class QrCodeHelper @Inject constructor() {

    fun encodePayloadUri(payload: PairingPayload): String {
        return Uri.Builder()
            .scheme("aura-share")
            .authority("pair")
            .appendQueryParameter("id", payload.deviceId)
            .appendQueryParameter("name", payload.displayName)
            .appendQueryParameter("host", payload.hostAddress)
            .appendQueryParameter("port", payload.port.toString())
            .appendQueryParameter("pin", payload.pairingPin)
            .appendQueryParameter("key", payload.sessionKeyHex)
            .build()
            .toString()
    }

    fun parsePayloadUri(raw: String): PairingPayload? {
        return runCatching {
            val uri = Uri.parse(raw.trim())
            if (uri.scheme != "aura-share" || uri.host != "pair") return null
            val id = uri.getQueryParameter("id")?.takeIf { it.isNotBlank() } ?: return null
            val name = uri.getQueryParameter("name")?.takeIf { it.isNotBlank() } ?: "Aura Peer"
            val host = uri.getQueryParameter("host")?.takeIf { it.isNotBlank() } ?: return null
            val port = uri.getQueryParameter("port")?.toIntOrNull()?.takeIf { it in 1024..65535 } ?: return null
            val pin = uri.getQueryParameter("pin")?.takeIf { it.length >= 4 } ?: return null
            val key = uri.getQueryParameter("key")?.takeIf { it.length == 64 } ?: return null
            PairingPayload(
                deviceId = id,
                displayName = name,
                hostAddress = host,
                port = port,
                pairingPin = pin,
                sessionKeyHex = key
            )
        }.getOrNull()
    }

    fun generateQrBitmap(content: String, sizePx: Int = 512): Bitmap? {
        return runCatching {
            val hints = mapOf(EncodeHintType.MARGIN to 1)
            val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val pixels = IntArray(sizePx * sizePx)
            for (y in 0 until sizePx) {
                val offset = y * sizePx
                for (x in 0 until sizePx) {
                    pixels[offset + x] = if (matrix[x, y]) Color.parseColor("#070A12") else Color.WHITE
                }
            }
            Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
            }
        }.getOrNull()
    }
}
