<script lang="ts" setup>
/* Payment reminders for members paying by transfer, as four steps: who gets one, which fee each
   pays, the due date and a check of what will be sent, then sent. Nothing goes before Send. */
import {computed, onMounted, ref} from "vue"
import {useRoute} from "vue-router"
import EmailPreviewDialog from "@/components/common/modals/EmailPreviewDialog.vue"
import {useEmailPreview} from "@/composables/useEmailPreview"
import {
  type ContributionPeriodResponse,
  ContributionEmailKind,
  type ReminderRow,
  listPeriods,
  readOneEmail,
  readSelection,
  reminderName,
  reminderRows,
  sendTheEmails,
} from "@/domains/contribution"
import {$handleNetworkError} from "@/plugins/handleNetworkError"
import {BulkFeeType} from "@/utils/bulkRow"
import {effectiveAmount, feeTypeLabels} from "@/utils/feePreview"
import {formatBulkDate} from "@/utils/bulkDisposition"

defineOptions({name: "PaymentRemindersPage"})

const STEPS = ["Who", "Fees", "Check", "Sent"] as const

const route = useRoute()
const periodId = computed(() => Number(route.params.periodId))
const ids = computed(() => String(route.query.ids ?? "").split(",").map(Number).filter((one) => Number.isInteger(one) && one > 0))
const back = computed(() => `/management/contributions/${periodId.value}`)

const period = ref<ContributionPeriodResponse | null>(null)
const rows = ref<ReminderRow[]>([])
const ticked = ref<Record<number, boolean>>({})
const fees = ref<Record<number, BulkFeeType>>({})
const dueDate = ref("")
const step = ref(0)
const loaded = ref(false)
const sending = ref(false)
const failure = ref<string | null>(null)
const sent = ref<number | null>(null)

const feeOptions = (Object.values(BulkFeeType) as BulkFeeType[]).map((value) => ({title: feeTypeLabels[value], value}))
const tomorrow = new Date(Date.now() + 86_400_000).toISOString().slice(0, 10)

const writable = computed(() => rows.value.filter((one) => one.leftOut === null))
const leftOut = computed(() => rows.value.filter((one) => one.leftOut !== null))
const chosen = computed(() => writable.value.filter((one) => ticked.value[one.userId]))
const feeOf = (one: ReminderRow) => fees.value[one.userId] ?? one.row.feeType ?? BulkFeeType.FULL_YEAR_FEE
const amountOf = (one: ReminderRow) => effectiveAmount(feeOf(one), period.value) ?? 0
const dueDateValid = computed(() => dueDate.value >= tomorrow)

const {open: previewOpen, loading: previewLoading, error: previewError, preview, show: showPreview} = useEmailPreview()

/** The real email that member gets, rendered by the api with the date and fee chosen here. */
const previewFor = (one: ReminderRow) => showPreview(async () => {
  const {data} = await readOneEmail({
    kind: ContributionEmailKind.REMINDER,
    contributionPeriodId: periodId.value,
    userId: one.userId,
    date: dueDate.value,
    feeType: feeOf(one),
  })
  return data ? {subject: data.subject, html: data.html, recipientEmail: data.recipientEmail, recipientName: data.recipientName} : null
})

const send = async () => {
  if (sending.value || chosen.value.length === 0 || !dueDateValid.value) return
  sending.value = true
  failure.value = null
  try {
    const overrides = Object.fromEntries(chosen.value
      .filter((one) => fees.value[one.userId] && fees.value[one.userId] !== one.row.feeType)
      .map((one) => [one.userId, fees.value[one.userId]!]))
    const {data, error} = await sendTheEmails({
      contributionPeriodId: periodId.value,
      userIds: chosen.value.map((one) => one.userId),
      forciblyIncludedUserIds: chosen.value.filter((one) => one.row.disposition === "WARNING").map((one) => one.userId),
      kindOverrides: {},
      paymentDueDate: dueDate.value,
      feeTypeOverrides: overrides,
    })
    if (!data) {
      failure.value = (error as {detail?: string} | undefined)?.detail ?? "The reminders could not be sent."
      return
    }
    sent.value = data.remindersSent
    step.value = 3
  } finally {
    sending.value = false
  }
}

