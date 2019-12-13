package no.nav.helse

import io.ktor.server.testing.withApplication
import no.nav.helse.dusseldorf.ktor.testsupport.asArguments
import no.nav.helse.dusseldorf.ktor.testsupport.wiremock.WireMockBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class K9DokumentWithMocks {
    companion object {

        private val logger: Logger = LoggerFactory.getLogger(K9DokumentWithMocks::class.java)

        @JvmStatic
        fun main(args: Array<String>) {

            val wireMockServer = WireMockBuilder()
                .withPort(8131)
                .withLoginServiceSupport()
                .withAzureSupport()
                .k9DokumentConfiguration()
                .build()
                .stubVirusScan()

            val s3 = S3()

            // Om true startes server kun med loginservice og 1 dag expiry på S3 bucket
            // Om false startes sever med azure & nais sts og uten expiry på S3 bucket
            val sluttBruker = true

            val testArgs = TestConfiguration.asMap(
                wireMockServer = wireMockServer,
                s3 = s3,
                port = 8132,
                konfigurerLoginService = sluttBruker,
                konfigurerAzure = !sluttBruker,
                s3ExpiryInDays = if (sluttBruker) 1 else null,
                azureAuthorizedClients = setOf("en-azure-client")
            ).asArguments()

            Runtime.getRuntime().addShutdownHook(object : Thread() {
                override fun run() {
                    logger.info("Tearing down")
                    wireMockServer.stop()
                    s3.stop()
                    logger.info("Tear down complete")
                }
            })

            withApplication { no.nav.helse.main(testArgs) }
        }
    }
}