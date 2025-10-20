<template>
  <Form
    v-slot="{ meta }"
    as="form"
    class="mx-auto"
    style="max-width: 500px"
    @submit="onSubmit"
  >
    <Field
      v-slot="{ value, errors, handleBlur, handleChange }"
      name="username"
      rules="required|alphaNum"
    >
      <v-text-field
        :model-value="value"
        :error-messages="errors"
        label="Username"
        autocomplete="username"
        @blur="handleBlur"
        @update:model-value="handleChange"
      />
    </Field>

    <Field
      v-slot="{ value, errors, handleBlur, handleChange }"
      name="password"
      rules="required|minChars:8|hasLower|hasUpper|hasNumber|hasSpecial"
    >
      <v-text-field
        :model-value="value"
        :type="showPass ? 'text' : 'password'"
        :append-inner-icon="showPass ? 'mdi-eye' : 'mdi-eye-off'"
        :error-messages="errors"
        label="Password"
        autocomplete="new-password"
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
        :model-value="value"
        :type="showPass ? 'text' : 'password'"
        :append-inner-icon="showPass ? 'mdi-eye' : 'mdi-eye-off'"
        :error-messages="errors"
        label="Repeat Password"
        autocomplete="new-password"
        @blur="handleBlur"
        @update:model-value="handleChange"
        @click:append-inner="showPass = !showPass"
      />
    </Field>

    <v-row
      class="mt-2"
      align="center"
      justify="end"
    >
      <v-btn
        type="submit"
        color="primary"
        :disabled="!meta.valid || loading"
        :loading="loading"
      >
        Activate Member
      </v-btn>
    </v-row>

    <v-alert
      v-if="errorMessage"
      type="error"
      variant="tonal"
      class="mt-4"
    >
      {{ errorMessage }}
    </v-alert>

    <div
      v-if="succeeded"
      class="mt-6"
    >
      <p class="text-subtitle-1">
        Membership activated! You can now log in.
      </p>
    </div>
  </Form>
</template>

<script lang="ts" setup>
import {onMounted, ref} from "vue"
import {useRoute, useRouter} from "vue-router"
import {Field, Form, useForm} from "vee-validate"
import {memberActivate, type MemberActivationRequest} from "@/services/api"
import {$handleNetworkError} from "@/plugins/handleNetworkError.ts"

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const succeeded = ref(false)
const showPass = ref(false)
const token = ref<string>("")
const errorMessage = ref<string | null>(null)

const {handleSubmit, setFieldValue} = useForm<{
  username: string
  password: string
  passwordAgain: string
}>({
  initialValues: {username: "", password: "", passwordAgain: ""},
})

onMounted(() => {
  token.value = (route.query.token as string) || ""
  const maybeUsername = (route.query.username as string) || ""
  if (maybeUsername) setFieldValue("username", maybeUsername)

  if (!token.value) {
    router.replace({name: "home"})
  }
})

const onSubmit = handleSubmit(async (values) => {
  loading.value = true
  errorMessage.value = null
  try {
    const payload: MemberActivationRequest = {
      token: token.value,
      username: values.username,
      password: values.password,
    }
    await memberActivate({body: payload, throwOnError: true})
    succeeded.value = true
  } catch (e: unknown) {
    $handleNetworkError(e)
    errorMessage.value = "We couldn’t activate your membership. The link may be invalid or expired."
  } finally {
    loading.value = false
  }
})
</script>
