import {beforeEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import EventDetails from "@/components/base/EventDetails.vue"
import {settle} from "../../helpers/testUtils"

const {
  mockGoto,
  mockMarkdownToHtml,
  mockDownloadEventBanner,
} = vi.hoisted(() => ({
  mockGoto: vi.fn(),
  mockMarkdownToHtml: vi.fn((markdown: string) => `<p>${markdown}</p>`),
  mockDownloadEventBanner: vi.fn(),
}))

vi.mock("@/plugins/goto.ts", () => ({
  $goto: mockGoto,
}))

vi.mock("@/plugins/markdownToHtml.ts", () => ({
  default: mockMarkdownToHtml,
}))

vi.mock("@/services/api", () => ({
  downloadEventBanner: mockDownloadEventBanner,
}))

const baseEvent = {
  id: 11,
  title: "LAN Party",
  description: "Bring your setup",
  startTime: "2099-02-20T12:00:00.000Z",
  endTime: "2099-02-20T14:00:00.000Z",
  location: "Discord server",
  banner: true,
  memberPrice: 5,
  publicPrice: 10,
}

describe("EventDetails", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockDownloadEventBanner.mockResolvedValue({data: new Blob(["banner"])})
  })

  it("downloads and displays a banner when banner metadata is present", async () => {
    const wrapper = mount(EventDetails, {
      props: {
        modelValue: baseEvent,
      },
    })

    await settle()

    expect(mockDownloadEventBanner).toHaveBeenCalledWith({
      path: {eventId: 11},
      throwOnError: true,
      responseType: "blob",
    })
    expect(wrapper.find("img").exists()).toBe(true)
    expect(wrapper.find("img").attributes("src")).toContain("blob:mock")
  })

  it("revokes stale banner URL when event no longer has a banner", async () => {
    const revokeSpy = vi.spyOn(URL, "revokeObjectURL")

    const wrapper = mount(EventDetails, {
      props: {
        modelValue: baseEvent,
      },
    })
    await settle()

    await wrapper.setProps({
      modelValue: {
        ...baseEvent,
        banner: false,
      },
    })
    await settle()

    expect(revokeSpy).toHaveBeenCalled()
    expect(wrapper.find("img").exists()).toBe(false)
  })

  it("formats date ranges and resolves location links with intent-specific routing", async () => {
    const wrapper = mount(EventDetails, {
      props: {
        modelValue: baseEvent,
      },
    })
    await settle()

    expect((wrapper.vm as any).formattedDate).toContain(" - ")

    ;(wrapper.vm as any).findLocation()
    expect(mockGoto).toHaveBeenCalledWith("https://discord.gg/23YMFQy")

    await wrapper.setProps({
      modelValue: {
        ...baseEvent,
        location: "Pel center",
      },
    })
    ;(wrapper.vm as any).findLocation()
    expect(mockGoto).toHaveBeenCalledWith(expect.stringContaining("Predator%20Esports%20Lounge"))

    await wrapper.setProps({
      modelValue: {
        ...baseEvent,
        location: "Enschede station",
      },
    })
    ;(wrapper.vm as any).findLocation()
    expect(mockGoto).toHaveBeenCalledWith(expect.stringContaining("Enschede%20station"))
  })
})
