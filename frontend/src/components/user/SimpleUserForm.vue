<template>
  <div>
    <!-- Provide a VeeValidate form context (render as a div to avoid nested <form> tags) -->
    <Form
      ref="formRef"
      as="div"
    >
      <v-row>
        <v-col cols="4">
          <Field
            v-slot="{ value, errors, handleChange, handleBlur }"
            v-model="userForm.initials"
            name="initials"
            rules="required"
          >
            <v-text-field
              :error-messages="errors"
              :model-value="value"
              label="Initials"
              @blur="handleBlur"
              @update:model-value="handleChange"
            />
          </Field>
        </v-col>

        <v-col cols="8">
          <Field
            v-slot="{ value, errors, handleChange, handleBlur }"
            v-model="userForm.firstName"
            name="firstName"
            rules="required"
          >
            <v-text-field
              :error-messages="errors"
              :model-value="value"
              label="First Name"
              @blur="handleBlur"
              @update:model-value="handleChange"
            />
          </Field>
        </v-col>
      </v-row>

      <v-row>
        <v-col cols="4">
          <Field
            v-slot="{ value, handleChange, errors, handleBlur }"
            v-model="userForm.prefix"
            name="prefix"
          >
            <v-text-field
              :error-messages="errors"
              :model-value="value"
              label="SurPrefix"
              @blur="handleBlur"
              @update:model-value="handleChange"
            />
          </Field>
        </v-col>

        <v-col cols="8">
          <Field
            v-slot="{ value, errors, handleChange, handleBlur }"
            v-model="userForm.lastName"
            name="lastName"
            rules="required"
          >
            <v-text-field
              :error-messages="errors"
              :model-value="value"
              label="Surname"
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
            v-model="userForm.username"
            name="username"
            rules="required|alphaNum"
          >
            <v-text-field
              :error-messages="errors"
              :model-value="value"
              label="Username"
              @blur="handleBlur"
              @update:model-value="handleChange"
            />
          </Field>
        </v-col>

        <v-col cols="6">
          <Field
            v-slot="{ value, errors, handleChange, handleBlur }"
            v-model="userForm.discord"
            name="discord"
            rules="required"
          >
            <v-text-field
              :error-messages="errors"
              :model-value="value"
              label="Discord"
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
            v-model="userForm.email"
            name="email"
            rules="required|email|noStudentEmail"
          >
            <v-text-field
              :error-messages="errors"
              :model-value="value"
              label="E-mail"
              @blur="handleBlur"
              @update:model-value="handleChange"
            />
          </Field>
        </v-col>
      </v-row>

      <v-row v-if="showPasswords">
        <v-col cols="6">
          <Field
            v-slot="{ value, errors, handleChange, handleBlur }"
            v-model="userForm.password"
            name="password"
            rules="required|min_chars:8|max_chars:100|has_lower|has_upper|has_number|has_special"
          >
            <v-text-field
              :append-inner-icon="showPass ? 'mdi-eye' : 'mdi-eye-off'"
              :error-messages="errors"
              :model-value="value"
              :type="showPass ? 'text' : 'password'"
              label="Password"
              @blur="handleBlur"
              @click:append-inner="showPass = !showPass"
              @update:model-value="handleChange"
            />
          </Field>
        </v-col>

        <v-col cols="6">
          <Field
            v-slot="{ value, errors, handleChange, handleBlur }"
            v-model="passwordAgain"
            name="passwordAgain"
            rules="required|match:@password"
          >
            <v-text-field
              :append-inner-icon="showPass ? 'mdi-eye' : 'mdi-eye-off'"
              :error-messages="errors"
              :model-value="value"
              :type="showPass ? 'text' : 'password'"
              label="Password (repeated)"
              @blur="handleBlur"
              @click:append-inner="showPass = !showPass"
              @update:model-value="handleChange"
            />
          </Field>
        </v-col>
      </v-row>

      <v-row
        align="center"
        justify="space-evenly"
      >
        <v-col cols="auto">
          <Field
            v-slot="{ value, handleChange }"
            v-model="userForm.newsletter"
            name="newsletter"
          >
            <v-checkbox
              :hide-details="true"
              :model-value="value"
              label="Subscribe to newsletter"
              @update:model-value="handleChange"
            />
          </Field>
        </v-col>
      </v-row>
    </Form>
  </div>
</template>

<script lang="ts" setup>
import {ref, type Ref, watch} from "vue"
import {type SimpleUser} from "@/lib"
import type {FormContext} from "vee-validate"
import {Field, Form} from "vee-validate"
import {useBackendValidation} from "@/plugins/serverValidation.ts"

interface Props {
  editing?: boolean;
  modelValue: SimpleUser;
  showPasswords?: boolean;
}

interface Emits {
  (e: "update:modelValue", user: SimpleUser): void;
}

const props = withDefaults(defineProps<Props>(), {
  editing: false,
  showPasswords: true,
})

const emit = defineEmits<Emits>()

const userForm: Ref<SimpleUser> = ref({...props.modelValue})

const passwordAgain: Ref<string> = ref("")
const showPass: Ref<boolean> = ref(false)

watch(
  () => props.modelValue,
  (newVal) => {
    if (JSON.stringify(userForm.value) !== JSON.stringify(newVal)) {
      userForm.value = {...newVal}
    }
  },
  {deep: true, immediate: true},
)

const {apply} = useBackendValidation()

watch(
  userForm,
  (newVal) => {
    const cleanuserForm: SimpleUser = {
      username: newVal.username,
      initials: newVal.initials,
      firstName: newVal.firstName,
      lastName: newVal.lastName,
      password: newVal.password,
      email: newVal.email,
      discord: newVal.discord,
      prefix: newVal.prefix,
      newsletter: newVal.newsletter,
    }
    emit("update:modelValue", cleanuserForm)
  },
  {deep: true},
)

const formRef = ref<FormContext>()

const validateForm = async (): Promise<boolean> => {
  const result = await formRef.value?.validate()
  return !!result?.valid
}

const applyErrors = async (err: unknown): Promise<boolean> => {
  if (!formRef.value) return false
  return apply(formRef.value, err)
}

defineExpose({validateForm, applyErrors})
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
