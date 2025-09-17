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
          ref="usernameField"
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

<script setup lang="ts">
import {onMounted, ref} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {useStore} from 'vuex'
import TopBanner from '@/components/banners/TopBanner.vue'
import {$handleNetworkError} from '@/plugins/handleNetworkError.js'
import {authenticate} from '@/lib'
import type {JwtRequest, JwtResponse} from '@/lib'
import type {State} from '@/plugins/store'

const router = useRouter()
const route = useRoute()
const store = useStore<State>()

const form = ref<any>(null)
const usernameField = ref<any>(null)
const username = ref<string>('')
const password = ref<string>('')
const valid = ref<boolean>(false)
const loading = ref<boolean>(false)
const showPass = ref<boolean>(false)

const usernameRules = [
  (v: string) => !!v || 'Username is required',
]

const passwordRules = [
  (v: string) => !!v || 'Password is required',
]

onMounted(() => {
  if (!store.getters.tokenExpired) {
    router.push('/account')
  }
})

const login = async () => {
  // Check if form is valid (meaning username and password are not empty)
  if (form.value && form.value.validate()) {
    loading.value = true

    try {
      const requestBody: JwtRequest = {
        username: username.value,
        password: password.value
      }

      const response = await authenticate<true>({
        body: requestBody,
        throwOnError: true
      })

      // Type the response data as JwtResponse
      const jwtResponse = response.data as JwtResponse

      // Store response (convert JwtResponse to Login type expected by store)
      const loginData = {
        userId: jwtResponse.userId!,
        username: jwtResponse.username!,
        roles: jwtResponse.roles!,
        token: jwtResponse.token!,
        expiration: jwtResponse.expiration!
      }

      store.commit('setLogin', loginData)

      // Go to redirect page or home page
      await router.push(route.query.redirect?.toString() || '/')
    } catch (e: any) {
      // Show Incorrect login snackbar
      if (e.response?.status === 401) {
        store.commit('setStatusSnackbarMessage', 'Incorrect login credentials. Please double check your username and password.')
      } else {
        $handleNetworkError(e)
      }
    } finally {
      loading.value = false
    }
  }
}
</script>
