package com.sustain.step.data.repo.audio

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.File

class AudioLoader(private val context: Context) {

    fun loadAllMusicFiles(): List<AudioItem> {
        val musicFiles = mutableListOf<AudioItem>()
        val seen = mutableSetOf<Pair<String, Long>>()
        val collectionUri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED
        ) + if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(MediaStore.Audio.Media.RELATIVE_PATH)
        } else {
            arrayOf(MediaStore.Audio.Media.DATA)
        }

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val selectionArgs = null

        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        val contentResolver = context.contentResolver
        contentResolver.query(
            collectionUri,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->

            // Indices
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val pathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
            } else {
                cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
            }

            // Iterate results
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val displayName = cursor.getString(nameCol) ?: "Unknown"
                val artistName = cursor.getString(artistCol) ?: "Unknown"
                val titleName = cursor.getString(titleCol) ?: "Unknown"
                val durationMs = cursor.getLong(durationCol)
                val dateAddedSeconds = cursor.getLong(dateAddedCol)
                val folderPath = if (pathCol >= 0) cursor.getString(pathCol).orEmpty() else ""
                val folderName = extractFolderName(
                    path = folderPath
                )

                // Build a content Uri for this audio item
                val contentUri = ContentUris.withAppendedId(collectionUri, id)

                // Check duplicates with (title, duration)
                val key = displayName to durationMs
                if (!seen.contains(key)) {
                    seen.add(key)
                    val musicItem = AudioItem(
                        name = displayName,
                        artist = artistName,
                        title = titleName,
                        uri = contentUri,
                        duration = durationMs,
                        folderName = folderName,
                        folderPath = folderPath,
                        dateAddedSeconds = dateAddedSeconds
                    )
                    musicFiles.add(musicItem)
                }
            }
        }

        return musicFiles
    }

    private fun extractFolderName(path: String): String {
        if (path.isBlank()) return "Music"
        val normalized = path.trimEnd('/')
        if (normalized.isBlank()) return "Music"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            normalized.split('/').lastOrNull { it.isNotBlank() } ?: "Music"
        } else {
            File(normalized).parentFile?.name ?: "Music"
        }
    }
}

data class AudioItem(
    val name: String,
    val artist: String,
    val title: String,
    val uri: Uri,
    val duration: Long,
    val folderName: String,
    val folderPath: String,
    val dateAddedSeconds: Long
)