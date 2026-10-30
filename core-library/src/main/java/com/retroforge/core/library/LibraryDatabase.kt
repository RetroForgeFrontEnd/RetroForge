package com.retroforge.core.library

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.OnConflictStrategy
import androidx.room.Update
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** A folder the user granted access to. RetroForge stores only this reference, never copies of the files. */
@Entity(tableName = "roots", indices = [Index(value = ["treeUri"], unique = true)])
data class RootEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val treeUri: String,
    val name: String,
    val addedAt: Long,
    val lastScanAt: Long? = null,
    val layout: String? = null,
    val gameCount: Int = 0,
    val problemCount: Int = 0,
    /** Folders that didn't match a known system, newline-separated. */
    val unrecognisedFolders: String = "",
)

@Entity(
    tableName = "games",
    foreignKeys = [ForeignKey(entity = RootEntity::class, parentColumns = ["id"], childColumns = ["rootId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("rootId"), Index("system"), Index("isHack"), Index("sortKey")],
)
data class GameEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rootId: Long,
    val system: String,
    val relativePath: String,
    val fileName: String,
    val documentId: String,
    val title: String,
    val sortKey: String,
    val tags: String,
    val regions: String,
    val isHack: Boolean,
    val hackFolder: String?,
    val sizeBytes: Long,
    val modifiedAt: Long,
    val duplicateKey: String?,
    val discs: Int?,
    val description: String?,
    val releaseDate: String?,
    val developer: String?,
    val publisher: String?,
    val genre: String?,
    val players: String?,
    val rating: Float?,
    val imageDocumentId: String?,
)

@Entity(
    tableName = "scan_problems",
    foreignKeys = [ForeignKey(entity = RootEntity::class, parentColumns = ["id"], childColumns = ["rootId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("rootId")],
)
data class ProblemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rootId: Long,
    val path: String,
    val message: String,
)

/** Keyed by folder + path, not game id, so history survives rescans (which recreate game rows). */
@Entity(
    tableName = "play_history",
    primaryKeys = ["rootId", "relativePath"],
    foreignKeys = [ForeignKey(entity = RootEntity::class, parentColumns = ["id"], childColumns = ["rootId"], onDelete = ForeignKey.CASCADE)],
)
data class PlayEntity(
    val rootId: Long,
    val relativePath: String,
    val lastPlayedAt: Long,
    val playCount: Int,
    val core: String?,
)

/** A cover the user chose, saved in RetroForge's own storage (never in the ROM folder). Keyed like play history. */
@Entity(
    tableName = "covers",
    primaryKeys = ["rootId", "relativePath"],
    foreignKeys = [ForeignKey(entity = RootEntity::class, parentColumns = ["id"], childColumns = ["rootId"], onDelete = ForeignKey.CASCADE)],
)
data class CoverEntity(
    val rootId: Long,
    val relativePath: String,
    /** File name inside the app's covers folder. */
    val file: String,
    /** "libretro" or "steamgriddb". */
    val source: String,
    val sourceUrl: String,
    val author: String?,
    val savedAt: Long,
)

/** The details a game can have edited by hand. */
data class GameDetails(
    val title: String,
    val description: String?,
    val releaseDate: String?,
    val developer: String?,
    val publisher: String?,
    val genre: String?,
    val players: String?,
)

fun GameEntity.details() = GameDetails(title, description, releaseDate, developer, publisher, genre, players)

fun GameEntity.withDetails(d: GameDetails) = copy(
    title = d.title, sortKey = com.retroforge.library.RomNames.sortKey(d.title), description = d.description,
    releaseDate = d.releaseDate, developer = d.developer, publisher = d.publisher, genre = d.genre, players = d.players,
)

/**
 * A game's details as edited by hand, keyed by folder + path so the edit survives rescans (each scan puts it back)
 * and follows the file through ROM manager changes. [scanned] keeps what the scan found, for "Reset".
 */
@Entity(
    tableName = "game_edits",
    primaryKeys = ["rootId", "relativePath"],
    foreignKeys = [ForeignKey(entity = RootEntity::class, parentColumns = ["id"], childColumns = ["rootId"], onDelete = ForeignKey.CASCADE)],
)
data class GameEditEntity(
    val rootId: Long,
    val relativePath: String,
    @androidx.room.Embedded(prefix = "edited_") val edited: GameDetails,
    @androidx.room.Embedded(prefix = "scanned_") val scanned: GameDetails,
    val editedAt: Long,
)

/** A favourite game, keyed by folder + path so it survives rescans. */
@Entity(
    tableName = "favourites",
    primaryKeys = ["rootId", "relativePath"],
    foreignKeys = [ForeignKey(entity = RootEntity::class, parentColumns = ["id"], childColumns = ["rootId"], onDelete = ForeignKey.CASCADE)],
)
data class FavouriteEntity(val rootId: Long, val relativePath: String, val addedAt: Long)

/** A playlist (collection) the user made. */
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

/** A game in a playlist, in the user's order. Keyed by folder + path like favourites. */
@Entity(
    tableName = "playlist_items",
    primaryKeys = ["playlistId", "rootId", "relativePath"],
    foreignKeys = [
        ForeignKey(entity = PlaylistEntity::class, parentColumns = ["id"], childColumns = ["playlistId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = RootEntity::class, parentColumns = ["id"], childColumns = ["rootId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("rootId")],
)
data class PlaylistItemEntity(val playlistId: Long, val rootId: Long, val relativePath: String, val position: Int, val addedAt: Long)

data class PlaylistSummary(val id: Long, val name: String, val games: Int)

data class SystemCount(val system: String, val games: Int, val hacks: Int)

@Dao
interface LibraryDao {
    @Query("SELECT * FROM roots ORDER BY addedAt")
    fun roots(): Flow<List<RootEntity>>

    @Query("SELECT * FROM roots WHERE id = :id")
    suspend fun root(id: Long): RootEntity?

    @Query("SELECT * FROM roots WHERE treeUri = :uri")
    suspend fun rootByUri(uri: String): RootEntity?

    @Insert
    suspend fun insertRoot(root: RootEntity): Long

    @Query("UPDATE roots SET name = :name WHERE id = :id")
    suspend fun renameRoot(id: Long, name: String)

    @Query("DELETE FROM roots WHERE id = :id")
    suspend fun deleteRoot(id: Long)

    @Query("SELECT system, COUNT(*) AS games, SUM(isHack) AS hacks FROM games GROUP BY system")
    fun systemCounts(): Flow<List<SystemCount>>

    @Query("SELECT * FROM games WHERE system = :system ORDER BY sortKey, fileName")
    fun gamesFor(system: String): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE rootId = :rootId")
    suspend fun gamesIn(rootId: Long): List<GameEntity>

    @Query("SELECT * FROM games WHERE isHack = 1 ORDER BY system, hackFolder, sortKey")
    fun hacks(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE id = :id")
    fun game(id: Long): Flow<GameEntity?>

    @Query("SELECT id FROM games WHERE isHack = 0 ORDER BY RANDOM() LIMIT 1")
    suspend fun randomGameId(): Long?

    @Query("SELECT id FROM games WHERE system = :system AND fileName = :fileName LIMIT 1")
    suspend fun gameIdFor(system: String, fileName: String): Long?

    @Query("SELECT * FROM games WHERE duplicateKey = :key AND id != :excludeId")
    suspend fun duplicatesOf(key: String, excludeId: Long): List<GameEntity>

    // Whole-title, then title-start, then word-start matches first, so a short word ("rez") still finds its game among
    // the hundreds of titles that merely contain it before the limit cuts the list.
    @Query(
        "SELECT * FROM games WHERE title LIKE '%' || :query || '%' ESCAPE '\\' ORDER BY CASE WHEN title LIKE :query ESCAPE '\\' THEN 0 WHEN title LIKE :query || '%' ESCAPE '\\' THEN 1 WHEN title LIKE '% ' || :query || '%' ESCAPE '\\' THEN 2 ELSE 3 END, sortKey LIMIT 300",
    )
    fun search(query: String): Flow<List<GameEntity>>

    @Query("SELECT * FROM scan_problems WHERE rootId = :rootId")
    fun problems(rootId: Long): Flow<List<ProblemEntity>>

    @Query("SELECT * FROM play_history WHERE rootId = :rootId AND relativePath = :path")
    suspend fun play(rootId: Long, path: String): PlayEntity?

    @androidx.room.Upsert
    suspend fun upsertPlay(play: PlayEntity)

    @Transaction
    suspend fun recordPlay(rootId: Long, path: String, core: String, at: Long) {
        val previous = play(rootId, path)
        upsertPlay(PlayEntity(rootId, path, at, (previous?.playCount ?: 0) + 1, core))
    }

    @Query(
        "SELECT games.* FROM games JOIN play_history ON games.rootId = play_history.rootId AND games.relativePath = play_history.relativePath ORDER BY play_history.lastPlayedAt DESC LIMIT :limit",
    )
    fun recentlyPlayed(limit: Int): Flow<List<GameEntity>>

    @Query("SELECT play_history.* FROM play_history JOIN games ON games.rootId = play_history.rootId AND games.relativePath = play_history.relativePath WHERE games.id = :gameId")
    fun playFor(gameId: Long): Flow<PlayEntity?>

    @Query("SELECT * FROM covers")
    fun covers(): Flow<List<CoverEntity>>

    @Query("SELECT * FROM covers WHERE rootId = :rootId AND relativePath = :path")
    suspend fun cover(rootId: Long, path: String): CoverEntity?

    @androidx.room.Upsert
    suspend fun upsertCover(cover: CoverEntity)

    @Query("DELETE FROM covers WHERE rootId = :rootId AND relativePath = :path")
    suspend fun deleteCover(rootId: Long, path: String)

    @Insert
    suspend fun insertGames(games: List<GameEntity>)

    @Insert
    suspend fun insertProblems(problems: List<ProblemEntity>)

    @Query("DELETE FROM games WHERE rootId = :rootId")
    suspend fun deleteGames(rootId: Long)

    @Query("DELETE FROM scan_problems WHERE rootId = :rootId")
    suspend fun deleteProblems(rootId: Long)

    @Query(
        "UPDATE roots SET lastScanAt = :at, layout = :layout, gameCount = :games, problemCount = :problems, unrecognisedFolders = :unrecognised WHERE id = :rootId",
    )
    suspend fun updateScan(rootId: Long, at: Long, layout: String, games: Int, problems: Int, unrecognised: String)

    @Query(
        "SELECT games.* FROM games JOIN favourites ON games.rootId = favourites.rootId AND games.relativePath = favourites.relativePath ORDER BY favourites.addedAt DESC",
    )
    fun favouriteGames(): Flow<List<GameEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favourites WHERE rootId = :rootId AND relativePath = :path)")
    fun isFavourite(rootId: Long, path: String): Flow<Boolean>

    @androidx.room.Upsert
    suspend fun addFavourite(favourite: FavouriteEntity)

    @Query("DELETE FROM favourites WHERE rootId = :rootId AND relativePath = :path")
    suspend fun removeFavourite(rootId: Long, path: String)

    @Query(
        "SELECT playlists.id AS id, playlists.name AS name, COUNT(games.id) AS games FROM playlists LEFT JOIN playlist_items ON playlist_items.playlistId = playlists.id LEFT JOIN games ON games.rootId = playlist_items.rootId AND games.relativePath = playlist_items.relativePath GROUP BY playlists.id ORDER BY playlists.name COLLATE NOCASE",
    )
    fun playlists(): Flow<List<PlaylistSummary>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun playlist(id: Long): Flow<PlaylistEntity?>

    @Query(
        "SELECT games.* FROM games JOIN playlist_items ON games.rootId = playlist_items.rootId AND games.relativePath = playlist_items.relativePath WHERE playlist_items.playlistId = :id ORDER BY playlist_items.position",
    )
    fun playlistGames(id: Long): Flow<List<GameEntity>>

    @Query("SELECT playlistId FROM playlist_items WHERE rootId = :rootId AND relativePath = :path")
    fun playlistsContaining(rootId: Long, path: String): Flow<List<Long>>

    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun renamePlaylist(id: Long, name: String)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_items WHERE playlistId = :id")
    suspend fun nextPosition(id: Long): Int

    @androidx.room.Upsert
    suspend fun upsertPlaylistItem(item: PlaylistItemEntity)

    @Query("DELETE FROM playlist_items WHERE playlistId = :id AND rootId = :rootId AND relativePath = :path")
    suspend fun removePlaylistItem(id: Long, rootId: Long, path: String)

    @Query("SELECT * FROM playlist_items WHERE playlistId = :id ORDER BY position")
    suspend fun playlistItems(id: Long): List<PlaylistItemEntity>

    @Transaction
    suspend fun addToPlaylist(id: Long, rootId: Long, path: String, at: Long) {
        upsertPlaylistItem(PlaylistItemEntity(id, rootId, path, nextPosition(id), at))
    }

    /** Moves one game up or down within a playlist by swapping places with its neighbour. */
    @Transaction
    suspend fun movePlaylistItem(id: Long, rootId: Long, path: String, by: Int) {
        val items = playlistItems(id)
        val i = items.indexOfFirst { it.rootId == rootId && it.relativePath == path }
        val j = i + by
        if (i < 0 || j !in items.indices) return
        upsertPlaylistItem(items[i].copy(position = items[j].position))
        upsertPlaylistItem(items[j].copy(position = items[i].position))
    }

    @Query("UPDATE OR IGNORE favourites SET relativePath = :to WHERE rootId = :rootId AND relativePath = :from")
    suspend fun relinkFavourite(rootId: Long, from: String, to: String)

    @Query("UPDATE OR IGNORE playlist_items SET relativePath = :to WHERE rootId = :rootId AND relativePath = :from")
    suspend fun relinkPlaylistItems(rootId: Long, from: String, to: String)

    @Query("UPDATE OR IGNORE play_history SET relativePath = :to WHERE rootId = :rootId AND relativePath = :from")
    suspend fun relinkPlay(rootId: Long, from: String, to: String)

    @Query("UPDATE OR IGNORE covers SET relativePath = :to WHERE rootId = :rootId AND relativePath = :from")
    suspend fun relinkCover(rootId: Long, from: String, to: String)

    @Query("UPDATE OR IGNORE game_edits SET relativePath = :to WHERE rootId = :rootId AND relativePath = :from")
    suspend fun relinkEdit(rootId: Long, from: String, to: String)

    @Query("SELECT * FROM game_edits WHERE rootId = :rootId")
    suspend fun editsFor(rootId: Long): List<GameEditEntity>

    @Query("SELECT * FROM game_edits WHERE rootId = :rootId AND relativePath = :path")
    fun edit(rootId: Long, path: String): Flow<GameEditEntity?>

    @Query("SELECT * FROM game_edits WHERE rootId = :rootId AND relativePath = :path")
    suspend fun editNow(rootId: Long, path: String): GameEditEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEdit(edit: GameEditEntity)

    @Query("DELETE FROM game_edits WHERE rootId = :rootId AND relativePath = :path")
    suspend fun deleteEdit(rootId: Long, path: String)

    @Update
    suspend fun updateGame(game: GameEntity)

    /** Saves an edit and shows it at once; the first edit remembers what the scan had found. */
    @Transaction
    suspend fun saveEdit(game: GameEntity, details: GameDetails, at: Long) {
        val scanned = editNow(game.rootId, game.relativePath)?.scanned ?: game.details()
        upsertEdit(GameEditEntity(game.rootId, game.relativePath, details, scanned, at))
        updateGame(game.withDetails(details))
    }

    /** Back to the scanned details. */
    @Transaction
    suspend fun resetEdit(game: GameEntity) {
        val edit = editNow(game.rootId, game.relativePath) ?: return
        deleteEdit(game.rootId, game.relativePath)
        updateGame(game.withDetails(edit.scanned))
    }

    /**
     * Moves RetroForge's own records for a game whose file changed path (compressed, or replaced by its kept
     * duplicate). Where the new path already has a record of its own, that one is kept and the old one stays put.
     */
    @Transaction
    suspend fun relink(rootId: Long, from: String, to: String) {
        relinkFavourite(rootId, from, to)
        relinkPlaylistItems(rootId, from, to)
        relinkPlay(rootId, from, to)
        relinkCover(rootId, from, to)
        relinkEdit(rootId, from, to)
    }

    /** Replaces a folder's index in one transaction, so an interrupted scan leaves the previous index intact. */
    @Transaction
    suspend fun replaceIndex(root: RootEntity, games: List<GameEntity>, problems: List<ProblemEntity>, layout: String, unrecognised: String, at: Long) {
        // Hand edits win over what the scan found; the scanned values are kept up to date for "Reset".
        val edits = editsFor(root.id).associateBy { it.relativePath }
        val merged = games.map { game ->
            val edit = edits[game.relativePath] ?: return@map game
            upsertEdit(edit.copy(scanned = game.details()))
            game.withDetails(edit.edited)
        }
        deleteGames(root.id)
        deleteProblems(root.id)
        merged.chunked(500).forEach { insertGames(it) }
        insertProblems(problems)
        updateScan(root.id, at, layout, games.size, problems.size, unrecognised)
    }
}

@Database(
    entities = [
        RootEntity::class, GameEntity::class, ProblemEntity::class, PlayEntity::class, CoverEntity::class,
        FavouriteEntity::class, PlaylistEntity::class, PlaylistItemEntity::class, GameEditEntity::class,
    ],
    version = 5,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4), AutoMigration(from = 4, to = 5)],
)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun dao(): LibraryDao

    companion object {
        fun create(context: Context): LibraryDatabase =
            Room.databaseBuilder(context.applicationContext, LibraryDatabase::class.java, "library.db").build()
    }
}
