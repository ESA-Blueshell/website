<script lang="ts" setup>
/* A membership's incasso standing and mandate, with the board's way to record a paper mandate or
   replace one. The account number shows masked until a board member reveals it; the revealed
   number lives in this component's memory alone, so it is gone when the panel closes. */
import {computed, ref, watch} from "vue"
import {maskedIban} from "@/domains/contribution"
import {IncassoStanding, MandateKind, type MandateResponse, readMandate, revealMandateIban, saveMandate} from "@/domains/user"
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
const replacesOnline = ref(false)
const revealed = ref<string | null>(null)
const revealFailure = ref<string | null>(null)
const revealing = ref(false)

const standing = computed(() => (mandate.value ? WORDS[mandate.value.standing] : ""))

const account = computed(() =>
  mandate.value ? [revealed.value ?? maskedIban(mandate.value), mandate.value.accountHolder].filter(Boolean).join(", ") : "")

/* Online or paper, and for an online mandate the day the member authorised it. A paper mandate
   recorded over an online one loses that record, so the board confirms it first. */
const online = computed(() => mandate.value?.kind === MandateKind.ONLINE)
const authorisedOn = computed(() => (mandate.value?.authorisedAt ? formatDate(mandate.value.authorisedAt) : ""))
const kind = computed(() => {
  if (!mandate.value?.kind) return ""
  return online.value ? `Online mandate, authorised on ${authorisedOn.value}` : "Paper mandate"
})

const load = async () => {
  revealed.value = null
  revealFailure.value = null
  mandate.value = await readMandate(membershipId)
}

const reveal = async () => {
  if (revealing.value) return
  revealing.value = true
  revealFailure.value = null
  const answered = await revealMandateIban(membershipId)
  revealing.value = false
  if (answered.ok) revealed.value = answered.saved.replace(/(.{4})/g, "$1 ").trim()
  else revealFailure.value = answered.reason
}

const save = async () => {
  if (saving.value) return
  saving.value = true
  failure.value = null
  const answered = await saveMandate(membershipId, {
    iban: iban.value, accountHolder: holder.value, signedOn: signedOn.value, replacesOnline: online.value && replacesOnline.value,
  })
  saving.value = false
  if (!answered.ok) {
    failure.value = answered.reason
    return
  }
  mandate.value = answered.saved
  revealed.value = null
  replacesOnline.value = false
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
      v-if="mandate?.ibanLastTwo"
      class="mandate__facts"
      data-testid="mandate-facts"
    >
      <div>
        <dt>Account</dt>
        <dd data-testid="mandate-account">
          {{ account }}
        </dd>
        <p
          v-if="mandate.bankDetailsWiped"
          data-testid="mandate-wiped"
        >
          The bank details were wiped 13 months after the last collection. What is left is the record of what was collected.
        </p>
        <button
          v-else
          class="mandate__action mandate__action--inline"
          data-testid="mandate-reveal"
          :disabled="revealing"
          type="button"
          @click="revealed ? (revealed = null) : reveal()"
        >
          {{ revealed ? "Hide the IBAN" : "Reveal the IBAN" }}
        </button>
        <p
          v-if="revealFailure"
          class="mandate__failure"
          data-testid="mandate-reveal-failure"
          role="alert"
        >
          {{ revealFailure }}
        </p>
      </div>
      <div>
        <dt>Mandate</dt>
        <dd>{{ mandate.reference }}, signed {{ mandate.signedOn }}</dd>
      </div>
      <div v-if="kind">
        <dt>Kind</dt>
        <dd data-testid="mandate-kind">
          {{ kind }}
        </dd>
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
      {{ mandate?.ibanLastTwo ? "Replace the mandate" : "Record a paper mandate" }}
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
      <template v-if="online">
        <p
          class="mandate__failure"
          data-testid="mandate-replaces-online"
        >
          This replaces the online mandate the member authorised on {{ authorisedOn }}. Its PDF will no longer be available.
        </p>
        <v-checkbox
          v-model="replacesOnline"
          data-testid="mandate-replaces-online-confirm"
          label="Replace the online mandate"
        />
      </template>
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
          :disabled="saving || (online && !replacesOnline)"
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

.mandate__action--inline {
  margin-top: 0.3rem;
  padding: 0.2rem 0.6rem;
  font-size: 0.78rem;
}

.mandate__action--main {
  border-color: var(--color-brand);
  color: var(--color-brand);
}

.mandate__failure {
  color: var(--color-error, #e5484d);
}
</style>
