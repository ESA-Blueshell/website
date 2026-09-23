import {computed, useAttrs, type ComputedRef} from "vue"

/**
 * What a test finds the field by: the `testid` prop, or the `data-testid` an older call site
 * writes as an attribute.
 */
export const useFieldName = (testid?: string): ComputedRef<string | undefined> => {
  const attrs = useAttrs()
  return computed(() => (attrs["data-testid"] as string | undefined) ?? testid)
}
