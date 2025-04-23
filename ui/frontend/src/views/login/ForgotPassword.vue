<template>
  <v-main>
    <top-banner title="Forgot Password" />


    <div class="mx-3">
      <div
        class="mx-auto mt-10"
        style="max-width: 500px"
      >
        <div v-if="!succeeded">
          <p>
            Enter your username, and we'll send you an email with a link to reset your password.
          </p>
          <v-form
            ref="form"
            v-model="valid"
            @submit.prevent
          >
            <v-text-field
              ref="username"
              v-model="username"
              :rules="[v => !!v || 'Username is required']"
              label="Username"
              @keydown.enter="sendResetMail"
            />
            <v-row>
              <v-spacer />
              <v-col cols="auto">
                <v-btn
                  :disabled="!valid"
                  :loading="loading"
                  @click="sendResetMail"
                >
                  Send reset mail
                </v-btn>
              </v-col>
            </v-row>
          </v-form>
        </div>
        <div v-else>
          <p>
            All right, you should get a mail with a link you can use to reset your password at the email address
            associated to your username. If you don't receive anything, please report it in the
            <a
              href="https://discord.com/channels/324285132133629963/1020245710987350047"
              target="_blank"
              class="text-decoration-none"
            >Sitecie suggestions channel on discord</a> and we'll help you out.
          </p>
        </div>
      </div>
    </div>
  </v-main>
</template>

<script>
import TopBanner from "@/components/banners/TopBanner.vue";
import {$handleNetworkError} from "@/plugins/handleNetworkError";

export default {
  components: {TopBanner: TopBanner},
  data: () => ({
    username: '',
    valid: false,
    succeeded: false,
    loading: false,
  }),
  mounted() {
    this.username = this.$route.query.username
  },
  methods: {
    async sendResetMail() {
      const {valid} = await this.$refs.form.validate()

      if (!valid) {
        return;
      }

      this.loading = true
      // Send reset request
      this.$http.delete(
        `users/password?username=${this.username}`
      ).then(() => {
        this.succeeded = true
      }).catch(e => {
        if (e.response?.status === 404) {
          this.$store.commit('setStatusSnackbarMessage', "Uhhh, we don't know that username... Maybe check the spelling?")
        } else {
          $handleNetworkError(e)
        }
      }).finally(() => {
        this.loading = false
      })
    },
  }
}
</script>
