import {describe, expect, it} from "vitest"
import {mount, RouterLinkStub} from "@vue/test-utils"
import CountryFlag from "@/components/island/CountryFlag.vue"
import CheckBox from "@/components/island/CheckBox.vue"
import CutButton from "@/components/island/CutButton.vue"
import FormField from "@/components/island/FormField.vue"
import FileInput from "@/components/island/FileInput.vue"
import TextInput from "@/components/island/TextInput.vue"
import RadioGroup from "@/components/island/RadioGroup.vue"
import PanChevron from "@/components/island/PanChevron.vue"

describe("TextInput", () => {
  const input = (props: Record<string, unknown> = {}) =>
    mount(TextInput, {props: {modelValue: "", ...props}})

  it("hides a password until the eye is pressed, and puts it back", async () => {
    const wrapper = input({type: "password", modelValue: "Password123!", testid: "secret"})

    expect(wrapper.find("input").attributes("type")).toBe("password")

    await wrapper.find('[data-testid="secret-reveal"]').trigger("click")

    expect(wrapper.find("input").attributes("type")).toBe("text")
    expect(wrapper.find("input").element.value).toBe("Password123!")

    await wrapper.find('[data-testid="secret-reveal"]').trigger("click")

    expect(wrapper.find("input").attributes("type")).toBe("password")
  })

  it("offers no eye on a field that is not a password", () => {
    expect(input({type: "email", testid: "mail"}).find('[data-testid="mail-reveal"]').exists())
      .toBe(false)
  })

  it("names what it is, what says it, and what is wrong with it", () => {
    const wrapper = input({
      controlId: "email", describedBy: "email-said", invalid: true,
      placeholder: "you@example.com", disabled: true, type: "email",
    })
    const field = wrapper.find("input")

    expect(field.attributes("id")).toBe("email")
    expect(field.attributes("aria-describedby")).toBe("email-said")
    expect(field.attributes("aria-invalid")).toBe("true")
    expect(field.attributes("placeholder")).toBe("you@example.com")
    expect(field.attributes("disabled")).toBeDefined()
    expect(field.classes()).toContain("island-input--wrong")
  })

  it("says nothing about being wrong when it is not", () => {
    const wrapper = input()

    expect(wrapper.find("input").attributes("aria-invalid")).toBeUndefined()
    expect(wrapper.find("input").classes()).not.toContain("island-input--wrong")
  })

  it("reports what is typed", async () => {
    const wrapper = input()

    await wrapper.find("input").setValue("Ada")

    expect(wrapper.emitted("update:modelValue")?.at(-1)?.[0]).toBe("Ada")
  })
})

describe("FormField", () => {
  const field = (props: Record<string, unknown> = {}) =>
    mount(FormField, {props: {label: "Email", ...props}, slots: {default: "<input>"}})

  it("stacks its label by default and lays it inside when asked", () => {
    expect(field().classes()).toContain("island-field--stacked")
    expect(field({variant: "inside"}).classes()).toContain("island-field--inside")
  })

  it("marks a field that holds an answer, so the label stays risen", () => {
    expect(field({variant: "inside", filled: true}).classes()).toContain("island-field--filled")
    expect(field({variant: "inside"}).classes()).not.toContain("island-field--filled")
  })

  it("draws one label, wherever it sits", () => {
    expect(field().findAll("label")).toHaveLength(1)
    expect(field({variant: "inside"}).findAll("label")).toHaveLength(1)
  })

  it("points the control at what says it, only when there is something to say", () => {
    const quiet = mount(FormField, {
      props: {label: "Email"},
      slots: {default: '<i :data-said="params.describedBy ?? \'none\'" />'},
    })
    const talking = mount(FormField, {
      props: {label: "Email", hint: "We will not share it."},
      slots: {default: '<i :data-said="params.describedBy ?? \'none\'" />'},
    })

    expect(quiet.find("i").attributes("data-said")).toBe("none")
    expect(talking.find("i").attributes("data-said"))
      .toBe(talking.find(".island-field__said").attributes("id"))
  })

  it("carries a name for a test to find it by", () => {
    expect(field({testid: "email-field"}).attributes("data-testid")).toBe("email-field")
  })
})

describe("CutButton", () => {
  const cut = (props: Record<string, unknown> = {}) =>
    mount(CutButton, {props, slots: {default: "Go"}, global: {stubs: {RouterLink: RouterLinkStub}}})

  it("presses as a button, leads as a link, and routes as a route", () => {
    expect(cut().element.tagName).toBe("BUTTON")
    expect(cut({href: "https://example.com"}).element.tagName).toBe("A")
    expect(cut({href: "/membership"}).findComponent(RouterLinkStub).exists()).toBe(true)
  })

  it("carries its name wherever it is drawn", () => {
    expect(cut({testid: "go"}).attributes("data-testid")).toBe("go")
    expect(cut({href: "/x", testid: "go"}).attributes("data-testid")).toBe("go")
    expect(cut({href: "https://x.test", testid: "go"}).attributes("data-testid")).toBe("go")
  })

  it("opens a new tab only for somewhere that is not the site", () => {
    expect(cut({href: "https://x.test", away: true}).attributes("rel")).toBe("noopener")
    expect(cut({href: "https://x.test"}).attributes("rel")).toBeUndefined()
  })
})

