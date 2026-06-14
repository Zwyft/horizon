package com.zwyft.horizon.data.dao

import androidx.room.*
import com.zwyft.horizon.data.entity.SettingEntity

@Dao
interface SettingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(setting: SettingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(settings: List<SettingEntity>)

    @Query("SELECT * FROM settings WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): SettingEntity?

    @Query("SELECT value FROM settings WHERE `key` = :key LIMIT 1")
    suspend fun getValue(key: String): String?

    @Query("INSERT OR REPLACE INTO settings (`key`, `value`, `type`, `advanced`) VALUES (:key, :value, 'string', 0)")
    suspend fun setValue(key: String, value: String)

    @Query("SELECT * FROM settings ORDER BY `key` ASC")
    suspend fun getAll(): List<SettingEntity>

    @Query("SELECT * FROM settings WHERE advanced = 0 ORDER BY `key` ASC")
    suspend fun getBasic(): List<SettingEntity>

    @Query("DELETE FROM settings WHERE `key` = :key")
    suspend fun delete(key: String)

    @Query("SELECT COUNT(*) FROM settings WHERE `key` = :key")
    suspend fun exists(key: String): Int

    // Observable
    @Query("SELECT value FROM settings WHERE `key` = :key LIMIT 1")
    fun observeValue(key: String): kotlinx.coroutines.flow.Flow<String?>
}
