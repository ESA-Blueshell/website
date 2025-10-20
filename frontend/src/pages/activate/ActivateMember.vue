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
        :error-messages="errors"
        :model-value="value"
        autocomplete="username"
        label="Username"
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
        :append-inner-icon="showPass ? 'mdi-eye' : 'mdi-eye-off'"
        :error-messages="errors"
        :model-value="value"
        :type="showPass ? 'text' : 'password'"
        autocomplete="new-password"
        label="Password"
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
        label="Repeat Password"
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
        Activate Member
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
