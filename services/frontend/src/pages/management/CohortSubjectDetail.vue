<script lang="ts" setup>
import {computed, onMounted, ref, watch} from "vue"
import {useRoute, useRouter} from "vue-router"
import TopBanner from "@/components/common/banners/TopBanner.vue"
import {$handleNetworkError} from "@/plugins/handleNetworkError"
import {
  type CohortSubjectDetail,
  CohortSubjectCategory,
  CohortSubjectType,
  TargetSystem,
  enqueue,
  findCohortSubjectById,
} from "@/services/api"
import InfoBox from "@/components/common/panels/InfoBox.vue"
import {cohortTypeLabel} from "@/domains/cohorts/cohortTypeLabels"
import {
  countLabel,
  rulesSummary,
  targetsSummary,
} from "@/domains/cohorts/cohortSubjectSummaries"
import {
  linkUserToExternal,
  removeExternalMember,
  triggerReconcile,
  type ExternalUserConflict,
} from "@/domains/cohorts/adapters/cohorts"
import UserPicker from "@/components/form/fields/UserPicker.vue"
import InboundReconcileModal from "@/domains/cohorts/components/InboundReconcileModal.vue"
import TargetPickerModal from "@/domains/cohorts/components/TargetPickerModal.vue"
import {filtersFor, useRowFilters} from "@/composables/useRowFilters"
import {useTableSort} from "@/composables/useTableSort"
import store from "@/plugins/store"

defineOptions({name: "CohortSubjectDetailPage"})

const route = useRoute()
const router = useRouter()

const subject = ref<CohortSubjectDetail | null>(null)
const loading = ref<boolean>(false)
const triggering = ref<string | null>(null)
const errorMessage = ref<string | null>(null)
const successMessage = ref<string | null>(null)
const activeTab = ref<string>("")

const pickerOpen = ref<boolean>(false)
const pickerMode = ref<"add" | "switch">("add")
const pickerSystem = ref<TargetSystem>(TargetSystem.BREVO)
const pickerCohortId = ref<number | undefined>(undefined)
const inboundOpen = ref<boolean>(false)
const inboundCohortId = ref<number | undefined>(undefined)

const subjectId = computed<number | null>(() => {
  const raw = route.params.id
  const value = typeof raw === "string" ? Number(raw) : Number(Array.isArray(raw) ? raw[0] : raw)
  return Number.isFinite(value) ? value : null
})

const CATEGORY_LABELS: Record<CohortSubjectCategory, string> = {
  [CohortSubjectCategory.COMMITTEES]: "Committees",
  [CohortSubjectCategory.PERIODS]: "Periods",
  [CohortSubjectCategory.MEMBERS]: "Members",
  [CohortSubjectCategory.OTHER]: "Other",
}

/** What this cohort is, said once: where it lives and what kind it is. */
const identity = computed<string>(() =>
  subject.value == null
    ? ""
    : `${CATEGORY_LABELS[subject.value.category]} · ${cohortTypeLabel(subject.value.type as CohortSubjectType)}`,
)

const rulesSubtitle = computed<string>(() => rulesSummary(subject.value?.rules ?? []))

const targetsSubtitle = computed<string>(() => targetsSummary(subject.value?.mappings ?? []))


/**
 * `MEMBER_IN_PERIOD` is how the fact is stored, not how it reads. The key stays as it is —
 * it is an id, and an operator matching it against the database needs it verbatim.
 */
const factKindLabel = (factKind: string): string => {
  const words = factKind.toLowerCase().replace(/_/g, " ")
  return words.charAt(0).toUpperCase() + words.slice(1)
}

const SYSTEM_LABELS: Record<string, string> = {
  BREVO: "Brevo",
  DISCORD: "Discord",
  GOOGLE_WORKSPACE: "Google Workspace",
}

const labelForSystem = (system: string): string => SYSTEM_LABELS[system] ?? system

