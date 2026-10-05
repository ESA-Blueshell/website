<script lang="ts" setup>
import FormFields from "@/components/island/FormFields.vue"
import {computed} from "vue"
import {Form} from "vee-validate"
import VvField from "@/components/form/fields/VvField.vue"
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
import {handleSubmitError, useSaving, useVeeForm} from "@/composables/formUtils"
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

const address = defineModel<AddressModel>({
  default: () => ({
    country: "NL",
    city: "",
    street: "",
    houseNumber: "",
    zipCode: "",
  }),
})

const isCreating = computed<boolean>(() => !address.value?.id)
const {formRef, validate} = useVeeForm()
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
    handleSubmitError(formRef.value, error)
    emit("submitted", false)
    return null
  }
}

defineExpose({validate, save})
</script>

<template>
  <Form
    ref="formRef"
    as="div"
  >
    <form-fields>
      <VvField
        v-model="address.street"
        label="Street"
        name="street"
        rules="required|minChars:2"
      />
      <VvField
        v-model="address.houseNumber"
        label="House Number"
        name="houseNumber"
        rules="required"
      />
    </form-fields>

    <form-fields>
      <VvField
        v-model="address.zipCode"
        label="Zipcode"
        name="zipCode"
        rules="required|minChars:2"
      />
      <VvField
        v-model="address.city"
        label="City"
        name="city"
        rules="required|minChars:2"
      />
    </form-fields>

    <form-fields>
      <VvField
        v-model="address.country"
        :component="CountrySelect"
        label="Country"
        name="country"
        rules="required"
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
  </Form>
</template>
<style lang="scss" scoped>
/* The save button at the form's foot, at the right. */
.form-save {
  display: flex;
  justify-content: flex-end;
  margin: 1.2rem 0 1.25rem;
}

.form-fields + .form-fields {
  margin-top: 0.5rem;
}
</style>
