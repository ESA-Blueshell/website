# Cookiebeleid van Blueshell E-Sports Association Enschede

Laatst bijgewerkt: 23 februari 2026

## Interpretatie en Definities

### Interpretatie

Woorden waarvan de beginletter met een hoofdletter is geschreven, hebben de betekenis die hieronder is vastgelegd.  
De volgende definities hebben dezelfde betekenis ongeacht of ze in enkelvoud of meervoud voorkomen.

### Definities

Voor de doeleinden van dit Cookiebeleid:

- `Vereniging` (in dit Cookiebeleid aangeduid als "de Vereniging", "Wij", "Ons" of "Onze") verwijst naar Blueshell
  E-Sports Association Enschede, Enschede.
- `Cookies` betekent kleine bestanden die door een website op je computer, mobiele apparaat of ander apparaat worden
  geplaatst.
- `Website` verwijst naar Blueshell, bereikbaar via [esa-blueshell.nl](https://esa-blueshell.nl).
- `Jij` betekent de persoon die de website bezoekt of gebruikt, of een bedrijf/rechtspersoon namens die persoon.
- `Lokale opslag` betekent browseropslag (bijvoorbeeld `localStorage`) die sleutel/waardegegevens op je apparaat
  bewaart.
- `Sessieopslag` betekent browseropslag (bijvoorbeeld `sessionStorage`) die wordt verwijderd wanneer de browsersessie
  eindigt.

## Gebruik van Cookies

### Type Cookies die Wij Gebruiken

Cookies kunnen "persistente" of "sessie" cookies zijn. Persistente cookies blijven op je apparaat staan wanneer je
offline gaat, terwijl sessiecookies worden verwijderd wanneer je je browser sluit.

Wij gebruiken momenteel sessie- en persistente cookies voor essentiele beveiliging en functionaliteit.

## Cookie- en Opslagoverzicht

### Cookies geplaatst door Blueshell-applicatiecomponenten

| Naam            | Type   | Geplaatst door | Doel                                                                                                                     | Typische bewaartermijn                                          | Essentieel                       |
|------------------|--------|-----------|--------------------------------|-------------------|------------|
| BSH_<br/>AUTH   | Cookie | API-backend    | Geauthenticeerde sessie-fallback <br/> voor JWT-tokenafhandeling.                                                        | Tot tokenverloop (standaard geconfigureerd op ongeveer 24 uur). | Ja                               |
| XSRF-<br/>TOKEN | Cookie | API-backend    | CSRF-bescherming voor <br/> statuswijzigende verzoeken.                                                                  | Sessie-achtig / opnieuw gegenereerd binnen beveiligingsflow.    | Ja                               |
| login           | Cookie | Frontend       | Slaat niet-gevoelige UI <br/> login-/sessiestatus op <br/> (userId, username, rollen, expiry-metadata; geen auth token). | 30 dagen (standaard frontendinstelling).                        | Functioneel                      |
| guestData       | Cookie | Frontend       | Slaat context voor <br/> gast-eventinschrijving op <br/> (naam, e-mail, discord, telefoon, gasttoegangstoken).           | 30 dagen (standaard frontendinstelling).                        | Functioneel voor gast-bewerkflow |

### Browseropslag gebruikt door de website

| Opslagsleutel                                  | Opslagtype    | Doel                                                              | Typische bewaartermijn                                   | Essentieel                |
|-----------------------------------|------------|----------------------|------------------|------------|
| esa-blueshell.nl:darkMode        | Lokale opslag | Slaat thema- <br/> voorkeur op.                                   | Tot verwijderd door gebruiker/browser.                   | Functioneel               |
| esa-blueshell.nl:cookiesAccepted | Lokale opslag | Slaat de wegklikstatus <br/> van de cookie-melding op.            | Tot verwijderd door gebruiker/browser.                   | Functioneel               |
| auth:ping                                 | Lokale opslag | Synchronisatiesignaal voor loginstatus <br/> tussen tabbladen.    | Bijgewerkt tijdens auth-sync; blijft bestaan tot wissen. | Functioneel               |
| recovery:password-reset:token    | Sessie opslag | Tijdelijke hersteltoken-afhandeling <br/> in wachtwoordresetflow. | Sessie duur.                                             | Essentieel voor herstel   |
| recovery:user-activation:token   | Sessie opslag | Tijdelijke tokenafhandeling <br/> in gebruikersactivatieflow.     | Sessie duur.                                             | Essentieel voor activatie |
| recovery:member-activation:token | Sessie opslag | Tijdelijke tokenafhandeling <br/> in lidactivatieflow.            | Sessie duur.                                             | Essentieel voor activatie |

## Diensten van Derden en Mogelijke Cookies van Derden

Wanneer je bepaalde functies gebruikt, kan je browser direct verbinding maken met diensten van derden (bijvoorbeeld
Discord, Google Maps, Google Calendar of socialmediabestemmingen).  
Deze derden kunnen eigen cookies plaatsen of je IP-/apparaatmetadata verwerken onder hun eigen beleid.

Voorbeelden in huidige websitefunctionaliteit:

- Google Maps-embed op de contactpagina.
- Discord-widget-API-request voor openbare serveraanwezigheid.
- Google Calendar-abonnementslinks.
- Externe socialmedia-links.

## Noodzakelijke / Essentiele Cookies

Noodzakelijke cookies worden gebruikt om kernfunctionaliteit te leveren, zoals login-/sessiebeveiliging,
CSRF-bescherming en integriteit van account-/eventworkflows.

Zonder deze cookies/opslagitems kunnen delen van de gevraagde functionaliteit niet worden geleverd (bijvoorbeeld login,
veilig formulierverkeer, herstel en bewerken van gastinschrijvingen).

## Functionele Cookies en Opslag

Functionele cookies/opslag worden gebruikt om voorkeuren te onthouden en de bruikbaarheid te verbeteren, zoals
darkmode-keuze en UI-auth-synchronisatie tussen tabbladen.

## Cookies die Wij Standaard Niet Plaatsen

De applicatie plaatst standaard geen first-party analytics- of advertentiecookies.

## Jouw Keuzes met Betrekking tot Cookies

Als je cookies wilt vermijden, kun je cookies in je browser uitschakelen en bestaande cookies voor deze website
verwijderen.  
Je kunt ook lokale-/sessieopslag wissen via browserinstellingen.

Let op: het uitschakelen van essentiele cookies/opslag kan ervoor zorgen dat authenticatie, veilige verzoekafhandeling,
accountherstel en bepaalde eventinschrijffunctionaliteit niet goed werken.

Als je cookies wilt verwijderen of je browser wilt instrueren cookies te verwijderen/weigeren, bezoek dan de
hulppagina's van je browser:

- Chrome: [Google support](https://support.google.com/accounts/answer/32050)
- Internet Explorer: [Microsoft support](http://support.microsoft.com/kb/278835)
- Firefox: [Mozilla support](https://support.mozilla.org/en-US/kb/delete-cookies-remove-info-websites-stored)
- Safari: [Apple support](https://support.apple.com/guide/safari/manage-cookies-and-website-data-sfri11471/mac)

Voor andere browsers, raadpleeg de officiele ondersteuningspagina's van je browser.

## Contact

Als je vragen hebt over dit Cookiebeleid, kun je contact met ons opnemen:

- Per e-mail: `board@blueshell.utwente.nl`
