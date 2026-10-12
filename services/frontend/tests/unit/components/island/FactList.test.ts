import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import FactList from "@/components/island/FactList.vue"

describe("FactList", () => {
  it("draws a fault and a value worth a look in their own colours, and the rest plainly", () => {
    const wrapper = mount(FactList, {
      props: {
        columns: 6,
        facts: [
          {label: "Dead", value: "2", tone: "danger", testid: "dead"},
          {label: "Failed", value: "1", tone: "warning", testid: "failed"},
          {label: "Total", value: "9", testid: "total"},
        ],
      },
    })

    expect(wrapper.classes()).toContain("facts--6")
    expect(wrapper.get('[data-testid="dead"] .facts__value').classes()).toContain("facts__value--danger")
    expect(wrapper.get('[data-testid="failed"] .facts__value').classes()).toContain("facts__value--warning")
    expect(wrapper.get('[data-testid="total"] .facts__value').classes()).toEqual(["facts__value"])
    wrapper.unmount()
  })
})
