package app.ammusic.data.library

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tracks")
data class TrackRow(
    @PrimaryKey val id: String,
    val title: String,
    val artists: String,
    val album: String?,
    val thumbnail: String?,
    val durationSeconds: Int?,
    val artistId: String?,
    val albumId: String?,
)

@Entity(tableName = "liked")
data class LikedRow(@PrimaryKey val trackId: String, val addedAt: Long, val local: Boolean)

@Entity(tableName = "history")
data class HistoryRow(@PrimaryKey val trackId: String, val playedAt: Long)

@Entity(tableName = "albums")
data class AlbumRow(
    @PrimaryKey val id: String,
    val playlistId: String,
    val title: String,
    val subtitle: String,
    val thumbnail: String?,
    val artistId: String?,
    val year: Int?,
    val addedAt: Long,
    val local: Boolean,
)

@Entity(tableName = "artists")
data class ArtistRow(@PrimaryKey val id: String, val name: String, val thumbnail: String?, val addedAt: Long, val local: Boolean)

@Entity(tableName = "playlists")
data class PlaylistRow(
    @PrimaryKey val id: String,
    val title: String,
    val subtitle: String,
    val thumbnail: String?,
    val addedAt: Long,
    val kind: Int,
)

object PlaylistKind {
    const val Local = 0

    const val Mine = 1

    const val Saved = 2
}

@Entity(tableName = "playlist_tracks", primaryKeys = ["playlistId", "trackId"])
data class PlaylistTrackRow(val playlistId: String, val trackId: String, val position: Long)

@Dao
abstract class LibraryDao {
    @Upsert
    abstract suspend fun upsertTracks(tracks: List<TrackRow>)

    @Query("SELECT tracks.* FROM liked JOIN tracks ON tracks.id = liked.trackId ORDER BY liked.addedAt DESC")
    abstract fun liked(): Flow<List<TrackRow>>

    @Query("SELECT trackId FROM liked")
    abstract fun likedIds(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertLiked(rows: List<LikedRow>)

    @Query("DELETE FROM liked WHERE trackId = :trackId")
    abstract suspend fun deleteLiked(trackId: String)

    @Query("DELETE FROM liked WHERE local = 0")
    abstract suspend fun clearRemoteLiked()

    @Transaction
    open suspend fun replaceRemoteLiked(tracks: List<TrackRow>, rows: List<LikedRow>) {
        clearRemoteLiked()
        upsertTracks(tracks)
        insertLiked(rows)
    }

    @Upsert
    abstract suspend fun upsertHistory(row: HistoryRow)

    @Query("SELECT tracks.* FROM history JOIN tracks ON tracks.id = history.trackId ORDER BY history.playedAt DESC LIMIT 200")
    abstract fun history(): Flow<List<TrackRow>>

    @Query("DELETE FROM history")
    abstract suspend fun clearHistory()

    @Query("SELECT * FROM albums ORDER BY addedAt DESC")
    abstract fun albums(): Flow<List<AlbumRow>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertAlbums(rows: List<AlbumRow>)

    @Query("DELETE FROM albums WHERE id = :id")
    abstract suspend fun deleteAlbum(id: String)

    @Query("DELETE FROM albums WHERE local = 0")
    abstract suspend fun clearRemoteAlbums()

    @Transaction
    open suspend fun replaceRemoteAlbums(rows: List<AlbumRow>) {
        clearRemoteAlbums()
        insertAlbums(rows)
    }

    @Query("SELECT * FROM artists ORDER BY addedAt DESC")
    abstract fun artists(): Flow<List<ArtistRow>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertArtists(rows: List<ArtistRow>)

    @Query("DELETE FROM artists WHERE id = :id")
    abstract suspend fun deleteArtist(id: String)

    @Query("DELETE FROM artists WHERE local = 0")
    abstract suspend fun clearRemoteArtists()

    @Transaction
    open suspend fun replaceRemoteArtists(rows: List<ArtistRow>) {
        clearRemoteArtists()
        insertArtists(rows)
    }

    @Query("SELECT * FROM playlists ORDER BY addedAt DESC")
    abstract fun playlists(): Flow<List<PlaylistRow>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    abstract fun playlist(id: String): Flow<PlaylistRow?>

    @Upsert
    abstract suspend fun upsertPlaylists(rows: List<PlaylistRow>)

    @Query("UPDATE playlists SET title = :title WHERE id = :id")
    abstract suspend fun renamePlaylist(id: String, title: String)

    @Query("UPDATE playlists SET thumbnail = :thumbnail WHERE id = :id")
    abstract suspend fun setPlaylistThumbnail(id: String, thumbnail: String?)

    @Query("DELETE FROM playlists WHERE id = :id")
    abstract suspend fun deletePlaylistRow(id: String)

    @Query("DELETE FROM playlists WHERE kind != 0")
    abstract suspend fun clearRemotePlaylists()

    @Transaction
    open suspend fun replaceRemotePlaylists(rows: List<PlaylistRow>) {
        clearRemotePlaylists()
        upsertPlaylists(rows)
    }

    @Query("SELECT tracks.* FROM playlist_tracks JOIN tracks ON tracks.id = playlist_tracks.trackId WHERE playlistId = :playlistId ORDER BY position")
    abstract fun playlistTracks(playlistId: String): Flow<List<TrackRow>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertPlaylistTrack(row: PlaylistTrackRow)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    abstract suspend fun deletePlaylistTrack(playlistId: String, trackId: String)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    abstract suspend fun clearPlaylistTracks(playlistId: String)

    @Query("SELECT tracks.thumbnail FROM playlist_tracks JOIN tracks ON tracks.id = playlist_tracks.trackId WHERE playlistId = :playlistId ORDER BY position LIMIT 1")
    abstract suspend fun firstThumbnail(playlistId: String): String?

    @Transaction
    open suspend fun deletePlaylist(id: String) {
        clearPlaylistTracks(id)
        deletePlaylistRow(id)
    }
}

@Database(
    entities = [TrackRow::class, LikedRow::class, HistoryRow::class, AlbumRow::class, ArtistRow::class, PlaylistRow::class, PlaylistTrackRow::class],
    version = 1,
    exportSchema = false,
)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun dao(): LibraryDao
}
