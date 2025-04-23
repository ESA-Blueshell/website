<template>
  <v-main>
    <top-banner title="Login" />

    <div class="mx-3">
      <v-form
        ref="form"
        v-model="valid"
        class="mx-auto mt-10"
        style="max-width: 500px"
        @submit.prevent
      >
        <v-text-field
          ref="username"
          v-model="username"
          :rules="usernameRules"
          label="Username"
          required
          @keydown.enter="login"
        />
        <v-text-field
          v-model="password"
          :rules="passwordRules"
          label="Password"
          required
          hide-details
          :append-icon="showPass ? 'mdi-eye' : 'mdi-eye-off'"
          :type="showPass ? 'text' : 'password'"
          @keydown.enter="login"
          @click:append="showPass = !showPass"
        />
        <v-row>
          <v-spacer />
          <v-col cols="auto">
            <v-btn
              variant="text"
              size="small"
              :to="`login/forgor?username=${username}`"
            >
              forgot password?
            </v-btn>
          </v-col>
        </v-row>
        <v-row>
          <v-col cols="auto">
            <v-btn
              variant="outlined"
              color="accent"
              to="account/create"
            >
              Create Account
            </v-btn>
          </v-col>
          <v-spacer />
          <v-col cols="auto">
            <v-btn
              :disabled="!valid"
              color="primary"
              @click="login"
            >
              Login
            </v-btn>
          </v-col>
        </v-row>
      </v-form>
    </div>
  </v-main>
</template>

<script>
import TopBanner from "@/components/banners/TopBanner.vue";
import {$handleNetworkError} from "@/plugins/handleNetworkError";

export default {
  components: {TopBanner: TopBanner},
  data: () => ({
    valid: false,
    loading: false,
    showPass: false,
    username: '',
    password: '',
    usernameRules: [
      v => !!v || 'Username is required',
    ],
    passwordRules: [
      v => !!v || 'Password is required',
    ],
  }),
  mounted() {
    if (!this.$store.getters.tokenExpired) {
      this.$router.push('/account')
    }
  },
  methods: {
    login() {
      // Check if form is valid (meaning username and password are not empty)
      if (this.$refs.form.validate()) {
        this.loading = true
        // Send authenticate request
        this.$http.post('authenticate', {username: this.username, password: this.password})
          .then(response => {
            // Store response
            this.$store.commit('setLogin', response.data)
            // Go to redirect page or home page
            this.$router.push(this.$route.query.redirect || '/')
          })
          .catch(e => {
            // Show Incorrect login snackbar
            if (e.response?.status === 401) {
              this.$store.commit('setStatusSnackbarMessage', 'Incorrect login credentials. Please double check your username and password.')
            } else {
              $handleNetworkError(e)
            }
          })
          .finally(() => {
            this.loading = false
          })
      }
    },
  }
}
</script>
