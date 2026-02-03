package com.example.fyp_25_s4_23.entity.data.dao

import androidx.room.*
import com.example.fyp_25_s4_23.entity.data.entities.ContactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {

    @Query("SELECT * FROM contacts")
    fun getAllContacts(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts")
    suspend fun getAllContactsOnce(): List<ContactEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: ContactEntity)

    @Delete
    suspend fun deleteContact(contact: ContactEntity)

    @Query("DELETE FROM contacts WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("SELECT * FROM contacts WHERE displayName = :username LIMIT 1")
    suspend fun getByUsername(username: String): ContactEntity?

    @Query(
        "SELECT EXISTS(" +
                "SELECT 1 FROM contacts WHERE displayName = :username LIMIT 1" +
                ")"
    )
    suspend fun existsByUsername(username: String): Boolean

    @Query("SELECT * FROM contacts WHERE phoneNumber = :phoneNumber LIMIT 1")
    suspend fun getByPhoneNumber(phoneNumber: String): ContactEntity?

    @Query(
        "SELECT EXISTS(" +
                "SELECT 1 FROM contacts WHERE phoneNumber = :phoneNumber LIMIT 1" +
                ")"
    )
    suspend fun existsByPhoneNumber(phoneNumber: String): Boolean

    @Query("UPDATE contacts SET label = :label WHERE id = :id")
    suspend fun updateLabel(id: Int, label: String)

    @Query("DELETE FROM contacts")
    suspend fun clearAll()
}
