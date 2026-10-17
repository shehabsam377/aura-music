package com.aura.music

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject

/** Queries MediaStore.Audio.Media. Stable id = "ms:<MediaStore _ID>". */
object MediaScanner {
    fun scan(ctx: Context): JSArray {
        val base = if (Build.VERSION.SDK_INT >= 29)
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val proj = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED, MediaStore.Audio.Media.DISPLAY_NAME
        )
        val sel = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 0"
        val seen = HashSet<Long>()
        val out = JSArray()
        val cur = ctx.contentResolver.query(base, proj, sel, null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")
            ?: throw IllegalStateException("MediaStore returned no cursor")
        cur.use { c ->
            val iId = c.getColumnIndexOrThrow(proj[0]); val iT = c.getColumnIndexOrThrow(proj[1])
            val iAr = c.getColumnIndexOrThrow(proj[2]); val iAl = c.getColumnIndexOrThrow(proj[3])
            val iAid = c.getColumnIndexOrThrow(proj[4]); val iD = c.getColumnIndexOrThrow(proj[5])
            val iAdd = c.getColumnIndexOrThrow(proj[6]); val iN = c.getColumnIndexOrThrow(proj[7])
            while (c.moveToNext()) {
                val id = c.getLong(iId)
                if (!seen.add(id)) continue // duplicate guard
                val artist = c.getString(iAr)?.takeIf { it != "<unknown>" } ?: ""
                val album = c.getString(iAl)?.takeIf { it != "<unknown>" } ?: ""
                val title = c.getString(iT)?.takeIf { it.isNotBlank() }
                    ?: c.getString(iN)?.substringBeforeLast('.') ?: "Unknown"
                out.put(JSObject().apply {
                    put("id", "ms:$id")
                    put("uri", ContentUris.withAppendedId(base, id).toString())
                    put("title", title); put("artist", artist); put("album", album)
                    put("albumId", c.getLong(iAid)); put("duration", c.getLong(iD))
                    put("added", c.getLong(iAdd))
                })
            }
        }
        return out
    }
}
