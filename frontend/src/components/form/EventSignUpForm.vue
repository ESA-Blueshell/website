<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import {useStore} from "vuex"
import {
  type Answer,
  createEventSignup,
  deleteEventSignup,
  type Event,
  type EventSignUp,
  type Question,
  updateEventSignUp,
} from "@/services/api"
import AnswersForm from "@/components/form/AnswersForm.vue"
import GuestForm from "@/components/form/GuestForm.vue"
import SubmitButton from "@/components/form/SubmitButton.vue"
import {$handleNetworkError} from "@/plugins/handleNetworkError.ts"
import {useSaving, useSubmitFeedback} from "@/composables/formUtils"

const emit = defineEmits<{
  (e: "update:signUp", value: EventSignUp): void
  (e: "delete:signUp", id: number): void // ⬅️ new emit
}>()

const props = defineProps<{ event: Event; buttonLoading?: boolean; initialSignUp?: EventSignUp }>()

const store = useStore()
const isLoggedIn = computed<boolean>(() => store.getters.isLoggedIn)
const login = computed(() => store.getters.getLogin)

const survey = computed(() => props.event.signUpForm ?? null)
const guest = ref(store.getters.getGuestData ?? {name: "", discord: "", email: "", phoneNumber: ""})

const guestRef = ref<InstanceType<typeof GuestForm>>()
const answersRef = ref<InstanceType<typeof AnswersForm>>()

const answers = ref<Answer[]>([...(props.initialSignUp?.answers ?? [])])

function sortAnswersBySurveyIdx() {
  const qs = survey.value?.questions ?? []
  if (!qs.length || !answers.value.length) return

  const idxById = new Map<number, number>()
  for (const q of qs) {
    if (q.id != null) idxById.set(q.id, q.idx)
  }

  answers.value.sort((a, b) => {
    const ia = idxById.get(a.questionId) ?? Number.MAX_SAFE_INTEGER
    const ib = idxById.get(b.questionId) ?? Number.MAX_SAFE_INTEGER
    return ia - ib
  })
}

watch(survey, sortAnswersBySurveyIdx, {immediate: true})

const signUp = computed<EventSignUp>(() => {
  const s = props.initialSignUp
  return {eventId: s?.eventId ?? props.event.id!, answers: answers.value ?? [], ...s}
})

const {isSaving, withSaving} = useSaving()
const {submitState, showSubmitStatus, setSubmitResult} = useSubmitFeedback()

async function validate() {
  if (!isLoggedIn.value) {
    const guestFormValid = await guestRef.value?.validate?.()
    if (!guestFormValid) return false
  }
  if (!survey.value) return true
  return answersRef.value?.validate?.()
}

async function save() {
  if (!(await validate())) {
    setSubmitResult(false)
    return
  }

  try {
    await withSaving(async () => {
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
        : await createEventSignup({body: signUp.value, throwOnError: true})

      const eventSignUp = resp.data!
      emit("update:signUp", eventSignUp)
      if (!isLoggedIn.value) store.commit("saveGuestData", eventSignUp.guest!)
    })
    setSubmitResult(true)
  } catch (e) {
    setSubmitResult(false)
    $handleNetworkError(e)
  }
}

async function removeSignUp() {
  if (!signUp.value.id) return

  try {
    await withSaving(async () => {
      await deleteEventSignup({
        path: {eventSignupId: signUp.value.id as number},
        query: {accessToken: signUp.value.guest?.accessToken},
        throwOnError: true,
      })
    })

    emit("delete:signUp", signUp.value.id as number)
    setSubmitResult(true)
  } catch (e) {
    setSubmitResult(false)
    $handleNetworkError(e)
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
      :key="survey.questions.map((q: Question) => q.id).join('')"
      ref="answersRef"
      v-model="answers"
      :survey="survey"
      class="mb-4"
    />

    <v-expand-transition>
      <v-alert
        prominent
        type="warning"
        variant="outlined"
      >
        By signing up to this event, you consent to share your name, username, email, Discord handle, phone number,
        and your responses with members of the organizing committee.
      </v-alert>
    </v-expand-transition>

    <v-row
      class="mt-3 mb-0 ms-auto"
      justify="end"
    >
      <v-col
        v-if="signUp.id"
        cols="auto"
      >
        <submit-button
          :block="true"
          :disabled="isSaving || buttonLoading"
          :loading="isSaving || buttonLoading"
          :show-submit-status="showSubmitStatus"
          :submit-state="submitState"
          color="error"
          icon="mdi-account-multiple-remove"
          text="Delete sign-up"
          variant="plain"
          @click="removeSignUp"
        />
      </v-col>
      <v-col cols="auto">
        <submit-button
          :block="true"
          :disabled="isSaving || buttonLoading"
          :icon="signUp.id ? 'mdi-content-save-edit' : 'mdi-content-save'"
          :loading="isSaving || buttonLoading"
          :show-submit-status="showSubmitStatus"
          :submit-state="submitState"
          :text="`${signUp.id ? 'Update' : 'Save'} sign-up`"
          @click="save"
        />
      </v-col>
    </v-row>
  </div>
</template>

<style lang="scss" scoped>
.v-checkbox .v-selection-control {
  min-height: 40px !important;
}
</style>
