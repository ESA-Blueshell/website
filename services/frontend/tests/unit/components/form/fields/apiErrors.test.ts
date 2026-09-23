import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import {defineComponent, h, ref} from "vue"
import {Form} from "vee-validate"
import VvField from "@/components/form/fields/VvField.vue"
import CountrySelect from "@/components/form/fields/CountrySelect.vue"
import EnumPicker from "@/components/form/fields/EnumPicker.vue"
import {apply} from "@/plugins/validation"

/** What the api sends when it refuses a field: ADR-026's shape, as the forms receive it. */
const refusal = (field: string, said: string) => ({
  response: {
    status: 400,
    data: {errors: [{field, message: said}]},
  },
})

/**
 * A form holding one island field, with a handle on the vee-validate context, which is what
 * `handleSubmitError` reaches for when the api refuses a save.
 */
const formWith = (name: string, control: Record<string, unknown>) => {
  const held = ref("")
  let context: {setFieldError: (f: string, m: string[]) => void} | undefined
  const wrapper = mount(defineComponent({
    setup() {
      return () => h(Form, null, {
        default: (slot: {setFieldError: (f: string, m: string[]) => void}) => {
          context = slot
          return h(VvField, {modelValue: held.value, "onUpdate:modelValue": (v: string) => {
            held.value = v
          }, name, label: "Country", ...control})
        },
      })
    },
  }))
  return {wrapper, context: () => context!}
}

describe("an api refusal", () => {
  it("is shown under the field it names", async () => {
    const {wrapper, context} = formWith("country", {componentProps: {kind: "country"}})

    apply(context() as never, refusal("country", "We do not ship there."))
    await wrapper.vm.$nextTick()
    await wrapper.vm.$nextTick()

    expect(wrapper.find(".island-field__said").text()).toBe("We do not ship there.")
    expect(wrapper.find(".island-field__said").classes()).toContain("island-field__said--wrong")
  })

  it("reaches a text field the same way", async () => {
    const {wrapper, context} = formWith("username", {})

    apply(context() as never, refusal("username", "That name is taken."))
    await wrapper.vm.$nextTick()
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
