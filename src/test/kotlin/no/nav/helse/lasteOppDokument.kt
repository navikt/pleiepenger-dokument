package no.nav.helse

import io.ktor.http.*
import io.ktor.http.content.PartData
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import kotlinx.io.streams.asInput
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

fun TestApplicationEngine.lasteOppDokumentMultipart(
    token: String,
    fileName: String = "iPhone_6.jpg",
    fileContent: ByteArray = fileName.fromResources(),
    aktoerId: String? = null,
    tittel: String = "En eller annen tittel",
    contentType: String = if (fileName.endsWith("pdf")) "application/pdf" else "image/jpeg"
) : String {

    val boundary = "***dokument***"

    val path = if (aktoerId != null) "/v1/dokument?aktoer_id=$aktoerId" else "/v1/dokument"

    handleRequest(HttpMethod.Post, path) {
        addHeader(HttpHeaders.Authorization, "Bearer $token")
        addHeader(HttpHeaders.XCorrelationId, "laster-opp-doument-ok-multipart")
        addHeader(
            HttpHeaders.ContentType,
            ContentType.MultiPart.FormData.withParameter("boundary", boundary).toString()
        )
        setBody(
            boundary,
            listOf(
                PartData.FileItem(
                    { fileContent.inputStream().asInput() }, {},
                    headersOf(
                        Pair(
                            HttpHeaders.ContentType,
                            listOf(contentType)
                        ),
                        Pair(
                            HttpHeaders.ContentDisposition,
                            listOf(
                                ContentDisposition.File
                                    .withParameter(ContentDisposition.Parameters.Name, "content")
                                    .withParameter(ContentDisposition.Parameters.FileName, fileName)
                                    .toString()
                            )
                        )
                    )
                ),
                PartData.FormItem(
                    tittel, {},
                    headersOf(
                        HttpHeaders.ContentDisposition,
                        listOf(
                            ContentDisposition.Inline
                                .withParameter(ContentDisposition.Parameters.Name, "title")
                                .toString()
                        )
                    )
                )
            )
        )
    }.apply {
        assertEquals(HttpStatusCode.Created, response.status())
        val locationHeader= response.headers[HttpHeaders.Location]
        assertNotNull(locationHeader)
        return locationHeader
    }
}


fun TestApplicationEngine.lasteOppDokumentJson(
    token: String,
    fileName: String = "iPhone_6.jpg",
    fileContent: ByteArray = fileName.fromResources(),
    aktoerId: String? = null,
    tittel: String = "En eller annen tittel",
    contentType: String = if (fileName.endsWith("pdf")) "application/pdf" else "image/jpeg"
) : String {
    val path = if (aktoerId != null) "/v1/dokument?aktoer_id=$aktoerId" else "/v1/dokument"
    val base64encodedContent = Base64.getEncoder().encodeToString(fileContent)

    handleRequest(HttpMethod.Post, path) {
        addHeader(HttpHeaders.Authorization, "Bearer $token")
        addHeader(HttpHeaders.ContentType, "application/json")
        addHeader(HttpHeaders.XCorrelationId, "laster-opp-doument-ok-json")
        setBody("""
            {
                "content" : "$base64encodedContent",
                "content_type": "$contentType",
                "title" : "$tittel"
            }
            """.trimIndent()
        ) }.apply {
            assertEquals(HttpStatusCode.Created, response.status())
            val locationHeader= response.headers[HttpHeaders.Location]
            assertNotNull(locationHeader)
            return locationHeader
        }
}


fun String.fromResources() : ByteArray {
    return Thread.currentThread().contextClassLoader.getResource(this).readBytes()
}