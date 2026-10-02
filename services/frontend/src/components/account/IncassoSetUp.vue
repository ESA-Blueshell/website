<script lang="ts" setup>
/* A member's own bank details for incasso. Saving signs the mandate that day, on the site; the
   account number is shown back only masked. From the account page a change asks the person to
   prove it is them first, and saves once they have. */
import {onMounted, ref} from "vue"
import {StepUpDialog, readTwoFactor} from "@/domains/auth"
import {maskedIban} from "@/domains/contribution"
import {type OwnMandateResponse, readOwnMandate, setUpIncasso} from "@/domains/user"

defineOptions({name: "IncassoSetUp"})

const {signupToken = undefined} = defineProps<{
  /** During a signup, which has no session: the details wait on the token for the membership. */
  signupToken?: string
}>()
const emit = defineEmits<{saved: []}>()

const own = ref<OwnMandateResponse | null>(null)
const open = ref(false)
const iban = ref("")
const holder = ref("")
const authorised = ref(false)
const failure = ref<string | null>(null)
const saved = ref(false)
const saving = ref(false)
const stepUpOpen = ref(false)
const twoFactorOn = ref(false)

const save = async () => {
  if (saving.value) return
  saving.value = true
  failure.value = null
  const answered = await setUpIncasso({iban: iban.value, accountHolder: holder.value, authorised: authorised.value}, signupToken)
  saving.value = false
  if (!answered.ok) {
    if ("needsStepUp" in answered && answered.needsStepUp) {
      stepUpOpen.value = true
      return
    }
    failure.value = answered.reason
    return
  }
  own.value = answered.saved ?? own.value
  saved.value = true
  open.value = false
  iban.value = ""
  emit("saved")
}

onMounted(async () => {
  if (signupToken) return
  const [held, standing] = await Promise.all([readOwnMandate(), readTwoFactor()])
  own.value = held
  twoFactorOn.value = standing?.on === true
})
</script>

<template>
  <section
    class="incasso"
    data-testid="incasso-set-up"
  >
    <p
      v-if="own?.ibanLastTwo"
      data-testid="incasso-current"
    >
      Your contribution is collected by incasso from the account {{ maskedIban(own) }}{{ own.pending ? ", from the day your membership starts" : "" }}.
    </p>
    <p
      v-else
      data-testid="incasso-none"
    >
      You pay by incasso by giving your bank details here. Other ways to pay exist, but they are handled by hand: the
      treasurer sends you a payment request.
    </p>
    <p
      v-if="saved"
      class="incasso__saved"
      data-testid="incasso-saved"
      role="status"
    >
      Your bank details are saved.
    </p>

    <v-btn
      v-if="!open"
      data-testid="incasso-open"
      variant="outlined"
      @click="open = true"
    >
      {{ own?.ibanLastTwo ? "Change bank details" : "Pay by incasso" }}
    </v-btn>
    <form
      v-else
      data-testid="incasso-form"
      @submit.prevent="save"
    >
      <v-text-field
        v-model="iban"
        autocomplete="off"
        data-testid="incasso-iban"
        label="IBAN"
      />
      <v-text-field
        v-model="holder"
        data-testid="incasso-holder"
        label="Account holder"
      />
      <v-checkbox
        v-model="authorised"
        data-testid="incasso-authorised"
        label="I authorise ESA Blueshell to collect my yearly contribution from this account by incasso, and my bank to pay it."
      />
      <p
        v-if="failure"
        class="incasso__failure"
        data-testid="incasso-failure"
        role="alert"
      >
        {{ failure }}
      </p>
      <v-btn
        class="mr-2"
        data-testid="incasso-cancel"
        variant="text"
        @click="open = false"
      >
        Cancel
      </v-btn>
      <v-btn
        color="primary"
        data-testid="incasso-save"
        :disabled="!authorised || saving"
        type="submit"
      >
        Save bank details
      </v-btn>
    </form>
    <step-up-dialog
      v-model="stepUpOpen"
      :two-factor-on="twoFactorOn"
      @proved="save"
    />
  </section>
</template>

<style scoped>
.incasso__saved {
  color: rgb(var(--v-theme-success));
}

.incasso__failure {
  color: rgb(var(--v-theme-error));
}
</style>
