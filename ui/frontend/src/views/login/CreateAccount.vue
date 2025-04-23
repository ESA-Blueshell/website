<template>
  <v-main>
    <top-banner title="Create Account" />

    <div
      v-if="!succeeded"
      class="mx-3 pb-10"
    >
      <v-form
        ref="form"
        class="mx-auto mt-10"
        style="max-width: 600px"
      >
        <v-row>
          <v-col cols="4">
            <v-text-field
              ref="initials"
              v-model="form.initials"
              :rules="initialsRules"
              label="Initials"
            />
          </v-col>
          <v-col cols="8">
            <v-text-field
              ref="firstName"
              v-model="form.firstName"
              :rules="firstNameRules"
              label="First name"
            />
          </v-col>
        </v-row>
        <v-row class="mt-n7 mb-n5">
          <v-col cols="4">
            <v-text-field
              ref="prefix"
              v-model="form.prefix"
              label="Surname Prefix"
            />
          </v-col>
          <v-col cols="8">
            <v-text-field
              ref="lastName"
              v-model="form.lastName"
              :rules="lastNameRules"
              label="Surname"
            />
          </v-col>
        </v-row>
        <v-row>
          <v-col cols="12">
            <v-text-field
              ref="discord"
              v-model="form.discord"
              label="Discord Username"
              :rules="discordRules"
            />
          </v-col>
        </v-row>
        <v-row>
          <v-col cols="4">
            <v-text-field
              ref="username"
              v-model="form.username"
              :rules="usernameRules"
              label="Username"
            />
          </v-col>
          <v-col cols="8">
            <v-text-field
              ref="email"
              v-model="form.email"
              :rules="emailRules"
              label="Email"
              required
            />
          </v-col>
        </v-row>
        <v-row>
          <v-col cols="6">
            <v-text-field
              v-model="form.password"
              :rules="passwordRules"
              label="Password"
              :append-icon="showPass ? 'mdi-eye' : 'mdi-eye-off'"
              :type="showPass ? 'text' : 'password'"
              @click:append="showPass = !showPass"
            />
          </v-col>
          <v-col cols="6">
            <v-text-field
              v-model="form.passwordAgain"
              :rules="[ v => !!v || 'Password is required', v => v===form.password || 'The passwords should be the same' ]"
              label="Password (repeated)"
              :append-icon="showPass ? 'mdi-eye' : 'mdi-eye-off'"
              :type="showPass ? 'text' : 'password'"
              @click:append="showPass = !showPass"
            />
          </v-col>
        </v-row>
        <v-spacer />
        <v-col cols="auto">
          <v-btn
            :loading="clicked"
            color="primary"
            @click="createAccount"
          >
            Create account
          </v-btn>
        </v-col>
      </v-form>
    </div>


    <div
      v-else-if="succeeded"
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

<script>
import TopBanner from "@/components/banners/TopBanner.vue";
import {$handleNetworkError} from "@/plugins/handleNetworkError";
import {ref} from "vue";

export default {
  components: {TopBanner: TopBanner},
  setup() {
    const phone = ref('');
    return {phone};
  },
  data: () => ({
    clicked: false,
    succeeded: false,
    showPass: false,
    form: {
      username: null,
      initials: null,
      firstName: null,
      lastName: null,
      password: null,
      passwordAgain: null,
      email: null,
      discord: null,
    },
    usernameRules: [
      v => !!v || 'Username is required',
      v => /^[a-zA-Z0-9]+$/.test(v) || 'Username must only contain alphanumeric characters',
    ],
    initialsRules: [
      v => !!v || 'Initials are required',
    ],
    firstNameRules: [
      v => !!v || 'First name is required',
    ],
    lastNameRules: [
      v => !!v || 'Surname is required',
    ],
    passwordRules: [
      v => !!v || 'Password is required',
      (v) => v.length >= 8 || 'Password must be at least 8 characters',

    ],
    emailRules: [
      v => !!v || 'Email is required',
      v => (!!v && /^[a-zA-Z0-9_!#$%&’*+/=?`{|}~^.-]+@[a-zA-Z0-9.-]+$/.test(v)) || 'Enter a valid e-mail address',
      v => (!/student/i.test(v)) || 'You may not use your student email to sign up',
    ],
    discordRules: [
      v => !!v || 'Discord Username is required',
    ],
  }),
  methods: {
    async createAccount() {
      const {valid} = await this.$refs.form.validate()

      if (!valid) {
        return;
      }

      this.clicked = true

      // Send authenticate request
      this.$http.post('users/create', this.form)
        .then(() => {
          this.succeeded = true
        })
        .catch(e => {
            if (e.response?.status === 400) {
              this.$store.commit('setStatusSnackbarMessage', e.response.data)
            } else {
              $handleNetworkError(e)
            }
          }
        )
        .finally(() => {
          this.clicked = false
        })
    },
  }
}
</script>
