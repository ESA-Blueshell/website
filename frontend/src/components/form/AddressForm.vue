<template>
  <Form
    ref="formRef"
    as="div"
  >
    <v-sheet
      class="pa-4"
      style="border-radius: 10px"
    >
      <v-row>
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

      <v-row>
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
          <v-btn
            :disabled="isSaving"
            :loading="isSaving"
            :prepend-icon="isCreating ? 'mdi-content-save' : 'mdi-content-save-edit'"
            size="large"
            type="button"
            @click="save"
          >
            {{ submitText }}
          </v-btn>
        </v-col>
      </v-row>
    </v-sheet>
  </Form>
</template>

<script lang="ts" setup>
import {computed, ref} from "vue"
import {Form, type FormContext} from "vee-validate"

import VvField from "@/components/form/fields/VvField.vue"
import CountrySelect from "@/components/form/fields/CountrySelect.vue"

import {type Address, createAddress, updateAddress} from "@/services/api"
import {useBackendValidation} from "@/plugins/serverValidation.ts"
import {$handleNetworkError} from "@/plugins/handleNetworkError.ts"

const {
  showSubmit = false,
  submitText = "Submit",
  userId = 0,
} = defineProps<{
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

const formRef = ref<FormContext>()
const isSaving = ref(false)
const isCreating = computed<boolean>(() => !address.value?.id)

const {apply} = useBackendValidation()

const validate = async (): Promise<boolean> => {
  const result = await formRef.value?.validate()
  return !!result?.valid
}

const save = async (): Promise<Address | null> => {
  if (!(await validate())) {
    emit("submitted", false)
    return null
  }

  isSaving.value = true
  try {
    const hasId = Boolean(address.value?.id)
    const resp = hasId
      ? await updateAddress({
        path: {id: address.value!.id!},
        body: address.value!,
        throwOnError: true,
      })
      : await createAddress({
        path: {userId},
        body: address.value!,
        throwOnError: true,
      })

    address.value = resp.data!
    emit("submitted", true)
    return resp.data!
  } catch (error: unknown) {
    if (!formRef.value || !apply(formRef.value, error)) {
      $handleNetworkError(error)
    }
    emit("submitted", false)
    return null
  } finally {
    isSaving.value = false
  }
}

defineExpose({validate, save})
</script>

<style lang="scss">
.v-col:first-child {
  padding-left: 0;
}

.v-col:last-child {
  padding-right: 0;
}

.v-col {
  padding-bottom: 0;
  padding-top: 0;
}
</style>
