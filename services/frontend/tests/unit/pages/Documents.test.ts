import {describe, expect, it} from "vitest"
import Documents from "@/pages/Documents.vue"
import {mountInApp} from "./helpers"

describe("Documents page", () => {
  it("renders document table", () => {
    const wrapper = mountInApp(Documents, {
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
