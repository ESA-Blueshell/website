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
      :key="questionsShapeKey"
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
import {computed, ref, watch} from "vue"
import {useStore} from "vuex"
import {
  type Answer,
  createEventSignup,
  type Event,
  type EventSignUp,
  type Question,
  QuestionType,
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
const questions = computed<Question[]>(() => survey.value?.questions ?? [])

const guest = ref(store.getters.getGuestData ?? {name: "", discord: "", email: "", phoneNumber: ""})

const guestRef = ref<InstanceType<typeof GuestForm>>()
const answersRef = ref<InstanceType<typeof AnswersForm>>()

const answers = ref<Answer[]>(props.initialSignUp?.answers ?? [])

const signUp = computed<EventSignUp>(() => {
  const s = props.initialSignUp
  return {eventId: s?.eventId ?? props.event.id!, answers: answers.value ?? [], ...s}
})

function normalizeForQuestion(q: Question, a: Answer | undefined): Answer {
  const next: Answer = {...(a ?? {}), questionId: q.id!}
  if (q.type === QuestionType.OPEN) {
    next.textResponse = typeof next.textResponse === "string" ? next.textResponse : ""
    delete next.optionSelections
  } else if (q.type === QuestionType.RADIO || q.type === QuestionType.CHECKBOX) {
    const need = q.choiceLabels?.length ?? 0
    const curr = Array.isArray(next.optionSelections) ? next.optionSelections.slice() : []
    while (curr.length < need) curr.push(false)
    if (curr.length > need) curr.length = need
    next.optionSelections = curr
    delete next.textResponse
  }
  return next
}

function alignAnswers(qs: Question[], base: Answer[]): Answer[] {
  const byId = new Map(base.map(a => [a.questionId, a] as const))
  const out: Answer[] = []
  for (const q of qs) {
    if (q.type === QuestionType.DESCRIPTION) continue
    out.push(normalizeForQuestion(q, byId.get(q.id!)))
  }
  return out
}

watch(
  [questions, () => props.initialSignUp?.answers],
  ([qs, incoming]) => {
    const base = (incoming ?? answers.value) ?? []
    answers.value = alignAnswers(qs ?? [], base)
  },
  {immediate: true, deep: true},
)

const questionsShapeKey = computed(() =>
  (questions.value ?? [])
    .map(q => `${q.id}:${q.type}:${q.choiceLabels?.length ?? 0}`)
    .join("|"),
)

async function validate() {
  if (!isLoggedIn.value) {
    const guestFormValid = await guestRef.value?.validate?.()
    if (!guestFormValid) return false
  }
  if (!survey.value) return true
  return (await answersRef.value?.validate?.()) === true
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
