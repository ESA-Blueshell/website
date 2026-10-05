<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import AnswersForm from "@/components/form/AnswersForm.vue"
import GuestForm from "@/components/form/GuestForm.vue"
import UserPicker from "@/components/form/fields/UserPicker.vue"
import CheckBox from "@/components/island/CheckBox.vue"
import CutButton from "@/components/island/CutButton.vue"
import ModalDialog from "@/components/island/ModalDialog.vue"
import NoticeBox from "@/components/island/NoticeBox.vue"
import SegmentedChoice from "@/components/island/SegmentedChoice.vue"
import {
  addSignUpAsBoard,
  type AnswerRequest,
  type CreateGuestRequest,
  type EventResponse,
  type EventSignUpResponse,
} from ".."

defineOptions({name: "AddSignUpDialog"})

const {modelValue, event} = defineProps<{
  modelValue: boolean
  event: EventResponse
}>()

const emit = defineEmits<{
  (e: "update:modelValue", value: boolean): void
  (e: "added", signUp: EventSignUpResponse): void
}>()

const HOLDERS = [
  {key: "account", label: "Account"},
  {key: "guest", label: "Guest"},
]

type GuestDetails = Required<Pick<CreateGuestRequest, "name" | "discord" | "email">> & {phoneNumber: string}

const noGuest = (): GuestDetails => ({name: "", discord: "", email: "", phoneNumber: ""})

const holder = ref("account")
const userId = ref<number | undefined>(undefined)
const guest = ref<GuestDetails>(noGuest())
const answers = ref<AnswerRequest[]>([])
// Silence is the default: a board member who wants the email asks for it.
const notify = ref(false)
const unpicked = ref("")
const failure = ref("")
const adding = ref(false)

const guestRef = ref<InstanceType<typeof GuestForm>>()
const answersRef = ref<InstanceType<typeof AnswersForm>>()

const survey = computed(() => event.signUpForm ?? null)
const hasQuestions = computed<boolean>(() => (survey.value?.questions ?? []).length > 0)
const forGuest = computed<boolean>(() => holder.value === "guest")

watch(() => modelValue, (open: boolean) => {
  if (!open) return
  holder.value = "account"
  userId.value = undefined
  guest.value = noGuest()
  answers.value = []
  notify.value = false
  unpicked.value = ""
  failure.value = ""
})

watch(userId, () => {
  unpicked.value = ""
})

async function valid(): Promise<boolean> {
  if (forGuest.value) {
    if (!(await guestRef.value?.validate?.())) return false
  } else if (userId.value == null) {
    unpicked.value = "Pick who the sign-up is for."
    return false
  }
  if (!hasQuestions.value) return true
  return !!(await answersRef.value?.validate?.())
}

async function add(): Promise<void> {
  failure.value = ""
  if (!(await valid())) return

  adding.value = true
  const done = await addSignUpAsBoard(
    event.id,
    {answers: answers.value, ...(forGuest.value ? {guest: guest.value} : {userId: userId.value})},
    forGuest.value && notify.value,
  )
  adding.value = false

  if (!done.ok) {
    failure.value = done.reason
    return
  }
  emit("added", done.saved)
  emit("update:modelValue", false)
}
</script>

<template>
  <modal-dialog
    cancel-testid="add-signup-cancel-btn"
    :open="modelValue"
    testid="add-signup-dialog"
    title="Add sign-up"
    @update:open="emit('update:modelValue', $event)"
  >
    <div class="add-signup">
      <!-- Members-only binds whoever attends, and a guest is never a member. -->
      <segmented-choice
        v-if="!event.membersOnly"
        v-model="holder"
        :options="HOLDERS"
        testid-prefix="add-signup-holder"
      />

      <template v-if="forGuest">
        <guest-form
          ref="guestRef"
          v-model="guest"
          force
        />
        <check-box
          v-model="notify"
          label="Email them about it"
          testid="add-signup-notify"
        />
      </template>
      <user-picker
        v-else
        v-model="userId"
        :error-messages="unpicked"
        label="Account"
        :members-only="event.membersOnly"
        required
        testid="add-signup-account"
      />

      <answers-form
        v-if="survey && hasQuestions"
        ref="answersRef"
        v-model="answers"
        :survey="survey"
      />

      <notice-box
        v-if="failure"
        testid="add-signup-failure"
        tone="danger"
      >
        {{ failure }}
      </notice-box>
    </div>

    <template #footer>
      <div class="add-signup__actions">
        <cut-button
          :disabled="adding"
          testid="add-signup-confirm-btn"
          tone="solid"
          @click="add"
        >
          {{ adding ? "Adding" : "Add" }}
        </cut-button>
      </div>
    </template>
  </modal-dialog>
</template>

<style scoped>
.add-signup {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.add-signup__actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.6rem;
  padding: 0.9rem 1.25rem 1.1rem;
  border-top: 1px solid var(--color-hairline);
}
</style>
