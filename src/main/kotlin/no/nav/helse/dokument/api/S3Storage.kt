package no.nav.helse.dokument.api

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.AmazonS3Exception
import com.amazonaws.services.s3.model.BucketLifecycleConfiguration
import com.amazonaws.services.s3.model.CannedAccessControlList
import com.amazonaws.services.s3.model.CreateBucketRequest
import com.amazonaws.services.s3.model.lifecycle.LifecycleFilter
import no.nav.helse.dokument.Storage
import no.nav.helse.dokument.StorageKey
import no.nav.helse.dokument.StorageValue
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val logger: Logger = LoggerFactory.getLogger("nav.S3Storage")
private const val BUCKET_NAME = "pleiepenger-dokument"

class S3Storage(private val s3 : AmazonS3,
                private val expirationInDays : Int) : Storage {

    override fun hent(key: StorageKey): StorageValue? {
        return try {
            StorageValue(s3.getObjectAsString(BUCKET_NAME, key.value))
        } catch (cause : AmazonS3Exception) {
            logger.warn("Feil ved henting med key '${key.value}'. Mest sannsynlig finnes det ikke. Feilmelding = '${cause.message}'")
            null
        }
    }

    override fun slett(storageKey: StorageKey): Boolean {
        val value = hent(storageKey)
        return if (value == null) false else {
            s3.deleteObject(BUCKET_NAME, storageKey.value)
            true
        }
    }

    override fun lagre(key: StorageKey, value: StorageValue) {
        s3.putObject(BUCKET_NAME, key.value, value.value)
    }


    init {
        ensureBucketExists()
        ensureProperBucketLifecycleConfiguration()
    }


    private fun ensureBucketExists() {
        if (!s3.listBuckets().any { it.name == BUCKET_NAME }) {
            logger.info("Bucket $BUCKET_NAME finnes ikke fra før, oppretter.")
            createBucket()
            logger.info("Bucket opprettet.")
        } else {
            logger.info("Bucket $BUCKET_NAME finnes allerede.")
        }
    }

    private fun ensureProperBucketLifecycleConfiguration() {
        logger.trace("Konfigurerer lifecycle for bucket.")
        s3.setBucketLifecycleConfiguration(BUCKET_NAME, bucketLifecycleConfiguration(expirationInDays))
        logger.trace("Konfigurert.")
    }

    private fun createBucket() {
        s3.createBucket(
            CreateBucketRequest(BUCKET_NAME)
                .withCannedAcl(CannedAccessControlList.Private)
        )

    }

    private fun bucketLifecycleConfiguration(days: Int): BucketLifecycleConfiguration {
        logger.info("Expiry på bucket settes til $days dager.")
        val rules= BucketLifecycleConfiguration.Rule()
            .withId("$BUCKET_NAME-$days")
            .withFilter(LifecycleFilter())
            .withStatus(BucketLifecycleConfiguration.ENABLED)
            .withExpirationInDays(days)

        return BucketLifecycleConfiguration().withRules(rules)
    }

}