describe("PanChevron", () => {
  it("asks for a pan whichever way it points", async () => {
    for (const way of ["back", "on"] as const) {
      const wrapper = mount(PanChevron, {props: {way, label: `Show ${way}`}})

      await wrapper.trigger("click")

      expect(wrapper.emitted("pan")).toHaveLength(1)
      expect(wrapper.classes()).toContain(`pan-chevron--${way}`)
    }
  })

  it("carries a name where it is given one, and none where it is not", () => {
    expect(mount(PanChevron, {props: {way: "on", label: "On", testid: "pan-on"}})
      .attributes("data-testid")).toBe("pan-on")
    expect(mount(PanChevron, {props: {way: "on", label: "On"}})
      .attributes("data-testid")).toBeUndefined()
  })
})

describe("CheckBox", () => {
  it("says its small print where it has any", () => {
    const plain = mount(CheckBox, {props: {modelValue: false, label: "Newsletter"}})
    const noted = mount(CheckBox, {
      props: {modelValue: true, label: "Photographs", hint: "At association events."},
    })

    expect(plain.find(".island-check__hint").exists()).toBe(false)
    expect(noted.find(".island-check__hint").text()).toBe("At association events.")
    expect(noted.find("input").element.checked).toBe(true)
  })

  it("is off when the form is, and carries a name when it is given one", () => {
    const wrapper = mount(CheckBox, {
      props: {modelValue: false, label: "Newsletter", disabled: true, testid: "news"},
    })

    expect(wrapper.find("input").attributes("disabled")).toBeDefined()
    expect(wrapper.find("input").attributes("data-testid")).toBe("news")
  })
})

describe("RadioGroup", () => {
  const options = [{key: "yes", label: "Coming"}, {key: "no", label: "Not", hint: "We will hold it"}]

  it("says a hint where a choice has one, and none where it has not", () => {
    const wrapper = mount(RadioGroup, {props: {modelValue: "yes", options}})

    expect(wrapper.findAll(".island-radio__hint")).toHaveLength(1)
  })

  it("makes its own group name, and carries no testid where none was given", () => {
    const wrapper = mount(RadioGroup, {props: {modelValue: null, options}})

    expect(wrapper.find("input").attributes("name")).toMatch(/-radio$/)
    expect(wrapper.find("input").attributes("data-testid")).toBeUndefined()
  })

  it("is off when the form is", () => {
    const wrapper = mount(RadioGroup, {props: {modelValue: null, options, disabled: true}})

    expect(wrapper.find("input").attributes("disabled")).toBeDefined()
  })
})

describe("FileInput", () => {
  const paper = (name: string, type: string, size = 10) =>
    new File(["x".repeat(size)], name, {type})

  it("tells a picture, a pdf, a sheet, a page of text and anything else apart", () => {
    const shapes: Array<[File, boolean]> = [
      [paper("a.png", "image/png"), true],
      [paper("a.pdf", "application/pdf"), false],
      [paper("a.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"), false],
      [paper("a.md", "text/markdown"), false],
      [paper("a.bin", "application/octet-stream"), false],
    ]
    for (const [file, isImage] of shapes) {
      const wrapper = mount(FileInput, {props: {modelValue: file}})
      expect(wrapper.find("img.island-file__shot").exists()).toBe(isImage)
    }
  })

  it("reads a kind off the name where the browser gave none", () => {
    for (const name of ["rules.pdf", "members.csv", "notes.txt"]) {
      const wrapper = mount(FileInput, {props: {modelValue: paper(name, "")}})
      expect(wrapper.find(".island-file__mark").exists()).toBe(true)
    }
  })

  it("says how heavy it is, in the unit that suits it", () => {
    const small = mount(FileInput, {props: {modelValue: paper("a.pdf", "application/pdf", 500)}})
    const large = mount(FileInput, {
      props: {modelValue: paper("a.pdf", "application/pdf", 2 * 1024 * 1024)},
    })

    expect(small.find(".island-file__weight").text()).toBe("1 KB")
    expect(large.find(".island-file__weight").text()).toBe("2.0 MB")
  })

  it("asks for a file where it holds none, and is off when the form is", () => {
    const wrapper = mount(FileInput, {
      props: {modelValue: null, say: "Choose a poster", disabled: true, accept: "image/*",
        describedBy: "poster-said", invalid: true},
    })

    expect(wrapper.find(".island-file__name").text()).toBe("Choose a poster")
    expect(wrapper.find(".island-file__weight").text()).toBe("Pick one from this machine")
    expect(wrapper.find("input").attributes("disabled")).toBeDefined()
    expect(wrapper.find("input").attributes("accept")).toBe("image/*")
    expect(wrapper.find("input").attributes("aria-describedby")).toBe("poster-said")
    expect(wrapper.classes()).toContain("island-file--wrong")
  })

  it("takes the file the browser hands it, and nothing where the dialog was cancelled", async () => {
    const wrapper = mount(FileInput, {props: {modelValue: null}})
    const field = wrapper.find("input")

    Object.defineProperty(field.element, "files", {
      value: [paper("a.png", "image/png")], configurable: true,
    })
    await field.trigger("change")

    expect((wrapper.emitted("update:modelValue")?.at(-1)?.[0] as File).name).toBe("a.png")

    Object.defineProperty(field.element, "files", {value: [], configurable: true})
    await field.trigger("change")

    expect(wrapper.emitted("update:modelValue")?.at(-1)?.[0]).toBeNull()
  })
})

describe("CountryFlag", () => {
  it("names the artwork by the code, in either case, and takes the size it is given", () => {
    const wrapper = mount(CountryFlag, {props: {code: "nl", size: 30}})

    expect(wrapper.classes()).toContain("fi-nl")
    expect(wrapper.attributes("style")).toContain("width: 30px")
    expect(mount(CountryFlag, {props: {code: "DE"}}).classes()).toContain("fi-de")
  })
})