onMounted(async () => {
  try {
    const [periods, preview] = await Promise.all([listPeriods(), ids.value.length ? readSelection(periodId.value, ids.value) : Promise.resolve(null)])
    period.value = periods.find((one) => one.id === periodId.value) ?? null
    rows.value = reminderRows(preview?.data?.rows ?? [], period.value?.startDate ?? "")
    ticked.value = Object.fromEntries(rows.value.filter((one) => one.leftOut === null).map((one) => [one.userId, true]))
  } catch (error) {
    $handleNetworkError(error)
  } finally {
    loaded.value = true
  }
})
</script>

<template>
  <div
    class="reminders"
    data-testid="payment-reminders"
  >
    <router-link
      class="reminders__back"
      :to="back"
    >
      Contributions
    </router-link>
    <h1 class="reminders__title">
      Payment reminders
    </h1>

    <ol
      class="reminders__steps"
      data-testid="payment-reminders-steps"
    >
      <li
        v-for="(name, index) in STEPS"
        :key="name"
        :aria-current="step === index ? 'step' : undefined"
        :class="{'reminders__step--on': step === index}"
      >
        {{ index + 1 }}. {{ name }}
      </li>
    </ol>

    <p
      v-if="loaded && rows.length === 0"
      class="reminders__note"
      data-testid="payment-reminders-empty"
    >
      Nobody is selected. Pick the people on Contributions first.
    </p>

    <section
      v-else-if="step === 0"
      data-testid="payment-reminders-who"
    >
      <ul class="reminders__rows">
        <li
          v-for="one in writable"
          :key="one.userId"
          :data-testid="`payment-reminders-row-${one.userId}`"
        >
          <label>
            <input
              v-model="ticked[one.userId]"
              :data-testid="`payment-reminders-tick-${one.userId}`"
              type="checkbox"
            >
            {{ one.name }}
          </label>
          <span class="reminders__sub">{{ reminderName(one) }}</span>
          <span
            v-if="one.remindedBefore"
            class="reminders__warn"
            :data-testid="`payment-reminders-before-${one.userId}`"
          >Reminded before on {{ formatBulkDate(one.remindedBefore) }}</span>
        </li>
      </ul>
      <h2 v-if="leftOut.length">
        Left out
      </h2>
      <ul class="reminders__rows">
        <li
          v-for="one in leftOut"
          :key="one.userId"
          :data-testid="`payment-reminders-left-out-${one.userId}`"
        >
          <span>{{ one.name }}</span>
          <span class="reminders__sub">{{ one.leftOut }}</span>
        </li>
      </ul>
    </section>

    <section
      v-else-if="step === 1"
      data-testid="payment-reminders-fees"
    >
      <ul class="reminders__rows">
        <li
          v-for="one in chosen"
          :key="one.userId"
        >
          <span>{{ one.name }}</span>
          <v-select
            density="compact"
            :data-testid="`payment-reminders-fee-${one.userId}`"
            hide-details
            :items="feeOptions"
            :model-value="feeOf(one)"
            @update:model-value="fees[one.userId] = $event"
          />
          <span class="reminders__sub">€ {{ amountOf(one).toFixed(2) }}</span>
        </li>
      </ul>
    </section>

    <section
      v-else-if="step === 2"
      data-testid="payment-reminders-check"
    >
      <v-text-field
        v-model="dueDate"
        data-testid="payment-reminders-due-date"
        :error-messages="dueDate && !dueDateValid ? 'A due date must be after today.' : undefined"
        label="Pay by"
        :min="tomorrow"
        type="date"
      />
      <ul class="reminders__rows">
        <li
          v-for="one in chosen"
          :key="one.userId"
        >
          <span>{{ one.name }}</span>
          <span class="reminders__sub">{{ reminderName(one) }}, € {{ amountOf(one).toFixed(2) }}</span>
          <button
            class="reminders__action"
            :data-testid="`payment-reminders-preview-${one.userId}`"
            :disabled="!dueDateValid"
            type="button"
            @click="previewFor(one)"
          >
            Preview
          </button>
        </li>
      </ul>
      <p
        v-if="failure"
        class="reminders__warn"
        data-testid="payment-reminders-failure"
        role="alert"
      >
        {{ failure }}
      </p>
    </section>

    <section
      v-else
      data-testid="payment-reminders-sent"
    >
      <p>{{ sent }} reminder{{ sent === 1 ? "" : "s" }} sent. Each counts as that member's last payment email.</p>
      <router-link :to="back">
        Back to Contributions
      </router-link>
    </section>

    <div
      v-if="rows.length > 0 && step < 3"
      class="reminders__nav"
    >
      <button
        v-if="step > 0"
        class="reminders__action"
        data-testid="payment-reminders-previous"
        type="button"
        @click="step -= 1"
      >
        Back
      </button>
      <button
        v-if="step < 2"
        class="reminders__action reminders__action--main"
        data-testid="payment-reminders-next"
        :disabled="chosen.length === 0"
        type="button"
        @click="step += 1"
      >
        Next
      </button>
      <button
        v-else
        class="reminders__action reminders__action--main"
        data-testid="payment-reminders-send"
        :disabled="chosen.length === 0 || !dueDateValid || sending"
        type="button"
        @click="send"
      >
        Send {{ chosen.length }} reminder{{ chosen.length === 1 ? "" : "s" }}
      </button>
    </div>

    <email-preview-dialog
      v-model="previewOpen"
      :error="previewError"
      :loading="previewLoading"
      :preview="preview"
      title="Payment reminder"
    />
  </div>
