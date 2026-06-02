<script lang="ts" setup>
import {computed, onMounted, ref, watch} from "vue"
import {useRoute, useRouter} from "vue-router"
import TopBanner from "@/components/common/banners/TopBanner.vue"
import {$handleNetworkError} from "@/plugins/handleNetworkError"
import {
  type CohortDetail,
  enqueue,
  findCohortById,
} from "@/services/api"
import store from "@/plugins/store"

defineOptions({name: "CohortDetailPage"})

const router = useRouter()
const route = useRoute()

const cohort = ref<CohortDetail | null>(null)
const loading = ref<boolean>(false)
const triggering = ref<string | null>(null)
const errorMessage = ref<string | null>(null)
const successMessage = ref<string | null>(null)

const cohortId = computed<number | null>(() => {
  const raw = route.params.id
  const value = typeof raw === "string" ? Number(raw) : Number(Array.isArray(raw) ? raw[0] : raw)
  return Number.isFinite(value) ? value : null
})

const load = async () => {
  if (cohortId.value == null) return
  loading.value = true
  try {
    const response = await findCohortById({path: {id: cohortId.value}})
    cohort.value = response.data ?? null
  } catch (error) {
    cohort.value = null
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

const resync = () => {
  if (cohort.value == null) return
  void triggerJob("cohort.resync", {cohortId: cohort.value.id})
}

const reevaluateMember = (userId: number) => {
  void triggerJob("cohort.evaluate-user", {userId})
}

const formatJoinedAt = (value: string): string => {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString()
}

onMounted(async () => {
  if (!store.getters.isAdmin) {
    await router.replace("/")
    return
  }
  await load()
})

watch(cohortId, () => void load())
</script>

<template>
  <v-main>
    <top-banner :title="cohort?.label ?? 'Cohort'" />

    <div class="mx-3">
      <div class="mx-auto my-3 cohort-detail-page">
        <v-btn
          class="mb-3"
          data-testid="cohort-detail-back"
          prepend-icon="mdi-arrow-left"
          variant="text"
          @click="router.push({name: 'cohortDashboard'})"
        >
          Back to cohorts
        </v-btn>

        <v-alert
          v-if="errorMessage"
          class="mb-3"
          data-testid="cohort-detail-error"
          density="compact"
          type="error"
        >
          {{ errorMessage }}
        </v-alert>
        <v-alert
          v-if="successMessage"
          class="mb-3"
          data-testid="cohort-detail-success"
          density="compact"
          type="success"
        >
          {{ successMessage }}
        </v-alert>

        <v-card
          v-if="cohort"
          class="manager-card mb-4"
          rounded="lg"
          variant="flat"
        >
          <div class="manager-card__header">
            <div>
              <p class="text-overline mb-1">
                {{ cohort.system }} · {{ cohort.kind }}
              </p>
              <h2 class="text-h6 mb-1">
                {{ cohort.label }}
              </h2>
              <p class="text-caption text-medium-emphasis mb-0">
                {{ cohort.memberCount }} members<span
                  v-if="cohort.externalId"
                > · external id {{ cohort.externalId }}</span>
              </p>
            </div>

            <div class="d-flex ga-2">
              <v-btn
                :disabled="!!triggering"
                :loading="triggering === 'cohort.resync'"
                color="primary"
                data-testid="cohort-detail-resync-btn"
                variant="flat"
                @click="resync"
              >
                Re-push to {{ cohort.system }}
              </v-btn>
            </div>
          </div>
        </v-card>

        <v-card
          v-if="cohort"
          class="manager-card mb-4"
          rounded="lg"
          variant="flat"
        >
          <div class="manager-card__header">
            <div>
              <p class="text-overline mb-1">
                Rules
              </p>
              <h2 class="text-h6 mb-1">
                Why members are in this cohort ({{ cohort.rules.length }})
              </h2>
              <p class="text-caption text-medium-emphasis mb-0">
                A user joins this cohort when they match any enabled rule.
              </p>
            </div>
          </div>
          <v-list density="comfortable">
            <v-list-item
              v-if="cohort.rules.length === 0"
              subtitle="Add one via the Cohort Rule manager to start populating this cohort."
              title="No rules target this cohort."
            />
            <v-list-item
              v-for="rule in cohort.rules"
              :key="rule.id"
              :data-testid="`cohort-rule-${rule.id}`"
            >
              <template #prepend>
                <v-icon :icon="rule.enabled ? 'mdi-check-circle' : 'mdi-pause-circle'" />
              </template>
              <v-list-item-title>{{ rule.factKind }} = {{ rule.factKey }}</v-list-item-title>
              <v-list-item-subtitle>
                {{ rule.enabled ? "Enabled" : "Disabled" }}
              </v-list-item-subtitle>
            </v-list-item>
          </v-list>
        </v-card>

        <v-card
          v-if="cohort"
          class="manager-card"
          rounded="lg"
          variant="flat"
        >
          <div class="manager-card__header">
            <div>
              <p class="text-overline mb-1">
                Members
              </p>
              <h2 class="text-h6 mb-1">
                In this cohort ({{ cohort.members.length }})
              </h2>
            </div>
          </div>

          <v-list
            data-testid="cohort-member-list"
            density="comfortable"
          >
            <v-list-item
              v-if="cohort.members.length === 0"
              subtitle="Re-evaluating users or running Reconcile will populate this list."
              title="No members in this cohort yet."
            />
            <v-list-item
              v-for="member in cohort.members"
              :key="member.cohortMemberId"
              :class="{'cohort-member--deleted': member.isUserDeleted}"
              :data-testid="`cohort-member-${member.userId}`"
            >
              <template #prepend>
                <v-icon :icon="member.isUserDeleted ? 'mdi-account-off' : 'mdi-account'" />
              </template>
              <v-list-item-title>
                <span
                  v-if="member.isUserDeleted"
                  class="cohort-member-label"
                >
                  Deleted user #{{ member.userId }}
                </span>
                <span v-else>
                  {{ member.userFullName ?? `User #${member.userId}` }}
                </span>
                <v-chip
                  v-if="member.isUserDeleted"
                  class="ml-2"
                  color="warning"
                  size="x-small"
                  variant="tonal"
                >
                  Deleted
                </v-chip>
              </v-list-item-title>
              <v-list-item-subtitle>
                <span v-if="member.userEmail">{{ member.userEmail }} · </span>
                <span v-if="member.isUserDeleted">Retained for historical stats · </span>
                Joined {{ formatJoinedAt(member.joinedAt) }}
              </v-list-item-subtitle>
              <template #append>
                <v-btn
                  v-if="!member.isUserDeleted"
                  :data-testid="`cohort-member-reeval-${member.userId}`"
                  :disabled="!!triggering"
                  size="small"
                  variant="outlined"
                  @click="reevaluateMember(member.userId)"
                >
                  Re-evaluate
                </v-btn>
              </template>
            </v-list-item>
          </v-list>
        </v-card>

        <v-progress-linear
          v-if="loading"
          indeterminate
        />
      </div>
    </div>
  </v-main>
</template>

<style lang="scss" scoped>
.cohort-detail-page {
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

.cohort-member--deleted {
  opacity: 0.6;
}

.cohort-member--deleted .cohort-member-label {
  font-style: italic;
}
</style>
