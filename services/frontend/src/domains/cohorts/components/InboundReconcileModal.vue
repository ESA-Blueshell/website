<script lang="ts" setup>
import { computed, watch } from "vue"
import { useInboundReconcile } from "@/domains/cohorts/composables/useInboundReconcile"

const props = defineProps<{
  modelValue: boolean
  subjectId: number
  cohortId: number
}>()

const emit = defineEmits<{
  "update:modelValue": [value: boolean]
  applied: []
}>()

const reconcile = useInboundReconcile()
const matchedCount = computed(() => reconcile.preview.value?.matched.length ?? 0)
const skippedCount = computed(() => reconcile.preview.value?.skipped.length ?? 0)

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return reconcile.reset()
    void reconcile.load(props.subjectId, props.cohortId)
  },
)

function close() {
  emit("update:modelValue", false)
}

async function confirm() {
  if (await reconcile.apply(props.subjectId, props.cohortId)) emit("applied")
}
</script>

<template>
  <v-dialog
    :model-value="modelValue"
    data-testid="inbound-reconcile-modal"
    max-width="760"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <v-card>
      <v-card-title>Inbound reconcile</v-card-title>
      <v-card-text>
        <v-alert
          v-if="reconcile.errorMessage.value"
          class="mb-3"
          data-testid="inbound-reconcile-error"
          density="compact"
          type="error"
        >
          {{ reconcile.errorMessage.value }}
        </v-alert>
        <v-alert
          v-if="reconcile.preview.value && !reconcile.preview.value.writerSupported"
          class="mb-3"
          data-testid="inbound-reconcile-unsupported"
          density="compact"
          type="warning"
        >
          {{ reconcile.preview.value.fact.kind }} cannot be written from inbound reconcile.
        </v-alert>
        <v-alert
          v-if="reconcile.applyResult.value"
          class="mb-3"
          data-testid="inbound-reconcile-result"
          density="compact"
          type="success"
        >
          Job #{{ reconcile.applyResult.value.jobId ?? "?" }} accepted
          {{ reconcile.applyResult.value.acceptedCount }} row{{ reconcile.applyResult.value.acceptedCount === 1 ? "" : "s" }}.
        </v-alert>
        <v-progress-linear
          v-if="reconcile.loading.value"
          class="mb-3"
          indeterminate
        />

        <template v-if="reconcile.preview.value">
          <div class="summary-row mb-3">
            <v-chip
              v-for="item in [
                `${reconcile.preview.value.remoteCount} external`,
                `${matchedCount} matched`,
                `${skippedCount} skipped`,
              ]"
              :key="item"
              density="comfortable"
              size="small"
              variant="tonal"
            >
              {{ item }}
            </v-chip>
          </div>

          <p class="text-overline mb-1">
            Matched
          </p>
          <v-table
            class="mb-4"
            density="compact"
          >
            <thead>
              <tr>
                <th />
                <th>External</th>
                <th>User</th>
                <th>Fact</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="row in reconcile.preview.value.matched"
                :key="row.externalUserId"
                :data-testid="`inbound-match-${row.externalUserId}`"
              >
                <td>
                  <v-checkbox-btn
                    v-model="reconcile.selectedExternalUserIds.value"
                    :disabled="!row.writable || !reconcile.preview.value.writerSupported"
                    :value="row.externalUserId"
                    density="compact"
                    hide-details
                  />
                </td>
                <td>
                  <div>{{ row.externalLabel ?? row.externalUserId }}</div>
                  <div class="text-caption text-medium-emphasis">
                    {{ row.externalUserId }}
                  </div>
                </td>
                <td>
                  <div>{{ row.userFullName ?? `User #${row.userId}` }}</div>
                  <div class="text-caption text-medium-emphasis">
                    {{ row.userEmail ?? "No email" }}
                  </div>
                </td>
                <td>
                  <v-chip
                    :color="row.alreadyTrue ? 'success' : 'primary'"
                    size="x-small"
                    variant="tonal"
                  >
                    {{ row.alreadyTrue ? "Already true" : "Writable" }}
                  </v-chip>
                </td>
              </tr>
              <tr v-if="reconcile.preview.value.matched.length === 0">
                <td
                  class="text-medium-emphasis"
                  colspan="4"
                >
                  No matched extras.
                </td>
              </tr>
            </tbody>
          </v-table>

          <p class="text-overline mb-1">
            Skipped
          </p>
          <v-table density="compact">
            <thead>
              <tr>
                <th>External</th>
                <th>Reason</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="row in reconcile.preview.value.skipped"
                :key="`${row.reason}-${row.externalUserId}`"
                :data-testid="`inbound-skip-${row.externalUserId}`"
              >
                <td>{{ row.externalLabel ?? row.externalUserId }}</td>
                <td>{{ row.reason?.replace(/_/g, " ").toLowerCase() ?? "" }}</td>
              </tr>
              <tr v-if="reconcile.preview.value.skipped.length === 0">
                <td
                  class="text-medium-emphasis"
                  colspan="2"
                >
                  No skipped extras.
                </td>
              </tr>
            </tbody>
          </v-table>
        </template>
      </v-card-text>
      <v-card-actions>
        <v-spacer />
        <v-btn
          data-testid="inbound-reconcile-cancel"
          variant="text"
          @click="close"
        >
          Close
        </v-btn>
        <v-btn
          :disabled="!reconcile.canApply.value"
          :loading="reconcile.applying.value"
          color="primary"
          data-testid="inbound-reconcile-apply"
          variant="flat"
          @click="confirm"
        >
          Apply selected
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<style lang="scss" scoped>
.summary-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
</style>
