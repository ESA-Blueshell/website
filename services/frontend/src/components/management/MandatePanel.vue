<script lang="ts" setup>
/* A membership's incasso standing and mandate, with the board's way to record a paper mandate or
   replace one. The account number shows masked until a board member reveals it; the revealed
   number lives in this component's memory alone, so it is gone when the panel closes. */
import {vFirstField} from "@/utils/firstField"
import {computed, ref, watch} from "vue"
import CheckBox from "@/components/island/CheckBox.vue"
import CutButton from "@/components/island/CutButton.vue"
import DateInput from "@/components/island/DateInput.vue"
import FormField from "@/components/island/FormField.vue"
import NoticeBox from "@/components/island/NoticeBox.vue"
import TextInput from "@/components/island/TextInput.vue"
import ListHead from "@/components/management/ListHead.vue"
import MiniButton from "@/components/management/MiniButton.vue"
import {maskedIban} from "@/domains/contribution"
import {IncassoStanding, MandateKind, type MandateResponse, fetchMandatePdf, readMandate, revealMandateIban, saveMandate} from "@/domains/user"
import {formatDate, formatDay} from "@/utils/timestamps"

defineOptions({name: "MandatePanel"})

const {membershipId, startOpen = false} = defineProps<{
  membershipId: number
  /** Opens on the form, where the panel is asked for in order to fill it in. */
  startOpen?: boolean
}>()
const emit = defineEmits<{changed: []}>()

const WORDS: Record<IncassoStanding, string> = {
  [IncassoStanding.NONE]: "Pays by transfer",
  [IncassoStanding.MANDATE_RECORDED]: "Collected by incasso",
  [IncassoStanding.ON_INCASSO_WITHOUT_BANK_DETAILS]: "On incasso, but no bank details are on file",
}

const mandate = ref<MandateResponse | null>(null)
const open = ref(startOpen)
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

/* Only an online mandate has a PDF: a paper one is its own record, and a wiped one has nothing to print. */
const hasPdf = computed(() => online.value && !mandate.value?.bankDetailsWiped && !!mandate.value?.reference)
const paperNote = computed(() => {
  const held = mandate.value
  if (!held || held.kind !== MandateKind.PAPER) return ""
  const who = held.recordedByName ? ` by ${held.recordedByName}` : ""
  const when = held.recordedAt ? ` on ${formatDate(held.recordedAt)}` : ""
  return `Paper mandate, recorded${who}${when}. The signed paper is the record, so there is no PDF.`
})
const pdfFailure = ref<string | null>(null)
const fetchingPdf = ref(false)

