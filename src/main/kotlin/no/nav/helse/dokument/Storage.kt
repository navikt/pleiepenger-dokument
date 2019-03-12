package no.nav.helse.dokument

interface Storage {
    fun hent(key : StorageKey) : StorageValue?
    fun slett(storageKey: StorageKey) : Boolean
    fun lagre(key: StorageKey, value: StorageValue)
    fun ready()
}

data class StorageKey(val value: String)
data class StorageValue(val value: String)
