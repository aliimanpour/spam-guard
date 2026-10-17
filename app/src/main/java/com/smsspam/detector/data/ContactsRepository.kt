package com.smsspam.detector.data

import android.content.Context
import com.smsspam.detector.model.ContactEntry
import com.smsspam.detector.model.ContactGroup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The app's own address book: contacts with tags, and named groups. Tags and
 * groups are the building blocks for segmentation used by bulk sending and
 * workflow targets.
 */
class ContactsRepository(context: Context) {

    private val contactStore = JsonListStore(context, "spam_guard_contacts", "contacts")
    private val groupStore = JsonListStore(context, "spam_guard_groups", "groups")

    private val _contacts = MutableStateFlow(contactStore.read().map { ContactEntry.fromJson(it) })
    val contacts: StateFlow<List<ContactEntry>> = _contacts.asStateFlow()

    private val _groups = MutableStateFlow(groupStore.read().map { ContactGroup.fromJson(it) })
    val groups: StateFlow<List<ContactGroup>> = _groups.asStateFlow()

    @Synchronized
    fun saveContact(contact: ContactEntry): List<ContactEntry> {
        val current = _contacts.value.toMutableList()
        val index = current.indexOfFirst { it.id == contact.id }
        if (index >= 0) current[index] = contact else current.add(contact)
        _contacts.value = current
        contactStore.write(current.map { it.toJson() })
        return current
    }

    @Synchronized
    fun deleteContact(id: String): List<ContactEntry> {
        val next = _contacts.value.filterNot { it.id == id }
        _contacts.value = next
        contactStore.write(next.map { it.toJson() })
        return next
    }

    @Synchronized
    fun saveGroup(group: ContactGroup): List<ContactGroup> {
        val current = _groups.value.toMutableList()
        val index = current.indexOfFirst { it.id == group.id }
        if (index >= 0) current[index] = group else current.add(group)
        _groups.value = current
        groupStore.write(current.map { it.toJson() })
        return current
    }

    @Synchronized
    fun deleteGroup(id: String): List<ContactGroup> {
        val next = _groups.value.filterNot { it.id == id }
        _groups.value = next
        groupStore.write(next.map { it.toJson() })
        return next
    }

    /** Every distinct tag across the address book, sorted. */
    fun allTags(): List<String> =
        _contacts.value.flatMap { it.tags }.map { it.trim() }.filter { it.isNotEmpty() }
            .distinct().sorted()

    /** Numbers in a saved group. */
    fun numbersInGroup(groupId: String): List<String> =
        _groups.value.firstOrNull { it.id == groupId }?.members.orEmpty()

    /** Numbers of contacts carrying [tag]. */
    fun numbersWithTag(tag: String): List<String> =
        _contacts.value.filter { entry -> entry.tags.any { it.equals(tag, ignoreCase = true) } }
            .map { it.number }
}
