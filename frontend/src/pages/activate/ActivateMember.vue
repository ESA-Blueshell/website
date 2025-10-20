<template>
  <v-main>
    <top-banner title="Activate Member Account" />

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
            <v-col cols="12">
              <VvField
                v-model="form.username"
                :component-props="{ label: 'Username', autocomplete: 'username' }"
                name="username"
                rules="required|alphaNum"
              />
            </v-col>

            <v-col cols="12">
              <VvField
                v-model="form.password"
                :component-props="{
                  type: showPass ? 'text' : 'password',
                  'append-inner-icon': showPass ? 'mdi-eye' : 'mdi-eye-off',
                  label: 'Password',
                  autocomplete: 'new-password'
                }"
                name="password"
                rules="required|minChars:8|hasLower|hasUpper|hasNumber|hasSpecial"
                @click:append-inner="showPass = !showPass"
              />
            </v-col>

            <v-col cols="12">
              <VvField
                v-model="form.passwordAgain"
                :component-props="{
                  type: showPass ? 'text' : 'password',
                  'append-inner-icon': showPass ? 'mdi-eye' : 'mdi-eye-off',
                  label: 'Repeat Password',
                  autocomplete: 'new-password'
                }"
                name="passwordAgain"
                rules="required|match:@password"
                @click:append-inner="showPass = !showPass"
              />
            </v-col>
          </v-row>

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
import {memberActivate, type MemberActivationRequest} from "@/services/api"
import {$handleNetworkError} from "@/plugins/handleNetworkError.ts"

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const succeeded = ref(false)
const showPass = ref(false)
const token = ref<string>("")
const errorMessage = ref<string | null>(null)

const form = ref({
  username: "",
  password: "",
  passwordAgain: "",
})

const {handleSubmit} = useForm()

onMounted(() => {
  token.value = (route.query.token as string) || ""

  if (!token.value) {
    router.replace({name: "home"})
  }
})

const onSubmit = handleSubmit(async () => {
  loading.value = true
  errorMessage.value = null

  try {
    const payload: MemberActivationRequest = {
      token: token.value,
      username: form.value.username,
      password: form.value.password,
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

<style lang="scss" scoped>
.v-card {
  border-radius: 12px;
}
</style>
