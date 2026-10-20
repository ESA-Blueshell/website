<script lang="ts" setup>
import FormControl from "@/components/island/FormControl.vue"
import CheckBox from "@/components/island/CheckBox.vue"
import CutButton from "@/components/island/CutButton.vue"
/* The signup's optional incasso step. The bank details wait on the signup's token for the
   membership, and the mandate takes the address the signup has just taken. A member sets up
   direct debit later on the Payment method page. */
import {vFirstField} from "@/utils/firstField"
import {ref} from "vue"
import {MANDATE_WORDING, setUpIncasso} from "@/domains/user"

defineOptions({name: "IncassoSetUp"})

const {signupToken} = defineProps<{
  /** The signup has no session, so the details go on its token. */
  signupToken: string
}>()

const open = ref(false)
const iban = ref("")
const holder = ref("")
const authorised = ref(false)
const failure = ref<string | null>(null)
const saved = ref(false)
const saving = ref(false)

const save = async () => {
  if (saving.value) return
  saving.value = true
  failure.value = null
  const answered = await setUpIncasso({iban: iban.value, accountHolder: holder.value, authorised: authorised.value}, signupToken)
  saving.value = false
  if (!answered.ok) {
    failure.value = answered.reason
    return
  }
  saved.value = true
  open.value = false
  iban.value = ""
}
</script>

<template>
  <section
    class="incasso"
    data-testid="incasso-set-up"
  >
    <p data-testid="incasso-none">
      Incasso is automatic: the association collects your contribution from your bank account once a contribution
      period, and emails you before it does. Without incasso you pay by hand, after a payment request from the
      treasurer.
    </p>
    <p
      v-if="saved"
      class="incasso__saved"
      data-testid="incasso-saved"
      role="status"
    >
      Your bank details are saved.
    </p>

    <cut-button
      v-if="!open"
      tone="plain"
      data-testid="incasso-open"
      @click="open = true"
    >
      Set up incasso
    </cut-button>
    <form
      v-else
      v-first-field
      class="island-form"
      data-testid="incasso-form"
      @submit.prevent="save"
    >
      <form-control
        v-model="iban"
        autocomplete="off"
        data-testid="incasso-iban"
        label="IBAN"
      />
      <form-control
        v-model="holder"
        data-testid="incasso-holder"
        label="Account holder"
      />
      <p data-testid="incasso-address-note">
        The address you gave in this signup is recorded with the mandate.
      </p>
      <check-box
        v-model="authorised"
        data-testid="incasso-authorised"
        :label="MANDATE_WORDING.text"
      />
      <p
        v-if="failure"
        class="incasso__failure"
        data-testid="incasso-failure"
        role="alert"
      >
        {{ failure }}
      </p>
      <div class="form-save">
        <cut-button
          tone="quiet"
          data-testid="incasso-cancel"
          @click="open = false"
        >
          Cancel
        </cut-button>
        <cut-button
          tone="solid"
          submit
          data-testid="incasso-save"
          :disabled="!authorised || saving"
        >
          Save bank details
        </cut-button>
      </div>
    </form>
  </section>
</template>

<style scoped>
.incasso__saved {
  color: var(--color-ok);
}

.incasso__failure {
  color: var(--color-danger);
}
</style>
