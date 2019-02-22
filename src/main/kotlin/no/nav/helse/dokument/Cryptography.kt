package no.nav.helse.dokument

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.IllegalStateException

private val logger: Logger = LoggerFactory.getLogger("nav.Cryptography")

class Cryptography(
    private val encryptionPassphrase: Pair<Int, String>,
    private val decryptionPassphrases: Map<Int, String>
) {

    init {
        logger.info("Krypterer med nøkkel med ID = ${encryptionPassphrase.first}")
        logger.info("Decrypterer med ${decryptionPassphrases.size} mulige Nøkkel ID'er:")
        decryptionPassphrases.forEach{ logger.info("${it.key}")}
    }

    fun encrypt(plainText : String,
                fodselsnummer: Fodselsnummer
    ) : String {
        val encrypted = Crypto(
            passphrase = encryptionPassphrase.second,
            iv = fodselsnummer.value
        ).encrypt(plainText)

        return EncryptedResult(
            passphraseIdentifier = encryptionPassphrase.first,
            encrypted = encrypted
        ).combined()
    }

    fun decrypt(encrypted: String,
                fodselsnummer: Fodselsnummer
    ) : String {
        val encryptedResult = EncryptedResult(combined = encrypted)
        if (!decryptionPassphrases.containsKey(encryptedResult.passphraseIdentifier)) {
            throw IllegalStateException("Ikke konfigurert til å dekryptere innhold med Nøkkel ID ${encryptedResult.passphraseIdentifier}")
        }

        return Crypto(
            passphrase = decryptionPassphrases[encryptedResult.passphraseIdentifier]!!,
            iv = fodselsnummer.value
        ).decrypt(encryptedResult.encrypted)
    }
}

private data class EncryptedResult(
    val passphraseIdentifier: Int,
    val encrypted: String
) {
    constructor(combined: String) : this(
        passphraseIdentifier = combined.split("-")[0].toInt(),
        encrypted = combined.removePrefix("${combined.split("-")[0]}-")
    )

    fun combined() : String {
        return "$passphraseIdentifier-$encrypted"
    }
}