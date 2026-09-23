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
  server: "Blueshell",
  online: 269,
  members: 1199,
  rooms: [
    {id: "1", name: "Public Voice 1", locked: false, people: [{name: "Emma", avatar: "/emma.png"}, {name: "Viktor"}], href: "https://discord.com/channels/g/1"},
    {id: "3", name: "Members lounge", locked: true, people: [{name: "Mo"}, {name: "Ana"}, {name: "Kai"}], href: "https://discord.com/channels/g/3"},
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
    expect(wrapper.get(".widget__server").text()).toBe("Blueshell")
  })

  it("counts online out of everybody, and joins each room in Discord itself", async () => {
    mockRead.mockResolvedValue(FIXTURE)
    const wrapper = await mountBand()

    expect(wrapper.get("[data-testid=home-discord-live]").text()).toBe("269/1199 online")
    const open = wrapper.get("[data-testid=home-discord-room-1]")
    expect(open.text()).toContain("Emma")
    expect(open.text()).toContain("Viktor")
    expect(open.get("img").attributes("src")).toBe("/emma.png")
    expect(open.get(".widget__join").attributes("href")).toBe("https://discord.com/channels/g/1")
  })

  it("marks a members-only room, and says why its way in asks for membership", async () => {
    mockRead.mockResolvedValue(FIXTURE)
    const wrapper = await mountBand()

    const locked = wrapper.get("[data-testid=home-discord-room-3]")
    expect(locked.classes()).toContain("widget__room--locked")
    expect(locked.get(".widget__room-name").text()).toBe("Members lounge · members only")
    expect(locked.get(".widget__join").attributes("aria-label")).toBe("Join Members lounge, which opens with membership")
    expect(wrapper.find(".widget__foot").exists()).toBe(true)
  })

  it("says nobody is in voice where every room is empty, and offers the empty rooms to start", async () => {
    mockRead.mockResolvedValue({server: "Blueshell", online: 3, rooms: [{id: "4", name: "Public Voice 1", locked: false, people: [], href: "h"}]})
    const wrapper = await mountBand()

    expect(wrapper.get("[data-testid=home-discord-room-4]").text()).toContain("nobody yet, start it")
    expect(wrapper.get("[data-testid=home-discord-room-4]").text()).toContain("empty")
    expect(wrapper.get("[data-testid=home-discord-quiet]").text()).toBe("Nobody is in voice right now.")
    expect(wrapper.get("[data-testid=home-discord-live]").text()).toBe("3 online")
    expect(wrapper.find(".widget__foot").exists()).toBe(false)
  })

  it("is the invite alone where Discord says nothing", async () => {
    mockRead.mockResolvedValue(null)
    const wrapper = await mountBand()

    const widget = wrapper.get("[data-testid=home-discord-widget]")
    expect(widget.classes()).toContain("widget--invite")
    expect(widget.find("[data-testid=home-discord-quiet]").exists()).toBe(false)
    expect(wrapper.find("[data-testid=home-discord-live]").exists()).toBe(false)
    expect(widget.text()).toContain("Blueshell")
  })

  it("reads again every minute while the page is seen, and keeps the last answer when a read fails", async () => {
    vi.useFakeTimers()
    mockRead.mockResolvedValueOnce(FIXTURE).mockResolvedValueOnce(null).mockResolvedValue({...FIXTURE, online: 300})
    const wrapper = await mountBand()

    await vi.advanceTimersByTimeAsync(60_000)
    expect(mockRead).toHaveBeenCalledTimes(2)
    expect(wrapper.get("[data-testid=home-discord-live]").text()).toBe("269/1199 online")

    await vi.advanceTimersByTimeAsync(60_000)
    expect(wrapper.get("[data-testid=home-discord-live]").text()).toBe("300/1199 online")

    // A hidden page is not asked for, and coming back into view reads at once.
    vi.spyOn(document, "visibilityState", "get").mockReturnValue("hidden")
    await vi.advanceTimersByTimeAsync(60_000)
    expect(mockRead).toHaveBeenCalledTimes(3)
    vi.spyOn(document, "visibilityState", "get").mockReturnValue("visible")
    document.dispatchEvent(new Event("visibilitychange"))
    await flushPromises()
    expect(mockRead).toHaveBeenCalledTimes(4)

    wrapper.unmount()
    await vi.advanceTimersByTimeAsync(60_000)
    expect(mockRead).toHaveBeenCalledTimes(4)
    vi.useRealTimers()
    vi.restoreAllMocks()
  })
})
