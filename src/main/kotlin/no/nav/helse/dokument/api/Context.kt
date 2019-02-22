package no.nav.helse.dokument.api

import io.ktor.application.ApplicationCall
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.auth.principal
import io.ktor.request.header
import no.nav.helse.dokument.Fodselsnummer
import no.nav.helse.validering.Brudd
import no.nav.helse.validering.Valideringsfeil

private const val FODSELSNUMMER_HEADER = "Nav-Personidenter"
private val manglerHeaderBrudd = listOf(Brudd(
    parameter = FODSELSNUMMER_HEADER,
    error = "Headern må være satt til personens fødselsnummer"
))

class Context(
    private val serviceAccountIssuer : String,
    private val sluttbrukerIssuer : String
) {
    fun erSluttbruker(call : ApplicationCall) : Boolean {
        return hentIssuer(call) == sluttbrukerIssuer
    }

    fun erServiceAccount(call : ApplicationCall) : Boolean {
        return hentIssuer(call) == serviceAccountIssuer
    }

    fun hentFodselsnummer(call : ApplicationCall) : Fodselsnummer {
        return when {
            erSluttbruker(call) -> Fodselsnummer(hentJwtPrincipal(call).payload.subject)
            erServiceAccount(call) -> {
                val fodselsnummer = call.request.header(FODSELSNUMMER_HEADER) ?: throw Valideringsfeil(manglerHeaderBrudd)
                Fodselsnummer(fodselsnummer)
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