package no.nav.helse

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import no.nav.helse.dusseldorf.testsupport.jws.Azure
import org.skyscreamer.jsonassert.JSONAssert
import java.time.ZonedDateTime
import java.util.*
import kotlin.test.assertEquals

internal object CustomDokumentIdUtils {

    internal fun systembrukerLagreOgHent(
        engine: TestApplicationEngine,
        json: String,
        path: String
    ) {
        val content = Base64.getEncoder().encodeToString(json.toByteArray())

        val jsonRequest = """
            {
                "content": "$content",
                "content_type": "application/json",
                "title": "En Json"
            }
        """.trimIndent()

        val authorization = "Bearer ${Azure.V2_0.generateJwt(clientId= "azure-client-1", audience = "k9-dokument")}"
        val expires = ZonedDateTime.now().plusMinutes(1)

        with(engine) {
            handleRequest(HttpMethod.Put, path) {
                addHeader(HttpHeaders.Authorization, authorization)
                addHeader(HttpHeaders.XCorrelationId, UUID.randomUUID().toString())
                addHeader(HttpHeaders.ContentType, "application/json")
                addHeader("Expires", expires.toString())
                setBody(jsonRequest)
            }.apply {
                assertEquals(HttpStatusCode.NoContent, response.status())
            }
        }

        with(engine) {
            handleRequest(HttpMethod.Get, path) {
                addHeader(HttpHeaders.Authorization, authorization)
                addHeader(HttpHeaders.XCorrelationId, UUID.randomUUID().toString())
                addHeader(HttpHeaders.Accept, "application/json")
            }.apply {
                assertEquals(HttpStatusCode.OK, response.status())
                JSONAssert.assertEquals(jsonRequest, response.content!!, true)
            }
        }
    }

    internal fun systembrukerHent(
        engine: TestApplicationEngine,
        path: String,
        expectedHttpStatus: HttpStatusCode) {
        val authorization = "Bearer ${Azure.V2_0.generateJwt(clientId= "azure-client-1", audience = "k9-dokument")}"

        with(engine) {
            handleRequest(HttpMethod.Get, path) {
                addHeader(HttpHeaders.Authorization, authorization)
                addHeader(HttpHeaders.XCorrelationId, UUID.randomUUID().toString())
                addHeader(HttpHeaders.Accept, "application/json")
            }.apply {
                assertEquals(expectedHttpStatus, response.status())
            }
        }
    }
}