package no.nav.helse

import io.ktor.config.ApplicationConfig
import io.ktor.util.KtorExperimentalAPI
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL

private val logger: Logger = LoggerFactory.getLogger("nav.Configuration")
private const val CRYPTO_PASSPHRASE_PREFIX = "CRYPTO_PASSPHRASE_"

@KtorExperimentalAPI
data class Configuration(private val config : ApplicationConfig) {
    private fun getString(key: String,
                          secret: Boolean = false) : String  {
        val stringValue = config.property(key).getString()
        logger.info("{}={}", key, if (secret) "***" else stringValue)
        return stringValue
    }

    private fun getOptionalString(key: String,
                          secret: Boolean = false) : String? {
        val configValue = config.propertyOrNull(key)
        return if (configValue != null) getString(key, secret) else null
    }

    private fun <T>getListFromCsv(key: String,
                                  secret: Boolean = false,
                                  builder: (value: String) -> T) : List<T> {
        val csv = getString(key, false)
        val list = csv.replace(" ", "").split(",")
        val builtList = mutableListOf<T>()
        list.filter{ !it.isBlank() }.forEach { entry ->
            logger.info("$key entry = ${if (secret) "***" else entry}")
            builtList.add(builder(entry))
        }
        return builtList.toList()
    }

    fun getEncryptionPassphrase() : Pair<Int, String> {
        val identifier = getString("nav.crypto.passphrase.encryption_identifier").toInt()
        val passphrase = getCryptoPasshrase("$CRYPTO_PASSPHRASE_PREFIX$identifier")
        return Pair(identifier, passphrase)
    }

    fun getDecryptionPassphrases() : Map<Int, String> {
        val identifiers = getListFromCsv(
            key = "nav.crypto.passphrase.decryption_identifiers",
            builder = { value -> value.toInt()}
        )
        val decryptionPassphrases = mutableMapOf<Int, String>()
        identifiers.forEach { decryptionPassphrases[it] = getCryptoPasshrase("$CRYPTO_PASSPHRASE_PREFIX$it") }
        val encryptionPassphrase = getEncryptionPassphrase()
        decryptionPassphrases[encryptionPassphrase.first] = encryptionPassphrase.second // Forsikre oss om at nåværende krypterings-ID alltid er en av decrypterings-ID'ene
        return decryptionPassphrases.toMap()
    }

    private fun getCryptoPasshrase(key: String) : String {
        val configValue = getOptionalString(key = key, secret = true)
        if (configValue != null) return configValue
        return System.getenv(key) ?: throw IllegalStateException("Mangler $key")
    }

    fun getAuthorizedSubjects(): List<String> {
        return getListFromCsv(
            key = "nav.authorization.authorized_subjects",
            builder = { value -> value}
        )
    }

    fun getJwksUrl() : URL {
        return URL(getString("nav.authorization.jwks_url"))
    }

    fun getIssuer() : String {
        return getString("nav.authorization.issuer")
    }

    fun logIndirectlyUsedConfiguration() {
        logger.info("# Indirectly used configuration")
        val properties = System.getProperties()
        logger.info("## System Properties")
        properties.forEach { key, value ->
            if (key is String && (key.startsWith(prefix = "http", ignoreCase = true) || key.startsWith(prefix = "https", ignoreCase = true))) {
                logger.info("$key=$value")
            }
        }
        logger.info("## Environment variables")
        val environmentVariables = System.getenv()
        logger.info("HTTP_PROXY=${environmentVariables["HTTP_PROXY"]}")
        logger.info("HTTPS_PROXY=${environmentVariables["HTTPS_PROXY"]}")
        logger.info("NO_PROXY=${environmentVariables["NO_PROXY"]}")
    }
}