const load = async () => {
  if (subjectId.value == null) return
  loading.value = true
  try {
    const response = await findCohortSubjectById({path: {id: subjectId.value}})
    subject.value = response.data ?? null
    activeTab.value = subject.value?.mappings[0]?.system ?? ""
  } catch (error) {
    subject.value = null
    $handleNetworkError(error)
  } finally {
    loading.value = false
  }
}

const triggerJob = async (jobType: string, payload: Record<string, unknown>) => {
  triggering.value = jobType
  errorMessage.value = null
  successMessage.value = null
  try {
    const response = await enqueue({body: {jobType, payload}})
    if (response.status === 200 && response.data) {
      successMessage.value = `Job enqueued (#${response.data.id ?? "?"}).`
    } else {
      errorMessage.value = `Failed to enqueue ${jobType}.`
    }
  } catch (error) {
    errorMessage.value = (error as Error)?.message ?? `Failed to enqueue ${jobType}.`
    $handleNetworkError(error)
  } finally {
    triggering.value = null
  }
}

const reevaluateMember = (userId: number) => {
  void triggerJob("cohort.evaluate-user", {userId})
}

/** The date only: a cohort's join time to the second says nothing an operator acts on. */
const formatJoinedAt = (value: string): string => {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleDateString(undefined, {year: "numeric", month: "2-digit", day: "2-digit"})
}

// ── The ledger, as one table ──────────────────────────────────────────────────
//
// Membership and its agreement with the external system are the same rows, so they are one
// table: our members, and beside them the rows that exist only in the target. Each says which
// it is, so nobody has to open a second panel and match ids by eye.

type MemberRow = CohortSubjectDetail["members"][number]

/** The four things a row can be, which is what the Sync column says. */
type SyncState = "IN_SYNC" | "ONLY_HERE" | "ONLY_EXTERNAL" | "BROKEN"

const syncStateOf = (member: MemberRow): SyncState => {
  switch (member.state) {
    case "SYNCED":
    case "VERIFIED":
      return "IN_SYNC"
    case "DESIRED":
      return "ONLY_HERE"
    case "STRANGER":
      return "ONLY_EXTERNAL"
    default:
      // INVALID, and anything a later api adds: a row we cannot vouch for reads as broken
      // rather than as healthy.
      return "BROKEN"
  }
}

const systemLabel = (member: MemberRow): string =>
  member.system == null ? "the target" : labelForSystem(member.system)

/**
 * What each state is called on screen. Only the exceptions are chipped: a cohort of eighty-
 * seven healthy rows would otherwise spend all its colour saying nothing.
 */
const syncLabel = (member: MemberRow): string => {
  switch (syncStateOf(member)) {
    case "IN_SYNC":
      return "In sync"
    case "ONLY_HERE":
      // "yet", because the sync queue resolves this one on its own.
      return `Not in ${systemLabel(member)} yet`
    case "ONLY_EXTERNAL":
      return `Only in ${systemLabel(member)}`
    default:
      return "Broken"
  }
}

const syncChipColour = (member: MemberRow): string | undefined => {
  switch (syncStateOf(member)) {
    case "ONLY_HERE":
      return "info"
    case "ONLY_EXTERNAL":
      return "warning"
    case "BROKEN":
      return "error"
    default:
      return undefined
  }
}

/** A row belonging to somebody here — as opposed to one that only the target knows about. */
const isMember = (member: MemberRow): boolean => syncStateOf(member) !== "ONLY_EXTERNAL"

const memberName = (member: MemberRow): string => {
  if (member.userFullName) return member.userFullName
  if (member.isUserDeleted && member.userId != null) return `Deleted user #${member.userId}`
  if (member.userId != null) return `User #${member.userId}`
  // A stranger nothing local claims: the external system's own label is all there is.
  return member.externalLabel ?? member.externalUserId ?? "Unknown"
}

/** Every ledger row the page holds: our members, and the rows only the target knows. */
const allRows = computed<MemberRow[]>(() => subject.value?.members ?? [])

/** The counts the box states before it is opened, and the badge beside the cohort's name. */
const memberCount = computed(() => allRows.value.filter(isMember).length)

