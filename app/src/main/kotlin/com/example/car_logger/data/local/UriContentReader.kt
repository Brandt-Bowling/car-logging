package com.example.car_logger.data.local

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.util.Locale

interface UriContentReader {
    fun resolveFileName(uri: Uri): String?
    fun resolveMimeType(uri: Uri, fileName: String): String
    fun readBytes(uri: Uri): ByteArray?
}

class AndroidUriContentReader(context: Context) : UriContentReader {
    private val appContext: Context = context.applicationContext ?: context

    override fun resolveFileName(uri: Uri): String? {
        val resolver = appContext.contentResolver
        return resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        } ?: uri.lastPathSegment
    }

    override fun resolveMimeType(uri: Uri, fileName: String): String =
        resolveMimeType(
            reportedMimeType = appContext.contentResolver.getType(uri),
            fileName = fileName
        )

    override fun readBytes(uri: Uri): ByteArray? =
        appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }

    companion object {
        const val DEFAULT_MIME_TYPE = "application/octet-stream"

        fun resolveMimeType(reportedMimeType: String?, fileName: String): String {
            val trimmedReported = reportedMimeType?.trim()
            if (!trimmedReported.isNullOrEmpty() &&
                !trimmedReported.equals(DEFAULT_MIME_TYPE, ignoreCase = true)
            ) {
                return trimmedReported
            }
            return inferMimeTypeFromFileName(fileName)
        }

        fun inferMimeTypeFromFileName(fileName: String): String {
            val normalized = fileName
                .trim()
                .substringBefore('?')
                .substringBefore('#')
                .trim()
                .lowercase(Locale.US)
            return when {
                normalized.endsWith(".pdf") -> "application/pdf"
                normalized.endsWith(".csv") -> "text/csv"
                normalized.endsWith(".png") -> "image/png"
                normalized.endsWith(".webp") -> "image/webp"
                normalized.endsWith(".jpg") || normalized.endsWith(".jpeg") -> "image/jpeg"
                else -> DEFAULT_MIME_TYPE
            }
        }
    }
}
