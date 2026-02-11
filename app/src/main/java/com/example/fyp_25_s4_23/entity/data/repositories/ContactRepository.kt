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

    fun getAllContacts(userId: String): Flow<List<Contact>> =
        contactDao.getAllContacts(userId).map { entities ->
            entities.map { it.toDomain() }
        }

    suspend fun getAllContactsOnce(userId: String): List<Contact> {
        return contactDao.getAllContactsOnce(userId).map { it.toDomain() }
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

    suspend fun existsByUsername(userId: String, username: String): Boolean {
        return contactDao.existsByUsername(userId, username)
    }

    suspend fun getContactByUsername(userId: String, username: String): Contact? {
        return contactDao.getByUsername(userId, username)?.toDomain()
    }

    suspend fun existsByPhoneNumber(userId: String, phoneNumber: String): Boolean {
        return contactDao.existsByPhoneNumber(userId, phoneNumber)
    }

    suspend fun getContactByPhoneNumber(userId: String, phoneNumber: String): Contact? {
        return contactDao.getByPhoneNumber(userId, phoneNumber)?.toDomain()
    }

    suspend fun updateContactLabel(id: String, label: String) {
        id.toIntOrNull()?.let { intId ->
            contactDao.updateLabel(intId, label)
        }
    }

    suspend fun updateContactDetails(id: String, displayName: String?, phoneNumber: String) {
        id.toIntOrNull()?.let { intId ->
            contactDao.updateContactDetails(intId, displayName, phoneNumber)
        }
    }

    suspend fun clearAll(){
        contactDao.clearAll()
    }
}
