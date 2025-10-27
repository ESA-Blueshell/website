import {computed, ref} from "vue"
import type {FormContext} from "vee-validate"
import {useStore} from "vuex"
import {apply} from "@/plugins/validation.ts"
import {$handleNetworkError} from "@/plugins/handleNetworkError.ts"
import type {CountryCode} from "libphonenumber-js/max"

export function useVeeForm() {
  const formRef = ref<FormContext>()
  const validate = async (): Promise<boolean> => {
    const result = await formRef.value?.validate()
    return !!result?.valid
  }
  return {formRef, validate}
}

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

export function handleSubmitError(formRef: FormContext | undefined, err: unknown) {
  if (!formRef || !apply(formRef, err)) {
    $handleNetworkError(err)
  }
}

export function useReadonly() {
  const store = useStore()
  const isLoggedIn = computed<boolean>(() => store.getters.isLoggedIn)
  const isBoard = computed<boolean>(() => store.getters.isBoard)
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

export function usePasswordToggle(defaultVisible = false) {
  const isPasswordVisible = ref<boolean>(defaultVisible)
  const passwordFieldProps = computed(() => ({
    type: isPasswordVisible.value ? "text" : "password",
    "append-inner-icon": isPasswordVisible.value ? "mdi-eye" : "mdi-eye-off",
    "click:append-inner": () => {
      isPasswordVisible.value = !isPasswordVisible.value
    },
  }))
  return {isPasswordVisible, passwordFieldProps}
}
