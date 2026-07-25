package com.codex.data

import android.content.Context
import androidx.room.*
import androidx.room.RoomDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    var title: String,
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    val starred: Boolean = false
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val role: String, // "user", "assistant", "system", "tool"
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "api_configs")
data class ApiConfigEntity(
    @PrimaryKey val id: String = "active",
    val baseUrl: String,
    val model: String,
    val apiKeyEnc: String, // encrypted API key
    val isActive: Boolean = true
)

@Entity(tableName = "state_cursors")
data class StateCursorEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val iteration: Int,
    val messagesJson: String,
    val state: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY updatedAt DESC")
    suspend fun getAllSessions(): List<SessionEntity>
    
    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getSession(id: String): SessionEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionEntity)
    
    @Delete
    suspend fun deleteSession(session: SessionEntity)
    
    @Query("UPDATE sessions SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateSession(id: String, title: String, updatedAt: Long)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessagesBySession(sessionId: String): List<MessageEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)
    
    @Query("DELETE FROM messages WHERE sessionId = :sessionId")
    suspend fun deleteMessagesBySession(sessionId: String)
}

@Dao
interface ApiConfigDao {
    @Query("SELECT * FROM api_configs WHERE isActive = 1 LIMIT 1")
    suspend fun getActive(): ApiConfigEntity?
    
    @Query("UPDATE api_configs SET isActive = 0")
    suspend fun deactivateAll()
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfig(config: ApiConfigEntity)
}

@Dao
interface StateCursorDao {
    @Query("SELECT * FROM state_cursors WHERE sessionId = :sessionId")
    suspend fun getBySessionId(sessionId: String): StateCursorEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCursor(cursor: StateCursorEntity)
    
    @Query("DELETE FROM state_cursors WHERE sessionId = :sessionId")
    suspend fun deleteBySessionId(sessionId: String)
}

@Database(entities = [SessionEntity::class, MessageEntity::class, ApiConfigEntity::class, StateCursorEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun messageDao(): MessageDao
    abstract fun configDao(): ApiConfigDao
    abstract fun cursorDao(): StateCursorDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "codex_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}