import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import StarboardFeed from "@/domains/discord/island/StarboardFeed.vue"

const {mockRead} = vi.hoisted(() => ({mockRead: vi.fn()}))
vi.mock("@/domains/discord/adapters/starboard", () => ({readStarboard: mockRead}))

const entry = (fields: Record<string, unknown>) => ({
  id: "9",
  authorName: "The Old Man",
  authorNickname: "Joris",
  avatar: "https://cdn.discordapp.com/avatars/1/a.png",
  text: "Soon to be released events page redesigns:",
  image: "https://cdn.discordapp.com/attachments/2/3/image.png",
  stars: 12,
  channel: "general",
  href: "https://discord.com/channels/1/2/9",
  postedAt: "2026-09-23T08:22:05Z",
  ...fields,
})

const stubs = {MarkdownView: {name: "MarkdownView", props: ["source"], template: "<div class=markdown />"}}

const mountFeed = async () => {
  const wrapper = mount(StarboardFeed, {global: {stubs}})
  await flushPromises()
  return wrapper
}

describe("StarboardFeed", () => {
  beforeEach(() => mockRead.mockReset())

  it("shows each starred message whole, with its author's server name, its channel, its day, its stars and a way into Discord", async () => {
    mockRead.mockResolvedValue([entry({}), entry({id: "10", authorNickname: null, text: null, image: null, avatar: null})])
    const wrapper = await mountFeed()

    expect(wrapper.get("[data-testid=home-starboard-channel]").attributes()).toMatchObject({
      href: expect.stringMatching(/\/api\/discord\/invite\/starboard$/),
      target: "_blank",
    })

    const first = wrapper.get("[data-testid=home-starboard-9]")
    expect(first.get(".starboard__name").text()).toBe("Joris")
    expect(first.get(".starboard__channel").text()).toBe("#general")
    expect(first.get(".starboard__when").attributes("datetime")).toBe("2026-09-23T08:22:05Z")
    expect(first.get(".starboard__when").text()).toBe("23 Sep")
    expect(first.getComponent({name: "MarkdownView"}).props("source")).toBe("Soon to be released events page redesigns:")
    expect(first.get(".starboard__image").attributes("src")).toBe("https://cdn.discordapp.com/attachments/2/3/image.png")
    expect(first.get(".starboard__stars").text()).toBe("12 stars")
    expect(first.get(".starboard__open").attributes()).toMatchObject({href: "https://discord.com/channels/1/2/9", target: "_blank"})

    const bare = wrapper.get("[data-testid=home-starboard-10]")
    expect(bare.get(".starboard__name").text()).toBe("The Old Man")
    expect(bare.find(".markdown").exists()).toBe(false)
    expect(bare.find("img").exists()).toBe(false)
    expect(bare.get(".starboard__avatar--initials").text()).toBe("T")
  })

  it("keeps the api's order, most stars first", async () => {
    mockRead.mockResolvedValue([entry({id: "1", stars: 30}), entry({id: "2", stars: 12}), entry({id: "3", stars: 3})])
    const wrapper = await mountFeed()

    expect(wrapper.findAll(".starboard__entry").map(item => item.attributes("data-testid")))
      .toEqual(["home-starboard-1", "home-starboard-2", "home-starboard-3"])
  })

  it("draws nothing where the api cannot say, or has nothing starred", async () => {
    mockRead.mockResolvedValue(null)
    expect((await mountFeed()).find("[data-testid=home-starboard]").exists()).toBe(false)

    mockRead.mockResolvedValue([])
    expect((await mountFeed()).find("[data-testid=home-starboard]").exists()).toBe(false)
  })
})
