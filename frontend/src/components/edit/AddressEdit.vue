<template>
  <!-- Provide a VeeValidate form context (render as a div to avoid nested <form> tags) -->
  <Form
    ref="formRef"
    as="div"
  >
    <v-sheet
      class="pa-4"
      style="border-radius: 10px"
    >
      <strong>Address Information</strong><br>

      <v-row>
        <v-col cols="8">
          <Field
            v-slot="{ value, errors, handleChange, handleBlur }"
            v-model="localAddress.street"
            name="street"
            rules="required|min_chars:2"
          >
            <v-text-field
              :model-value="value"
              :error-messages="errors"
              label="Street"
              @update:model-value="handleChange"
              @blur="handleBlur"
            />
          </Field>
        </v-col>

        <v-col cols="4">
          <Field
            v-slot="{ value, errors, handleChange, handleBlur }"
            v-model="localAddress.houseNumber"
            name="houseNumber"
            rules="required|houseNumber"
          >
            <v-text-field
              :model-value="value"
              :error-messages="errors"
              label="House Number"
              @update:model-value="handleChange"
              @blur="handleBlur"
            />
          </Field>
        </v-col>
      </v-row>

      <v-row>
        <v-col cols="6">
          <Field
            v-slot="{ value, errors, handleChange, handleBlur }"
            v-model="localAddress.zipCode"
            name="zipCode"
            rules="required|zipByCountry:@country"
          >
            <v-text-field
              :model-value="value"
              :error-messages="errors"
              label="Zipcode"
              @update:model-value="handleChange"
              @blur="handleBlur"
            />
          </Field>
        </v-col>

        <v-col cols="6">
          <Field
            v-slot="{ value, errors, handleChange, handleBlur }"
            v-model="localAddress.city"
            name="city"
            rules="required|cityName|min_chars:2"
          >
            <v-text-field
              :model-value="value"
              :error-messages="errors"
              label="City"
              @update:model-value="handleChange"
              @blur="handleBlur"
            />
          </Field>
        </v-col>
      </v-row>

      <v-row>
        <v-col cols="12">
          <Field
            v-slot="{ value, errors, handleChange, handleBlur }"
            v-model="localAddress.country"
            name="country"
            rules="required"
          >
            <country-select
              :model-value="value"
              :error-messages="errors"
              label="Country"
              @update:model-value="handleChange"
              @blur="handleBlur"
            />
          </Field>
        </v-col>
      </v-row>
    </v-sheet>
  </Form>
</template>

<script lang="ts" setup>
import {ref, type Ref, watch} from "vue"
import type {Address} from "@/lib"
import {createAddress, updateAddress} from "@/lib"
import CountrySelect from "@/components/select/CountrySelect.vue"

import type {FormContext} from "vee-validate"
import {Field, Form, useForm} from "vee-validate"
import {useBackendValidation} from "@/plugins/serverValidation.ts"
import {$handleNetworkError} from "@/plugins/handleNetworkError.ts"

interface Props {
  modelValue: Address
}

interface Emits {
  (e: "update:modelValue", value: Address): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const localAddress: Ref<Address> = ref({...props.modelValue})

watch(
  () => props.modelValue,
  (val) => {
    if (JSON.stringify(val) !== JSON.stringify(localAddress.value)) {
      localAddress.value = {...val}
    }
  },
  {deep: true, immediate: true},
)

watch(
  localAddress,
  (val) => emit("update:modelValue", val),
  {deep: true},
)

const formRef = ref<FormContext>()
const {validate: vvValidate, resetForm} = useForm()
const {apply} = useBackendValidation()

const validateAddress = async (): Promise<boolean> => {
  const res = await vvValidate()
  return Boolean(res.valid)
}

const saveAddress = async (): Promise<void> => {
  const ok = await validateAddress()
  if (!ok) throw new Error("Address validation failed")

  try {
    let response: { data?: Address }
    if (localAddress.value.id) {
      response = await updateAddress({
        path: {id: localAddress.value.id!},
        body: localAddress.value,
        throwOnError: true,
      })
    } else {
      response = await createAddress({
        body: localAddress.value,
        throwOnError: true,
      })
    }

    if (response.data) {
      localAddress.value = response.data
      emit("update:modelValue", response.data)
      resetForm({values: {...response.data}})
    }
  } catch (err: unknown) {
    if (!apply(formRef.value!, err)) {
      $handleNetworkError(err)
    }
  }
}

const clearAddress = (): void => {
  const empty: Address = {
    id: undefined,
    userId: localAddress.value.userId, // preserve userId
    street: "",
    houseNumber: "",
    zipCode: "",
    city: "",
    country: "",
  }
  localAddress.value = empty
  resetForm({values: {...empty}})
}

defineExpose({
  saveAddress,
  validateAddress,
  clearAddress,
})
</script>

<style lang="scss" scoped>
.v-sheet {
  background: white;
}
</style>
