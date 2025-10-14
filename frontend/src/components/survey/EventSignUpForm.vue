<script lang="ts" setup>
import {computed, type Ref, ref} from "vue"
import {useStore} from "vuex"
import {
  type Answer,
  createEventSignup,
  type Event,
  type EventSignUp,
  type Question,
  QuestionType,
  updateEventSignUp,
} from "@/lib"
import {Field, Form} from "vee-validate"
import type {VForm} from "vuetify/lib/components"

const emit = defineEmits(["submit"])

interface Props {
  event: Event
  showGuestForm: boolean
  buttonLoading?: boolean
  initialSignUp?: EventSignUp
}

const props = defineProps<Props>()

const store = useStore()

const answersData = ref<Answer[]>(
  props.initialSignUp?.answers
  ?? props.event.signUpForm?.questions
    .filter((q: Question) => q.type == QuestionType.DESCRIPTION).map((question: Question) => {
        if (question.type === QuestionType.OPEN) {
          return {
            questionId: question.id!,
            textResponse: "",
          }
        }
        if (question.type === QuestionType.RADIO) {
          return {
            questionId: question.id!,
            optionsSelections: Array(question.choiceLabels?.length ?? 0).fill(false),
          }
        }
        return {
          questionId: question.id!,
          optionsSelections: Array(question.choiceLabels?.length ?? 0).fill(false),
        }
      },
    )
  ?? [],
)

const guestData = ref(
  store.getters.getGuestData ?? {
    name: "",
    discord: "",
    email: "",
  },
)

const isLoggedIn = computed<boolean>(() => store.getters.isLoggedIn)
const login = computed(() => store.getters.getLogin)

const eventSignUpForm = computed(() => props.event.signUpForm ?? [])
const signUp = computed(() => {
    const signupProp = props.initialSignUp
    return {
      id: signupProp?.id,
      eventId: signupProp?.eventId,
      answers: signupProp?.answers ?? [],
    } as EventSignUp
  },
)


const answersForm: Ref<VForm | undefined> = ref()
const guestForm: Ref<VForm | undefined> = ref()

async function submit() {
  if (!isLoggedIn.value) {
    const guestFormValid = await guestForm.value?.validate()
    if (!guestFormValid) return
    signUp.value.guest = guestData.value ?? {}
  } else {
    signUp.value.userId = login.value.userId
  }

  const formValid = await answersForm.value?.validate()
  if (!formValid) return
  signUp.value.answers = answersData.value ?? []

  if (isLoggedIn.value) {
    if (signUp.value?.id) {
      await updateEventSignUp({
        path: {
          eventId: props.event.id!,
        },
        body: signUp.value,
      })
    } else {
      await createEventSignup({
        path: {
          eventId: props.event.id!,
        },
        body: {
          ...signUp.value,
          eventId: props.event.id!,
        },
      })
    }
  } else {
    store.commit("saveGuestData", guestData.value)
    await createEventSignup({
      path: {
        eventId: props.event.id!,
      },
      body: {
        ...signUp.value,
        eventId: props.event.id!,
      },
    })
  }
  emit("submit")
}

</script>

<template>
  <div>
    <Form
      v-if="!isLoggedIn"
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
          hint="We'll use this to send you a link you can use to edit your sign-up form later"
          label="Email"
          @blur="handleBlur"
          @update:model-value="handleChange"
        />
      </Field>
    </Form>

    <Form
      v-if="eventSignUpForm !== null"
      ref="answersForm"
      as="div"
    >
      <div
        v-for="(question, i) in eventSignUpForm"
        :key="i"
        class="mb-4"
      >
        <p :class="question.type === 'description' ? 'text-body-1' : 'text-h6 mb-0'">
          {{ question.prompt }}
        </p>
        <template
          v-if="question.type === 'open'"
        >
          <Field
            v-slot="{ value, errors, handleChange, handleBlur }"
            v-model="answersData[i]"
            :name="`answersData.${i}`"
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

        <template v-else-if="question.type === 'radio'">
          <Field
            v-slot="{ value = [], errors, handleChange, handleBlur }"
            v-model="answersData[i]"
            :name="`answersData.${i}`"
            :rules="(val: boolean[]) => (val && val.some(Boolean)) || 'Select at least one option'"
          >
            <div>
              <v-checkbox
                v-for="(option, j) in question.options"
                :key="j"
                :label="option"
                :model-value="(value?.[j] ?? false)"
                hide-details
                @blur="handleBlur"
                @update:model-value="(checked: boolean) => {
                  const next = Array.isArray(value)
                    ? [...value]
                    : Array(question.options.length).fill(false)
                  next[j] = checked
                  handleChange(next)
                }"
              />
              <div
                v-if="errors?.length"
                class="text-error text-caption mt-1"
              >
                {{ errors[0] }}
              </div>
            </div>
          </Field>
        </template>

        <template
          v-else-if="question.type === 'checkbox'"
        >
          <Field
            v-slot="{ value, errors, handleChange, handleBlur }"
            v-model="answersData[i]"
            :name="`answersData.${i}`"
            rules="required"
          >
            <v-checkbox
              v-for="(option, j) in question.options"
              :key="j"
              :error-messages="errors"
              :label="option"
              :model-value="value"
              :value="j"
              hide-details
              @blur="handleBlur"
              @update:model-value="handleChange"
            />
          </Field>
        </template>
      </div>
    </Form>

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

    <v-btn
      :block="true"
      :loading="buttonLoading"
      @click="submit"
    >
      {{ signUp.id ? "Update" : "Save" }} sign-up form
    </v-btn>
  </div>
</template>
S
<style lang="scss" scoped>
.v-checkbox .v-selection-control {
  min-height: 40px !important;
}
</style>