const driftCounts = computed(() => {
  const rows = allRows.value
  return {
    onlyExternal: rows.filter((row) => syncStateOf(row) === "ONLY_EXTERNAL").length,
    onlyHere: rows.filter((row) => syncStateOf(row) === "ONLY_HERE").length,
    broken: rows.filter((row) => syncStateOf(row) === "BROKEN").length,
  }
})

const membersSummaryLine = computed(() => {
  const {onlyExternal, onlyHere, broken} = driftCounts.value
  const parts = [countLabel(memberCount.value, "member")]
  if (onlyHere > 0) parts.push(`${onlyHere} not synced`)
  if (onlyExternal > 0) parts.push(`${onlyExternal} only external`)
  if (broken > 0) parts.push(`${broken} broken`)
  return parts.join(" · ")
})

// ── Filtering ─────────────────────────────────────────────────────────────────

type SyncFilter = "all" | "attention" | SyncState

const filter = filtersFor<MemberRow>()

/**
 * Everything a row can be recognised by, in one string: the name we hold, the address, and
 * the identity the external system knows it as — which for a stranger is all there is.
 */
const searchHaystack = (member: MemberRow): string =>
  [
    memberName(member),
    member.userFullName,
    member.userEmail,
    member.externalLabel,
    member.externalUserId,
  ]
    .filter(Boolean)
    .join(" ")
    .toLowerCase()

const {state: filterState, filteredRows} = useRowFilters(allRows, {
  // Cheapest first: the dropdown is one comparison and rules most rows out before the search
  // has to build a haystack.
  sync: filter<SyncFilter>({
    initial: "all",
    unset: "all",
    match: (value) => {
      // "Needs attention" is the question somebody actually arrives with; the individual
      // states are there to narrow it further.
      if (value === "attention") return (row) => syncStateOf(row) !== "IN_SYNC"
      return (row) => syncStateOf(row) === value
    },
  }),
  search: filter<string | null>({
    initial: "",
    unset: "",
    // The field is `clearable`, and its clear button writes null rather than "".
    isUnset: (value) => (value ?? "").trim() === "",
    match: (value) => {
      const terms = (value ?? "").trim().toLowerCase().split(/\s+/)
      return (row) => {
        const haystack = searchHaystack(row)
        return terms.every((term) => haystack.includes(term))
      }
    },
  }),
})

const SYNC_FILTER_ITEMS = [
  {title: "All", value: "all"},
  {title: "Needs attention", value: "attention"},
  {title: "In sync", value: "IN_SYNC"},
  {title: "Not synced yet", value: "ONLY_HERE"},
  {title: "Only external", value: "ONLY_EXTERNAL"},
  {title: "Broken", value: "BROKEN"},
]

// ── Sorting ───────────────────────────────────────────────────────────────────

type MemberSortKey = "name" | "email" | "joinedAt" | "sync"

const {
  sortedItems: sortedMembers,
  toggleSort: toggleMemberSort,
  sortIcon: memberSortIcon,
  ariaSort: memberAriaSort,
} = useTableSort<MemberRow, MemberSortKey>(filteredRows, {
  name: (a, b) => memberName(a).localeCompare(memberName(b)),
  email: (a, b) => (a.userEmail ?? "").localeCompare(b.userEmail ?? ""),
  joinedAt: (a, b) => a.joinedAt.localeCompare(b.joinedAt),
  // Healthy last, so one click brings the faults together.
  sync: (a, b) => syncLabel(a).localeCompare(syncLabel(b)),
})

const MEMBER_COLUMNS: ReadonlyArray<{label: string; sortKey: MemberSortKey; width: string}> = [
  {label: "Member", sortKey: "name", width: "28%"},
  {label: "Email", sortKey: "email", width: "26%"},
  {label: "Joined", sortKey: "joinedAt", width: "14%"},
  {label: "Sync", sortKey: "sync", width: "22%"},
]

