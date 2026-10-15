<script lang="ts" setup>
import {computed, onMounted, ref} from "vue"
import {StepUpDialog, readTwoFactor} from "@/domains/auth"
import CutButton from "@/components/island/CutButton.vue"
import FormControl from "@/components/island/FormControl.vue"
import FormField from "@/components/island/FormField.vue"
import FormFields from "@/components/island/FormFields.vue"
import NoticeBox from "@/components/island/NoticeBox.vue"
import {saveOwnBankAccount} from "../adapters/bankAccounts"
import {type BankAccount, type BankAccountPreview, bicProblem, ibanCountry, ibanState, needsBic, plain, tidied, wrongIn} from "../bankAccount"
import BicInput from "./BicInput.vue"
import IbanInput from "./IbanInput.vue"

/** The reader's bank account typed in whole, which replaces whatever account was on file. */
defineOptions({name: "BankAccountForm"})

const {cancellable = false} = defineProps<{
  cancellable?: boolean
}>()

const emit = defineEmits<{saved: [preview: BankAccountPreview]; cancel: []}>()

const typed = ref<BankAccount>({holder: "", iban: "", bic: ""})
const tried = ref(false)
const saving = ref(false)
const failure = ref<string | null>(null)
const stepUpOpen = ref(false)
const twoFactorOn = ref(false)
onMounted(async () => {
  twoFactorOn.value = (await readTwoFactor())?.on === true
})
const wrong = computed(() => (tried.value ? wrongIn(typed.value) : {}))

/* The IBAN says how it is getting on while it is typed, and its mistakes as soon as they are certain. */
const iban = computed(() => ibanState(typed.value.iban))
const ibanLeft = ref(false)
const ibanError = computed(() => wrong.value.iban ?? iban.value.problem ?? (ibanLeft.value && !iban.value.complete && plain(typed.value.iban) ? "This IBAN is not complete yet." : undefined))
// A BIC still being typed is not wrong yet; it is judged once it is long enough or a save is tried.
const bicError = computed(() => wrong.value.bic
  ?? (plain(typed.value.bic).length >= 8 ? bicProblem(typed.value.bic, typed.value.iban) : null)
  ?? undefined)

const save = async () => {
  tried.value = true
  if (Object.keys(wrongIn(typed.value)).length > 0 || saving.value) return
  saving.value = true
  failure.value = null
  try {
    const answered = await saveOwnBankAccount(tidied(typed.value))
    if (!answered.ok) {
      // Replacing the account asks a step-up first; proving it saves again.
      if (answered.needsStepUp) {
        stepUpOpen.value = true
        return
      }
      failure.value = answered.reason
      return
    }
    emit("saved", answered.saved)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <form
    class="island-panel"
    data-testid="bank-account-form"
    @submit.prevent="save"
  >
    <form-fields>
      <div class="form-span">
        <form-control
          v-model="typed.holder"
          :error-messages="wrong.holder"
          label="Name on the account*"
          testid="bank-account-holder"
        />
      </div>
      <div class="form-span">
        <form-field
          :error="ibanError"
          filled
          :hint="iban.hint"
          label="IBAN"
          required
          :style="{'--field-label-left': 'calc(var(--field-lead, 4.35rem) + 1rem)'}"
          testid="bank-account-iban"
          variant="inside"
        >
          <template #default="{controlId, describedBy, invalid}">
            <iban-input
              v-model="typed.iban"
              :control-id="controlId"
              :described-by="describedBy"
              :invalid="invalid"
              testid="bank-account-iban-input"
              @blur="ibanLeft = true"
            />
          </template>
        </form-field>
      </div>
      <div
        v-if="needsBic(typed.iban)"
        class="form-span"
      >
        <form-field
          :error="bicError"
          filled
          hint="Your bank is outside the Netherlands, so we need its BIC."
          label="BIC"
          required
          :style="{'--field-label-left': 'calc(var(--field-lead, 4.35rem) + 1rem)'}"
          testid="bank-account-bic"
          variant="inside"
        >
          <template #default="{controlId, describedBy, invalid}">
            <bic-input
              v-model="typed.bic"
              :control-id="controlId"
              :country="ibanCountry(typed.iban)"
              :described-by="describedBy"
              :invalid="invalid"
              testid="bank-account-bic-input"
            />
          </template>
        </form-field>
      </div>
    </form-fields>
    <notice-box
      v-if="failure"
      tone="danger"
    >
      {{ failure }}
    </notice-box>
    <div class="form-save bank__save">
      <cut-button
        v-if="cancellable"
        testid="bank-account-cancel"
        tone="quiet"
        @click="emit('cancel')"
      >
        Cancel
      </cut-button>
      <cut-button
        :disabled="saving"
        submit
        testid="bank-account-save"
        tone="solid"
      >
        {{ saving ? "Saving" : "Save bank account" }}
      </cut-button>
    </div>
  </form>
  <step-up-dialog
    v-model="stepUpOpen"
    :two-factor-on="twoFactorOn"
    @proved="save"
  />
</template>

<style scoped>
/* The save sits right under the last field, with no rule between: the panel holds only the form. */
.island-panel .bank__save {
  padding-top: 0;
  border-top: 0;
}
</style>
