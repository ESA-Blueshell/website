<template>
  <v-main>
    <top-banner title="Confirm Your Account" />

    <div
      class="mx-auto my-10"
      style="max-width: 600px"
    >
      <v-card class="pa-6">
        <div
          v-if="!succeeded"
          data-testid="resend-confirmation-form-state"
        >
          <p>
            Enter your username, and we’ll email you a new link to confirm your address.
            You need it before you can sign in.
          </p>

          <Form
            v-slot="{ meta }"
            as="form"
            data-testid="resend-confirmation-form"
            @submit="onSubmit"
          >
            <v-row>
              <v-col cols="12">
                <VvField
                  v-model="form.username"
                  :component-props="{ label: 'Username', autocomplete: 'username', 'data-testid': 'resend-confirmation-username-field' }"
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
                  data-testid="resend-confirmation-submit-btn"
                  type="submit"
                >
                  Send confirmation mail
                </v-btn>
              </v-col>
            </v-row>
          </Form>
        </div>

        <div
          v-else
          data-testid="resend-confirmation-success-state"
        >
          <p>
            If an account with that username is still waiting to be confirmed, you’ll receive an
            email with a fresh link. Didn’t get it? Check your spam folder or try again later.
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
import {resendUserActivation} from "@/services/api"
import {$handleNetworkError} from "@/plugins/handleNetworkError"

/**
 * Asking for the confirmation link again, from outside the signup form.
 *
 * The form's own last step could already do this, and it was the only place that could:
 * an applicant who closed it and came back after the link expired had no way to ask for
 * another, and login answers a confirmed-looking wrong password either way.
 *
 * Says the same thing whether or not the account exists, like the password reset beside
 * it, so this cannot be used to find out who has an account.
 */
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
    await resendUserActivation({path: {username: form.value.username}, throwOnError: true})
  } catch (e) {
    // Whether the account exists, is already confirmed, or nothing was sent, this page
    // says the same thing — telling those apart is what would turn it into a way of
    // finding out who has an account. A refusal to send at all is about the caller and
    // not the account, so that one is worth saying, and claiming "sent" would be false.
    if (wasRateLimited(e)) {
      $handleNetworkError(e)
      return
    }
  } finally {
    loading.value = false
  }
  succeeded.value = true
})

function wasRateLimited(e: unknown): boolean {
  return (e as {response?: {status?: number}})?.response?.status === 429
}
</script>

<style lang="scss" scoped>
.v-card {
  border-radius: 12px;
}
</style>
