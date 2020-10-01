package no.nav.helse.dokument.storage

import com.google.cloud.storage.*
import com.google.cloud.storage.BucketInfo.LifecycleRule
import io.ktor.util.date.*
import no.nav.helse.dusseldorf.ktor.health.HealthCheck
import no.nav.helse.dusseldorf.ktor.health.Healthy
import no.nav.helse.dusseldorf.ktor.health.Result
import no.nav.helse.dusseldorf.ktor.health.UnHealthy
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.time.ZonedDateTime

class GcpStorageBucket(
    private val gcpStorage: com.google.cloud.storage.Storage,
    private val bucketName: String,
    private val expirationInDays: Int?
) : Storage {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(GcpStorageBucket::class.java)
    }

    init {
        ensureBucketExists()
        ensureProperBucketLifecycleConfiguration()
    }

    override fun hent(key: StorageKey): StorageValue? {
        return try {
            val blob = gcpStorage.get(BlobId.of(bucketName, key.value))
            val outputStream = ByteArrayOutputStream()
            blob.downloadTo(outputStream)

            StorageValue(String(outputStream.toByteArray()))
        } catch (ex: StorageException) {
            logger.error("Henting av dokument med id ${key.value} feilet.", ex)
            null
        }
    }

    override fun slett(storageKey: StorageKey): Boolean {
        val value = hent(storageKey)
        return if (value == null) false else {
            return try {
                gcpStorage.delete(bucketName, storageKey.value)
                true
            } catch (cause: StorageException) {
                logger.warn("Sletting av dokument med id ${storageKey.value} feilet.", cause)
                false
            }
        }
    }

    override fun lagre(key: StorageKey, value: StorageValue) {
        val blobId = BlobId.of(bucketName, key.value)
        val blobInfo = BlobInfo.newBuilder(blobId).build()

        lagre(blobInfo, value)
    }

    override fun lagre(key: StorageKey, value: StorageValue, expires: ZonedDateTime) {
        val blobId = BlobId.of(bucketName, key.value)
        val blobInfo = BlobInfo.newBuilder(blobId)
            .setMetadata(
                mapOf(
                    "contentType" to "text/plain",
                    "contentLength" to "${value.value.toByteArray().size.toLong()}",
                    "expirationTime" to "${expires.toGMTDate().toJvmDate()}"
                )
            )
            .build()

        lagre(blobInfo, value)
    }

    private fun lagre(blobInfo: BlobInfo, value: StorageValue) {
        try {
            gcpStorage.create(blobInfo, value.value.toByteArray(), 0, value.value.length)
        } catch (ex: StorageException) {
            logger.error("Feiled med å lagre dokument med id: ${blobInfo.blobId.name}", ex)
        }
    }

    override fun ready() {
        gcpStorage[bucketName].location
    }

    private fun ensureBucketExists() {
        if (gcpStorage[bucketName] !== null) {
            logger.info("Bucket $bucketName funnet.")
        } else {
            logger.warn("Fant ikke bucket ved navn $bucketName. Oppretter ny...")
            gcpStorage.create(
                BucketInfo.newBuilder(bucketName)
                    .setStorageClass(StorageClass.STANDARD)
                    .setLocation("EUROPE-NORTH1")
                    .build()
            )
        }
    }

    private fun ensureProperBucketLifecycleConfiguration() {
        val lifecycleConfigurationEnabled = expirationInDays != null && expirationInDays > 0
        logger.info("expiryInDays=$expirationInDays")
        logger.info("lifecycleConfigurationEnabled=$lifecycleConfigurationEnabled")

        val bucket = gcpStorage[bucketName]
        if (lifecycleConfigurationEnabled) {
            logger.trace("Konfigurerer lifecycle for bucket.")
            logger.info("Expiry på bucket objects settes til $expirationInDays dager.")
            bucket.toBuilder()
                .setLifecycleRules(
                    listOf(
                        LifecycleRule(
                            LifecycleRule.LifecycleAction.newDeleteAction(),
                            LifecycleRule.LifecycleCondition.newBuilder().setAge(expirationInDays).build()
                        )
                    )
                ).build()
                .update()
            logger.trace("Lifecycle konfigurasjon lagret.")

        } else {
            logger.info("Sletter eventuelle aktive lifecycle konfigurasjoner.")
            bucket.toBuilder()
                .deleteLifecycleRules()
                .build()
                .update()
            logger.info("Sletting av licecycle konfigurasjoner OK.")
        }
    }
}

class GcpStorageHealthCheck(
    private val gcpStorageBucket: GcpStorageBucket
) : HealthCheck {

    private companion object {
        private val logger: Logger = LoggerFactory.getLogger("nav.S3StorageHealthCheck")
        private val name = "S3StorageHealthCheck"
    }

    override suspend fun check(): Result {
        return try {
            gcpStorageBucket.ready()
            Healthy(name = name, result = "Tilkobling mot GCP bucket OK.")
        } catch (cause: Throwable) {
            logger.error("Feil ved tilkobling mot GCP bucket.", cause)
            UnHealthy(name = name, result = cause.message ?: "Feil ved tilkobling mot GCP bucket.")
        }
    }
}
