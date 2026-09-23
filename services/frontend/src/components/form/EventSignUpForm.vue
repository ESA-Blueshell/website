<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import {useStore} from "vuex"
import type {GuestSessionData} from "@/plugins/store.ts"
import {
  type AnswerRequest,
  changeOwnSignUp,
  type CreateEventSignUpRequest,
  type EventResponse,
  type EventSignUpResponse,
  type QuestionResponse,
  saveSignUpAsBoard,
  signUpForEvent,
  withdrawSignUp,
} from "@/domains/events"
import AnswersForm from "@/components/form/AnswersForm.vue"
import GuestForm from "@/components/form/GuestForm.vue"
import UserPicker from "@/components/form/fields/UserPicker.vue"
import CutButton from "@/components/island/CutButton.vue"
import NoticeBox from "@/components/island/NoticeBox.vue"
import {$handleNetworkError} from "@/plugins/handleNetworkError.ts"
import {useSaving, useSubmitFeedback} from "@/composables/formUtils"

const emit = defineEmits<{
  (e: "update:signUp", value: EventSignUpResponse): void
  (e: "delete:signUp", id: number): void
}>()

const props = defineProps<{
  event: EventResponse;
  buttonLoading?: boolean;
  initialSignUp?: EventSignUpResponse;
  /**
   * Board mode: edits the sign-up it is given rather than the caller's own, which is all the
   * self-service path can reach.
   */
  boardEdit?: boolean;
}>()

const store = useStore()
const isLoggedIn = computed<boolean>(() => store.getters.isLoggedIn)
const login = computed(() => store.getters.getLogin)

const survey = computed(() => props.event.signUpForm ?? null)
const hasQuestions = computed(() => (survey.value?.questions ?? []).length > 0)
const isGuestSignUp = computed<boolean>(() => props.initialSignUp?.guest != null)
/** Board mode edits the guest on the sign-up; otherwise the guest is whoever is filling this in. */
const editsGuestDetails = computed<boolean>(() => (props.boardEdit ? isGuestSignUp.value : !isLoggedIn.value))

const guest = ref(
  props.boardEdit && props.initialSignUp?.guest
    ? {
      name: props.initialSignUp.guest.name,
      discord: props.initialSignUp.guest.discord,
      email: props.initialSignUp.guest.email,
      phoneNumber: props.initialSignUp.guest.phoneNumber ?? "",
    }
    : store.getters.getGuestData ?? {name: "", discord: "", email: "", phoneNumber: ""},
)

const guestRef = ref<InstanceType<typeof GuestForm>>()
const answersRef = ref<InstanceType<typeof AnswersForm>>()

const answers = ref<AnswerRequest[]>((
  props.initialSignUp?.answers ?? []
).map((answer) => ({
  questionId: answer.questionId,
  textResponse: answer.textResponse,
  optionSelections: answer.optionSelections,
})))

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

const signUp = computed<EventSignUpResponse | undefined>(() => props.initialSignUp)
const isEditing = computed(() => !!signUp.value?.id)

const {isSaving, withSaving} = useSaving()
const {submitState, showSubmitStatus, setSubmitResult} = useSubmitFeedback()

/* The button says how the last press went for a moment, then what it does again. */
const submitSaid = computed<string>(() => {
  if (isSaving.value || props.buttonLoading) return "Saving"
  if (showSubmitStatus.value) return submitState.value === "success" ? "Saved" : "Check the form"
  if (props.boardEdit) return "Save changes"
  return isEditing.value ? "Update sign-up" : "Sign me up"
})

async function validate() {
  // A sign-up on its way to an account has no guest details left to check.
  if (editsGuestDetails.value && reassignTo.value == null) {
    const guestFormValid = await guestRef.value?.validate?.()
    if (!guestFormValid) return false
  }
  if (!hasQuestions.value) return true
  return answersRef.value?.validate?.()
}

