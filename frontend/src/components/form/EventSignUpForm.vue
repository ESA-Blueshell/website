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
      :key="survey.questions.map((q: Question) => q.id).join('')"
      ref="answersRef"
      v-model="answers"
      :survey="survey"
      class="mb-4"
    />

    <v-expand-transition class="mb-3">
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

<script lang="ts" setup>
import {computed, ref} from "vue"
import {useStore} from "vuex"
import {
  type Answer,
  createEventSignup,
  type Event,
  type EventSignUp,
  type Question,
  updateEventSignUp,
} from "@/services/api"
import AnswersForm from "@/components/form/AnswersForm.vue"
import GuestForm from "@/components/form/GuestForm.vue"
import {$handleNetworkError} from "@/plugins/handleNetworkError.ts"

const emit = defineEmits<{ (e: "update:signUp", value: EventSignUp): void }>()
const props = defineProps<{ event: Event; buttonLoading?: boolean; initialSignUp?: EventSignUp }>()

const store = useStore()
const isLoggedIn = computed<boolean>(() => store.getters.isLoggedIn)
const login = computed(() => store.getters.getLogin)

const survey = computed(() => props.event.signUpForm ?? null)
const guest = ref(store.getters.getGuestData ?? {name: "", discord: "", email: "", phoneNumber: ""})

const guestRef = ref<InstanceType<typeof GuestForm>>()
const answersRef = ref<InstanceType<typeof AnswersForm>>()

const answers = ref<Answer[]>(props.initialSignUp?.answers ?? [])

const signUp = computed<EventSignUp>(() => {
  const s = props.initialSignUp
  return {eventId: s?.eventId ?? props.event.id!, answers: answers.value ?? [], ...s}
})

async function validate() {
  if (!isLoggedIn.value) {
    const guestFormValid = await guestRef.value?.validate?.()
    if (!guestFormValid) return false
  }
  if (!survey.value) return true
  return answersRef.value?.validate?.()
}

async function save() {
  if (!(await validate())) return
  try {
    if (isLoggedIn.value) signUp.value.userId = login.value.userId
    else signUp.value.guest = guest.value ?? {}

    const eventId = props.event.id!

    if (answers.value) {
      signUp.value.answers = answers.value
    }

    const resp = signUp.value.id
      ? await updateEventSignUp({
        path: {eventId},
        query: {accessToken: signUp.value.guest?.accessToken},
        body: signUp.value,
        throwOnError: true,
      })
      : await createEventSignup({path: {eventId}, body: signUp.value, throwOnError: true})

    const eventSignUp = resp.data!
    emit("update:signUp", eventSignUp)
    if (!isLoggedIn.value) store.commit("saveGuestData", eventSignUp.guest!)
  } catch (e) {
    $handleNetworkError(e)
  }
}

defineExpose({save, validate})
</script>

<style lang="scss" scoped>
.v-checkbox .v-selection-control {
  min-height: 40px !important;
}
</style>