// ── Row actions ───────────────────────────────────────────────────────────────

const removingExternal = ref<string | null>(null)

/** What this row can be acted on with, which is what its menu holds. */
const canReevaluate = (member: MemberRow): boolean =>
  member.userId != null && syncStateOf(member) !== "ONLY_EXTERNAL"

const canRemoveExternal = (member: MemberRow): boolean =>
  syncStateOf(member) === "ONLY_EXTERNAL" && member.externalUserId != null

const canLinkUser = (member: MemberRow): boolean =>
  syncStateOf(member) === "ONLY_EXTERNAL" && member.userId == null && member.externalUserId != null

const hasRowActions = (member: MemberRow): boolean =>
  canReevaluate(member) || canRemoveExternal(member) || canLinkUser(member)

/** A row belongs to one system's ledger; the cohort behind it is that system's mapping. */
const cohortIdFor = (member: MemberRow): number | null =>
  subject.value?.mappings.find((mapping) => mapping.system === member.system)?.cohortId ?? null

const removeExternalRow = async (member: MemberRow) => {
  const cohortId = cohortIdFor(member)
  if (member.externalUserId == null || cohortId == null) return
  removingExternal.value = member.externalUserId
  try {
    await removeExternalMember(cohortId, member.externalUserId)
    successMessage.value = "Removal enqueued."
    await load()
  } catch (error) {
    errorMessage.value = (error as Error)?.message ?? "Could not remove that row."
    $handleNetworkError(error)
  } finally {
    removingExternal.value = null
  }
}

// ── Linking a stranger to an account ──────────────────────────────────────────
//
// An external id that nothing local claims can be pointed at a user by hand. The dialog came
// from the drift panel; it belongs to the row it acts on.

const linkingRow = ref<MemberRow | null>(null)
const linkUserId = ref<number | undefined>(undefined)
const linkSubmitting = ref<boolean>(false)
const linkConflict = ref<ExternalUserConflict | null>(null)

const openLinkUser = (member: MemberRow) => {
  linkingRow.value = member
  linkUserId.value = undefined
  linkConflict.value = null
}

const closeLinkUser = () => {
  linkingRow.value = null
  linkUserId.value = undefined
  linkConflict.value = null
}

const submitLinkUser = async () => {
  const row = linkingRow.value
  if (row?.externalUserId == null || row.system == null || linkUserId.value == null) return
  if (subjectId.value == null) return
  linkSubmitting.value = true
  linkConflict.value = null
  try {
    const result = await linkUserToExternal(
      subjectId.value,
      linkUserId.value,
      row.system as TargetSystem,
      row.externalUserId,
    )
    if (result.type === "conflict") {
      // That external id already points at somebody; saying who is more use than failing.
      linkConflict.value = result.conflict
      return
    }
    closeLinkUser()
    successMessage.value = "External id linked."
    await load()
  } catch (error) {
    errorMessage.value = (error as Error)?.message ?? "Could not link that user."
    $handleNetworkError(error)
  } finally {
    linkSubmitting.value = false
  }
}

// ── Reconciling a target ──────────────────────────────────────────────────────

const reconciling = ref<number | null>(null)

const reconcileTarget = async (cohortId: number) => {
  reconciling.value = cohortId
  try {
    await triggerReconcile(cohortId)
    successMessage.value = "Reconcile enqueued."
  } catch (error) {
    errorMessage.value = (error as Error)?.message ?? "Could not enqueue a reconcile."
    $handleNetworkError(error)
  } finally {
    reconciling.value = null
  }
}

/** What the target row says about its agreement with the external system. */
const lastReconciledLabel = (mapping: {externalId?: string | null; lastReconciledAt?: string | null}): string => {
  if (!mapping.externalId) return "not created in the external system yet"
  if (!mapping.lastReconciledAt) return "never reconciled"
  return `last reconciled ${new Date(mapping.lastReconciledAt).toLocaleDateString(undefined, {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  })}`
}

