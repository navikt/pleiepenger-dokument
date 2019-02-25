package no.nav.helse.dokument

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.aktoer.AktoerId
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val logger: Logger = LoggerFactory.getLogger("nav.DokumentService")

data class DokumentService(
    private val cryptography: Cryptography,
    private val storage: Storage,
    private val objectMapper: ObjectMapper
) {
    fun hentDokument(
        dokumentId: DokumentId,
        aktoerId: AktoerId
    ) : Dokument? {
        logger.trace("Henter dokument $dokumentId.")
        val value = storage.hent(
            generateStorageKey(
                dokumentId = dokumentId,
                aktoerId = aktoerId
            )
        )?: return null

        logger.trace("Fant dokument, dekrypterer.")

        val decrypted = cryptography.decrypt(
            id = dokumentId.id,
            encrypted = value.value,
            aktoerId = aktoerId
        )

        logger.trace("Dekryptert, mapper til dokument.")

        return objectMapper.readValue(decrypted)
    }

    fun slettDokument(
        dokumentId: DokumentId,
        aktoerId: AktoerId
    ) : Boolean {
        logger.trace("Sletter dokument $dokumentId")
        val result = storage.slett(
            generateStorageKey(
                dokumentId = dokumentId,
                aktoerId = aktoerId
            )
        )
        if (!result) logger.warn("Fant ikke noe dokument å slette.")
        return result
    }

    fun lagreDokument(
        dokument: Dokument,
        aktoerId: AktoerId
    ) : DokumentId {
        logger.trace("Generer DokumentID")
        val dokumentId = generateDokumentId()
        logger.trace("DokumentID=$dokumentId. Krypterer.")

        val encrypted = cryptography.encrypt(
            id = dokumentId.id,
            plainText = objectMapper.writeValueAsString(dokument),
            aktoerId = aktoerId
        )

        logger.trace("Larer dokument.")

        storage.lagre(
            key = generateStorageKey(
                dokumentId = dokumentId,
                aktoerId = aktoerId
            ),
            value = StorageValue(
                value = encrypted
            )
        )

        logger.trace("Lagring OK.")

        return dokumentId
    }

    private fun generateStorageKey(
        dokumentId: DokumentId,
        aktoerId: AktoerId
    ) : StorageKey {
        logger.trace("Genrerer Storage Key for $dokumentId. Krypterer.")
        val plainText = "${aktoerId.id}-${dokumentId.id}"
        val encrypted = cryptography.encrypt(
            id = dokumentId.id,
            plainText = plainText,
            aktoerId = aktoerId
        )
        logger.trace("Storage Key kryptert.")
        return StorageKey(
            value = encrypted
        )
    }

    private fun generateDokumentId() : DokumentId = DokumentId(id = cryptography.id())
}

data class DokumentId(val id: String)

data class Dokument(
    val title: String,
    val content: ByteArray,
    val contentType: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Dokument

        if (title != other.title) return false
        if (!content.contentEquals(other.content)) return false
        if (contentType != other.contentType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + content.contentHashCode()
        result = 31 * result + contentType.hashCode()
        return result
    }
}