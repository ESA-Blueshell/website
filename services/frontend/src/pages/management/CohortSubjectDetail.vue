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
import CohortDriftPanel from "@/domains/cohorts/components/CohortDriftPanel.vue"
import InfoBox from "@/components/common/panels/InfoBox.vue"
import {cohortTypeLabel} from "@/domains/cohorts/cohortTypeLabels"
import {
  membersSummary,
  rulesSummary,
  targetsSummary,
} from "@/domains/cohorts/cohortSubjectSummaries"
import InboundReconcileModal from "@/domains/cohorts/components/InboundReconcileModal.vue"
import TargetPickerModal from "@/domains/cohorts/components/TargetPickerModal.vue"
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

const membersSubtitle = computed<string>(() => membersSummary(subject.value?.members ?? []))

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

/** Sorting members by name, address or join date, as the member table sorts its own rows. */
type MemberSortKey = "name" | "email" | "joinedAt"

const memberRows = computed(() => subject.value?.members ?? [])

const memberName = (member: {userFullName?: string | null; userId: number; isUserDeleted: boolean}) =>
  member.isUserDeleted ? `Deleted user #${member.userId}` : member.userFullName ?? `User #${member.userId}`

const {
  sortedItems: sortedMembers,
  toggleSort: toggleMemberSort,
  sortIcon: memberSortIcon,
  ariaSort: memberAriaSort,
} = useTableSort<CohortSubjectDetail["members"][number], MemberSortKey>(memberRows, {
  name: (a, b) => memberName(a).localeCompare(memberName(b)),
  email: (a, b) => (a.userEmail ?? "").localeCompare(b.userEmail ?? ""),
  joinedAt: (a, b) => a.joinedAt.localeCompare(b.joinedAt),
})

const MEMBER_COLUMNS: ReadonlyArray<{label: string; sortKey: MemberSortKey; width: string}> = [
  {label: "Member", sortKey: "name", width: "34%"},
  {label: "Email", sortKey: "email", width: "36%"},
  {label: "Joined", sortKey: "joinedAt", width: "18%"},
]

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
                  :content="subject.members.length"
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

                  <cohort-drift-panel
                    v-if="subjectId != null"
                    class="mt-2"
                    :cohort-id="mapping.cohortId"
                    :subject-id="subjectId"
                    :subject-type="(subject.type as CohortSubjectType)"
                    :system="(mapping.system as TargetSystem)"
                  />
                </div>
              </info-box>

              <info-box
                default-open
                expandable
                label="Members"
                :summary="membersSubtitle"
                testid="cohort-subject-members"
              >
                <p
                  v-if="subject.members.length === 0"
                  class="text-body-2 text-medium-emphasis mb-0"
                >
                  None yet. Re-evaluating users will populate this list.
                </p>

                <v-table
                  v-else
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
                      :data-testid="`cohort-subject-member-${member.userId}`"
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
                        {{ formatJoinedAt(member.joinedAt) }}
                      </td>
                      <td class="text-right">
                        <v-btn
                          v-if="!member.isUserDeleted"
                          aria-label="Re-evaluate this member"
                          :data-testid="`cohort-subject-member-reeval-${member.userId}`"
                          :disabled="!!triggering"
                          icon="mdi-refresh"
                          size="small"
                          title="Re-evaluate this member"
                          variant="text"
                          @click="reevaluateMember(member.userId)"
                        />
                      </td>
                    </tr>
                  </tbody>
                </v-table>
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

.subject-member--deleted {
  opacity: 0.6;
}
</style>
