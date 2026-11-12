package com.anyelk.led.data.music

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Track(
    val uri: Uri,
    val title: String,
    val artist: String?,
    val displayName: String
)

/**
 * Walks a SAF directory tree (ACTION_OPEN_DOCUMENT_TREE) and collects audio files.
 */
class MusicRepository(private val context: Context) {

    private val audioExtensions = setOf(
        "mp3", "flac", "wav", "m4a", "aac", "ogg", "oga", "opus", "wma", "ape", "aiff", "mka"
    )

    suspend fun scanTree(treeUri: Uri): List<Track> = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext emptyList()
        val out = ArrayList<Track>()
        collect(root, out, depth = 0)
        out.sortedBy { it.title.lowercase() }
    }

    private fun collect(dir: DocumentFile, out: MutableList<Track>, depth: Int) {
        if (depth > MAX_DEPTH) return
        val children = dir.listFiles()
        for (child in children) {
            if (child.isDirectory) {
                collect(child, out, depth + 1)
            } else if (child.isFile) {
                val name = child.name ?: continue
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext !in audioExtensions) continue
                val base = name.substringBeforeLast('.')
                val split = base.split(" - ", limit = 2)
                val track = if (split.size == 2) {
                    Track(
                        uri = child.uri,
                        title = split[1].trim(),
                        artist = split[0].trim().ifBlank { null },
                        displayName = name
                    )
                } else {
                    Track(uri = child.uri, title = base, artist = null, displayName = name)
                }
                out += track
            }
        }
    }

    fun takePersistablePermission(treeUri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // Already taken or not offered by the provider.
        }
    }

    companion object {
        private const val MAX_DEPTH = 8
    }
}
