package no.nav.helse.dokument.api

import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.request.ApplicationRequest
import io.ktor.request.accept
import io.ktor.request.header
import io.ktor.response.respond
import io.ktor.response.respondBytes
import io.ktor.routing.Route
import io.ktor.routing.delete
import io.ktor.routing.get
import io.ktor.routing.post
import no.nav.helse.dokument.DokumentId
import no.nav.helse.dokument.DokumentService
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val logger: Logger = LoggerFactory.getLogger("nav.dokumentApis")

fun Route.dokumentV1Apis(
    context: Context,
    dokumentService: DokumentService
) {

    post("v1/dokument") {
        call.request.ensureCorrelationId()
        logger.trace("ErServiceAccount=${context.erServiceAccount(call)}")
        logger.trace("ErSluttBruker=${context.erSluttbruker(call)}")
        //dokumentService.lagreDokument()
        call.respond(HttpStatusCode.Created)
    }

    get("v1/dokument/{dokumentId}") {
        call.request.ensureCorrelationId()
        val dokumentId = call.dokumentId()
        val etterspurtJson = call.request.etterspurtJson()
        logger.info("DokumentId=$dokumentId")
        logger.trace("ErServiceAccount=${context.erServiceAccount(call)}")
        logger.trace("ErSluttBruker=${context.erSluttbruker(call)}")
        logger.trace("EtterspurtJson=$etterspurtJson")

        val dokument = dokumentService.hentDokument(
            dokumentId = call.dokumentId(),
            fodselsnummer = context.hentFodselsnummer(call)
        )

        logger.trace("FantDokment=${dokument != null}")

        when {
            dokument == null -> call.respond(HttpStatusCode.NotFound)
            etterspurtJson -> call.respond(HttpStatusCode.OK, dokument)
            else -> call.respondBytes(
                bytes = dokument.content,
                contentType = ContentType.parse(dokument.contentType),
                status = HttpStatusCode.OK
            )
        }
    }

    delete("v1/dokument/{dokumentId}") {
        call.request.ensureCorrelationId()
        val dokumentId = call.dokumentId()
        logger.info("DokumentId=$dokumentId")
        logger.trace("ErServiceAccount=${context.erServiceAccount(call)}")
        logger.trace("ErSluttBruker=${context.erSluttbruker(call)}")

        val result = dokumentService.slettDokument(
            dokumentId = dokumentId,
            fodselsnummer = context.hentFodselsnummer(call)
        )

        when {
            result -> call.respond(HttpStatusCode.NoContent)
            else -> call.respond(HttpStatusCode.NoContent)
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