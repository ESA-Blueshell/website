import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import IslandPhone from "@/components/island/IslandPhone.vue"

const phone = (number = "") =>
  mount(IslandPhone, {props: {modelValue: number, testidPrefix: "phone"}, attachTo: document.body})

const numberField = (wrapper: ReturnType<typeof phone>) =>
  wrapper.find('[data-testid="phone-number"]')

describe("IslandPhone", () => {
  it("keeps what is typed as one international number", async () => {
    const wrapper = phone()

    await numberField(wrapper).setValue("612345678")

    expect(wrapper.emitted("update:modelValue")?.at(-1)?.[0]).toBe("+31612345678")
    wrapper.unmount()
  })

  it("drops the trunk zero a Dutch number is dialled with at home", async () => {
    const wrapper = phone()

    await numberField(wrapper).setValue("0612345678")

    expect(wrapper.emitted("update:modelValue")?.at(-1)?.[0]).toBe("+31612345678")
    wrapper.unmount()
  })

  it("splits a number it is given back into the country and the rest", () => {
    const wrapper = phone("+4917012345")

    expect(wrapper.find('[data-testid="phone-from-shut"]').attributes("aria-label"))
      .toContain("Germany")
    wrapper.unmount()
  })

  it("holds nothing rather than a bare dial code while nothing is typed", () => {
    const wrapper = phone()

    expect(wrapper.emitted("update:modelValue")).toBeUndefined()
    wrapper.unmount()
  })
})
