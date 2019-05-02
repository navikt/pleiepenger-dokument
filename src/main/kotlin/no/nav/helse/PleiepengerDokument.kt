package no.nav.helse

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import io.ktor.application.*
import io.ktor.auth.Authentication
import io.ktor.auth.authenticate
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.auth.jwt.jwt
import io.ktor.features.*
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.routing.Routing
import io.ktor.util.KtorExperimentalAPI
import io.prometheus.client.hotspot.DefaultExports
import no.nav.helse.dokument.crypto.Cryptography
import no.nav.helse.dokument.DokumentService
import no.nav.helse.dokument.VirusScanner
import no.nav.helse.dokument.storage.S3Storage
import no.nav.helse.dokument.storage.S3StorageHealthCheck
import no.nav.helse.dokument.api.*
import no.nav.helse.dusseldorf.ktor.client.HttpRequestHealthCheck
import no.nav.helse.dusseldorf.ktor.core.*
import no.nav.helse.dusseldorf.ktor.health.HealthRoute
import no.nav.helse.dusseldorf.ktor.jackson.JacksonStatusPages
import no.nav.helse.dusseldorf.ktor.jackson.dusseldorfConfigured
import no.nav.helse.dusseldorf.ktor.metrics.CallMonitoring
import no.nav.helse.dusseldorf.ktor.metrics.MetricsRoute
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

private val logger: Logger = LoggerFactory.getLogger("nav.PleiepengerDokument")

fun main(args: Array<String>): Unit  = io.ktor.server.netty.EngineMain.main(args)

@KtorExperimentalAPI
fun Application.pleiepengerDokument() {
    val appId = environment.config.id()
    logProxyProperties()
    DefaultExports.initialize()

    val configuration = Configuration(environment.config)

    val authorizedSubjects = configuration.getAuthorizedSubjects()

    install(Authentication) {
        val issuer = configuration.getIssuer()
        val jwksProvider = JwkProviderBuilder(configuration.getJwksUrl()).buildConfigured()

        jwt {
            verifier(jwksProvider, issuer)
            realm = appId
            validate { credentials ->
                when {
                    authorizedSubjects.isEmpty() -> {
                        logger.trace("Authorized: Ettersom alle subject kan benytte tjenesten.")
                        return@validate JWTPrincipal(credentials.payload)
                    }
                    credentials.payload.subject in authorizedSubjects -> {
                        logger.trace("Authorized : ${credentials.payload.subject} er i listen [${authorizedSubjects.joinToString()}]")
                        return@validate JWTPrincipal(credentials.payload)
                    }
                    else -> {
                        logger.warn("Unauthorized : ${credentials.payload.subject} er ikke i listen [${authorizedSubjects.joinToString()}]")
                        return@validate null
                    }
                }
            }
        }
    }

    install(ContentNegotiation) {
        jackson {
            dusseldorfConfigured()
        }
    }

    install(StatusPages) {
        DefaultStatusPages()
        JacksonStatusPages()
    }

    val s3Storage = S3Storage(
        s3 = configuration.getS3Configured(),
        expirationInDays = configuration.getS3ExpirationInDays()
    )

    install(CallIdRequired)

    install(Routing) {
        authenticate {
            requiresCallId {
                dokumentV1Apis(
                    dokumentService = DokumentService(
                        cryptography = Cryptography(
                            encryptionPassphrase = configuration.getEncryptionPassphrase(),
                            decryptionPassphrases = configuration.getDecryptionPassphrases()
                        ),
                        storage = s3Storage,
                        virusScanner = getVirusScanner(configuration)
                    ),
                    eierResolver = EierResolver(
                        authorizedSubjects = authorizedSubjects
                    ),
                    contentTypeService = ContentTypeService()
                )
            }
        }

        DefaultProbeRoutes()
        MetricsRoute()
        HealthRoute(
            healthChecks = setOf(
                HttpRequestHealthCheck(
                    app = appId,
                    urlExpectedHttpStatusCodeMap = mapOf(
                        configuration.getJwksUrl() to HttpStatusCode.OK
                    )
                ),
                S3StorageHealthCheck(
                    s3Storage = s3Storage
                )
            )
        )
    }

    install(CallMonitoring) {
        app = appId
        overridePaths = mapOf(
            Regex("/v1/dokument/.*") to "/v1/dokument"
        )
    }

    install(CallId) {
        fromXCorrelationIdHeader()
    }

    install(CallLogging) {
        correlationIdAndRequestIdInMdc()
        logRequests()
    }
}

@KtorExperimentalAPI
private fun getVirusScanner(config: Configuration) : VirusScanner? {
    if (!config.enableVirusScan()) return null
    return VirusScanner(url = config.getVirusScanUrl())
}

private fun JwkProviderBuilder.buildConfigured() : JwkProvider {
    cached(10, 24, TimeUnit.HOURS)
    rateLimited(10, 1, TimeUnit.MINUTES)
    return build()
}