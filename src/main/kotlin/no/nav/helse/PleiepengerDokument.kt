package no.nav.helse

import io.ktor.application.*
import io.ktor.auth.Authentication
import io.ktor.auth.authenticate
import io.ktor.features.*
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.metrics.micrometer.MicrometerMetrics
import io.ktor.routing.Routing
import io.ktor.util.KtorExperimentalAPI
import io.prometheus.client.hotspot.DefaultExports
import no.nav.helse.dokument.crypto.Cryptography
import no.nav.helse.dokument.DokumentService
import no.nav.helse.dokument.VirusScanner
import no.nav.helse.dokument.storage.S3Storage
import no.nav.helse.dokument.storage.S3StorageHealthCheck
import no.nav.helse.dokument.api.*
import no.nav.helse.dokument.eier.EierResolver
import no.nav.helse.dusseldorf.ktor.auth.*
import no.nav.helse.dusseldorf.ktor.client.HttpRequestHealthCheck
import no.nav.helse.dusseldorf.ktor.client.HttpRequestHealthConfig
import no.nav.helse.dusseldorf.ktor.core.*
import no.nav.helse.dusseldorf.ktor.health.HealthRoute
import no.nav.helse.dusseldorf.ktor.health.HealthService
import no.nav.helse.dusseldorf.ktor.jackson.JacksonStatusPages
import no.nav.helse.dusseldorf.ktor.jackson.dusseldorfConfigured
import no.nav.helse.dusseldorf.ktor.metrics.MetricsRoute
import no.nav.helse.dusseldorf.ktor.metrics.init
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URI

private val logger: Logger = LoggerFactory.getLogger("nav.PleiepengerDokument") //TODO: Rename logger

fun main(args: Array<String>): Unit  = io.ktor.server.netty.EngineMain.main(args)

@KtorExperimentalAPI
fun Application.pleiepengerDokument() { //TODO: Rename klasse og fil.
    val appId = environment.config.id()
    logProxyProperties()
    DefaultExports.initialize()

    val configuration = Configuration(environment.config)
    val issuers = configuration.issuers()

    install(Authentication) {
        multipleJwtIssuers(issuers)
    }

    install(ContentNegotiation) {
        jackson {
            dusseldorfConfigured()
        }
    }

    install(StatusPages) {
        DefaultStatusPages()
        JacksonStatusPages()
        AuthStatusPages()
    }

    val s3Storage = S3Storage(
        s3 = configuration.getS3Configured(),
        expirationInDays = configuration.getS3ExpirationInDays()
    )

    install(CallIdRequired)

    install(Routing) {
        authenticate(*issuers.allIssuers()) {
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
                        hentEierFra = configuration.hentEierFra()
                    ),
                    contentTypeService = ContentTypeService(),
                    baseUrl = configuration.getBaseUrl()
                )
            }
        }

        DefaultProbeRoutes()
        MetricsRoute()
        HealthRoute(
            healthService = HealthService(
                setOf(
                    S3StorageHealthCheck(
                        s3Storage = s3Storage
                    ),
                    HttpRequestHealthCheck(issuers.healthCheckMap())
                )
            )
        )
    }

    install(MicrometerMetrics) {
        init(appId)
    }

    install(CallId) {
        fromXCorrelationIdHeader()
    }

    intercept(ApplicationCallPipeline.Monitoring) {
        call.request.log()
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

private fun Map<Issuer, Set<ClaimRule>>.healthCheckMap(
    initial : MutableMap<URI, HttpRequestHealthConfig> = mutableMapOf()
) : Map<URI, HttpRequestHealthConfig> {
    forEach { issuer, _ ->
        initial[issuer.jwksUri()] = HttpRequestHealthConfig(expectedStatus = HttpStatusCode.OK, includeExpectedStatusEntity = false)
    }
    return initial.toMap()
}