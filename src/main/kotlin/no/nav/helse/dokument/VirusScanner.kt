package no.nav.helse.dokument

import com.github.kittinunf.fuel.coroutines.awaitStringResponseResult
import com.github.kittinunf.fuel.httpPut
import io.prometheus.client.Counter
import org.json.JSONArray
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL
import java.time.Duration

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
        private val logger = LoggerFactory.getLogger("nav.ClamAvGateway")
        private val timeout = Duration.ofSeconds(2).toMillisPart()
        private val headers = mapOf(
            "Accept" to "application/json"
        )
    }

    internal suspend fun scan(dokument: Dokument) : ScanResult {

        val (_, _, res) = url.toString()
            .httpPut()
            .body(dokument.content)
            .timeout(timeout)
            .header(headers)
            .responseString()
            //.awaitStringResponseResult()

        return res.fold(
            { success ->
                try {
                    JSONArray(success)
                        .getJSONObject(0)
                        .getString("Result")
                        .tilScanResult()
                } catch (cause: Throwable) {
                    logger.error("Response fra virusscan ikke JSON. Response = '$success'", cause)
                    ScanResult.SCAN_ERROR
                }

            }, // TOD: Result
            { error ->
                logger.error("Feil ved virusscan. $error")
                ScanResult.SCAN_ERROR
            }
        )
    }
}

private fun String.tilScanResult(): ScanResult {
    return if (toUpperCase() == "OK") ScanResult.CLEAN else ScanResult.INFECTED
}
