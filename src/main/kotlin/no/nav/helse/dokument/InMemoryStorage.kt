package no.nav.helse.dokument

class InMemoryStorage : Storage {

    private val storage = mutableMapOf<StorageKey, StorageValue>()

    override fun slett(storageKey: StorageKey) : Boolean {
        val value = storage.remove(storageKey)
        return value != null
    }

    override fun lagre(key: StorageKey, value: StorageValue) {
        storage[key] = value
    }

    override fun hent(key: StorageKey): StorageValue? {
        return storage[key]
    }

}