package no.nav.helse

import com.fasterxml.jackson.module.kotlin.readValue
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
import no.nav.helse.dokument.Dokument
import no.nav.helse.validering.Valideringsfeil
import org.junit.AfterClass
import org.junit.BeforeClass
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.test.*

private val logger: Logger = LoggerFactory.getLogger("nav.PleiepengerDokumentTest")

@KtorExperimentalAPI
class PleiepengerDokumentTest {

    @KtorExperimentalAPI
    private companion object {

        private val wireMockServer: WireMockServer = WiremockWrapper.bootstrap()
        private val authorizedAccessToken = Authorization.getAccessToken(wireMockServer.getServiceAccountIssuer(), wireMockServer.getSubject())
        private val objectMapper = ObjectMapper.server()


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
        val fnr = "29099012345"

        val url = engine.lasteOppDokument(
            token = authorizedAccessToken,
            fnr = fnr
        )
        val path = Url(url).fullPath

        with(engine) {

            handleRequest(HttpMethod.Get, path) {
                addHeader(HttpHeaders.Authorization, "Bearer $authorizedAccessToken")
                addHeader(HttpHeaders.XCorrelationId, "henter-dokument-som-service-account")
                addHeader("Nav-Personidenter", fnr)
            }.apply {
                assertEquals(HttpStatusCode.OK, response.status())
                assertEquals("image/jpeg", response.contentType().toString())
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
    fun `request med sluttbruker ID-Token fungerer`() {
        val fnr = "29099012345"
        val idToken = Authorization.getIdToken(wireMockServer.getEndUserIssuer(), fnr)

        val url = engine.lasteOppDokument(
            token = idToken,
            fileName = "test.pdf",
            tittel = "PDF"
        )

        val path = Url(url).fullPath

        with(engine) {
            handleRequest(HttpMethod.Get, path) {
                addHeader(HttpHeaders.Authorization, "Bearer $idToken")
                addHeader(HttpHeaders.XCorrelationId, "henter-dokument-som-sluttbruker")
            }.apply {
                assertEquals(HttpStatusCode.OK, response.status())
                assertEquals("application/pdf", response.contentType().toString())
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


    @Test
    fun `opplasting, henting og sletting av dokument som sytembruker`() {
        val fnr = "29099012345"
        with(engine) {
            val jpeg = "iPhone_6.jpg".fromResources()

            // LASTER OPP Dokument
            val url = lasteOppDokument(
                token = authorizedAccessToken,
                fnr = fnr,
                fileContent = jpeg,
                fileName = "iPhone_6.jpg",
                tittel = "Bilde av en iphone",
                contentType = "image/jpeg"

            )
            val path = Url(url).fullPath
            // HENTER OPPLASTET DOKUMENT
            handleRequest(HttpMethod.Get, path) {
                addHeader(HttpHeaders.Authorization, "Bearer $authorizedAccessToken")
                addHeader("Nav-Personidenter", fnr)
                addHeader(HttpHeaders.XCorrelationId, "henter-dokument-ok")

            }.apply {
                assertEquals(HttpStatusCode.OK, response.status())
                assertEquals(ContentType.Image.JPEG, response.contentType())
                assertTrue(Arrays.equals(jpeg, response.byteContent))

                // HENTER OPPLASTET DOKUMENT SOM JSON
                handleRequest(HttpMethod.Get, path) {
                    addHeader(HttpHeaders.Authorization, "Bearer $authorizedAccessToken")
                    addHeader("Nav-Personidenter", fnr)
                    addHeader(HttpHeaders.XCorrelationId, "henter-dokument-som-json-ok")
                    addHeader(HttpHeaders.Accept, "application/json")
                }.apply {

                    assertEquals(HttpStatusCode.OK, response.status())
                    assertEquals("application/json; charset=UTF-8", response.contentType().toString())

                    val expected = Dokument(
                        content = jpeg,
                        contentType = "image/jpeg",
                        tittel = "Bilde av en iphone"
                    )
                    val actual = objectMapper.readValue<Dokument>(response.content!!)
                    assertEquals(expected, actual)
                    // SLETTER OPPLASTET DOKUMENT
                    handleRequest(HttpMethod.Delete, path) {
                        addHeader(HttpHeaders.Authorization, "Bearer $authorizedAccessToken")
                        addHeader("Nav-Personidenter", fnr)
                        addHeader(HttpHeaders.XCorrelationId, "sletter-dokument-ok")

                    }.apply {
                        assertEquals(HttpStatusCode.NoContent, response.status())

                        // VERIFISERER AT DOKMENT ER SLETTET
                        handleRequest(HttpMethod.Get, path) {
                            addHeader(HttpHeaders.Authorization, "Bearer $authorizedAccessToken")
                            addHeader("Nav-Personidenter", fnr)
                            addHeader(HttpHeaders.XCorrelationId, "henter-dokument-ikke-funnet")

                        }.apply {
                            assertEquals(HttpStatusCode.NotFound, response.status())
                        }
                    }
                }
            }
        }
    }
}