async function save() {
  if (!(await validate())) {
    setSubmitResult(false)
    return
  }

  if (props.boardEdit) {
    await saveAsBoard()
    return
  }

  try {
    await withSaving(async () => {
      const payload: CreateEventSignUpRequest = {
        answers: answers.value,
      }
      if (isLoggedIn.value) {
        payload.userId = login.value.userId
      } else {
        payload.guest = {
          name: guest.value.name,
          discord: guest.value.discord,
          email: guest.value.email,
          phoneNumber: guest.value.phoneNumber,
        }
      }

      const eventId = props.event.id!
      const existingGuestToken = (store.getters.getGuestData as GuestSessionData | null)?.accessToken ?? null
      const saved = signUp.value?.id
        ? await changeOwnSignUp(
          eventId,
          {...payload, version: signUp.value.version},
          existingGuestToken,
        )
        : await signUpForEvent(eventId, payload)

      const eventSignUp = saved.signUp
      emit("update:signUp", eventSignUp)
      if (!isLoggedIn.value && eventSignUp.guest != null) {
        const guestAccessToken = saved.guestAccessToken ?? existingGuestToken
        if (guestAccessToken != null) {
          store.commit("saveGuestData", {
            ...eventSignUp.guest,
            accessToken: guestAccessToken,
          } satisfies GuestSessionData)
        }
      }
    })
    setSubmitResult(true)
  } catch (e) {
    setSubmitResult(false)
    $handleNetworkError(e)
  }
}

async function removeSignUp() {
  const existingSignUp = signUp.value
  if (!existingSignUp?.id) return

  try {
    await withSaving(async () => {
      const guestAccessToken = (store.getters.getGuestData as GuestSessionData | null)?.accessToken ?? null
      await withdrawSignUp(existingSignUp.id as number, guestAccessToken)
    })

    emit("delete:signUp", existingSignUp.id as number)
    setSubmitResult(true)
  } catch (e) {
    setSubmitResult(false)
    $handleNetworkError(e)
  }
}

const reassignTo = ref<number | undefined>(undefined)

/** The board-side save: the sign-up is named on the path, and its holder is left alone. */
async function saveAsBoard() {
  const target = signUp.value
  if (!target?.id) return

  try {
    await withSaving(async () => {
      const saved = await saveSignUpAsBoard(target.id, {
        answers: answers.value,
        version: target.version,
        ...(isGuestSignUp.value && reassignTo.value == null
          ? {
            guest: {
              name: guest.value.name,
              discord: guest.value.discord,
              email: guest.value.email,
              phoneNumber: guest.value.phoneNumber,
            },
          }
          : {}),
        ...(reassignTo.value != null ? {userId: reassignTo.value} : {}),
      })
      emit("update:signUp", saved)
    })
    setSubmitResult(true)
  } catch (e) {
    setSubmitResult(false)
    $handleNetworkError(e)
  }
}

defineExpose({save, validate})
</script>

<template>
  <div
    class="event-signup"
    data-testid="event-signup-form"
  >
    <template v-if="boardEdit && isGuestSignUp">
      <user-picker
        v-model="reassignTo"
        data-testid="signup-reassign-picker"
        label="Move this sign-up to an account"
        :members-only="event.membersOnly"
      />
    </template>

    <guest-form
      v-if="editsGuestDetails && reassignTo == null"
      ref="guestRef"
      v-model="guest"
      :force="boardEdit"
    />

    <answers-form
      v-if="survey && hasQuestions"
      :key="survey.questions.map((q: QuestionResponse) => q.id).join('')"
      ref="answersRef"
      v-model="answers"
      :survey="survey"
    />

    <notice-box
      v-if="!boardEdit"
      class="event-signup__consent"
    >
      By signing up to this event, you consent to share your name, username, email, Discord handle, phone number
      and your responses with members of the organising committee.
    </notice-box>

    <div class="event-signup__actions">
      <cut-button
        v-if="isEditing && !boardEdit"
        :disabled="isSaving || buttonLoading"
        testid="event-signup-delete-btn"
        tone="quiet"
        @click="removeSignUp"
      >
        Sign me out
      </cut-button>
      <cut-button
        :data-signup-mode="isEditing ? 'update' : 'create'"
        :disabled="isSaving || buttonLoading"
        testid="event-signup-submit-btn"
        tone="solid"
        @click="save"
      >
        {{ submitSaid }}
      </cut-button>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.event-signup {
  display: flex;
  flex-direction: column;
  gap: 1rem;

  &__actions {
    display: flex;
    flex-wrap: wrap;
    gap: 0.5rem;
    justify-content: flex-end;
    align-items: center;
    padding-top: 0.25rem;
  }
}
</style>
