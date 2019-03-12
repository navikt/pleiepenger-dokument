package no.nav.helse

import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3ClientBuilder
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
                          secret: Boolean,
                          optional: Boolean) : String? {
        val configValue = config.propertyOrNull(key) ?: return if (optional) null else throw IllegalArgumentException("$key må settes.")
        val stringValue = configValue.getString()
        if (stringValue.isBlank()) {
            return if (optional) null else throw IllegalArgumentException("$key må settes.")
        }
        logger.info("{}={}", key, if (secret) "***" else stringValue)
        return stringValue
    }
    private fun getRequiredString(key: String, secret: Boolean = false) : String = getString(key, secret, false)!!
    private fun getOptionalString(key: String, secret: Boolean = false) : String? = getString(key, secret, true)
    private fun <T>getListFromCsv(csv: String, builder: (value: String) -> T) : List<T> = csv.replace(" ", "").split(",").map(builder)

    fun getEncryptionPassphrase() : Pair<Int, String> {
        val identifier = getRequiredString("nav.crypto.passphrase.encryption_identifier").toInt()
        val passphrase = getCryptoPasshrase("$CRYPTO_PASSPHRASE_PREFIX$identifier")
        return Pair(identifier, passphrase)
    }

    fun getDecryptionPassphrases() : Map<Int, String> {
        val csv = getOptionalString("nav.crypto.passphrase.decryption_identifiers") // Kan være kun den vi krypterer med

        val identifiers = if (csv == null) emptyList() else getListFromCsv(
            csv = csv,
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
        return System.getenv(key) ?: throw IllegalStateException("Environment Variable $key må være satt")
    }

    fun getAuthorizedSubjects(): List<String> {
        val csv = getOptionalString("nav.authorization.authorized_subjects") ?: return emptyList()
        return getListFromCsv(
            csv = csv,
            builder = { value -> value}
        )
    }

    fun getJwksUrl() : URL = URL(getRequiredString("nav.authorization.jwks_url"))
    fun getIssuer() : String = getRequiredString("nav.authorization.issuer")

    private fun getS3AccessKey() : String = getRequiredString("nav.storage.s3.access_key", secret = true)
    private fun getS3SecretKey() : String = getRequiredString("nav.storage.s3.secret_key", secret = true)
    private fun getS3SigningRegion() : String = getRequiredString("nav.storage.s3.signing_region", secret = false)
    private fun getS3ServiceEndpoint() : String = getRequiredString("nav.storage.s3.service_endpoint", secret = false)
    fun getS3Configured() : AmazonS3 {
        return AmazonS3ClientBuilder.standard()
            .withEndpointConfiguration(AwsClientBuilder.EndpointConfiguration(getS3ServiceEndpoint(), getS3SigningRegion()))
            .enablePathStyleAccess()
            .withCredentials(AWSStaticCredentialsProvider(BasicAWSCredentials(getS3AccessKey(), getS3SecretKey())))
            .build()
    }
    fun getS3ExpirationInDays() : Int? = getOptionalString("nav.storage.s3.expiration_in_days", secret = false)?.toInt()


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