<script lang="ts" setup>
import {vFirstField} from "@/utils/firstField"
import FormFields from "@/components/island/FormFields.vue"
import {computed, reactive} from "vue"
import FormControl from "@/components/island/FormControl.vue"
import CountrySelect from "@/components/form/fields/CountrySelect.vue"
import SubmitButton from "@/components/form/SubmitButton.vue"
import {
  type AddressResponse,
  type CreateAddressRequest,
  saveAddressChange,
  saveNewAddress,
  saveSignupAddress,
  type UpdateAddressRequest,
} from "@/domains/user"
import {useSaving} from "@/composables/formUtils"
import {reportRefusal, useFormChecks} from "@/composables/useFormChecks"
import {minChars, required} from "@/utils/checks"
import {$showStatusMessage} from "@/plugins/handleNetworkError"
import type {PartialNullable} from "@/types/api"

type AddressModel = PartialNullable<Omit<CreateAddressRequest, "userId"> & AddressResponse>

const {showSubmit = false, submitText = "Submit", userId = 0, signupToken = undefined} = defineProps<{
  showSubmit?: boolean
  submitText?: string
  userId?: number
  /** Present during a signup: the address is saved on the token's own account. */
  signupToken?: string
}>()

const emit = defineEmits<{
  (e: "submitted", ok: boolean): void
}>()

// A default handed to an unbound v-model stays raw, so its checks would never see an edit.
const address = defineModel<AddressModel>({
  default: () => reactive({
    country: "NL",
    city: "",
    street: "",
    houseNumber: "",
    zipCode: "",
  }),
})

const isCreating = computed<boolean>(() => !address.value?.id)
const checks = useFormChecks(() => ({
  street: {value: () => address.value.street, checks: [required, minChars(2)]},
  houseNumber: {value: () => address.value.houseNumber, checks: [required]},
  zipCode: {value: () => address.value.zipCode, checks: [required, minChars(2)]},
  city: {value: () => address.value.city, checks: [required, minChars(2)]},
  country: {value: () => address.value.country, checks: [required]},
}))
const {errorsOf, touch} = checks
const validate = async (): Promise<boolean> => checks.attempt()
const {isSaving, withSaving} = useSaving()

const toCreateAddressRequest = (): CreateAddressRequest => ({
  city: address.value.city ?? "",
  country: address.value.country ?? "NL",
  houseNumber: address.value.houseNumber ?? "",
  street: address.value.street ?? "",
  userId,
  zipCode: address.value.zipCode ?? "",
})

const toSignupAddressRequest = () => ({
  city: address.value.city ?? "",
  country: address.value.country ?? "NL",
  houseNumber: address.value.houseNumber ?? "",
  street: address.value.street ?? "",
  zipCode: address.value.zipCode ?? "",
})

const toUpdateAddressRequest = (): UpdateAddressRequest => ({
  city: address.value.city ?? "",
  country: address.value.country ?? "NL",
  houseNumber: address.value.houseNumber ?? "",
  street: address.value.street ?? "",
  version: address.value.version ?? 0,
  zipCode: address.value.zipCode ?? "",
})

const save = async (): Promise<AddressModel | null> => {
  if (!(await validate())) {
    emit("submitted", false)
    return null
  }
  // An address belongs to somebody. Without a signup token and without an account
  // there is nobody to attach it to, and posting anyway spends the round trip to
  // be told so under a field name this form does not render.
  if (!signupToken && !userId && !address.value?.id) {
    emit("submitted", false)
    $showStatusMessage("your account is not ready for an address yet, so start again")
    return null
  }
  try {
    if (signupToken) {
      // The signup route answers 204 and upserts, so there is no id to track and
      // going back a step to correct the address just posts again.
      await withSaving(async () => await saveSignupAddress(signupToken, toSignupAddressRequest()))
      emit("submitted", true)
      return address.value
    }
    const resp = await withSaving(async () => {
      const hasId = Boolean(address.value?.id)
      return hasId
        ? await saveAddressChange(address.value.id!, toUpdateAddressRequest())
        : await saveNewAddress(toCreateAddressRequest())
    })
    address.value = resp
    emit("submitted", true)
    // Bound through v-model, address.value still reads the copy from before the save.
    return resp
  } catch (error: unknown) {
    reportRefusal(checks, error)
    emit("submitted", false)
    return null
  }
}

defineExpose({validate, save})
</script>

<template>
  <div v-first-field>
    <form-fields>
      <form-control
        v-model="address.street"
        :error-messages="errorsOf('street')"
        label="Street"
        @blur="touch('street')"
      />
      <form-control
        v-model="address.houseNumber"
        :error-messages="errorsOf('houseNumber')"
        label="House Number"
        @blur="touch('houseNumber')"
      />
    </form-fields>

    <form-fields>
      <form-control
        v-model="address.zipCode"
        :error-messages="errorsOf('zipCode')"
        label="Zipcode"
        @blur="touch('zipCode')"
      />
      <form-control
        v-model="address.city"
        :error-messages="errorsOf('city')"
        label="City"
        @blur="touch('city')"
      />
    </form-fields>

    <form-fields>
      <country-select
        v-model="address.country"
        :error-messages="errorsOf('country')"
        label="Country"
        @blur="touch('country')"
      />
    </form-fields>

    <div
      v-if="showSubmit"
      class="form-save"
    >
      <submit-button
        :disabled="isSaving"
        :loading="isSaving"
        :text="submitText"
        data-testid="address-form-submit-btn"
        :data-submit-mode="isCreating ? 'create' : 'update'"
        @click="save"
      />
    </div>
  </div>
</template>
<style lang="scss" scoped>
.form-save {
  display: flex;
  justify-content: flex-end;
  margin: 1.2rem 0 1.25rem;
}

.form-fields + .form-fields {
  margin-top: 0.5rem;
}
</style>
