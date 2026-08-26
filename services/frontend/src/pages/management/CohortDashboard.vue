<script lang="ts" setup>
import {computed, onMounted, ref} from "vue"
import {useRouter} from "vue-router"
import TopBanner from "@/components/common/banners/TopBanner.vue"
import {$handleNetworkError} from "@/plugins/handleNetworkError"
import {
  type CohortSubjectSummary,
  CohortSubjectCategory,
  enqueue,
  findCohortSubjects,
} from "@/services/api"
import store from "@/plugins/store"
import {jobCatalogEntry} from "@/utils/jobCatalog"
import {useTableSort} from "@/composables/useTableSort"

defineOptions({name: "CohortDashboardPage"})

const router = useRouter()

const subjects = ref<CohortSubjectSummary[]>([])
const loading = ref<boolean>(false)
const triggering = ref<string | null>(null)
const errorMessage = ref<string | null>(null)
const successMessage = ref<string | null>(null)

/**
 * Top-level taxonomy shown as the landing page. Each card links to a
 * per-category browse page. Order is fixed (Committees, Periods,
 * Members, Other) rather than enum-iteration order so the most-touched
 * categories sit at the top regardless of the backend enum's
 * declaration order.
 */
type Card = {
  category: CohortSubjectCategory
  title: string
  blurb: string
  icon: string
}

const CARDS: Card[] = [
  {
    category: CohortSubjectCategory.COMMITTEES,
    title: "Committees",
    blurb: "One cohort per committee. Driven by committee_members.",
    icon: "mdi-account-group",
  },
  {
    category: CohortSubjectCategory.PERIODS,
    title: "Periods",
    blurb: "Contribution-period scoped cohorts: Members, Active Members, Contribution Paid.",
    icon: "mdi-calendar-range",
  },
  {
    category: CohortSubjectCategory.MEMBERS,
    title: "Members",
    blurb: "Member-status cohorts that aren't tied to a single period (today: Newsletter).",
    icon: "mdi-shield-account",
  },
]

/** What the engine holds in total, which is what the heading claims. */
const totalSubjects = computed(() => subjects.value.length)

const countsByCategory = computed<Record<CohortSubjectCategory, {subjects: number; members: number}>>(() => {
  const counts: Record<CohortSubjectCategory, {subjects: number; members: number}> = {
    [CohortSubjectCategory.COMMITTEES]: {subjects: 0, members: 0},
    [CohortSubjectCategory.PERIODS]: {subjects: 0, members: 0},
    [CohortSubjectCategory.MEMBERS]: {subjects: 0, members: 0},
  }
  for (const subject of subjects.value) {
    counts[subject.category].subjects += 1
    counts[subject.category].members += subject.memberCount
  }
  return counts
})

const refresh = async () => {
  loading.value = true
  try {
    const response = await findCohortSubjects()
    subjects.value = response.data ?? []
  } catch (error) {
    $handleNetworkError(error)
  } finally {
    loading.value = false
  }
}

