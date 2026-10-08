import type {Directive} from "vue"

const FIELDS = "input:not([type=hidden]), textarea, select, [contenteditable=true]"
const TYPED = new Set(["text", "email", "number", "password", "search", "tel", "url", "date", "time", "datetime-local"])

/**
 * A field somebody types into. A picker opens its list on focus, as a date field opens its calendar, and
 * a tick box has nothing to type, so none of them counts.
 */
const typed = (field: HTMLElement): boolean => {
  if (field.matches(":disabled, [readonly], [role=combobox], [aria-haspopup]")) return false
  if (field instanceof HTMLInputElement) return TYPED.has(field.type)
  return field instanceof HTMLTextAreaElement || field.isContentEditable || field.getAttribute("contenteditable") === "true"
}

const inView = (field: HTMLElement): boolean => {
  const box = field.getBoundingClientRect()
  return box.top >= 0 && box.bottom <= window.innerHeight
}

/**
 * Puts the cursor in a form's first field, so it can be typed into as it opens. Only the first
 * field is looked at: where that is a picker or a tick box, nothing is focused, since the field
 * after it is not where the form starts. Nothing happens on a touch screen, where focus raises the
 * keyboard over the form, nor while somebody is typing somewhere else, nor for a field out of view,
 * since the page is never scrolled to it.
 */
export function focusFirstField(root: Element): boolean {
  if (window.matchMedia?.("(pointer: coarse)").matches) return false
  const first = root.querySelector<HTMLElement>(FIELDS)
  const active = document.activeElement
  if (!first || !typed(first) || !inView(first)) return false
  if (active instanceof HTMLElement && active !== first && active.matches(FIELDS) && typed(active)) return false
  first.focus({preventScroll: true})
  return true
}

/** `v-first-field` on a form: its first field takes the cursor when the form is put on the page. */
export const vFirstField: Directive<HTMLElement> = {
  mounted: (form) => void focusFirstField(form),
}
