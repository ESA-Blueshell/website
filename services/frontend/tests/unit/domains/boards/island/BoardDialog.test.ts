import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import {h} from "vue"
import BoardDialog from "@/domains/boards/island/BoardDialog.vue"

// The dialog portals its content out of the component's subtree, so it is replaced by a
// pass-through: what is under test is what the form puts inside it.
const stubs = {
  ModalDialog: {
    props: ["open"],
    setup: (_: unknown, {slots}: {slots: Record<string, () => unknown>}) =>
      () => h("div", [slots["default"]?.(), slots["footer"]?.()]),
  },
  ConfirmDialog: true,
  ImagePicker: true,
}

describe("BoardDialog, on the board's description", () => {
  it("is written in the markdown editor, named by the label above it", () => {
    const wrapper = mount(BoardDialog, {
      props: {open: true, board: null, nextNumber: 11},
      global: {stubs},
      attachTo: document.body,
    })

    const label = wrapper.findAll(".board-form__label").find(one => one.text() === "Description")
    const editor = wrapper.get("[data-testid='board-dialog-description'] .cm-content")

    expect(label?.attributes("id")).toBeTruthy()
    expect(editor.attributes("aria-labelledby")).toBe(label?.attributes("id"))
    wrapper.unmount()
  })
})
