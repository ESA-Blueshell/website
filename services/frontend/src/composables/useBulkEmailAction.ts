import {computed, ref, watch, type ComputedRef, type Ref} from "vue"
import {DateTime} from "luxon"
import {useBulkPreview, type BulkRow} from "@/composables/useBulkPreview"
import {useEmailPreview} from "@/composables/useEmailPreview"
import {useSubmitFeedback, type SubmitState} from "@/composables/formUtils"
import type {ContributionPeriodResponse} from "@/services/api"
import type {
  BulkContributionReminderExecuteRequest,
  BulkIncassoNotificationExecuteRequest,
  ContributionReminderPreviewRequest,
  IncassoNotificationPreviewRequest,
} from "@/services/api/blueshell/types.gen"
import type {BulkTarget} from "@/utils/bulkTarget"
import {effectiveAmount, feeTypeLabels, type FeeType} from "@/utils/feePreview"
import {halfYearCutoffDefault} from "@/utils/bulkTarget"
import type {BulkActionCounts} from "@/utils/bulkRow"

/**
 * The execute-request body type for either bulk email action.
 * Both share the same shape; the date field name differs.
 */
export type BulkEmailExecuteBody =
  | BulkContributionReminderExecuteRequest
  | BulkIncassoNotificationExecuteRequest

/**
 * The preview-request body type for either bulk email action.
 */
export type BulkEmailPreviewBody =
  | ContributionReminderPreviewRequest
  | IncassoNotificationPreviewRequest

/**
 * Configuration for a bulk email action (reminder or incasso notification) that
 * applies fee resolution, inclusion/exclusion logic, and per-row overrides over
 * a contribution period. Named for the shared concept, not the consumers.
 *
 * The two current consumers (reminder/incasso dialogs) parameterize the same logic
 * via this config: API endpoints, date field names, row computation, and Reminder-only
 * extras (struck rendering, lastReminded fetch). Future email-style actions can slot
 * in without renaming the composable.
 */
export interface BulkEmailActionConfig {
  // Date field ref name: "paymentDueDate" (reminder) or "expectedIncassoDate" (incasso)
  dateFieldName: "paymentDueDate" | "expectedIncassoDate"
  dateLabel: string
  dateTestid: string
  // Validation rule for the date field (after today, e.g.)
  dateValidationRule: (v: string, serverToday: string) => true | string
  // Row compute function: computeReminderRows or computeIncassoRows
  computeRows: (targets: BulkTarget[], period: ContributionPeriodResponse | null, cutoff: string) => BulkRow[]
  // API submit and preview functions — typed to the real generated SDK types
  executeApi: (body: BulkEmailExecuteBody) => Promise<{data?: unknown}>
  previewApi: (body: BulkEmailPreviewBody) => Promise<{data?: {subject: string; html: string} | null}>
  // Columns displayed in the table (reminder includes lastReminded, incasso doesn't)
  columns: Array<{key: string; header: string; sortable?: boolean; align?: string; width?: string}>
  // Help text for the action
  help: {title: string; body: string}
  // Reminder-only: should this row render struck-through (incasso-payer, not re-included)?
  isStruck?: (row: BulkRow, reincludeOverrides: Record<number, boolean>) => boolean
  // Reminder-only: fetch lastRemindedAt on dialog open
  loadLastSent?: () => Promise<Record<number, string>>
}

export interface BulkEmailActionProps {
  modelValue: boolean
  targets: BulkTarget[]
  period: ContributionPeriodResponse | null
  serverToday: string
  latestPeriod?: ContributionPeriodResponse | null
}

/** The scaffold exposes a single validate() that the composable calls on preview/confirm. */
export interface BulkScaffoldInstance {
  validate: () => Promise<boolean>
}

