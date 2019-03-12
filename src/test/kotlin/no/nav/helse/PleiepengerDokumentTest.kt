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
        private val authorizedServiceAccountAccessToken = Authorization.getAccessToken(wireMockServer.getIssuer(), "srvpleiepenger-joark")
        private val objectMapper = ObjectMapper.server()
        private val s3 = S3()


        fun getConfig() : ApplicationConfig {
            val fileConfig = ConfigFactory.load()
            val testConfig = ConfigFactory.parseMap(TestConfiguration.asMap(
                wireMockServer = wireMockServer,
                s3 = s3
            ))
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
            s3.stop()
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
                        handleRequest(HttpMethod.Get, "/isready-deep") {}.apply {
                            assertEquals(HttpStatusCode.OK, response.status())
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `request med service account Access Token fungerer`() {
        val aktoerId = "12345"

        val url = engine.lasteOppDokumentMultipart(
            token = authorizedServiceAccountAccessToken
        )
        val path = Url(url).fullPath

        with(engine) {

            handleRequest(HttpMethod.Get, "$path?aktoer_id=$aktoerId") {
                addHeader(HttpHeaders.Authorization, "Bearer $authorizedServiceAccountAccessToken")
                addHeader(HttpHeaders.XCorrelationId, "henter-dokument-som-service-account")
            }.apply {
                assertEquals(HttpStatusCode.OK, response.status())
                assertEquals("image/jpeg", response.contentType().toString())
            }
        }
    }


    @Test
    fun `request med sluttbruker ID-Token fungerer`() {
        val fnr = "29099912345"

        val idToken = Authorization.getIdToken(wireMockServer.getIssuer(), fnr)

        val url = engine.lasteOppDokumentMultipart(
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
    fun `request med sluttbruker ID-Token for annen bruker fungerer ikke`() {
        val fnrLagre = "29099012345"
        val fnrHente = "29099067891"

        val idTokenLagre = Authorization.getIdToken(wireMockServer.getIssuer(), fnrLagre)
        val idTokenHente = Authorization.getIdToken(wireMockServer.getIssuer(), fnrHente)

        val url = engine.lasteOppDokumentMultipart(
            token = idTokenLagre,
            fileName = "test.pdf",
            tittel = "PDF"
        )

        val path = Url(url).fullPath

        with(engine) {
            handleRequest(HttpMethod.Get, path) {
                addHeader(HttpHeaders.Authorization, "Bearer $idTokenHente")
                addHeader(HttpHeaders.XCorrelationId, "henter-dokument-som-feil-sluttbruker")
            }.apply {
                assertEquals(HttpStatusCode.NotFound, response.status())
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
        val aktoerId = "134679"
        with(engine) {
            val jpeg = "iPhone_6.jpg".fromResources()

            // LASTER OPP Dokument
            val url = lasteOppDokumentMultipart(
                token = authorizedServiceAccountAccessToken,
                fileContent = jpeg,
                fileName = "iPhone_6.jpg",
                tittel = "Bilde av en iphone",
                contentType = "image/jpeg"

            )
            val path = "${Url(url).fullPath}?aktoer_id=$aktoerId"
            // HENTER OPPLASTET DOKUMENT
            handleRequest(HttpMethod.Get, path) {
                addHeader(HttpHeaders.Authorization, "Bearer $authorizedServiceAccountAccessToken")
                addHeader(HttpHeaders.XCorrelationId, "henter-dokument-ok")

            }.apply {
                assertEquals(HttpStatusCode.OK, response.status())
                assertEquals(ContentType.Image.JPEG, response.contentType())
                assertTrue(Arrays.equals(jpeg, response.byteContent))

                // HENTER OPPLASTET DOKUMENT SOM JSON
                handleRequest(HttpMethod.Get, path) {
                    addHeader(HttpHeaders.Authorization, "Bearer $authorizedServiceAccountAccessToken")
                    addHeader(HttpHeaders.XCorrelationId, "henter-dokument-som-json-ok")
                    addHeader(HttpHeaders.Accept, "application/json")
                }.apply {

                    assertEquals(HttpStatusCode.OK, response.status())
                    assertEquals("application/json; charset=UTF-8", response.contentType().toString())

                    val expected = Dokument(
                        content = jpeg,
                        contentType = "image/jpeg",
                        title = "Bilde av en iphone"
                    )
                    val actual = objectMapper.readValue<Dokument>(response.content!!)
                    assertEquals(expected, actual)
                    // SLETTER OPPLASTET DOKUMENT
                    handleRequest(HttpMethod.Delete, path) {
                        addHeader(HttpHeaders.Authorization, "Bearer $authorizedServiceAccountAccessToken")
                        addHeader(HttpHeaders.XCorrelationId, "sletter-dokument-ok")

                    }.apply {
                        assertEquals(HttpStatusCode.NoContent, response.status())

                        // VERIFISERER AT DOKMENT ER SLETTET
                        handleRequest(HttpMethod.Get, path) {
                            addHeader(HttpHeaders.Authorization, "Bearer $authorizedServiceAccountAccessToken")
                            addHeader(HttpHeaders.XCorrelationId, "henter-dokument-ikke-funnet")

                        }.apply {
                            assertEquals(HttpStatusCode.NotFound, response.status())
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `lagring og dokumenter som json istedenfor multipart fungerer`() {
        val aktoerId = "182489"
        with(engine) {
            val jpeg = "iPhone_6.jpg".fromResources()

            // LASTER OPP DOKUMENT
            val url = lasteOppDokumentJson(
                token = authorizedServiceAccountAccessToken,
                fileContent = jpeg,
                fileName = "iPhone_6.jpg",
                tittel = "Bilde av en iphone",
                contentType = "image/jpeg"

            )
            val path = "${Url(url).fullPath}?aktoer_id=$aktoerId"

            // HENTER OPPLASTET DOKUMENT
            handleRequest(HttpMethod.Get, path) {
                addHeader(HttpHeaders.Authorization, "Bearer $authorizedServiceAccountAccessToken")
                addHeader(HttpHeaders.XCorrelationId, "henter-dokument-ok-etter-opplasting-json")

            }.apply {
                assertEquals(HttpStatusCode.OK, response.status())
                assertEquals(ContentType.Image.JPEG, response.contentType())
                assertTrue(Arrays.equals(jpeg, response.byteContent))
            }
        }
    }
}

