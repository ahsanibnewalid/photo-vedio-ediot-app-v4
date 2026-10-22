package com.example.photovideoeditor.engine

import android.content.Context
import android.net.Uri

data class StoredExportRequest(val uri: Uri, val startMs: Long, val endMs: Long)

class ExportRequestStore(context: Context) {
    private val prefs = context.getSharedPreferences("shuvo_export_request", Context.MODE_PRIVATE)
    fun save(request: StoredExportRequest) {
        require(request.startMs >= 0L && request.endMs > request.startMs)
        check(prefs.edit().putString("uri", request.uri.toString())
            .putLong("start", request.startMs).putLong("end", request.endMs).commit())
    }
    fun load(): StoredExportRequest? {
        val uri = prefs.getString("uri", null) ?: return null
        val start = prefs.getLong("start", 0L)
        val end = prefs.getLong("end", 0L)
        return if (start >= 0L && end > start) StoredExportRequest(Uri.parse(uri), start, end) else null
    }
    fun clear() { prefs.edit().clear().apply() }
}