</template>

<style scoped>
.reminders {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  max-width: 52rem;
  padding: 2rem 2.4rem 3rem;
}

.reminders__back {
  align-self: flex-start;
  font-size: 0.84rem;
  color: var(--color-brand);
}

.reminders__title {
  margin: 0;
  font-family: var(--font-display);
  font-size: clamp(1.4rem, 3vw, 2rem);
}

.reminders__steps {
  display: flex;
  flex-wrap: wrap;
  gap: 1rem;
  margin: 0;
  padding: 0;
  list-style: none;
  color: var(--color-ash);
}

.reminders__step--on {
  color: var(--color-chalk);
  box-shadow: inset 0 -2px 0 var(--color-brand);
}

.reminders h2 {
  margin: 1rem 0 0.4rem;
  font-size: 0.8rem;
  letter-spacing: 0.2em;
  text-transform: uppercase;
  color: var(--color-ash);
}

.reminders__rows {
  display: flex;
  flex-direction: column;
  margin: 0;
  padding: 0;
  list-style: none;
  border-top: 1px solid var(--color-hairline);
}

.reminders__rows li {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.4rem 1rem;
  padding: 0.6rem 0.4rem;
  border-bottom: 1px solid var(--color-hairline);
}

.reminders__rows li > :first-child {
  flex: 1 1 12rem;
}

.reminders__sub {
  font-size: 0.84rem;
  color: var(--color-ash);
}

.reminders__warn {
  margin: 0;
  font-size: 0.84rem;
  color: var(--color-warning);
}

.reminders__note {
  margin: 0;
  color: var(--color-ash);
}

.reminders__nav {
  display: flex;
  gap: 0.6rem;
  justify-content: flex-end;
}

.reminders__action {
  padding: 0.4rem 0.9rem;
  border: 1px solid var(--color-hairline);
  background: none;
  font: inherit;
  font-size: 0.86rem;
  color: var(--color-chalk);
  cursor: pointer;
}

.reminders__action--main {
  border-color: var(--color-brand);
  color: var(--color-brand);
}

.reminders__action:disabled {
  opacity: 0.45;
  cursor: default;
}

@media (max-width: 839px) {
  .reminders {
    padding: 1.2rem 1.1rem 2rem;
  }
}
</style>
