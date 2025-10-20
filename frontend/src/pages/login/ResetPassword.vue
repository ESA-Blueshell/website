<template>
  <!-- VeeValidate handles validation + form submit; Vuetify is purely UI -->
  <Form
    v-slot="{ meta }"
    as="form"
    class="mx-auto"
    style="max-width: 500px"
    @submit="onSubmit"
  >
    <Field
      v-slot="{ value, errors, handleBlur, handleChange }"
      name="password"
      rules="required|minChars:8|hasLower|hasUpper|hasNumber|hasSpecial"
    >
      <v-text-field
        :append-inner-icon="showPass ? 'mdi-eye' : 'mdi-eye-off'"
        :error-messages="errors"
        :model-value="value"
        :type="showPass ? 'text' : 'password'"
        autocomplete="new-password"
        label="New Password"
        @blur="handleBlur"
        @update:model-value="handleChange"
        @click:append-inner="showPass = !showPass"
      />
    </Field>

    <Field
      v-slot="{ value, errors, handleBlur, handleChange }"
      name="passwordAgain"
      rules="required|match:@password"
    >
      <v-text-field
        :append-inner-icon="showPass ? 'mdi-eye' : 'mdi-eye-off'"
        :error-messages="errors"
        :model-value="value"
        :type="showPass ? 'text' : 'password'"
        autocomplete="new-password"
        label="Repeat New Password"
        @blur="handleBlur"
        @update:model-value="handleChange"
        @click:append-inner="showPass = !showPass"
      />
    </Field>

    <v-row
      align="center"
      class="mt-2"
      justify="end"
    >
      <v-btn
        :disabled="!meta.valid || loading"
        :loading="loading"
        color="primary"
        type="submit"
      >
        Reset Password
      </v-btn>
    </v-row>

    <v-alert
      v-if="errorMessage"
      class="mt-4"
      type="error"
      variant="tonal"
    >
      {{ errorMessage }}
    </v-alert>

    <div
      v-if="succeeded"
      class="mt-6"
    >
      <p class="text-subtitle-1">
        Your password has been reset successfully.
        <RouterLink
          :to="{ name: 'login' }"
          class="text-decoration-none"
        >
          Sign in
        </RouterLink>
      </p>
    </div>
  </Form>
</template>

<script lang="ts" setup>
import {onMounted, ref} from "vue"
import {useRoute, useRouter} from "vue-router"
import {Field, Form, useForm} from "vee-validate"
import {type PasswordResetRequest, setPassword} from "@/services/api"
import {$handleNetworkError} from "@/plugins/handleNetworkError.ts"

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const succeeded = ref(false)
const showPass = ref(false)
const errorMessage = ref<string | null>(null)

const token = ref<string>("")
const username = ref<string>("")

const {handleSubmit} = useForm()

onMounted(() => {
  token.value = (route.query.token as string) || ""
  username.value = (route.query.username as string) || ""

  if (!token.value || !username.value) {
    router.replace({name: "home"})
    return
  }

  router.replace({name: "resetPassword", query: {username: username.value, token: token.value}})
})

const onSubmit = handleSubmit(async (values) => {
  loading.value = true
  errorMessage.value = null

  try {
    const payload: PasswordResetRequest = {
      token: token.value,
      username: username.value,
      password: values.password,
    }

    await setPassword({body: payload, throwOnError: true})
    succeeded.value = true
  } catch (e: unknown) {
    $handleNetworkError(e)
    errorMessage.value = "We couldn't reset your password. The link may be invalid or expired."
  } finally {
    loading.value = false
  }
})
</script>
