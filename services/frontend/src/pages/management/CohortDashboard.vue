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
  {
    category: CohortSubjectCategory.OTHER,
    title: "Other",
    blurb: "Custom cohorts created by an operator.",
    icon: "mdi-dots-horizontal-circle",
  },
]

const countsByCategory = computed<Record<CohortSubjectCategory, {subjects: number; members: number}>>(() => {
  const counts: Record<CohortSubjectCategory, {subjects: number; members: number}> = {
    [CohortSubjectCategory.COMMITTEES]: {subjects: 0, members: 0},
    [CohortSubjectCategory.PERIODS]: {subjects: 0, members: 0},
    [CohortSubjectCategory.MEMBERS]: {subjects: 0, members: 0},
    [CohortSubjectCategory.OTHER]: {subjects: 0, members: 0},
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

        <div class="dashboard-actions mb-3">
          <v-btn
            :disabled="!!triggering"
            :loading="triggering === 'cohort.reconcile-contribution-periods'"
            color="primary"
            data-testid="cohort-action-reconcile-periods"
            size="small"
            variant="flat"
            @click="reconcilePeriods"
          >
            Reconcile periods
          </v-btn>
          <v-btn
            :disabled="!!triggering"
            :loading="triggering === 'cohort.reconcile-all-users'"
            color="primary"
            data-testid="cohort-action-reconcile-users"
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
            :disabled="loading"
            data-testid="cohort-refresh-btn"
            size="small"
            variant="outlined"
            @click="refresh"
          >
            Refresh
          </v-btn>
        </div>

        <div class="category-grid">
          <v-card
            v-for="card in CARDS"
            :key="card.category"
            :data-testid="`cohort-category-card-${card.category.toLowerCase()}`"
            class="category-card"
            role="button"
            tabindex="0"
            variant="flat"
            @click="openCategory(card.category)"
            @keydown.enter.prevent="openCategory(card.category)"
            @keydown.space.prevent="openCategory(card.category)"
          >
            <div class="category-card__header">
              <v-icon
                :icon="card.icon"
                class="category-card__icon"
                size="32"
              />
              <h2 class="category-card__title">
                {{ card.title }}
              </h2>
            </div>
            <p class="category-card__blurb">
              {{ card.blurb }}
            </p>
            <div class="category-card__stats">
              <div class="stat">
                <div class="stat-value">
                  {{ countsByCategory[card.category].subjects }}
                </div>
                <div class="stat-label">
                  Cohorts
                </div>
              </div>
              <v-divider vertical />
              <div class="stat">
                <div class="stat-value">
                  {{ countsByCategory[card.category].members }}
                </div>
                <div class="stat-label">
                  Members
                </div>
              </div>
            </div>
          </v-card>
        </div>
      </div>
    </v-container>
  </v-main>
</template>

<style lang="scss" scoped>
.cohorts-page {
  max-width: 980px;
}

.dashboard-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}

.category-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 14px;
}

.category-card {
  background: rgba(var(--v-theme-surface), 0.92);
  padding: 16px 18px;
  cursor: pointer;
  transition: background-color 0.15s ease, outline-color 0.15s ease;
  outline: 1px solid transparent;
}

.category-card:hover,
.category-card:focus-visible {
  background: rgba(var(--v-theme-on-surface), 0.05);
  outline-color: rgba(var(--v-theme-primary), 0.4);
}

.category-card__header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}

.category-card__icon {
  color: rgba(var(--v-theme-primary), 0.85);
}

.category-card__title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  line-height: 1.1;
}

.category-card__blurb {
  margin: 0 0 12px;
  font-size: 12.5px;
  line-height: 1.35;
  color: rgba(var(--v-theme-on-surface), 0.7);
  min-height: 34px;
}

.category-card__stats {
  display: flex;
  align-items: stretch;
  gap: 14px;
}

.stat {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.stat-value {
  font-size: 22px;
  font-weight: 700;
  line-height: 1.1;
}

.stat-label {
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: rgba(var(--v-theme-on-surface), 0.6);
}
</style>
