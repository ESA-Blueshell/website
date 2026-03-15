import {describe, expect, it} from "vitest"
import {shallowMount} from "@vue/test-utils"
import {validate} from "vee-validate"
import MembershipForm from "@/components/form/MembershipForm.vue"

const vvFieldStub = {
  name: "VvField",
  props: ["name", "rules"],
  template: "<div class='vv-field-stub' :data-name='name' :data-rules='rules' />",
}
const formStub = {template: "<div><slot /></div>"}

function rulesByName(wrapper: ReturnType<typeof shallowMount>) {
  return Object.fromEntries(
    wrapper
      .findAll(".vv-field-stub")
      .map((field) => [String(field.attributes("data-name")), String(field.attributes("data-rules") ?? "")]),
  )
}

describe("MembershipForm", () => {
  it("requires explicit terms acceptance", () => {
    const wrapper = shallowMount(MembershipForm, {
      global: {
        stubs: {
          Form: formStub,
          VvField: vvFieldStub,
        },
      },
    })
    expect(rulesByName(wrapper)).toMatchObject({
      consented: "accepted",
    })
  })

  it("returns the intended acceptance validation message", async () => {
    shallowMount(MembershipForm)
    const result = await validate(false, "accepted")

    expect(result.valid).toBe(false)
    expect(result.errors[0]).toBe("You must accept the membership conditions to continue.")
  })

})
