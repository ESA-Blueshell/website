import {ref} from "vue"
import type {Written} from "../adapters/accountSecurity"

/**
 * Runs a write that the api may refuse until the person proves it is them: the step-up dialog
 * opens, and the write runs again once they have. Any other refusal goes to [onRefused].
 */
export function useStepUp(onRefused: (reason: string) => void) {
  const open = ref(false)
  let pending: (() => void) | null = null

  const ask = (retry: () => void) => {
    pending = retry
    open.value = true
  }

  const attempt = async (write: () => Promise<Written<unknown>>): Promise<void> => {
    const result = await write()
    if (result.ok) return
    if (result.needsStepUp) ask(() => void attempt(write))
    else onRefused(result.reason)
  }

  const proved = () => {
    pending?.()
    pending = null
  }

  return {open, ask, attempt, proved}
}
