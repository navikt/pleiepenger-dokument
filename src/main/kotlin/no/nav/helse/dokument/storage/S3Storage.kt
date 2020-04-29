package no.nav.helse.dokument.storage

import com.amazonaws.AmazonServiceException
import com.amazonaws.SdkClientException
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.BucketLifecycleConfiguration
import com.amazonaws.services.s3.model.CannedAccessControlList
import com.amazonaws.services.s3.model.CreateBucketRequest
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.lifecycle.LifecycleFilter
import io.ktor.util.date.toGMTDate
import io.ktor.util.date.toJvmDate
import io.prometheus.client.Counter
import io.prometheus.client.Histogram
import no.nav.helse.dusseldorf.ktor.health.HealthCheck
import no.nav.helse.dusseldorf.ktor.health.Healthy
import no.nav.helse.dusseldorf.ktor.health.Result
import no.nav.helse.dusseldorf.ktor.health.UnHealthy
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.time.ZonedDateTime

class S3Storage(private val s3 : AmazonS3,
                private val expirationInDays : Int?) : Storage {

    private companion object {
        private val logger: Logger = LoggerFactory.getLogger("nav.S3Storage")
        private const val BUCKET_NAME = "pleiepengerdokument"

        private val s3Histogram = Histogram
            .build("s3_operation_histogram",
                "Histogram for operasjoner gjort mot S3.")
            .labelNames("operation")
            .register()

        private val s3Counter = Counter
            .build(
                "s3_operation_counter",
                "Counter for utfall a operasjoner gjort mot S3.")
            .labelNames("operation", "status")
            .register()
    }

    override fun ready() {
        s3Operation (
            operation = { s3.getBucketLocation(BUCKET_NAME) },
            operationName = "getBucketLocation"
        )
    }

    override fun hent(key: StorageKey): StorageValue? {
        val objectAsString = s3Operation(
            operation = { s3.getObjectAsString(BUCKET_NAME, key.value) },
            allowNotFound = true,
            operationName = "getObjectAsString"
        )

        if (objectAsString == null) {
            logger.info("Fant ikke value for key ${key.value}")
            return null
        }

        return StorageValue(objectAsString)
    }

    override fun slett(storageKey: StorageKey): Boolean {
        val value = hent(storageKey)
        return if (value == null) false else {
            return try {
                s3Operation(
                    operation = { s3.deleteObject(BUCKET_NAME, storageKey.value) },
                    operationName = "deleteObject"
                )
                true
            } catch (cause: Throwable) {
                logger.warn("Fikk ikke slettet value for key ${storageKey.value}", cause)
                false
            }
        }
    }

    override fun lagre(key: StorageKey, value: StorageValue) {
        s3Operation(
            operation = { s3.putObject(BUCKET_NAME, key.value, value.value) },
            operationName = "putObject"
        )
    }

    override fun lagre(key: StorageKey, value: StorageValue, expires: ZonedDateTime) {
        val (metadata, inputStream) = value.initMedExpiry(expires)
        s3Operation(
            operation = { s3.putObject(BUCKET_NAME, key.value, inputStream, metadata) },
            operationName = "putObjectWithExpiry"
        )
    }

    init {
        logger.info("Initialiserer S3 Storage.")
        ensureBucketExists()
        ensureProperBucketLifecycleConfiguration()
        logger.info("S3 Storage initialisert OK.")
    }


    private fun ensureBucketExists() {
        if (!s3Operation( operation = { s3.doesBucketExistV2(BUCKET_NAME) } , operationName = "doesBucketExistV2" )!!) {
            logger.info("Bucket $BUCKET_NAME finnes ikke fra før, oppretter.")
            createBucket()
            logger.info("Bucket opprettet.")
        } else {
            logger.info("Bucket $BUCKET_NAME finnes allerede.")
        }
    }

    private fun ensureProperBucketLifecycleConfiguration() {
        val lifecycleConfigurationEnabled = expirationInDays != null && expirationInDays > 0
        logger.info("expiryInDays=$expirationInDays")
        logger.info("lifecycleConfigurationEnabled=$lifecycleConfigurationEnabled")

        if (lifecycleConfigurationEnabled) {
            logger.trace("Konfigurerer lifecycle for bucket.")
            logger.info("Expiry på bucket objects settes til $expirationInDays dager.")
            s3Operation(
                operation = { s3.setBucketLifecycleConfiguration(BUCKET_NAME, bucketLifecycleConfiguration(expirationInDays!!)) },
                operationName = "setBucketLifecycleConfiguration"
            )
            logger.trace("Liceyclye konfigurasjon lagret.")

        } else {
            logger.info("Sletter eventuelle aktive lifecycle konfigurasjoner.")
            s3Operation(
                operation = { s3.deleteBucketLifecycleConfiguration(BUCKET_NAME) },
                operationName = "deleteBucketLifecycleConfiguration"
            )
            logger.info("Sletting av licecycle konfigurasjoner OK.")
        }
    }

    private fun createBucket() {
        s3Operation(
            operation = { s3.createBucket(CreateBucketRequest(BUCKET_NAME).withCannedAcl(CannedAccessControlList.Private)) },
            operationName = "createBucket"
        )
    }

    private fun bucketLifecycleConfiguration(days: Int): BucketLifecycleConfiguration {
        val rules= BucketLifecycleConfiguration.Rule()
            .withId("$BUCKET_NAME-$days")
            .withFilter(LifecycleFilter())
            .withStatus(BucketLifecycleConfiguration.ENABLED)
            .withExpirationInDays(days)
        return BucketLifecycleConfiguration().withRules(rules)
    }

    private fun<T> s3Operation(
        operation : () -> T,
        operationName : String,
        allowNotFound : Boolean = false) : T? {
        val timer = s3Histogram.labels(operationName).startTimer()
        return try {
            val result = operation.invoke()
            s3Counter.labels(operationName, "success").inc()
            result
        } catch (cause : AmazonServiceException) {
            if (404 == cause.statusCode && allowNotFound) return null
            else {
                s3Counter.labels(operationName, "s3Failure").inc()
                throw IllegalStateException("Fikk response fra S3, men en feil forekom på tjenestesiden. ${cause.message}", cause)
            }
        } catch (cause: SdkClientException) {
            s3Counter.labels(operationName, "connectionFailure").inc()
            throw IllegalStateException("Fikk ikke response fra S3. ${cause.message}", cause)
        } catch (cause: Throwable) {
            s3Counter.labels(operationName, "failure").inc()
            throw IllegalStateException("Uventet feil ved tjenestekall mot S3.", cause)
        } finally {
            timer.observeDuration()
        }
    }
}

private fun StorageValue.initMedExpiry(expires: ZonedDateTime) : Pair<ObjectMetadata, InputStream> {
    val contentBytes = value.toByteArray()
    val metadata = ObjectMetadata().apply {
        contentType = "text/plain"
        contentLength = value.toByteArray().size.toLong()
        expirationTime = expires.toGMTDate().toJvmDate()
    }
    return Pair(metadata, ByteArrayInputStream(contentBytes))
}

class S3StorageHealthCheck(
    private val s3Storage: S3Storage
) : HealthCheck {

    private companion object {
        private val logger: Logger = LoggerFactory.getLogger("nav.S3StorageHealthCheck")
        private val name = "S3StorageHealthCheck"
    }

    override suspend fun check(): Result {
        return try {
            s3Storage.ready()
            Healthy(name = name, result = "Tilkobling mot S3 OK.")
        } catch (cause: Throwable) {
            logger.error("Feil ved tilkobling mot S3.", cause)
            UnHealthy(name = name, result = cause.message ?: "Feil ved tilkobling mot S3.")
        }
    }
}