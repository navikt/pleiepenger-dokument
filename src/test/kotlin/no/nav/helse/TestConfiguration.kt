package no.nav.helse

import com.github.tomakehurst.wiremock.WireMockServer

object TestConfiguration {

    fun asMap(
        wireMockServer: WireMockServer? = null,
        port : Int = 8080,
        serviceAccountJwkSetUrl : String? = wireMockServer?.getServiceAccountJwksUrl(),
        serviceAccountIssuer : String? = wireMockServer?.getServiceAccountIssuer(),
        endUserJwkSetUrl : String? = wireMockServer?.getEndUserJwksUrl(),
        endUserIssuer : String? = wireMockServer?.getEndUserIssuer(),
        authorizedSystems : String? = wireMockServer?.getSubject(),
        passphrase1 : String = "password",
        passphrase2 : String = "oldpassword",
        passphrase3: String = "reallyoldpassword"
    ) : Map<String, String> {
        return mapOf(
            Pair("ktor.deployment.port","$port"),
            Pair("nav.authorization.service_account.jwks_url","$serviceAccountJwkSetUrl"),
            Pair("nav.authorization.service_account.issuer","$serviceAccountIssuer"),
            Pair("nav.authorization.end_user.jwks_url","$endUserJwkSetUrl"),
            Pair("nav.authorization.end_user.issuer","$endUserIssuer"),
            Pair("CRYPTO_PASSPHRASE_1",passphrase1),
            Pair("CRYPTO_PASSPHRASE_2",passphrase2),
            Pair("CRYPTO_PASSPHRASE_3",passphrase3),
            Pair("nav.rest_api.authorized_systems","$authorizedSystems")
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