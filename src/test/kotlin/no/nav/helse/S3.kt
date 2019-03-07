package no.nav.helse

import com.amazonaws.client.builder.AwsClientBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testcontainers.containers.localstack.LocalStackContainer

private val logger: Logger = LoggerFactory.getLogger("nav.S3")


class S3 : LocalStackContainer() {

    private val endpointConfiguration : AwsClientBuilder.EndpointConfiguration

    init {
        super.withServices(LocalStackContainer.Service.S3)
        super.start()
        endpointConfiguration = super.getEndpointConfiguration(LocalStackContainer.Service.S3)
        logger.info("AccessKey=${getAccessKey()}")
        logger.info("SecretKey=${getSecretKey()}")
        logger.info("SigningRegion=${getSigningRegion()}")
        logger.info("ServiceEndpoint=${getServiceEndpoint()}")

    }

    fun getAccessKey() : String = super.getDefaultCredentialsProvider().credentials.awsAccessKeyId
    fun getSecretKey() : String = super.getDefaultCredentialsProvider().credentials.awsSecretKey
    fun getSigningRegion() : String = endpointConfiguration.signingRegion
    fun getServiceEndpoint() : String = endpointConfiguration.serviceEndpoint
}