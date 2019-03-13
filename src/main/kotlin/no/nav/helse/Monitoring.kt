package no.nav.helse

import io.ktor.application.call
import io.ktor.client.HttpClient
import io.ktor.client.call.call
import io.ktor.client.engine.apache.Apache
import io.ktor.client.response.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.response.respondTextWriter
import io.ktor.routing.Route
import io.ktor.routing.get
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.exporter.common.TextFormat
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import no.nav.helse.dokument.api.S3Storage
import org.apache.http.impl.conn.SystemDefaultRoutePlanner
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.ProxySelector
import java.net.URL
import java.util.*

private val logger: Logger = LoggerFactory.getLogger("nav.monitoring")

fun Route.monitoring(
    collectorRegistry: CollectorRegistry,
    s3Storage: S3Storage,
    pingUrls: Map<URL, HttpStatusCode>
) {

    val httpClient= HttpClient(Apache) {
        engine {
            socketTimeout = 1_000  // Max time between TCP packets - default 10 seconds
            connectTimeout = 1_000 // Max time to establish an HTTP connection - default 10 seconds
            customizeClient { setProxyRoutePlanner() }
        }
    }

    get("/isalive") {
        call.respondText("ALIVE")
    }

    get("/isready") {
        call.respondText("READY")
    }

    get("/health") {
        val success = mutableListOf<String>()
        val error = mutableListOf<String>()

        s3check(
            s3Storage = s3Storage,
            error = error,
            success = success
        )

        pingUrlsCheck(
            pingUrls = pingUrls,
            httpClient = httpClient,
            error = error,
            success = success
        )

        call.respond(if(error.isEmpty()) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable, mapOf(
            Pair("error", error),
            Pair("success", success))
        )
    }

    get("/metrics") {
        val names = call.request.queryParameters.getAll("name[]")?.toSet() ?: Collections.emptySet()
        call.respondTextWriter(ContentType.parse(TextFormat.CONTENT_TYPE_004)) {
            TextFormat.write004(this, collectorRegistry.filteredMetricFamilySamples(names))
        }
    }
}

suspend fun pingUrlsCheck(
    pingUrls: Map<URL, HttpStatusCode>,
    httpClient: HttpClient,
    error: MutableList<String>,
    success: MutableList<String>
) {
    val httpResponses = coroutineScope {
        val futures = mutableListOf<Deferred<WrappedHttpResponse>>()

        pingUrls.forEach { url, expectedHttpStatusCode ->
            futures.add(async {
                try {
                    httpClient.call(url).response.use {
                        WrappedHttpResponse(
                            response = it,
                            expectedHttpStatusCode = expectedHttpStatusCode,
                            url = url
                        )
                    }
                } catch (cause: Throwable) {
                    logger.error("Feil ved tilkobling mot $url.", cause)
                    WrappedHttpResponse(
                        expectedHttpStatusCode = expectedHttpStatusCode,
                        url = url,
                        error = cause.message
                    )
                }
            })

        }
        futures.awaitAll()
    }

    httpResponses.forEach {
        when {
            it.response == null -> error.add("Feil ved oppkobling mot ${it.url}. Forventet ${it.expectedHttpStatusCode} men fikk ingen response. ${it.error}.")
            it.response.status == it.expectedHttpStatusCode -> success.add("Tilkobling mot ${it.url} OK.")
            else -> error.add("Feil ved oppkobling mot ${it.url}. Forventet ${it.expectedHttpStatusCode} men fikk ${it.response.status}.")
        }
    }
}

private data class WrappedHttpResponse (
    val url : URL,
    val expectedHttpStatusCode: HttpStatusCode,
    val response : HttpResponse? = null,
    val error : String? = null
)

private fun s3check(
    s3Storage: S3Storage,
    error: MutableList<String>,
    success: MutableList<String>
) {
    try {
        s3Storage.ready()
        success.add("Tilkobling mot S3 OK.")
    } catch (cause: Throwable) {
        logger.error("Feil ved tilkobling mot S3.", cause)
        error.add("Feil ved tilkobling mot S3, ${cause.message}.")
    }
}

private fun HttpAsyncClientBuilder.setProxyRoutePlanner() {
    setRoutePlanner(SystemDefaultRoutePlanner(ProxySelector.getDefault()))
}