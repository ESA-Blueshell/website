<script lang="ts" setup>
import {computed, ref, type Ref} from "vue"
import {useStore} from "vuex"
import {Field, Form} from "vee-validate"
import type {VForm} from "vuetify/lib/components"
import {type Answer, createEventSignup, type Event, type EventSignUp, updateEventSignUp} from "@/lib"
import SurveyForm from "@/components/survey/SurveyForm.vue"

const emit = defineEmits(["submit"])

interface Props {
  event: Event
  showGuestForm: boolean
  buttonLoading?: boolean
  initialSignUp?: EventSignUp
}

const props = defineProps<Props>()
const store = useStore()

const isLoggedIn = computed<boolean>(() => store.getters.isLoggedIn)
const login = computed(() => store.getters.getLogin)

const guestData = ref(
  store.getters.getGuestData ?? {
    name: "",
    discord: "",
    email: "",
  },
)

const guestForm: Ref<VForm | undefined> = ref()

const eventSurvey = computed(() => props.event.signUpForm ?? null)

const answersData = ref<Answer[]>(
  props.initialSignUp?.answers ?? [],
)

const surveyFormRef = ref<InstanceType<typeof SurveyForm> | null>(null)

const signUp = computed<EventSignUp>(() => {
  const s = props.initialSignUp ?? {}
  return {
    id: s?.id,
    eventId: props.event.id!,
    userId: s?.userId,
    guest: s?.guest,
    answers: answersData.value ?? [],
  }
})

async function submit() {
  if (!isLoggedIn.value) {
    const guestFormValid = await guestForm.value?.validate()
    if (!guestFormValid) return
  }

  const answersValid = await surveyFormRef.value?.validate?.()
  if (!answersValid) return

  const payload: EventSignUp = {
    ...signUp.value,
    answers: answersData.value ?? [],
  }

  if (!isLoggedIn.value) {
    payload.guest = guestData.value ?? {}
  } else {
    payload.userId = login.value.userId
  }

  if (isLoggedIn.value && payload.id) {
    await updateEventSignUp({
      path: {eventId: props.event.id!},
      body: payload,
    })
  } else {
    await createEventSignup({
      path: {eventId: props.event.id!},
      body: payload,
    })
  }

  if (!isLoggedIn.value) {
    store.commit("saveGuestData", guestData.value)
  }

  emit("submit")
}
</script>

<template>
  <div>
    <!-- Guest form (optional + only when not logged in) -->
    <Form
      v-if="showGuestForm && !isLoggedIn"
      ref="guestForm"
      as="div"
      class="mb-4"
    >
      <v-alert
        class="mb-4"
        text="It seems you are not logged in. You can still sign up for this event, but we'll need some extra info from you."
        type="info"
        variant="outlined"
      />
      <Field
        v-slot="{ value, errors, handleChange, handleBlur }"
        v-model="guestData.name"
        name="name"
        rules="required"
      >
        <v-text-field
          :model-value="value"
          :error-messages="errors"
          label="Name"
          @update:model-value="handleChange"
          @blur="handleBlur"
        />
      </Field>

      <Field
        v-slot="{ value, errors, handleChange, handleBlur }"
        v-model="guestData.discord"
        name="discord"
        rules="required"
      >
        <v-text-field
          :model-value="value"
          :error-messages="errors"
          label="Discord username"
          @update:model-value="handleChange"
          @blur="handleBlur"
        />
      </Field>

      <Field
        v-slot="{ value, errors, handleChange, handleBlur }"
        v-model="guestData.email"
        name="email"
        rules="required|email|noStudentEmail"
      >
        <v-text-field
          :model-value="value"
          :error-messages="errors"
          hint="We'll use this to send you a link you can use to edit your sign-up form later"
          label="Email"
          @update:model-value="handleChange"
          @blur="handleBlur"
        />
      </Field>
    </Form>

    <!-- Answers / Survey -->
    <survey-form
      v-if="eventSurvey"
      ref="surveyFormRef"
      v-model="answersData"
      :survey="eventSurvey"
      class="mb-4"
    />

    <v-expand-transition
      v-if="answersData.length > 0"
      class="mb-3"
    >
      <v-alert
        prominent
        type="warning"
        variant="outlined"
      >
        By submitting this form, you consent to share your name, username, email, Discord handle, phone number,
        and your responses with members of the organizing committee.
      </v-alert>
    </v-expand-transition>

    <!-- Submit -->
    <v-btn
      :block="true"
      :loading="buttonLoading"
      @click="submit"
    >
      {{ signUp.id ? "Update" : "Save" }} sign-up form
    </v-btn>
  </div>
</template>

<style scoped lang="scss">
.v-checkbox .v-selection-control {
  min-height: 40px !important;
}
</style>
