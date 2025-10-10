<template>
  <!-- Use VeeValidate as the single source of truth (avoid nested <form>) -->
  <Form
    v-slot="{ meta }"
    as="div"
  >
    <v-form
      ref="formRef"
      v-model="vuetifyValid"
      class="mx-auto"
      style="max-width: 500px"
    >
      <Field
        v-slot="{ value, errors, handleChange, handleBlur }"
        v-model="form.password"
        name="password"
        rules="required|min_chars:8|has_lower|has_upper|has_number|has_special"
      >
        <v-text-field
          :append-inner-icon="showPass ? 'mdi-eye' : 'mdi-eye-off'"
          :error-messages="errors"
          :model-value="value"
          :type="showPass ? 'text' : 'password'"
          label="New Password"
          @blur="handleBlur"
          @click:append-inner="showPass = !showPass"
          @update:model-value="handleChange"
        />
      </Field>

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
          label="Repeat New Password"
          @blur="handleBlur"
          @click:append-inner="showPass = !showPass"
          @update:model-value="handleChange"
        />
      </Field>

      <v-row
        align="center"
        class="mt-2"
        justify="end"
      >
        <v-btn
          :disabled="!meta.valid"
          :loading="loading"
          color="primary"
          @click="submit"
        >
          Reset Password
        </v-btn>
      </v-row>

      <div
        v-if="succeeded"
        class="mt-6"
      >
        <p class="text-subtitle-1">
          Your password has been reset successfully.
        </p>
      </div>
    </v-form>
  </Form>
</template>

<script lang="ts" setup>
import {onMounted, ref} from "vue"
import type {VForm} from "vuetify/components"
import {useRoute, useRouter} from "vue-router"
import type {PasswordResetRequest} from "@/lib"
import {resetPassword} from "@/lib"
import {Field, Form, useForm} from "vee-validate"

const route = useRoute()
const router = useRouter()

const formRef = ref<VForm | null>(null)
const vuetifyValid = ref<boolean>(true) // keep Vuetify form happy; VeeValidate is source of truth

const loading = ref<boolean>(false)
const succeeded = ref<boolean>(false)
const showPass = ref<boolean>(false)
const token = ref<string>("")

const form = ref<PasswordResetRequest>({
  token: "",
  username: "",
  password: "",
})

const passwordAgain = ref<string>("")

const {validate: vvValidate} = useForm()

onMounted(() => {
  token.value = (route.query.token as string) || ""
  form.value.username = (route.query.username as string) || ""
  if (!token.value) {
    router.push("/")
    return
  }
  form.value.token = token.value
})

const submit = async () => {
  loading.value = true
  try {
    const {valid} = await vvValidate()
    if (!valid) return

    await resetPassword({body: form.value})
    succeeded.value = true
  } finally {
    loading.value = false
  }
}
</script>

<style lang="scss" scoped>
.v-row {
  background-color: #212121;
}

.rounded-t {
  border-top-left-radius: 8px;
  border-top-right-radius: 8px;
  overflow: hidden;
}

.rounded-b {
  border-bottom-left-radius: 8px;
  border-bottom-right-radius: 8px;
  overflow: hidden;
}
</style>
