import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount, RouterLinkStub} from "@vue/test-utils"
import CasualBand from "@/domains/association/island/CasualBand.vue"
import {forgetCasualGames} from "@/domains/games"

const push = vi.fn()
vi.mock("vue-router", () => ({useRouter: () => ({push})}))

const findCasualGames = vi.fn()
vi.mock("@/services/api", async importOriginal => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  findCasualGames: () => findCasualGames(),
}))

const game = (code: string, name: string, archived = false) => ({
  code, name, slug: name.toLowerCase(), accent: null, intro: null, banner: null, icon: null, sortIndex: 0, archived, inCompetition: false, channels: [],
})

/* The reel is its own component with its own tests; what is under test is what the band hands it. */
const FlickReel = {name: "FlickReel", props: ["items", "testidPrefix", "panBackLabel", "panOnLabel"], emits: ["go"], template: "<div />"}
const LeadBand = {name: "LeadBand", template: "<section><slot /><slot name=\"bleed\" /></section>"}

const mountBand = async () => {
  const wrapper = mount(CasualBand, {global: {stubs: {FlickReel, LeadBand, RouterLink: RouterLinkStub}}})
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  forgetCasualGames()
  push.mockReset()
  findCasualGames.mockResolvedValue({data: [game("CHESS", "Chess"), game("DOTA_2", "Dota 2", true), game("WORDLE", "Wordle")]})
})

describe("CasualBand", () => {
  it("runs the games that are played on the reel, pinned dark, and leaves the archived ones out", async () => {
    const reel = (await mountBand()).findComponent({name: "FlickReel"})

    expect(reel.props("items").map((one: {title: string}) => one.title)).toEqual(["Chess", "Wordle"])
    expect(reel.props("items")[0]).toMatchObject({href: "/casual/chess", initials: "C", accent: "var(--color-brand)"})
    expect(reel.classes()).toContain("island-dark")
  })

  it("leads to every game on the casual page", async () => {
    const more = (await mountBand()).findComponent(RouterLinkStub)

    expect(more.props("to")).toBe("/casual")
    expect(more.text()).toBe("All games")
  })

  it("follows a game to its own page", async () => {
    const reel = (await mountBand()).findComponent({name: "FlickReel"})

    reel.vm.$emit("go", {href: "/casual/chess"})

    expect(push).toHaveBeenCalledWith("/casual/chess")
  })

  it("hides itself while there is no game to show", async () => {
    findCasualGames.mockResolvedValue({data: undefined})

    expect((await mountBand()).find("section").exists()).toBe(false)
  })
})
