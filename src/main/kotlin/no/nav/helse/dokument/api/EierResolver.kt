package no.nav.helse.dokument.api

import io.ktor.application.ApplicationCall
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.auth.principal
import no.nav.helse.Eier
import org.slf4j.Logger
import org.slf4j.LoggerFactory


private val logger: Logger = LoggerFactory.getLogger("nav.EierResolver")

class EierResolver(
    authorizedSubjects : List<String>
) {
    private val tillattEierFraQuery =
        authorizedSubjects.isNotEmpty() // Kun tillatt å hente eier fra query når det er white listed subjects

    init {
        logger.info("TillatEierFraQuery=$tillattEierFraQuery")
    }

    fun hentEier(call: ApplicationCall) : Eier {
        var eierId : String? = null
        if (tillattEierFraQuery) {
            logger.trace("Ser om det er en query parameter 'eier' som skal brukes")
            eierId = call.request.queryParameters["eier"]
        }
        return if (!eierId.isNullOrEmpty())  {
            logger.trace("Bruker eier fra query parameter 'eier'")
            Eier(eierId)
        } else  {
            logger.trace("Forsøker å hente eier fra JWT token 'subject' claim")
            val jwtPrincipal : JWTPrincipal = call.principal() ?: throw IllegalStateException("Principal ikke satt.")
            Eier(jwtPrincipal.payload.subject)
        }
    }
}