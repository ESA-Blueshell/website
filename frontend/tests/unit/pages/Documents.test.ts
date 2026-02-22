import {describe, expect, it} from "vitest"
import {shallowMount} from "@vue/test-utils"
import Documents from "@/pages/Documents.vue"

describe("Documents page", () => {
  it("renders document table", () => {
    const wrapper = shallowMount(Documents, {
      global: {
        stubs: {
          DocumentTable: {
            template: "<div data-test='doc-table'>table</div>",
          },
        },
      },
    })

    expect(wrapper.find("[data-test='doc-table']").exists()).toBe(true)
  })
})
