package no.nav.helse.dokument

import java.util.*

class InMemoryDokumentStorage : DokumentStorage {

    private val dokumenter = mutableMapOf<DokumentId, Dokument>()

    override fun hentDokument(dokumentId: DokumentId): Dokument? {
        return dokumenter[dokumentId]
    }

    override fun slettDokument(dokumentId: DokumentId) {
        dokumenter.remove(dokumentId)
    }

    override fun lagreDokument(dokument: Dokument): DokumentId {
        val dokumentId = DokumentId(UUID.randomUUID().toString())
        dokumenter[dokumentId] = dokument
        return dokumentId
    }

}