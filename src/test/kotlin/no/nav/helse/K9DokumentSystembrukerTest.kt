package no.nav.helse

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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
import io.prometheus.client.CollectorRegistry
import no.nav.helse.dokument.Dokument
import no.nav.helse.dusseldorf.ktor.core.fromResources
import no.nav.helse.dusseldorf.ktor.jackson.dusseldorfConfigured
import no.nav.helse.dusseldorf.ktor.testsupport.jws.Azure
import no.nav.helse.dusseldorf.ktor.testsupport.wiremock.WireMockBuilder
import org.junit.AfterClass
import org.junit.BeforeClass
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.test.*

@KtorExperimentalAPI
class K9DokumentSystembrukerTest {

    @KtorExperimentalAPI
    private companion object {

        private val logger: Logger = LoggerFactory.getLogger(K9DokumentSystembrukerTest::class.java)

        private const val eier = "290990123456"
        private const val eierQueryString = "?eier=$eier"

        private val wireMockServer: WireMockServer = WireMockBuilder()
            .withAzureSupport()
            .k9DokumentConfiguration()
            .build()
            .stubVirusScan()

        private fun getAccessToken() = Azure.V1_0.generateJwt(clientId = "azure-client-1", audience = "k9-dokument")


        private val authorizedServiceAccountAccessToken = getAccessToken()
        private val objectMapper = jacksonObjectMapper().dusseldorfConfigured()
        private val s3 = S3()


        fun getConfig() : ApplicationConfig {
            val fileConfig = ConfigFactory.load()
            val testConfig = ConfigFactory.parseMap(TestConfiguration.asMap(
                wireMockServer = wireMockServer,
                s3 = s3,
                konfigurerAzure = true,
                azureAuthorizedClients = setOf("azure-client-1"),
                k9DokumentAzureClientId = "k9-dokument",
                s3ExpiryInDays = null
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
            CollectorRegistry.defaultRegistry.clear()
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
                        handleRequest(HttpMethod.Get, "/health") {}.apply {
                            assertEquals(HttpStatusCode.OK, response.status())
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `request med service account Access Token fungerer`() {

        val jpeg = "iPhone_6.jpg".fromResources().readBytes()

        val url = engine.lasteOppDokumentJson(
            token = authorizedServiceAccountAccessToken,
            fileContent = jpeg,
            fileName = "iPhone_6.jpg",
            tittel = "Bilde av en iphone",
            contentType = "image/jpeg",
            eier = eier
        )

        val path = "${Url(url).fullPath}$eierQueryString"

        with(engine) {

            handleRequest(HttpMethod.Get, path) {
                addHeader(HttpHeaders.Authorization, "Bearer ${getAccessToken()}")
                addHeader(HttpHeaders.XCorrelationId, "henter-dokument-som-service-account")
            }.apply {
                assertEquals(HttpStatusCode.OK, response.status())
                assertEquals("image/jpeg", response.contentType().toString())
            }
        }
    }

    @Test
    fun `opplasting, henting og sletting av dokument som azure v1 bruker`() {
        opplastingHentingOgSlettingFungerer(Azure.V1_0.generateJwt(clientId= "azure-client-1", audience = "k9-dokument"))
    }

    @Test
    fun `opplasting, henting og sletting av dokument som azure v2 bruker`() {
        opplastingHentingOgSlettingFungerer(Azure.V2_0.generateJwt(clientId= "azure-client-1", audience = "k9-dokument"))
    }

    private fun opplastingHentingOgSlettingFungerer(token: String) {
        with(engine) {
            val jpeg = "iPhone_6.jpg".fromResources().readBytes()

            // LASTER OPP Dokument
            val url = engine.lasteOppDokumentJson(
                token = token,
                fileContent = jpeg,
                fileName = "iPhone_6.jpg",
                tittel = "Bilde av en iphone",
                contentType = "image/jpeg",
                eier = eier
            )

            val path = "${Url(url).fullPath}$eierQueryString"

            // HENTER OPPLASTET DOKUMENT
            handleRequest(HttpMethod.Get, path) {
                addHeader(HttpHeaders.Authorization, "Bearer $token")
                addHeader(HttpHeaders.XCorrelationId, "henter-dokument-ok")

            }.apply {
                assertEquals(HttpStatusCode.OK, response.status())
                assertEquals(ContentType.Image.JPEG, response.contentType())
                assertTrue(Arrays.equals(jpeg, response.byteContent))

                // HENTER OPPLASTET DOKUMENT SOM JSON
                handleRequest(HttpMethod.Get, path) {
                    addHeader(HttpHeaders.Authorization, "Bearer $token")
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
                        addHeader(HttpHeaders.Authorization, "Bearer $token")
                        addHeader(HttpHeaders.XCorrelationId, "sletter-dokument-ok")

                    }.apply {
                        assertEquals(HttpStatusCode.NoContent, response.status())

                        // VERIFISERER AT DOKMENT ER SLETTET
                        handleRequest(HttpMethod.Get, path) {
                            addHeader(HttpHeaders.Authorization, "Bearer $token")
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
    fun `lagring og dokumenter som json istedenfor fungerer`() {
        with(engine) {
            val jpeg = "iPhone_6.jpg".fromResources().readBytes()

            // LASTER OPP DOKUMENT
            val url = lasteOppDokumentJson(
                token = authorizedServiceAccountAccessToken,
                fileContent = jpeg,
                fileName = "iPhone_6.jpg",
                tittel = "Bilde av en iphone",
                contentType = "image/jpeg",
                eier = eier

            )
            val path = "${Url(url).fullPath}$eierQueryString"

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

    @Test
    fun `lagring av application json dokument fungerer`() {
        with(engine) {
            val json = "jwkset.json".fromResources().readBytes()

            // LASTER OPP Dokument
            val url = lasteOppDokumentJson(
                token = authorizedServiceAccountAccessToken,
                fileContent = json,
                fileName = "jwkset.json",
                tittel = "Test JWK set",
                contentType = "application/json",
                eier = eier

            )
            val path = "${Url(url).fullPath}$eierQueryString"
            // HENTER OPPLASTET DOKUMENT
            handleRequest(HttpMethod.Get, path) {
                addHeader(HttpHeaders.Authorization, "Bearer $authorizedServiceAccountAccessToken")
                addHeader(HttpHeaders.XCorrelationId, "henter-dokument-ok")

            }.apply {
                assertEquals(HttpStatusCode.OK, response.status())
                assertEquals(ContentType.Application.Json, response.contentType())
                assertTrue(Arrays.equals(json, response.byteContent))
            }
        }
    }

    @Test
    fun `lagring av zip dokument feiler`() {
        with(engine) {
            val zip = "iphone_6.zip".fromResources().readBytes()

            // LASTER OPP Dokument
            lasteOppDokumentJson(
                eier = eier,
                token = authorizedServiceAccountAccessToken,
                fileContent = zip,
                fileName = "iphone_6.zip",
                tittel = "Zip av Bilde av en iphone",
                contentType = "application/zip",
                expectedHttpStatusCode = HttpStatusCode.BadRequest
            )
        }
    }

    @Test
    fun `lagring av zip dokument med content type pdf feiler`() {
        with(engine) {
            val zip = "iphone_6.zip".fromResources().readBytes()

            // LASTER OPP Dokument
            lasteOppDokumentJson(
                eier = eier,
                token = authorizedServiceAccountAccessToken,
                fileContent = zip,
                fileName = "iphone_6.zip",
                tittel = "Zip av Bilde av en iphone",
                contentType = "application/pdf",
                expectedHttpStatusCode = HttpStatusCode.BadRequest
            )
        }
    }

    @Test
    fun `En unauthorized subject kan ikke lagre dokument`() {
        engine.lasteOppDokumentMultipart(
            eier = eier,
            token = Azure.V2_0.generateJwt(clientId = "azure-client-2", audience = "k9-dokument"),
            expectedHttpStatusCode = HttpStatusCode.Forbidden
        )
    }

    @Test
    fun `Lagring og henting med eier satt som to forskjellige query parametere feiler`() {

        val url = engine.lasteOppDokumentJson(
            token = authorizedServiceAccountAccessToken,
            eier = "123"
        )

        val path = "${Url(url).fullPath}?eier=321"

        with(engine) {
            handleRequest(HttpMethod.Get, path) {
                addHeader(HttpHeaders.Authorization, "Bearer ${authorizedServiceAccountAccessToken}")
                addHeader(HttpHeaders.XCorrelationId, "henter-dokument-med-eier-query-mismatch")
            }.apply {
                assertEquals(HttpStatusCode.NotFound, response.status())
            }
        }
    }
}

