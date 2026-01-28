//package com.example.fyp_25_s4_23.data.repositories
//
//import com.example.fyp_25_s4_23.domain.entities.Contact
//
//object TrustedContactsRepo {
//    private val contacts = mutableSetOf<Contact>()
//
//    fun add(contact: Contact) {
//        contacts.add(contact)
//    }
//
//    fun remove(phoneNumber: String) {
//        contacts.removeAll { it.phoneNumber == phoneNumber }
//    }
//
//    fun isTrusted(phoneNumber: String): Boolean {
//        return contacts.any { it.phoneNumber == phoneNumber }
//    }
//
//    fun getAll(): List<Contact> = contacts.toList()
//}