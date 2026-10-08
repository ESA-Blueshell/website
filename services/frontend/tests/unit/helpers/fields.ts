import type {VueWrapper} from "@vue/test-utils"
import {settle} from "./testUtils"

/** Empties every island field of a form and leaves it, as somebody tabbing through clearing each would. */
export async function clearEveryField(wrapper: VueWrapper): Promise<void> {
  for (const field of wrapper.findAllComponents({name: "FormControl"})) {
    field.vm.$emit("update:modelValue", "")
    field.vm.$emit("blur")
  }
  await settle()
}

/** What each island field of a form says, by its label. */
export const saidByLabel = (wrapper: VueWrapper): Record<string, string[]> =>
  Object.fromEntries(wrapper.findAllComponents({name: "FormControl"})
    .map(field => [String(field.props("label")), [field.props("errorMessages")].flat() as string[]]))
