package no.nav.helse

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.extension.Extension
import no.nav.security.oidc.test.support.JwkGenerator
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val logger: Logger = LoggerFactory.getLogger("nav.WiremockWrapper")
private const val jwkSetPath = "/auth-mock/jwk-set"
private const val tokenPath = "/auth-mock/token"
private const val getAccessTokenPath = "/auth-mock/get-test-access-token"
private const val subject = "srvpps-prosessering"


object WiremockWrapper {

    fun bootstrap(
        port: Int? = null,
        extensions : Array<Extension> = arrayOf()) : WireMockServer {

        val wireMockConfiguration = WireMockConfiguration.options()

        extensions.forEach {
            wireMockConfiguration.extensions(it)
        }

        if (port == null) {
            wireMockConfiguration.dynamicPort()
        } else {
            wireMockConfiguration.port(port)
        }

        val wireMockServer = WireMockServer(wireMockConfiguration)

        wireMockServer.start()
        WireMock.configureFor(wireMockServer.port())

        stubGetSystembrukerToken()
        stubJwkSet()

        provideGetAccessTokenEndPoint(wireMockServer.getServiceAccountIssuer())

        logger.info("Mock available on '{}'", wireMockServer.baseUrl())
        return wireMockServer
    }

    private fun stubGetSystembrukerToken() {
        WireMock.stubFor(
            WireMock.get(WireMock.urlPathMatching(".*$tokenPath.*"))
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"i-am-an-access-token\", \"expires_in\": 5000}")
                )
        )
    }

    private fun stubJwkSet() {
        WireMock.stubFor(
            WireMock.get(WireMock.urlPathMatching(".*$jwkSetPath.*"))
                .willReturn(
                    WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withStatus(200)
                        .withBody(WiremockWrapper::class.java.getResource(JwkGenerator.DEFAULT_JWKSET_FILE).readText())
                )
        )
    }

    private fun provideGetAccessTokenEndPoint(issuer: String) {
        val jwt = Authorization.getAccessToken(issuer, subject)
        WireMock.stubFor(
            WireMock.get(WireMock.urlPathMatching(".*$getAccessTokenPath.*"))
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"$jwt\", \"expires_in\": 5000}")
                )
        )
    }
}

fun WireMockServer.getServiceAccountJwksUrl() : String {
    return baseUrl() + jwkSetPath
}

fun WireMockServer.getServiceAccountIssuer() : String {
    return baseUrl() + "/service-account"
}

fun WireMockServer.getEndUserJwksUrl() : String {
    return baseUrl() + jwkSetPath
}

fun WireMockServer.getEndUserIssuer() : String {
    return baseUrl() + "/end-user"
}

fun WireMockServer.getSubject() : String {
    return subject
}

