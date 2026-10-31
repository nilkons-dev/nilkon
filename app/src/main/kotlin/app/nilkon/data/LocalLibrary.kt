package app.nilkon.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore

object LocalLibrary {
    fun scan(ctx: Context): List<Song> {
        val out = ArrayList<Song>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val proj = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.ALBUM_ID,
        )
        val sel = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 15000"
        runCatching {
            ctx.contentResolver.query(uri, proj, sel, null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val iT = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val iA = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val iAl = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val iD = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val iAi = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                while (c.moveToNext()) {
                    val id = c.getLong(iId)
                    val key = "local:$id"
                    if (key in Store.hidden) continue
                    val artist = c.getString(iA)?.takeIf { it != "<unknown>" } ?: "Unknown artist"
                    out += Song(
                        key, Store.titles[key] ?: (c.getString(iT) ?: "Unknown"), artist, c.getString(iAl) ?: "",
                        c.getLong(iD), ContentUris.withAppendedId(uri, id).toString(),
                        "content://media/external/audio/albumart/${c.getLong(iAi)}",
                    )
                }
            }
        }
        return out
    }
}
