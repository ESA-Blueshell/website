<template>
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
            v-model="address.street"
            name="street"
            rules="required|minChars:2"
          >
            <v-text-field
              :error-messages="errors"
              :model-value="value"
              label="Street"
              @blur="handleBlur"
              @update:model-value="handleChange"
            />
          </Field>
        </v-col>

        <v-col cols="4">
          <Field
            v-slot="{ value, errors, handleChange, handleBlur }"
            v-model="address.houseNumber"
            name="houseNumber"
            rules="required|houseNumber"
          >
            <v-text-field
              :error-messages="errors"
              :model-value="value"
              label="House Number"
              @blur="handleBlur"
              @update:model-value="handleChange"
            />
          </Field>
        </v-col>
      </v-row>

      <v-row>
        <v-col cols="6">
          <Field
            v-slot="{ value, errors, handleChange, handleBlur }"
            v-model="address.zipCode"
            name="zipCode"
            rules="required|zipByCountry:@country"
          >
            <v-text-field
              :error-messages="errors"
              :model-value="value"
              label="Zipcode"
              @blur="handleBlur"
              @update:model-value="handleChange"
            />
          </Field>
        </v-col>

        <v-col cols="6">
          <Field
            v-slot="{ value, errors, handleChange, handleBlur }"
            v-model="address.city"
            name="city"
            rules="required|cityName|minChars:2"
          >
            <v-text-field
              :error-messages="errors"
              :model-value="value"
              label="City"
              @blur="handleBlur"
              @update:model-value="handleChange"
            />
          </Field>
        </v-col>
      </v-row>

      <v-row>
        <v-col cols="12">
          <Field
            v-slot="{ value, errors, handleChange, handleBlur }"
            v-model="address.country"
            name="country"
            rules="required"
          >
            <country-select
              :error-messages="errors"
              :model-value="value"
              label="Country"
              @blur="handleBlur"
              @update:model-value="handleChange"
            />
          </Field>
        </v-col>
      </v-row>
    </v-sheet>
  </Form>
</template>

<script lang="ts" setup>
import {ref, type Ref, watch} from "vue"
import CountrySelect from "@/components/form/fields/CountrySelect.vue"
import {createAddress, updateAddress, type Address} from "@/services/api"
import {Field, Form, type FormContext, useForm} from "vee-validate"
import {useBackendValidation} from "@/plugins/serverValidation.ts"
import {$handleNetworkError} from "@/plugins/handleNetworkError.ts"

interface Props {
  modelValue: Address
}

type Emits = (e: "update:modelValue", value: Address) => void

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const address: Ref<Address> = ref({...props.modelValue})

watch(
  () => props.modelValue,
  (val) => {
    if (JSON.stringify(val) !== JSON.stringify(address.value)) {
      address.value = {...val}
    }
  },
  {deep: true, immediate: true},
)

watch(
  address,
  (val) => emit("update:modelValue", val),
  {deep: true},
)

const formRef = ref<FormContext>()
const {resetForm} = useForm()
const {apply} = useBackendValidation()

const validate = async (): Promise<boolean> => {
  const result = await formRef.value?.validate()
  return !!result?.valid
}

const save = async (): Promise<void> => {
  if (!(await validate())) throw new Error("Address validation failed")
  try {
    const resp = address.value.id
      ? await updateAddress({
        path: {id: address.value.id!},
        body: address.value,
        throwOnError: true,
      })
      : await createAddress({
        body: address.value,
        throwOnError: true,
      })

    if (resp?.data) {
      address.value = resp.data
      emit("update:modelValue", resp.data)
      resetForm({values: {...resp.data}})
    }
  } catch (err: unknown) {
    if (!apply(formRef.value!, err)) $handleNetworkError(err)
  }
}

const clear = (): void => {
  const empty: Address = {
    id: undefined,
    userId: address.value.userId,
    street: "",
    houseNumber: "",
    zipCode: "",
    city: "",
    country: "",
  }
  address.value = empty
  resetForm({values: {...empty}})
}

defineExpose({save, validate, clear})
</script>

<style lang="scss" scoped>
.v-sheet {
  background: white;
}
</style>
