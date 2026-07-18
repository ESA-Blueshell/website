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
import InboundReconcileModal from "@/domains/cohorts/components/InboundReconcileModal.vue"
import TargetPickerModal from "@/domains/cohorts/components/TargetPickerModal.vue"
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

const formatJoinedAt = (value: string): string => {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString()
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

        <v-card
          v-if="subject"
          class="manager-card mb-3"
          rounded="lg"
          variant="flat"
        >
          <div class="manager-card__header">
            <div>
              <p class="text-overline mb-0">
                {{ CATEGORY_LABELS[subject.category] }} · {{ subject.type.replace(/_/g, " ").toLowerCase() }}
              </p>
              <h2 class="subject-label">
                {{ subject.label }}
              </h2>
              <p
                v-if="subject.description"
                class="text-caption text-medium-emphasis mb-0"
              >
                {{ subject.description }}
              </p>
              <p class="text-caption text-medium-emphasis mb-0">
                {{ subject.members.length }} members · {{ subject.mappings.length }} sync target{{ subject.mappings.length === 1 ? "" : "s" }}
              </p>
            </div>
          </div>
        </v-card>

        <!-- Rules: why people are in this cohort -->
        <v-card
          v-if="subject && subject.rules.length"
          class="manager-card mb-3"
          rounded="lg"
          variant="flat"
        >
          <div class="manager-card__header">
            <p class="text-overline mb-0">
              Rules
            </p>
          </div>
          <v-list density="comfortable">
            <v-list-item
              v-for="rule in subject.rules"
              :key="rule.id"
              :data-testid="`cohort-subject-rule-${rule.id}`"
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

        <!-- Sync target tabs: one per external system mapping. -->
        <v-card
          v-if="subject"
          class="manager-card mb-3"
          rounded="lg"
          variant="flat"
        >
          <div class="manager-card__header d-flex align-center justify-space-between">
            <p class="text-overline mb-0">
              Sync targets
            </p>
            <v-btn
              data-testid="cohort-subject-add-target"
              prepend-icon="mdi-plus"
              size="small"
              variant="outlined"
              @click="openAddTarget"
            >
              Add target
            </v-btn>
          </div>

          <v-tabs
            v-model="activeTab"
            color="primary"
            data-testid="cohort-subject-tabs"
          >
            <v-tab
              v-for="mapping in subject.mappings"
              :key="mapping.system"
              :data-testid="`cohort-subject-tab-${mapping.system.toLowerCase()}`"
              :value="mapping.system"
            >
              {{ labelForSystem(mapping.system) }}
            </v-tab>
            <v-tab
              v-if="subject.mappings.length === 0"
              disabled
              value="__none__"
            >
              No targets
            </v-tab>
          </v-tabs>

          <v-window v-model="activeTab">
            <v-window-item
              v-for="mapping in subject.mappings"
              :key="mapping.system"
              :value="mapping.system"
            >
              <div class="mapping-panel">
                <div class="mapping-meta">
                  <div class="mapping-meta-cell">
                    <div class="mapping-meta-label">
                      Kind
                    </div>
                    <div class="mapping-meta-value">
                      {{ mapping.kind }}
                    </div>
                  </div>
                  <div class="mapping-meta-cell">
                    <div class="mapping-meta-label">
                      External label
                    </div>
                    <div class="mapping-meta-value">
                      {{ mapping.label }}
                    </div>
                  </div>
                  <div class="mapping-meta-cell">
                    <div class="mapping-meta-label">
                      External id
                    </div>
                    <div class="mapping-meta-value">
                      {{ mapping.externalId ?? "—" }}
                    </div>
                  </div>
                  <div class="mapping-meta-cell mapping-meta-cell--action">
                    <v-btn
                      :data-testid="`cohort-subject-switch-target-${mapping.system.toLowerCase()}`"
                      prepend-icon="mdi-swap-horizontal"
                      size="small"
                      variant="outlined"
                      @click="openSwitchTarget(mapping.cohortId, mapping.system)"
                    >
                      Switch target
                    </v-btn>
                    <v-btn
                      :data-testid="`cohort-subject-inbound-reconcile-${mapping.system.toLowerCase()}`"
                      :disabled="!mapping.externalId"
                      class="mt-2"
                      prepend-icon="mdi-import"
                      size="small"
                      variant="outlined"
                      @click="openInboundReconcile(mapping.cohortId)"
                    >
                      Inbound reconcile
                    </v-btn>
                  </div>
                </div>

                <cohort-drift-panel
                  v-if="subjectId != null"
                  :cohort-id="mapping.cohortId"
                  :subject-id="subjectId"
                  :subject-type="(subject.type as CohortSubjectType)"
                  :system="(mapping.system as TargetSystem)"
                  class="mt-4"
                />
              </div>
            </v-window-item>
          </v-window>

          <div
            v-if="!subject.mappings.length"
            class="mapping-panel"
          >
            <p class="text-body-2 text-medium-emphasis">
              No external mappings yet. Engine cohorts are created lazily on first sync,
              or attach one now.
            </p>
            <v-btn
              data-testid="cohort-subject-add-target-empty"
              prepend-icon="mdi-plus"
              size="small"
              variant="outlined"
              @click="openAddTarget"
            >
              Add target
            </v-btn>
          </div>
        </v-card>

        <!-- Members: shared across all mappings (membership is subject-level). -->
        <v-card
          v-if="subject"
          class="manager-card"
          rounded="lg"
          variant="flat"
        >
          <div class="manager-card__header">
            <p class="text-overline mb-0">
              Members ({{ subject.members.length }})
            </p>
          </div>
          <v-list
            data-testid="cohort-subject-member-list"
            density="compact"
          >
            <v-list-item
              v-if="subject.members.length === 0"
              subtitle="Re-evaluating users will populate this list."
              title="No members yet."
            />
            <v-list-item
              v-for="member in subject.members"
              :key="member.cohortMemberId"
              :class="{'subject-member--deleted': member.isUserDeleted}"
              :data-testid="`cohort-subject-member-${member.userId}`"
            >
              <template #prepend>
                <v-icon :icon="member.isUserDeleted ? 'mdi-account-off' : 'mdi-account'" />
              </template>
              <v-list-item-title>
                <span v-if="member.isUserDeleted">Deleted user #{{ member.userId }}</span>
                <span v-else>{{ member.userFullName ?? `User #${member.userId}` }}</span>
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
                  :data-testid="`cohort-subject-member-reeval-${member.userId}`"
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

.manager-card {
  background: rgba(var(--v-theme-surface), 0.92);
  box-shadow: none;
}

.manager-card__header {
  padding: 10px 14px 8px;
}

.subject-label {
  margin: 0;
  font-size: 22px;
  font-weight: 600;
  line-height: 1.15;
}

.mapping-panel {
  padding: 14px 16px;
}

.mapping-meta {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 8px 18px;
  margin-bottom: 12px;
}

.mapping-meta-cell {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.mapping-meta-label {
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: rgba(var(--v-theme-on-surface), 0.6);
}

.mapping-meta-value {
  font-size: 14px;
  font-weight: 600;
}

.subject-member--deleted {
  opacity: 0.6;
}
</style>
