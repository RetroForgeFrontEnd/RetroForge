package com.retroforge.core.library

import android.content.Context
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

    @Query("DELETE FROM roots WHERE id = :id")
    suspend fun deleteRoot(id: Long)

    @Query("SELECT system, COUNT(*) AS games, SUM(isHack) AS hacks FROM games GROUP BY system")
    fun systemCounts(): Flow<List<SystemCount>>

    @Query("SELECT * FROM games WHERE system = :system ORDER BY sortKey, fileName")
    fun gamesFor(system: String): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE isHack = 1 ORDER BY system, hackFolder, sortKey")
    fun hacks(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE id = :id")
    fun game(id: Long): Flow<GameEntity?>

    @Query("SELECT * FROM games WHERE duplicateKey = :key AND id != :excludeId")
    suspend fun duplicatesOf(key: String, excludeId: Long): List<GameEntity>

    @Query("SELECT * FROM games WHERE title LIKE '%' || :query || '%' ESCAPE '\\' ORDER BY sortKey LIMIT 200")
    fun search(query: String): Flow<List<GameEntity>>

    @Query("SELECT * FROM scan_problems WHERE rootId = :rootId")
    fun problems(rootId: Long): Flow<List<ProblemEntity>>

    @Insert
    suspend fun insertGames(games: List<GameEntity>)

    @Insert
    suspend fun insertProblems(problems: List<ProblemEntity>)

    @Query("DELETE FROM games WHERE rootId = :rootId")
    suspend fun deleteGames(rootId: Long)

    @Query("DELETE FROM scan_problems WHERE rootId = :rootId")
    suspend fun deleteProblems(rootId: Long)

    @Query(
        "UPDATE roots SET lastScanAt = :at, layout = :layout, gameCount = :games, problemCount = :problems, " +
            "unrecognisedFolders = :unrecognised WHERE id = :rootId",
    )
    suspend fun updateScan(rootId: Long, at: Long, layout: String, games: Int, problems: Int, unrecognised: String)

    /** Replaces a folder's index in one transaction, so an interrupted scan leaves the previous index intact. */
    @Transaction
    suspend fun replaceIndex(root: RootEntity, games: List<GameEntity>, problems: List<ProblemEntity>, layout: String, unrecognised: String, at: Long) {
        deleteGames(root.id)
        deleteProblems(root.id)
        games.chunked(500).forEach { insertGames(it) }
        insertProblems(problems)
        updateScan(root.id, at, layout, games.size, problems.size, unrecognised)
    }
}

@Database(entities = [RootEntity::class, GameEntity::class, ProblemEntity::class], version = 1, exportSchema = true)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun dao(): LibraryDao

    companion object {
        fun create(context: Context): LibraryDatabase =
            Room.databaseBuilder(context.applicationContext, LibraryDatabase::class.java, "library.db").build()
    }
}
