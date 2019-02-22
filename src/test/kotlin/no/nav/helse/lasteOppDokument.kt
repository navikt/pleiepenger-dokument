package no.nav.helse

import io.ktor.http.*
import io.ktor.http.content.PartData
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import kotlinx.io.streams.asInput
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

fun TestApplicationEngine.lasteOppDokument(
    token: String,
    fileName: String,
    fileContent: ByteArray,
    fnr: String? = null,
    tittel: String,
    contentType: String
) : String {

    val boundary = "***dokument***"

    handleRequest(HttpMethod.Post, "/v1/dokument") {
        addHeader(HttpHeaders.Authorization, "Bearer $token")
        addHeader(HttpHeaders.XCorrelationId, "laster-opp-doument-ok")
        if (fnr != null) addHeader("Nav-Personidenter", fnr)
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

fun String.fromResources() : ByteArray {
    return Thread.currentThread().contextClassLoader.getResource(this).readBytes()
}