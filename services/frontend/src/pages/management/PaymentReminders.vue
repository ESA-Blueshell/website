<script lang="ts" setup>
/* Payment reminders for members paying by transfer, as four steps: who gets one, which fee each
   pays, the due date and a check of what will be sent, then sent. Nothing goes before Send. */
import {computed, onMounted, ref} from "vue"
import {useRoute} from "vue-router"
import EmailPreviewDialog from "@/components/common/modals/EmailPreviewDialog.vue"
import CutButton from "@/components/island/CutButton.vue"
import DateInput from "@/components/island/DateInput.vue"
import FormField from "@/components/island/FormField.vue"
import SearchPicker from "@/components/island/SearchPicker.vue"
import StateMark from "@/components/island/StateMark.vue"
import ListHead from "@/components/management/ListHead.vue"
import ManagementPage from "@/components/management/ManagementPage.vue"
import ManagementTable, {type TableColumn} from "@/components/management/ManagementTable.vue"
import PersonLink from "@/components/management/PersonLink.vue"
import MiniButton from "@/components/management/MiniButton.vue"
import PairList from "@/components/management/PairList.vue"
import RowCheck from "@/components/management/RowCheck.vue"
import StepStrip from "@/components/management/StepStrip.vue"
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

const feeOptions = (Object.values(BulkFeeType) as BulkFeeType[]).map((value) => ({key: value, label: feeTypeLabels[value]}))

const WHO_COLUMNS: TableColumn[] = [
  {key: "name", label: "Member"},
  {key: "what", label: "Reminder", wrap: true},
  {key: "before", label: "Reminded before", wrap: true},
]
const FEE_COLUMNS: TableColumn[] = [
  {key: "name", label: "Member"},
  {key: "fee", label: "Fee type"},
  {key: "amount", label: "Amount"},
]
const CHECK_COLUMNS: TableColumn[] = [
  {key: "name", label: "Member"},
  {key: "what", label: "Reminder", wrap: true},
  {key: "amount", label: "Amount"},
]
const tomorrow = new Date(Date.now() + 86_400_000).toISOString().slice(0, 10)

