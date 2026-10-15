/** The dialog as a plain block that draws its content only while open, as Reka's does. */
export const ModalDialog = {
  name: "ModalDialog",
  props: {open: Boolean, title: String, testid: String, cancelTestid: String, wide: Boolean, cancel: {type: Boolean, default: true}},
  emits: ["update:open"],
  template: "<div v-if=\"open\" :data-testid=\"testid\"><slot /><slot name=\"footer\" /></div>",
}

/** A pointer event as jsdom can make one: it has no PointerEvent, so a mouse event named like one. */
export async function point(target: {element: Element}, type: string, clientX = 0, clientY = 0, pointerId = 1, extra: Record<string, unknown> = {}) {
  const event = new MouseEvent(type, {clientX, clientY, bubbles: true, cancelable: true})
  Object.defineProperty(event, "pointerId", {value: pointerId})
  for (const [key, value] of Object.entries(extra)) Object.defineProperty(event, key, {value})
  target.element.dispatchEvent(event)
  await Promise.resolve()
}
