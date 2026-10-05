import {afterEach, describe, expect, it} from "vitest"
import {h} from "vue"
import {flushPromises, mount} from "@vue/test-utils"
import ModalDialog from "@/components/island/ModalDialog.vue"

// The dialog portals to the body, so what it draws is read off the document.
const open = (props: Record<string, unknown> = {}) =>
  mount(ModalDialog, {
    props: {open: true, title: "Delete Sitecie?", testid: "delete-dialog", ...props},
    slots: {default: () => h("p", "It goes for good."), footer: () => h("button", {"data-testid": "go"}, "Delete")},
    attachTo: document.body,
  })

describe("ModalDialog", () => {
  afterEach(() => {
    document.body.innerHTML = ""
  })

  it("starts every foot with a quiet Cancel that closes the dialog, before the dialog's own act", async () => {
    const wrapper = open()
    await flushPromises()

    const cancel = document.querySelector<HTMLButtonElement>("[data-testid=delete-dialog-cancel]")!
    expect(cancel.textContent?.trim()).toBe("Cancel")
    expect(cancel.compareDocumentPosition(document.querySelector("[data-testid=go]")!) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    cancel.click()
    expect(wrapper.emitted("update:open")).toEqual([[false]])
    wrapper.unmount()
  })

  it("takes the testid a dialog gives its Cancel, draws a removal in the danger colour, and leaves Cancel out on request", async () => {
    const named = open({cancelTestid: "keep-cancel", danger: true})
    await flushPromises()
    expect(document.querySelector("[data-testid=keep-cancel]")).not.toBeNull()
    expect(document.querySelector(".island-dialog")?.classList).toContain("island-dialog--danger")
    named.unmount()

    const bare = open({cancel: false})
    await flushPromises()
    expect(document.querySelector("[data-testid=delete-dialog-cancel]")).toBeNull()
    bare.unmount()
  })
})
