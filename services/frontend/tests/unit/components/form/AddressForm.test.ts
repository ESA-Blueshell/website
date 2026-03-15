import {describe, expect, it} from "vitest"
import {shallowMount} from "@vue/test-utils"
import AddressForm from "@/components/form/AddressForm.vue"

const vvFieldStub = {
  name: "VvField",
  props: ["name", "rules"],
  template: "<div class='vv-field-stub' :data-name='name' :data-rules='rules' />",
}
const formStub = {template: "<div><slot /></div>"}

function fieldRules(wrapper: ReturnType<typeof shallowMount>) {
  return Object.fromEntries(
    wrapper
      .findAll(".vv-field-stub")
      .map((field) => [String(field.attributes("data-name")), String(field.attributes("data-rules") ?? "")]),
  )
}

describe("AddressForm", () => {
  it("declares validation rules for all address fields", () => {
    const wrapper = shallowMount(AddressForm, {
      global: {
        stubs: {
          Form: formStub,
          VvField: vvFieldStub,
        },
      },
    })
    expect(fieldRules(wrapper)).toMatchObject({
      street: "required|minChars:2",
      houseNumber: "required",
      zipCode: "required|minChars:2",
      city: "required|minChars:2",
      country: "required",
    })
  })

})
