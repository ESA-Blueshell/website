# Privacybeleid van Blueshell E-Sports Association Enschede

Laatst bijgewerkt: 23 februari 2026  
Locatie: Enschede, Nederland

## Afkortingen

- `ALV`: Algemene Ledenvergadering
- `UT`: Universiteit Twente

Dit document bevat het privacybeleid van ESA Blueshell E-Sports Association Enschede ("Blueshell", "wij", "ons", "
onze"). Het is van toepassing op iedereen die een websiteaccount aanmaakt of gebruikt (inclusief niet-leden), en op
bredere verenigingsactiviteiten (bijvoorbeeld ledenadministratie, activiteiten, CRM en communicatietooling).  
Door akkoord te gaan met dit privacybeleid bij het aanmaken/gebruiken van een account, geef je toestemming voor
fotografie en beeldopnames tijdens verenigingsactiviteiten.  
Voor vragen en/of klachten kun je altijd contact opnemen met het bestuur van de vereniging via
`board@blueshell.utwente.nl`.

## Gegevensregister

### Websiteaccount met aanvullende lidmaatschaps-/profielinformatie

**Contracttype**: Websiteaccountovereenkomst en, waar van toepassing, lidmaatschapsovereenkomst
**Verwerking**: Accountadministratie, ledenadministratie (waar van toepassing), contributie-inning,
informatievoorziening, uitnodiging voor vergaderingen (inclusief `ALV`), communicatie, deelname aan activiteiten en
statistiek  
**Verwerkt door**: Bestuur van de vereniging, geautoriseerde commissies (beperkt tot activiteitgerelateerde gegevens) en
geautoriseerde beheerders  
**Bewaartermijn**: Zie sectie "Bewaartermijn"  
**Beveiliging**: Rolgebaseerde toegangscontrole, beveiligde verenigingsopslag, soft-delete model, beveiligd transport (
HTTPS), gehashte credentials, CSRF-bescherming en geauditeerde wijzigingen

Persoonsgegevens die momenteel worden verwerkt voor accountbeheer en, waar van toepassing, ledenbeheer omvatten:

1. Lidmaatschapsnummer / interne gebruikers-ID  
   Vereist voor een complete administratie en voor statistiek (bijvoorbeeld analyse van ledenstromen per academische
   periode).
2. Naamvelden (`initialen`, `voornaam`, optioneel tussenvoegsel, `achternaam`, `volledige naam`)  
   Gebruikt om accounthouders/leden te identificeren in administratie en communicatie, en voor betalingsgerelateerde
   administratie.
3. Gebruikersnaam  
   Gebruikt voor accountlogin en identificatie in interne systemen.
4. E-mailadres  
   Gebruikt als primair communicatiekanaal voor accountcommunicatie, activatie, wachtwoordherstel, nieuwsbrieven,
   officiele aankondigingen en het bijeenroepen/informeren van leden voor de Algemene Ledenvergadering (`ALV`).
5. Wachtwoord (gehasht)  
   Gebruikt voor accountauthenticatie. Wachtwoorden worden niet in platte tekst opgeslagen.
6. Telefoonnummer  
   Gebruikt als reserve-contactmethode voor urgente zaken wanneer andere communicatiekanalen onvoldoende zijn.
7. Discord-handle  
   Gebruikt om leden/gasten te identificeren in communitykanalen en voor coordinatie van activiteiten/community.
8. Nieuwsbriefvoorkeur  
   Gebruikt om te bepalen welke mail-/contactcommunicatie naar een accounthouder wordt verzonden.
9. Adresgegevens (`land`, `stad`, `straat`, `huisnummer`, `postcode`)  
   Gebruikt voor account-/ledenadministratie en papieren/postale communicatie waar nodig.
10. Lidprofielgegevens (`geboortedatum`, `studentnummer`, `geslacht`, `nationaliteit`, `fototoestemming`, `BHV`,
    `EHBO`)  
    Gebruikt voor ledenadministratie, statistiek/volledigheid, veiligheidsplanning en verenigingsverplichtingen:
    - `studentnummer`: UT-gerelateerde zaken (bijvoorbeeld subsidieadministratie of institutionele koppeling waar
      nodig).
    - `geboortedatum`: administratieve volledigheid en statistiek.
    - `EHBO`/`BHV`: planning van veilige bezetting voor activiteiten (binnen en buiten UT-contexten).
    - `fototoestemming`: acceptatie van dit privacybeleid geldt als toestemming voor fotografie/beeldopnames tijdens
      activiteiten; dit wordt niet als aparte opt-in behandeld.
11. Lidmaatschapsgegevens (`lidtype`, `startdatum`, `einddatum`, `incasso`)  
    Gebruikt voor lidmaatschapsstatus en contributie-/betalingsadministratie:
    - `lidtype`: vereist omdat statutaire/administratieve verschillen kunnen gelden tussen lidcategorieen.
    - `startdatum`/`einddatum`: vereist voor administratie van de lidmaatschapscyclus en statistiek.
    - `incasso`: registreert de betaalmethode.
12. Rol- en permissiegegevens  
    Gebruikt voor autorisatie in de applicatie (bijvoorbeeld bestuur-, commissie- en ledenrollen) en om te bepalen welke
    interne werkgebieden/gegevens toegankelijk zijn.
13. Groepslidmaatschappen (`Bestuur`, `Commissies`, `Teams`)  
    Gebruikt om toegang tot interne werkomgevingen te beheren en voor verenigingsstatistiek.
14. Bankgegevens (`IBAN`/`BIC`)  
    Gebruikt om betalingen te identificeren en incasso-/betalingsadministratie uit te voeren.
15. Incassotoestemming  
    Gebruikt om vast te leggen of automatische incasso is toegestaan.
16. Contributie- en herinneringsgegevens  
    Gebruikt om betalingsnaleving, openstaande bedragen en herinneringsworkflows bij te houden.
17. Registratiestatus voor mailinglijsten  
    Gebruikt om bij te houden welke communicatiekanalen (bijvoorbeeld nieuwsbrief/actieve-ledenlijst) van toepassing
    zijn op een accounthouder/lid.
18. Contactsynchronisatiereferentie (`contact_id`)  
    Gebruikt om contacten te synchroniseren met externe contactmanagementverwerkers.
19. Auditmetadata (`created/updated` tijdstempels, actor-tracking in beheerflows)  
    Gebruikt voor operationele integriteit, beveiliging en verantwoording.

### Activiteitenformulier (eventinschrijving)

**Contracttype**: Inschrijfformulier voor een activiteit (lid of gast)  
**Verwerking**: Beheer van activiteitdeelname, aanwezigheidsadministratie, communicatie rond activiteiten en optionele
vragenlijstverwerking  
**Verwerkt door**: Bestuur van de vereniging, activiteitorganiserende commissies  
**Bewaartermijn**: Zie sectie "Bewaartermijn"  
**Beveiliging**: Geauthenticeerde toegangscontroles, gasttoegang-tokenflow met gehashte tokenopslag en rolgebaseerde
toegang

Persoonsgegevens die momenteel worden verwerkt voor eventinschrijvingen kunnen omvatten:

1. Naam  
   Vereist om de deelnemer te identificeren.
2. E-mail  
   Vereist als primair communicatiekanaal tussen organisatoren en deelnemers.
3. Discord-gebruikersnaam  
   Gebruikt waar nodig voor game-/eventcoordinatie (bijvoorbeeld uitnodigingen voor toernooien/custom games).
4. Telefoonnummer  
   Gebruikt als reserve-contactmethode voor urgente operationele communicatie.
5. Activiteitspecifieke vragenlijstantwoorden (inclusief vrije-tekstantwoorden)  
   Gebruikt om activiteiten te organiseren (bijvoorbeeld deelnamevoorkeuren, planningskeuzes en logistieke input).
6. Voor gastinschrijvingen: een gasttoegangstoken (als hash opgeslagen in de database; ruwe token wordt gebruikt voor
   gast-bewerk/verwijderflows)  
   Vereist om gasten hun eigen inschrijving veilig te laten bewerken of verwijderen zonder een volledig account aan te
   maken.
7. In-game naam (optioneel)  
   Gebruikt waar relevant voor gamespecifieke organisatie (bijvoorbeeld toernooi/custom-game invites).
8. Activiteitsvoorkeuren (optioneel)  
   Gebruikt wanneer organisatoren voorkeursvragen opnemen in een inschrijfflow.
9. Beschikbare middelen (optioneel)  
   Gebruikt wanneer organisatoren deelnemers vragen welke middelen zij kunnen meebrengen (bijvoorbeeld
   stekkerdozen/apparatuur).

### CRM / Contactsynchronisatie

**Contracttype**: Contactsynchronisatie voor communicatie en beheer van contributieperiode-lijsten  
**Verwerking**: Synchroniseren van contactgegevens met externe contactverwerker en bewerkingen op
contributieperiode-lijsten  
**Verwerkt door**: Bestuur van de vereniging en geautoriseerde technische beheerders  
**Bewaartermijn**: Zie sectie "Bewaartermijn"  
**Beveiliging**: Toegangsbeperkte jobverwerking en geauthenticeerde verwerker-API's

Gegevens die worden gebruikt voor contactsynchronisatie omvatten:

1. E-mail  
   Gebruikt voor communicatie en synchronisatie van mailinglijsten.
2. Voor- en achternaam  
   Gebruikt om het contact te identificeren en correspondentieregistratie duidelijk te houden.
3. Telefoonnummer (indien beschikbaar)  
   Gebruikt als aanvullend communicatiekanaal waar nodig.
4. Organisatie/bedrijf  
   Gebruikt om de contactcontext te identificeren en relevante communicatiegeschiedenis te onderhouden.
5. Notities  
   Gebruikt om relevante contextuele informatie over het contact/de contactpersoon op te slaan.
6. Nieuwsbriefvoorkeur  
   Gebruikt om contacten op basis van voorkeur in/uit communicatiecampagnes op te nemen.
7. Ledenstatus-indicatoren  
   Gebruikt om contributieperiode- en lidmaatschapsgerelateerde contactlijsten correct te synchroniseren.

### Discord en communitykanalen

**Contracttype**: Communitydeelname en optionele externe community-integraties  
**Verwerking**: Communitycoordinatie en zichtbaarheid van openbare communityinformatie  
**Verwerkt door**: Leden/gasten zelf, Discord-platform en bestuur-/commissiekanalen waar relevant  
**Bewaartermijn**: Bepaald door platform-/accountlevenscyclus en interne registraties waar van toepassing  
**Beveiliging**: Platformniveau-controles en verenigings-toegangscontroles

Notities:

- Blueshell slaat de door een gebruiker/gast opgegeven Discord-handle op in account- of eventinschrijfflows.
- De website kan Discord-widgetdata opvragen vanaf het openbare Discord-eindpunt om online aanwezigheid/kanaalinformatie
  te tonen.
- Optionele Discord-spelvoorkeuren kunnen worden gebruikt voor statistiek en vindbaarheid tussen leden.
- Discord-groep-/rollidmaatschap (bijvoorbeeld lid-/commissie-/teamrollen) kan binnen Discord zichtbaar zijn en worden
  gebruikt voor identificatie en interne coordinatie.

### Verwerking voor beveiliging, authenticatie en herstel

**Contracttype**: Accountbeveiliging en preventie van fraude/misbruik  
**Verwerking**: Authenticatie, autorisatie, sessiebeheer, CSRF-bescherming, accountherstel en misbruik-rate-limiting  
**Verwerkt door**: Blueshell-platform en geautoriseerde beheerders  
**Bewaartermijn**: Zie sectie "Bewaartermijn"  
**Beveiliging**: Veilige cookies, gehashte credentials/tokens, rolgebaseerde permissies en afgeschermde endpoints

Verwerkte gegevens omvatten:

1. Authenticatietokendata (JWT/sessiecookie-afhandeling)
2. CSRF-tokendata
3. Selector/verifier-hash van hersteltokens, verloopdatum en consumed-status
4. Beveiligingseventmetadata (bijvoorbeeld actor-ID, rol/type in operationele jobtracking)
5. Beperkte IP-gebaseerde metadata voor rate-limiting op openbare authenticatie-endpoints

## Bewaartermijn

Bewaartermijnen verschillen per verwerkingsdoel en technisch subsysteem.

### Lidmaatschaps- en accountgegevens

- Account-, profiel-, adres-, lidmaatschaps- en gerelateerde administratieve gegevens worden bewaard totdat via
  officiele kanalen een verwijderverzoek bij het bestuur is ingediend.
- Wanneer je verwijdering van je account aanvraagt, gaat je account in een herstelperiode van 90 dagen. Tijdens deze
  periode kan je account op verzoek worden hersteld.
- Na 90 dagen worden je accountgegevens onherroepelijk verwijderd of geanonimiseerd en is herstel niet meer mogelijk.
- Waar wettelijke of financiele verplichtingen gelden, kunnen specifieke registraties in geminimaliseerde of wettelijk
  vereiste vorm worden bewaard.

### Activiteitenformulier / eventinschrijvingen

- Eventinschrijvingen, gastregistraties en gekoppelde vragenlijstantwoorden worden bewaard.
- Nadat een verwijderverzoek is verwerkt, worden gerelateerde activiteitenformulier- en eventinschrijvingsgegevens
  gede-identificeerd zodat ze niet langer direct tot personen herleidbaar zijn.

### CRM / contactsynchronisatie

- Wanneer een verwijderverzoek is verwerkt, worden corresponderende contactregistraties verwijderd uit
  CRM-/contactsynchronisatieverwerkers.

### Beveiligings- en herstelgegevens

- Hersteltokens hebben ingebouwde vervalvensters.
- Verlopen of gebruikte tokenrijen kunnen blijven bestaan tot opschoning.
- Rate-limit buckets zijn proceslokaal en kortlevend.

### Operationele jobregistraties

- Jobuitvoeringsregistraties (status, payloadmetadata, foutdetails en actormetadata) worden bewaard voor operationele
  monitoring en troubleshooting.
- Operationele jobregistraties worden automatisch verwijderd na 1 jaar.

## Verstrekking van Gegevens / Verwerkersovereenkomst

### Intern

Binnen de vereniging worden gegevens op need-to-know-basis gedeeld met:

- Bestuursleden (brede administratieve toegang waar vereist door rol).
- Commissies (beperkte toegang, voornamelijk eventgerelateerde deelnemersgegevens voor door hen georganiseerde events).
- Geautoriseerde beheerders/technische operators.

Gegevensdeling kan verschillen per orgaan, moment en activiteit. Gegevens worden verstrekt voor doelen zoals
deelnemerscommunicatie, organisatie van activiteiten en aanwezigheidsadministratie. Volledige ledenadministratie blijft
onder bestuurscontrole.

### Extern

Blueshell gebruikt momenteel externe verwerkers/diensten voor specifieke doeleinden:

1. ING  
   Verzorgt incasso-/betalingsverwerking voor de vereniging waar van toepassing.
2. NBSE (Nederlandse Bond voor Studenten E-sportsverenigingen)  
   Ontvangt overeengekomen lidmaatschapsgerelateerde gegevens (bijvoorbeeld voornaam, achternaam, e-mail, stad, adres)
   voor verenigingsbrede lidmaatschapsadministratie en berekening van stemrechten.
3. SMTP-e-mailprovider (hornet.snt.utwente.nl)
   Gebruikt voor het verzenden van transactionele e-mails (activatie, herstel, herinneringen, eventcommunicatie).
4. Brevo contactverwerker
   Gebruikt voor contactsynchronisatie en bewerkingen op contributieperiode-mailinglijsten.
5. Google Calendar  
   Gebruikt om goedgekeurde eventgegevens te synchroniseren (titel, beschrijving, locatie, start-/eindtijd).
6. Diensten van derden die gebruikers via frontendfunctionaliteit benaderen  
   Bijvoorbeeld Google Maps-embeds, Discord-widget-endpoints, Google Calendar-links en socialmedia-links.

### Buiten de EU/EER

Afhankelijk van verwerkerinfrastructuur en gebruikersinteractie met diensten van derden kunnen persoonsgegevens buiten
de EU/EER worden verwerkt.  
Waar vereist streeft Blueshell naar gebruik van rechtmatige doorgiftemechanismen onder de AVG.

Verenigingsbrede tooling en communicatieverwerkers waarbij mogelijk buiten de EU/EER wordt verwerkt, omvatten:

1. Google Inc. / Google-diensten  
   Verenigingsadministratie en samenwerkingstooling kunnen Google-diensten omvatten (bijvoorbeeld gedeelde drives en
   kalendertooling), wat leidt tot gegevensuitwisseling met verwerkers.
2. Mailchimp  
   Verzending van nieuwsbrief/info-mail kan het delen van e-mailadressen en, waar relevant, naamgegevens voor
   mailingactiviteiten omvatten.
3. Brevo  
   Contactsynchronisatie en mailingactiviteiten kunnen verwerking via Brevo-infrastructuur omvatten, wat afhankelijk van
   serviceconfiguratie en routing verwerking buiten de EU/EER kan inhouden.

Waar van toepassing onderhoudt Blueshell verwerkers-/gegevensverwerkingsovereenkomsten met verwerkers om privacyrechten
en verplichtingen vast te leggen.

## Rechten van Betrokkenen

Onder de AVG heb je de volgende rechten (onder voorbehoud van wettelijke voorwaarden en uitzonderingen):

### Recht op inzage

Je hebt recht op inzage in je gegevens en verwerkingsdoeleinden (artikel 15 AVG).  
Verzoeken kunnen via officiele kanalen bij het bestuur worden ingediend.  
Blueshell streeft ernaar om inzageverzoeken snel af te handelen en, waar haalbaar, binnen een werkdag voor eenvoudige
verzoeken.

### Recht op rectificatie

Je hebt het recht om onjuiste persoonsgegevens te laten corrigeren (artikel 16 AVG).

### Recht op gegevenswissing

Je hebt het recht om verwijdering van je gegevens te verzoeken (artikel 17 AVG).  
Als financiele/wettelijke verplichtingen nog specifieke gegevens vereisen, kunnen die registraties in
geminimaliseerde/geanonimiseerde vorm worden bewaard zoals wettelijk vereist.

### Recht op beperking van verwerking

Je kunt verzoeken om beperking van verwerking waar dit wettelijk van toepassing is.

### Meldplicht

Als rectificatie, verwijdering of beperking is toegepast, informeert Blueshell waar vereist relevante
ontvangers/verwerkers.

### Recht op gegevensoverdraagbaarheid

Je kunt een export aanvragen van je bekende persoonsgegevens in een gangbaar formaat (bijvoorbeeld een Excel-compatibele
export waar van toepassing).

### Recht van bezwaar

Je kunt bezwaar maken tegen specifieke verwerking. Als het bezwaar verplichte verwerking betreft die nodig is voor
lidmaatschap of deelname aan activiteiten, kan dit gevolgen hebben voor het behoud van dat lidmaatschap/deelname.

### Recht om toestemming in te trekken

Waar verwerking is gebaseerd op toestemming, kun je die toestemming op ieder moment intrekken.

Om rechten uit te oefenen kun je contact opnemen met het bestuur via `board@blueshell.utwente.nl`.

## Verstrekking van Persoonsgegevens aan Derden

Zonder jouw toestemming verstrekt Blueshell persoonsgegevens alleen aan derden wanneer dit noodzakelijk is voor
uitvoering van de overeenkomst, legitieme verenigingsactiviteiten, beveiliging of wettelijke verplichtingen.

## Inzage, Correctie en Verwijdering van Persoonsgegevens

Je kunt inzage, correctie of verwijdering van je persoonsgegevens verzoeken.  
Verwijderverzoeken moeten via officiele kanalen bij het bestuur worden ingediend (bijvoorbeeld via
`board@blueshell.utwente.nl`).  
Voor verzoeken kan Blueshell je vragen jezelf te identificeren om je gegevens te beschermen.
Waar van toepassing zal Blueshell voltooide verwijdering communiceren aan verwerkers/organisaties die de betreffende
gegevens hebben ontvangen.
Voor verwijderverzoeken kunnen gegevens die nodig zijn voor wettelijke/financiele verplichtingen in geminimaliseerde
vorm worden bewaard, zoals wettelijk vereist.

## Beveiliging van Persoonsgegevens

Blueshell neemt passende technische en organisatorische maatregelen, waaronder:

- Rolgebaseerde autorisatie
- Beveiligd transport (HTTPS)
- Veilige verwerking van authenticatiecookies
- CSRF-verdediging
- Wachtwoordhashing
- Tokenhashing waar van toepassing
- Geauditeerde gegevenswijzigingen en operationele logging
- Toegangscontrole op basis van noodzaak ("need to know")
- Periodieke beoordeling van beveiligingsmaatregelen

## Links naar Andere Websites

De website kan links of embeds bevatten van websites/diensten van derden.  
Dit privacybeleid is alleen van toepassing op door Blueshell gecontroleerde verwerking, niet op onafhankelijke
beleidsregels van derden.

## Wijziging van het Privacybeleid

Blueshell kan dit privacybeleid aanpassen om het accuraat en actueel te houden.  
De meest recente versie wordt gepubliceerd via officiele Blueshell-kanalen.  
Bij significante wijzigingen zal Blueshell redelijke inspanningen leveren om leden te informeren via officiele
communicatiekanalen.

## Datalekken

In geval van een datalek met persoonsgegevens handelt Blueshell conform AVG-verplichtingen, waaronder:

- Beoordeling en inperking
- Melding aan de toezichthoudende autoriteit waar vereist (zonder onredelijke vertraging en waar haalbaar binnen 72 uur)
- Melding aan getroffen betrokkenen waar wettelijk vereist
- Onderzoek naar de waarschijnlijke oorzaak en preventieve vervolgmaatregelen

## Contactinformatie

Verantwoordelijke: Bestuur van Blueshell E-Sports Association Enschede  
E-mail: `board@blueshell.utwente.nl`

## Klacht over de Verwerking van je Persoonsgegevens

Als je een klacht hebt, neem dan eerst contact met ons op zodat we deze proberen op te lossen.  
Je hebt ook het recht om een klacht in te dienen bij de Autoriteit Persoonsgegevens.
