<script lang="ts" setup>
import { computed, watch } from "vue"
import { useTargetPicker } from "@/domains/cohorts/composables/useTargetPicker"
import type { TargetSystem } from "@/domains/cohorts/adapters/cohorts"

const props = defineProps<{
  modelValue: boolean
  mode: "add" | "switch"
  subjectId: number
  system: TargetSystem
  cohortId?: number
}>()

const emit = defineEmits<{
  "update:modelValue": [value: boolean]
  saved: []
}>()

const {
  submitting,
  loading,
  errorMessage,
  conflict,
  descriptor,
  filteredOptions,
  hasCatalog,
  canCreate,
  form,
  reset,
  load,
  submitAdd,
  submitSwitch,
} = useTargetPicker()

const idLabel = computed(() => descriptor.value?.idLabel ?? "External target id")
const targetLabel = computed(() => descriptor.value?.targetLabel ?? "Target name")
const folderLabel = computed(() => descriptor.value?.folderLabel ?? null)
const catalogItems = computed(() =>
  filteredOptions.value.map((target) => ({
    ...target,
    title: [target.folderLabel, target.label].filter(Boolean).join(" / "),
    subtitle: target.memberCount == null ? target.externalId : `${target.externalId} · ${target.memberCount}`,
  })),
)

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    reset()
    void load(props.system)
  },
)

const close = () => emit("update:modelValue", false)

const submit = async () => {
  const ok =
    props.mode === "switch" && props.cohortId != null
      ? await submitSwitch(props.subjectId, props.cohortId)
      : await submitAdd(props.subjectId, props.system)
  if (ok) {
    emit("saved")
    close()
  }
}
</script>

<template>
  <v-dialog
    :model-value="modelValue"
    max-width="520"
    data-testid="target-picker-modal"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <v-card>
      <v-card-title>
        {{ mode === "switch" ? "Switch external target" : "Add external target" }}
      </v-card-title>

      <v-card-text>
        <v-alert
          v-if="conflict"
          class="mb-3"
          data-testid="target-picker-conflict"
          density="compact"
          type="warning"
        >
          This subject already has a {{ system }} target. Switch the existing one instead.
        </v-alert>
        <v-alert
          v-if="errorMessage"
          class="mb-3"
          data-testid="target-picker-error"
          density="compact"
          type="error"
        >
          {{ errorMessage }}
        </v-alert>

        <!-- Add mode: pick between linking an existing target and creating one. -->
        <template v-if="mode === 'add'">
          <v-tabs
            v-if="canCreate"
            v-model="form.tab"
            color="primary"
            data-testid="target-picker-tabs"
          >
            <v-tab value="existing">
              Existing
            </v-tab>
            <v-tab value="create">
              Create new
            </v-tab>
          </v-tabs>

          <v-window
            v-model="form.tab"
            :class="{ 'mt-3': canCreate }"
          >
            <v-window-item value="existing">
              <v-combobox
                v-if="hasCatalog"
                v-model="form.externalId"
                v-model:search="form.search"
                :items="catalogItems"
                :label="idLabel"
                :loading="loading"
                data-testid="target-picker-combobox"
                item-title="title"
                item-value="externalId"
                no-filter
                :return-object="false"
              />
              <v-text-field
                v-else
                v-model="form.externalId"
                data-testid="target-picker-external-id"
                :label="idLabel"
              />
            </v-window-item>
            <v-window-item
              v-if="canCreate"
              value="create"
            >
              <v-text-field
                v-model="form.label"
                data-testid="target-picker-label"
                :label="targetLabel"
              />
              <v-text-field
                v-if="folderLabel"
                v-model="form.folderHint"
                class="mt-2"
                data-testid="target-picker-folder"
                :label="`${folderLabel} (optional)`"
              />
            </v-window-item>
          </v-window>
        </template>

        <!-- Switch mode: repoint at a different target. -->
        <template v-else>
          <v-combobox
            v-if="hasCatalog"
            v-model="form.externalId"
            v-model:search="form.search"
            :items="catalogItems"
            :label="idLabel"
            :loading="loading"
            data-testid="target-picker-combobox"
            item-title="title"
            item-value="externalId"
            no-filter
            :return-object="false"
          />
          <v-text-field
            v-else
            v-model="form.externalId"
            data-testid="target-picker-external-id"
            :label="idLabel"
          />
          <v-checkbox
            v-model="form.deletePrevious"
            data-testid="target-picker-delete-previous"
            label="Delete the previous target on its system"
          />
          <v-checkbox
            v-model="form.reconcileNow"
            data-testid="target-picker-reconcile-now"
            label="Reconcile the new target now"
          />
        </template>
      </v-card-text>

      <v-card-actions>
        <v-spacer />
        <v-btn
          data-testid="target-picker-cancel"
          variant="text"
          @click="close"
        >
          Cancel
        </v-btn>
        <v-btn
          :loading="submitting"
          color="primary"
          data-testid="target-picker-submit"
          variant="flat"
          @click="submit"
        >
          {{ mode === "switch" ? "Switch" : "Save" }}
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>
