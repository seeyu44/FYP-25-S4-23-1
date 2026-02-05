package com.example.fyp_25_s4_23.entity.data.dao

import androidx.room.*
import com.example.fyp_25_s4_23.entity.data.entities.ContactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {

    @Query("SELECT * FROM contacts WHERE userId = :userId")
    fun getAllContacts(userId: String): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE userId = :userId")
    suspend fun getAllContactsOnce(userId: String): List<ContactEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: ContactEntity)

    @Delete
    suspend fun deleteContact(contact: ContactEntity)

    @Query("DELETE FROM contacts WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("SELECT * FROM contacts WHERE userId = :userId AND displayName = :username LIMIT 1")
    suspend fun getByUsername(userId: String, username: String): ContactEntity?

    @Query(
        "SELECT EXISTS(" +
                "SELECT 1 FROM contacts WHERE userId = :userId AND displayName = :username LIMIT 1" +
                ")"
    )
    suspend fun existsByUsername(userId: String, username: String): Boolean

    @Query("SELECT * FROM contacts WHERE userId = :userId AND phoneNumber = :phoneNumber LIMIT 1")
    suspend fun getByPhoneNumber(userId: String, phoneNumber: String): ContactEntity?

    @Query(
        "SELECT EXISTS(" +
                "SELECT 1 FROM contacts WHERE userId = :userId AND phoneNumber = :phoneNumber LIMIT 1" +
                ")"
    )
    suspend fun existsByPhoneNumber(userId: String, phoneNumber: String): Boolean

    @Query("UPDATE contacts SET label = :label WHERE id = :id")
    suspend fun updateLabel(id: Int, label: String)

    @Query("DELETE FROM contacts")
    suspend fun clearAll()
}