/** Explicit return-type contract for useBulkEmailAction. */
export interface BulkEmailActionComposable {
  // Refs/state
  cutoffDate: Ref<string>
  feeTypeSelections: Ref<Record<number, FeeType>>
  lastSentAt: Ref<Record<number, string>>
  // Computed/derived
  boundsPeriod: ComputedRef<ContributionPeriodResponse | null>
  periodInfo: ComputedRef<{range: string; fees: Array<{label: string; icon: string}>} | null>
  cutoffRules: Array<(v: string) => true | string>
  dateRules: Array<(v: string) => true | string>
  computedRows: ComputedRef<BulkRow[]>
  previewUserOptions: ComputedRef<Array<{value: number; title: string}>>
  previewInputsReady: ComputedRef<boolean>
  datesValid: ComputedRef<boolean>
  canConfirm: ComputedRef<boolean>
  // Bulk preview state
  rows: Ref<BulkRow[]>
  counts: ComputedRef<BulkActionCounts>
  includedUserIds: ComputedRef<number[]>
  reincludeOverrides: Ref<Record<number, boolean>>
  submitting: Ref<boolean>
  // Email preview state (nested refs; template accesses .value explicitly)
  emailPreview: {
    selectedUserId: Ref<number | null>
    dialogOpen: Ref<boolean>
    loading: Ref<boolean>
    error: Ref<string | null>
    subject: Ref<string | null>
    html: Ref<string | null>
    runPreview: (fetcher: (userId: number) => Promise<{subject: string; html: string} | null>) => Promise<void>
    reset: () => void
  }
  submitState: Ref<SubmitState>
  showSubmitStatus: Ref<boolean>
  // Helper functions
  fmtDate: (iso: string) => string
  fmtFee: (amount: number) => string
  rowAmount: (row: BulkRow) => number | null
  isEditable: (row: BulkRow) => boolean
  feeLabel: (row: BulkRow) => string
  lastSentLabel: (userId: number) => string
  isStruck: (row: BulkRow, reincludeOverrides: Record<number, boolean>) => boolean
  seedFeeSelections: (newRows: BulkRow[]) => void
  // Action handlers
  onPreview: () => Promise<void>
  onConfirm: () => Promise<boolean | undefined>
}

/**
 * Shared composable for bulk email actions (reminders, incasso notifications).
 * Encapsulates row computation, date validation, fee-type seeding/overrides,
 * preview wiring (recipient selection + API fetching), included/excluded counts,
 * and submit orchestration. Both dialogs' <script setup> shrink to config + template glue.
 *
 * @param dateRef Mutable ref for the date field value (passed in because the dialog owns it)
 * @param props Dialog props (modelValue, targets, period, serverToday, latestPeriod)
 * @param config Action-specific config (APIs, date field, row compute, columns, help)
 * @param scaffoldRef Template ref for the BulkDialogScaffold instance; the dialog creates
 *   a local ref (e.g. `const scaffoldRef = ref<BulkScaffoldInstance | null>(null)`) and binds
 *   it with `:ref="scaffoldRef"` so that validate() calls actually reach the mounted component.
 */
