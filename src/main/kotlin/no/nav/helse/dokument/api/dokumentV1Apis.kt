package no.nav.helse.dokument.api

import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.features.origin
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.content.MultiPartData
import io.ktor.http.content.PartData
import io.ktor.http.content.readAllParts
import io.ktor.http.content.streamProvider
import io.ktor.request.*
import io.ktor.response.header
import io.ktor.response.respond
import io.ktor.response.respondBytes
import io.ktor.routing.Route
import io.ktor.routing.delete
import io.ktor.routing.get
import io.ktor.routing.post
import no.nav.helse.CorrelationId
import no.nav.helse.DefaultError
import no.nav.helse.dokument.Dokument
import no.nav.helse.dokument.DokumentId
import no.nav.helse.dokument.DokumentService
import no.nav.helse.validering.Brudd
import no.nav.helse.validering.Valideringsfeil
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URI

private val logger: Logger = LoggerFactory.getLogger("nav.dokumentApis")
private const val BASE_PATH = "v1/dokument"
private const val MAX_DOKUMENT_SIZE = 8 * 1024 * 1024

private val hasToBeMultipartType = URI.create("/errors/multipart-form-required")
private const val hasToBeMultipartTitle = "Requesten må være en 'multipart/form-data' request hvor en 'part' er en fil, har 'name=vedlegg' og har Content-Type header satt."

private val dokumentNotFoundType = URI.create("/errors/document-not-found")
private const val dokumentNotFoundTitle = "Inget dokument funnet med etterspurt ID."

private const val CONTENT_PART_NAME = "content"
private const val TITLE_PART_NAME = "title"

fun Route.dokumentV1Apis(
    context: Context,
    dokumentService: DokumentService
) {

    post(BASE_PATH) {
        call.request.ensureCorrelationId()
        logger.trace("ErServiceAccount=${context.erServiceAccount(call)}")
        logger.trace("ErSluttBruker=${context.erSluttbruker(call)}")

        if (!call.request.isFormMultipart()) {
            logger.warn("Ikke multipart request")
            call.respondHasToBeMultiPart()
        }

        logger.trace("Henter dokument fra requesten")
        val dokument = call.receiveMultipart().getDokument()

        logger.trace("Dokument hetent fra reqeuste, forsøker å lagre")
        val dokumentId = dokumentService.lagreDokument(
            dokument = dokument,
            aktoerId = context.hentAktoerId(call)
        )
        logger.trace("Dokument lagret.")

        call.respondCreatedDokument(dokumentId)
    }

    get("$BASE_PATH/{dokumentId}") {
        call.request.ensureCorrelationId()
        val dokumentId = call.dokumentId()
        val etterspurtJson = call.request.etterspurtJson()
        logger.info("DokumentId=$dokumentId")
        logger.trace("ErServiceAccount=${context.erServiceAccount(call)}")
        logger.trace("ErSluttBruker=${context.erSluttbruker(call)}")
        logger.trace("EtterspurtJson=$etterspurtJson")

        val dokument = dokumentService.hentDokument(
            dokumentId = call.dokumentId(),
            aktoerId = context.hentAktoerId(call)
        )

        logger.trace("FantDokment=${dokument != null}")

        when {
            dokument == null -> call.respondDokumentNotFound(dokumentId)
            etterspurtJson -> call.respond(HttpStatusCode.OK, dokument)
            else -> call.respondBytes(
                bytes = dokument.content,
                contentType = ContentType.parse(dokument.contentType),
                status = HttpStatusCode.OK
            )
        }
    }

    delete("$BASE_PATH/{dokumentId}") {
        call.request.ensureCorrelationId()
        val dokumentId = call.dokumentId()
        logger.info("DokumentId=$dokumentId")
        logger.trace("ErServiceAccount=${context.erServiceAccount(call)}")
        logger.trace("ErSluttBruker=${context.erSluttbruker(call)}")

        val result = dokumentService.slettDokument(
            dokumentId = dokumentId,
            aktoerId = context.hentAktoerId(call)
        )

        when {
            result -> call.respond(HttpStatusCode.NoContent)
            else -> call.respond(HttpStatusCode.NotFound)
        }
    }
}

private fun ApplicationCall.dokumentId() : DokumentId {
    return DokumentId(parameters["dokumentId"]!!)
}

private fun ApplicationRequest.etterspurtJson() : Boolean {
    return ContentType.Application.Json.toString() == accept()
}

private fun ApplicationRequest.ensureCorrelationId() {
    header(HttpHeaders.XCorrelationId) ?: throw ManglerCorrelationId()
}

fun ApplicationRequest.getCorrelationId() : CorrelationId = CorrelationId(header(HttpHeaders.XCorrelationId)!!)

private suspend fun MultiPartData.getDokument() : Dokument {
    var content : ByteArray? = null
    var contentType : String? = null
    var tittel : String? = null

    for (partData in readAllParts()) {
        if (partData is PartData.FileItem && CONTENT_PART_NAME == partData.name) {
            content = partData.streamProvider().readBytes()
            contentType = partData.contentType.toString()
        } else if (partData is PartData.FormItem && TITLE_PART_NAME == partData.name) {
            tittel = partData.value
        }
        partData.dispose()
    }

    val brudd = mutableListOf<Brudd>()
    if (content == null) brudd.add(Brudd(parameter = CONTENT_PART_NAME, error = "Fant ingen 'part' som er en fil."))
    if (content != null && content.size > MAX_DOKUMENT_SIZE) brudd.add(Brudd(parameter = CONTENT_PART_NAME, error = "Dokumentet er større en maks tillat 8MB."))
    if (contentType == null) brudd.add(Brudd(parameter = HttpHeaders.ContentType, error = "Ingen Content-Type satt på fil."))
    if (tittel == null) brudd.add(Brudd(parameter = TITLE_PART_NAME, error = "Fant ingen 'part' som er en form item."))
    return if (brudd.isEmpty()) Dokument(tittel = tittel!!, contentType = contentType!!, content = content!!) else throw Valideringsfeil(brudd)
}

private fun ApplicationRequest.isFormMultipart(): Boolean {
    return contentType().withoutParameters().match(ContentType.MultiPart.FormData)
}

private suspend fun ApplicationCall.respondHasToBeMultiPart() {
    respond(
        HttpStatusCode.BadRequest, DefaultError(
            status = HttpStatusCode.BadRequest.value,
            type = hasToBeMultipartType,
            title = hasToBeMultipartTitle
        )
    )
}

private suspend fun ApplicationCall.respondDokumentNotFound(dokumentId : DokumentId) {
    respond(
        HttpStatusCode.NotFound, DefaultError(
            status = HttpStatusCode.NotFound.value,
            type = dokumentNotFoundType,
            title = dokumentNotFoundTitle,
            detail = "Dokument med ID ${dokumentId.id} ikke funnet."
        )
    )
}

private suspend fun ApplicationCall.respondCreatedDokument(dokumentId: DokumentId) {
    val url = URLBuilder(getBaseUrlFromRequest()).path(BASE_PATH,dokumentId.id).build().toString()
    response.header(HttpHeaders.Location, url)
    respond(HttpStatusCode.Created)
}

private fun ApplicationCall.getBaseUrlFromRequest() : String {
    val host = request.origin.host
    val isLocalhost = "localhost".equals(host, ignoreCase = true)
    val scheme = if (isLocalhost) "http" else "https"
    val port = if (isLocalhost) ":${request.origin.port}" else ""
    return "$scheme://$host$port"
}