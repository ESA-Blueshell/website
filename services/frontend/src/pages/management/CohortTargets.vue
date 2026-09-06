<script lang="ts" setup>
import {computed, onMounted, ref} from "vue"
import TopBanner from "@/components/common/banners/TopBanner.vue"
import ManagerCard from "@/components/common/cards/ManagerCard.vue"
import {useTargetOverview} from "@/domains/cohorts/composables/useTargetOverview"
import {TargetSystem, type ExternalTarget} from "@/domains/cohorts/adapters/cohorts"
import BaseModal from "@/components/common/modals/BaseModal.vue"

defineOptions({name: "CohortTargets"})

const {
  loading,
  errorMessage,
  descriptor,
  targets,
  search,
  matching,
  folders,
  folderNames,
  unlinkedCount,
  canMove,
  moving,
  selectedCount,
  allMatchingSelected,
  movingSelection,
  rejection,
  failedMoves,
  isSelected,
  toggleSelection,
  toggleAllMatching,
  clearSelection,
  load,
  move,
  moveSelected,
} = useTargetOverview()

/**
 * One dialog for both moves. Filing one target and filing thirty ask the same question and
 * take the same answer, so they share the form rather than having one each.
 */
const movingTarget = ref<ExternalTarget | null>(null)
const movingSelectionOpen = ref(false)
const destination = ref<string | null>(null)

const moveDialogOpen = computed(() => movingTarget.value !== null || movingSelectionOpen.value)
const moveBusy = computed(() => moving.value !== null || movingSelection.value)

/** The system's own word for what it holds — Brevo has lists, another system may not. */
const targetNoun = computed(() => descriptor.value?.targetLabel?.toLowerCase() ?? "target")

const moveTitle = computed(() => movingTarget.value
  ? `Move ${movingTarget.value.label}`
  : `Move ${selectedCount.value} ${targetNoun.value}${selectedCount.value === 1 ? "" : "s"}`)

function openMove(target: ExternalTarget) {
  movingTarget.value = target
  destination.value = target.folderLabel ?? null
}

function openBulkMove() {
  movingSelectionOpen.value = true
  // No sensible default: the point of moving a set is that they are not all in one place.
  destination.value = null
}

function closeMoveDialog() {
  movingTarget.value = null
  movingSelectionOpen.value = false
}

async function confirmMove() {
  if (!destination.value) return
  const target = movingTarget.value
  if (target) {
    if (await move(TargetSystem.BREVO, target, destination.value)) closeMoveDialog()
    return
  }
  // A refusal keeps the dialog open with its reasons; so does a system that moved some but
  // not all, because the ones left are still selected and can be tried again.
  if (await moveSelected(TargetSystem.BREVO, destination.value)) closeMoveDialog()
}

onMounted(() => void load(TargetSystem.BREVO))
</script>