const openAddTarget = () => {
  pickerMode.value = "add"
  pickerSystem.value = TargetSystem.BREVO
  pickerCohortId.value = undefined
  pickerOpen.value = true
}

const openSwitchTarget = (cohortId: number, system: string) => {
  pickerMode.value = "switch"
  pickerSystem.value = system as TargetSystem
  pickerCohortId.value = cohortId
  pickerOpen.value = true
}

const onTargetSaved = () => {
  successMessage.value = "External target saved."
  void load()
}

const openInboundReconcile = (cohortId: number) => {
  inboundCohortId.value = cohortId
  inboundOpen.value = true
}

const onInboundApplied = () => {
  successMessage.value = "Inbound reconcile job enqueued."
}

const backToCategory = () => {
  if (subject.value == null) return
  void router.push({
    name: "cohortCategory",
    params: {category: subject.value.category.toLowerCase()},
  })
}

onMounted(async () => {
  if (!store.getters.isAdmin) {
    await router.replace("/")
    return
  }
  await load()
})

watch(subjectId, () => void load())
</script>

<template>
  <v-main>
    <top-banner :title="subject?.label ?? 'Cohort'" />

    <v-container>
      <div class="mx-auto my-3 subject-page">
        <v-btn
          v-if="subject"
          class="mb-3"
          data-testid="cohort-subject-back"
          prepend-icon="mdi-arrow-left"
          size="small"
          variant="text"
          @click="backToCategory"
        >
          {{ CATEGORY_LABELS[subject.category] }}
        </v-btn>

        <v-alert
          v-if="errorMessage"
          class="mb-3"
          data-testid="cohort-subject-error"
          density="compact"
          type="error"
        >
          {{ errorMessage }}
        </v-alert>
        <v-alert
          v-if="successMessage"
          class="mb-3"
          data-testid="cohort-subject-success"
          density="compact"
          type="success"
        >
          {{ successMessage }}
        </v-alert>

        <!-- One card. This was four, each with its own heading and its own count line under
             it, which said the cohort's name three times before saying anything about it. -->
        <v-card
          v-if="subject"
          class="manager-card"
          data-testid="cohort-subject-identity"
          rounded="lg"
          variant="flat"
        >
          <v-card-text>
            <div
              class="d-flex align-start justify-space-between flex-wrap mb-1"
              style="gap: 12px"
            >
              <div class="manager-heading">
                <v-badge
                  color="primary"
                  :content="memberCount"
                  data-testid="cohort-subject-member-count"
                >
                  <h2 class="ma-0 subject-label">
                    {{ subject.label }}
                  </h2>
                </v-badge>
              </div>

              <v-btn
                data-testid="cohort-subject-add-target"
                prepend-icon="mdi-plus"
                size="small"
                variant="text"
                @click="openAddTarget"
              >
                Add target
              </v-btn>
            </div>

            <p class="text-body-2 text-medium-emphasis mb-0">
              {{ identity }}
            </p>
            <p
              v-if="subject.description"
              class="text-body-2 text-medium-emphasis mb-0"
            >
              {{ subject.description }}
            </p>

            <!-- Three boxes saying what they hold, opened when the reader wants the detail.
                 Members starts open: it is what the page is for. -->
            <div class="subject-boxes mt-4">
              <info-box
                v-if="subject.rules.length"
                expandable
                label="Rules"
                :summary="rulesSubtitle"
                testid="cohort-subject-rules"
              >
                <div
                  v-for="rule in subject.rules"
                  :key="rule.id"
                  class="d-flex align-center subject-rule"
                  :data-testid="`cohort-subject-rule-${rule.id}`"
                >
                  <v-icon
                    class="mr-2"
                    :color="rule.enabled ? undefined : 'medium-emphasis'"
                    :icon="rule.enabled ? 'mdi-check-circle-outline' : 'mdi-pause-circle-outline'"
                    size="18"
                    :title="rule.enabled ? 'Enabled' : 'Disabled'"
                  />
                  <!-- Reads as the rule it is: the fact, then the value it must equal. -->
                  <span>{{ factKindLabel(rule.factKind) }}</span>
                  <span class="text-medium-emphasis mx-1">=</span>
                  <span class="font-mono">{{ rule.factKey }}</span>
                  <span
                    v-if="!rule.enabled"
                    class="text-medium-emphasis ml-2"
                  >· disabled</span>
                </div>
              </info-box>

              <info-box
                expandable
                label="Sync targets"
                :summary="targetsSubtitle"
                testid="cohort-subject-targets"
              >
                <p
                  v-if="!subject.mappings.length"
                  class="text-body-2 text-medium-emphasis mb-0"
                >
                  None yet. Engine cohorts are created on first sync, or attach one with
                  Add target.
                </p>

                <!-- One block per target, its two actions behind a menu rather than as two
                     more buttons on a page that had eight. -->
                <div
                  v-for="mapping in subject.mappings"
                  :key="mapping.system"
                  class="subject-target"
                  :data-testid="`cohort-subject-target-${mapping.system.toLowerCase()}`"
                >
                  <div
                    class="d-flex align-center justify-space-between"
                    style="gap: 12px"
                  >
                    <div class="subject-target__identity">
                      <span class="font-weight-medium">{{ labelForSystem(mapping.system) }}</span>
                      <span class="text-medium-emphasis">
                        · {{ mapping.kind }} · {{ mapping.label }}
                        · id {{ mapping.externalId ?? "—" }}
                      </span>
                    </div>

                    <v-menu location="bottom end">
                      <template #activator="{props: menuProps}">
                        <v-btn
                          v-bind="menuProps"
                          :aria-label="`${labelForSystem(mapping.system)} target actions`"
                          :data-testid="`cohort-subject-target-menu-${mapping.system.toLowerCase()}`"
                          icon="mdi-dots-vertical"
                          size="small"
                          variant="text"
                        />
                      </template>
                      <v-list
                        density="compact"
                        min-width="220"
                      >
                        <v-list-item
                          :data-testid="`cohort-subject-switch-target-${mapping.system.toLowerCase()}`"
                          prepend-icon="mdi-swap-horizontal"
                          title="Switch target"
                          @click="openSwitchTarget(mapping.cohortId, mapping.system)"
                        />
                        <v-list-item
                          :data-testid="`cohort-subject-inbound-reconcile-${mapping.system.toLowerCase()}`"
                          :disabled="!mapping.externalId"
                          prepend-icon="mdi-import"
                          title="Inbound reconcile"
                          @click="openInboundReconcile(mapping.cohortId)"
                        />
                      </v-list>
                    </v-menu>
                  </div>

                  <!-- What the drift panel used to say about the target itself. The rows it
                       listed are in the members table now; this is what is left. -->
                  <div
                    class="d-flex align-center justify-space-between mt-1"
                    style="gap: 12px"
                  >
                    <span
                      class="text-body-2 text-medium-emphasis"
                      :data-testid="`cohort-subject-target-reconciled-${mapping.system.toLowerCase()}`"
                    >
                      {{ lastReconciledLabel(mapping) }}
                    </span>
                    <v-btn
                      :data-testid="`cohort-subject-reconcile-${mapping.system.toLowerCase()}`"
                      :disabled="!mapping.externalId || reconciling != null"
                      :loading="reconciling === mapping.cohortId"
                      size="small"
                      variant="text"
                      @click="reconcileTarget(mapping.cohortId)"
                    >
                      Reconcile now
                    </v-btn>
                  </div>
                </div>
              </info-box>

              <info-box
                default-open
                expandable
                flush
                label="Members"
                :summary="membersSummaryLine"
                testid="cohort-subject-members"
              >
                <p
                  v-if="allRows.length === 0"
                  class="text-body-2 text-medium-emphasis mb-0"
                >
                  None yet. Re-evaluating users will populate this list.
                </p>

                <template v-else>
                  <div class="members-toolbar mb-3">
                    <v-text-field
                      v-model="filterState.search"
                      clearable
                      data-testid="cohort-member-search"
                      density="compact"
                      hide-details
                      label="Search members"
                      prepend-inner-icon="mdi-magnify"
                    />
                    <v-select
                      v-model="filterState.sync"
                      data-testid="cohort-member-filter-sync"
                      density="compact"
                      hide-details
                      :items="SYNC_FILTER_ITEMS"
                      label="Sync"
                    />
                  </div>

                  <v-table
                    class="manager-table"
                    data-testid="cohort-subject-member-list"
                    density="compact"
                  >
                    <thead>
                      <tr>
                        <th
                          v-for="column in MEMBER_COLUMNS"
                          :key="column.sortKey"
                          :aria-sort="memberAriaSort(column.sortKey)"
                          class="sortable-header"
                          :data-testid="`cohort-member-header-${column.sortKey}`"
                          role="button"
                          :style="`width: ${column.width}`"
                          tabindex="0"
                          @click="toggleMemberSort(column.sortKey)"
                          @keydown.enter="toggleMemberSort(column.sortKey)"
                          @keydown.space.prevent="toggleMemberSort(column.sortKey)"
                        >
                          {{ column.label }}
                          <v-icon
                            :icon="memberSortIcon(column.sortKey)"
                            size="16"
                          />
                        </th>
                        <th style="width: 12%" />
                      </tr>
                    </thead>
                    <tbody>
                      <tr
                        v-for="member in sortedMembers"
                        :key="member.cohortMemberId"
                        :class="{'subject-member--deleted': member.isUserDeleted}"
                        :data-testid="`cohort-subject-member-${member.cohortMemberId}`"
                      >
                        <td>
                          {{ memberName(member) }}
                          <v-chip
                            v-if="member.isUserDeleted"
                            class="ml-2"
                            color="warning"
                            size="x-small"
                            title="Kept for historical stats"
                            variant="tonal"
                          >
                            Deleted
                          </v-chip>
                        </td>
                        <td class="text-medium-emphasis">
                          {{ member.userEmail ?? "—" }}
                        </td>
                        <td class="text-medium-emphasis">
                          {{ isMember(member) ? formatJoinedAt(member.joinedAt) : "—" }}
                        </td>
                        <td :data-testid="`cohort-subject-member-sync-${member.cohortMemberId}`">
                          <!-- Only the exceptions are chipped: a table of healthy rows spends
                             its colour on nothing, and the faults stop standing out. -->
                          <v-chip
                            v-if="syncStateOf(member) !== 'IN_SYNC'"
                            :color="syncChipColour(member)"
                            size="small"
                            variant="flat"
                          >
                            {{ syncLabel(member) }}
                          </v-chip>
                          <span
                            v-else
                            class="text-medium-emphasis"
                          >{{ syncLabel(member) }}</span>
                        </td>
                        <td class="text-right">
                          <!-- Always present, disabled when the row has nothing to offer, so
                             the column keeps one width whatever state its rows are in. -->
                          <v-menu location="bottom end">
                            <template #activator="{props: menuProps}">
                              <v-btn
                                v-bind="menuProps"
                                aria-label="Row actions"
                                :data-testid="`cohort-subject-member-menu-${member.cohortMemberId}`"
                                :disabled="!hasRowActions(member) || !!triggering"
                                icon="mdi-dots-vertical"
                                size="small"
                                variant="text"
                              />
                            </template>
                            <v-list
                              density="compact"
                              min-width="220"
                            >
                              <v-list-item
                                v-if="canReevaluate(member)"
                                :data-testid="`cohort-subject-member-reeval-${member.userId}`"
                                prepend-icon="mdi-refresh"
                                title="Re-evaluate"
                                @click="reevaluateMember(member.userId!)"
                              />
                              <v-list-item
                                v-if="canLinkUser(member)"
                                :data-testid="`cohort-subject-member-link-${member.cohortMemberId}`"
                                prepend-icon="mdi-account-arrow-left"
                                title="Link to a user"
                                @click="openLinkUser(member)"
                              />
                              <v-list-item
                                v-if="canRemoveExternal(member)"
                                base-color="error"
                                :data-testid="`cohort-subject-member-remove-${member.cohortMemberId}`"
                                prepend-icon="mdi-close-circle-outline"
                                :title="`Remove from ${systemLabel(member)}`"
                                @click="removeExternalRow(member)"
                              />
                            </v-list>
                          </v-menu>
                        </td>
                      </tr>
                    </tbody>
                  </v-table>
                </template>
              </info-box>
            </div>
          </v-card-text>
        </v-card>

        <v-progress-linear
          v-if="loading"
          indeterminate
        />

        <target-picker-modal
          v-if="subjectId != null"
          v-model="pickerOpen"
          :cohort-id="pickerCohortId"
          :mode="pickerMode"
          :subject-id="subjectId"
          :system="pickerSystem"
          @saved="onTargetSaved"
        />
        <v-dialog
          max-width="440"
          :model-value="linkingRow != null"
          @update:model-value="(value: boolean) => { if (!value) closeLinkUser() }"
        >
          <v-card title="Link this external id to a user">
            <v-card-text>
              <p class="text-body-2 text-medium-emphasis mb-3">
                External id: <code>{{ linkingRow?.externalUserId }}</code>
              </p>

              <user-picker
                v-model="linkUserId"
                label="Local user"
                required
              />

              <v-alert
                v-if="linkConflict"
                class="mt-2"
                density="compact"
                type="warning"
                variant="tonal"
              >
                That external id is already linked to
                <strong>{{ linkConflict.existingUserFullName ?? `User #${linkConflict.existingUserId}` }}</strong>.
                Resolve the existing mapping first or choose a different user.
              </v-alert>
            </v-card-text>

            <v-card-actions>
              <v-spacer />
              <v-btn
                variant="text"
                @click="closeLinkUser"
              >
                Cancel
              </v-btn>
              <v-btn
                color="primary"
                data-testid="cohort-subject-link-confirm"
                :disabled="linkUserId == null || linkSubmitting"
                :loading="linkSubmitting"
                variant="flat"
                @click="submitLinkUser"
              >
                Link
              </v-btn>
            </v-card-actions>
          </v-card>
        </v-dialog>

        <inbound-reconcile-modal
          v-if="subjectId != null && inboundCohortId != null"
          v-model="inboundOpen"
          :cohort-id="inboundCohortId"
          :subject-id="subjectId"
          @applied="onInboundApplied"
        />
      </div>
    </v-container>
  </v-main>
