package no.nav.helse.dokument

interface DokumentStorage {
    fun hentDokument(dokumentId: DokumentId) : Dokument?
    fun slettDokument(dokumentId: DokumentId)
    fun lagreDokument(dokument: Dokument) : DokumentId
}