const writable = computed(() => rows.value.filter((one) => one.leftOut === null))
const leftOut = computed(() => rows.value.filter((one) => one.leftOut !== null))
const chosen = computed(() => writable.value.filter((one) => ticked.value[one.userId]))
const leftOutPairs = computed(() => leftOut.value.map((one) => ({label: one.name, value: one.leftOut ?? "", testid: `payment-reminders-left-out-${one.userId}`})))
const euro = (amount: number) => `€ ${amount.toFixed(2)}`
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
  <management-page
    :back="{to: back, label: 'Contributions'}"
    eyebrow="Contributions"
    testid="payment-reminders"
    title="Payment reminders"
  >
    <template #lede>
      Email members who pay by transfer what they owe and by when. Each email can be previewed before anything is sent.
    </template>

    <step-strip
      :current="step"
      :steps="STEPS"
      testid="payment-reminders-steps"
    />

    <p
      v-if="loaded && rows.length === 0"
      class="reminders__note"
      data-testid="payment-reminders-empty"
    >
      Nobody is selected. Go back to Contributions and tick the people first.
    </p>

    <section
      v-else-if="step === 0"
      class="reminders__stage"
      data-testid="payment-reminders-who"
    >
      <h2 class="reminders__heading">
        Who gets a reminder
      </h2>
      <management-table
        :columns="WHO_COLUMNS"
        :row-key="(one) => one.userId"
        :row-testid="(one) => `payment-reminders-row-${one.userId}`"
        search-label="Search members"
        :search-text="(one) => one.name"
        :rows="writable"
      >
        <template #check="{row}">
          <row-check
            :checked="ticked[row.userId] === true"
            :label="`Include ${row.name}`"
            :testid="`payment-reminders-tick-${row.userId}`"
            @toggle="ticked[row.userId] = !ticked[row.userId]"
          />
        </template>
        <template #name="{row}">
          <person-link
            :name="row.name"
            :user-id="row.userId"
          />
        </template>
        <template #what="{row}">
          <span class="mg-quiet">{{ reminderName(row) }}</span>
        </template>
        <template #before="{row}">
          <state-mark
            v-if="row.remindedBefore"
            kind="extra"
            :testid="`payment-reminders-before-${row.userId}`"
          >
            Reminded before on {{ formatBulkDate(row.remindedBefore) }}
          </state-mark>
          <span
            v-else
            class="mg-quiet"
          >Not yet</span>
        </template>
      </management-table>

      <template v-if="leftOut.length">
        <list-head :title="`Left out · ${leftOut.length}`" />
        <pair-list :pairs="leftOutPairs" />
      </template>
    </section>

    <section
      v-else-if="step === 1"
      class="reminders__stage"
      data-testid="payment-reminders-fees"
    >
      <h2 class="reminders__heading">
        What each is asked to pay
      </h2>
      <management-table
        :columns="FEE_COLUMNS"
        :row-key="(one) => one.userId"
        search-label="Search members"
        :search-text="(one) => one.name"
        :rows="chosen"
      >
        <template #name="{row}">
          <person-link
            :name="row.name"
            :user-id="row.userId"
          />
        </template>
        <template #fee="{row}">
          <search-picker
            class="reminders__fee"
            compact
            :options="feeOptions"
            :selected-key="feeOf(row)"
            :testid-prefix="`payment-reminders-fee-${row.userId}`"
            @pick="fees[row.userId] = $event as BulkFeeType"
          />
        </template>
        <template #amount="{row}">
          {{ euro(amountOf(row)) }}
        </template>
      </management-table>
    </section>

    <section
      v-else-if="step === 2"
      class="reminders__stage"
      data-testid="payment-reminders-check"
    >
      <h2 class="reminders__heading">
        {{ chosen.length }} reminder{{ chosen.length === 1 ? "" : "s" }} will be sent
      </h2>
      <div class="reminders__fields">
        <form-field
          v-slot="field"
          :error="dueDate && !dueDateValid ? 'The date to pay by has to be after today.' : ''"
          label="Pay by"
          testid="payment-reminders-due-date"
        >
          <date-input
            v-model="dueDate"
            :control-id="field.controlId"
            :invalid="field.invalid"
            :min="tomorrow"
          />
        </form-field>
      </div>
      <management-table
        :columns="CHECK_COLUMNS"
        :row-key="(one) => one.userId"
        search-label="Search members"
        :search-text="(one) => one.name"
        :rows="chosen"
      >
        <template #name="{row}">
          <person-link
            :name="row.name"
            :user-id="row.userId"
          />
        </template>
        <template #what="{row}">
          <span class="mg-quiet">{{ reminderName(row) }}</span>
        </template>
        <template #amount="{row}">
          {{ euro(amountOf(row)) }}
        </template>
        <template #acts="{row}">
          <mini-button
            :disabled="!dueDateValid"
            :testid="`payment-reminders-preview-${row.userId}`"
            @click="previewFor(row)"
          >
            Preview
          </mini-button>
        </template>
      </management-table>
      <p
        v-if="failure"
        class="reminders__failure"
        data-testid="payment-reminders-failure"
        role="alert"
      >
        {{ failure }}
      </p>
    </section>

    <section
      v-else
      class="reminders__stage"
      data-testid="payment-reminders-sent"
    >
      <h2 class="reminders__heading">
        {{ sent }} reminder{{ sent === 1 ? "" : "s" }} sent
      </h2>
      <p class="reminders__note">
        Each counts as that member's last payment email.
      </p>
      <div class="reminders__acts">
        <cut-button :href="back">
          Back to contributions
        </cut-button>
      </div>
    </section>

    <div
      v-if="rows.length > 0 && step < 3"
      class="reminders__acts"
    >
      <cut-button
        v-if="step < 2"
        :disabled="chosen.length === 0"
        testid="payment-reminders-next"
        tone="solid"
        @click="step += 1"
      >
        {{ step === 0 ? "Next: fees" : "Next: check" }}
      </cut-button>
      <cut-button
        v-else
        :disabled="chosen.length === 0 || !dueDateValid || sending"
        testid="payment-reminders-send"
        tone="solid"
        @click="send"
      >
        Send {{ chosen.length }} reminder{{ chosen.length === 1 ? "" : "s" }}
      </cut-button>
      <cut-button
        v-if="step > 0"
        testid="payment-reminders-previous"
        tone="quiet"
        @click="step -= 1"
      >
        Back
      </cut-button>
      <cut-button
        v-else
        :href="back"
        tone="quiet"
      >
        Cancel
      </cut-button>
    </div>

    <email-preview-dialog
      v-model="previewOpen"
      :error="previewError"
      :loading="previewLoading"
      :preview="preview"
      title="Payment reminder"
    />
  </management-page>
</template>

<style scoped>
.reminders__stage {
  display: flex;
  flex-direction: column;
  gap: 1.15rem;
  padding-top: 1.6rem;
}

.reminders__heading {
  font-size: 2.1rem;
  line-height: 1.02;
  text-transform: uppercase;
}

.reminders__fields {
  max-width: 16rem;
}

.reminders__fee {
  min-width: 12rem;
}

.reminders__acts {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.6rem;
  padding-top: 1rem;
}

.reminders__note {
  margin-top: 0.4rem;
  color: var(--color-ash);
}

.reminders__failure {
  color: var(--color-danger);
}

@media (--phone) {
  .reminders__heading {
    font-size: 1.5rem;
  }
}
</style>
