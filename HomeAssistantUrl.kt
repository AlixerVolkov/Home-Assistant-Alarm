package dev.homepanel.app.network

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object HomeAssistantUrl {
    fun normalizeBaseUrl(raw: String): HttpUrl {
        val trimmed = raw.trim().trimEnd('/')
        require(trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            "URL must start with http:// or https://"
        }
        return trimmed.toHttpUrlOrNull() ?: error("Invalid Home Assistant URL")
    }

    fun restUrl(rawBaseUrl: String, path: String): HttpUrl {
        val base = normalizeBaseUrl(rawBaseUrl)
        return base.newBuilder()
            .addPathSegments(path.trimStart('/'))
            .build()
    }

    fun webSocketUrl(rawBaseUrl: String): String {
        val httpUrl = restUrl(rawBaseUrl, "api/websocket").toString()
        return when {
            httpUrl.startsWith("https://") -> "wss://" + httpUrl.removePrefix("https://")
            else -> "ws://" + httpUrl.removePrefix("http://")
        }
    }
}
