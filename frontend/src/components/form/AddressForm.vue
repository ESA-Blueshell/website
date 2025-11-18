<script lang="ts" setup>
import {computed} from "vue"
import {Form} from "vee-validate"
import VvField from "@/components/form/fields/VvField.vue"
import CountrySelect from "@/components/form/fields/CountrySelect.vue"
import SubmitButton from "@/components/form/SubmitButton.vue"
import {type Address, createAddress, updateAddress} from "@/services/api"
import {handleSubmitError, useSaving, useSubmitFeedback, useVeeForm} from "@/composables/formUtils"

const {showSubmit = false, submitText = "Submit", userId = 0} = defineProps<{
  showSubmit?: boolean
  submitText?: string
  userId?: number
}>()

const emit = defineEmits<{
  (e: "submitted", ok: boolean): void
}>()

const address = defineModel<Address>({
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

const save = async (): Promise<Address | null> => {
  if (!(await validate())) {
    emit("submitted", false)
    setSubmitResult(false)
    return null
  }
  try {
    const resp = await withSaving(async () => {
      const hasId = Boolean(address.value?.id)
      return hasId
        ? await updateAddress({path: {id: address.value!.id!}, body: address.value!, throwOnError: true})
        : await createAddress({path: {userId}, body: address.value!, throwOnError: true})
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
    <v-sheet
      class="pa-4"
      style="border-radius: 10px"
    >
      <v-row class="tight-row">
        <v-col cols="8">
          <VvField
            v-model="address.street"
            label="Street"
            name="street"
            rules="required|minChars:2"
          />
        </v-col>
        <v-col cols="4">
          <VvField
            v-model="address.houseNumber"
            label="House Number"
            name="houseNumber"
            rules="required"
          />
        </v-col>
      </v-row>

      <v-row class="tight-row">
        <v-col cols="6">
          <VvField
            v-model="address.zipCode"
            label="Zipcode"
            name="zipCode"
            rules="required|minChars:2"
          />
        </v-col>
        <v-col cols="6">
          <VvField
            v-model="address.city"
            label="City"
            name="city"
            rules="required|minChars:2"
          />
        </v-col>
      </v-row>

      <v-row class="tight-row">
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
        justify="end"
        class="mt-2 tight-row"
      >
        <v-col cols="auto">
          <submit-button
            :disabled="isSaving"
            :loading="isSaving"
            :icon="isCreating ? 'mdi-content-save' : 'mdi-content-save-edit'"
            :text="submitText"
            :submit-state="submitState"
            :show-submit-status="showSubmitStatus"
            @click="save"
          />
        </v-col>
      </v-row>
    </v-sheet>
  </Form>
</template>
<style lang="scss" scoped>
</style>
