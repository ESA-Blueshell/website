import {h} from "vue"

/** The page shell, the previews and the pickers as pass-throughs: what is under test is the form. */
const passThrough = (name: string, props: string[] = []) => ({
  name, props,
  setup: (_: unknown, {slots}: {slots: Record<string, () => unknown>}) =>
    () => h("div", [slots["actions"]?.(), slots["default"]?.(), slots["footer"]?.(), slots["preview"]?.()]),
})

export const editorStubs = {
  EditPage: passThrough("EditPage", ["title", "eyebrow", "back", "testid", "accent"]),
  PreviewFrame: passThrough("PreviewFrame"),
  Timeline: {name: "Timeline", props: ["stops", "selectedId", "accent"], template: "<div />"},
  BoardBand: {name: "BoardBand", props: ["cheer", "description", "eyebrow", "label", "name", "photo"], template: "<div />"},
  SliceBand: {name: "SliceBand", props: ["items", "openId"], template: "<div><slot name=\"details\" v-for=\"item in items\" :item=\"item\" /></div>"},
  ImagePicker: {name: "ImagePicker", props: ["picture", "store", "testid", "shape"], emits: ["update:picture"], template: "<div />"},
  SearchPicker: {name: "SearchPicker", props: ["options", "emptyNote", "testidPrefix"], emits: ["pick"], template: "<div />"},
  ConfirmDialog: {name: "ConfirmDialog", props: ["open", "question", "failure", "working", "testid"], emits: ["confirm", "update:open"], template: "<div />"},
  MarkdownEditor: {name: "MarkdownEditor", props: ["modelValue", "maxLength"], emits: ["update:modelValue"], template: "<div />"},
  CutButton: {props: ["href", "testid", "disabled"], emits: ["click"], template: "<button :data-testid='testid' :data-disabled='String(Boolean(disabled))' @click=\"$emit('click')\"><slot /></button>"},
}

/** Types into the island field under [testid], as its control reports what was typed. */
export const write = (wrapper: {findAllComponents: (selector: {name: string}) => Array<{attributes: (key: string) => string | undefined, vm: {$emit: (event: string, value: unknown) => void}}>}, testid: string, value: string) =>
  wrapper.findAllComponents({name: "FormControl"}).find(one => one.attributes("data-testid") === testid)!.vm.$emit("update:modelValue", value)
