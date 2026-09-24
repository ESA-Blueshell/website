# Changelog

## [1.10.0](https://github.com/ESA-Blueshell/website/compare/v1.9.1...v1.10.0) (2026-09-24)


### Features

* **association:** the home page closes on six membership perks, the partner logos and the call band, replacing the old directory ([#1491](https://github.com/ESA-Blueshell/website/issues/1491)) ([818991b](https://github.com/ESA-Blueshell/website/commit/818991b9bead5186de00823b3f536e0ccabb5eaa))
* **association:** the home page opens on an island hero with the wordmark, Become a member, Join our Discord and filled social glyphs ([#1488](https://github.com/ESA-Blueshell/website/issues/1488)) ([973da67](https://github.com/ESA-Blueshell/website/commit/973da6742f566d4f0be79a82374eaa5c0ab9084e))
* **association:** the home page runs the casual games and this season's esports as banner-height slice bands, replacing the Vuetify games grid ([#1490](https://github.com/ESA-Blueshell/website/issues/1490)) ([d376093](https://github.com/ESA-Blueshell/website/commit/d376093c36df7c780d92b5db10eb476c3f05e474))
* **association:** the home page runs upcoming events as a poster strip with each event's sign-up state, on a shared band head ([#1489](https://github.com/ESA-Blueshell/website/issues/1489)) ([4dd4f81](https://github.com/ESA-Blueshell/website/commit/4dd4f81350806aa84adf023529cfc608e7500c6c))
* **components:** the footer is island, on every page ([#1486](https://github.com/ESA-Blueshell/website/issues/1486)) ([fed0eb1](https://github.com/ESA-Blueshell/website/commit/fed0eb1c5c2e2e4fda89961f33f30ca18a4609f7))
* **design:** a dev-only /design/parts gallery draws every band part in both themes, and the count badge follows a wrapping heading's last word ([#1487](https://github.com/ESA-Blueshell/website/issues/1487)) ([3903e8f](https://github.com/ESA-Blueshell/website/commit/3903e8f35933f9fa0a2573b4e45445c395f73127))
* **discord:** bot-made invites, empty rooms leave the band ([#1531](https://github.com/ESA-Blueshell/website/issues/1531)) ([e92e57c](https://github.com/ESA-Blueshell/website/commit/e92e57cfcf923c70555f9aaa8284711d41ddc387))
* **discord:** linked accounts follow their member's name in the server ([#1533](https://github.com/ESA-Blueshell/website/issues/1533)) ([#1535](https://github.com/ESA-Blueshell/website/issues/1535)) ([a46c862](https://github.com/ESA-Blueshell/website/commit/a46c8627fdcd97236c0179d404654efd6e50f05f))
* **discord:** live band via the bot, pushed over a socket ([#1528](https://github.com/ESA-Blueshell/website/issues/1528)) ([2184e11](https://github.com/ESA-Blueshell/website/commit/2184e115af6726181823bcb72d5e481fd2aaa485))
* **discord:** pick your Discord account from the server ([#1534](https://github.com/ESA-Blueshell/website/issues/1534)) ([51509d9](https://github.com/ESA-Blueshell/website/commit/51509d989a7d7ccd85088bd675e174671b8a6039))
* **discord:** room-makers back in the band, locks as the viewer's roles allow ([#1536](https://github.com/ESA-Blueshell/website/issues/1536)) ([#1537](https://github.com/ESA-Blueshell/website/issues/1537)) ([66e84fd](https://github.com/ESA-Blueshell/website/commit/66e84fd2b159eca167319f40a7bda7baf5318987))
* **discord:** the bot posts events and lists them in the server ([#1547](https://github.com/ESA-Blueshell/website/issues/1547)) ([99a8ac1](https://github.com/ESA-Blueshell/website/commit/99a8ac144c624fe091f978bb126f1fc8a8cd67e0))
* **discord:** the home page's Discord band lists the voice rooms in Discord's own chrome, read from the public widget, and falls back to the invite ([#1493](https://github.com/ESA-Blueshell/website/issues/1493)) ([ad63ab2](https://github.com/ESA-Blueshell/website/commit/ad63ab2c1db9e06a31b33b2a9267c0d97ef80b9a))
* **events:** adding and editing an event happens on an island page beside a poster preview, with a raised save bar ([#1497](https://github.com/ESA-Blueshell/website/issues/1497)) ([06a14b8](https://github.com/ESA-Blueshell/website/commit/06a14b8807547f17807234b9e85bc40a0178d6bd))
* **events:** agenda rows cut on both ends, wider and taller ([#1548](https://github.com/ESA-Blueshell/website/issues/1548)) ([#1549](https://github.com/ESA-Blueshell/website/issues/1549)) ([1f9b7c7](https://github.com/ESA-Blueshell/website/commit/1f9b7c7e9a1a52b9ea151c067b4cb33dc969c43f))
* **events:** event links show the event in chat apps ([#1546](https://github.com/ESA-Blueshell/website/issues/1546)) ([60a4a7a](https://github.com/ESA-Blueshell/website/commit/60a4a7aa2b0f15d443d90dcf6fe62a8e6c5d061c))
* **events:** every event gets its own page with the full description, a sign-up panel, the organiser strip and what else is coming ([#1496](https://github.com/ESA-Blueshell/website/issues/1496)) ([6e482e1](https://github.com/ESA-Blueshell/website/commit/6e482e13b2d4d304d004082f5d09d3d37a0c0f92))
* **events:** past events get an archive at /events/past, posters by month with year filters, a title search and paging ([#1499](https://github.com/ESA-Blueshell/website/issues/1499)) ([5b74c13](https://github.com/ESA-Blueshell/website/commit/5b74c13919ece970d3620eb3f79f9b1ecb455634))
* **events:** the events page opens on the next event and a dated agenda, replacing the month calendar, with the calendar subscription kept ([#1494](https://github.com/ESA-Blueshell/website/issues/1494)) ([4e5e37c](https://github.com/ESA-Blueshell/website/commit/4e5e37c8980b80b41b1090acab7072eb33f1c004))
* **events:** the sign-up form builder is on the island, one flat band per question with a Required tick, icon buttons and a row of buttons to add each type ([#1498](https://github.com/ESA-Blueshell/website/issues/1498)) ([eca723e](https://github.com/ESA-Blueshell/website/commit/eca723ea3a7629116ebc818b772da48dd021b1e9))
* **events:** the sign-ups page moves onto the island with counts, a searchable attendee roster, tallied responses and island dialogs ([#1495](https://github.com/ESA-Blueshell/website/issues/1495)) ([0b4e59f](https://github.com/ESA-Blueshell/website/commit/0b4e59f2aab245080bd34ddd05f72158605cef3c))
* **frontend:** forms use island fields, with searchable pickers and a markdown editor ([#1400](https://github.com/ESA-Blueshell/website/issues/1400)) ([11ae671](https://github.com/ESA-Blueshell/website/commit/11ae671c3a3bc14f1dec82b8afaa42ce3ef5ff45))
* **island:** DateTimeInput, CountInput, NoticeBox, IconButton and a danger cut for the event form, with flush panels, Today and a stepping clock ([#1492](https://github.com/ESA-Blueshell/website/issues/1492)) ([c703b8b](https://github.com/ESA-Blueshell/website/commit/c703b8bd07456dd55bcff9c1f22d6f1172577887))


### Bug Fixes

* **build:** the build prints no warnings ([#1501](https://github.com/ESA-Blueshell/website/issues/1501)) ([#1503](https://github.com/ESA-Blueshell/website/issues/1503)) ([bbc7f9f](https://github.com/ESA-Blueshell/website/commit/bbc7f9f71d8ae76bc94ee0f65a2bb3ed521620e7))
* **ci:** the coverage gate passes a change no suite measures when only one suite ran ([#1527](https://github.com/ESA-Blueshell/website/issues/1527)) ([bac6339](https://github.com/ESA-Blueshell/website/commit/bac633949927cf8c6c1e0e0ba46c5a177ba600c9)), closes [#1526](https://github.com/ESA-Blueshell/website/issues/1526)
* **dev:** the API's mail lands in a local Stalwart inbox, with every [@blueshell](https://github.com/blueshell).test address caught and readable over IMAP ([#1505](https://github.com/ESA-Blueshell/website/issues/1505)) ([8fad50e](https://github.com/ESA-Blueshell/website/commit/8fad50eb790507e5dffa1f06765a76249e7c1ae9))
* **discord:** Discord's voice glyphs, green when in use ([#1516](https://github.com/ESA-Blueshell/website/issues/1516)) ([831c744](https://github.com/ESA-Blueshell/website/commit/831c744fc942c184e375c6cecfbf7bbe275742df))
* **discord:** online of total, occupied rooms first, avatars, room join ([#1512](https://github.com/ESA-Blueshell/website/issues/1512)) ([9f34f17](https://github.com/ESA-Blueshell/website/commit/9f34f17fb69e23d49965d759053985acf51e3241))
* **events:** date plates, posters link to events, cut agenda rows ([#1513](https://github.com/ESA-Blueshell/website/issues/1513)) ([d252ba8](https://github.com/ESA-Blueshell/website/commit/d252ba822b812582a6d9dc1212d730067864a8f7))
* **home:** partners centred, green perks wash, full social row ([#1509](https://github.com/ESA-Blueshell/website/issues/1509)) ([6b4b75b](https://github.com/ESA-Blueshell/website/commit/6b4b75b89343d80df65757c4aba9966d53882bf0))
* **island:** a narrow slice opens without stretching the band, and a shut one too narrow for its name shows its icon ([#1511](https://github.com/ESA-Blueshell/website/issues/1511)) ([99e0d5d](https://github.com/ESA-Blueshell/website/commit/99e0d5dc010f3ea572d64b475917eacfa810b448)), closes [#1510](https://github.com/ESA-Blueshell/website/issues/1510)
* **island:** a slice band's toggle is its heading, so the links a slice reveals no longer sit inside a button ([#1500](https://github.com/ESA-Blueshell/website/issues/1500)) ([acb64b5](https://github.com/ESA-Blueshell/website/commit/acb64b55af7f5b297036093d96c2bc6cb88c11b1))
* **island:** rows fit by width, portraits keep their shape ([#1518](https://github.com/ESA-Blueshell/website/issues/1518)) ([560e50c](https://github.com/ESA-Blueshell/website/commit/560e50c9d4461cce75ec4343eabd283c82a45b40))


### Documentation

* **auth:** two-factor glossary and ADRs, decided before built ([#1566](https://github.com/ESA-Blueshell/website/issues/1566)) ([f97a7b6](https://github.com/ESA-Blueshell/website/commit/f97a7b61f0c4120645089b5672ba479801d7f490))
* **design:** the design language covers the events pages and the forms that run them ([#1484](https://github.com/ESA-Blueshell/website/issues/1484)) ([607f0bd](https://github.com/ESA-Blueshell/website/commit/607f0bd9f3d54623f743ae2b18cc045aaa20d556))
* **platform:** Discord bot runbook and check script ([#1525](https://github.com/ESA-Blueshell/website/issues/1525)) ([60d305b](https://github.com/ESA-Blueshell/website/commit/60d305bd052b42408037b6285717e435f531dc25))

## [1.9.1](https://github.com/ESA-Blueshell/website/compare/v1.9.0...v1.9.1) (2026-09-23)


### Bug Fixes

* **ci:** the coverage gate finds the report when one unit suite ran ([#1469](https://github.com/ESA-Blueshell/website/issues/1469)) ([91863da](https://github.com/ESA-Blueshell/website/commit/91863da851ba4b0512ccba5f1012bd56fd8e3084)), closes [#1468](https://github.com/ESA-Blueshell/website/issues/1468)
* **components:** the phone menu opens folded, and one icon opens the account panel ([#1471](https://github.com/ESA-Blueshell/website/issues/1471)) ([7d28e2d](https://github.com/ESA-Blueshell/website/commit/7d28e2de8d74ef424a3c34e25719208e6cdf63ec))
* **events:** the respondents table sorts like the management tables ([#1473](https://github.com/ESA-Blueshell/website/issues/1473)) ([880e319](https://github.com/ESA-Blueshell/website/commit/880e31952f643718cc1afe69a0b8d5541c1ff16f))
* **frontend:** the poster strip fits the screen it is on ([#1465](https://github.com/ESA-Blueshell/website/issues/1465)) ([1f5666b](https://github.com/ESA-Blueshell/website/commit/1f5666bc13f756dfa0e32288cb9d39c418de7616))
* **platform:** Flux tells Discord only when something fails ([#1462](https://github.com/ESA-Blueshell/website/issues/1462)) ([4902eb4](https://github.com/ESA-Blueshell/website/commit/4902eb49d1c5aba969ea69891b4950610b3b026b)), closes [#1461](https://github.com/ESA-Blueshell/website/issues/1461)
* **seed:** the seed files add rows and never edit one ([#1467](https://github.com/ESA-Blueshell/website/issues/1467)) ([857bdac](https://github.com/ESA-Blueshell/website/commit/857bdac4b18b79e58c2d9c5eb3e10379cf5f5e94)), closes [#1466](https://github.com/ESA-Blueshell/website/issues/1466)

## [1.9.0](https://github.com/ESA-Blueshell/website/compare/v1.8.0...v1.9.0) (2026-09-22)


### Features

* **association:** past events read as a poster strip on three pages ([#1431](https://github.com/ESA-Blueshell/website/issues/1431)) ([3714034](https://github.com/ESA-Blueshell/website/commit/37140349d419497f3d84ff7544bd569cad832a12))
* **board:** a shut phone slice shows a peek of the portrait ([#1413](https://github.com/ESA-Blueshell/website/issues/1413)) ([b4c465e](https://github.com/ESA-Blueshell/website/commit/b4c465eb787d29df0bf5fe015a4fced699fbb21f)), closes [#1026](https://github.com/ESA-Blueshell/website/issues/1026)
* **canary:** a release says so in Discord ([#1401](https://github.com/ESA-Blueshell/website/issues/1401)) ([b92c707](https://github.com/ESA-Blueshell/website/commit/b92c707b41170049bb3c0cb640a3a455239b9dda))
* **ci:** a pull request adding a changeset gets a comment with the SQL it will run ([#1407](https://github.com/ESA-Blueshell/website/issues/1407)) ([7dcb6c0](https://github.com/ESA-Blueshell/website/commit/7dcb6c01f0130f366287227e02536144fd73aa11))
* **ci:** a pull request with an uncovered changed line cannot merge ([#1410](https://github.com/ESA-Blueshell/website/issues/1410)) ([6a7b00e](https://github.com/ESA-Blueshell/website/commit/6a7b00e86849329417d7514a7beb1b9bb5829605)), closes [#1408](https://github.com/ESA-Blueshell/website/issues/1408)
* **events:** a development start seeds the association's own events ([#1441](https://github.com/ESA-Blueshell/website/issues/1441)) ([bfc76a1](https://github.com/ESA-Blueshell/website/commit/bfc76a1b91a68a168960fb0ebac9754f4a3a1a25))
* **events:** the signup page shows membership status, board members can edit signups ([#1399](https://github.com/ESA-Blueshell/website/issues/1399)) ([1fe0b15](https://github.com/ESA-Blueshell/website/commit/1fe0b151edc6a91b6939ed76d614fa596f8d904f))
* **files:** animated GIF banners convert to animated WebP at every width ([#1391](https://github.com/ESA-Blueshell/website/issues/1391)) ([77473d2](https://github.com/ESA-Blueshell/website/commit/77473d2d43b6f7423ccbce80915e39fb48c0d19e))
* **platform:** a deploy says in Discord which version it deployed ([#1458](https://github.com/ESA-Blueshell/website/issues/1458)) ([c8a862f](https://github.com/ESA-Blueshell/website/commit/c8a862f019c3ce1cb074ea7fda964e8343590d91))
* **platform:** Flux commits to main with its own credential ([#1447](https://github.com/ESA-Blueshell/website/issues/1447)) ([4072489](https://github.com/ESA-Blueshell/website/commit/4072489c1d40254e97bf442dcd31dc7de8cd624e))
* **platform:** Flux scans the registry for released versions ([#1443](https://github.com/ESA-Blueshell/website/issues/1443)) ([034772c](https://github.com/ESA-Blueshell/website/commit/034772c729119d9ab70d15bd9a9d2711dcf252ef))
* **platform:** the mail overlay follows the released version ([#1456](https://github.com/ESA-Blueshell/website/issues/1456)) ([8abbb62](https://github.com/ESA-Blueshell/website/commit/8abbb6285634cd1df457498146e1bdca4d612ca4)), closes [#1438](https://github.com/ESA-Blueshell/website/issues/1438)
* **platform:** the Stalwart tooling image is pinned by digest, and Keel is removed ([#1409](https://github.com/ESA-Blueshell/website/issues/1409)) ([fdc1682](https://github.com/ESA-Blueshell/website/commit/fdc16825e9f135247c274acf728068a7b1e07c3c))
* **platform:** the stateless overlay follows the released version ([#1455](https://github.com/ESA-Blueshell/website/issues/1455)) ([72f257b](https://github.com/ESA-Blueshell/website/commit/72f257b2a6e201ed413f72c0a8335e1a527b6149))
* **release:** CI pins api and frontend by digest on the release branch, and a required check verifies it ([#1404](https://github.com/ESA-Blueshell/website/issues/1404)) ([a42b55f](https://github.com/ESA-Blueshell/website/commit/a42b55faeab4e2cabbab7ccbe0ef4d54261f0d9e))
* **release:** the version tag is written once at release, and builds publish only the sha tag ([#1406](https://github.com/ESA-Blueshell/website/issues/1406)) ([c03a628](https://github.com/ESA-Blueshell/website/commit/c03a6283d354ceb35a650329dd1635d94b71abb5))
* **renovate:** a patch update merges itself once every required check passes ([#1411](https://github.com/ESA-Blueshell/website/issues/1411)) ([e4ec296](https://github.com/ESA-Blueshell/website/commit/e4ec29693a7995cc3c7854dd153fc0cce43c111b))


### Bug Fixes

* **canary:** every webhook fits inside its own timeout ([#1383](https://github.com/ESA-Blueshell/website/issues/1383)) ([b27e766](https://github.com/ESA-Blueshell/website/commit/b27e76687bbc846482c52cdce98539728f904385))
* **canary:** the gate reads whether the migration succeeded ([#1381](https://github.com/ESA-Blueshell/website/issues/1381)) ([1452892](https://github.com/ESA-Blueshell/website/commit/1452892803ef806333d949c7e4a66421ba5a9f8b))
* **ci:** a push to main writes the coverage baseline, the comment shows unit coverage ([#1403](https://github.com/ESA-Blueshell/website/issues/1403)) ([d8a1e97](https://github.com/ESA-Blueshell/website/commit/d8a1e97012586e71e1ddbff17293149fc5b93a5e)), closes [#1402](https://github.com/ESA-Blueshell/website/issues/1402)
* **ci:** one matcher decides the buckets, so a negation stops claiming every path ([#1454](https://github.com/ESA-Blueshell/website/issues/1454)) ([a6aacf0](https://github.com/ESA-Blueshell/website/commit/a6aacf0c4635becfea3cab47c1c360dcd608d40e)), closes [#1453](https://github.com/ESA-Blueshell/website/issues/1453)
* **ci:** the coverage baseline is an artifact that nothing evicts ([#1429](https://github.com/ESA-Blueshell/website/issues/1429)) ([d7d9c6c](https://github.com/ESA-Blueshell/website/commit/d7d9c6ce6b11e13e12d3abf91cb7a89fd0ff8ee2)), closes [#1428](https://github.com/ESA-Blueshell/website/issues/1428)
* **e2e:** the virtualized row is asserted inside the wait ([#1412](https://github.com/ESA-Blueshell/website/issues/1412)) ([741e08a](https://github.com/ESA-Blueshell/website/commit/741e08ad4433e1889615dbb462515b6c6ab4aaa8))
* **email:** the body copy keeps its colour in a dark mail app ([#1451](https://github.com/ESA-Blueshell/website/issues/1451)) ([41f06c6](https://github.com/ESA-Blueshell/website/commit/41f06c6fc54d03d960bfb2a20205bf8401562eed))
* **events:** a sign-up with nothing to edit says so, and dev has one to edit ([#1460](https://github.com/ESA-Blueshell/website/issues/1460)) ([2353601](https://github.com/ESA-Blueshell/website/commit/23536019f3612b193f49f21090e6f2089769248a)), closes [#1459](https://github.com/ESA-Blueshell/website/issues/1459)
* **frontend:** the user door names the member type once ([#1432](https://github.com/ESA-Blueshell/website/issues/1432)) ([1c97493](https://github.com/ESA-Blueshell/website/commit/1c974934a9f4d0b115c4b1bfadfab17688fd8552))
* **oidc:** the vault CLI has a callback it can listen on ([#1446](https://github.com/ESA-Blueshell/website/issues/1446)) ([b661f49](https://github.com/ESA-Blueshell/website/commit/b661f4910b0fe11565a6e1cfbba74a7233b3ca25))
* **platform:** the Flagger alerting secret can authenticate to Vault ([#1457](https://github.com/ESA-Blueshell/website/issues/1457)) ([f60e820](https://github.com/ESA-Blueshell/website/commit/f60e820622facc1cd76d5a5c5171d2076d2334e4))
* **platform:** the image automation commit message renders ([#1452](https://github.com/ESA-Blueshell/website/issues/1452)) ([0ad0768](https://github.com/ESA-Blueshell/website/commit/0ad076871644c9e391eb353cc6feb7b7ef675934))
* **release:** the pin commit includes the mail overlay, and the release path survives a concurrent merge, an untested sidecar and a failed migration ([#1415](https://github.com/ESA-Blueshell/website/issues/1415)) ([a150ab1](https://github.com/ESA-Blueshell/website/commit/a150ab1e78df4d691de2652750a1cb18ecf6f766))
* **release:** the pin is written once per version, and the check no longer resolves a sha ([#1414](https://github.com/ESA-Blueshell/website/issues/1414)) ([54ce607](https://github.com/ESA-Blueshell/website/commit/54ce6071a534987282646d747cc16f698822cba8))
* **release:** the pin refuses a branch that moved under it ([#1427](https://github.com/ESA-Blueshell/website/issues/1427)) ([01cca8f](https://github.com/ESA-Blueshell/website/commit/01cca8fa9489a9f631bbdc2542a6cb88e9e63edc))
* **release:** the release build decides what a version is ([#1442](https://github.com/ESA-Blueshell/website/issues/1442)) ([5d9f789](https://github.com/ESA-Blueshell/website/commit/5d9f789ca429a9674333c1973247d3b6f1843d32))
* **schema:** the changelog names itself ([#1386](https://github.com/ESA-Blueshell/website/issues/1386)) ([9371868](https://github.com/ESA-Blueshell/website/commit/9371868827e2c70b0a45e9822c3176d24b4b5df2))
* **system-tests:** a click waits for its control on its own budget ([#1384](https://github.com/ESA-Blueshell/website/issues/1384)) ([3b99d8c](https://github.com/ESA-Blueshell/website/commit/3b99d8c1a32c11cc641a3e49542a1db396ca7df4)), closes [#1287](https://github.com/ESA-Blueshell/website/issues/1287) [#1375](https://github.com/ESA-Blueshell/website/issues/1375)


### Refactoring

* **blogs:** the blog pages ask a domain ([#1416](https://github.com/ESA-Blueshell/website/issues/1416)) ([394af41](https://github.com/ESA-Blueshell/website/commit/394af4172e5ade7c592f9e83f1ac0e505c2aadf4)), closes [#1257](https://github.com/ESA-Blueshell/website/issues/1257)
* **committees:** the committee pages ask their domain ([#1418](https://github.com/ESA-Blueshell/website/issues/1418)) ([78191e9](https://github.com/ESA-Blueshell/website/commit/78191e97464e2985df7bd370458d7792a73c0ca6)), closes [#1259](https://github.com/ESA-Blueshell/website/issues/1259)
* **components:** the cards, lists and rows ask their domains ([#1421](https://github.com/ESA-Blueshell/website/issues/1421)) ([f3817c6](https://github.com/ESA-Blueshell/website/commit/f3817c6dbf9a0cc28fefe88f43dde58ad1ad9661))
* **components:** the committee, survey and confirmation forms ask their domains ([#1424](https://github.com/ESA-Blueshell/website/issues/1424)) ([81d5fc5](https://github.com/ESA-Blueshell/website/commit/81d5fc54620cb3dcd32ae73b78c5b32f6347e851))
* **components:** the contribution and Discord components ask their domains ([#1420](https://github.com/ESA-Blueshell/website/issues/1420)) ([6c12b17](https://github.com/ESA-Blueshell/website/commit/6c12b170a415d96e11be344c619411fe45e11bc9))
* **components:** the field components ask their domains ([#1423](https://github.com/ESA-Blueshell/website/issues/1423)) ([4c76668](https://github.com/ESA-Blueshell/website/commit/4c766688ea0198901624051c8eb470075d074fe4))
* **components:** the job and membership dialogs ask their domains ([#1422](https://github.com/ESA-Blueshell/website/issues/1422)) ([1118dca](https://github.com/ESA-Blueshell/website/commit/1118dca80f9301f25bfd9d2f8579cbbf3132eeb3))
* **components:** the signup forms ask their domain ([#1425](https://github.com/ESA-Blueshell/website/issues/1425)) ([3528b80](https://github.com/ESA-Blueshell/website/commit/3528b8062abd6e4ea412069e606313fe74e04d5c))
* **events:** the event edit page asks a domain ([#1417](https://github.com/ESA-Blueshell/website/issues/1417)) ([9631d6c](https://github.com/ESA-Blueshell/website/commit/9631d6c7667c3100f9b90325022283c78e6adce2)), closes [#1258](https://github.com/ESA-Blueshell/website/issues/1258)
* **frontend:** every page and component asks a domain, and the allowlist goes ([#1444](https://github.com/ESA-Blueshell/website/issues/1444)) ([089a6b1](https://github.com/ESA-Blueshell/website/commit/089a6b16a0cf84e9e5c813a7b2ac38dc403fc826)), closes [#1260](https://github.com/ESA-Blueshell/website/issues/1260) [#1261](https://github.com/ESA-Blueshell/website/issues/1261) [#1274](https://github.com/ESA-Blueshell/website/issues/1274) [#1275](https://github.com/ESA-Blueshell/website/issues/1275) [#1276](https://github.com/ESA-Blueshell/website/issues/1276) [#1266](https://github.com/ESA-Blueshell/website/issues/1266)
* **membership:** the membership sign-up asks a domain ([#1419](https://github.com/ESA-Blueshell/website/issues/1419)) ([3980829](https://github.com/ESA-Blueshell/website/commit/3980829d3cad81c2c2017ceae550b7e20506857d)), closes [#1262](https://github.com/ESA-Blueshell/website/issues/1262)

## [1.8.0](https://github.com/ESA-Blueshell/website/compare/v1.7.1...v1.8.0) (2026-09-21)


### Features

* **api:** add association statistics end point, and make event banners public ([#1097](https://github.com/ESA-Blueshell/website/issues/1097)) ([0a78185](https://github.com/ESA-Blueshell/website/commit/0a781859d12d0275b3fe7d210188ac2bf94f71ba))
* **association:** about us and become-a-partner move onto the island ([#1104](https://github.com/ESA-Blueshell/website/issues/1104)) ([a7e8e5a](https://github.com/ESA-Blueshell/website/commit/a7e8e5ab4c87a8f50873d02c2767b5b12489ab52))
* **bar:** the bar leaves Vuetify, and its menus stop growing past their triggers ([#1361](https://github.com/ESA-Blueshell/website/issues/1361)) ([6eba1d2](https://github.com/ESA-Blueshell/website/commit/6eba1d294ab0ad589eb520997ec1064b58f02a3e))
* **canary:** the canary is measured, not just pinged ([#1359](https://github.com/ESA-Blueshell/website/issues/1359)) ([a27170c](https://github.com/ESA-Blueshell/website/commit/a27170c80c4a3b8d710075f0a9a85fe0e15a0651))
* **files:** a game's icon and a team's may be a vector ([#1219](https://github.com/ESA-Blueshell/website/issues/1219)) ([af52910](https://github.com/ESA-Blueshell/website/commit/af52910112fcabc1ef9d82c54b8f09fd57dac94c)), closes [#857](https://github.com/ESA-Blueshell/website/issues/857)
* **frontend:** a domain may not reach into another domain's innards either ([#1317](https://github.com/ESA-Blueshell/website/issues/1317)) ([1aa16c4](https://github.com/ESA-Blueshell/website/commit/1aa16c45d5944b5ff0585da81b4022058e54577d))
* **frontend:** the built image serves the api at the page's own origin ([#1171](https://github.com/ESA-Blueshell/website/issues/1171)) ([28eeb68](https://github.com/ESA-Blueshell/website/commit/28eeb68dafe254d2d241f34bd689831222b89f1a)), closes [#1000](https://github.com/ESA-Blueshell/website/issues/1000)
* **history:** a line you can read while scrolling past it ([#1142](https://github.com/ESA-Blueshell/website/issues/1142)) ([712aabf](https://github.com/ESA-Blueshell/website/commit/712aabf7daa7d110a558bde83b2f0e5f7e9a8631))
* **island:** a swipe spawns a band already open ([#1093](https://github.com/ESA-Blueshell/website/issues/1093)) ([7e5d450](https://github.com/ESA-Blueshell/website/commit/7e5d450984a8382da217db629ed2fcf3b1d6a8b0))
* **island:** the site bar joins the island and follows the theme ([#1116](https://github.com/ESA-Blueshell/website/issues/1116)) ([8482d94](https://github.com/ESA-Blueshell/website/commit/8482d94ced51826e175b67a5c0d6183b86ae99f5))
* **membership:** the membership page is rebuilt on the island ([#1115](https://github.com/ESA-Blueshell/website/issues/1115)) ([711dfc6](https://github.com/ESA-Blueshell/website/commit/711dfc6bee5e7c9ca5d9972d4d75aad7a67c5821))
* **partners:** show a partner the space, on the thing it is on ([#1136](https://github.com/ESA-Blueshell/website/issues/1136)) ([aa359d8](https://github.com/ESA-Blueshell/website/commit/aa359d8034a3bd3d4fcaa80cbc3a8ebda29b93b7))
* **platform:** the site's images name a version, and Flagger promotes the pair ([#1316](https://github.com/ESA-Blueshell/website/issues/1316)) ([fe4d58f](https://github.com/ESA-Blueshell/website/commit/fe4d58f4c84ec2852fcffd37d236e2edca8a69d2))
* **release:** one app identity owns the release branch ([#1362](https://github.com/ESA-Blueshell/website/issues/1362)) ([17b867b](https://github.com/ESA-Blueshell/website/commit/17b867b432926165781625aad3a7ae0341bae71f))
* **roles:** an admin changes what a person may reach, and the change leaves a record ([#1268](https://github.com/ESA-Blueshell/website/issues/1268)) ([3ca6075](https://github.com/ESA-Blueshell/website/commit/3ca6075d02f64949a7fb71f0ab27289e04f6dcde))
* **schema:** a changeset the last release cannot read does not merge ([#1374](https://github.com/ESA-Blueshell/website/issues/1374)) ([0521204](https://github.com/ESA-Blueshell/website/commit/0521204609eba9117fd1ffb054ab03c6e71df26b))
* **schema:** the schema moves once per release, not once per pod ([#1360](https://github.com/ESA-Blueshell/website/issues/1360)) ([d03a25f](https://github.com/ESA-Blueshell/website/commit/d03a25fc26759e6bdcde3f14f00557b342751df7))
* **users:** the committee picker asks the api for the name typed ([#1146](https://github.com/ESA-Blueshell/website/issues/1146)) ([c3ef905](https://github.com/ESA-Blueshell/website/commit/c3ef905c9a1fdc24590836972be4fc9bce4a923b)), closes [#1139](https://github.com/ESA-Blueshell/website/issues/1139)
* **version:** what is running says which release it is ([#1334](https://github.com/ESA-Blueshell/website/issues/1334)) ([210a916](https://github.com/ESA-Blueshell/website/commit/210a916de042435866a527a13021e8fb308c1590))


### Bug Fixes

* **api:** a request that names a page or a size gets one ([#1156](https://github.com/ESA-Blueshell/website/issues/1156)) ([38c2a4b](https://github.com/ESA-Blueshell/website/commit/38c2a4ba58383618054b745aa0803d6455b3c70b)), closes [#1145](https://github.com/ESA-Blueshell/website/issues/1145)
* **api:** the element constraints on bulk ids are actually emitted ([#1131](https://github.com/ESA-Blueshell/website/issues/1131)) ([e037698](https://github.com/ESA-Blueshell/website/commit/e0376982e349231974c9d961fa62aaa319640752))
* **association:** a figure counted as none comes off the band ([#1182](https://github.com/ESA-Blueshell/website/issues/1182)) ([3045a3c](https://github.com/ESA-Blueshell/website/commit/3045a3c64bc9e233970c30bf6bcf1310d8fa108f))
* **auth:** a sign-in lasts as long as the session behind it ([#1242](https://github.com/ESA-Blueshell/website/issues/1242)) ([22759c4](https://github.com/ESA-Blueshell/website/commit/22759c4879fbe4bbe698cc7a058e88551512d7b6))
* **bar:** the bar draws one wordmark, not one per theme ([#1369](https://github.com/ESA-Blueshell/website/issues/1369)) ([54db3b7](https://github.com/ESA-Blueshell/website/commit/54db3b78f775f7adec425f641a8bd8e4c4646038))
* **boards:** the photograph's promised width is measured from the picture ([#1209](https://github.com/ESA-Blueshell/website/issues/1209)) ([612ece9](https://github.com/ESA-Blueshell/website/commit/612ece95dfdfd5aebc81ed8ad63a6fb22e9f1b42))
* **canary:** a deleted Canary gives the Deployment back ([#1350](https://github.com/ESA-Blueshell/website/issues/1350)) ([f25a58d](https://github.com/ESA-Blueshell/website/commit/f25a58d175d3c20847a122397b908623cc88294b))
* **ci:** the pull request report has a main coverage baseline to compare against ([#1377](https://github.com/ESA-Blueshell/website/issues/1377)) ([f8b0955](https://github.com/ESA-Blueshell/website/commit/f8b095531fbdd121d857b12b506a5dcf1c6f6dad)), closes [#1376](https://github.com/ESA-Blueshell/website/issues/1376)
* **committees:** a seat without a role reads back, and does not 500 ([#1333](https://github.com/ESA-Blueshell/website/issues/1333)) ([8c34b28](https://github.com/ESA-Blueshell/website/commit/8c34b28ec62632518108f3455c15f09f7dd12a19)), closes [#1332](https://github.com/ESA-Blueshell/website/issues/1332)
* **committees:** the manager hears the event the form sends ([#1215](https://github.com/ESA-Blueshell/website/issues/1215)) ([3ef08ec](https://github.com/ESA-Blueshell/website/commit/3ef08ecf470137f266ca5b744a713a421e3ee670))
* **esports:** a refused game entry says why it was refused ([#1152](https://github.com/ESA-Blueshell/website/issues/1152)) ([24c4a8f](https://github.com/ESA-Blueshell/website/commit/24c4a8f3cd6e92e58a803baa3202ec7aad437ecc))
* **esports:** a saved season comes back carrying the season ([#1122](https://github.com/ESA-Blueshell/website/issues/1122)) ([d86039c](https://github.com/ESA-Blueshell/website/commit/d86039c194addd1b6d32b86f257af57df26cde02))
* **esports:** a season id in the url is a whole number ([#1121](https://github.com/ESA-Blueshell/website/issues/1121)) ([e73a303](https://github.com/ESA-Blueshell/website/commit/e73a3038a95624973ff646f4c495af69ad47055b))
* **esports:** the seed says what members remember ([#1069](https://github.com/ESA-Blueshell/website/issues/1069)) ([fd57c4f](https://github.com/ESA-Blueshell/website/commit/fd57c4fd8a9a8ae41b65c1d606860025e45bd252))
* **files:** every kind says what it admits, and an empty list refuses rather than admits ([#1279](https://github.com/ESA-Blueshell/website/issues/1279)) ([1eaa396](https://github.com/ESA-Blueshell/website/commit/1eaa396c8c3900f9825fc9f1b8f4c3de9962886b))
* **frontend:** a committee saves whatever the user list says ([#1138](https://github.com/ESA-Blueshell/website/issues/1138)) ([ec07271](https://github.com/ESA-Blueshell/website/commit/ec072717e30f41553305dc606ab7fa3d91e510c2))
* **frontend:** a committee saves when its member is off the first page ([#1100](https://github.com/ESA-Blueshell/website/issues/1100)) ([dfeffc8](https://github.com/ESA-Blueshell/website/commit/dfeffc8eb40c8e80d26b0d07a51eab6b4cdbc015)), closes [#1099](https://github.com/ESA-Blueshell/website/issues/1099)
* **frontend:** a failed account read is not a list with nobody on it ([#1119](https://github.com/ESA-Blueshell/website/issues/1119)) ([5df19a8](https://github.com/ESA-Blueshell/website/commit/5df19a8ae2a841398b0a87c72b2472e0cf472dd6))
* **frontend:** a heading that names its own size keeps it ([#1175](https://github.com/ESA-Blueshell/website/issues/1175)) ([9276b3a](https://github.com/ESA-Blueshell/website/commit/9276b3a063d9ac9fd89fadf2038e9beb2c34e505))
* **frontend:** the job catalogue names every job the api registers ([#1124](https://github.com/ESA-Blueshell/website/issues/1124)) ([71422ef](https://github.com/ESA-Blueshell/website/commit/71422efa486e3838c6e676eef380816f3ab5535f))
* **frontend:** the reduced-motion preference reaches the page ([#1151](https://github.com/ESA-Blueshell/website/issues/1151)) ([acd8b44](https://github.com/ESA-Blueshell/website/commit/acd8b44579d11999cc046ba41ed98daa2e68252c))
* **frontend:** the user table header stays at its edge ([#1065](https://github.com/ESA-Blueshell/website/issues/1065)) ([ab82434](https://github.com/ESA-Blueshell/website/commit/ab82434b63b385a3d860982dbd4e0e690776d1dc))
* **island:** every milestone opens as the reader goes past it ([#1155](https://github.com/ESA-Blueshell/website/issues/1155)) ([b81bb10](https://github.com/ESA-Blueshell/website/commit/b81bb105f3757f8b04283bf1b791e4d02a5e4f63))
* **island:** the history is as wide as the words want, and stays written ([#1168](https://github.com/ESA-Blueshell/website/issues/1168)) ([cd52042](https://github.com/ESA-Blueshell/website/commit/cd5204244ed35061fed0b63077cb196da706761d))
* **island:** the history is shorter again ([#1201](https://github.com/ESA-Blueshell/website/issues/1201)) ([a62f128](https://github.com/ESA-Blueshell/website/commit/a62f128e0853daf8072c43920981c1e7c598e165))
* **island:** the pinned band's ground is pinned with its ink ([#1214](https://github.com/ESA-Blueshell/website/issues/1214)) ([63b45f5](https://github.com/ESA-Blueshell/website/commit/63b45f5473f310679424101ee5b8110f8f40fec6)), closes [#984](https://github.com/ESA-Blueshell/website/issues/984)
* **partners:** the sponsor pack's own artwork, and the arrows point at it ([#1205](https://github.com/ESA-Blueshell/website/issues/1205)) ([aaad433](https://github.com/ESA-Blueshell/website/commit/aaad433c610a25a5b68fc54d22549876c5da8ace))
* **platform:** every chart names the version it runs ([#1246](https://github.com/ESA-Blueshell/website/issues/1246)) ([8812b42](https://github.com/ESA-Blueshell/website/commit/8812b428e4f761a633c90f93d92d141cc06940d8))
* **platform:** external-dns names the policy it already enforces ([#1248](https://github.com/ESA-Blueshell/website/issues/1248)) ([fa7598f](https://github.com/ESA-Blueshell/website/commit/fa7598fc43c22421de70b8a264a46a16cee8076b))
* **platform:** the injector retires its old pod without asking for Recreate ([#1270](https://github.com/ESA-Blueshell/website/issues/1270)) ([e7a245c](https://github.com/ESA-Blueshell/website/commit/e7a245cf9b4028977ba02502d256c701fd7352fc))
* **platform:** the old pod goes first, on the key the chart reads ([#1250](https://github.com/ESA-Blueshell/website/issues/1250)) ([45c0edf](https://github.com/ESA-Blueshell/website/commit/45c0edf34bbbc55e224aebb0f9eb0f556eea4ac0))
* **release:** a release run reaches its jobs again ([#1364](https://github.com/ESA-Blueshell/website/issues/1364)) ([853d2d1](https://github.com/ESA-Blueshell/website/commit/853d2d1d28afd0e5ac444f65747804e32c3b27a3))
* **schema:** the baseline is the schema production has ([#1345](https://github.com/ESA-Blueshell/website/issues/1345)) ([49e031b](https://github.com/ESA-Blueshell/website/commit/49e031b2f8f9fcea5f09d03ed3bf14fd915458ae))
* **schema:** the drop waits a release ([#1366](https://github.com/ESA-Blueshell/website/issues/1366)) ([855c429](https://github.com/ESA-Blueshell/website/commit/855c429ce7c3b70af804132c748aa326883a3a19))
* **system-tests:** a failure log times the test it belongs to ([#1144](https://github.com/ESA-Blueshell/website/issues/1144)) ([8e7309e](https://github.com/ESA-Blueshell/website/commit/8e7309ed50810632dd757b873f4eaefda29790eb))
* **system-tests:** the member picker is given time to answer ([#1154](https://github.com/ESA-Blueshell/website/issues/1154)) ([9f59629](https://github.com/ESA-Blueshell/website/commit/9f59629f8f76a54b1bb77de3596c4f824d7b6354))
* **tests:** load the module before the clock starts ([#1147](https://github.com/ESA-Blueshell/website/issues/1147)) ([500ff9e](https://github.com/ESA-Blueshell/website/commit/500ff9ebc53a8da08145d554e5721d7bba649ed2))
* **tests:** say enableAutoUnmount once, and give all three pages one events band ([#1128](https://github.com/ESA-Blueshell/website/issues/1128)) ([1059671](https://github.com/ESA-Blueshell/website/commit/1059671d98a0191f45dc93821e61f6d17854fef9))


### Refactoring

* **api:** a read maps its entity to its response once ([#1185](https://github.com/ESA-Blueshell/website/issues/1185)) ([c671ab5](https://github.com/ESA-Blueshell/website/commit/c671ab559670d452a18c113ab46e308fbaf296e9))
* **auth:** the login cookie keeps who the reader is, and nothing else ([#1252](https://github.com/ESA-Blueshell/website/issues/1252)) ([98bcb83](https://github.com/ESA-Blueshell/website/commit/98bcb83f527ceeb4b1fbf42c554e548a03495d3f))
* **auth:** the login page asks whether the reader signed in ([#1245](https://github.com/ESA-Blueshell/website/issues/1245)) ([eaeaca7](https://github.com/ESA-Blueshell/website/commit/eaeaca7fdc6b36d68e8a4472acbea57caf98aad1))
* **boards:** the asset file names go ([#1218](https://github.com/ESA-Blueshell/website/issues/1218)) ([d2efc1c](https://github.com/ESA-Blueshell/website/commit/d2efc1c2878a1f7c6662f30ffebf3b1172667533))
* **cohort:** a cohort job is a line, not a file ([#1191](https://github.com/ESA-Blueshell/website/issues/1191)) ([1db1716](https://github.com/ESA-Blueshell/website/commit/1db1716c713430d00d66a9cf7b1b3595783b8fc4))
* **cohort:** one port over the list, with the vendor behind contact ([#1172](https://github.com/ESA-Blueshell/website/issues/1172)) ([01546d5](https://github.com/ESA-Blueshell/website/commit/01546d565eebc64e1f660ee05483c62dbdf185e3))
* **cohorts:** the cohort pages read a domain record, not a response ([#1199](https://github.com/ESA-Blueshell/website/issues/1199)) ([f5fbe0d](https://github.com/ESA-Blueshell/website/commit/f5fbe0d756e8d828bd6905c4cec61ac356bae9e7))
* **contact:** the module keeps only what something reads ([#1178](https://github.com/ESA-Blueshell/website/issues/1178)) ([4b32c5a](https://github.com/ESA-Blueshell/website/commit/4b32c5a6f9cbe02734aa4b5fd6861211e5d20a09))
* **contribution:** one kind, one decision, and a preview that reads once ([#1193](https://github.com/ESA-Blueshell/website/issues/1193)) ([ecd7aad](https://github.com/ESA-Blueshell/website/commit/ecd7aad885e26a2905495cd222d171e032f1c8ad))
* **contribution:** the bulk dialogs reach the api through a domain ([#1160](https://github.com/ESA-Blueshell/website/issues/1160)) ([9d9d385](https://github.com/ESA-Blueshell/website/commit/9d9d38546ae799ae78f1a6fd01f92c4f6e804618)), closes [#946](https://github.com/ESA-Blueshell/website/issues/946)
* **file:** a store behind the files, and the rules out from under it ([#1170](https://github.com/ESA-Blueshell/website/issues/1170)) ([2895463](https://github.com/ESA-Blueshell/website/commit/28954632d09f1ea696f5cfa23becd794a1471d89))
* **frontend:** a domain behind each manager page ([#1188](https://github.com/ESA-Blueshell/website/issues/1188)) ([135c1ba](https://github.com/ESA-Blueshell/website/commit/135c1ba0aaacbb6e2f69209fad8266a7d57bbf8f))
* **frontend:** a domain is entered through its door ([#1206](https://github.com/ESA-Blueshell/website/issues/1206)) ([225805d](https://github.com/ESA-Blueshell/website/commit/225805d8576497ba874484bb1c86e0f6a4f64509))
* **frontend:** one reader for what a refusal says ([#1153](https://github.com/ESA-Blueshell/website/issues/1153)) ([0bf1f6f](https://github.com/ESA-Blueshell/website/commit/0bf1f6f3dac6485b7265a7ab64bf2ca5936943bc))
* **frontend:** one rule has one definition ([#1123](https://github.com/ESA-Blueshell/website/issues/1123)) ([54b3725](https://github.com/ESA-Blueshell/website/commit/54b372548e3b37c54a4265d8e7fa76fe3447da15))
* **frontend:** the navigation guard returns its answer ([#1200](https://github.com/ESA-Blueshell/website/issues/1200)) ([4a9b0d6](https://github.com/ESA-Blueshell/website/commit/4a9b0d69f0a5cb026f9c1d9799b10fa95aa9fe82))
* **island:** one header band, and the site bar leaves the app shell ([#1103](https://github.com/ESA-Blueshell/website/issues/1103)) ([dbef38a](https://github.com/ESA-Blueshell/website/commit/dbef38a11c4201430185543960ca0d7f67a23653)), closes [#1074](https://github.com/ESA-Blueshell/website/issues/1074)
* **jobs:** queueing a job crosses one seam ([#1179](https://github.com/ESA-Blueshell/website/issues/1179)) ([363c1f3](https://github.com/ESA-Blueshell/website/commit/363c1f3255318920ea501353e9fa4a9f7a95e0e8))
* **login:** the login and activation pages ask a domain ([#1253](https://github.com/ESA-Blueshell/website/issues/1253)) ([c4aa290](https://github.com/ESA-Blueshell/website/commit/c4aa2903ab4496eaed530221a4eee4b4d3be9fac))
* **management:** the manager pages ask a domain ([#1210](https://github.com/ESA-Blueshell/website/issues/1210)) ([a26a658](https://github.com/ESA-Blueshell/website/commit/a26a658bbc4146fcf952cafa24bb31bcf3d92028))
* **schema:** Liquibase owns the schema, from a baseline that rolls back ([#1330](https://github.com/ESA-Blueshell/website/issues/1330)) ([ec44228](https://github.com/ESA-Blueshell/website/commit/ec44228ad91d57fbc5035c4bf2343793f40e9517))
* **seed:** the boards and the esports history load on start, not in a migration ([#1328](https://github.com/ESA-Blueshell/website/issues/1328)) ([3b7803c](https://github.com/ESA-Blueshell/website/commit/3b7803c8e4d03d9074bf34fb592bd9c86ae25b30))
* **system-tests:** every shared acceptance step has one owner ([#1098](https://github.com/ESA-Blueshell/website/issues/1098)) ([3254561](https://github.com/ESA-Blueshell/website/commit/3254561ac7232fea17ddb16de8d0189d1b828cdc))
* **system-tests:** recovery emails say what the person got ([#1117](https://github.com/ESA-Blueshell/website/issues/1117)) ([c7c3424](https://github.com/ESA-Blueshell/website/commit/c7c3424282da8f55b8f63be472c120785bc32a92)), closes [#966](https://github.com/ESA-Blueshell/website/issues/966)
* **system-tests:** the bulk feature says what was recorded ([#1114](https://github.com/ESA-Blueshell/website/issues/1114)) ([a24eb13](https://github.com/ESA-Blueshell/website/commit/a24eb132ea5c2c4ee962530aee70856a4b098ffe)), closes [#965](https://github.com/ESA-Blueshell/website/issues/965)


### Documentation

* **adr:** ADR-001 says what is true about the domains ([#1315](https://github.com/ESA-Blueshell/website/issues/1315)) ([a499910](https://github.com/ESA-Blueshell/website/commit/a499910f3d938ba8d9854b580ff3e191f53e7393))
* **adr:** ADR-017 keeps what the build cannot check ([#1197](https://github.com/ESA-Blueshell/website/issues/1197)) ([f0838c0](https://github.com/ESA-Blueshell/website/commit/f0838c01d61af983bcbb7ba152227c162f21876a)), closes [#907](https://github.com/ESA-Blueshell/website/issues/907)
* **adr:** the anti-corruption layers are the ones we have ([#1208](https://github.com/ESA-Blueshell/website/issues/1208)) ([aafa085](https://github.com/ESA-Blueshell/website/commit/aafa085618a3475bde691ba8ee8d630ec954b6aa)), closes [#1196](https://github.com/ESA-Blueshell/website/issues/1196)
* **ci:** the diagnostics say which test they belong to, and which they do not ([#1202](https://github.com/ESA-Blueshell/website/issues/1202)) ([4dbef91](https://github.com/ESA-Blueshell/website/commit/4dbef918040e2fe477a451bea4837dabded3e5c8))
* **design:** the island says what it looks like, and the corners are square ([#1343](https://github.com/ESA-Blueshell/website/issues/1343)) ([d4a37ec](https://github.com/ESA-Blueshell/website/commit/d4a37ec6d870a944ed814688737232f8b3c8a225))


### Build and Dependencies

* **deps-dev:** bump vitest and @vitest/coverage-istanbul from 4.1.11 to 5.0.0 in /services/frontend ([#1231](https://github.com/ESA-Blueshell/website/issues/1231)) ([325a016](https://github.com/ESA-Blueshell/website/commit/325a016f2c50384d4f2d3f872dfb985a83ce4846))
* **deps:** bump @humanfs/node ([#1225](https://github.com/ESA-Blueshell/website/issues/1225)) ([2ab68a9](https://github.com/ESA-Blueshell/website/commit/2ab68a9902b432cad49ffa8c67706d0a47497f43))
* **deps:** bump eclipse-temurin in /services/api ([#1227](https://github.com/ESA-Blueshell/website/issues/1227)) ([04290d3](https://github.com/ESA-Blueshell/website/commit/04290d32244a5c3c075d9db53616938c0ee1dd78))
* **deps:** bump nginxinc/nginx-unprivileged in /services/frontend ([#1161](https://github.com/ESA-Blueshell/website/issues/1161)) ([83f39bb](https://github.com/ESA-Blueshell/website/commit/83f39bb62abd70324b1f5e789d6ce4ef1579e045))
* **deps:** bump nginxinc/nginx-unprivileged in /services/frontend ([#1228](https://github.com/ESA-Blueshell/website/issues/1228)) ([a9f3ae9](https://github.com/ESA-Blueshell/website/commit/a9f3ae90fc3431b101d224845a9275d07d18835c))
* **deps:** bump node ([#1226](https://github.com/ESA-Blueshell/website/issues/1226)) ([33aedb6](https://github.com/ESA-Blueshell/website/commit/33aedb6d2b2ae13bc7d0a42e97d13204afda3974))
* **deps:** bump the actions group with 3 updates ([#1163](https://github.com/ESA-Blueshell/website/issues/1163)) ([b5bd9f4](https://github.com/ESA-Blueshell/website/commit/b5bd9f47c0c858b8e754056858affe331140b6c9))
* **deps:** bump the frontend group ([#1164](https://github.com/ESA-Blueshell/website/issues/1164)) ([2407728](https://github.com/ESA-Blueshell/website/commit/2407728efad9f76f6dca18551d1f66f04643f4e0))
* **deps:** bump the frontend group in /services/frontend with 13 updates ([#1230](https://github.com/ESA-Blueshell/website/issues/1230)) ([7f2d8aa](https://github.com/ESA-Blueshell/website/commit/7f2d8aa631129becfb07f2c3d2d4de8a66238975))
* **deps:** bump the gradle group across 1 directory with 8 updates ([#1229](https://github.com/ESA-Blueshell/website/issues/1229)) ([ccda337](https://github.com/ESA-Blueshell/website/commit/ccda33783fd60da17e1d62950b19fc069197e968))
* **deps:** bump the gradle group across 2 directories with 7 updates ([#1162](https://github.com/ESA-Blueshell/website/issues/1162)) ([85779eb](https://github.com/ESA-Blueshell/website/commit/85779eb239b38b44fcf478fc9ffd49bbed008226))
* **deps:** one bot updates this repo, and it reaches the cluster too ([#1234](https://github.com/ESA-Blueshell/website/issues/1234)) ([fc4a68b](https://github.com/ESA-Blueshell/website/commit/fc4a68bb28f0080d0f0af3686f9c30c11a22aa88))
* **deps:** vuetify 4.2.1, and the sass variable it drops ([#1236](https://github.com/ESA-Blueshell/website/issues/1236)) ([badcd18](https://github.com/ESA-Blueshell/website/commit/badcd187765f9a208f5213958a8fa92c23bebb91))


### Styling

* **kotlin:** ktlint runs, and the tree is formatted to it ([#1285](https://github.com/ESA-Blueshell/website/issues/1285)) ([40a5b26](https://github.com/ESA-Blueshell/website/commit/40a5b269d6698d07d9cac143e2cbda62c73597b6))

## [1.7.1](https://github.com/ESA-Blueshell/website/compare/v1.7.0...v1.7.1) (2026-09-04)


### Bug Fixes

* **frontend:** a new contribution period is created instead of the edited one ([#1059](https://github.com/ESA-Blueshell/website/issues/1059)) ([eb99199](https://github.com/ESA-Blueshell/website/commit/eb99199bec2eb396b342056350e03c6ba44b81e7)), closes [#1056](https://github.com/ESA-Blueshell/website/issues/1056)
* **frontend:** payment emails can be sent from a phone ([#1051](https://github.com/ESA-Blueshell/website/issues/1051)) ([d1f306f](https://github.com/ESA-Blueshell/website/commit/d1f306faf924de9501ced38d1fccde7e5dddd5e0)), closes [#1041](https://github.com/ESA-Blueshell/website/issues/1041)
* **frontend:** the details step saves the profile the account already has ([#1055](https://github.com/ESA-Blueshell/website/issues/1055)) ([b17ecc4](https://github.com/ESA-Blueshell/website/commit/b17ecc437576ec600c235df5c006c42a13730047)), closes [#1052](https://github.com/ESA-Blueshell/website/issues/1052)
* **frontend:** the user picker fills in once its list of users arrives ([#1047](https://github.com/ESA-Blueshell/website/issues/1047)) ([de12735](https://github.com/ESA-Blueshell/website/commit/de1273565208f203e4ebbd54429606163dd43238)), closes [#1046](https://github.com/ESA-Blueshell/website/issues/1046)


### Refactoring

* **esports:** a line-up draft is published behind one seam ([#1061](https://github.com/ESA-Blueshell/website/issues/1061)) ([28f438b](https://github.com/ESA-Blueshell/website/commit/28f438b3879e3a560373a5cc4999074b5e510ca1))


### Documentation

* **contact:** the adapters are selected, not fanned out across ([#1050](https://github.com/ESA-Blueshell/website/issues/1050)) ([0a6f1ff](https://github.com/ESA-Blueshell/website/commit/0a6f1ffbd4bb08c220f0c6bf874073157aeb759f)), closes [#1048](https://github.com/ESA-Blueshell/website/issues/1048)
* **oidc:** four hosts are gated, not five ([#1054](https://github.com/ESA-Blueshell/website/issues/1054)) ([1c8bd55](https://github.com/ESA-Blueshell/website/commit/1c8bd550fc7cfd9738769c2d94cf7cd696f709fa)), closes [#1053](https://github.com/ESA-Blueshell/website/issues/1053)

## [1.7.0](https://github.com/ESA-Blueshell/website/compare/v1.6.0...v1.7.0) (2026-09-04)


### Features

* **board:** a board and a seat carry a picture somebody chose ([#955](https://github.com/ESA-Blueshell/website/issues/955)) ([47bf7e3](https://github.com/ESA-Blueshell/website/commit/47bf7e31b92197fa5ea3c1ddf2b3b02439e72929))
* **board:** a board is corrected on the page it is read on ([#962](https://github.com/ESA-Blueshell/website/issues/962)) ([0a1799f](https://github.com/ESA-Blueshell/website/commit/0a1799fbba5a11ddde8dbb354082d14d631f8680))
* **board:** a board with people on it is not removed by one click ([#951](https://github.com/ESA-Blueshell/website/issues/951)) ([f340326](https://github.com/ESA-Blueshell/website/commit/f340326d952f7a4f88bf368d70818f3691a34fd8))
* **board:** a member's description is read below their portrait on a phone ([#997](https://github.com/ESA-Blueshell/website/issues/997)) ([89377a3](https://github.com/ESA-Blueshell/website/commit/89377a3f885ea9dc48f8dbe0cebfaad7ba7acb7e))
* **board:** a seat is filled in on the page it is read on ([#968](https://github.com/ESA-Blueshell/website/issues/968)) ([43b7a2a](https://github.com/ESA-Blueshell/website/commit/43b7a2a6eded786b2d87aaa07666972d2f15a2cd))
* **board:** a seat opens to say who sat in it ([#959](https://github.com/ESA-Blueshell/website/issues/959)) ([31b497f](https://github.com/ESA-Blueshell/website/commit/31b497ffa00165a57a26f625e63de25f113afc00))
* **board:** four boards draw in the colour they chose ([#958](https://github.com/ESA-Blueshell/website/issues/958)) ([2bcd282](https://github.com/ESA-Blueshell/website/commit/2bcd282445cc3d56c5ec1471a5b2740597ade1e2))
* **board:** how a board's number, year and seats read ([#953](https://github.com/ESA-Blueshell/website/issues/953)) ([3e4fc5a](https://github.com/ESA-Blueshell/website/commit/3e4fc5a33ba6945e3d5ea184b92eeb01fd131a02))
* **board:** make the nine boards data the association owns ([#647](https://github.com/ESA-Blueshell/website/issues/647)) ([c68a7df](https://github.com/ESA-Blueshell/website/commit/c68a7dfe26090a7c8b6e978c9ac11e6e4f4d29db))
* **board:** restyle the board page on the shared slice band ([#971](https://github.com/ESA-Blueshell/website/issues/971)) ([eeb954a](https://github.com/ESA-Blueshell/website/commit/eeb954accef4e3dbae7324bd58a50c31650e0fb0))
* **board:** the boards come from the files that record them ([#949](https://github.com/ESA-Blueshell/website/issues/949)) ([d8af803](https://github.com/ESA-Blueshell/website/commit/d8af80397d99813981e0b7f56bb896172d95184b))
* **board:** the boards read as a timeline ([#956](https://github.com/ESA-Blueshell/website/issues/956)) ([1ae76a9](https://github.com/ESA-Blueshell/website/commit/1ae76a9ddc85a666d6033b46efa3f154d34816c6))
* **cohorts:** move a Brevo list to another folder ([#600](https://github.com/ESA-Blueshell/website/issues/600)) ([77f9048](https://github.com/ESA-Blueshell/website/commit/77f9048a7916182323739d97fa41052c0d97dc70))
* **cohorts:** move a set of Brevo lists to one folder ([#604](https://github.com/ESA-Blueshell/website/issues/604)) ([489f3a5](https://github.com/ESA-Blueshell/website/commit/489f3a5722db7b6626309302bab018a10ed03780))
* **cohorts:** say where each sync target sits, not only what it is called ([#654](https://github.com/ESA-Blueshell/website/issues/654)) ([e8c6111](https://github.com/ESA-Blueshell/website/commit/e8c6111be397d29bc1336acd676201b72b525dbe))
* **cohorts:** tell the cohort page whether each member is in step with its target ([#626](https://github.com/ESA-Blueshell/website/issues/626)) ([a91c4ef](https://github.com/ESA-Blueshell/website/commit/a91c4ef00756882f1d24e8670a1e13bab57622de))
* **committees:** let a member sit on a committee without a role ([#594](https://github.com/ESA-Blueshell/website/issues/594)) ([5a9ce4f](https://github.com/ESA-Blueshell/website/commit/5a9ce4fd8e0a71efe8a004ea2dc2ab8fb2fa2670))
* **contribution:** new members are told how to pay ([#1009](https://github.com/ESA-Blueshell/website/issues/1009)) ([5471206](https://github.com/ESA-Blueshell/website/commit/5471206771529705d23e63d55e0fc9026c68bec6))
* **contribution:** one fee cycle over the direct-debit partition, and an ask is a row ([#908](https://github.com/ESA-Blueshell/website/issues/908)) ([9a0e5a1](https://github.com/ESA-Blueshell/website/commit/9a0e5a186944530e5d8d72984b7aa296095ed177))
* **contribution:** rewrite fee cycles to payment emails in unified modal ([#921](https://github.com/ESA-Blueshell/website/issues/921)) ([f6ecfcc](https://github.com/ESA-Blueshell/website/commit/f6ecfccfeff4161338cc53d797ef47318caa527a))
* development reaches the api at the page's own origin ([#1008](https://github.com/ESA-Blueshell/website/issues/1008)) ([2faf37c](https://github.com/ESA-Blueshell/website/commit/2faf37c9f7dfdf5c92a8b570df41f476f7813999))
* **email:** let the board read the outbox and send a failed email again ([#618](https://github.com/ESA-Blueshell/website/issues/618)) ([449b9d4](https://github.com/ESA-Blueshell/website/commit/449b9d4fda66aaeb8a19b41b75f70ec4a29eb379))
* **email:** read a sent email back, with its urls stripped out ([#617](https://github.com/ESA-Blueshell/website/issues/617)) ([b4a0ca8](https://github.com/ESA-Blueshell/website/commit/b4a0ca802a41ad90bafb4321a97e84f3882d3ec1))
* **esports:** add a season from the end of the timeline ([#763](https://github.com/ESA-Blueshell/website/issues/763)) ([1fe24e0](https://github.com/ESA-Blueshell/website/commit/1fe24e02a8c4bcb5df09b0d71ca8a7ae3807aa0f))
* **esports:** add a team from two panes on a game's page ([#880](https://github.com/ESA-Blueshell/website/issues/880)) ([df401e8](https://github.com/ESA-Blueshell/website/commit/df401e83a00bcf5f95b6daa54a912190214f31ac))
* **esports:** add a team to the season on show ([#764](https://github.com/ESA-Blueshell/website/issues/764)) ([e8d70ea](https://github.com/ESA-Blueshell/website/commit/e8d70eab1857a991151631633ffd76b90b5fbc19))
* **esports:** add and remove a game in a season, from two panes on the index ([#879](https://github.com/ESA-Blueshell/website/issues/879)) ([2b9ef01](https://github.com/ESA-Blueshell/website/commit/2b9ef01b8ca94782ceb1bde327688a642fcf090e))
* **esports:** add, change and remove a game through the pages ([#830](https://github.com/ESA-Blueshell/website/issues/830)) ([d735d6a](https://github.com/ESA-Blueshell/website/commit/d735d6a7d7c6674cf98d74cf22bfae5fcbc735c0))
* **esports:** add, change and remove a game through the pages ([#846](https://github.com/ESA-Blueshell/website/issues/846)) ([d09b1ce](https://github.com/ESA-Blueshell/website/commit/d09b1ce900fd9020da4841b211806f012a98d774))
* **esports:** address public files by content and convert uploads on the way in ([#847](https://github.com/ESA-Blueshell/website/issues/847)) ([6e44ab3](https://github.com/ESA-Blueshell/website/commit/6e44ab3d6a50a39ac93191a23179e13576a4c39b))
* **esports:** change a line-up in a modal rather than in a slice ([#876](https://github.com/ESA-Blueshell/website/issues/876)) ([4b601ca](https://github.com/ESA-Blueshell/website/commit/4b601ca35041670c1e42afb3e18495536d574f6d))
* **esports:** change a line-up in the band rather than over it ([#789](https://github.com/ESA-Blueshell/website/issues/789)) ([d616cac](https://github.com/ESA-Blueshell/website/commit/d616cac5f1de0644a06b0a97dbd0457dfbff918a))
* **esports:** change a season from the page it is shown on ([#762](https://github.com/ESA-Blueshell/website/issues/762)) ([9aa2d14](https://github.com/ESA-Blueshell/website/commit/9aa2d14109d08e261f8228a579e530df62e32eb0))
* **esports:** change a team's line-up from the slice that shows it ([#766](https://github.com/ESA-Blueshell/website/issues/766)) ([15fab0f](https://github.com/ESA-Blueshell/website/commit/15fab0fd887747dad99a762d89cba9a52103fbaf))
* **esports:** change a team's own name, banner and recorded names in place ([#769](https://github.com/ESA-Blueshell/website/issues/769)) ([8b645fe](https://github.com/ESA-Blueshell/website/commit/8b645fe9f23d4c0c38daefb45358a98311496397))
* **esports:** derive which games we currently play from the seasons ([#877](https://github.com/ESA-Blueshell/website/issues/877)) ([8241341](https://github.com/ESA-Blueshell/website/commit/82413413ea8808c2fb7325b5e44d91e4720485b4))
* **esports:** field a team in another season with the line-up it last had ([#761](https://github.com/ESA-Blueshell/website/issues/761)) ([4d39565](https://github.com/ESA-Blueshell/website/commit/4d395655eccb5bdfb36538516a4236f02ea02d34))
* **esports:** give a game a record of its own ([#795](https://github.com/ESA-Blueshell/website/issues/795)) ([cde60e5](https://github.com/ESA-Blueshell/website/commit/cde60e56738431de293b7647241d71a03be65b91))
* **esports:** let a member decide whether their name is shown on the team pages ([#648](https://github.com/ESA-Blueshell/website/issues/648)) ([7754e9b](https://github.com/ESA-Blueshell/website/commit/7754e9b3cad84af3a43057b2ca686cd54bc050eb))
* **esports:** let a roster say what somebody did and a word about them ([#765](https://github.com/ESA-Blueshell/website/issues/765)) ([e2bff4b](https://github.com/ESA-Blueshell/website/commit/e2bff4bb08b3d0366ffcdafcb6992159bcd0310f))
* **esports:** let the index add a game rather than a team ([#786](https://github.com/ESA-Blueshell/website/issues/786)) ([0b95b74](https://github.com/ESA-Blueshell/website/commit/0b95b741a2d7889b9286567297ff07c8a053732c))
* **esports:** make a game's page a record rather than a component ([#758](https://github.com/ESA-Blueshell/website/issues/758)) ([5eb15e7](https://github.com/ESA-Blueshell/website/commit/5eb15e7462385ce73b2b37114c9b0c5e2a0e8779))
* **esports:** make teams, seasons and rosters data the association owns ([#643](https://github.com/ESA-Blueshell/website/issues/643)) ([c6c819e](https://github.com/ESA-Blueshell/website/commit/c6c819e6fda1e7c85fc2d20362b77d3153176a32))
* **esports:** make the recovered history reviewable data rather than inline SQL ([#675](https://github.com/ESA-Blueshell/website/issues/675)) ([61e4ad6](https://github.com/ESA-Blueshell/website/commit/61e4ad6df3a4a1074c6e32956da2280e0b90cc69))
* **esports:** one way to add a game or team, and lighter banners ([#881](https://github.com/ESA-Blueshell/website/issues/881)) ([c235cdc](https://github.com/ESA-Blueshell/website/commit/c235cdc8be5411edf2390f18e53823ff40e83e7d))
* **esports:** plain English in the dialogs, and the home page reads the records ([#903](https://github.com/ESA-Blueshell/website/issues/903)) ([bb2f23e](https://github.com/ESA-Blueshell/website/commit/bb2f23ee055f19f5fe99862b615c011f46341fea))
* **esports:** record that a team is fielded in a season ([#757](https://github.com/ESA-Blueshell/website/issues/757)) ([3df182b](https://github.com/ESA-Blueshell/website/commit/3df182bda7cf3102811b34b6096c3fb14934371c))
* **esports:** recut the shipped art at 1440p ([#897](https://github.com/ESA-Blueshell/website/issues/897)) ([18b2dca](https://github.com/ESA-Blueshell/website/commit/18b2dcaba0e3d111fffcc5db6c475a78d7c49ce7))
* **esports:** ship the association's own art and put it on the records ([#849](https://github.com/ESA-Blueshell/website/issues/849)) ([36ae5ee](https://github.com/ESA-Blueshell/website/commit/36ae5ee7702d4645328a683684ec83c1f792d7d9))
* **esports:** store pictures at several widths, upload them through one endpoint ([#848](https://github.com/ESA-Blueshell/website/issues/848)) ([cae175b](https://github.com/ESA-Blueshell/website/commit/cae175bf3e47df19ddb5043c63075ee3ad5c4116))
* **esports:** take a season, a fielding or a player away from where it is shown ([#768](https://github.com/ESA-Blueshell/website/issues/768)) ([e2de860](https://github.com/ESA-Blueshell/website/commit/e2de8601db0c4f01dad64f41fe7a2d1c0c1afd8a))
* **esports:** the team pool is shared across games, the line-up is not ([#875](https://github.com/ESA-Blueshell/website/issues/875)) ([2f11691](https://github.com/ESA-Blueshell/website/commit/2f116915bd6d203c897de04eb407438f9a749406))
* **esports:** upload a game's icon and give a team one of its own ([#856](https://github.com/ESA-Blueshell/website/issues/856)) ([f82729d](https://github.com/ESA-Blueshell/website/commit/f82729d74011f2a8117736b420371086a9855bf1))
* **esports:** upload posters, banners and player icons ([#797](https://github.com/ESA-Blueshell/website/issues/797)) ([01301fb](https://github.com/ESA-Blueshell/website/commit/01301fbecf71c29433575ac9144c70ebea2ebe89))
* **island:** a phone travels between stops by swiping ([#996](https://github.com/ESA-Blueshell/website/issues/996)) ([6b9574e](https://github.com/ESA-Blueshell/website/commit/6b9574e1088cad7a339997ba2b7e30228e467399))
* **members:** end and start memberships in bulk ([#906](https://github.com/ESA-Blueshell/website/issues/906)) ([9b0f4e3](https://github.com/ESA-Blueshell/website/commit/9b0f4e3ff4df8d42318251dfb83d0cb09602cd5a)), closes [#816](https://github.com/ESA-Blueshell/website/issues/816)
* **ui:** one labelled box, openable, instead of three spellings of it ([#621](https://github.com/ESA-Blueshell/website/issues/621)) ([230d12f](https://github.com/ESA-Blueshell/website/commit/230d12f956f7e3c8867fcc0d9bf7d1fa93f1d15f))
* **user-manager:** move every user action into the table header row ([#603](https://github.com/ESA-Blueshell/website/issues/603)) ([88823a9](https://github.com/ESA-Blueshell/website/commit/88823a983c28ea035137c804dd5b6f2ed94aaec7))


### Bug Fixes

* **api:** remove unnecessary non-null assertions and fix deprecated enum usage ([#868](https://github.com/ESA-Blueshell/website/issues/868)) ([a164cb9](https://github.com/ESA-Blueshell/website/commit/a164cb942861b18a9e7e09f187e92bb9bdfca1c3))
* **board:** a seat's photograph sits opposite the one before it ([#919](https://github.com/ESA-Blueshell/website/issues/919)) ([f070beb](https://github.com/ESA-Blueshell/website/commit/f070beb6c11ae090a7f5e4b589b19e91ae7811e1))
* **board:** the seed counts what it wrote rather than tallying as it goes ([#954](https://github.com/ESA-Blueshell/website/issues/954)) ([d886e23](https://github.com/ESA-Blueshell/website/commit/d886e23df120845ba47d2a45b931b0be02d32e5e))
* **ci:** pre-create blueshell schema in H2 INIT for openapi-sync ([#874](https://github.com/ESA-Blueshell/website/issues/874)) ([9aa7fdc](https://github.com/ESA-Blueshell/website/commit/9aa7fdc71d5c7193d5ba9e715e4e5454a4a17815)), closes [#871](https://github.com/ESA-Blueshell/website/issues/871) [#868](https://github.com/ESA-Blueshell/website/issues/868)
* **cohorts:** keep the subject page up when a cohort names a system that is gone ([#655](https://github.com/ESA-Blueshell/website/issues/655)) ([2539432](https://github.com/ESA-Blueshell/website/commit/2539432c817bbbe90a81edbf790183c4fb31c87e))
* **cohorts:** keep the sync targets table, and its menus, on a phone ([#644](https://github.com/ESA-Blueshell/website/issues/644)) ([4fff343](https://github.com/ESA-Blueshell/website/commit/4fff343e596ad5c61581235a7c90777c953661b0))
* **cohorts:** type the member-name helper with the member it receives ([#630](https://github.com/ESA-Blueshell/website/issues/630)) ([338fde1](https://github.com/ESA-Blueshell/website/commit/338fde1e7832b3b182629415e7464d8ccdf13965))
* **contribution:** pin the reminder email's money locale to nl-NL ([#734](https://github.com/ESA-Blueshell/website/issues/734)) ([47b48e3](https://github.com/ESA-Blueshell/website/commit/47b48e3374ed5c38ff00474689fcbad68ed7d91a))
* **contribution:** the payment window is counted from the date they joined ([#1029](https://github.com/ESA-Blueshell/website/issues/1029)) ([74bb15f](https://github.com/ESA-Blueshell/website/commit/74bb15faf8becbff78d1e517eef742e0506659ee))
* **esports:** a narrow slice is tall, and the picture is fetched for the taller side ([#916](https://github.com/ESA-Blueshell/website/issues/916)) ([08cafc4](https://github.com/ESA-Blueshell/website/commit/08cafc4d3c5c93ae633d157088b80f1273c40ecb))
* **esports:** a season is named for the year it happens in ([#914](https://github.com/ESA-Blueshell/website/issues/914)) ([fd428a5](https://github.com/ESA-Blueshell/website/commit/fd428a5780fe05ef004f7ad77c3bad21fbd664fb))
* **esports:** hold the band's height only while a season is travelling ([#854](https://github.com/ESA-Blueshell/website/issues/854)) ([dfb7f4b](https://github.com/ESA-Blueshell/website/commit/dfb7f4b861b7adb215e9cee0c85dfc1bedede8ac))
* **esports:** hold the reader's place when they choose a season ([#858](https://github.com/ESA-Blueshell/website/issues/858)) ([#859](https://github.com/ESA-Blueshell/website/issues/859)) ([8cce86b](https://github.com/ESA-Blueshell/website/commit/8cce86bac698d7a1a17d93e88b2de8bad7c4bbbe))
* **esports:** let the header flow, affordances leave and seasons follow games ([#834](https://github.com/ESA-Blueshell/website/issues/834)) ([cd5935e](https://github.com/ESA-Blueshell/website/commit/cd5935e9f0228e7ab869759c56c9054f21fbc59b))
* **esports:** resolve uploaded image urls against the api ([#829](https://github.com/ESA-Blueshell/website/issues/829)) ([d5e8209](https://github.com/ESA-Blueshell/website/commit/d5e8209e4703c32d8503f8560f2c88ba18cc7d81))
* **esports:** show the most recent seasons games on esports index, and move between seasons with animations ([#851](https://github.com/ESA-Blueshell/website/issues/851)) ([13ee777](https://github.com/ESA-Blueshell/website/commit/13ee777cb35ded60575dab05a45e90b832852380))
* **event:** map event_sign_up_answers once, as the link table it is ([#740](https://github.com/ESA-Blueshell/website/issues/740)) ([e1a5841](https://github.com/ESA-Blueshell/website/commit/e1a584127ff234ab8c3705ee24affbc15e8f5761))
* **frontend:** a committee saves while its user list is still loading ([#1045](https://github.com/ESA-Blueshell/website/issues/1045)) ([6c82c5b](https://github.com/ESA-Blueshell/website/commit/6c82c5b6cc6c5c1736b322dde80e938e4da9db3a)), closes [#1042](https://github.com/ESA-Blueshell/website/issues/1042)
* **frontend:** a name is set in a font that has its letters ([#948](https://github.com/ESA-Blueshell/website/issues/948)) ([c7729a0](https://github.com/ESA-Blueshell/website/commit/c7729a0e95e561efb7143e26dccf5a1e783dbdf9))
* **frontend:** a read that failed is not reported as an emptiness ([#1017](https://github.com/ESA-Blueshell/website/issues/1017)) ([c964515](https://github.com/ESA-Blueshell/website/commit/c964515eeee618e038eea0dae6b843f899328def))
* **frontend:** a refused write is not reported as one that landed ([#1019](https://github.com/ESA-Blueshell/website/issues/1019)) ([16f65ff](https://github.com/ESA-Blueshell/website/commit/16f65ffd74e69a9fc98a5140e8d96d876542d46d))
* **frontend:** a switched row keeps the date the member was last written to ([#1032](https://github.com/ESA-Blueshell/website/issues/1032)) ([6d7efb9](https://github.com/ESA-Blueshell/website/commit/6d7efb90532d074815047cc98d7dba05ef415381))
* **frontend:** keep the dev container's own dependencies ([#785](https://github.com/ESA-Blueshell/website/issues/785)) ([e82bae9](https://github.com/ESA-Blueshell/website/commit/e82bae930a95fd919654b29543bf5af54851f12e))
* **frontend:** the dev container installs against the lockfile it mounts ([#902](https://github.com/ESA-Blueshell/website/issues/902)) ([e2a9b20](https://github.com/ESA-Blueshell/website/commit/e2a9b203be738c5bcef8e6214d2deef2ad0d928e))
* **frontend:** the esports island keeps its tailwind to itself ([#920](https://github.com/ESA-Blueshell/website/issues/920)) ([b70329c](https://github.com/ESA-Blueshell/website/commit/b70329ca455560cd8ea38f92270cfca8fc6a6594))
* **management:** give the management card a visible edge ([#602](https://github.com/ESA-Blueshell/website/issues/602)) ([1075b6d](https://github.com/ESA-Blueshell/website/commit/1075b6dcf208ea9255265856611c406739ccf3de))
* **members:** the paid dialogs read the spell the fee is charged on, and the review follow-ups ([#910](https://github.com/ESA-Blueshell/website/issues/910)) ([bcccd4d](https://github.com/ESA-Blueshell/website/commit/bcccd4d198741c231d626e01e6b7b63701447ab5))
* **security:** close the code-scanning follow-ups from the boundary work ([#796](https://github.com/ESA-Blueshell/website/issues/796)) ([24e3b76](https://github.com/ESA-Blueshell/website/commit/24e3b7668abd205010def88d9d96a2502568d208))
* **signup:** a refused step says so, and a second tab does not strand the first ([#1007](https://github.com/ESA-Blueshell/website/issues/1007)) ([1077201](https://github.com/ESA-Blueshell/website/commit/10772011c0aa88cbddac4214017859e8c6a3477c))
* **signup:** a signup that lost its tab picks itself up, and a refusal says why ([#1023](https://github.com/ESA-Blueshell/website/issues/1023)) ([99fb516](https://github.com/ESA-Blueshell/website/commit/99fb516cea38f9dfa5e88286b4e54f6e8b3c5e21)), closes [#1022](https://github.com/ESA-Blueshell/website/issues/1022)
* **system-tests:** point the banner fixture at the frontend's new relative depth ([#743](https://github.com/ESA-Blueshell/website/issues/743)) ([f20ea4b](https://github.com/ESA-Blueshell/website/commit/f20ea4b1639ecb3b645666e092d5246fa96bdaa1))
* **user-manager:** keep the table searchable after the clear button is used ([#607](https://github.com/ESA-Blueshell/website/issues/607)) ([c9b809d](https://github.com/ESA-Blueshell/website/commit/c9b809d028181d0aaa1fbf837248dfd44307b2a7))
* **user-manager:** sort only when a column is chosen, and define the order ([#609](https://github.com/ESA-Blueshell/website/issues/609)) ([6b557ed](https://github.com/ESA-Blueshell/website/commit/6b557edcfebcc1c388f170478ae861cd9aaa1a92))
* **user-manager:** tint the table surfaces with the theme, not with black ([#605](https://github.com/ESA-Blueshell/website/issues/605)) ([c076cbb](https://github.com/ESA-Blueshell/website/commit/c076cbbb504e127771974c9053eb22b1a4bd5768))


### Performance

* **user-manager:** give each member row its own component ([#610](https://github.com/ESA-Blueshell/website/issues/610)) ([6197b92](https://github.com/ESA-Blueshell/website/commit/6197b92b353306b8fd716301e94ca475718bb1b4))
* **user-manager:** render only the member rows that are on screen ([#615](https://github.com/ESA-Blueshell/website/issues/615)) ([d785134](https://github.com/ESA-Blueshell/website/commit/d78513488b557e072a25ad515cebf32f39260dea))


### Refactoring

* **api:** delete the command bus ([#678](https://github.com/ESA-Blueshell/website/issues/678)) ([f70a86d](https://github.com/ESA-Blueshell/website/commit/f70a86d603fbbfbb62f2db57106a23d61584e8cc))
* **api:** delete the survey command layer nothing reaches ([#650](https://github.com/ESA-Blueshell/website/issues/650)) ([516d230](https://github.com/ESA-Blueshell/website/commit/516d2301bdd08a803757a8a2e2ad6f98935b8cf6))
* **api:** drop the command-to-job bridge nothing calls ([#627](https://github.com/ESA-Blueshell/website/issues/627)) ([3ab407a](https://github.com/ESA-Blueshell/website/commit/3ab407a415ab7252ab0e7afeba0a66a5fdb88a6a))
* **api:** flatten the package topology ([#787](https://github.com/ESA-Blueshell/website/issues/787)) ([76044af](https://github.com/ESA-Blueshell/website/commit/76044afdd0ec05761a29660e83406a0a5a826f46))
* **api:** move permission evaluators to the module they govern ([#633](https://github.com/ESA-Blueshell/website/issues/633)) ([c62cfdf](https://github.com/ESA-Blueshell/website/commit/c62cfdf4ff883ae9a9b5bf8a1949ad655846b0f7))
* **api:** move single-consumer code out of the shared kernel ([#628](https://github.com/ESA-Blueshell/website/issues/628)) ([2b6e515](https://github.com/ESA-Blueshell/website/commit/2b6e5157b4c6050964517e0ec31a45c19b3301c0))
* **api:** name the twenty application modules Modulith could not see ([#742](https://github.com/ESA-Blueshell/website/issues/742)) ([d80d984](https://github.com/ESA-Blueshell/website/commit/d80d984891c563c9138ff32e3d382ab359c13fcf))
* **api:** one seed reader and one picture resolver, out of the esports domain ([#943](https://github.com/ESA-Blueshell/website/issues/943)) ([528ff8f](https://github.com/ESA-Blueshell/website/commit/528ff8f3311c7980c9e0f83c339ccee1082f8bf8))
* **api:** publish the api and entities named interfaces and the module whitelists ([#771](https://github.com/ESA-Blueshell/website/issues/771)) ([269dfad](https://github.com/ESA-Blueshell/website/commit/269dfadff66730a8198922a4cf887e1cc849fb2f))
* **api:** take address and member profile off the command bus ([#653](https://github.com/ESA-Blueshell/website/issues/653)) ([d47326e](https://github.com/ESA-Blueshell/website/commit/d47326e8ada6eb92da0c9079bb5854eeb0d9540a))
* **api:** take auth off the command bus ([#652](https://github.com/ESA-Blueshell/website/issues/652)) ([bc8bc58](https://github.com/ESA-Blueshell/website/commit/bc8bc586c2dbc076e3d303cd9500ea60aa54bc27))
* **api:** take blog, sponsor, telemetry and files off the command bus ([#637](https://github.com/ESA-Blueshell/website/issues/637)) ([4e41b69](https://github.com/ESA-Blueshell/website/commit/4e41b6994bfe6c4e7749a2295e1b0cd5fac1539a))
* **api:** take board and committee off the command bus ([#645](https://github.com/ESA-Blueshell/website/issues/645)) ([8c7269f](https://github.com/ESA-Blueshell/website/commit/8c7269f28f81a5d4d67995d24aba1c9365ca35dc))
* **api:** take contribution off the command bus ([#646](https://github.com/ESA-Blueshell/website/issues/646)) ([f6d47a7](https://github.com/ESA-Blueshell/website/commit/f6d47a7e2cbdd50769f77276500575444751d1a4))
* **api:** take esports off the command bus ([#676](https://github.com/ESA-Blueshell/website/issues/676)) ([7d38e10](https://github.com/ESA-Blueshell/website/commit/7d38e103817144e1c2eb56e97d90b30f5188c39f))
* **api:** take event off the command bus ([#649](https://github.com/ESA-Blueshell/website/issues/649)) ([c34be53](https://github.com/ESA-Blueshell/website/commit/c34be532d7c7e7abaf10ff0ee50402656b495207))
* **api:** take memberships off the command bus ([#656](https://github.com/ESA-Blueshell/website/issues/656)) ([942fb63](https://github.com/ESA-Blueshell/website/commit/942fb630a68e9c958a3945964d3d27c051e9af04))
* **api:** take users and signup off the command bus ([#671](https://github.com/ESA-Blueshell/website/issues/671)) ([5f4e544](https://github.com/ESA-Blueshell/website/commit/5f4e544a23d835c3e09b94ee43bb5492521f7dc7))
* **blog:** drop the exception nothing raises ([#1044](https://github.com/ESA-Blueshell/website/issues/1044)) ([d29b8fc](https://github.com/ESA-Blueshell/website/commit/d29b8fc55b50749d6221045390d96b967fab212e)), closes [#1043](https://github.com/ESA-Blueshell/website/issues/1043)
* **board:** a board has members, and the shared bands lose their esports names ([#982](https://github.com/ESA-Blueshell/website/issues/982)) ([d1bb5ac](https://github.com/ESA-Blueshell/website/commit/d1bb5ac60d1851d2d91c9d3d0694844686e8b0a4))
* **board:** a board is edited in one place, not two ([#969](https://github.com/ESA-Blueshell/website/issues/969)) ([1f1159f](https://github.com/ESA-Blueshell/website/commit/1f1159fdd015704c819fb4c2df1606a8f0940eea))
* **cohort:** file the cohort job definitions with cohort ([#713](https://github.com/ESA-Blueshell/website/issues/713)) ([0b50fef](https://github.com/ESA-Blueshell/website/commit/0b50fef95b5eaa63bf3709819fe9f8727468bef4)), closes [#702](https://github.com/ESA-Blueshell/website/issues/702)
* **cohorts:** fold drift into the members table ([#629](https://github.com/ESA-Blueshell/website/issues/629)) ([e644bc5](https://github.com/ESA-Blueshell/website/commit/e644bc53f9b3f6d82bfb6c993b8a7e609aec627e))
* **cohorts:** give both cohort pages the table the other managers have ([#620](https://github.com/ESA-Blueshell/website/issues/620)) ([4c86e0a](https://github.com/ESA-Blueshell/website/commit/4c86e0ac9970009f5c3b9b44305385ddaf910290))
* **cohorts:** one card, three named boxes, on the cohort page ([#622](https://github.com/ESA-Blueshell/website/issues/622)) ([#624](https://github.com/ESA-Blueshell/website/issues/624)) ([c3bc1a0](https://github.com/ESA-Blueshell/website/commit/c3bc1a0b4510f5e1f5763a09c6f6d94e3143bc37))
* **cohorts:** put the subject detail page on ManagerCard ([#601](https://github.com/ESA-Blueshell/website/issues/601)) ([c588c80](https://github.com/ESA-Blueshell/website/commit/c588c808995bd28037299cdf57554e284f763af2))
* **cohorts:** rebuild the dashboard and group cohorts by what they are ([#597](https://github.com/ESA-Blueshell/website/issues/597)) ([#599](https://github.com/ESA-Blueshell/website/issues/599)) ([afd66ff](https://github.com/ESA-Blueshell/website/commit/afd66ff5126be5194a59cafdf6213c5f130a87b8))
* **cohorts:** state each cohort in code instead of matching facts ([#651](https://github.com/ESA-Blueshell/website/issues/651)) ([a4f041b](https://github.com/ESA-Blueshell/website/commit/a4f041b3af3ac30c7fd17ca0e9f177daa73a123d))
* **comments:** a test doc states the rule it pins ([#1038](https://github.com/ESA-Blueshell/website/issues/1038)) ([1b3f975](https://github.com/ESA-Blueshell/website/commit/1b3f9758d4c3d5d978328658049f866449705a7d))
* **comments:** the api says the constraint and stops ([#1037](https://github.com/ESA-Blueshell/website/issues/1037)) ([e111ad0](https://github.com/ESA-Blueshell/website/commit/e111ad0341dc40edf045b5597c85c879a01fa68a))
* **comments:** the frontend's prose says the rule and stops ([#1039](https://github.com/ESA-Blueshell/website/issues/1039)) ([4b43434](https://github.com/ESA-Blueshell/website/commit/4b43434b4256e04cc9d5aba91aafe593f9eae741))
* **comments:** the system tests say what they prove, once ([#1040](https://github.com/ESA-Blueshell/website/issues/1040)) ([837daf0](https://github.com/ESA-Blueshell/website/commit/837daf080a8f95754ecbd6a064fc66e097891e04))
* **committee:** keep seat bookkeeping in the module that owns seats ([#712](https://github.com/ESA-Blueshell/website/issues/712)) ([73e1e4f](https://github.com/ESA-Blueshell/website/commit/73e1e4f3d2b075df9b356bd833b8e475bbeedc19)), closes [#700](https://github.com/ESA-Blueshell/website/issues/700)
* **email:** let each domain compose the email it wants sent ([#728](https://github.com/ESA-Blueshell/website/issues/728)) ([2880c86](https://github.com/ESA-Blueshell/website/commit/2880c865642199caa95d46bdfec2f963d1c1b30a)), closes [#703](https://github.com/ESA-Blueshell/website/issues/703)
* **esports:** a game is a game, not a game page ([#905](https://github.com/ESA-Blueshell/website/issues/905)) ([9b85848](https://github.com/ESA-Blueshell/website/commit/9b85848ca11418c59c337bfe598b255c30d847e7))
* **esports:** hang a line-up off the fielding rather than a team and a season ([#869](https://github.com/ESA-Blueshell/website/issues/869)) ([d3c183e](https://github.com/ESA-Blueshell/website/commit/d3c183e470200de271e49f25599d97dc2868f1f8))
* **esports:** retire the esports manager ([#770](https://github.com/ESA-Blueshell/website/issues/770)) ([18f3047](https://github.com/ESA-Blueshell/website/commit/18f3047c1000abffcee87fb4c1c1129ba92d70d5))
* **esports:** the seed files are the only record of the recovered history ([#870](https://github.com/ESA-Blueshell/website/issues/870)) ([afbfa99](https://github.com/ESA-Blueshell/website/commit/afbfa99afb2afdc67a6e2a040666122329a43fec))
* **file:** resolve an event's banner through the event module ([#709](https://github.com/ESA-Blueshell/website/issues/709)) ([b47eaee](https://github.com/ESA-Blueshell/website/commit/b47eaee165d59b3f334c20edfed3ca9f5866e711)), closes [#697](https://github.com/ESA-Blueshell/website/issues/697)
* **frontend:** the island belongs to the site, not to esports ([#942](https://github.com/ESA-Blueshell/website/issues/942)) ([979d38d](https://github.com/ESA-Blueshell/website/commit/979d38dc6ec7767fffa50a3ed6c0df17202b2d2d))
* **frontend:** the member list belongs to the user domain ([#938](https://github.com/ESA-Blueshell/website/issues/938)) ([8aba7a9](https://github.com/ESA-Blueshell/website/commit/8aba7a9ca91cca0a9036d8db4038909b700315df))
* **frontend:** the picture helpers belong to the island, not to esports ([#945](https://github.com/ESA-Blueshell/website/issues/945)) ([e13d7ab](https://github.com/ESA-Blueshell/website/commit/e13d7abc15b7a7eddca7bec78b6a73cf48e949e4))
* **job:** name the deferred-execution verb runAsync ([#738](https://github.com/ESA-Blueshell/website/issues/738)) ([a859c19](https://github.com/ESA-Blueshell/website/commit/a859c195ead3a4de67256c2b28352f9042338a5e))
* **jobs:** let each module describe its own job subjects ([#726](https://github.com/ESA-Blueshell/website/issues/726)) ([d416284](https://github.com/ESA-Blueshell/website/commit/d416284d50134cf9665d45fed203cabe9854d9b4)), closes [#702](https://github.com/ESA-Blueshell/website/issues/702)
* **survey:** drop the eager back-reference into event sign-ups ([#708](https://github.com/ESA-Blueshell/website/issues/708)) ([cdca0ac](https://github.com/ESA-Blueshell/website/commit/cdca0acd5cdf7ae54c50b2bb104fc55b3187cf2e)), closes [#696](https://github.com/ESA-Blueshell/website/issues/696)
* **sync:** file the contact-sync jobs with the module that syncs ([#711](https://github.com/ESA-Blueshell/website/issues/711)) ([c679a34](https://github.com/ESA-Blueshell/website/commit/c679a342750e12c0ad33e143c2ec338b21ec0558)), closes [#699](https://github.com/ESA-Blueshell/website/issues/699)
* **user-manager:** define each filter in one place instead of a row predicate ([#608](https://github.com/ESA-Blueshell/website/issues/608)) ([#614](https://github.com/ESA-Blueshell/website/issues/614)) ([96f732a](https://github.com/ESA-Blueshell/website/commit/96f732aafe609aa738d1cd7b2965cc495f707d81))
* **user-manager:** finish renaming the page to the User Manager ([#606](https://github.com/ESA-Blueshell/website/issues/606)) ([18ef1e0](https://github.com/ESA-Blueshell/website/commit/18ef1e05befc10bdeba99958305211e4fba0047d))
* **user:** delete the dead Event specification in the user module ([#710](https://github.com/ESA-Blueshell/website/issues/710)) ([ceb8d73](https://github.com/ESA-Blueshell/website/commit/ceb8d736d0f7a7b7cf4112a0cf35a2140cbffc90)), closes [#701](https://github.com/ESA-Blueshell/website/issues/701)
* **user:** drop the committee-member back-reference nothing reads ([#732](https://github.com/ESA-Blueshell/website/issues/732)) ([320e341](https://github.com/ESA-Blueshell/website/commit/320e341467f8dd38e9186e925ccbb7a056f6245f))
* **user:** drop the profile-picture association nothing reads ([#706](https://github.com/ESA-Blueshell/website/issues/706)) ([08d4644](https://github.com/ESA-Blueshell/website/commit/08d46448b3210686c64dacae3ddd58d1a3c76f3f)), closes [#695](https://github.com/ESA-Blueshell/website/issues/695)
* **user:** drop three cross-module collections nothing reads ([#729](https://github.com/ESA-Blueshell/website/issues/729)) ([6af3985](https://github.com/ESA-Blueshell/website/commit/6af39851a441f292055f6f41dcfabfc2a4f81b41)), closes [#698](https://github.com/ESA-Blueshell/website/issues/698)
* **user:** reach signup completion through a port the user module owns ([#727](https://github.com/ESA-Blueshell/website/issues/727)) ([6fcd3ac](https://github.com/ESA-Blueshell/website/commit/6fcd3ac04bb975f2f83cb80eae1b586ca7a7ccb7)), closes [#704](https://github.com/ESA-Blueshell/website/issues/704)


### Documentation

* **adr-025:** the decision says what the tab holding the form does ([#1030](https://github.com/ESA-Blueshell/website/issues/1030)) ([53c23b2](https://github.com/ESA-Blueshell/website/commit/53c23b20d8cb6dc0886187bc37c7ae6d53af0869))
* **adr:** correct ADR-005 — the user unique constraints already exist ([#733](https://github.com/ESA-Blueshell/website/issues/733)) ([c3a3a14](https://github.com/ESA-Blueshell/website/commit/c3a3a1468001a0e5cbbdea0e98cda12204a1c683))
* **adr:** correct three measured figures ([#625](https://github.com/ESA-Blueshell/website/issues/625)) ([406c19f](https://github.com/ESA-Blueshell/website/commit/406c19fb505ef0908d732283a9e01526e2f74be0))
* **adr:** settle ADR-005's validator placement against the code ([#735](https://github.com/ESA-Blueshell/website/issues/735)) ([a937ac4](https://github.com/ESA-Blueshell/website/commit/a937ac4419e08bb2084f12923e8b1b545a08b57b))
* **agents:** what a comment in this repo is for ([#1036](https://github.com/ESA-Blueshell/website/issues/1036)) ([f1a888e](https://github.com/ESA-Blueshell/website/commit/f1a888e69df9fcb95e2246821db322887b4960d8))
* **architecture:** correct what Modulith already provides ([#636](https://github.com/ESA-Blueshell/website/issues/636)) ([b0d1e43](https://github.com/ESA-Blueshell/website/commit/b0d1e438ced0e201c36ed4f307fc7475017a0272))
* **architecture:** measure the claims the migration records rest on ([#692](https://github.com/ESA-Blueshell/website/issues/692)) ([df9470d](https://github.com/ESA-Blueshell/website/commit/df9470d380bdc5b79a39cb51bb8a85446a9903e9)), closes [#691](https://github.com/ESA-Blueshell/website/issues/691)
* **architecture:** move authorization next to the aggregate it governs ([#631](https://github.com/ESA-Blueshell/website/issues/631)) ([95427d9](https://github.com/ESA-Blueshell/website/commit/95427d9ab167e18586987932d47934e6b414cf4c))
* **architecture:** record modules over layers, and where a file goes ([#623](https://github.com/ESA-Blueshell/website/issues/623)) ([3d01974](https://github.com/ESA-Blueshell/website/commit/3d019749266f7b7142f79f0f56f190f7b01c8f21))
* **board:** the words a board is described in ([#937](https://github.com/ESA-Blueshell/website/issues/937)) ([31825de](https://github.com/ESA-Blueshell/website/commit/31825de4c45a106ea7ddf6995e4850e3d9fa26bc))
* **testing:** record the test pyramid and the coverage gates it carries ([#619](https://github.com/ESA-Blueshell/website/issues/619)) ([c7ce344](https://github.com/ESA-Blueshell/website/commit/c7ce3448a9494231efe57fa293822c3d3311e322))


### Build and Dependencies

* **api:** the committed spec is block yaml, a line per value ([#960](https://github.com/ESA-Blueshell/website/issues/960)) ([2515fa0](https://github.com/ESA-Blueshell/website/commit/2515fa04a15528364fdc7531be149c5efd8f5e0b))
* **deps:** bump actions/setup-java from 5.7.0 to 6.0.0 ([#891](https://github.com/ESA-Blueshell/website/issues/891)) ([1a88217](https://github.com/ESA-Blueshell/website/commit/1a8821736cf5995266af96b4fecf9e77fc774dd4))
* **deps:** bump browserslist ([#961](https://github.com/ESA-Blueshell/website/issues/961)) ([56abde7](https://github.com/ESA-Blueshell/website/commit/56abde7460b3292d630f6377f7af8c655c7ee7bf))
* **deps:** bump dev.detekt:detekt-gradle-plugin ([#887](https://github.com/ESA-Blueshell/website/issues/887)) ([e4e29b1](https://github.com/ESA-Blueshell/website/commit/e4e29b143d23358466139bd4821dc7a79674395a))
* **deps:** bump dev.detekt:detekt-gradle-plugin in /build-logic ([#888](https://github.com/ESA-Blueshell/website/issues/888)) ([3b22324](https://github.com/ESA-Blueshell/website/commit/3b223242cc78d33e1b9530ee2f7a2934fc93856d))
* **deps:** bump eclipse-temurin in /services/api ([#883](https://github.com/ESA-Blueshell/website/issues/883)) ([650c6f3](https://github.com/ESA-Blueshell/website/commit/650c6f3209a5c93868525d1f1d57085bf15977cb))
* **deps:** bump nginxinc/nginx-unprivileged in /services/frontend ([#884](https://github.com/ESA-Blueshell/website/issues/884)) ([305ec20](https://github.com/ESA-Blueshell/website/commit/305ec20fa965a783ad6ae20cb75f13d65b166ec2))
* **deps:** bump node ([#882](https://github.com/ESA-Blueshell/website/issues/882)) ([f0d0d9e](https://github.com/ESA-Blueshell/website/commit/f0d0d9e437bf017754d41b36a6680acdb1431ff6))
* **deps:** bump org.apache.tika:tika-core from 3.3.2 to 4.0.0 ([#886](https://github.com/ESA-Blueshell/website/issues/886)) ([1b7d2e2](https://github.com/ESA-Blueshell/website/commit/1b7d2e2fcec6ff88984dfa866d6733177ef1cc36))
* **deps:** bump postcss-selector-parser ([#957](https://github.com/ESA-Blueshell/website/issues/957)) ([ef6c089](https://github.com/ESA-Blueshell/website/commit/ef6c089861eca4b2f064b217a1fcbecacc804d7d))
* **deps:** bump the actions group with 3 updates ([#890](https://github.com/ESA-Blueshell/website/issues/890)) ([e2f04dc](https://github.com/ESA-Blueshell/website/commit/e2f04dc4a382b7b764440312a84e2a33ef2790a4))
* **deps:** bump the frontend group ([#889](https://github.com/ESA-Blueshell/website/issues/889)) ([881a8a5](https://github.com/ESA-Blueshell/website/commit/881a8a585e9a5158e4f0bdd6d6506b31a347586b))
* **deps:** bump the gradle group across 2 directories with 6 updates ([#885](https://github.com/ESA-Blueshell/website/issues/885)) ([347d792](https://github.com/ESA-Blueshell/website/commit/347d7924bf355567bf4bc6ca47ec2ba5347c206b))
* relocate the generated clients and the system test suite ([#739](https://github.com/ESA-Blueshell/website/issues/739)) ([40c6c6d](https://github.com/ESA-Blueshell/website/commit/40c6c6d8dce9475eb40c8cad337c189a060cfa68))


### Styling

* **cohorts:** give a box a heading with its count on it, not a line under it ([#634](https://github.com/ESA-Blueshell/website/issues/634)) ([4c88174](https://github.com/ESA-Blueshell/website/commit/4c8817402262e7154c0937b4e24505c34954e474))
* **esports:** a shell texture behind the pages, and light mode ([#912](https://github.com/ESA-Blueshell/website/issues/912)) ([2a341cd](https://github.com/ESA-Blueshell/website/commit/2a341cda2a0ea10b5317799736b28e321d0565e5))
* **esports:** give the esports pages their own styling system ([#693](https://github.com/ESA-Blueshell/website/issues/693)) ([1879e68](https://github.com/ESA-Blueshell/website/commit/1879e68b366ec8176c3ac204a3a8192b52488f00))
* **esports:** make the ways in look like the things they add ([#774](https://github.com/ESA-Blueshell/website/issues/774)) ([43d5bbc](https://github.com/ESA-Blueshell/website/commit/43d5bbc706d4254c8cb1948b436db548f78e9f80))
* **esports:** tidy up the season strip and close the gaps between slices ([#828](https://github.com/ESA-Blueshell/website/issues/828)) ([54fc522](https://github.com/ESA-Blueshell/website/commit/54fc522cf7be6d2be8cd429eb449eda971202122))

## [1.6.0](https://github.com/ESA-Blueshell/website/compare/v1.5.0...v1.6.0) (2026-08-25)


### Features

* **cohorts:** let an existing folder be searched when creating a target ([#584](https://github.com/ESA-Blueshell/website/issues/584)) ([2448173](https://github.com/ESA-Blueshell/website/commit/2448173d7712e4336d081f89df7b707d512bcba1))
* **cohorts:** show every Brevo target and the folder it sits in ([#596](https://github.com/ESA-Blueshell/website/issues/596)) ([b792170](https://github.com/ESA-Blueshell/website/commit/b792170ea836210c4d3a544565dd52f775f40466))
* **user-manager:** select users and mark their contributions paid or unpaid ([#595](https://github.com/ESA-Blueshell/website/issues/595)) ([16075ff](https://github.com/ESA-Blueshell/website/commit/16075ff315bba3ca9472b5710743b53adee8d67e))


### Bug Fixes

* **cohorts:** label the target fields for what they hold ([#583](https://github.com/ESA-Blueshell/website/issues/583)) ([d2406af](https://github.com/ESA-Blueshell/website/commit/d2406af70fbeee2ad20aee36ec8a1e909bac1ddd))
* **cohorts:** make the target picker hand back an id, not the item it came from ([#582](https://github.com/ESA-Blueshell/website/issues/582)) ([c4d8c26](https://github.com/ESA-Blueshell/website/commit/c4d8c26a11215f428a42bdb11190c2442a759389))
* **recovery:** offer an activation email only where one applies ([#581](https://github.com/ESA-Blueshell/website/issues/581)) ([dd6d452](https://github.com/ESA-Blueshell/website/commit/dd6d452efd3e24fefd018b12d1abe80804c85d91))


### Refactoring

* **management:** give the shared card one definition ([#592](https://github.com/ESA-Blueshell/website/issues/592)) ([233dfd6](https://github.com/ESA-Blueshell/website/commit/233dfd65c6b2bf1feb6b36f35e1828d811cbe835))

## [1.5.0](https://github.com/ESA-Blueshell/website/compare/v1.4.0...v1.5.0) (2026-08-25)


### Features

* **api:** bulk mark contributions paid and unpaid ([#567](https://github.com/ESA-Blueshell/website/issues/567)) ([2a4f7f4](https://github.com/ESA-Blueshell/website/commit/2a4f7f433b696f9bc53155cc78bba0ed62f69cdf))
* **ci:** announce cut releases on Discord ([#574](https://github.com/ESA-Blueshell/website/issues/574)) ([891bc29](https://github.com/ESA-Blueshell/website/commit/891bc296aee6bb61726dd8ddf8c081b7d922392e))
* **dev:** seed the development database from named fixtures ([#578](https://github.com/ESA-Blueshell/website/issues/578)) ([5bce05f](https://github.com/ESA-Blueshell/website/commit/5bce05f2e13f5d217ddfcd76d3614b4950778e62))
* **email:** a shared base for rendering an email for inspection ([#576](https://github.com/ESA-Blueshell/website/issues/576)) ([a3f1114](https://github.com/ESA-Blueshell/website/commit/a3f1114dd9e789cae0fa1a055448071fb01a20cf))
* **modals:** make BaseModal a shell every dialog can actually use ([#577](https://github.com/ESA-Blueshell/website/issues/577)) ([0a65b0c](https://github.com/ESA-Blueshell/website/commit/0a65b0c92ad8bf631e4cc7fe4b69de0bf762f115))
* **recovery:** preview recovery emails ([#575](https://github.com/ESA-Blueshell/website/issues/575)) ([e21bdd4](https://github.com/ESA-Blueshell/website/commit/e21bdd4464de8015120565a395438c91afd7d9c7))


### Bug Fixes

* **api:** keep CSRF protection on the actuator filter chain ([#570](https://github.com/ESA-Blueshell/website/issues/570)) ([f16fdbc](https://github.com/ESA-Blueshell/website/commit/f16fdbc9e1ee8d747754defc1749c0cbb4aac54f))
* **api:** keep the actuator chain session-free under CSRF ([#572](https://github.com/ESA-Blueshell/website/issues/572)) ([7bbe206](https://github.com/ESA-Blueshell/website/commit/7bbe206c8855c8634860d0fe3e68189e4ab857d1))
* **frontend:** allowlist the post-login redirect target ([#571](https://github.com/ESA-Blueshell/website/issues/571)) ([bdd462c](https://github.com/ESA-Blueshell/website/commit/bdd462c05acbe5b8d289ab90da95372fbc9806a7))


### Build and Dependencies

* **deps:** bump docker/setup-buildx-action in the actions group ([#565](https://github.com/ESA-Blueshell/website/issues/565)) ([892ca21](https://github.com/ESA-Blueshell/website/commit/892ca219c3da159611e24427dc72701aa1d7a9c3))
* **deps:** bump the frontend group ([#564](https://github.com/ESA-Blueshell/website/issues/564)) ([c595301](https://github.com/ESA-Blueshell/website/commit/c59530152ae71e28370e8344cf664e0b65f613aa))
* **deps:** bump the gradle group across 1 directory with 5 updates ([#563](https://github.com/ESA-Blueshell/website/issues/563)) ([04a273d](https://github.com/ESA-Blueshell/website/commit/04a273dc956ea5f9e47290554758d27abde7cd80))
* **deps:** bump undici, js-yaml and brace-expansion out of the open advisories ([#568](https://github.com/ESA-Blueshell/website/issues/568)) ([f5144c5](https://github.com/ESA-Blueshell/website/commit/f5144c5e658f6bb843565e59605d7ab9bf69af4c))

## [1.4.0](https://github.com/ESA-Blueshell/website/compare/v1.3.0...v1.4.0) (2026-08-21)


### Features

* **documents:** make the document buttons copyable links ([#557](https://github.com/ESA-Blueshell/website/issues/557)) ([2dd924d](https://github.com/ESA-Blueshell/website/commit/2dd924d7bc99559dcc012b73a4a14f4a6c2078e3))
* **platform:** alert on outages and announce rollouts via Discord ([#560](https://github.com/ESA-Blueshell/website/issues/560)) ([dd0b67e](https://github.com/ESA-Blueshell/website/commit/dd0b67e429ff9bd474fc06d722bd96c397192415))


### Refactoring

* **platform:** cut the commentary on the Discord alerting manifests ([#561](https://github.com/ESA-Blueshell/website/issues/561)) ([c359468](https://github.com/ESA-Blueshell/website/commit/c359468c9d8119c13b18490f4a0e3974ae175bca))


### Build and Dependencies

* **deps-dev:** bump jsdom from 29.1.1 to 30.0.1 in /services/frontend ([#553](https://github.com/ESA-Blueshell/website/issues/553)) ([d7c6285](https://github.com/ESA-Blueshell/website/commit/d7c6285dca3dd9ab27c0dc8b4959c4410887c2d8))
* **deps:** bump nginxinc/nginx-unprivileged in /services/frontend ([#552](https://github.com/ESA-Blueshell/website/issues/552)) ([dc1e261](https://github.com/ESA-Blueshell/website/commit/dc1e26157119d7775892ca98f651fdf5aa4f4409))
* **deps:** bump the actions group with 3 updates ([#555](https://github.com/ESA-Blueshell/website/issues/555)) ([fadd09a](https://github.com/ESA-Blueshell/website/commit/fadd09a7854d346c9a82e908886db3f47c1852bb))
* **deps:** bump the frontend group across 1 directory with 25 updates ([#521](https://github.com/ESA-Blueshell/website/issues/521)) ([c055844](https://github.com/ESA-Blueshell/website/commit/c0558448d7396de353ceebbfebc08f8dd7a75c53))
* **deps:** bump the gradle group and declare mandatory request fields non-nullable ([#554](https://github.com/ESA-Blueshell/website/issues/554)) ([bd5298c](https://github.com/ESA-Blueshell/website/commit/bd5298c962b3358cec0e2b860707950a854a9d0f))

## [1.3.0](https://github.com/ESA-Blueshell/website/compare/v1.2.0...v1.3.0) (2026-08-20)


### Features

* **api:** shared vocabulary and fee resolution for bulk actions ([#529](https://github.com/ESA-Blueshell/website/issues/529)) ([594e468](https://github.com/ESA-Blueshell/website/commit/594e468a16b26296ee69e34db566530e3145f6b9))
* **email:** redesign the transactional email template ([#539](https://github.com/ESA-Blueshell/website/issues/539)) ([74cfc19](https://github.com/ESA-Blueshell/website/commit/74cfc196ddb0431aed11af6fe31ac6306732b242))
* **signup:** send the confirmation email first and commit the membership on the last fact ([#545](https://github.com/ESA-Blueshell/website/issues/545)) ([8e2379a](https://github.com/ESA-Blueshell/website/commit/8e2379a7f222334275ec788424121b0917ffa5e6))


### Bug Fixes

* **release:** stop the component check rejecting every release ([#542](https://github.com/ESA-Blueshell/website/issues/542)) ([62e43e3](https://github.com/ESA-Blueshell/website/commit/62e43e34e66f689d351ce536367ed8a739172fc7))


### Refactoring

* **frontend:** one User Manager page instead of two ([#540](https://github.com/ESA-Blueshell/website/issues/540)) ([a3582a0](https://github.com/ESA-Blueshell/website/commit/a3582a0558eaa639166104ffa42055704c620913))


### Build and Dependencies

* **deps:** bump node ([#501](https://github.com/ESA-Blueshell/website/issues/501)) ([93c7af3](https://github.com/ESA-Blueshell/website/commit/93c7af3c3178d8dc624c40e6ffbc6e90d08c4b04))

## [1.2.0](https://github.com/ESA-Blueshell/website/compare/v1.1.1...v1.2.0) (2026-08-17)


### Features

* **ci:** automate releases and gate rollout on them ([#532](https://github.com/ESA-Blueshell/website/issues/532)) ([79a5bb1](https://github.com/ESA-Blueshell/website/commit/79a5bb1e59356f9635d5ab38ddffde67016bb443))
* **frontend:** host the transactional email image assets ([#528](https://github.com/ESA-Blueshell/website/issues/528)) ([ed1bbc3](https://github.com/ESA-Blueshell/website/commit/ed1bbc367fd47c5835e7d6dcc1d3e24a60808efe))


### Bug Fixes

* **api:** stop Redis repository scanning at startup ([#527](https://github.com/ESA-Blueshell/website/issues/527)) ([2b1515f](https://github.com/ESA-Blueshell/website/commit/2b1515f3e96d3dce35bf720f72261c2618aab3a9))
* **ci:** classify repository-level tooling in the diff breakdown ([#533](https://github.com/ESA-Blueshell/website/issues/533)) ([6cb77f5](https://github.com/ESA-Blueshell/website/commit/6cb77f5f33af49dc1d72d7d29eed0ace858a3719))
* **ci:** name the release pull request after its version ([#536](https://github.com/ESA-Blueshell/website/issues/536)) ([1feabaa](https://github.com/ESA-Blueshell/website/commit/1feabaac94cad2b8baa4a3a96bbfb2741aaacdac))
* **ci:** publish release images without a stored token ([#538](https://github.com/ESA-Blueshell/website/issues/538)) ([bf5d9a5](https://github.com/ESA-Blueshell/website/commit/bf5d9a500528d35d567a9b82c8c6ca16b31d47b1))


### Refactoring

* **frontend:** share the member type label formatter ([#524](https://github.com/ESA-Blueshell/website/issues/524)) ([caaf11a](https://github.com/ESA-Blueshell/website/commit/caaf11aa430a90c322d2d5ae44d06326bc3357a3))

## 2026 Automated Releases

Generated by release-please from Conventional Commits subjects on `main`.
Earlier periods were written by hand; see the index in `CHANGELOG.md`.

`style` is published here because it means a visible change — spacing, colour,
type rendering. Code formatting belongs under `chore`, which stays hidden along
with `ci` and `test`.
