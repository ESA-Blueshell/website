import {computed, ref} from "vue"
import {useStore} from "vuex"
import type {CountryCode} from "libphonenumber-js/max"
import {useIsBoard} from "@/composables/useIsBoard"

export function useSaving() {
  const isSaving = ref(false)

  async function withSaving<T>(fn: () => Promise<T>): Promise<T> {
    isSaving.value = true
    try {
      return await fn()
    } finally {
      isSaving.value = false
    }
  }

  return {isSaving, withSaving}
}

export function useReadonly() {
  const store = useStore()
  const isLoggedIn = computed<boolean>(() => store.getters.isLoggedIn)
  const isBoard = useIsBoard()
  const isReadonly = computed<boolean>(() => isLoggedIn.value && !isBoard.value)
  return {store, isLoggedIn, isBoard, isReadonly}
}

export function useCountry(initial: CountryCode = "NL" as CountryCode) {
  const country = ref<CountryCode>(initial)
  const onCountryUpdate = (newCountry: string) => {
    country.value = newCountry as CountryCode
  }
  return {country, onCountryUpdate}
}

export type SubmitState = "idle" | "success" | "error"

export function useSubmitFeedback(timeoutMs = 1200) {
  const submitState = ref<SubmitState>("idle")
  const showSubmitStatus = ref(false)

  let timer: ReturnType<typeof setTimeout> | undefined

  function setSubmitResult(ok: boolean) {
    if (timer) clearTimeout(timer)

    submitState.value = ok ? "success" : "error"
    showSubmitStatus.value = true

    timer = setTimeout(() => {
      showSubmitStatus.value = false
      submitState.value = "idle"
    }, timeoutMs)
  }

  return {submitState, showSubmitStatus, setSubmitResult}
}
