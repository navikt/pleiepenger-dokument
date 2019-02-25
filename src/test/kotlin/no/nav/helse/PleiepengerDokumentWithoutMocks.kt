package no.nav.helse

import io.ktor.server.testing.withApplication

/**
 *  - Mer leslig loggformat
 *  - Setter proxy settings
 *  - Starter på annen port
 */
class PleiepengerDokumentWithoutMocks {
    companion object {

        @JvmStatic
        fun main(args: Array<String>) {

            System.setProperty("http.nonProxyHosts", "localhost")
            System.setProperty("http.proxyHost", "127.0.0.1")
            System.setProperty("http.proxyPort", "5001")
            System.setProperty("https.proxyHost", "127.0.0.1")
            System.setProperty("https.proxyPort", "5001")

            val q1Args = TestConfiguration.asArray(TestConfiguration.asMap(
                port = 8133,
                serviceAccountJwkSetUrl = "https://security-token-service.nais.preprod.local/rest/v1/sts/jwks",
                serviceAccountIssuer = "https://security-token-service.nais.preprod.local",
                endUserJwkSetUrl = "https://login.microsoftonline.com/navtestb2c.onmicrosoft.com/discovery/v2.0/keys?p=b2c_1a_idporten_ver1",
                endUserIssuer = "https://login.microsoftonline.com/d38f25aa-eab8-4c50-9f28-ebf92c1256f2/v2.0/",
                authorizedSystems = "srvpps-prosessering,srvpleiepenger-joark",
                aktoerRegisterBaseUrl = "https://app-q1.adeo.no/aktoerregister",
                tokenUrl = "https://security-token-service.nais.preprod.local/rest/v1/sts/token"
            ))

            withApplication { no.nav.helse.main(q1Args) }
        }
    }
}
