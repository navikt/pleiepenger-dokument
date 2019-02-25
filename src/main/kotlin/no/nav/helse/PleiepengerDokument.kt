package no.nav.helse

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.JWT
import io.ktor.application.*
import io.ktor.auth.Authentication
import io.ktor.auth.authenticate
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.auth.jwt.jwt
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.features.*
import io.ktor.http.HttpHeaders
import io.ktor.jackson.jackson
import io.ktor.request.header
import io.ktor.response.header
import io.ktor.routing.Routing
import io.ktor.util.KtorExperimentalAPI
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.hotspot.DefaultExports
import no.nav.helse.aktoer.AktoerGateway
import no.nav.helse.dokument.Cryptography
import no.nav.helse.dokument.DokumentService
import no.nav.helse.dokument.InMemoryStorage
import no.nav.helse.dokument.api.Context
import no.nav.helse.dokument.api.dokumentV1Apis
import no.nav.helse.dokument.api.metadataStatusPages
import no.nav.helse.systembruker.SystembrukerGateway
import no.nav.helse.systembruker.SystembrukerService
import no.nav.helse.validering.valideringStatusPages
import org.apache.http.impl.conn.SystemDefaultRoutePlanner
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.ProxySelector
import java.util.*
import java.util.concurrent.TimeUnit

private val logger: Logger = LoggerFactory.getLogger("nav.PleiepengerDokument")
private const val GENERATED_REQUEST_ID_PREFIX = "generated-"
private const val REALM = "pleiepenger-dokument"
private const val SERVICE_ACCCOUNT_AUTHENTICATION_PROVIDER = "service-account-authentication-provider"
private const val END_USER_AUTHENTICATION_PROVIDER = "end-user-authentication-provider"
private const val TOKEN_AUTH_SCHEME_PREFIX = "Bearer "

fun main(args: Array<String>): Unit  = io.ktor.server.netty.EngineMain.main(args)

@KtorExperimentalAPI
fun Application.pleiepengerDokument() {
    val collectorRegistry = CollectorRegistry.defaultRegistry
    DefaultExports.initialize()

    val configuration = Configuration(environment.config)
    configuration.logIndirectlyUsedConfiguration()

    val authorizedSystems = configuration.getAuthorizedSystemsForRestApi()

    val serviceAccountIssuer = configuration.getServiceAccountIssuer()
    val serviceAccountJwkProvider = JwkProviderBuilder(configuration.getServiceAccountJwksUrl()).buildConfigured()
    val endUserJwkProvider = JwkProviderBuilder(configuration.getEndUserJwksUrl()).buildConfigured()
    val endUserIssuer = configuration.getEndUserIssuer()

    val httpClient = HttpClient(Apache) {
        install(JsonFeature) {
            serializer = JacksonSerializer{
                ObjectMapper.server(this)
            }
        }
        engine {
            customizeClient { setProxyRoutePlanner() }
        }
    }


    install(Authentication) {
        jwt (name = SERVICE_ACCCOUNT_AUTHENTICATION_PROVIDER) {
            skipWhen {it.tokenIsSetAndIssuerIs(endUserIssuer)} // Verifiserer ikke Service Account Access-Token hvis issuer av token tilhører sluttbruker token-issuer.
            verifier(serviceAccountJwkProvider, serviceAccountIssuer)
            realm = REALM
            validate { credentials ->
                logger.info("Authorization attempt for Service Account ${credentials.payload.subject}")
                if (credentials.payload.subject in authorizedSystems) {
                    log.info("Authorization OK")
                    return@validate JWTPrincipal(credentials.payload)
                }
                logger.warn("Authorization failed")
                return@validate null
            }
        }
        jwt (name = END_USER_AUTHENTICATION_PROVIDER) {
            skipWhen {it.tokenIsSetAndIssuerIs(serviceAccountIssuer)} // Verifiserer ikke sluttbruker ID-Token hvis issuer av token tilhører Service Account token-issuer.
            verifier(endUserJwkProvider, endUserIssuer)
            realm = REALM
            validate { credentials ->
                return@validate JWTPrincipal(credentials.payload)
            }
        }
    }

    install(ContentNegotiation) {
        jackson {
            ObjectMapper.server(this)
        }
    }

    install(StatusPages) {
        defaultStatusPages()
        valideringStatusPages()
        metadataStatusPages()
    }

    val systembrukerService = SystembrukerService(
        systembrukerGateway = SystembrukerGateway(
            httpClient = httpClient,
            clientId = configuration.getServiceAccountClientId(),
            clientSecret = configuration.getServiceAccountClientSecret(),
            scopes = configuration.getServiceAccountScopes(),
            tokenUrl = configuration.getTokenUrl()
        )
    )

    val context = Context(
        serviceAccountIssuer = configuration.getServiceAccountIssuer(),
        sluttbrukerIssuer = configuration.getEndUserIssuer(),
        aktoerGateway = AktoerGateway(
            httpClient = httpClient,
            baseUrl = configuration.getAktoerRegisterBaseUrl(),
            systembrukerService = systembrukerService
        )
    )

    install(Routing) {
        authenticate(END_USER_AUTHENTICATION_PROVIDER, SERVICE_ACCCOUNT_AUTHENTICATION_PROVIDER) {
            dokumentV1Apis(
                context = context,
                dokumentService = DokumentService(
                    cryptography = Cryptography(
                        encryptionPassphrase = configuration.getEncryptionPassphrase(),
                        decryptionPassphrases = configuration.getDecryptionPassphrases()
                    ),
                    storage = InMemoryStorage(),
                    objectMapper = ObjectMapper.server()
                )
            )
        }

        monitoring(
            collectorRegistry = collectorRegistry
        )
    }

    install(CallId) {
        header(HttpHeaders.XCorrelationId)
    }

    install(CallLogging) {
        callIdMdc("correlation_id")
        mdc("request_id") { call ->
            val requestId = call.request.header(HttpHeaders.XRequestId)?.removePrefix(GENERATED_REQUEST_ID_PREFIX) ?: "$GENERATED_REQUEST_ID_PREFIX${UUID.randomUUID()}"
            call.response.header(HttpHeaders.XRequestId, requestId)
            requestId
        }
    }
}

private fun HttpAsyncClientBuilder.setProxyRoutePlanner() {
    setRoutePlanner(SystemDefaultRoutePlanner(ProxySelector.getDefault()))
}

private fun ApplicationCall.tokenIsSetAndIssuerIs(issuer: String): Boolean {
    val token = request.header(HttpHeaders.Authorization)?.removePrefix(TOKEN_AUTH_SCHEME_PREFIX) ?: return false
    return try { issuer == JWT.decode(token).issuer } catch (cause: Throwable) { false }
}

private fun JwkProviderBuilder.buildConfigured() : JwkProvider {
    cached(10, 24, TimeUnit.HOURS)
    rateLimited(10, 1, TimeUnit.MINUTES)
    return build()
}