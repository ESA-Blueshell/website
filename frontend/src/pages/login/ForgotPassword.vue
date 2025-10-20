<template>
  <v-main>
    <top-banner title="Forgot Password" />

    <div class="mx-3">
      <div
        class="mx-auto mt-10"
        style="max-width: 500px"
      >
        <div v-if="!succeeded">
          <p>Enter your username, and we’ll email you a link to reset your password.</p>

          <Form
            v-slot="{ meta }"
            as="form"
            @submit="onSubmit"
          >
            <Field
              v-slot="{ value, errors, handleBlur, handleChange }"
              name="username"
              rules="required|alphaNum"
            >
              <v-text-field
                ref="usernameInput"
                :model-value="value"
                :error-messages="errors"
                label="Username"
                autocomplete="username"
                @blur="handleBlur"
                @update:model-value="handleChange"
              />
            </Field>

            <v-row>
              <v-spacer />
              <v-col cols="auto">
                <v-btn
                  type="submit"
                  :disabled="!meta.valid || loading"
                  :loading="loading"
                >
                  Send reset mail
                </v-btn>
              </v-col>
            </v-row>
          </Form>
        </div>

        <div v-else>
          <p>
            If an account with that username exists, you’ll receive an email with a password reset link.
            Didn’t get it? Check your spam folder or try again later.
          </p>
        </div>
      </div>
    </div>
  </v-main>
</template>

<script lang="ts" setup>
import {onMounted, ref} from "vue"
import {useRoute} from "vue-router"
import TopBanner from "@/components/common/banners/TopBanner.vue"
import {Field, Form, useForm} from "vee-validate"
import {resetPassword} from "@/services/api"
import {$handleNetworkError} from "@/plugins/handleNetworkError.ts"

const route = useRoute()
const loading = ref(false)
const succeeded = ref(false)
const usernameInput = ref<HTMLInputElement | null>(null)

const {setFieldValue, handleSubmit} = useForm<{ username: string }>({
  initialValues: {username: ""},
})

onMounted(() => {
  const q = route.query.username
  if (typeof q === "string") setFieldValue("username", q)
})

const onSubmit = handleSubmit(async (values) => {
  loading.value = true
  try {
    await resetPassword({query: {username: values.username}, throwOnError: false})
    succeeded.value = true
  } catch (e: unknown) {
    $handleNetworkError(e)
  } finally {
    loading.value = false
  }
})
</script>
