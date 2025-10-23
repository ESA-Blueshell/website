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
          ref="formRef"
          as="form"
          @submit="onSubmit"
        >
          <v-row>
            <VvField
              v-model="form.username"
              :component-props="{ label: 'Username', autocomplete: 'username' }"
              name="username"
              rules="required|alphaNum"
            />
          </v-row>
          <v-row>
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
          </v-row>

          <v-row>
            <VvField
              v-model="passwordAgain"
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
          </v-row>


          <v-row>
            <Field
              v-slot="{ value, errors }"
              v-model="form.token"
              name="token"
              rules="required"
            >
              <v-text-field
                :model-value="value"
                disabled
                label="Reset token"
                name="token"
              />
              <br>
              <span class="v-field--error">
                {{ errors[0] }}
              </span>
            </Field>
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

          <v-alert
            v-if="succeeded"
            type="success"
            class="mb-2"
          >
            Account activated! You will be redirected to the login page.
          </v-alert>
        </Form>
      </v-card>
    </div>
  </v-main>
</template>

<script lang="ts" setup>
import {onMounted, ref} from "vue"
import {useRoute, useRouter} from "vue-router"
import {Field, Form, type FormContext, useForm} from "vee-validate"
import TopBanner from "@/components/common/banners/TopBanner.vue"
import VvField from "@/components/form/fields/VvField.vue"
import {memberActivate, type MemberActivationRequest} from "@/services/api"
import {$handleNetworkError} from "@/plugins/handleNetworkError.ts"
import {apply} from "@/plugins/validation.ts"

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const succeeded = ref(false)
const showPass = ref(false)
const errorMessage = ref<string | null>(null)

const form = ref<MemberActivationRequest>({
  username: "",
  password: "",
  token: "",
})

const passwordAgain = ref("")

const formRef = ref<FormContext>()

const {handleSubmit, validate} = useForm()

onMounted(() => {
  form.value.token = (route.query.token as string) || ""

  if (!form.value.token) {
    router.replace({name: "home"})
  }
})

function redirectToLogin(ms = 2000) {
  window.setTimeout(() => router.push({name: "login"}), ms)
}

const onSubmit = handleSubmit(async () => {
  if (!await validate()) return

  loading.value = true
  errorMessage.value = null

  try {
    await memberActivate({body: form.value, throwOnError: true})
    succeeded.value = true
    redirectToLogin(2500)
  } catch (e: unknown) {
    if (!formRef.value || !apply(formRef.value, e)) {
      $handleNetworkError(e)
      errorMessage.value = "We couldn’t activate your membership. The link may be invalid or expired."
    }
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
