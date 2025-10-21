<script lang="ts" setup>
import {computed, ref, type Ref} from "vue"
import {useStore} from "vuex"
import type {VForm} from "vuetify/lib/components"
import {type Answer, createEventSignup, type Event, type EventSignUp, updateEventSignUp} from "@/services/api"
import AnswersForm from "@/components/form/AnswersForm.vue"
import GuestForm from "@/components/form/GuestForm.vue"

interface Emits {
  (e: "update:signUp", value: EventSignUp): void;
}

const emit = defineEmits<Emits>()

interface Props {
  event: Event
  buttonLoading?: boolean
  initialSignUp?: EventSignUp
}

const props = defineProps<Props>()
const store = useStore()

const isLoggedIn = computed<boolean>(() => store.getters.isLoggedIn)
const login = computed(() => store.getters.getLogin)

const survey = computed(() => props.event.signUpForm ?? null)
const guest = ref(
  store.getters.getGuestData ?? {
    name: "",
    discord: "",
    email: "",
    phoneNumber: "",
  },
)

const guestRef: Ref<VForm | undefined> = ref()
const answersRef = ref<InstanceType<typeof AnswersForm>>()

const answers = ref<Answer[]>(
  props.initialSignUp?.answers ?? [],
)

const signUp = computed<EventSignUp>(() => {
  const s = props.initialSignUp
  return {
    id: s?.id,
    eventId: props.event.id!,
    userId: s?.userId,
    guest: s?.guest,
    answers: answers.value ?? [],
  }
})

async function validate() {
  console.log("VALIDATING")
  if (!isLoggedIn.value) {
    console.log("LOGGEDIN")
    const guestFormValid = await guestRef.value?.validate()
    if (!guestFormValid) return false
  }

  if (!survey.value) return true
  console.log("SURVEYING")

  console.log("ANSERSREF VALUE:", answersRef.value)
  return answersRef.value?.validate()
}

async function save() {
  console.log("SAVING!")
  if (!await validate()) return

  const payload: EventSignUp = {
    ...signUp.value,
    answers: answers.value ?? [],
  }

  if (isLoggedIn.value) {
    payload.userId = login.value.userId
  } else {
    payload.guest = guest.value ?? {}
  }

  let eventSignUp: EventSignUp

  if (isLoggedIn.value && payload.id) {
    const resp = await updateEventSignUp({
      path: {eventId: props.event.id!},
      body: payload,
      throwOnError: true,
    })
    eventSignUp = resp.data!
  } else {
    const resp = await createEventSignup({
      path: {eventId: props.event.id!},
      body: payload,
      throwOnError: true,
    })
    eventSignUp = resp.data!
  }

  emit("update:signUp", eventSignUp)
  if (!isLoggedIn.value) {
    store.commit("saveGuestData", eventSignUp.guest!)
  }
}

defineExpose({save, validate})
</script>

<template>
  <div>
    <guest-form
      v-if="!isLoggedIn"
      ref="guestRef"
      v-model="guest"
      class="mb-4"
    />

    <answers-form
      v-if="survey"
      ref="answersRef"
      v-model="answers"
      :survey="survey"
      class="mb-4"
    />

    <v-expand-transition
      class="mb-3"
    >
      <v-alert
        prominent
        type="warning"
        variant="outlined"
      >
        By signing up to this event, you consent to share your name, username, email, Discord handle, phone number,
        and your responses with members of the organizing committee.
      </v-alert>
    </v-expand-transition>

    <v-btn
      :block="true"
      :loading="buttonLoading"
      @click="save"
    >
      {{ signUp.id ? "Update" : "Save" }} sign-up form
    </v-btn>
  </div>
</template>

<style lang="scss" scoped>
.v-checkbox .v-selection-control {
  min-height: 40px !important;
}
</style>
