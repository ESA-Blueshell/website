import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import {defineComponent, h, ref} from "vue"
import FormControl from "@/components/island/FormControl.vue"
import CountrySelect from "@/components/form/fields/CountrySelect.vue"
import EnumPicker from "@/components/form/fields/EnumPicker.vue"
import {type FormChecks, useFormChecks} from "@/composables/useFormChecks"
import type {ControlKind} from "@/components/island/FormControl.vue"

/** What the api sends when it refuses a field: ADR-026's shape, as the forms receive it. */
const refusal = (field: string, said: string) => ({
  response: {
    status: 400,
    data: {errors: [{field, message: said}]},
  },
})

/** A form holding one island field, shown with what its checks say of it. */
const formWith = (name: string, kind: ControlKind = "text") => {
  let checks: FormChecks | undefined
  const wrapper = mount(defineComponent({
    setup() {
      const held = ref("")
      checks = useFormChecks(() => ({[name]: {value: () => held.value, checks: []}}))
      return () => h(FormControl, {
        modelValue: held.value,
        "onUpdate:modelValue": (v: string | null) => {
          held.value = v ?? ""
        },
        errorMessages: checks!.errorsOf(name),
        kind,
        label: "Country",
      })
    },
  }))
  return {wrapper, checks: () => checks!}
}

describe("an api refusal", () => {
  it("is shown under the field it names", async () => {
    const {wrapper, checks} = formWith("country", "country")

    checks().refuse(refusal("country", "We do not ship there."))
    await wrapper.vm.$nextTick()

    expect(wrapper.find(".island-field__said").text()).toBe("We do not ship there.")
    expect(wrapper.find(".island-field__said").classes()).toContain("island-field__said--wrong")
  })

  it("reaches a text field the same way", async () => {
    const {wrapper, checks} = formWith("username")

    checks().refuse(refusal("username", "That name is taken."))
    await wrapper.vm.$nextTick()

    expect(wrapper.find(".island-field__said").text()).toBe("That name is taken.")
  })

  it("reaches the country field, which renders a picker rather than an input", async () => {
    const wrapper = mount(CountrySelect, {props: {modelValue: "NL", errorMessages: ["Pick one."]}})

    expect(wrapper.find(".island-field__said").text()).toBe("Pick one.")
  })

  it("reaches a ported picker, which has no input at all", () => {
    const wrapper = mount(EnumPicker, {
      props: {values: ["PAID"], modelValue: undefined, errorMessages: "Say which."},
    })

    expect(wrapper.find(".island-field__said").text()).toBe("Say which.")
  })
})