export function useBulkEmailAction(
  dateRef: Ref<string>,
  props: BulkEmailActionProps,
  config: BulkEmailActionConfig,
  scaffoldRef: Ref<BulkScaffoldInstance | null>,
): BulkEmailActionComposable {
  // ── Composable state ──────────────────────────────────────────────────────
  const cutoffDate = ref("")
  const feeTypeSelections = ref<Record<number, FeeType>>({})
  const lastSentAt = ref<Record<number, string>>({})

  const bulkPreview = useBulkPreview()
  const emailPreview = useEmailPreview()
  const {submitState, showSubmitStatus, setSubmitResult} = useSubmitFeedback()

  // ── Derived state ────────────────────────────────────────────────────────
  const boundsPeriod = computed(() => props.period ?? props.latestPeriod ?? null)

  // Format date as dd/MM/yyyy
  function fmtDate(iso: string): string {
    const dt = DateTime.fromISO(iso)
    return dt.isValid ? dt.toFormat("dd/MM/yyyy") : iso
  }

  // Format amount as €X.XX
  function fmtFee(amount: number): string {
    return `€${amount.toFixed(2)}`
  }

  // Contribution-period summary: date range + fees
  const periodInfo = computed(() => {
    const p = boundsPeriod.value
    if (!p) return null
    return {
      range: `${fmtDate(p.startDate)} – ${fmtDate(p.endDate)}`,
      fees: [
        {label: `Full year ${fmtFee(p.fullYearFee)}`, icon: "mdi-account"},
        {label: `Half year ${fmtFee(p.halfYearFee)}`, icon: "mdi-account-clock"},
        {label: `Alumni ${fmtFee(p.alumniFee)}`, icon: "mdi-school"},
      ],
    }
  })

  // Cutoff-date validation rules: required + must fall within bounds period
  const cutoffRules = [
    (v: string) => !!v || "Half-year cutoff date is required.",
    (v: string) => {
      const p = boundsPeriod.value
      if (!v || !p) return true
      if (v < p.startDate || v > p.endDate) {
        return `Cutoff date must fall within the contribution period (${fmtDate(p.startDate)} to ${fmtDate(p.endDate)}).`
      }
      return true
    },
  ]

  // Date field validation rules: required + config-supplied rule (e.g., must be after today)
  const dateRules = [
    (v: string) => !!v || `${config.dateLabel} is required.`,
    (v: string) => {
      if (!v || !props.serverToday) return true
      return config.dateValidationRule(v, props.serverToday)
    },
  ]

  // Computed rows from targets, period, and cutoffDate
  const computedRows = computed(() =>
    config.computeRows(props.targets, props.period, cutoffDate.value),
  )

  // Get the amount for a row given its selected fee type
  function rowAmount(row: BulkRow): number | null {
    const selected = feeTypeSelections.value[row.userId] ?? row.recommendedFeeType
    return effectiveAmount(selected, props.period)
  }

  // Row is editable if INCLUDED or re-included WARNING
  function isEditable(row: BulkRow): boolean {
    return row.disposition === "INCLUDED" ||
      (row.disposition === "WARNING" && !!bulkPreview.reincludeOverrides.value[row.userId])
  }

  // Get the readable fee-type label for a non-editable row
  function feeLabel(row: BulkRow): string {
    return row.recommendedFeeType ? feeTypeLabels[row.recommendedFeeType] : ""
  }

  // Read-only label for last-sent date (Reminder only; Incasso passes undefined config.loadLastSent)
  function lastSentLabel(userId: number): string {
    const iso = lastSentAt.value[userId]
    if (!iso) return "Never"
    const dt = DateTime.fromISO(iso)
    return dt.isValid ? dt.toFormat("dd/MM/yyyy") : "Never"
  }

  // Selectable preview recipients: currently-included users
  const previewUserOptions = computed(() => {
    const included = new Set(bulkPreview.includedUserIds.value)
    return bulkPreview.rows.value
      .filter((r) => included.has(r.userId))
      .map((r) => ({value: r.userId, title: r.name}))
  })

  // Default preview recipient to first included user; keep valid as rows change
  watch(
    previewUserOptions,
    (options) => {
      const current = emailPreview.selectedUserId.value
      if (options.length === 0) {
        emailPreview.selectedUserId.value = null
      } else if (current == null || !options.some((o) => o.value === current)) {
        emailPreview.selectedUserId.value = options[0]!.value
      }
    },
    {immediate: true},
  )

  // Preview needs period and date field to render faithfully
  const previewInputsReady = computed(() => !!props.period && !!dateRef.value)

  // Guard: dates must satisfy the same rules the v-form enforces
  const datesValid = computed(() => {
    if (!dateRef.value || !cutoffDate.value) return false
    if (props.serverToday && dateRef.value <= props.serverToday) return false
    const p = boundsPeriod.value
    if (p && (cutoffDate.value < p.startDate || cutoffDate.value > p.endDate)) return false
    return true
  })

  const canConfirm = computed(() => bulkPreview.includedUserIds.value.length > 0 && !bulkPreview.submitting.value)

  // Re-seed fee selections from computed rows' recommendations
  function seedFeeSelections(newRows: BulkRow[]) {
    const selections: Record<number, FeeType> = {}
    for (const row of newRows) {
      if (row.recommendedFeeType) selections[row.userId] = row.recommendedFeeType
    }
    feeTypeSelections.value = selections
  }

  // Validate and run preview; config.previewApi is called with dialog-specific parameters
  async function onPreview() {
    const valid = await scaffoldRef.value?.validate()
    if (!valid) return
    const periodId = props.period?.id
    if (periodId == null || !dateRef.value) return
    await emailPreview.runPreview(async (userId) => {
      const feeType: FeeType = feeTypeSelections.value[userId] ?? "FULL_YEAR_FEE"
      const body = {
        userId,
        contributionPeriodId: periodId,
        feeType,
        [config.dateFieldName]: dateRef.value,
      } as BulkEmailPreviewBody
      const resp = await config.previewApi(body)
      return resp.data ?? null
    })
  }

  // Build the API request body and submit; handles fee-type overrides for included users
  async function onConfirm() {
    if (!canConfirm.value || !datesValid.value || !props.period) return
    // Collect number-keyed overrides internally, then convert to string keys for the API
    // (generated API types declare string keys as the JSON-boundary type).
    const numberKeyedOverrides: Record<number, FeeType> = {}
    for (const userId of bulkPreview.includedUserIds.value) {
      const feeType = feeTypeSelections.value[userId]
      if (feeType) numberKeyedOverrides[userId] = feeType
    }
    // Convert number keys to string keys for the API request
    const overrides: Record<string, FeeType> = {}
    for (const [userIdStr, feeType] of Object.entries(numberKeyedOverrides)) {
      overrides[userIdStr] = feeType
    }
    const body = {
      userIds: props.targets.map((t) => t.userId),
      contributionPeriodId: props.period.id,
      includedUserIds: bulkPreview.includedUserIds.value,
      cutoffDate: cutoffDate.value,
      [config.dateFieldName]: dateRef.value,
      feeTypeOverrides: overrides,
    } as BulkEmailExecuteBody
    const ok = await bulkPreview.submit(async () => {
      const resp = await config.executeApi(body)
      return resp.data != null
    })
    setSubmitResult(ok)
    return ok
  }

  // Lifecycle: initialize dates on open, compute rows reactively
  watch(
    () => props.modelValue,
    (isOpen) => {
      if (isOpen) {
        dateRef.value = ""
        cutoffDate.value = halfYearCutoffDefault(boundsPeriod.value)
        seedFeeSelections(computedRows.value)
        bulkPreview.setRows(computedRows.value)
        if (config.loadLastSent) {
          void config.loadLastSent().then((data) => {
            lastSentAt.value = data
          })
        }
      } else {
        bulkPreview.reset()
        emailPreview.reset()
        feeTypeSelections.value = {}
        lastSentAt.value = {}
      }
    },
    {immediate: true},
  )

  // Recompute rows and re-seed fee selections whenever cutoff (or targets) change
  watch(computedRows, (newRows) => {
    if (props.modelValue) {
      seedFeeSelections(newRows)
      bulkPreview.setRows(newRows)
    }
  })

  // Wrapper for isStruck so it works even if undefined (for Incasso)
  const isStruckFunc = (row: BulkRow, overrides: Record<number, boolean>) => {
    if (!config.isStruck) return false
    return config.isStruck(row, overrides)
  }

  return {
    // Refs/state
    cutoffDate,
    feeTypeSelections,
    lastSentAt,
    // Computed/derived
    boundsPeriod,
    periodInfo,
    cutoffRules,
    dateRules,
    computedRows,
    previewUserOptions,
    previewInputsReady,
    datesValid,
    canConfirm,
    // Bulk preview state (rows, counts, includedUserIds, reincludeOverrides, submitting)
    rows: bulkPreview.rows,
    counts: bulkPreview.counts,
    includedUserIds: bulkPreview.includedUserIds,
    reincludeOverrides: bulkPreview.reincludeOverrides,
    submitting: bulkPreview.submitting,
    // Email preview state
    emailPreview,
    submitState,
    showSubmitStatus,
    // Helper functions
    fmtDate,
    fmtFee,
    rowAmount,
    isEditable,
    feeLabel,
    lastSentLabel,
    isStruck: isStruckFunc,
    seedFeeSelections,
    // Action handlers
    onPreview,
    onConfirm,
  }
}
