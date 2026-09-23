import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import DiscordBand from "@/domains/discord/island/DiscordBand.vue"

const {mockRead} = vi.hoisted(() => ({mockRead: vi.fn()}))

vi.mock("@/domains/discord/rooms", async (importOriginal) => ({
  ...(await importOriginal<object>()),
  readDiscordRooms: mockRead,
}))

/* The shape #1344's endpoint answers with, members-only rooms and the member total included. */
const FIXTURE = {
  server: "ESA Blueshell",
  online: 42,
  members: 380,
  rooms: [
    {id: "1", name: "#general-voice", locked: false, people: ["Emma", "Viktor"]},
    {id: "2", name: "#looking-for-group", locked: false, people: []},
    {id: "3", name: "#members-lounge", locked: true, people: ["Mo", "Ana", "Kai"]},
  ],
}

const mountBand = async () => {
  const wrapper = mount(DiscordBand)
  await flushPromises()
  return wrapper
}

describe("DiscordBand", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("invites a look in, with the widget's Join server as the band's only way in", async () => {
    mockRead.mockResolvedValue(FIXTURE)
    const wrapper = await mountBand()

    expect(wrapper.find("h2").text()).toBe("Come check the vibes")
    expect(wrapper.find(".band-head__way").exists()).toBe(false)
    expect(wrapper.get("[data-testid=home-discord-join]").attributes()).toMatchObject({href: "https://discord.gg/23YMFQy", target: "_blank"})
  })

  it("lists every room: public ones joinable, members-only ones locked with who is inside", async () => {
    mockRead.mockResolvedValue(FIXTURE)
    const wrapper = await mountBand()

    expect(wrapper.get("[data-testid=home-discord-live]").text()).toBe("42 online of 380 members")
    expect(wrapper.get("[data-testid=home-discord-room-1]").text()).toContain("Emma and Viktor")
    const locked = wrapper.get("[data-testid=home-discord-room-3]")
    expect(locked.classes()).toContain("widget__room--locked")
    expect(locked.text()).toContain("members only · Mo, Ana and 1 more")
    expect(locked.get(".widget__join").attributes("aria-label")).toBe("Join #members-lounge, which opens with membership")
    expect(wrapper.find(".widget__foot").exists()).toBe(true)
  })

  it("says only the online count where the member total is not known, and no foot without a locked room", async () => {
    mockRead.mockResolvedValue({server: "ESA Blueshell", online: 7, rooms: [FIXTURE.rooms[0]]})
    const wrapper = await mountBand()

    expect(wrapper.get("[data-testid=home-discord-live]").text()).toBe("7 online")
    expect(wrapper.find(".widget__foot").exists()).toBe(false)
  })

  it("is the invite alone where Discord says nothing", async () => {
    mockRead.mockResolvedValue(null)
    const wrapper = await mountBand()

    const widget = wrapper.get("[data-testid=home-discord-widget]")
    expect(widget.classes()).toContain("widget--invite")
    expect(widget.find(".widget__rooms").exists()).toBe(false)
    expect(wrapper.find("[data-testid=home-discord-live]").exists()).toBe(false)
    expect(widget.text()).toContain("ESA Blueshell")
    expect(wrapper.find("[data-testid=home-discord-join]").exists()).toBe(true)
  })

  it("draws the head alone where the server has no voice rooms", async () => {
    mockRead.mockResolvedValue({server: "ESA Blueshell", online: 3, rooms: []})
    const wrapper = await mountBand()

    expect(wrapper.get("[data-testid=home-discord-widget]").classes()).toContain("widget--invite")
  })
})
