# pleiepenger-dokument

Mellomlagrer dokumenter før journalføring.

## API
### Tilgang
- Enten sluttbruker ID-Token i "Authorization" header (Bearer schema)
- Eller Service Account Access Token i "Authorization" header (Bearer schema)
- Eier av dokumenten settes fra subject claim i token
- Sluttbrukere kan bare operere på sine egne dokumenter
- Service Account kan operere på alles dokumenter ved å bruke query parameter "eier" ved lagring og henting/sletting så fremt det instansen er startet med restriksjoner på hvilke subjects som kan nå tjenesten (environment variable AUTHORIZED_SUBJECTS)
- Uten restriksjoner på subject kan Service Account også kun operere på singe egne dokumenter.

### Hente dokument
GET @ /v1/dokument/{dokumentId}
- 200 eller 404 response
- Om "Accept" Header er satt til "application/json" returners vedlegget på format
```json
    {
        "title" : "Tittel som ble satt ved lagring",
        "content" : "ey123...",
        "content_type" : "application/pdf"
    }
```
- Om en annen/ingen "Accept"-header er satt vil vedlegget returneres som oppgitt Content-Type ved lagring.

### Slette dokument
DELETE @ /v1/dokument/{dokumentId}
- 204 Response om dokumentet ble slettet
- 404 Resposne om det ikke fantes noe dokument å slette

### Lagre dokument
POST @ /v1/dokument
- 201 response med "Location" Header satt som peker på URL'en til dokumentet.
- Enten en Multipart Request;
- En file part med navn "content" som inneholder filen. Samme part må ha "Content-Type header" til content-type av dokumentet.
- En form part med navn "title" som inneholder tittelen på dokumentet
- Eller en JSON request på samme format som henting av dokument som JSON beskrevet ovenfor.
- Returnerer også en entity med id til dokumentet
```json
{
    "id" : "eyJraWQiOiIxIiwidHlwIjoiSldUIiwiYWxnIjoibm9uZSJ9.eyJqdGkiOiJiZTRhMjM5Yy1hZDIxLTQ5OTYtOTE3MS1kNjljY2Y1OGE4YjAifQ"
}
```

## Correlation ID vs Request ID
Correlation ID blir propagert videre, og har ikke nødvendigvis sitt opphav hos konsumenten.
Request ID blir ikke propagert videre, og skal ha sitt opphav hos konsumenten.

## For NAV-ansatte

Interne henvendelser kan sendes via Slack i kanalen #område-helse.
