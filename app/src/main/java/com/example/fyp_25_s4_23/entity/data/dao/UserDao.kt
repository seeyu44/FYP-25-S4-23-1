package com.example.fyp_25_s4_23.entity.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.fyp_25_s4_23.entity.data.entities.UserEntity

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(user: UserEntity): Long

    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun findByUsername(username: String): UserEntity?

    @Query("SELECT * FROM users ORDER BY username ASC")
    suspend fun listUsers(): List<UserEntity>

    @Query("DELETE FROM users")
    suspend fun clear()
}

