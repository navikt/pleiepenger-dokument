package no.nav.helse.dokument

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.logging.Logging
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.accept
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.prometheus.client.Counter
import no.nav.helse.dusseldorf.ktor.client.MonitoredHttpClient
import no.nav.helse.dusseldorf.ktor.client.setProxyRoutePlanner
import no.nav.helse.dusseldorf.ktor.client.sl4jLogger
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL

class VirusScanner(
    url: URL
) {
    private companion object {
        private val logger: Logger = LoggerFactory.getLogger("nav.VirusScanner")
        private val virusScannerCounter = Counter.build()
            .name("virus_scan_counter")
            .help("Teller for scanning for virus i dokumenter")
            .labelNames("result")
            .register()
    }

    private val gateway = ClamAvGateway(url)

    suspend fun scan(dokument: Dokument) {
        logger.info("Scanner Dokument for virus.")
        val scanResult = gateway.scan(dokument)
        logger.info("scanResult=$scanResult")
        virusScannerCounter.labels(scanResult.name).inc()
        if (ScanResult.INFECTED == scanResult) {
            throw IllegalStateException("Dokumentet inneholder virus.")
        }
        // CLEAN/SCAN_ERROR håndteres som OK
    }
}

private enum class ScanResult {
    CLEAN,
    INFECTED,
    SCAN_ERROR
}


private class ClamAvGateway(
    private val url: URL
) {
    private companion object {
        private val logger: Logger = LoggerFactory.getLogger("nav.ClamAvGateway")
        private fun configureObjectMapper(objectMapper: ObjectMapper) : ObjectMapper {
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            return objectMapper
        }
        private val objectMapper = configureObjectMapper(jacksonObjectMapper())
    }
    private val monitoredHttpClient = MonitoredHttpClient(
        source = "pleiepenger-dokument",
        destination = "clam-av",
        httpClient = HttpClient(Apache) {
            engine {
                customizeClient { setProxyRoutePlanner() }
                socketTimeout = 2_000  // Max time between TCP packets - default 10 seconds
                connectTimeout = 2_000 // Max time to establish an HTTP connection - default 10 seconds
            }
            install (Logging) {
                sl4jLogger("clam-av")
            }
        }
    )

    internal suspend fun scan(dokument: Dokument) : ScanResult {
        val httpRequest = HttpRequestBuilder()
        httpRequest.method = HttpMethod.Put
        httpRequest.accept(ContentType.Application.Json)
        httpRequest.body = dokument.content
        httpRequest.url(url)

        val response = try {
            monitoredHttpClient.request(httpRequest)
        } catch (cause: Throwable) {
            logger.error("Uventet feil ved virusscanning av dokument", cause)
            return ScanResult.SCAN_ERROR
        }

        val responseTree = try {
            val json = response.use { it.receive<String>() }
            objectMapper.readTree(json)
        } catch (cause: Throwable) {
            logger.error("Klarte ikke å mappe response", cause)
            return ScanResult.SCAN_ERROR
        }

        if (!responseTree.isArray || responseTree.size() != 1 || !responseTree.first().has("Result")) {
            logger.error("Uventet format på response etter scanning.")
            return ScanResult.SCAN_ERROR
        }

        val result = responseTree.first().get("Result").asText()
        return result.tilScanResult()
    }
}

private fun String.tilScanResult(): ScanResult {
    return if (toUpperCase() == "OK") ScanResult.CLEAN else ScanResult.INFECTED
}
