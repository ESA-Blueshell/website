import {describe, expect, it} from "vitest"
import {mount, RouterLinkStub} from "@vue/test-utils"
import CasualGamePage from "@/domains/games/components/CasualGamePage.vue"
import type {CasualGame} from "@/domains/games"

const picture = (url: string) => ({url, path: url, width: 1600, height: 900, renditions: [{url: `${url}?w=640`, width: 640}]})

const valorant: CasualGame = {
  code: "VALORANT", name: "Valorant", slug: "valorant", accent: "#ff4655", intro: "Five-stacks, customs and clips.", sortIndex: 1,
  archived: false, inCompetition: true, banner: picture("/v.webp"), icon: picture("/v-icon.webp"),
}
const dota: CasualGame = {code: "DOTA_2", name: "Dota 2", slug: "dota-2", accent: null, intro: null, sortIndex: 2, archived: true, inCompetition: false, banner: null, icon: null}

const mountPage = (game: CasualGame) =>
  mount(CasualGamePage, {props: {game}, global: {stubs: {RouterLink: RouterLinkStub, VMain: {template: "<main><slot /></main>"}}}})

describe("one game's page", () => {
  it("heads the page with the game's banner, icon, name and intro, and leads to its competition page", () => {
    const wrapper = mountPage(valorant)
    const head = wrapper.get("[data-testid=casual-game-head]")

    expect(head.get("h1").text()).toBe("Valorant")
    expect(head.get("h1 img").attributes("src")).toBe("/v-icon.webp")
    expect(head.text()).toContain("Five-stacks, customs and clips.")
    expect(head.get(".game-page__art img").attributes("srcset")).toBe("/v.webp?w=640 640w, /v.webp 1600w")
    expect(wrapper.findAllComponents(RouterLinkStub).map(link => link.props("to"))).toEqual(["/casual", "/competition/valorant"])
    expect(wrapper.find("[data-testid=casual-game-archived]").exists()).toBe(false)
  })

  it("says a game nobody fields is not played in competition, and marks an archived one", () => {
    const wrapper = mountPage(dota)

    expect(wrapper.get("[data-testid=casual-game-not-competitive]").text()).toBe("We don't currently play this game competitively")
    expect(wrapper.get("[data-testid=casual-game-archived]").text()).toBe("Archived")
    expect(wrapper.get(".game-page__plate").text()).toBe("D2")
    expect(wrapper.find("[data-testid=casual-game-competition]").exists()).toBe(false)
  })
})