const downloadPdf = async () => {
  if (fetchingPdf.value || !mandate.value) return
  fetchingPdf.value = true
  pdfFailure.value = null
  const answered = await fetchMandatePdf(membershipId)
  fetchingPdf.value = false
  if (!answered.ok) {
    pdfFailure.value = answered.reason
    return
  }
  const url = URL.createObjectURL(answered.file)
  const anchor = document.createElement("a")
  anchor.href = url
  anchor.download = `mandate-${mandate.value.reference}.pdf`
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  URL.revokeObjectURL(url)
}

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
    <list-head title="Incasso details">
      <span data-testid="mandate-standing">{{ standing }}</span>
    </list-head>

    <div
      v-if="mandate?.ibanLastTwo"
      class="mandate__facts"
      data-testid="mandate-facts"
    >
      <div class="mandate__fact">
        <p class="mandate__label">
          Account
        </p>
        <p
          class="mandate__value"
          data-testid="mandate-account"
        >
          {{ account }}
        </p>
        <p
          v-if="mandate.bankDetailsWiped"
          class="mandate__sub"
          data-testid="mandate-wiped"
        >
          The bank details were wiped 13 months after the last collection. What is left is the record of what was collected.
        </p>
        <mini-button
          v-else
          class="mandate__act"
          :disabled="revealing"
          testid="mandate-reveal"
          @click="revealed ? (revealed = null) : reveal()"
        >
          {{ revealed ? "Hide the IBAN" : "Reveal the IBAN" }}
        </mini-button>
        <p
          v-if="revealFailure"
          class="mandate__failure"
          data-testid="mandate-reveal-failure"
          role="alert"
        >
          {{ revealFailure }}
        </p>
      </div>
      <div class="mandate__fact">
        <p class="mandate__label">
          Mandate
        </p>
        <p class="mandate__value">
          {{ mandate.reference }}
        </p>
        <p class="mandate__sub">
          Signed {{ formatDay(mandate.signedOn) }}
        </p>
      </div>
      <div
        v-if="kind"
        class="mandate__fact"
      >
        <p class="mandate__label">
          Kind
        </p>
        <p
          class="mandate__sub"
          data-testid="mandate-kind"
        >
          {{ paperNote || kind }}
        </p>
        <mini-button
          v-if="hasPdf"
          class="mandate__act"
          :disabled="fetchingPdf"
          testid="mandate-pdf"
          @click="downloadPdf"
        >
          Download the mandate PDF
        </mini-button>
        <p
          v-if="pdfFailure"
          class="mandate__failure"
          data-testid="mandate-pdf-failure"
          role="alert"
        >
          {{ pdfFailure }}
        </p>
      </div>
      <div
        v-if="mandate.recordedAt"
        class="mandate__fact"
      >
        <p class="mandate__label">
          Recorded
        </p>
        <p class="mandate__sub">
          {{ formatDate(mandate.recordedAt) }}
        </p>
      </div>
    </div>

    <div
      v-if="!open"
      class="mandate__acts"
    >
      <cut-button
        testid="mandate-record"
        @click="open = true"
      >
        {{ mandate?.ibanLastTwo ? "Replace incasso details" : "Add incasso details" }}
      </cut-button>
    </div>

    <form

      v-else
      v-first-field
      class="mandate__form"
      data-testid="mandate-form"
      @submit.prevent="save"
    >
      <form-field
        v-slot="field"
        label="IBAN"
        testid="mandate-iban"
      >
        <text-input
          v-model="iban"
          :control-id="field.controlId"
        />
      </form-field>
      <form-field
        v-slot="field"
        label="Account holder"
        testid="mandate-holder"
      >
        <text-input
          v-model="holder"
          :control-id="field.controlId"
        />
      </form-field>
      <form-field
        v-slot="field"
        label="Signed on"
        testid="mandate-signed-on"
      >
        <date-input
          v-model="signedOn"
          :control-id="field.controlId"
        />
      </form-field>
      <template v-if="online">
        <notice-box
          testid="mandate-replaces-online"
          tone="warning"
        >
          This replaces the online mandate the member authorised on {{ authorisedOn }}. Its PDF will no longer be available.
        </notice-box>
        <check-box
          v-model="replacesOnline"
          label="Replace the online mandate"
          testid="mandate-replaces-online-confirm"
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
      <div class="mandate__acts">
        <cut-button
          :disabled="saving || (online && !replacesOnline)"
          submit
          testid="mandate-save"
          tone="solid"
        >
          Save incasso details
        </cut-button>
        <cut-button
          testid="mandate-cancel"
          tone="quiet"
          @click="open = false"
        >
          Cancel
        </cut-button>
      </div>
    </form>
  </section>
</template>

<style scoped>
.mandate__facts {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(14rem, 1fr));
  gap: 1.2rem 0;
  padding-bottom: 1rem;
}

.mandate__fact {
  position: relative;
  min-width: 0;
  padding-inline: 1.25rem;
}

.mandate__fact::before {
  content: "";
  position: absolute;
  top: 0.2rem;
  bottom: 0.2rem;
  left: 0;
  width: 1px;
  background-color: var(--color-hairline);
  transform: skewX(-12deg);
}

.mandate__fact:first-child {
  padding-inline-start: 0;
}

.mandate__fact:first-child::before {
  display: none;
}

.mandate__label {
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  color: var(--color-ash);
}

.mandate__value {
  margin-top: 0.45rem;
  font-family: var(--font-display);
  font-size: 1.05rem;
  line-height: 1.2;
  font-variant-numeric: tabular-nums;
}

.mandate__sub {
  margin-top: 0.25rem;
  font-size: 0.9rem;
  line-height: 1.4;
  color: var(--color-ash);
}

.mandate__act {
  margin-top: 0.5rem;
}

.mandate__form {
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
  max-width: 32rem;
}

.mandate__acts {
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem;
  padding-top: 0.4rem;
}

.mandate__failure {
  margin-top: 0.4rem;
  font-size: 0.86rem;
  color: var(--color-danger);
}
</style>
