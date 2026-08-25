# Changelog

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