<template>
  <v-main>
    <top-banner title="Brevo targets" />

    <v-container>
      <div class="mx-auto my-3 cohort-targets-page">
        <v-alert
          v-if="errorMessage"
          class="mb-3"
          data-testid="cohort-targets-error"
          density="compact"
          type="error"
        >
          {{ errorMessage }}
        </v-alert>

        <manager-card
          eyebrow="Cohort targets"
          spaced
          :subtitle="errorMessage
            ? 'Nothing was read, so there is nothing to count.'
            : `${targets.length} ${descriptor?.kind === 'LIST' ? 'lists' : 'targets'} in ${folders.length} folder${folders.length === 1 ? '' : 's'} · ${unlinkedCount} linked to nothing`"
          testid="cohort-targets-summary"
          :title="descriptor?.systemLabel ?? 'Targets'"
        >
          <template #actions>
            <v-btn
              data-testid="cohort-targets-refresh"
              :disabled="loading"
              size="small"
              variant="outlined"
              @click="load(TargetSystem.BREVO)"
            >
              Refresh
            </v-btn>
          </template>

          <v-text-field
            v-model="search"
            clearable
            data-testid="cohort-targets-search"
            density="comfortable"
            hide-details
            label="Search by name, folder or id"
            prepend-inner-icon="mdi-magnify"
          />

          <div
            v-if="canMove"
            class="selection-bar"
            data-testid="cohort-targets-selection-bar"
          >
            <v-checkbox-btn
              data-testid="cohort-targets-select-all"
              :disabled="matching.length === 0"
              :label="`Select all ${matching.length} shown`"
              :model-value="allMatchingSelected"
              @update:model-value="toggleAllMatching"
            />
            <v-spacer />
            <span
              v-if="selectedCount"
              class="text-caption text-medium-emphasis"
              data-testid="cohort-targets-selected-count"
            >
              {{ selectedCount }} selected
            </span>
            <v-btn
              v-if="selectedCount"
              data-testid="cohort-targets-clear-selection"
              size="small"
              variant="text"
              @click="clearSelection"
            >
              Clear
            </v-btn>
            <v-btn
              data-testid="cohort-targets-move-selected"
              :disabled="selectedCount === 0"
              size="small"
              variant="outlined"
              @click="openBulkMove"
            >
              Move selected
            </v-btn>
          </div>
        </manager-card>

        <v-progress-linear
          v-if="loading"
          class="mb-3"
          data-testid="cohort-targets-loading"
          indeterminate
        />

        <manager-card
          v-for="folder in folders"
          :key="folder.label ?? '__unfiled__'"
          class="mb-3"
          :data-testid="`cohort-target-folder-${folder.label ?? 'unfiled'}`"
          :eyebrow="folder.label ?? 'No folder'"
          flush
          :subtitle="`${folder.targets.length} · ${folder.memberCount ?? '—'} contacts · ${folder.linkedCount} linked`"
        >
          <v-list density="compact">
            <v-list-item
              v-for="target in folder.targets"
              :key="target.externalId"
              :data-testid="`cohort-target-${target.externalId}`"
              :subtitle="`id ${target.externalId}`"
              :title="target.label"
            >
              <template
                v-if="canMove"
                #prepend
              >
                <v-checkbox-btn
                  :data-testid="`cohort-target-select-${target.externalId}`"
                  :model-value="isSelected(target.externalId)"
                  @update:model-value="toggleSelection(target.externalId)"
                />
              </template>
              <template #append>
                <div class="d-flex align-center gap-2">
                  <span class="text-caption text-medium-emphasis">
                    {{ target.memberCount ?? "—" }} contacts
                  </span>
                  <!-- A target nothing points at is either finished with or a mistake. -->
                  <v-btn
                    v-if="canMove"
                    :data-testid="`cohort-target-move-${target.externalId}`"
                    :disabled="moving === target.externalId"
                    :loading="moving === target.externalId"
                    size="small"
                    variant="text"
                    @click="openMove(target)"
                  >
                    Move
                  </v-btn>
                  <v-chip
                    :color="target.linkedCohortId == null ? undefined : 'primary'"
                    :data-testid="`cohort-target-link-${target.externalId}`"
                    size="small"
                    variant="tonal"
                  >
                    {{ target.linkedCohortId == null ? "Unlinked" : "Linked" }}
                  </v-chip>
                </div>
              </template>
            </v-list-item>
          </v-list>
        </manager-card>

        <manager-card
          v-if="!loading && folders.length === 0"
          eyebrow="Cohort targets"
          :subtitle="search ? 'Nothing matches that search.' : 'This system reports no targets.'"
          testid="cohort-targets-empty"
        />
        <base-modal
          :model-value="moveDialogOpen"
          :save-disabled="!destination || destination === movingTarget?.folderLabel"
          :save-loading="moveBusy"
          save-label="Move"
          save-testid="cohort-target-move-confirm"
          show-save
          testid="cohort-target-move-dialog"
          :title="moveTitle"
          @cancel="closeMoveDialog"
          @save="confirmMove"
          @update:model-value="(open) => { if (!open) closeMoveDialog() }"
        >
          <!-- Only folders the system actually has: a name that is not one of these would
               be refused, so it is not offered. -->
          <v-select
            v-model="destination"
            data-testid="cohort-target-move-folder"
            :items="folderNames"
            :label="descriptor?.folderLabel ?? 'Folder'"
          />

          <!-- Refused whole: nothing was sent, so the selection is still there to correct. -->
          <v-alert
            v-if="rejection"
            class="mt-2"
            data-testid="cohort-target-move-rejection"
            density="compact"
            type="warning"
          >
            <p
              v-for="reason in rejection.reasons"
              :key="reason.code"
              class="mb-0"
            >
              {{ reason.message }}
            </p>
            <v-btn
              v-if="rejection.requiresReload"
              class="mt-2"
              data-testid="cohort-target-move-reload"
              size="small"
              variant="outlined"
              @click="load(TargetSystem.BREVO)"
            >
              Reload the catalogue
            </v-btn>
          </v-alert>

          <!-- Accepted, then the system refused some. The ones that moved stay moved. -->
          <v-alert
            v-if="failedMoves.length"
            class="mt-2"
            data-testid="cohort-target-move-failures"
            density="compact"
            type="error"
          >
            <p
              v-for="failure in failedMoves"
              :key="failure.externalId"
              class="mb-0"
            >
              {{ failure.label }}: {{ failure.message }}
            </p>
          </v-alert>
        </base-modal>
      </div>
    </v-container>
  </v-main>
</template>

<style lang="scss" scoped>
.cohort-targets-page {
  max-width: 980px;
}

.selection-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
}
</style>
