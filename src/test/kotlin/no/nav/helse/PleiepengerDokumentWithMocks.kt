package no.nav.helse

import io.ktor.server.testing.withApplication
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val logger: Logger = LoggerFactory.getLogger("nav.PleiepengerDokumentWithMocks")

class PleiepengerDokumentWithMocks {
    companion object {

        @JvmStatic
        fun main(args: Array<String>) {

            val wireMockServer = WiremockWrapper.bootstrap(port = 8121)

            val testArgs = TestConfiguration.asArray(TestConfiguration.asMap(
                wireMockServer = wireMockServer,
                port = 8122
            ))

            Runtime.getRuntime().addShutdownHook(object : Thread() {
                override fun run() {
                    logger.info("Tearing down")
                    wireMockServer.stop()
                    logger.info("Tear down complete")
                }
            })

            withApplication { no.nav.helse.main(testArgs) }
        }
    }
}