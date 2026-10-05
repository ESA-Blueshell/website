<script lang="ts" setup>
/* A member's own bank details for incasso. Saving authorises an online mandate at that moment,
   under the wording shown and the address confirmed here; the account number is shown back only
   masked. From the account page a change asks the person to prove it is them first, and saves
   once they have. The address is the mandate's own record: saving it here leaves the address on
   the account as it is. */
import {onMounted, ref, watch} from "vue"
import CountrySelect from "@/components/form/fields/CountrySelect.vue"
import {StepUpDialog, readTwoFactor} from "@/domains/auth"
import {maskedIban} from "@/domains/contribution"
import {MANDATE_WORDING, type MandateAddressRequest, type OwnMandateResponse, readAddress, readOwnMandate, setUpIncasso} from "@/domains/user"

defineOptions({name: "IncassoSetUp"})

const {signupToken = undefined, addressId = null} = defineProps<{
  /** During a signup, which has no session: the details wait on the token for the membership. */
  signupToken?: string
  /** The address on the member's account, which the form shows to confirm or correct. */
  addressId?: number | null
}>()
const emit = defineEmits<{saved: []}>()

const own = ref<OwnMandateResponse | null>(null)
const open = ref(false)
const iban = ref("")
const holder = ref("")
const address = ref<MandateAddressRequest>({country: "NL", city: "", street: "", houseNumber: "", zipCode: ""})
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
  const answered = await setUpIncasso(
    {iban: iban.value, accountHolder: holder.value, authorised: authorised.value, address: address.value},
    signupToken,
  )
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

// Prefilled from the account's address where it opens; one that does not is typed in here.
watch(() => addressId, async (id) => {
  if (id == null) return
  const onFile = await readAddress(id).catch(() => null)
  if (!onFile?.opened) return
  address.value = {
    country: onFile.country ?? "NL", city: onFile.city ?? "", street: onFile.street ?? "",
    houseNumber: onFile.houseNumber ?? "", zipCode: onFile.zipCode ?? "",
  }
}, {immediate: true})

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

    <v-btn
      v-if="!open"
      data-testid="incasso-open"
      variant="outlined"
      @click="open = true"
    >
      {{ own?.ibanLastTwo ? "Change bank details" : "Set up incasso" }}
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
      <template v-if="!signupToken">
        <p data-testid="incasso-address-note">
          Your address is recorded with the mandate. Check it, and correct it here if it has changed.
        </p>
        <v-text-field
          v-model="address.street"
          data-testid="incasso-street"
          label="Street"
        />
        <v-text-field
          v-model="address.houseNumber"
          data-testid="incasso-house-number"
          label="House number"
        />
        <v-text-field
          v-model="address.zipCode"
          data-testid="incasso-zip-code"
          label="Zipcode"
        />
        <v-text-field
          v-model="address.city"
          data-testid="incasso-city"
          label="City"
        />
        <country-select
          v-model="address.country"
          test-id="incasso-country"
        />
      </template>
      <p
        v-else
        data-testid="incasso-address-note"
      >
        The address you gave in this signup is recorded with the mandate.
      </p>
      <v-checkbox
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
