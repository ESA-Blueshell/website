<template>
  <div>
    <v-form
      ref="form"
      v-model="valid"
    >
      <v-row>
        <v-col cols="4">
          <v-text-field
            v-model="userData.initials"
            :rules="initialsRules"
            :error-messages="err('initials')"
            label="Initials"
            @update:model-value="clear('initials')"
          />
        </v-col>
        <v-col cols="8">
          <v-text-field
            v-model="userData.firstName"
            :rules="firstNameRules"
            :error-messages="err('firstName')"
            label="First Name"
            @update:model-value="clear('firstName')"
          />
        </v-col>
      </v-row>

      <v-row>
        <v-col cols="4">
          <v-text-field
            v-model="userData.prefix"
            :error-messages="err('prefix')"
            label="SurPrefix"
            @update:model-value="clear('prefix')"
          />
        </v-col>
        <v-col cols="8">
          <v-text-field
            v-model="userData.lastName"
            :rules="lastNameRules"
            :error-messages="err('lastName')"
            label="Surname"
            @update:model-value="clear('lastName')"
          />
        </v-col>
      </v-row>

      <v-row>
        <v-col cols="6">
          <v-text-field
            v-model="userData.username"
            :rules="usernameRules"
            :error-messages="err('username')"
            label="Username"
            @update:model-value="clear('username')"
          />
        </v-col>

        <v-col cols="6">
          <v-text-field
            v-model="userData.discord"
            :rules="discordRules"
            :error-messages="err('discord')"
            label="Discord"
            @update:model-value="clear('discord')"
          />
        </v-col>
      </v-row>

      <v-row>
        <v-col cols="12">
          <v-text-field
            v-model="userData.email"
            :rules="emailRules"
            :error-messages="err('email')"
            label="E-mail"
            @update:model-value="clear('email')"
          />
        </v-col>
      </v-row>

      <v-row v-if="showPasswords">
        <v-col cols="6">
          <v-text-field
            v-model="userData.password"
            :append-inner-icon="showPass ? 'mdi-eye' : 'mdi-eye-off'"
            :rules="passwordRules"
            :type="showPass ? 'text' : 'password'"
            :error-messages="err('password')"
            label="Password"
            @click:append-inner="showPass = !showPass"
            @update:model-value="clear('password')"
          />
        </v-col>
        <v-col cols="6">
          <v-text-field
            v-model="passwordAgain"
            :append-inner-icon="showPass ? 'mdi-eye' : 'mdi-eye-off'"
            :rules="passwordConfirmRules"
            :type="showPass ? 'text' : 'password'"
            :error-messages="err('passwordAgain')"
            label="Password (repeated)"
            @click:append-inner="showPass = !showPass"
            @update:model-value="clear('passwordAgain')"
          />
        </v-col>
      </v-row>

      <v-row
        align="center"
        justify="space-evenly"
      >
        <v-col cols="auto">
          <v-checkbox
            v-model="userData.newsletter"
            :hide-details="true"
            label="Subscribe to newsletter"
          />
        </v-col>
      </v-row>
    </v-form>
  </div>
</template>

<script lang="ts" setup>
import {ref, type Ref, watch} from "vue"
import {type SimpleUser} from "@/lib"
import type {VForm} from "vuetify/components"

interface Props {
  editing?: boolean;
  modelValue: SimpleUser;
  showPasswords?: boolean;
}

interface Emits {
  (e: "update:modelValue", user: SimpleUser): void;
}

const props = withDefaults(defineProps<Props>(), {
  editing: false,
  showPasswords: true,
})

const emit = defineEmits<Emits>()

const userData: Ref<SimpleUser> = ref({...props.modelValue})
const valid: Ref<boolean> = ref(true)
const form: Ref<VForm | undefined> = ref()

const passwordAgain: Ref<string> = ref("")
const showPass: Ref<boolean> = ref(false)

watch(
  () => props.modelValue,
  (newVal) => {
    if (JSON.stringify(userData.value) !== JSON.stringify(newVal)) {
      userData.value = {...newVal}
    }
  },
  {deep: true, immediate: true},
)

watch(
  userData,
  (newVal) => {
    const cleanUserData: SimpleUser = {
      username: newVal.username,
      initials: newVal.initials,
      firstName: newVal.firstName,
      lastName: newVal.lastName,
      password: newVal.password,
      email: newVal.email,
      discord: newVal.discord,
      prefix: newVal.prefix,
      newsletter: newVal.newsletter,
    }
    emit("update:modelValue", cleanUserData)
  },
  {deep: true},
)

// ------- your existing rules unchanged -------
const usernameRules = [
  (v: string) => !!v || "Username is required",
  (v: string) => /^[a-zA-Z0-9]+$/.test(v) || "Username must only contain alphanumeric characters",
]
const passwordRules = [
  (v: string) => !!v || "Password is required",
  (v: string) => v.length >= 8 || "Password must be at least 8 characters",
  (v: string) => /(?=.*[a-z])/.test(v) || "Password must contain at least one lowercase letter",
  (v: string) => /(?=.*[A-Z])/.test(v) || "Password must contain at least one uppercase letter",
  (v: string) => /(?=.*\d)/.test(v) || "Password must contain at least one number",
  (v: string) => /(?=.*[@$!%*?&])/.test(v) || "Password must contain at least one special character (@$!%*?&)",
]
const passwordConfirmRules = [
  (v: string) => !!v || "Password confirmation is required",
  (v: string) => v === userData.value.password || "Passwords do not match",
]
const initialsRules = [(v: string) => !!v || "Initials are required"]
const firstNameRules = [(v: string) => !!v || "First name is required"]
const lastNameRules = [(v: string) => !!v || "Surname is required"]
const discordRules = [(v: string) => !!v || "Discord Username is required"]
const emailRules = [
  (v: string | undefined) => !!v || "Email is required",
  (v: string | undefined) => /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/.test(v || "") || "Enter a valid e-mail address",
  (v: string | undefined) => !/student/i.test(v || "") || "You may not use your student email to sign up",
]

const validateForm = async (): Promise<boolean> => {
  if (!form.value) return false
  const result = await form.value.validate()
  return result.valid
}

defineExpose({validateForm})
</script>

<style lang="scss">
.v-col:first-child {
  padding-left: 0;
}

.v-col:last-child {
  padding-right: 0;
}

.v-col {
  padding-bottom: 0;
  padding-top: 0;
}
</style>
