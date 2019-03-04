package no.nav.helse

import com.github.tomakehurst.wiremock.WireMockServer

object TestConfiguration {

    fun asMap(
        wireMockServer: WireMockServer? = null,
        port : Int = 8080,
        jwksUrl : String? = wireMockServer?.getJwksUrl(),
        issuer : String? = wireMockServer?.getIssuer(),
        authorizedSubjects : String = "",
        passphrase1 : String = "password",
        passphrase2 : String = "oldpassword",
        passphrase3: String = "reallyoldpassword"
    ) : Map<String, String> {
        return mapOf(
            Pair("ktor.deployment.port","$port"),
            Pair("nav.authorization.jwks_url","$jwksUrl"),
            Pair("nav.authorization.issuer","$issuer"),
            Pair("nav.authorization.authorized_subjects", authorizedSubjects),
            Pair("nav.crypto.passphrase.decryption_identifiers", "2,3"),
            Pair("CRYPTO_PASSPHRASE_1",passphrase1),
            Pair("CRYPTO_PASSPHRASE_2",passphrase2),
            Pair("CRYPTO_PASSPHRASE_3",passphrase3)
        )
    }

    fun asArray(map : Map<String, String>) : Array<String>  {
        val list = mutableListOf<String>()
        map.forEach { configKey, configValue ->
            list.add("-P:$configKey=$configValue")
        }
        return list.toTypedArray()
    }
}