import {afterEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {defineComponent, h, nextTick, withDirectives} from "vue"
import ModalDialog from "@/components/island/ModalDialog.vue"
import {focusFirstField, vFirstField} from "@/utils/firstField"

const form = (inner: string): HTMLElement => {
  const root = document.createElement("form")
  root.innerHTML = inner
  document.body.append(root)
  return root
}

describe("the first field of a form", () => {
  afterEach(() => {
    document.body.innerHTML = ""
    vi.unstubAllGlobals()
  })

  it("takes the cursor when it is typed into", () => {
    const root = form('<input type="hidden"><input id="name" type="text"><input id="slug" type="text">')

    expect(focusFirstField(root)).toBe(true)
    expect(document.activeElement?.id).toBe("name")
  })

  it("counts a text area and an editor as typed into", () => {
    expect(focusFirstField(form('<textarea id="body"></textarea>'))).toBe(true)
    expect(document.activeElement?.id).toBe("body")
    ;(document.activeElement as HTMLElement).blur()
    expect(focusFirstField(form('<div id="editor" contenteditable="true" tabindex="0"></div>'))).toBe(true)
    expect(document.activeElement?.id).toBe("editor")
  })

  it("leaves a form that starts with a picker, a tick box or a field that cannot be changed alone", () => {
    for (const first of [
      '<input role="combobox" type="text">',
      '<input type="checkbox">',
      '<select><option>One</option></select>',
      '<input type="text" disabled>',
      '<input type="text" readonly>',
    ]) {
      expect(focusFirstField(form(`${first}<input class="second" type="text">`))).toBe(false)
    }
    expect(focusFirstField(form("<p>Nothing to fill in.</p>"))).toBe(false)
    expect(document.activeElement).toBe(document.body)
  })

  it("leaves a field that is out of view, so the page is never scrolled to it", () => {
    const root = form('<input id="far" type="text">')
    vi.spyOn(root.querySelector("input")!, "getBoundingClientRect").mockReturnValue({top: 4000, bottom: 4040} as DOMRect)

    expect(focusFirstField(root)).toBe(false)
  })

  it("leaves somebody who is typing somewhere else where they are, and does nothing on a touch screen", () => {
    const first = form('<input id="one" type="text">')
    const second = form('<input id="two" type="text">')
    focusFirstField(first)

    expect(focusFirstField(second)).toBe(false)
    expect(focusFirstField(first)).toBe(true)
    expect(document.activeElement?.id).toBe("one")

    ;(document.activeElement as HTMLElement).blur()
    vi.stubGlobal("matchMedia", vi.fn(() => ({matches: true})))
    expect(focusFirstField(first)).toBe(false)
    expect(document.activeElement).toBe(document.body)
  })

  it("is focused by the directive as the form is put on the page", () => {
    const Page = defineComponent({
      render: () => withDirectives(h("form", [h("input", {id: "title", type: "text"})]), [[vFirstField]]),
    })
    const wrapper = mount(Page, {attachTo: document.body})

    expect(document.activeElement?.id).toBe("title")
    wrapper.unmount()
  })

  it("is where a dialog opens, and a dialog without one keeps its own focus", async () => {
    const withForm = mount(ModalDialog, {
      props: {open: true, title: "New list"},
      slots: {default: () => h("input", {id: "list-name", type: "text"})},
      attachTo: document.body,
    })
    await nextTick()
    await nextTick()
    expect(document.activeElement?.id).toBe("list-name")
    withForm.unmount()

    const plain = mount(ModalDialog, {props: {open: true, title: "Sure?"}, slots: {default: () => h("p", "Nothing to fill in.")}, attachTo: document.body})
    await nextTick()
    await nextTick()
    expect(document.activeElement?.getAttribute("data-testid")).toBe("island-dialog-close")
    plain.unmount()
  })
})
