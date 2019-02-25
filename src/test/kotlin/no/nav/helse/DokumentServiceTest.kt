package no.nav.helse

import no.nav.helse.dokument.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DokumentServiceTest {

    @Test
    fun `Rullering av passord for kyptering i fart fungerer`() {
        // Setup
        val storage = InMemoryStorage()
        val objectMapper = ObjectMapper.server()

        val fnr1 = Fodselsnummer("29099012345")
        val fnr2 = Fodselsnummer("29099067891")
        val fnr3 = Fodselsnummer("29099011121")

        val dokument1 = Dokument(
            tittel = "Tittel1",
            content = byteArrayOf(1,2,3,4),
            contentType = "application/pdf"
        )
        val dokument2 = Dokument(
            tittel = "Tittel2",
            content = byteArrayOf(5,6,7,8),
            contentType = "image/png"
        )
        val dokument3 = Dokument(
            tittel = "Tittel3",
            content = byteArrayOf(9,10,11,12),
            contentType = "image/jpeg"
        )

        val dokumentService1 = DokumentService(
            storage = storage,
            objectMapper = objectMapper,
            cryptography = Cryptography(
                encryptionPassphrase = Pair(1, "passord"),
                decryptionPassphrases = mapOf(Pair(1, "passord1"))
            )
        )

        val dokumentService2 = DokumentService(
            storage = storage,
            objectMapper = objectMapper,
            cryptography = Cryptography(
                encryptionPassphrase = Pair(2, "passord2"),
                decryptionPassphrases = mapOf(Pair(1, "passord1"), Pair(2, "passord2"))
            )
        )

        val dokumentService3 = DokumentService(
            storage = storage,
            objectMapper = objectMapper,
            cryptography = Cryptography(
                encryptionPassphrase = Pair(3, "passord3"),
                decryptionPassphrases = mapOf(Pair(1, "passord1"), Pair(2, "passord2"), Pair(3, "passord3"))
            )
        )

        // Test
        // Lagrer de tre dokumentene so bruker hver sitt passord
        val dokumentId1 = dokumentService1.lagreDokument(
            dokument = dokument1,
            fodselsnummer = fnr1
        )
        val dokumentId2 = dokumentService2.lagreDokument(
            dokument = dokument2,
            fodselsnummer = fnr2
        )
        val dokumentId3 = dokumentService3.lagreDokument(
            dokument = dokument3,
            fodselsnummer = fnr3
        )

        // dokumentId3 bør kun kunne bli dekryptert av dokumentService3
        dekrypterOgAssert(dokumentService = dokumentService1, dokumentId = dokumentId3, fnr = fnr3, expectedDokument = dokument3, expectOk = false)
        dekrypterOgAssert(dokumentService = dokumentService2, dokumentId = dokumentId3, fnr = fnr3, expectedDokument = dokument3, expectOk = false)
        dekrypterOgAssert(dokumentService = dokumentService3, dokumentId = dokumentId3, fnr = fnr3, expectedDokument = dokument3, expectOk = true)


        // dokumentId2 bør kunne dekrypteres både med dokumentService2 og dokumentService3
        dekrypterOgAssert(dokumentService = dokumentService1, dokumentId = dokumentId2, fnr = fnr2, expectedDokument = dokument2, expectOk = false)
        dekrypterOgAssert(dokumentService = dokumentService2, dokumentId = dokumentId2, fnr = fnr2, expectedDokument = dokument2, expectOk = true)
        dekrypterOgAssert(dokumentService = dokumentService3, dokumentId = dokumentId2, fnr = fnr2, expectedDokument = dokument2, expectOk = true)

        // dokumentId1 bør kunne decrypteres med alle servicene
        dekrypterOgAssert(dokumentService = dokumentService1, dokumentId = dokumentId1, fnr = fnr1, expectedDokument = dokument1, expectOk = true)
        dekrypterOgAssert(dokumentService = dokumentService2, dokumentId = dokumentId1, fnr = fnr1, expectedDokument = dokument1, expectOk = true)
        dekrypterOgAssert(dokumentService = dokumentService3, dokumentId = dokumentId1, fnr = fnr1, expectedDokument = dokument1, expectOk = true)
    }


    private fun dekrypterOgAssert(
        dokumentService: DokumentService,
        dokumentId: DokumentId,
        fnr: Fodselsnummer,
        expectedDokument: Dokument,
        expectOk: Boolean
    ) {
        try {
            val dokument = dokumentService.hentDokument(
                dokumentId = dokumentId,
                fodselsnummer = fnr
            )
            assertEquals(expectedDokument, dokument)
            assertTrue(expectOk)
        } catch (cause: Throwable) {
            assertFalse(expectOk) // Om det oppstår en exception må expectOk == false
        }
    }

    private class InMemoryStorage : Storage{
        private val storage = mutableMapOf<StorageKey, StorageValue>()

        override fun slett(storageKey: StorageKey) : Boolean {
            val value = storage.remove(storageKey)
            return value != null
        }

        override fun lagre(key: StorageKey, value: StorageValue) {
            storage[key] = value
        }

        override fun hent(key: StorageKey): StorageValue? {
            return storage[key]
        }
    }
}