<template>
  <v-main>
    <top-banner title="Forgot Password" />

    <div
      class="mx-auto my-10"
      style="max-width: 600px"
    >
      <div
        class="island-panel"
      >
        <div
          v-if="!succeeded"
          data-testid="forgot-password-form-state"
        >
          <p>Enter your username, and we’ll email you a link to reset your password.</p>

          <notice-box
            v-if="failed"
            tone="warning"
            testid="forgot-password-failed-alert"
          >
            We could not send that just now, so no email is on its way. Please try again.
          </notice-box>

          <Form
            v-slot="{ meta }"
            as="form"
            data-testid="forgot-password-form"
            @submit="() => onSubmit()"
          >
            <VvField
              v-model="form.username"
              :component-props="{ label: 'Username', autocomplete: 'username', 'data-testid': 'forgot-password-username-field' }"
              name="username"
              rules="required"
            />

            <div class="form-save">
              <cut-button
                tone="solid"
                submit
                :disabled="!meta.valid || loading"
                data-testid="forgot-password-submit-btn"
              >
                Send reset mail
              </cut-button>
            </div>
          </Form>
        </div>

        <div
          v-else
          data-testid="forgot-password-success-state"
        >
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
import CutButton from "@/components/island/CutButton.vue"
import NoticeBox from "@/components/island/NoticeBox.vue"
import {onMounted, ref} from "vue"
import {useRoute} from "vue-router"
import TopBanner from "@/components/common/banners/TopBanner.vue"
import VvField from "@/components/form/fields/VvField.vue"
import {Form, useForm} from "vee-validate"
import {requestPasswordReset} from "@/domains/recovery"

const route = useRoute()
const loading = ref(false)
const succeeded = ref(false)
/** The request itself did not get through, which is not the same as an unknown username. */
const failed = ref(false)

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
  failed.value = false
  try {
    // Deliberately vague about whether the account exists — but only about that.
    // A server that could not take the request has not sent anything.
    await requestPasswordReset(form.value.username)
    succeeded.value = true
  } catch {
    failed.value = true
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
