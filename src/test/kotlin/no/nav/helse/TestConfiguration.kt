package no.nav.helse

import com.github.tomakehurst.wiremock.WireMockServer

object TestConfiguration {

    fun asMap(
        wireMockServer: WireMockServer? = null,
        s3: S3? = null,
        port : Int = 8080,
        jwksUrl : String? = wireMockServer?.getJwksUrl(),
        issuer : String? = wireMockServer?.getIssuer(),
        authorizedSubjects : String = "",
        passphrase1 : String = "password",
        passphrase2 : String = "oldpassword",
        passphrase3: String = "reallyoldpassword",
        s3ServiceEndpoint : String? = s3?.getServiceEndpoint(),
        s3SigningRegion : String? = s3?.getSigningRegion(),
        s3ExpiryInDays : String = "1"

    ) : Map<String, String> {
        val map =  mutableMapOf(
            Pair("ktor.deployment.port","$port"),
            Pair("nav.authorization.jwks_url","$jwksUrl"),
            Pair("nav.authorization.issuer","$issuer"),
            Pair("nav.authorization.authorized_subjects", authorizedSubjects),
            Pair("nav.crypto.passphrase.encryption_identifier", "1"),
            Pair("nav.crypto.passphrase.decryption_identifiers", "2,3"),
            Pair("CRYPTO_PASSPHRASE_1",passphrase1),
            Pair("CRYPTO_PASSPHRASE_2",passphrase2),
            Pair("CRYPTO_PASSPHRASE_3",passphrase3),
            Pair("nav.storage.s3.service_endpoint", "$s3ServiceEndpoint"),
            Pair("nav.storage.s3.signing_region", "$s3SigningRegion"),
            Pair("nav.storage.s3.expiration_in_days", s3ExpiryInDays)
        )

        if (!s3?.getSecretKey().isNullOrEmpty()) {
            map["nav.storage.s3.secret_key"] = s3!!.getSecretKey()
        }
        if (!s3?.getAccessKey().isNullOrEmpty()) {
            map["nav.storage.s3.access_key"] = s3!!.getAccessKey()
        }

        return map.toMap()
    }

    fun asArray(map : Map<String, String>) : Array<String>  {
        val list = mutableListOf<String>()
        map.forEach { configKey, configValue ->
            list.add("-P:$configKey=$configValue")
        }
        return list.toTypedArray()
    }
}