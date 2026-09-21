import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import PhoneInput from "@/components/island/PhoneInput.vue"

const phone = (props: Record<string, unknown> = {}) =>
  mount(PhoneInput, {props: {modelValue: "", testidPrefix: "phone", ...props}, attachTo: document.body})

const number = (wrapper: ReturnType<typeof phone>) => wrapper.find('[data-testid="phone-number"]')
const said = (wrapper: ReturnType<typeof phone>) =>
  wrapper.emitted("update:modelValue")?.at(-1)?.[0]

describe("a phone number typed whole", () => {
  it("is taken apart rather than read as a local one", async () => {
    const wrapper = phone()

    await number(wrapper).setValue("+4917012345678")

    expect(said(wrapper)).toBe("+4917012345678")
    expect(wrapper.find('[data-testid="phone-from-shut"]').attributes("aria-label"))
      .toContain("Germany")
    wrapper.unmount()
  })

  it("is read as it is typed, before it is a whole number", async () => {
    const wrapper = phone()

    await number(wrapper).setValue("+31 6")

    expect(said(wrapper)).toBe("+316")
    wrapper.unmount()
  })

  it("keeps the country already chosen when the dial code names none", async () => {
    const wrapper = phone()

    await number(wrapper).setValue("+9999 123")

    expect(said(wrapper)).toContain("+31")
    wrapper.unmount()
  })
})

describe("a phone field", () => {
  it("opens on the country it was told to", () => {
    const wrapper = phone({defaultCountry: "BE"})

    expect(wrapper.emitted("update:country")?.at(-1)?.[0]).toBe("BE")
    wrapper.unmount()
  })

  it("rebuilds the number when another country is chosen", async () => {
    const wrapper = phone()
    await number(wrapper).setValue("612345678")

    wrapper.findComponent({name: "SearchPicker"}).vm.$emit("pick", "DE")
    await wrapper.vm.$nextTick()

    expect(said(wrapper)).toBe("+49612345678")
    expect(wrapper.emitted("update:country")?.at(-1)?.[0]).toBe("DE")
    wrapper.unmount()
  })

  it("holds nothing at all while nothing is typed", async () => {
    const wrapper = phone()

    await number(wrapper).setValue("")

    expect(wrapper.emitted("update:modelValue")).toBeUndefined()
    wrapper.unmount()
  })

  it("splits what the form hands it, and ignores what is not a number", async () => {
    const wrapper = phone({modelValue: "+31612345678"})
    expect((number(wrapper).element as HTMLInputElement).value).toContain("6")

    await wrapper.setProps({modelValue: "nonsense"})

    expect((number(wrapper).element as HTMLInputElement).value).toContain("6")
    wrapper.unmount()
  })

  it("says it is wrong, is off when the form is, and carries what it is told to say", () => {
    const wrapper = phone({
      invalid: true, disabled: true, placeholder: "6 12345678",
      controlId: "phone", describedBy: "phone-said",
    })

    expect(wrapper.classes()).toContain("island-phone--wrong")
    expect(number(wrapper).attributes("disabled")).toBeDefined()
    expect(number(wrapper).attributes("placeholder")).toBe("6 12345678")
    expect(number(wrapper).attributes("id")).toBe("phone")
    expect(number(wrapper).attributes("aria-describedby")).toBe("phone-said")
    expect(number(wrapper).attributes("aria-invalid")).toBe("true")
    wrapper.unmount()
  })

  it("offers every country the api can dial, each with its code", () => {
    const wrapper = phone()
    const rows = wrapper.findComponent({name: "SearchPicker"}).props("options") as
      Array<{key: string; label: string; note: string; flag: string}>

    expect(rows.length).toBeGreaterThan(200)
    expect(rows.every(one => one.note.startsWith("+") && one.flag === one.key)).toBe(true)
    wrapper.unmount()
  })
})
