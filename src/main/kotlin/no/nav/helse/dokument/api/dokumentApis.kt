package no.nav.helse.dokument.api

import io.ktor.application.call
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.request.ApplicationRequest
import io.ktor.request.header
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.post
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val logger: Logger = LoggerFactory.getLogger("nav.dokumentApis")

fun Route.dokumentApis(
    context: Context
) {

    post("v1/dokument") {
        call.request.ensureCorrelationId()
        logger.info("ErServiceAccount=${context.erServiceAccount(call)}")
        logger.info("ErSluttBruker=${context.erSluttbruker(call)}")
        logger.info("Fodselsnummer=${context.hentFodselsnummer(call)}")
        call.respond(HttpStatusCode.Created)
    }

    get("v1/dokument") {
        call.request.ensureCorrelationId()
        logger.info("ErServiceAccount=${context.erServiceAccount(call)}")
        logger.info("ErSluttBruker=${context.erSluttbruker(call)}")
        logger.info("Fodselsnummer=${context.hentFodselsnummer(call)}")
        call.respond(HttpStatusCode.OK)
    }
}

private fun ApplicationRequest.ensureCorrelationId() {
    header(HttpHeaders.XCorrelationId) ?: throw ManglerCorrelationId()
}