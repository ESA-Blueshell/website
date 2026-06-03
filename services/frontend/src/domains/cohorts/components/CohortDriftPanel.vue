<script lang="ts" setup>
import { onMounted, ref } from "vue"
import type { CohortSubjectType } from "@/services/api"
import { useCohortDrift } from "../composables/useCohortDrift"
import DriftTable from "./DriftTable.vue"
import LinkUserModal from "./LinkUserModal.vue"
import type { TargetSystem } from "../types"

const props = defineProps<{
  subjectId: number
  system: TargetSystem
  subjectType: CohortSubjectType
}>()

const drift = useCohortDrift(props.subjectId, props.system)
const linkingExternalUserId = ref<string | null>(null)

onMounted(() => drift.load())

async function handleRemove({ cohortId, externalUserId }: { cohortId: number; externalUserId: string }) {
  await drift.remove(cohortId, externalUserId)
}

function handleLink({ externalUserId }: { externalUserId: string }) {
  linkingExternalUserId.value = externalUserId
}

async function handleReconcile() {
  await drift.reconcile()
  await drift.load()
}

async function doLink(userId: number) {
  if (linkingExternalUserId.value == null) return { type: "ok" as const }
  return drift.link(userId, linkingExternalUserId.value)
}
</script>

<template>
  <v-expansion-panels variant="accordion">
    <v-expansion-panel
      :data-testid="`cohort-drift-panel-${system.toLowerCase()}`"
      title="Drift"
    >
      <v-expansion-panel-text>
        <v-alert
          v-if="drift.error.value"
          class="mb-3"
          density="compact"
          type="error"
        >
          {{ drift.error.value }}
        </v-alert>

        <v-progress-linear
          v-if="drift.loading.value || drift.reconciling.value"
          class="mb-3"
          indeterminate
        />

        <div class="d-flex align-center justify-space-between mb-3">
          <span
            v-if="drift.report.value?.lastReconciledAt"
            class="text-caption text-medium-emphasis"
          >
            Last reconciled {{ new Date(drift.report.value.lastReconciledAt).toLocaleString() }}
          </span>
          <span
            v-else
            class="text-caption text-medium-emphasis"
          >
            Never reconciled
          </span>
          <v-btn
            :loading="drift.reconciling.value"
            density="compact"
            variant="tonal"
            @click="handleReconcile"
          >
            Reconcile now
          </v-btn>
        </div>

        <drift-table
          v-if="drift.report.value"
          :report="drift.report.value"
          :subject-type="subjectType"
          @remove="handleRemove"
          @link="handleLink"
        />
      </v-expansion-panel-text>
    </v-expansion-panel>
  </v-expansion-panels>

  <link-user-modal
    v-if="linkingExternalUserId != null"
    :external-user-id="linkingExternalUserId"
    :link="doLink"
    :model-value="linkingExternalUserId != null"
    @update:model-value="(val) => { if (!val) linkingExternalUserId = null }"
  />
</template>
