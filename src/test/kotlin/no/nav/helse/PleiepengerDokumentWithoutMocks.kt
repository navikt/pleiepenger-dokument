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

            System.setProperty("http.nonProxyHosts", "localhost|login.microsoftonline.com")
            System.setProperty("http.proxyHost", "127.0.0.1")
            System.setProperty("http.proxyPort", "5001")
            System.setProperty("https.proxyHost", "127.0.0.1")
            System.setProperty("https.proxyPort", "5001")

            /*
            Må settes som parametre ved oppstart
                -Dnav.storage.s3.access_key=
                -Dnav.storage.s3.secret_key=
             */

            val q1ServiceAccountArgs = TestConfiguration.asArray(TestConfiguration.asMap(
                port = 8133,
                jwksUrl = "https://security-token-service.nais.preprod.local/rest/v1/sts/jwks",
                issuer = "https://security-token-service.nais.preprod.local",
                authorizedSubjects = "srvpps-prosessering,srvpleiepenger-joark",
                s3ServiceEndpoint = "https://s3.nais.preprod.local",
                virusScanUrl = "https://clamav.nais.oera-q.local/scan",
                s3SigningRegion = "us-east-1",
                s3ExpiryInDays = null
            ))

            val q1EndUserArgs = TestConfiguration.asArray(TestConfiguration.asMap(
                port = 8133,
                jwksUrl = "https://login.microsoftonline.com/navtestb2c.onmicrosoft.com/discovery/v2.0/keys?p=b2c_1a_idporten_ver1",
                issuer = "https://login.microsoftonline.com/d38f25aa-eab8-4c50-9f28-ebf92c1256f2/v2.0/",
                s3ServiceEndpoint = "https://s3.nais.preprod.local",
                virusScanUrl = "https://clamav.nais.oera-q.local/scan",
                s3SigningRegion = "us-east-1",
                s3ExpiryInDays = "1"
            ))

            withApplication { no.nav.helse.main(q1EndUserArgs) }
        }
    }
}