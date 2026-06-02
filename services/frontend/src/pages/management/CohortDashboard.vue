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

const UNGROUPED = "Other"

/**
 * Group cohorts by folder first (the operator-facing organisation —
 * Committees / Periods / …), then by external system inside each
 * folder. Cohorts without a folder fall into the "Other" group so
 * nothing is hidden from the dashboard.
 */
const groupedCohorts = computed<{folder: string; systems: {system: string; rows: CohortSummary[]}[]}[]>(() => {
  const byFolder = new Map<string, Map<string, CohortSummary[]>>()
  for (const cohort of cohorts.value) {
    const folder = cohort.folder ?? UNGROUPED
    const systems = byFolder.get(folder) ?? new Map<string, CohortSummary[]>()
    const list = systems.get(cohort.system) ?? []
    list.push(cohort)
    systems.set(cohort.system, list)
    byFolder.set(folder, systems)
  }

  const folderRank = (name: string): number => {
    if (name === UNGROUPED) return Number.MAX_SAFE_INTEGER
    return 0
  }

  return Array.from(byFolder.entries())
    .sort(([leftFolder], [rightFolder]) => {
      const rankDelta = folderRank(leftFolder) - folderRank(rightFolder)
      return rankDelta !== 0 ? rankDelta : leftFolder.localeCompare(rightFolder)
    })
    .map(([folder, systems]) => ({
      folder,
      systems: Array.from(systems.entries())
        .sort(([leftSystem], [rightSystem]) => leftSystem.localeCompare(rightSystem))
        .map(([system, rows]) => ({
          system,
          rows: rows.slice().sort((leftCohort, rightCohort) => {
            const byKind = leftCohort.kind.localeCompare(rightCohort.kind)
            return byKind !== 0 ? byKind : leftCohort.label.localeCompare(rightCohort.label)
          }),
        })),
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
      <div class="mx-auto my-2 cohorts-page">
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
          class="manager-card mb-2"
          rounded="lg"
          variant="flat"
        >
          <div class="manager-card__header">
            <div>
              <p class="text-overline mb-0">
                Cohort Engine
              </p>
              <p class="text-caption text-medium-emphasis mb-0">
                Each button enqueues a job. Watch progress in Manage jobs.
              </p>
            </div>
            <div class="d-flex flex-wrap ga-2">
              <v-btn
                :disabled="!!triggering"
                :loading="triggering === 'cohort.reconcile-contribution-periods'"
                color="primary"
                data-testid="cohort-action-reconcile-periods"
                size="small"
                variant="flat"
                @click="reconcileContributionPeriods"
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
                :disabled="loading"
                data-testid="cohort-refresh-btn"
                size="small"
                variant="outlined"
                @click="refresh"
              >
                Refresh
              </v-btn>
            </div>
          </div>
        </v-card>

        <v-card
          class="manager-card"
          rounded="lg"
          variant="flat"
        >
          <div class="manager-card__header">
            <div>
              <p class="text-overline mb-0">
                Cohorts ({{ cohorts.length }})
              </p>
            </div>
          </div>

          <v-list
            data-testid="cohort-list"
            density="compact"
          >
            <v-list-item
              v-if="!loading && cohorts.length === 0"
              subtitle="They appear automatically when the engine first encounters them."
              title="No cohorts yet."
            />
            <template
              v-for="folderGroup in groupedCohorts"
              :key="folderGroup.folder"
            >
              <v-list-subheader class="cohort-folder-header">
                {{ folderGroup.folder }}
              </v-list-subheader>
              <template
                v-for="group in folderGroup.systems"
                :key="`${folderGroup.folder}-${group.system}`"
              >
                <v-list-subheader class="cohort-system-header">
                  {{ group.system }}
                </v-list-subheader>
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
                      size="x-small"
                      variant="outlined"
                      @click.stop="resyncCohort(cohort)"
                    >
                      Re-push
                    </v-btn>
                  </template>
                </v-list-item>
              </template>
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
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
}

.manager-card__body {
  padding: 8px 12px 10px;
}

.global-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.cohort-folder-header {
  font-weight: 700;
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: rgba(var(--v-theme-primary), 0.9);
  padding-block: 4px;
}

.cohort-system-header {
  font-size: 11px;
  padding-inline-start: 24px;
  color: rgba(var(--v-theme-on-surface), 0.55);
}
</style>
