package no.nav.helse.dokument

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

private val logger: Logger = LoggerFactory.getLogger("nav.DokumentService")

data class DokumentService(
    private val cryptography: Cryptography,
    private val storage: Storage,
    private val objectMapper: ObjectMapper
) {
    fun hentDokument(
        dokumentId: DokumentId,
        fodselsnummer: Fodselsnummer
    ) : Dokument? {
        logger.trace("Henter dokument $dokumentId.")
        val value = storage.hent(
            generateStorageKey(
                dokumentId = dokumentId,
                fodselsnummer = fodselsnummer
            )
        )?: return null

        logger.trace("Fant dokument, dekrypterer.")

        val decrypted = cryptography.decrypt(
            encrypted = value.value,
            fodselsnummer = fodselsnummer
        )

        logger.trace("Dekryptert, mapper til dokument.")

        return objectMapper.readValue(decrypted)
    }

    fun slettDokument(
        dokumentId: DokumentId,
        fodselsnummer: Fodselsnummer
    ) : Boolean {
        logger.trace("Sletter dokument $dokumentId")
        val result = storage.slett(
            generateStorageKey(
                dokumentId = dokumentId,
                fodselsnummer = fodselsnummer
            )
        )
        if (!result) logger.warn("Fant ikke noe dokument å slette.")
        return result
    }

    fun lagreDokument(
        dokument: Dokument,
        fodselsnummer: Fodselsnummer
    ) : DokumentId {
        logger.trace("Lagrer dokument, krypterer.")
        val encrypted = cryptography.encrypt(
            plainText = objectMapper.writeValueAsString(dokument),
            fodselsnummer = fodselsnummer
        )

        logger.trace("Kryptert. Genrerer DokumentId")

        val dokumentId = generateDokumentId()

        logger.trace("DokumentID=$dokumentId. Lagrer dokument.")

        storage.lagre(
            key = generateStorageKey(
                dokumentId = dokumentId,
                fodselsnummer = fodselsnummer
            ),
            value = StorageValue(
                value = encrypted
            )
        )

        logger.trace("Lagring OK")

        return dokumentId
    }

    private fun generateStorageKey(
        dokumentId: DokumentId,
        fodselsnummer: Fodselsnummer
    ) : StorageKey {
        logger.trace("Genrerer Storage Key for DokumentID=$dokumentId. Krypterer.")
        val plainText = "${fodselsnummer.value}-${dokumentId.id}"
        val encrypted = cryptography.encrypt(
            plainText = plainText,
            fodselsnummer = fodselsnummer
        )
        logger.trace("Kryptert.")
        return StorageKey(
            value = encrypted
        )
    }

    private fun generateDokumentId() : DokumentId = DokumentId(id = UUID.randomUUID().toString())
}

data class DokumentId(val id: String)

data class Dokument(
    val tittel: String,
    val content: ByteArray,
    val contentType: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Dokument

        if (tittel != other.tittel) return false
        if (!content.contentEquals(other.content)) return false
        if (contentType != other.contentType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = tittel.hashCode()
        result = 31 * result + content.contentHashCode()
        result = 31 * result + contentType.hashCode()
        return result
    }
}