package no.nav.helse

import com.github.tomakehurst.wiremock.WireMockServer
import com.typesafe.config.ConfigFactory
import io.ktor.config.ApplicationConfig
import io.ktor.config.HoconApplicationConfig
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.createTestEnvironment
import io.ktor.server.testing.handleRequest
import io.ktor.util.KtorExperimentalAPI
import no.nav.helse.validering.Valideringsfeil
import org.junit.AfterClass
import org.junit.BeforeClass
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.test.*

private val logger: Logger = LoggerFactory.getLogger("nav.PleiepengerDokumentTest")

@KtorExperimentalAPI
class PleiepengerDokumentTest {

    @KtorExperimentalAPI
    private companion object {

        private val wireMockServer: WireMockServer = WiremockWrapper.bootstrap()
        private val authorizedAccessToken = Authorization.getAccessToken(wireMockServer.getServiceAccountIssuer(), wireMockServer.getSubject())


        fun getConfig() : ApplicationConfig {
            val fileConfig = ConfigFactory.load()
            val testConfig = ConfigFactory.parseMap(TestConfiguration.asMap(wireMockServer = wireMockServer))
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
    fun `request med service account Access Token fungerer`() {
        with(engine) {
            handleRequest(HttpMethod.Get, "/v1/dokument/1234") {
                addHeader(HttpHeaders.Authorization, "Bearer $authorizedAccessToken")
                addHeader(HttpHeaders.XCorrelationId, "123")
                addHeader("Nav-Personidenter", "29099012345")
            }.apply {
                assertEquals(HttpStatusCode.OK, response.status())
            }
        }
    }

    @Test(expected = Valideringsfeil::class)
    fun `request med service account Access Token og uten fodselsnummer som header feiler`() {
        with(engine) {
            handleRequest(HttpMethod.Get, "/v1/dokument/12345") {
                addHeader(HttpHeaders.Authorization, "Bearer $authorizedAccessToken")
                addHeader(HttpHeaders.XCorrelationId, "123")
            }.apply {
                assertEquals(HttpStatusCode.OK, response.status())
            }
        }
    }

    @Test
    fun `request med sluttbruker  ID-Token fungerer`() {
        val fnr = "29099012345"
        val idToken = Authorization.getIdToken(wireMockServer.getEndUserIssuer(), fnr)
        with(engine) {
            handleRequest(HttpMethod.Get, "/v1/dokument/1234") {
                addHeader(HttpHeaders.Authorization, "Bearer $idToken")
                addHeader(HttpHeaders.XCorrelationId, "123")
                addHeader(HttpHeaders.Accept, "application/json")
            }.apply {
                assertEquals(HttpStatusCode.OK, response.status())
            }
        }
    }

    @Test
    fun `request med token utstedt av en annen issuer feiler`() {
        val fnr = "29099012345"
        val idToken = Authorization.getIdToken("http://localhost:8080/en-anne-issuer", fnr)
        with(engine) {
            handleRequest(HttpMethod.Get, "/v1/dokument/1234567") {
                addHeader(HttpHeaders.Authorization, "Bearer $idToken")
                addHeader(HttpHeaders.XCorrelationId, "123")
            }.apply {
                assertEquals(HttpStatusCode.Unauthorized, response.status())
            }
        }
    }

    @Test
    fun `request uten token feiler`() {
        with(engine) {
            handleRequest(HttpMethod.Get, "/v1/dokument/123456789") {
                addHeader(HttpHeaders.XCorrelationId, "123")
            }.apply {
                assertEquals(HttpStatusCode.Unauthorized, response.status())
            }
        }
    }

    // Test: Hente dokument som ikke finnes = 404
    // Test: Slette dokument som ikke finnes = 404
    // Test: Slette dokment som finnes = 204
}