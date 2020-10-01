package no.nav.helse

import com.amazonaws.client.builder.AwsClientBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testcontainers.containers.localstack.LocalStackContainer

private val logger: Logger = LoggerFactory.getLogger("nav.S3")


class S3 : LocalStackContainer() {

    private val endpointConfiguration : AwsClientBuilder.EndpointConfiguration

    init {
        super.withServices(Service.S3)
        super.start()
        endpointConfiguration = super.getEndpointConfiguration(Service.S3)
        logger.info("AccessKey=${accessKey}")
        logger.info("SecretKey=${secretKey}")
        logger.info("SigningRegion=${getSigningRegion()}")
        logger.info("ServiceEndpoint=${getServiceEndpoint()}")

    }

    fun getSigningRegion() : String = endpointConfiguration.signingRegion
    fun getServiceEndpoint() : String = endpointConfiguration.serviceEndpoint
}
