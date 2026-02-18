<template>
  <v-main>
    <top-banner title="Reset Password" />

    <div
      class="mx-auto my-10"
      style="max-width: 600px"
    >
      <v-card class="pa-6">
        <Form
          v-slot="{ meta }"
          as="form"
          @submit="onSubmit"
        >
          <v-row>
            <VvField
              v-model="form.password"
              :component-props="{
                type: showPass ? 'text' : 'password',
                'append-inner-icon': showPass ? 'mdi-eye' : 'mdi-eye-off',
                autocomplete: 'new-password',
                label: 'New Password',
                'onClick:append-inner': () => (showPass = !showPass)
              }"
              name="password"
              rules="required|minChars:8|hasLower|hasUpper|hasNumber|hasSpecial"
            />
          </v-row>
          <v-row>
            <VvField
              v-model="passwordAgain"
              :component-props="{
                type: showPass ? 'text' : 'password',
                'append-inner-icon': showPass ? 'mdi-eye' : 'mdi-eye-off',
                autocomplete: 'new-password',
                label: 'Repeat New Password',
                'onClick:append-inner': () => (showPass = !showPass)
              }"
              name="passwordAgain"
              rules="required|match:@password"
            />
          </v-row>

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
      </v-card>
    </div>
  </v-main>
</template>

<script lang="ts" setup>
import {onMounted, ref} from "vue"
import {useRoute, useRouter} from "vue-router"
import {Form, useForm} from "vee-validate"
import TopBanner from "@/components/common/banners/TopBanner.vue"
import VvField from "@/components/form/fields/VvField.vue"
import {type PasswordResetRequest, setPassword} from "@/services/api"
import {$handleNetworkError} from "@/plugins/handleNetworkError.ts"

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const succeeded = ref(false)
const showPass = ref(false)
const errorMessage = ref<string | null>(null)

const token = ref<string>("")
const passwordAgain = ref<string>("")

const form = ref<PasswordResetRequest>({
  password: "",
  token: "",
})

const {handleSubmit} = useForm()

onMounted(() => {
  const queryToken = (route.query.token as string) || ""
  form.value.token = queryToken
  token.value = queryToken

  if (!queryToken) {
    router.replace({name: "home"})
    return
  }

  router.replace({name: "resetPassword", query: {token: queryToken}})
})

const onSubmit = handleSubmit(async () => {
  loading.value = true
  errorMessage.value = null

  try {
    await setPassword({body: form.value, throwOnError: true})
    succeeded.value = true
  } catch (e: unknown) {
    $handleNetworkError(e)
    errorMessage.value = "We couldn't reset your password. The link may be invalid or expired."
  } finally {
    loading.value = false
  }
})
</script>

<style lang="scss" scoped>
.v-card {
  border-radius: 12px;
}
</style>
