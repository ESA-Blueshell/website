<script lang="ts" setup>
import {computed} from "vue"
import {Form} from "vee-validate"
import VvField from "@/components/form/fields/VvField.vue"
import CountrySelect from "@/components/form/fields/CountrySelect.vue"
import SubmitButton from "@/components/form/SubmitButton.vue"
import {
  type AddressResponse,
  type CreateAddressRequest,
  createAddress,
  saveAddress,
  type UpdateAddressRequest,
  updateAddress,
} from "@/services/api"
import {handleSubmitError, useSaving, useSubmitFeedback, useVeeForm} from "@/composables/formUtils"
import store from "@/plugins/store"
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
const {submitState, showSubmitStatus, setSubmitResult} = useSubmitFeedback()

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
    setSubmitResult(false)
    return null
  }
  // An address belongs to somebody. Without a signup token and without an account
  // there is nobody to attach it to, and posting anyway spends the round trip to
  // be told so under a field name this form does not render.
  if (!signupToken && !userId && !address.value?.id) {
    emit("submitted", false)
    setSubmitResult(false)
    store.commit("setStatusSnackbarMessage", "your account is not ready for an address yet, so start again")
    return null
  }
  try {
    if (signupToken) {
      // The signup route answers 204 and upserts, so there is no id to track and
      // going back a step to correct the address just posts again.
      await withSaving(async () => await saveAddress({
        headers: {"X-Signup-Token": signupToken},
        body: toSignupAddressRequest(),
        throwOnError: true,
      }))
      emit("submitted", true)
      setSubmitResult(true)
      return address.value
    }
    const resp = await withSaving(async () => {
      const hasId = Boolean(address.value?.id)
      return hasId
        ? await updateAddress({path: {id: address.value.id!}, body: toUpdateAddressRequest(), throwOnError: true})
        : await createAddress({body: toCreateAddressRequest(), throwOnError: true})
    })
    address.value = resp.data!
    emit("submitted", true)
    setSubmitResult(true)
    return address.value
  } catch (error: unknown) {
    handleSubmitError(formRef.value, error)
    emit("submitted", false)
    setSubmitResult(false)
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
    <v-row>
      <v-col
        cols="12"
        sm="8"
      >
        <VvField
          v-model="address.street"
          label="Street"
          name="street"
          rules="required|minChars:2"
        />
      </v-col>
      <v-col
        cols="12"
        sm="4"
      >
        <VvField
          v-model="address.houseNumber"
          label="House Number"
          name="houseNumber"
          rules="required"
        />
      </v-col>
    </v-row>

    <v-row>
      <v-col
        cols="12"
        sm="6"
      >
        <VvField
          v-model="address.zipCode"
          label="Zipcode"
          name="zipCode"
          rules="required|minChars:2"
        />
      </v-col>
      <v-col
        cols="12"
        sm="6"
      >
        <VvField
          v-model="address.city"
          label="City"
          name="city"
          rules="required|minChars:2"
        />
      </v-col>
    </v-row>

    <v-row>
      <v-col cols="12">
        <VvField
          v-model="address.country"
          :component="CountrySelect"
          label="Country"
          name="country"
          rules="required"
        />
      </v-col>
    </v-row>

    <v-row
      v-if="showSubmit"
      align="end"
      class="mt-2"
      justify="end"
    >
      <v-col cols="auto">
        <submit-button
          :disabled="isSaving"
          :icon="isCreating ? 'mdi-content-save' : 'mdi-content-save-edit'"
          :loading="isSaving"
          :show-submit-status="showSubmitStatus"
          :submit-state="submitState"
          :text="submitText"
          data-testid="address-form-submit-btn"
          :data-submit-mode="isCreating ? 'create' : 'update'"
          @click="save"
        />
      </v-col>
    </v-row>
  </Form>
</template>
<style lang="scss" scoped>
</style>
