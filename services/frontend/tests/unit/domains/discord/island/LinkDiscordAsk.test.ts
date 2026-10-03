import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import LinkDiscordAsk from "@/domains/discord/island/LinkDiscordAsk.vue"

const {mockRoles} = vi.hoisted(() => ({mockRoles: vi.fn()}))
vi.mock("@/domains/discord/adapters/unlinked", () => ({listMyUnlinkedRoles: mockRoles}))

const mountAsk = async (linked: boolean) => {
  const wrapper = mount(LinkDiscordAsk, {props: {linked}})
  await flushPromises()
  return wrapper
}

describe("LinkDiscordAsk", () => {
  beforeEach(() => mockRoles.mockReset())

  it("names the roles waiting on a linked Discord account", async () => {
    mockRoles.mockResolvedValue(["Member", "Sitecie", "Activist"])
    expect((await mountAsk(false)).get("[data-testid='link-discord-ask']").text())
      .toContain("get your roles on the server: Member, Sitecie and Activist.")

    mockRoles.mockResolvedValue(["Member"])
    expect((await mountAsk(false)).text()).toContain("get your role on the server: Member.")
  })

  it("asks nothing once an account is linked or while no role waits", async () => {
    mockRoles.mockResolvedValue(["Member"])
    expect((await mountAsk(true)).find("[data-testid='link-discord-ask']").exists()).toBe(false)

    mockRoles.mockResolvedValue([])
    expect((await mountAsk(false)).find("[data-testid='link-discord-ask']").exists()).toBe(false)
  })
})
