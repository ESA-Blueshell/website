import {describe, expect, it} from "vitest"
import {RouterLinkStub} from "@vue/test-utils"
import Unauthorized from "@/pages/Unauthorized.vue"
import {mountInApp} from "./helpers"

describe("Unauthorized page", () => {
  it("names the service that refused and offers the way back", () => {
    const wrapper = mountInApp(Unauthorized, {
      global: {stubs: {RouterLink: RouterLinkStub}, mocks: {$route: {query: {service: "vault.esa-blueshell.nl"}}}},
    })

    expect(wrapper.text()).toContain("Vault")
    const links = wrapper.findAllComponents(RouterLinkStub)
    expect(links.map(link => [link.text(), link.props("to")])).toEqual([["Back to My Apps", "/myapps"], ["Home", "/"]])
  })
})
