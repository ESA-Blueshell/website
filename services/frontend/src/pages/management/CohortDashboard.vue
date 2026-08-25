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

/** What the engine holds in total, which is what the header claims. */
const totalSubjects = computed(() => subjects.value.length)
const totalMembers = computed(() => subjects.value.reduce((sum, s) => sum + s.memberCount, 0))

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

        <!-- The engine's own state and the operations on it, where every other management
             page puts them: in the header of the card that describes the thing. -->
        <manager-card
          eyebrow="Cohort engine"
          spaced
          :subtitle="`${totalSubjects} cohorts across ${CARDS.length} categories · ${totalMembers} memberships`"
          testid="cohort-dashboard-summary"
          title="Reconciliation"
        >
          <template #actions>
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
          </template>

          <div class="d-flex flex-wrap gap-2">
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
          </div>
        </manager-card>

        <!-- The categories are navigation, so they read as a list of places to go rather
             than as tiles that happen to be clickable. -->
        <manager-card
          eyebrow="Categories"
          flush
          testid="cohort-category-list"
        >
          <v-list density="comfortable">
            <v-list-item
              v-for="card in CARDS"
              :key="card.category"
              :data-testid="`cohort-category-card-${card.category.toLowerCase()}`"
              :prepend-icon="card.icon"
              :subtitle="card.blurb"
              :title="card.title"
              @click="openCategory(card.category)"
            >
              <template #append>
                <div class="d-flex align-center category-counts">
                  <span class="text-body-2">
                    <strong>{{ countsByCategory[card.category].subjects }}</strong> cohorts
                  </span>
                  <span class="text-body-2 text-medium-emphasis">
                    <strong>{{ countsByCategory[card.category].members }}</strong> members
                  </span>
                  <v-icon
                    icon="mdi-chevron-right"
                    size="20"
                  />
                </div>
              </template>
            </v-list-item>
          </v-list>
        </manager-card>
      </div>
    </v-container>
  </v-main>
</template>

<style lang="scss" scoped>
.cohorts-page {
  max-width: 980px;
}

.category-counts {
  gap: 16px;
}
</style>
