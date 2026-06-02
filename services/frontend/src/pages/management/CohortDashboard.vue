<script lang="ts" setup>
import {computed, onMounted, ref} from "vue"
import {useRouter} from "vue-router"
import TopBanner from "@/components/common/banners/TopBanner.vue"
import {$handleNetworkError} from "@/plugins/handleNetworkError"
import {
  type CohortSummary,
  CohortKind,
  enqueue,
  findCohorts,
} from "@/services/api"
import store from "@/plugins/store"
import {jobCatalogEntry} from "@/utils/jobCatalog"

defineOptions({name: "CohortDashboardPage"})

const router = useRouter()

const cohorts = ref<CohortSummary[]>([])
const loading = ref<boolean>(false)
const triggering = ref<string | null>(null)
const errorMessage = ref<string | null>(null)
const successMessage = ref<string | null>(null)

const groupedCohorts = computed<{system: string; rows: CohortSummary[]}[]>(() => {
  const bySystem = new Map<string, CohortSummary[]>()
  for (const cohort of cohorts.value) {
    const list = bySystem.get(cohort.system) ?? []
    list.push(cohort)
    bySystem.set(cohort.system, list)
  }
  return Array.from(bySystem.entries())
    .sort(([leftSystem], [rightSystem]) => leftSystem.localeCompare(rightSystem))
    .map(([system, rows]) => ({
      system,
      rows: rows.slice().sort((leftCohort, rightCohort) => {
        const byKind = leftCohort.kind.localeCompare(rightCohort.kind)
        return byKind !== 0 ? byKind : leftCohort.label.localeCompare(rightCohort.label)
      }),
    }))
})

const refresh = async () => {
  loading.value = true
  try {
    const response = await findCohorts()
    cohorts.value = response.data ?? []
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

const resyncCohort = (cohort: CohortSummary) => triggerJob("cohort.resync", {cohortId: cohort.id})

const reconcileContributionPeriods = () =>
  triggerJob("cohort.reconcile-contribution-periods")

const reconcileAllUsers = () => triggerJob("cohort.reconcile-all-users")

const kindIcon: Record<CohortKind, string> = {
  [CohortKind.LIST]: "mdi-format-list-bulleted",
  [CohortKind.ROLE]: "mdi-shield-account",
  [CohortKind.GROUP]: "mdi-account-group",
}

const openDetail = (cohort: CohortSummary) => {
  void router.push({name: "cohortDetail", params: {id: cohort.id}})
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

    <div class="mx-3">
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

        <v-card
          class="manager-card mb-4"
          rounded="lg"
          variant="flat"
        >
          <div class="manager-card__header">
            <div>
              <p class="text-overline mb-1">
                Cohort Engine
              </p>
              <h2 class="text-h6 mb-1">
                Global actions
              </h2>
              <p class="text-caption text-medium-emphasis mb-0">
                Each button enqueues a job. Watch progress in Manage jobs.
              </p>
            </div>
          </div>
          <div class="manager-card__body global-actions">
            <v-btn
              :disabled="!!triggering"
              :loading="triggering === 'cohort.reconcile-contribution-periods'"
              color="primary"
              data-testid="cohort-action-reconcile-periods"
              variant="flat"
              @click="reconcileContributionPeriods"
            >
              Reconcile contribution-period cohorts
            </v-btn>
            <v-btn
              :disabled="!!triggering"
              :loading="triggering === 'cohort.reconcile-all-users'"
              color="primary"
              data-testid="cohort-action-reconcile-users"
              variant="flat"
              @click="reconcileAllUsers"
            >
              Re-evaluate every user's cohorts
            </v-btn>
            <v-btn
              :disabled="loading"
              data-testid="cohort-refresh-btn"
              variant="outlined"
              @click="refresh"
            >
              Refresh
            </v-btn>
          </div>
        </v-card>

        <v-card
          class="manager-card"
          rounded="lg"
          variant="flat"
        >
          <div class="manager-card__header">
            <div>
              <p class="text-overline mb-1">
                Membership
              </p>
              <h2 class="text-h6 mb-1">
                Cohorts ({{ cohorts.length }})
              </h2>
            </div>
          </div>

          <v-list
            data-testid="cohort-list"
            density="comfortable"
          >
            <v-list-item
              v-if="!loading && cohorts.length === 0"
              subtitle="They appear automatically when the engine first encounters them."
              title="No cohorts yet."
            />
            <template
              v-for="group in groupedCohorts"
              :key="group.system"
            >
              <v-list-subheader>{{ group.system }}</v-list-subheader>
              <v-list-item
                v-for="cohort in group.rows"
                :key="cohort.id"
                :data-testid="`cohort-row-${cohort.id}`"
                role="button"
                tabindex="0"
                @click="openDetail(cohort)"
                @keydown.enter.prevent="openDetail(cohort)"
                @keydown.space.prevent="openDetail(cohort)"
              >
                <template #prepend>
                  <v-icon :icon="kindIcon[cohort.kind]" />
                </template>

                <v-list-item-title>{{ cohort.label }}</v-list-item-title>
                <v-list-item-subtitle>
                  {{ cohort.kind }} · {{ cohort.memberCount }} members<span
                    v-if="cohort.externalId"
                  > · external id {{ cohort.externalId }}</span>
                </v-list-item-subtitle>

                <template #append>
                  <v-btn
                    :data-testid="`cohort-resync-btn-${cohort.id}`"
                    :disabled="!!triggering"
                    :loading="triggering === 'cohort.resync' && false"
                    size="small"
                    variant="outlined"
                    @click.stop="resyncCohort(cohort)"
                  >
                    Re-push
                  </v-btn>
                </template>
              </v-list-item>
            </template>
          </v-list>
        </v-card>
      </div>
    </div>
  </v-main>
</template>

<style lang="scss" scoped>
.cohorts-page {
  max-width: 980px;
}

.manager-card {
  background: rgba(var(--v-theme-surface), 0.92);
  box-shadow: none;
}

.manager-card__header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  padding: 18px 18px 14px;
}

.manager-card__body {
  padding: 14px 18px 18px;
}

.global-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
</style>
