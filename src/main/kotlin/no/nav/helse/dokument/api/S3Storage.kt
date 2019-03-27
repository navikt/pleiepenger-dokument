package no.nav.helse.dokument.api

import com.amazonaws.AmazonServiceException
import com.amazonaws.SdkClientException
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.BucketLifecycleConfiguration
import com.amazonaws.services.s3.model.CannedAccessControlList
import com.amazonaws.services.s3.model.CreateBucketRequest
import com.amazonaws.services.s3.model.lifecycle.LifecycleFilter
import no.nav.helse.dokument.Storage
import no.nav.helse.dokument.StorageKey
import no.nav.helse.dokument.StorageValue
import no.nav.helse.dusseldorf.ktor.health.HealthCheck
import no.nav.helse.dusseldorf.ktor.health.Healthy
import no.nav.helse.dusseldorf.ktor.health.Result
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val logger: Logger = LoggerFactory.getLogger("nav.S3Storage")
private const val BUCKET_NAME = "pleiepengerdokument"

class S3Storage(private val s3 : AmazonS3,
                private val expirationInDays : Int?) : Storage {

    override fun ready() {
        s3Operation (
            operation = { s3.getBucketLocation(BUCKET_NAME) }
        )
    }

    override fun hent(key: StorageKey): StorageValue? {
        val objectAsString = s3Operation(
            operation = { s3.getObjectAsString(BUCKET_NAME, key.value) },
            allowServiceException = true
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
                    operation = { s3.deleteObject(BUCKET_NAME, storageKey.value) }
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
            operation = { s3.putObject(BUCKET_NAME, key.value, value.value) }
        )
    }

    init {
        logger.info("Initialiserer S3 Storage.")
        ensureBucketExists()
        ensureProperBucketLifecycleConfiguration()
        logger.info("S3 Storage initialisert OK.")
    }


    private fun ensureBucketExists() {
        if (!s3Operation( operation = { s3.doesBucketExistV2(BUCKET_NAME) } )!!) {
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
                operation = { s3.setBucketLifecycleConfiguration(BUCKET_NAME, bucketLifecycleConfiguration(expirationInDays!!)) }
            )
            logger.trace("Liceyclye konfigurasjon lagret.")

        } else {
            logger.info("Sletter eventuelle aktive lifecycle konfigurasjoner.")
            s3Operation(
                operation = { s3.deleteBucketLifecycleConfiguration(BUCKET_NAME) }
            )
            logger.info("Sletting av licecycle konfigurasjoner OK.")
        }
    }

    private fun createBucket() {
        s3Operation(
            operation = { s3.createBucket(CreateBucketRequest(BUCKET_NAME).withCannedAcl(CannedAccessControlList.Private)) }
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
        allowServiceException : Boolean = false) : T? {
        return try {
            operation.invoke()
        } catch (cause : AmazonServiceException) {
            if (allowServiceException) return null
            else throw IllegalStateException("Fikk response fra S3, men en feil forekom på tjenestesiden. ${cause.message}", cause)
        } catch (cause: SdkClientException) {
            throw IllegalStateException("Fikk ikke response fra S3. ${cause.message}", cause)
        } catch (cause: Throwable) {
            throw IllegalStateException("Uventet feil ved tjenestekall mot S3.", cause)
        }
    }
}

class S3StorageHealthCheck(
    private val s3Storage: S3Storage
) : HealthCheck {
    private val logger: Logger = LoggerFactory.getLogger("nav.S3StorageHealthCheck")
    private val name = "S3StorageHealthCheck"

    override suspend fun check(): Result {
        return try {
            s3Storage.ready()
            Healthy(name = name, result = "Tilkobling mot S3 OK.")
        } catch (cause: Throwable) {
            logger.error("Feil ved tilkobling mot S3.", cause)
            Healthy(name = name, result = cause.message ?: "Feil ved tilkobling mot S3.")
        }
    }
}