import {describe, expect, it, vi} from "vitest"
import Home from "@/pages/Home.vue"
import router from "@/plugins/router"
import {mountInApp} from "./helpers"

const mockGoto = vi.hoisted(() => vi.fn())

vi.mock("@/plugins/goto", () => ({
  $goto: mockGoto,
}))

describe("Home page", () => {
  it("routes CTA clicks and keeps partner link configuration intact", async () => {
    const wrapper = mountInApp(Home, {
      global: {
        stubs: {
          MainBanner: true,
          DiscordBanner: true,
          SocialsBanner: true,
          GamesWePlay: true,
        },
      },
    })

    await wrapper.find("a").trigger("click")
    expect(mockGoto).toHaveBeenCalledWith("/aboutus")

    const partnerUrls = (wrapper.vm as any).partners.map((partner: { url: string }) => partner.url)
    expect(partnerUrls).toEqual([
      "/partners/el-nino",
      "https://marketingmaatwerk.nl/",
      "https://esportsteamtwente.nl/",
    ])
    expect(router.resolve(partnerUrls[0]).matched.length).toBeGreaterThan(0)
  })
})
