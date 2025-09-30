<script lang="ts" setup>

/*
  eventId is the id of the event that form will be submitted for.

  form is the event's sign-up form as an Object and is structured as follows:
  [
    {
      prompt: 'question to be asked',
      type: 'type of question',
      options: [
        'the options the user can select',
        'this is only done for radio and checkbox questions',
      ],
    }, etc.
  ]
  The question's type will be either 'open' for open questions,
  'radio' for a multiple choice question (named after the radio buttons it uses),
  or 'checkbox' for a question with checkboxes.

  Answers should be an Array, in this prop the answers to the form are stored.
  Each question will have an entry in answers. The entry should be:
  - a string for 'open' questions
  - a Number from 0 to i-1 for i options (null if no value is selected)
  - an array with the selected checkboxes. The array can contain any Number from 0 to i-1 for i options for each of the selected option
*/


import {ref} from "vue"
import {useStore} from "vuex"
import type {Event, FormQuestion, Guest} from "@/lib"
import {Field} from "vee-validate"

const emit = defineEmits(["submit"])

interface Props {
  event: Event
  // If provided, we assume it's already a valid set of answers
  initialFormAnswers?: any[]
  showGuestForm: boolean
  buttonLoading?: boolean
}

const props = defineProps<Props>()

const store = useStore()

/**
 * If `props.answersString` is provided, assume it’s already a valid array/object of answers.
 * Otherwise, if `props.event.signUpForm` is present, build an initial answers array according
 * to the question type. If neither is present, set answers to null.
 */
const formAnswers = ref(
  props.initialFormAnswers
  ?? props.event.signUpForm?.map((question: FormQuestion) => {
    if (question.type === "open") return ""
    if (question.type === "checkbox") return []
    return null // For radio or anything else
  }),
)

/**
 * If the user is not logged in, we allow them to enter temporary guest data
 */
const guestData = ref(
  store.getters.getGuestData ?? {
    name: "",
    discord: "",
    email: "",
  },
)

/**
 * The sign-up form structure for the event.
 * We assume `props.event.signUpForm` is already an array of question objects.
 */
const form = ref(props.event.signUpForm || null)

/**
 * Validates both the answers form and the guest form, then emits "submit"
 * with the collected data if valid.
 */
async function submit() {
  const formValid = answersForm.value ? (await answersForm.value.validate()).valid : true
  const guestFormValid = guestForm.value ? (await guestForm.value.validate()).valid : true

  if (formValid && guestFormValid) {
    emit("submit", {
      answers: formAnswers,
      guestData: guestData,
    })
  }
}

/**
 * Refs pointing to <v-form> so we can call validate() on them
 @submit="({ answers, guestData }): { answers: Array<{ [key: string]: unknown; }>; guestData: Guest } => submitSignUpForm(event.id as number, { answers, guestData })"
 */
const answersForm = ref<Array<{ [key: string]: unknown; }>>()
const guestForm = ref<Guest>()
</script>

<template>
  <div>
    <!-- GUEST FORM (shown if user is not logged in) -->
    <v-form
      v-if="props.showGuestForm"
      ref="guestForm"
      class="mb-4"
    >
      <v-alert
        class="mb-4"
        text="It seems you are not logged in. You can still sign up for this event, but we'll need some extra info from you."
        type="info"
        variant="outlined"
      />
      <v-text-field
        v-model="guestData.name"
        :rules="[(v: string) => !!v || 'Name is required']"
        label="Name"
      />
      <v-text-field
        v-model="guestData.discord"
        :rules="[(v: string) => !!v || 'Discord username is required']"
        label="Discord username"
      />
      <v-text-field
        v-model="guestData.email"
        :rules="[(v: string) => !!v || 'Email is required', (v: string) => /.+@.+\..+/.test(v) || 'E-mail must be valid']"
        hint="We'll use this to send you a link you can use to edit your sign-up form later"
        label="Email"
      />
    </v-form>

    <!-- ANSWERS FORM -->
    <Form
      v-if="formAnswers !== null"
      ref="answersForm"
      as="div"
    >
      <div
        v-for="(question, i) in form"
        :key="i"
        class="mb-4"
      >
        <p :class="question.type === 'description' ? 'text-body-1' : 'text-h6 mb-0'">
          {{ question.prompt }}
        </p>

        <!-- Open Question -->
        <Field
          v-slot="{ value, errors, handleChange, handleBlur }"
          v-model="formAnswers[i]"
          :name="`formAnswers.${i}`"
          rules="required"
        >
          <v-text-field
            v-if="question.type === 'open'"
            :model-value="value"
            :error-messages="errors"
            @update:model-value="handleChange"
            @blur="handleBlur"
          />
        </Field>

        <!-- Radio Question -->
        <v-radio-group
          v-if="question.type === 'radio'"
          v-model="formAnswers[i]"
          :rules="[(v: string) => v != null || 'An answer is required']"
          hide-details="auto"
        >
          <v-radio
            v-for="(option, j) in question.options"
            :key="j"
            :label="option"
            :value="j"
          />
        </v-radio-group>

        <!-- Checkbox Question -->
        <v-checkbox
          v-for="(option, j) in question.options"
          v-else-if="question.type === 'checkbox'"
          :key="j"
          v-model="formAnswers[i]"
          :label="option"
          :value="j"
          hide-details
        />
      </div>
    </Form>

    <!-- SUBMIT BUTTON -->
    <v-btn
      :block="true"
      :loading="buttonLoading"
      @click="submit"
    >
      {{ props.initialFormAnswers ? "Update" : "Save" }} sign-up form
    </v-btn>
  </div>
</template>

<style lang="scss" scoped>
.v-checkbox .v-selection-control {
  min-height: 40px !important;
}
</style>