const triggerJob = async (jobType: string, payload?: Record<string, unknown>) => {
  triggering.value = jobType
  errorMessage.value = null
  successMessage.value = null
  try {
    const response = await enqueue({body: {jobType, payload: payload ?? {}}})
    if (response.status === 200 && response.data) {
      const entry = jobCatalogEntry(jobType)
      successMessage.value = `${entry.title} enqueued (job #${response.data.id ?? "?"}).`
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

const reconcilePeriods = () => triggerJob("cohort.reconcile-contribution-periods")
const reconcileAllUsers = () => triggerJob("cohort.reconcile-all-users")

/**
 * A row per category, with its counts alongside rather than appended to its name. Declared
 * order is the unsorted order — the most-touched categories first — and a column sort replaces
 * it only once one is asked for.
 */
type CategoryRow = Card & {cohorts: number; members: number}

const rows = computed<CategoryRow[]>(() =>
  CARDS.map((card) => ({
    ...card,
    cohorts: countsByCategory.value[card.category].subjects,
    members: countsByCategory.value[card.category].members,
  })),
)

type SortKey = "title" | "cohorts" | "members"

const {sortedItems, toggleSort, sortIcon, ariaSort} = useTableSort<CategoryRow, SortKey>(rows, {
  title: (a, b) => a.title.localeCompare(b.title),
  cohorts: (a, b) => a.cohorts - b.cohorts,
  members: (a, b) => a.members - b.members,
})

const COLUMNS: ReadonlyArray<{label: string; sortKey: SortKey; align?: string; width: string}> = [
  {label: "Category", sortKey: "title", width: "18%"},
  // Wide enough for the label and its sort arrow on one line: any narrower and the arrow
  // wraps under the word.
  {label: "Cohorts", sortKey: "cohorts", align: "text-right", width: "12%"},
  {label: "Members", sortKey: "members", align: "text-right", width: "12%"},
]

const openCategory = (category: CohortSubjectCategory) => {
  void router.push({name: "cohortCategory", params: {category: category.toLowerCase()}})
}

onMounted(async () => {
  if (!store.getters.isAdmin) {
    await router.replace("/")
    return
  }
  await refresh()
})
</script>

<template>
  <v-main>
    <top-banner title="Cohorts" />

    <v-container>
      <div class="mx-auto my-3 cohorts-page">
        <v-alert
          v-if="errorMessage"
          class="mb-3"
          data-testid="cohort-error"
          density="compact"
          type="error"
        >
          {{ errorMessage }}
        </v-alert>
        <v-alert
          v-if="successMessage"
          class="mb-3"
          data-testid="cohort-success"
          density="compact"
          type="success"
        >
          {{ successMessage }}
        </v-alert>

        <!-- One card, one heading: the banner already names the page, and the engine's
             actions belong beside the thing they act on rather than in a card of their own. -->
        <v-card
          class="manager-card"
          data-testid="cohort-dashboard-summary"
          rounded="lg"
          variant="flat"
        >
          <v-card-text>
            <div
              class="d-flex align-start justify-space-between flex-wrap mb-4"
              style="gap: 12px"
            >
              <div class="manager-heading">
                <v-badge
                  color="primary"
                  :content="totalSubjects"
                  data-testid="cohort-total-count"
                >
                  <h2 class="ma-0">
                    Cohorts
                  </h2>
                </v-badge>
              </div>

              <div
                class="d-flex flex-wrap align-center"
                style="gap: 8px"
              >
                <v-btn
                  color="primary"
                  data-testid="cohort-action-reconcile-periods"
                  :disabled="!!triggering"
                  :loading="triggering === 'cohort.reconcile-contribution-periods'"
                  size="small"
                  variant="flat"
                  @click="reconcilePeriods"
                >
                  Reconcile periods
                </v-btn>
                <v-btn
                  color="primary"
                  data-testid="cohort-action-reconcile-users"
                  :disabled="!!triggering"
                  :loading="triggering === 'cohort.reconcile-all-users'"
                  size="small"
                  variant="flat"
                  @click="reconcileAllUsers"
                >
                  Re-evaluate all users
                </v-btn>
                <v-btn
                  data-testid="cohort-targets-link"
                  size="small"
                  :to="{name: 'cohortTargets'}"
                  variant="outlined"
                >
                  Brevo targets
                </v-btn>
                <v-btn
                  data-testid="cohort-refresh-btn"
                  :disabled="loading"
                  size="small"
                  variant="outlined"
                  @click="refresh"
                >
                  Refresh
                </v-btn>
              </div>
            </div>

            <div
              data-testid="cohort-category-list"
              style="overflow-x: auto"
            >
              <v-table class="manager-table">
                <thead>
                  <tr>
                    <th style="width: 4%" />
                    <th
                      v-for="column in COLUMNS"
                      :key="column.sortKey"
                      :aria-sort="ariaSort(column.sortKey)"
                      :class="['sortable-header', column.align]"
                      :data-testid="`cohort-header-${column.sortKey}`"
                      role="button"
                      :style="`width: ${column.width}`"
                      tabindex="0"
                      @click="toggleSort(column.sortKey)"
                      @keydown.enter="toggleSort(column.sortKey)"
                      @keydown.space.prevent="toggleSort(column.sortKey)"
                    >
                      {{ column.label }}
                      <v-icon
                        :icon="sortIcon(column.sortKey)"
                        size="16"
                      />
                    </th>
                    <!-- Not sortable: prose, and sorting a description alphabetically tells
                         nobody anything. -->
                    <th>What it holds</th>
                    <th style="width: 6%" />
                  </tr>
                </thead>

                <tbody>
                  <tr
                    v-for="card in sortedItems"
                    :key="card.category"
                    class="manager-table__row"
                    :data-testid="`cohort-category-card-${card.category.toLowerCase()}`"
                    role="button"
                    tabindex="0"
                    @click="openCategory(card.category)"
                    @keydown.enter.prevent="openCategory(card.category)"
                    @keydown.space.prevent="openCategory(card.category)"
                  >
                    <td>
                      <v-icon
                        :icon="card.icon"
                        size="20"
                      />
                    </td>
                    <td class="font-weight-medium">
                      {{ card.title }}
                    </td>
                    <td class="text-right">
                      {{ card.cohorts }}
                    </td>
                    <td class="text-right">
                      {{ card.members }}
                    </td>
                    <td class="text-medium-emphasis">
                      {{ card.blurb }}
                    </td>
                    <td class="text-right">
                      <v-icon
                        icon="mdi-chevron-right"
                        size="20"
                      />
                    </td>
                  </tr>
                </tbody>
              </v-table>
            </div>
          </v-card-text>
        </v-card>
      </div>
    </v-container>
  </v-main>
</template>

<style lang="scss" scoped>
.cohorts-page {
  max-width: 980px;
}
</style>
