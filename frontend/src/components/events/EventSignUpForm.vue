<script lang="ts" setup>
import {computed, type Ref, ref} from "vue"
import {useStore} from "vuex"
import {createEventSignup, type Event, type EventSignUp, type FormQuestion, updateEventSignUp} from "@/lib"
import {Field, Form} from "vee-validate"
import type {VForm} from "vuetify/lib/components"

const emit = defineEmits(["submit"])

interface Props {
  event: Event
  initialFormAnswers?: any[]
  showGuestForm: boolean
  buttonLoading?: boolean
  initialSignUp?: EventSignUp
}

const props = defineProps<Props>()

const store = useStore()

/**
 * If `props.answersString` is provided, assume it’s already a valid array/object of answers.
 * Otherwise, if `props.event.signUpForm` is present, build an initial answers array according
 * to the question type. If neither is present, set answers to null.
 */
const answersData = ref(
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

const isLoggedIn = computed<boolean>(() => store.getters.isLoggedIn)
const login = computed(() => store.getters.getLogin)

/**
 * The sign-up form structure for the event.
 * We assume `props.event.signUpForm` is already an array of question objects.
 */
const eventSignUpForm = computed(() => props.event.signUpForm ?? [])
const signUp = computed(() => {
    const signupProp = props.initialSignUp ?? {}
    return {
      id: signupProp?.id,
      eventId: signupProp?.eventId,
      formAnswers: signupProp?.formAnswers ?? [],
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
  signUp.value.formAnswers = answersData.value ?? []

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
  emit('submit')
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

    <!-- ANSWERS FORM -->
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
        <!-- Open Question -->
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
              :model-value="value"
              :error-messages="errors"
              @update:model-value="handleChange"
              @blur="handleBlur"
            />
          </Field>
        </template>

        <!-- Radio Question -->
        <template
          v-else-if="question.type === 'radio'"
        >
          <Field
            v-slot="{ value, errors, handleChange, handleBlur }"
            v-model="answersData[i]"
            :name="`answersData.${i}`"
            rules="required"
          >
            <v-radio-group
              :model-value="value"
              :error-messages="errors"
              hide-details="auto"
              @update:model-value="handleChange"
            >
              <v-radio
                v-for="(option, j) in question.options"
                :key="j"
                :label="option"
                :value="j"
                @blur="handleBlur"
              />
            </v-radio-group>
          </Field>
        </template>


        <!-- Checkbox Question -->
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
              :model-value="value"
              :error-messages="errors"
              :label="option"
              :value="j"
              hide-details
              @update:model-value="handleChange"
              @blur="handleBlur"
            />
          </Field>
        </template>
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
