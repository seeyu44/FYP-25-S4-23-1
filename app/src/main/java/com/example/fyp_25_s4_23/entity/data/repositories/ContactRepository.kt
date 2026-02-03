package com.example.fyp_25_s4_23.entity.data.repositories

import com.example.fyp_25_s4_23.entity.data.dao.ContactDao
import com.example.fyp_25_s4_23.entity.data.mappers.toDomain
import com.example.fyp_25_s4_23.entity.data.mappers.toEntity
import com.example.fyp_25_s4_23.domain.entities.Contact
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ContactRepository(
    private val contactDao: ContactDao
) {

    fun getAllContacts(): Flow<List<Contact>> =
        contactDao.getAllContacts().map { entities ->
            entities.map { it.toDomain() }
        }

    suspend fun getAllContactsOnce(): List<Contact> {
        return contactDao.getAllContactsOnce().map { it.toDomain() }
    }

    suspend fun insertContact(contact: Contact) {
        contactDao.insertContact(contact.toEntity())
    }

    suspend fun deleteContact(contact: Contact) {
        contactDao.deleteContact(contact.toEntity())
    }

    suspend fun deleteById(id: String) {
        id.toIntOrNull()?.let { intId ->
            contactDao.deleteById(intId)
        }
    }

    suspend fun existsByUsername(username: String): Boolean {
        return contactDao.existsByUsername(username)
    }

    suspend fun getContactByUsername(username: String): Contact? {
        return contactDao.getByUsername(username)?.toDomain()
    }

    suspend fun existsByPhoneNumber(phoneNumber: String): Boolean {
        return contactDao.existsByPhoneNumber(phoneNumber)
    }

    suspend fun getContactByPhoneNumber(phoneNumber: String): Contact? {
        return contactDao.getByPhoneNumber(phoneNumber)?.toDomain()
    }

    suspend fun updateContactLabel(id: String, label: String) {
        id.toIntOrNull()?.let { intId ->
            contactDao.updateLabel(intId, label)
        }
    }

    suspend fun clearAll(){
        contactDao.clearAll()
    }
}
