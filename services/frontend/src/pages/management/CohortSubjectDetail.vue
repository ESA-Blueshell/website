<script lang="ts" setup>
import {computed, onMounted, ref, watch} from "vue"
import {useRoute, useRouter} from "vue-router"
import TopBanner from "@/components/common/banners/TopBanner.vue"
import {$handleNetworkError} from "@/plugins/handleNetworkError"
import {
  type CohortSubjectDetail,
  CohortSubjectCategory,
  TargetSystem,
  enqueue,
  findCohortSubjectById,
} from "@/services/api"
import InfoBox from "@/components/common/panels/InfoBox.vue"
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

/** The badge beside the cohort's name: people, not rows. */
const memberCount = computed(() => allRows.value.filter(isMember).length)

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
/** Reads under a "Last reconciled" column, so it carries the value and not the label. */
const lastReconciledLabel = (mapping: {externalId?: string | null; lastReconciledAt?: string | null}): string => {
  if (!mapping.externalId) return "Not created yet"
  if (!mapping.lastReconciledAt) return "Never"
  return new Date(mapping.lastReconciledAt).toLocaleDateString(undefined, {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  })
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
            <div class="manager-heading mb-1">
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
                :count="subject.rules.length"
                label="Rules"
                testid="cohort-subject-rules"
              >
                <v-table
                  class="manager-table"
                  data-testid="cohort-subject-rule-list"
                  density="compact"
                >
                  <thead>
                    <tr>
                      <th style="width: 46%">
                        Fact
                      </th>
                      <th style="width: 34%">
                        Must equal
                      </th>
                      <th style="width: 20%">
                        State
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr
                      v-for="rule in subject.rules"
                      :key="rule.id"
                      :data-testid="`cohort-subject-rule-${rule.id}`"
                    >
                      <!-- The fact reads as a sentence; the key stays verbatim because it is
                           an id somebody matches against the database. -->
                      <td>{{ factKindLabel(rule.factKind) }}</td>
                      <td class="text-monospace text-medium-emphasis">
                        {{ rule.factKey }}
                      </td>
                      <td>
                        <v-chip
                          :color="rule.enabled ? undefined : 'warning'"
                          size="small"
                          :variant="rule.enabled ? 'tonal' : 'flat'"
                        >
                          {{ rule.enabled ? "Enabled" : "Disabled" }}
                        </v-chip>
                      </td>
                    </tr>
                  </tbody>
                </v-table>
              </info-box>

              <info-box
                expandable
                :count="subject.mappings.length"
                label="Sync targets"
                testid="cohort-subject-targets"
              >
                <!-- A row per target, in the table idiom the rest of these pages use: what
                     it is, where it points, when it last agreed, and one menu of actions. -->
                <v-table
                  class="manager-table"
                  data-testid="cohort-subject-target-list"
                  density="compact"
                >
                  <thead>
                    <tr>
                      <th style="width: 16%">
                        System
                      </th>
                      <th
                        class="targets-col-detail"
                        style="width: 14%"
                      >
                        Kind
                      </th>
                      <th style="width: 30%">
                        Target
                      </th>
                      <th
                        class="targets-col-detail"
                        style="width: 12%"
                      >
                        External id
                      </th>
                      <th style="width: 22%">
                        Last reconciled
                      </th>
                      <!-- What the table as a whole can do, where the member table keeps the
                           same menu: on the header row, not on the box around it. -->
                      <th style="width: 6%">
                        <div class="targets-th-actions">
                          <v-menu location="bottom end">
                            <template #activator="{props: menuProps}">
                              <v-btn
                                v-bind="menuProps"
                                aria-label="Sync target actions"
                                data-testid="cohort-subject-targets-menu"
                                icon="mdi-dots-vertical"
                                size="small"
                                variant="text"
                              />
                            </template>
                            <v-list
                              density="compact"
                              min-width="200"
                            >
                              <v-list-item
                                data-testid="cohort-subject-add-target"
                                prepend-icon="mdi-plus"
                                title="Add target"
                                @click="openAddTarget"
                              />
                            </v-list>
                          </v-menu>
                        </div>
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-if="!subject.mappings.length">
                      <td
                        class="text-medium-emphasis"
                        colspan="6"
                      >
                        None yet. Engine cohorts are created on first sync, or attach one with
                        Add target.
                      </td>
                    </tr>
                    <tr
                      v-for="mapping in subject.mappings"
                      :key="mapping.system"
                      :data-testid="`cohort-subject-target-${mapping.system.toLowerCase()}`"
                    >
                      <td class="font-weight-medium">
                        {{ labelForSystem(mapping.system) }}
                      </td>
                      <td class="text-medium-emphasis targets-col-detail">
                        {{ mapping.kind }}
                      </td>
                      <td class="text-medium-emphasis">
                        {{ mapping.label }}
                      </td>
                      <td class="text-monospace text-medium-emphasis targets-col-detail">
                        {{ mapping.externalId ?? "—" }}
                      </td>
                      <td
                        class="text-medium-emphasis"
                        :data-testid="`cohort-subject-target-reconciled-${mapping.system.toLowerCase()}`"
                      >
                        {{ lastReconciledLabel(mapping) }}
                      </td>
                      <td class="text-right">
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
                              :data-testid="`cohort-subject-reconcile-${mapping.system.toLowerCase()}`"
                              :disabled="!mapping.externalId || reconciling != null"
                              prepend-icon="mdi-sync"
                              title="Reconcile now"
                              @click="reconcileTarget(mapping.cohortId)"
                            />
                            <v-list-item
                              :data-testid="`cohort-subject-inbound-reconcile-${mapping.system.toLowerCase()}`"
                              :disabled="!mapping.externalId"
                              prepend-icon="mdi-import"
                              title="Inbound reconcile"
                              @click="openInboundReconcile(mapping.cohortId)"
                            />
                            <v-list-item
                              :data-testid="`cohort-subject-switch-target-${mapping.system.toLowerCase()}`"
                              prepend-icon="mdi-swap-horizontal"
                              title="Switch target"
                              @click="openSwitchTarget(mapping.cohortId, mapping.system)"
                            />
                          </v-list>
                        </v-menu>
                      </td>
                    </tr>
                  </tbody>
                </v-table>
              </info-box>

              <div data-testid="cohort-subject-members">
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
              </div>
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

// Inside a box, a table paints no fill: the box is already a lifted surface, so a table that
// carried its own read as a second panel dropped onto the first. Its structure comes from the
// separators instead, one step stronger than the hairline a card uses so they still read
// against the tint.
.info-box .manager-table {
  --v-border-opacity: 0.2;

  background: transparent;

  thead th {
    background: transparent;
  }
}

// The header's menu sits against the trailing edge, over the row of dots below it.
.targets-th-actions {
  display: flex;
  justify-content: flex-end;
}

// Six columns do not fit a phone: the table scrolled sideways and took its own action menus
// off-screen with it, so the only way to reach a target's actions was to scroll a table the
// page gives no hint scrolls. Kind and external id are the two a reader can do without — the
// system and the target still name the row — and dropping them lets the rest fit.
@media (max-width: 700px) {
  .targets-col-detail {
    display: none;
  }

  // What is left may wrap, and sits in tighter gutters. Vuetify keeps a header on one line and
  // pads for a desktop, either of which is enough on its own to push the four remaining
  // columns past the width of a phone.
  [data-testid="cohort-subject-target-list"] th,
  [data-testid="cohort-subject-target-list"] td {
    padding-inline: 8px;
    white-space: normal;
  }
}

// The boxes stack with a gap rather than each carrying its own card and margin.
.subject-boxes {
  display: flex;
  flex-direction: column;
  gap: 10px;
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

// The members table is the page's own content, so it sits on the card; the two boxes above it
// are asides and keep their tint.
.subject-boxes > [data-testid="cohort-subject-members"] {
  margin-top: 4px;
}

.subject-member--deleted {
  opacity: 0.6;
}
</style>
