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

  it("routes each partner's address to its content", () => {
    expect(router.resolve("/partners/el-nino").matched[0]?.props.default).toEqual({slug: "el-nino"})
    expect(router.resolve("/partners/marketing-maatwerk").matched[0]?.props.default).toEqual({slug: "marketing-maatwerk"})
  })
})
