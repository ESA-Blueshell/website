import {afterEach, describe, expect, it, vi} from "vitest"
import MyApps from "@/pages/MyApps.vue"
import {mountInApp, settle} from "./helpers"

const answer = (ok: boolean, body: unknown) => vi.fn(async () => ({ok, json: async () => body}))

describe("My Apps page", () => {
  afterEach(() => vi.unstubAllGlobals())

  it("shows each service as a tile that opens it in a new tab", async () => {
    vi.stubGlobal("fetch", answer(true, [{id: "vault", name: "Vault", url: "https://vault.example", iconUrl: "/vault.svg", description: "Secrets"}]))
    const wrapper = mountInApp(MyApps)
    await settle()

    const tile = wrapper.get("a.apps__tile")
    expect(tile.attributes("href")).toBe("https://vault.example")
    expect(tile.attributes("target")).toBe("_blank")
    expect(tile.text()).toContain("Vault")
    expect(tile.text()).toContain("Secrets")
  })

  it("says so when there is no service to show", async () => {
    vi.stubGlobal("fetch", answer(false, null))
    const wrapper = mountInApp(MyApps)
    await settle()

    expect(wrapper.text()).toContain("No services available.")
  })
})
