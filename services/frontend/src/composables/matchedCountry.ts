import {computed, watch, type WritableComputedRef} from "vue"
import type {Country} from "world-countries"
import {findTopMatch, isValidCca2} from "@/composables/countries"

/**
 * The cca2 code behind a field's value. An older record holding a name or a lowercase code is
 * matched on the way in and the match written back, so the form never sends the original.
 */
export function useMatchedCountry(
  value: () => string | null | undefined,
  write: (code: string | null) => void,
  source: Country[],
): WritableComputedRef<string | null> {
  const matched = computed<string | null>(() => {
    const incoming = value()
    if (!incoming || !incoming.trim()) return null
    if (isValidCca2(incoming)) return incoming.toUpperCase()
    return findTopMatch(incoming, source)?.cca2 ?? null
  })

  // An unmatched value is left to the picker, which opens on the Netherlands instead.
  watch(matched, code => {
    if (code && code !== value()) write(code)
  }, {immediate: true})

  return computed<string | null>({get: () => matched.value, set: write})
}
