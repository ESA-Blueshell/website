import {describe, expect, it} from "vitest"
import Partner from "@/pages/partners/Partner.vue"
import PartnerPage from "@/domains/association/island/PartnerPage.vue"
import {PARTNER_PAGES} from "@/domains/association"
import router from "@/plugins/router"
import {mountInApp} from "../helpers"

describe("Partner page", () => {
  it("draws the partner its route names", () => {
    const wrapper = mountInApp(Partner, {props: {slug: "marketing-maatwerk"}})

    expect(wrapper.getComponent(PartnerPage).props("content")).toBe(PARTNER_PAGES["marketing-maatwerk"])
  })

  it("routes each partner's address to this page, with its content's slug", async () => {
    for (const slug of ["el-nino", "marketing-maatwerk"]) {
      const route = router.resolve(`/partners/${slug}`).matched[0]!
      const load = route.components!.default as () => Promise<{default: unknown}>

      expect(route.props.default).toEqual({slug})
      expect((await load()).default).toBe(Partner)
    }
  })
})
