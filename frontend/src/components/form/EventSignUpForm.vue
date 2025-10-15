<template>
  <div>
    <Form
      v-if="!isLoggedIn"
      ref="guestFormRef"
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
          :error-messages="errors"
          :model-value="value"
          label="Name"
          @blur="handleBlur"
          @update:model-value="handleChange"
        />
      </Field>

      <Field
        v-slot="{ value, errors, handleChange, handleBlur }"
        v-model="guestData.discord"
        name="discord"
        rules="required"
      >
        <v-text-field
          :error-messages="errors"
          :model-value="value"
          label="Discord username"
          @blur="handleBlur"
          @update:model-value="handleChange"
        />
      </Field>

      <Field
        v-slot="{ value, errors, handleChange, handleBlur }"
        v-model="guestData.email"
        name="email"
        rules="required|email|noStudentEmail"
      >
        <v-text-field
          :error-messages="errors"
          :model-value="value"
          label="Email"
          hint="We'll use this to send you a link you can use to edit your sign-up later"
          @blur="handleBlur"
          @update:model-value="handleChange"
        />
      </Field>
    </Form>

    <Form
      v-if="questions.length > 0"
      ref="answersFormRef"
      as="div"
    >
      <div
        v-for="(q, i) in questions"
        :key="q.id"
        class="mb-4"
      >
        <p :class="q.type === QuestionType.DESCRIPTION ? 'text-body-1' : 'text-h6 mb-0'">
          {{ q.prompt }}
        </p>

        <!-- OPEN -->
        <template v-if="q.type === QuestionType.OPEN">
          <Field
            v-slot="{ value, errors, handleChange, handleBlur }"
            v-model="answers[i]"
            :name="`answers[${i}]`"
            rules="required"
          >
            <v-text-field
              :error-messages="errors"
              :model-value="value"
              @blur="handleBlur"
              @update:model-value="handleChange"
            />
          </Field>
        </template>

        <template v-else-if="q.type === QuestionType.RADIO">
          <Field
            v-slot="{ value = [], errors, handleChange }"
            v-model="answers[i].optionsSelections"
            :name="`answers[${i}]`"
            :rules="(val: boolean[]) => (val && val.some(Boolean)) || 'Select one option'"
          >
            <div>
              <v-checkbox
                v-for="(option, j) in (q.options ?? q.choiceLabels ?? [])"
                :key="j"
                :label="option"
                :model-value="(value?.[j] ?? false)"
                :error-messages="errors"
                hide-details
                @update:model-value="(checked: boolean) => {
                  const len = (q.options ?? q.choiceLabels ?? []).length
                  const next = Array.isArray(value) ? Array.from({ length: len }, (_, k) => k === j ? checked : false) : Array(len).fill(false)
                  handleChange(next)
                }"
              />
            </div>
          </Field>
        </template>

        <template v-else-if="q.type === QuestionType.CHECKBOX">
          <Field
            v-slot="{ value = [], errors, handleChange }"
            v-model="answers[i].optionsSelections"
            :name="`answers[${i}]`"
            :rules="(val: boolean[]) => (val && val.some(Boolean)) || 'Select at least one option'"
          >
            <div>
              <v-checkbox
                v-for="(option, j) in (q.options ?? q.choiceLabels ?? [])"
                :key="j"
                :label="option"
                :model-value="(value?.[j] ?? false)"
                :error-messages="errors"
                hide-details
                @update:model-value="(checked: boolean) => {
                  const len = (q.options ?? q.choiceLabels ?? []).length
                  const next = Array.isArray(value) ? [...value] : Array(len).fill(false)
                  next[j] = checked
                  handleChange(next)
                }"
              />
            </div>
          </Field>
        </template>
      </div>
    </Form>

    <v-expand-transition
      v-if="answers.length > 0"
      class="mb-3"
    >
      <v-alert
        prominent
        type="warning"
        variant="outlined"
      >
        By submitting this form, you consent to share your name, username, email, Discord handle, phone number, and your
        responses with members of the organizing committee.
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
import {Field, Form, type FormContext} from "vee-validate"
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
import {useBackendValidation} from "@/plugins/serverValidation.ts"
import {$handleNetworkError} from "@/plugins/handleNetworkError.ts"

const emit = defineEmits(["submit"])

interface Props {
  event: Event
  buttonLoading?: boolean
  initialSignUp?: EventSignUp
}

const props = defineProps<Props>()
const store = useStore()
const {apply} = useBackendValidation()

const isLoggedIn = computed<boolean>(() => store.getters.isLoggedIn)
const questions = computed<Question[]>(() => props.event.signUpForm?.questions ?? [])

const answers = ref<Answer[]>(
  (props.initialSignUp?.answers as Answer[] | undefined) ??
  (questions.value.map((q) => {
    const optionsLen = (q.choiceLabels ?? []).length
    if (q.type === QuestionType.OPEN) return {questionId: q.id!, textResponse: ""}
    if (q.type === QuestionType.RADIO || q.type === QuestionType.CHECKBOX)
      return {questionId: q.id!, optionsSelections: Array(optionsLen).fill(false)}
    return {questionId: q.id!} as Answer
  }) as Answer[]),
)

const guestData = ref(store.getters.getGuestData ?? {name: "", discord: "", email: ""})

const signUp = computed<EventSignUp>(() => ({
  id: props.initialSignUp?.id,
  eventId: props.initialSignUp?.eventId ?? props.event.id!,
  answers: props.initialSignUp?.answers ?? [],
}))

const guestFormRef = ref<FormContext>()
const answersFormRef = ref<FormContext>()

const validate = async (): Promise<boolean> => {
  if (!isLoggedIn.value) {
    const guestValid = await guestFormRef.value?.validate()
    if (!guestValid?.valid) return false
  }
  const answersValid = await answersFormRef.value?.validate()
  return Boolean(answersValid?.valid)
}

const applyServerErrors = async (err: unknown): Promise<boolean> => {
  const results: boolean[] = []
  if (!isLoggedIn.value && guestFormRef.value) {
    results.push(apply(guestFormRef.value, err))
  }
  if (answersFormRef.value) {
    results.push(apply(answersFormRef.value, err))
  }
  return results.some(Boolean)
}

async function save() {
  if (!(await validate())) return

  const payload: EventSignUp = {
    ...signUp.value,
    answers: answers.value,
  }

  try {
    if (isLoggedIn.value) {
      if (payload.id) {
        await updateEventSignUp({path: {eventId: props.event.id!}, body: payload})
      } else {
        await createEventSignup({path: {eventId: props.event.id!}, body: {...payload, eventId: props.event.id!}})
      }
    } else {
      store.commit("saveGuestData", guestData.value)
      await createEventSignup({
        path: {eventId: props.event.id!},
        body: {...payload, guest: guestData.value, eventId: props.event.id!},
      })
    }

    emit("submit")
  } catch (e: unknown) {
    const handled = await applyServerErrors(e)
    if (!handled) {
      $handleNetworkError(e)
    }
  }
}

defineExpose({validate, save})
</script>


<style lang="scss" scoped>
.v-checkbox .v-selection-control {
  min-height: 40px !important;
}
</style>