</template>

<style lang="scss" scoped>
.subject-page {
  max-width: 980px;
}

// The heading the badge hangs off, sized like the other managers' headings.
.subject-label {
  margin: 0;
  font-size: 22px;
  font-weight: 600;
  line-height: 1.15;
}

// The boxes stack with a gap rather than each carrying its own card and margin.
.subject-boxes {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.subject-rule + .subject-rule {
  margin-top: 6px;
}

.subject-target + .subject-target {
  margin-top: 12px;
  padding-top: 12px;
  border-top: thin solid rgba(var(--v-border-color), var(--v-border-opacity));
}

// The identity line truncates rather than pushing the target's menu off the row.
.subject-target__identity {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

// Search takes the room and the state select keeps its size, the way the member manager lays
// its toolbar out.
.members-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;

  .v-text-field {
    flex: 1 1 auto;
    min-width: 0;
  }

  .v-select {
    flex: 0 0 220px;
  }
}

@media (max-width: 700px) {
  .members-toolbar {
    flex-direction: column;
    align-items: stretch;

    .v-select {
      flex: 0 0 auto;
    }
  }
}

// The members box carries the page's own content, so it sits on the card rather than in a
// panel of its own; the two boxes above it are asides and keep their tint.
.subject-boxes :deep(.info-box--flush) {
  margin-top: 4px;
}

.subject-member--deleted {
  opacity: 0.6;
}
</style>
