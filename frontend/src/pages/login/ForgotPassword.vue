<template>
  <v-main>
    <top-banner title="Forgot Password" />

    <div
      class="mx-auto my-10"
      style="max-width: 600px"
    >
      <v-card class="pa-6">
        <div v-if="!succeeded">
          <p>Enter your username, and we’ll email you a link to reset your password.</p>

          <Form
            v-slot="{ meta }"
            as="form"
            @submit="onSubmit"
          >
            <v-row>
              <v-col cols="12">
                <VvField
                  v-model="form.username"
                  :component-props="{ label: 'Username', autocomplete: 'username' }"
                  name="username"
                  rules="required|alphaNum"
                />
              </v-col>
            </v-row>

            <v-row>
              <v-spacer />
              <v-col cols="auto">
                <v-btn
                  :disabled="!meta.valid || loading"
                  :loading="loading"
                  color="primary"
                  type="submit"
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
      </v-card>
    </div>
  </v-main>
</template>

<script lang="ts" setup>
import {onMounted, ref} from "vue"
import {useRoute} from "vue-router"
import TopBanner from "@/components/common/banners/TopBanner.vue"
import VvField from "@/components/form/fields/VvField.vue"
import {Form, useForm} from "vee-validate"
import {resetPassword} from "@/services/api"

const route = useRoute()
const loading = ref(false)
const succeeded = ref(false)

const form = ref({username: ""})
const {handleSubmit, setFieldValue} = useForm<{ username: string }>({
  initialValues: {username: ""},
})

onMounted(() => {
  const q = route.query.username
  if (typeof q === "string") {
    setFieldValue("username", q)
    form.value.username = q
  }
})

const onSubmit = handleSubmit(async () => {
  loading.value = true
  try {
    await resetPassword({path: {username: form.value.username}, throwOnError: false})
  } finally {
    loading.value = false
    succeeded.value = true
  }
})
</script>

<style lang="scss" scoped>
.v-card {
  border-radius: 12px;
}
</style>
