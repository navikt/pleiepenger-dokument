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
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.post
import no.nav.helse.dokument.DokumentId
import no.nav.helse.dokument.DokumentStorage
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val logger: Logger = LoggerFactory.getLogger("nav.dokumentApis")

fun Route.dokumentApis(
    context: Context,
    dokumentStorage: DokumentStorage
) {

    post("v1/dokument") {
        call.request.ensureCorrelationId()
        logger.info("ErServiceAccount=${context.erServiceAccount(call)}")
        logger.info("ErSluttBruker=${context.erSluttbruker(call)}")
        logger.info("Fodselsnummer=${context.hentFodselsnummer(call)}")
        call.request.etterspurtJson()
        call.respond(HttpStatusCode.Created)
    }

    get("v1/dokument/{dokumentId}") {
        call.request.ensureCorrelationId()
        val dokumentId = call.dokumentId()
        logger.info("DokumentId=$dokumentId")
        logger.info("ErServiceAccount=${context.erServiceAccount(call)}")
        logger.info("ErSluttBruker=${context.erSluttbruker(call)}")
        logger.info("Fodselsnummer=${context.hentFodselsnummer(call)}")
        call.respond(HttpStatusCode.OK)
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