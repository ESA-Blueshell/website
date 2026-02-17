<template>
  <v-main>
    <TopBanner title="Create Account" />

    <div
      v-if="!isSuccess"
      class="mx-auto pb-10 mt-10"
      style="max-width: 600px"
    >
      <user-form
        show-password
        show-submit
        submit-text="Create Account"
        @submitted="onFormSubmitted"
      />
    </div>

    <div
      v-else
      class="mx-auto my-10"
      style="max-width: 600px"
    >
      <p class="text-center text-subtitle-1 font-weight-light">
        Your account has successfully been created! Head over to your email to confirm your account.
        If you can't find the email, make sure to check your spam folder.
      </p>
    </div>
  </v-main>
</template>

<script lang="ts" setup>
import {ref} from "vue"
import TopBanner from "@/components/common/banners/TopBanner.vue"
import UserForm from "@/components/form/UserForm.vue"

const isSuccess = ref(false)

const onFormSubmitted = (ok: boolean) => {
  isSuccess.value = ok
}
</script>
