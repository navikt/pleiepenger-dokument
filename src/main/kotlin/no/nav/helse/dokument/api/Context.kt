package no.nav.helse.dokument.api

import io.ktor.application.ApplicationCall
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.auth.principal
import no.nav.helse.aktoer.AktoerGateway
import no.nav.helse.aktoer.AktoerId
import no.nav.helse.aktoer.Fodselsnummer
import no.nav.helse.validering.Brudd
import no.nav.helse.validering.Valideringsfeil
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val logger: Logger = LoggerFactory.getLogger("nav.Context")

private const val AKTOER_ID_QUERY_PARAM = "aktoer_id"

private val manglerAktoerId = listOf(Brudd(
    parameter = AKTOER_ID_QUERY_PARAM,
    error = "Mangler query parameter for hvilken aktør det gjelder."
))


class Context(
    private val serviceAccountIssuer : String,
    private val sluttbrukerIssuer : String,
    private val aktoerGateway: AktoerGateway
) {
    fun erSluttbruker(call : ApplicationCall) : Boolean {
        return hentIssuer(call) == sluttbrukerIssuer
    }

    fun erServiceAccount(call : ApplicationCall) : Boolean {
        return hentIssuer(call) == serviceAccountIssuer
    }

    suspend fun hentAktoerId(call: ApplicationCall) : AktoerId {
        return when {
            erSluttbruker(call) -> {
                logger.trace("Henter AktørID fra fødselsnummer.")
                val fodselsnummer = Fodselsnummer(hentJwtPrincipal(call).payload.subject)
                aktoerGateway.getAktoerId(
                    fodselsnummer = fodselsnummer,
                    correlationId = call.request.getCorrelationId()
                )
            }
            erServiceAccount(call) -> {
                logger.trace("Henter AktørID fra query parameter.")
                val aktoerIdString = call.request.queryParameters[AKTOER_ID_QUERY_PARAM] ?: throw Valideringsfeil(manglerAktoerId)
                AktoerId(aktoerIdString)
            }
            else -> throw IllegalStateException("Er hverken sluttbruker eller service account.")
        }
    }

    private fun hentJwtPrincipal(call: ApplicationCall) : JWTPrincipal {
        return call.principal() ?: throw IllegalStateException("Principal ikke satt.")
    }

    private fun hentIssuer(call : ApplicationCall) : String {
        return hentJwtPrincipal(call).payload.issuer
    }
}