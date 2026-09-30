<script lang="ts" setup>
/* A membership's incasso standing and mandate, with the board's way to record a paper mandate or
   replace one. The account number is only ever shown by its last four. */
import {computed, ref, watch} from "vue"
import {IncassoStanding, type MandateResponse, readMandate, saveMandate} from "@/domains/user"
import {formatDate} from "@/utils/timestamps"

defineOptions({name: "MandatePanel"})

const {membershipId} = defineProps<{membershipId: number}>()
const emit = defineEmits<{changed: []}>()

const WORDS: Record<IncassoStanding, string> = {
  [IncassoStanding.NONE]: "Pays by transfer",
  [IncassoStanding.MANDATE_RECORDED]: "Collected by incasso",
  [IncassoStanding.ON_INCASSO_WITHOUT_BANK_DETAILS]: "On incasso, but no bank details are on file",
}

const mandate = ref<MandateResponse | null>(null)
const open = ref(false)
const iban = ref("")
const holder = ref("")
const signedOn = ref(new Date().toISOString().slice(0, 10))
const failure = ref<string | null>(null)
const saving = ref(false)

const standing = computed(() => (mandate.value ? WORDS[mandate.value.standing] : ""))

const load = async () => {
  mandate.value = await readMandate(membershipId)
}

const save = async () => {
  if (saving.value) return
  saving.value = true
  failure.value = null
  const answered = await saveMandate(membershipId, {iban: iban.value, accountHolder: holder.value, signedOn: signedOn.value})
  saving.value = false
  if (!answered.ok) {
    failure.value = answered.reason
    return
  }
  mandate.value = answered.saved
  open.value = false
  iban.value = ""
  emit("changed")
}

watch(() => membershipId, load, {immediate: true})
</script>

<template>
  <section
    class="mandate"
    data-testid="mandate-panel"
  >
    <h3 class="mandate__title">
      Incasso
    </h3>
    <p data-testid="mandate-standing">
      {{ standing }}
    </p>
    <dl
      v-if="mandate?.ibanLastFour"
      class="mandate__facts"
      data-testid="mandate-facts"
    >
      <div>
        <dt>Account</dt>
        <dd>•••• {{ mandate.ibanLastFour }}, {{ mandate.accountHolder }}</dd>
      </div>
      <div>
        <dt>Mandate</dt>
        <dd>{{ mandate.reference }}, signed {{ mandate.signedOn }}</dd>
      </div>
      <div v-if="mandate.recordedAt">
        <dt>Recorded</dt>
        <dd>{{ formatDate(mandate.recordedAt) }}</dd>
      </div>
    </dl>

    <button
      v-if="!open"
      class="mandate__action"
      data-testid="mandate-record"
      type="button"
      @click="open = true"
    >
      {{ mandate?.ibanLastFour ? "Replace the mandate" : "Record a paper mandate" }}
    </button>
    <form
      v-else
      class="mandate__form"
      data-testid="mandate-form"
      @submit.prevent="save"
    >
      <v-text-field
        v-model="iban"
        autocomplete="off"
        data-testid="mandate-iban"
        label="IBAN"
      />
      <v-text-field
        v-model="holder"
        data-testid="mandate-holder"
        label="Account holder"
      />
      <v-text-field
        v-model="signedOn"
        data-testid="mandate-signed-on"
        label="Signed on"
        type="date"
      />
      <p
        v-if="failure"
        class="mandate__failure"
        data-testid="mandate-failure"
        role="alert"
      >
        {{ failure }}
      </p>
      <div class="mandate__buttons">
        <button
          class="mandate__action"
          data-testid="mandate-cancel"
          type="button"
          @click="open = false"
        >
          Cancel
        </button>
        <button
          class="mandate__action mandate__action--main"
          data-testid="mandate-save"
          :disabled="saving"
          type="submit"
        >
          Save mandate
        </button>
      </div>
    </form>
  </section>
</template>

<style scoped>
.mandate {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  margin-bottom: 1.2rem;
  padding: 1rem;
  background-color: var(--band-ground);
  border: 1px solid var(--color-hairline);
}

.mandate__title {
  margin: 0;
  font-size: 0.75rem;
  letter-spacing: 0.2em;
  text-transform: uppercase;
  color: var(--color-eyebrow);
}

.mandate p {
  margin: 0;
}

.mandate__facts {
  display: grid;
  gap: 0.4rem;
  margin: 0;
}

.mandate__facts dt {
  font-size: 0.72rem;
  color: var(--color-ash);
}

.mandate__facts dd {
  margin: 0;
}

.mandate__form {
  max-width: 28rem;
}

.mandate__buttons {
  display: flex;
  gap: 0.6rem;
}

.mandate__action {
  align-self: flex-start;
  padding: 0.4rem 0.9rem;
  border: 1px solid var(--color-hairline);
  background: none;
  font: inherit;
  font-size: 0.86rem;
  color: var(--color-chalk);
  cursor: pointer;
}

.mandate__action--main {
  border-color: var(--color-brand);
  color: var(--color-brand);
}

.mandate__failure {
  color: var(--color-error, #e5484d);
}
</style>
