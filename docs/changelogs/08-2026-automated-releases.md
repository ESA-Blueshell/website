# Changelog

## [1.8.0](https://github.com/ESA-Blueshell/website/compare/v1.7.1...v1.8.0) (2026-09-06)


### Features

* **api:** add association statistics end point, and make event banners public ([#1097](https://github.com/ESA-Blueshell/website/issues/1097)) ([0a78185](https://github.com/ESA-Blueshell/website/commit/0a781859d12d0275b3fe7d210188ac2bf94f71ba))
* **association:** about us and become-a-partner move onto the island ([#1104](https://github.com/ESA-Blueshell/website/issues/1104)) ([a7e8e5a](https://github.com/ESA-Blueshell/website/commit/a7e8e5ab4c87a8f50873d02c2767b5b12489ab52))
* **frontend:** the built image serves the api at the page's own origin ([#1171](https://github.com/ESA-Blueshell/website/issues/1171)) ([28eeb68](https://github.com/ESA-Blueshell/website/commit/28eeb68dafe254d2d241f34bd689831222b89f1a)), closes [#1000](https://github.com/ESA-Blueshell/website/issues/1000)
* **history:** a line you can read while scrolling past it ([#1142](https://github.com/ESA-Blueshell/website/issues/1142)) ([712aabf](https://github.com/ESA-Blueshell/website/commit/712aabf7daa7d110a558bde83b2f0e5f7e9a8631))
* **island:** a swipe spawns a band already open ([#1093](https://github.com/ESA-Blueshell/website/issues/1093)) ([7e5d450](https://github.com/ESA-Blueshell/website/commit/7e5d450984a8382da217db629ed2fcf3b1d6a8b0))
* **island:** the site bar joins the island and follows the theme ([#1116](https://github.com/ESA-Blueshell/website/issues/1116)) ([8482d94](https://github.com/ESA-Blueshell/website/commit/8482d94ced51826e175b67a5c0d6183b86ae99f5))
* **membership:** the membership page is rebuilt on the island ([#1115](https://github.com/ESA-Blueshell/website/issues/1115)) ([711dfc6](https://github.com/ESA-Blueshell/website/commit/711dfc6bee5e7c9ca5d9972d4d75aad7a67c5821))
* **partners:** show a partner the space, on the thing it is on ([#1136](https://github.com/ESA-Blueshell/website/issues/1136)) ([aa359d8](https://github.com/ESA-Blueshell/website/commit/aa359d8034a3bd3d4fcaa80cbc3a8ebda29b93b7))
* **users:** the committee picker asks the api for the name typed ([#1146](https://github.com/ESA-Blueshell/website/issues/1146)) ([c3ef905](https://github.com/ESA-Blueshell/website/commit/c3ef905c9a1fdc24590836972be4fc9bce4a923b)), closes [#1139](https://github.com/ESA-Blueshell/website/issues/1139)


### Bug Fixes

* **api:** a request that names a page or a size gets one ([#1156](https://github.com/ESA-Blueshell/website/issues/1156)) ([38c2a4b](https://github.com/ESA-Blueshell/website/commit/38c2a4ba58383618054b745aa0803d6455b3c70b)), closes [#1145](https://github.com/ESA-Blueshell/website/issues/1145)
* **api:** the element constraints on bulk ids are actually emitted ([#1131](https://github.com/ESA-Blueshell/website/issues/1131)) ([e037698](https://github.com/ESA-Blueshell/website/commit/e0376982e349231974c9d961fa62aaa319640752))
* **association:** a figure counted as none comes off the band ([#1182](https://github.com/ESA-Blueshell/website/issues/1182)) ([3045a3c](https://github.com/ESA-Blueshell/website/commit/3045a3c64bc9e233970c30bf6bcf1310d8fa108f))
* **esports:** a refused game entry says why it was refused ([#1152](https://github.com/ESA-Blueshell/website/issues/1152)) ([24c4a8f](https://github.com/ESA-Blueshell/website/commit/24c4a8f3cd6e92e58a803baa3202ec7aad437ecc))
* **esports:** a saved season comes back carrying the season ([#1122](https://github.com/ESA-Blueshell/website/issues/1122)) ([d86039c](https://github.com/ESA-Blueshell/website/commit/d86039c194addd1b6d32b86f257af57df26cde02))
* **esports:** a season id in the url is a whole number ([#1121](https://github.com/ESA-Blueshell/website/issues/1121)) ([e73a303](https://github.com/ESA-Blueshell/website/commit/e73a3038a95624973ff646f4c495af69ad47055b))
* **esports:** the seed says what members remember ([#1069](https://github.com/ESA-Blueshell/website/issues/1069)) ([fd57c4f](https://github.com/ESA-Blueshell/website/commit/fd57c4fd8a9a8ae41b65c1d606860025e45bd252))
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
* **system-tests:** a failure log times the test it belongs to ([#1144](https://github.com/ESA-Blueshell/website/issues/1144)) ([8e7309e](https://github.com/ESA-Blueshell/website/commit/8e7309ed50810632dd757b873f4eaefda29790eb))
* **system-tests:** the member picker is given time to answer ([#1154](https://github.com/ESA-Blueshell/website/issues/1154)) ([9f59629](https://github.com/ESA-Blueshell/website/commit/9f59629f8f76a54b1bb77de3596c4f824d7b6354))
* **tests:** load the module before the clock starts ([#1147](https://github.com/ESA-Blueshell/website/issues/1147)) ([500ff9e](https://github.com/ESA-Blueshell/website/commit/500ff9ebc53a8da08145d554e5721d7bba649ed2))
* **tests:** say enableAutoUnmount once, and give all three pages one events band ([#1128](https://github.com/ESA-Blueshell/website/issues/1128)) ([1059671](https://github.com/ESA-Blueshell/website/commit/1059671d98a0191f45dc93821e61f6d17854fef9))


### Refactoring

* **api:** a read maps its entity to its response once ([#1185](https://github.com/ESA-Blueshell/website/issues/1185)) ([c671ab5](https://github.com/ESA-Blueshell/website/commit/c671ab559670d452a18c113ab46e308fbaf296e9))
* **cohort:** a cohort job is a line, not a file ([#1191](https://github.com/ESA-Blueshell/website/issues/1191)) ([1db1716](https://github.com/ESA-Blueshell/website/commit/1db1716c713430d00d66a9cf7b1b3595783b8fc4))
* **cohort:** one port over the list, with the vendor behind contact ([#1172](https://github.com/ESA-Blueshell/website/issues/1172)) ([01546d5](https://github.com/ESA-Blueshell/website/commit/01546d565eebc64e1f660ee05483c62dbdf185e3))
* **cohorts:** the cohort pages read a domain record, not a response ([#1199](https://github.com/ESA-Blueshell/website/issues/1199)) ([f5fbe0d](https://github.com/ESA-Blueshell/website/commit/f5fbe0d756e8d828bd6905c4cec61ac356bae9e7))
* **contact:** the module keeps only what something reads ([#1178](https://github.com/ESA-Blueshell/website/issues/1178)) ([4b32c5a](https://github.com/ESA-Blueshell/website/commit/4b32c5a6f9cbe02734aa4b5fd6861211e5d20a09))
* **contribution:** one kind, one decision, and a preview that reads once ([#1193](https://github.com/ESA-Blueshell/website/issues/1193)) ([ecd7aad](https://github.com/ESA-Blueshell/website/commit/ecd7aad885e26a2905495cd222d171e032f1c8ad))
* **contribution:** the bulk dialogs reach the api through a domain ([#1160](https://github.com/ESA-Blueshell/website/issues/1160)) ([9d9d385](https://github.com/ESA-Blueshell/website/commit/9d9d38546ae799ae78f1a6fd01f92c4f6e804618)), closes [#946](https://github.com/ESA-Blueshell/website/issues/946)
* **file:** a store behind the files, and the rules out from under it ([#1170](https://github.com/ESA-Blueshell/website/issues/1170)) ([2895463](https://github.com/ESA-Blueshell/website/commit/28954632d09f1ea696f5cfa23becd794a1471d89))
* **frontend:** a domain behind each manager page ([#1188](https://github.com/ESA-Blueshell/website/issues/1188)) ([135c1ba](https://github.com/ESA-Blueshell/website/commit/135c1ba0aaacbb6e2f69209fad8266a7d57bbf8f))
* **frontend:** one reader for what a refusal says ([#1153](https://github.com/ESA-Blueshell/website/issues/1153)) ([0bf1f6f](https://github.com/ESA-Blueshell/website/commit/0bf1f6f3dac6485b7265a7ab64bf2ca5936943bc))
* **frontend:** one rule has one definition ([#1123](https://github.com/ESA-Blueshell/website/issues/1123)) ([54b3725](https://github.com/ESA-Blueshell/website/commit/54b372548e3b37c54a4265d8e7fa76fe3447da15))
* **frontend:** the navigation guard returns its answer ([#1200](https://github.com/ESA-Blueshell/website/issues/1200)) ([4a9b0d6](https://github.com/ESA-Blueshell/website/commit/4a9b0d69f0a5cb026f9c1d9799b10fa95aa9fe82))
* **island:** one header band, and the site bar leaves the app shell ([#1103](https://github.com/ESA-Blueshell/website/issues/1103)) ([dbef38a](https://github.com/ESA-Blueshell/website/commit/dbef38a11c4201430185543960ca0d7f67a23653)), closes [#1074](https://github.com/ESA-Blueshell/website/issues/1074)
* **jobs:** queueing a job crosses one seam ([#1179](https://github.com/ESA-Blueshell/website/issues/1179)) ([363c1f3](https://github.com/ESA-Blueshell/website/commit/363c1f3255318920ea501353e9fa4a9f7a95e0e8))
* **system-tests:** every shared acceptance step has one owner ([#1098](https://github.com/ESA-Blueshell/website/issues/1098)) ([3254561](https://github.com/ESA-Blueshell/website/commit/3254561ac7232fea17ddb16de8d0189d1b828cdc))
* **system-tests:** recovery emails say what the person got ([#1117](https://github.com/ESA-Blueshell/website/issues/1117)) ([c7c3424](https://github.com/ESA-Blueshell/website/commit/c7c3424282da8f55b8f63be472c120785bc32a92)), closes [#966](https://github.com/ESA-Blueshell/website/issues/966)
* **system-tests:** the bulk feature says what was recorded ([#1114](https://github.com/ESA-Blueshell/website/issues/1114)) ([a24eb13](https://github.com/ESA-Blueshell/website/commit/a24eb132ea5c2c4ee962530aee70856a4b098ffe)), closes [#965](https://github.com/ESA-Blueshell/website/issues/965)


### Documentation

* **adr:** ADR-017 keeps what the build cannot check ([#1197](https://github.com/ESA-Blueshell/website/issues/1197)) ([f0838c0](https://github.com/ESA-Blueshell/website/commit/f0838c01d61af983bcbb7ba152227c162f21876a)), closes [#907](https://github.com/ESA-Blueshell/website/issues/907)
* **ci:** the diagnostics say which test they belong to, and which they do not ([#1202](https://github.com/ESA-Blueshell/website/issues/1202)) ([4dbef91](https://github.com/ESA-Blueshell/website/commit/4dbef918040e2fe477a451bea4837dabded3e5c8))


### Build and Dependencies

* **deps:** bump nginxinc/nginx-unprivileged in /services/frontend ([#1161](https://github.com/ESA-Blueshell/website/issues/1161)) ([83f39bb](https://github.com/ESA-Blueshell/website/commit/83f39bb62abd70324b1f5e789d6ce4ef1579e045))
* **deps:** bump the actions group with 3 updates ([#1163](https://github.com/ESA-Blueshell/website/issues/1163)) ([b5bd9f4](https://github.com/ESA-Blueshell/website/commit/b5bd9f47c0c858b8e754056858affe331140b6c9))
* **deps:** bump the frontend group ([#1164](https://github.com/ESA-Blueshell/website/issues/1164)) ([2407728](https://github.com/ESA-Blueshell/website/commit/2407728efad9f76f6dca18551d1f66f04643f4e0))
* **deps:** bump the gradle group across 2 directories with 7 updates ([#1162](https://github.com/ESA-Blueshell/website/issues/1162)) ([85779eb](https://github.com/ESA-Blueshell/website/commit/85779eb239b38b44fcf478fc9ffd49bbed008226))

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
