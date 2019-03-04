package no.nav.helse

import com.github.tomakehurst.wiremock.WireMockServer
import com.typesafe.config.ConfigFactory
import io.ktor.config.ApplicationConfig
import io.ktor.config.HoconApplicationConfig
import io.ktor.http.*
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.contentType
import io.ktor.server.testing.createTestEnvironment
import io.ktor.server.testing.handleRequest
import io.ktor.util.KtorExperimentalAPI
import org.junit.AfterClass
import org.junit.BeforeClass
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.test.Test
import kotlin.test.assertEquals

private val logger: Logger = LoggerFactory.getLogger("nav.SubjectLimitedPleiepengerDokumentTest")

@KtorExperimentalAPI
class SubjectLimitedPleiepengerDokumentTest {

    @KtorExperimentalAPI
    private companion object {

        private val wireMockServer: WireMockServer = WiremockWrapper.bootstrap()
        private val authorizedServiceAccountAccessToken =
            Authorization.getAccessToken(wireMockServer.getIssuer(), "srvpleiepenger-joark")
        private val unauthorizedServiceAccountAccessToken =
            Authorization.getAccessToken(wireMockServer.getIssuer(), "srvpleiepenger-notfound")

        private val objectMapper = ObjectMapper.server()


        fun getConfig(): ApplicationConfig {
            val fileConfig = ConfigFactory.load()
            val testConfig = ConfigFactory.parseMap(
                TestConfiguration.asMap(
                    wireMockServer = wireMockServer,
                    authorizedSubjects = "srvpleiepenger-joark"
                )
            )
            val mergedConfig = testConfig.withFallback(fileConfig)
            return HoconApplicationConfig(mergedConfig)
        }

        val engine = TestApplicationEngine(createTestEnvironment {
            config = getConfig()
        })


        @BeforeClass
        @JvmStatic
        fun buildUp() {
            engine.start(wait = true)
        }

        @AfterClass
        @JvmStatic
        fun tearDown() {
            logger.info("Tearing down")
            wireMockServer.stop()
            logger.info("Tear down complete")
        }
    }

    @Test
    fun `test isready, isalive og metrics`() {
        with(engine) {
            handleRequest(HttpMethod.Get, "/isready") {}.apply {
                assertEquals(HttpStatusCode.OK, response.status())
                handleRequest(HttpMethod.Get, "/isalive") {}.apply {
                    assertEquals(HttpStatusCode.OK, response.status())
                    handleRequest(HttpMethod.Get, "/metrics") {}.apply {
                        assertEquals(HttpStatusCode.OK, response.status())
                    }
                }
            }
        }
    }

    @Test
    fun `En authorized subject kan lagre og hente dokument`() {
        val url = engine.lasteOppDokumentMultipart(
            token = authorizedServiceAccountAccessToken
        )

        val path = Url(url).fullPath

        with(engine) {
            handleRequest(HttpMethod.Get, path) {
                addHeader(HttpHeaders.Authorization, "Bearer $authorizedServiceAccountAccessToken")
                addHeader(HttpHeaders.XCorrelationId, "henter-dokument-som-authorized-service-account")
            }.apply {
                assertEquals(HttpStatusCode.OK, response.status())
                assertEquals("image/jpeg", response.contentType().toString())
            }
        }
    }

    @Test
    fun `En unauthorized subject kan ikke lagre dokument`() {
        engine.lasteOppDokumentMultipart(
            token = unauthorizedServiceAccountAccessToken,
            expectedHttpStatusCode = HttpStatusCode.Unauthorized
        )
    }
}