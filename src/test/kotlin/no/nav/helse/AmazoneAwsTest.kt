package no.nav.helse

import com.typesafe.config.ConfigFactory
import io.ktor.config.HoconApplicationConfig
import io.ktor.util.KtorExperimentalAPI
import no.nav.helse.dusseldorf.ktor.testsupport.wiremock.WireMockBuilder
import no.nav.helse.dusseldorf.ktor.testsupport.wiremock.getNaisStsWellKnownUrl
import org.junit.Rule
import org.junit.Test
import org.junit.contrib.java.lang.system.EnvironmentVariables
import kotlin.test.assertEquals

@KtorExperimentalAPI
class AmazoneAwsTest {

    private companion object {
        val wireMockServer = WireMockBuilder()
            .withNaisStsSupport()
            .build()
    }

    @Rule @JvmField
    val environmentVariables = EnvironmentVariables()

    // https://github.com/aws/aws-sdk-java/issues/2070
    @Test
    fun `AmazonS3 Client blir initialisert selv om HTTP_PROXY & HTTPS_PROXY environment variable er satt uten userInfo-del`() {
        environmentVariables.set("NAIS_STS_DISCOVERY_ENDPOINT", wireMockServer.getNaisStsWellKnownUrl())

        val httpProxy = "http://localhost:8085"
        val httpsProxy = "http://localhost:8086"

        environmentVariables.set("HTTP_PROXY", httpProxy)
        environmentVariables.set("HTTPS_PROXY", httpsProxy)
        assertEquals(httpProxy, System.getenv("HTTP_PROXY"));
        assertEquals(httpsProxy, System.getenv("HTTPS_PROXY"));

        val fileConfig = ConfigFactory.load()
        val testConfig = ConfigFactory.parseMap(mapOf(
            "nav.storage.s3.access_key" to "accesskey",
            "nav.storage.s3.secret_key" to "secretkey",
            "nav.storage.s3.signing_region" to "us-east",
            "nav.storage.s3.service_endpoint" to "http://localhost:8087"
        ))

        val configuration = Configuration(HoconApplicationConfig(testConfig.withFallback(fileConfig)))

        configuration.getS3Configured()
    }
}