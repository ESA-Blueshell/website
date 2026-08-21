# Changelog

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
