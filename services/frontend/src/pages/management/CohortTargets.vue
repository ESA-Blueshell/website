<script lang="ts" setup>
import {computed, onMounted, ref} from "vue"
import TopBanner from "@/components/common/banners/TopBanner.vue"
import ManagerCard from "@/components/common/cards/ManagerCard.vue"
import {useTargetOverview} from "@/domains/cohorts"
import {systemLabel, TargetSystem, type ExternalTarget} from "@/domains/cohorts"
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
  writeRefusal,
  writing,
  createList,
  rename,
  archive,
  remove,
  tidyMoves,
  tidyFoldersToCreate,
  tidyPicked,
  tidyFailures,
  previewTidy,
  toggleTidyPick,
  applyTidyPicks,
} = useTargetOverview()

const tidying = ref(false)

async function openTidy() {
  tidying.value = true
  await previewTidy(TargetSystem.BREVO)
}

async function confirmTidy() {
  if (await applyTidyPicks(TargetSystem.BREVO)) tidying.value = false
}

/** The delete dialog: only for a list linked to nothing, confirmed by its name typed exactly. */
const deleting = ref<ExternalTarget | null>(null)
const typedName = ref("")

function openDelete(target: ExternalTarget) {
  deleting.value = target
  typedName.value = ""
  writeRefusal.value = null
}

async function confirmDelete() {
  const target = deleting.value
  if (target && await remove(TargetSystem.BREVO, target, typedName.value)) deleting.value = null
}

/** The new-list dialog: a name, and a folder picked or a new one named. */
const NEW_FOLDER = "__new__"
const creating = ref(false)
const newName = ref("")
const newFolderChoice = ref<string | null>(null)
const newFolderName = ref("")
const folderChoices = computed(() => [
  ...folderNames.value.map((name) => ({title: name, value: name})),
  {title: "New folder…", value: NEW_FOLDER},
])

function openCreate() {
  creating.value = true
  newName.value = ""
  newFolderChoice.value = null
  newFolderName.value = ""
  writeRefusal.value = null
}

async function confirmCreate() {
  const isNew = newFolderChoice.value === NEW_FOLDER
  const folder = isNew ? newFolderName.value.trim() : newFolderChoice.value
  if (await createList(TargetSystem.BREVO, newName.value.trim(), folder || null, isNew)) creating.value = false
}

/** The rename dialog, for one list at a time. */
const renaming = ref<ExternalTarget | null>(null)
const renameTo = ref("")

function openRename(target: ExternalTarget) {
  renaming.value = target
  renameTo.value = target.label
  writeRefusal.value = null
}

async function confirmRename() {
  const target = renaming.value
  if (target && await rename(TargetSystem.BREVO, target, renameTo.value.trim())) renaming.value = null
}

/**
 * One dialog for both moves. Filing one target and filing thirty ask the same question and
 * take the same answer, so they share the form rather than having one each.
 */
const movingTarget = ref<ExternalTarget | null>(null)
const movingSelectionOpen = ref(false)
const destination = ref<string | null>(null)

const moveDialogOpen = computed(() => movingTarget.value !== null || movingSelectionOpen.value)
const moveBusy = computed(() => moving.value !== null || movingSelection.value)

/** What this page lists: Brevo files contacts in lists. */
const targetNoun = `${systemLabel(TargetSystem.BREVO).toLowerCase()} list`

const moveTitle = computed(() => movingTarget.value
  ? `Move ${movingTarget.value.label}`
  : `Move ${selectedCount.value} ${targetNoun}${selectedCount.value === 1 ? "" : "s"}`)

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

        <v-alert
          v-if="writeRefusal && !creating && !renaming && !deleting"
          class="mb-3"
          data-testid="cohort-targets-write-refusal"
          density="compact"
          type="error"
        >
          {{ writeRefusal }}
        </v-alert>

        <manager-card
          eyebrow="Cohort targets"
          spaced
          :subtitle="errorMessage
            ? 'Nothing was read, so there is nothing to count.'
            : `${targets.length} ${descriptor?.kind === 'LIST' ? 'lists' : 'targets'} in ${folders.length} folder${folders.length === 1 ? '' : 's'} · ${unlinkedCount} linked to nothing`"
          testid="cohort-targets-summary"
          :title="systemLabel(TargetSystem.BREVO)"
        >
          <template #actions>
            <v-btn
              data-testid="cohort-targets-create"
              :disabled="loading"
              size="small"
              variant="outlined"
              @click="openCreate"
            >
              New list
            </v-btn>
            <v-btn
              data-testid="cohort-targets-tidy"
              :disabled="loading"
              size="small"
              variant="outlined"
              @click="openTidy"
            >
              Tidy folders
            </v-btn>
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
              <template #prepend>
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
                    :data-testid="`cohort-target-rename-${target.externalId}`"
                    size="small"
                    variant="text"
                    @click="openRename(target)"
                  >
                    Rename
                  </v-btn>
                  <v-btn
                    :data-testid="`cohort-target-archive-${target.externalId}`"
                    :disabled="writing || target.folderLabel === 'Archive'"
                    size="small"
                    variant="text"
                    @click="archive(TargetSystem.BREVO, target)"
                  >
                    Archive
                  </v-btn>
                  <!-- Brevo cannot undo a delete, so only a list linked to nothing offers one. -->
                  <v-btn
                    v-if="target.linkedCohortId == null"
                    color="error"
                    :data-testid="`cohort-target-delete-${target.externalId}`"
                    size="small"
                    variant="text"
                    @click="openDelete(target)"
                  >
                    Delete
                  </v-btn>
                  <v-btn
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
          :model-value="creating"
          :save-disabled="!newName.trim() || (newFolderChoice === NEW_FOLDER && !newFolderName.trim())"
          :save-loading="writing"
          save-label="Make the list"
          save-testid="cohort-target-create-confirm"
          show-save
          testid="cohort-target-create-dialog"
          title="New list"
          @cancel="creating = false"
          @save="confirmCreate"
          @update:model-value="(open) => { if (!open) creating = false }"
        >
          <v-text-field
            v-model="newName"
            data-testid="cohort-target-create-name"
            label="Name"
          />
          <v-select
            v-model="newFolderChoice"
            clearable
            data-testid="cohort-target-create-folder"
            :items="folderChoices"
            label="Folder"
          />
          <v-text-field
            v-if="newFolderChoice === NEW_FOLDER"
            v-model="newFolderName"
            data-testid="cohort-target-create-folder-name"
            label="New folder's name"
          />
          <v-alert
            v-if="writeRefusal"
            class="mt-2"
            data-testid="cohort-target-write-refusal"
            density="compact"
            type="error"
          >
            {{ writeRefusal }}
          </v-alert>
        </base-modal>

        <base-modal
          :model-value="renaming !== null"
          :save-disabled="!renameTo.trim() || renameTo.trim() === renaming?.label"
          :save-loading="writing"
          save-label="Rename"
          save-testid="cohort-target-rename-confirm"
          show-save
          testid="cohort-target-rename-dialog"
          :title="`Rename ${renaming?.label ?? ''}`"
          @cancel="renaming = null"
          @save="confirmRename"
          @update:model-value="(open) => { if (!open) renaming = null }"
        >
          <v-text-field
            v-model="renameTo"
            data-testid="cohort-target-rename-name"
            label="Name"
          />
          <v-alert
            v-if="writeRefusal"
            class="mt-2"
            data-testid="cohort-target-write-refusal"
            density="compact"
            type="error"
          >
            {{ writeRefusal }}
          </v-alert>
        </base-modal>

        <base-modal
          :model-value="tidying"
          :save-disabled="tidyPicked.size === 0"
          :save-loading="writing"
          :save-label="`Move ${tidyPicked.size}`"
          save-testid="cohort-targets-tidy-confirm"
          show-save
          testid="cohort-targets-tidy-dialog"
          title="Tidy folders"
          @cancel="tidying = false"
          @save="confirmTidy"
          @update:model-value="(open) => { if (!open) tidying = false }"
        >
          <p
            v-if="!writing && tidyMoves.length === 0 && tidyFailures.length === 0"
            data-testid="cohort-targets-tidy-nothing"
          >
            Every linked list is in its cohort type's folder.
          </p>
          <p
            v-if="tidyFoldersToCreate.length"
            class="mb-2"
            data-testid="cohort-targets-tidy-folders"
          >
            Folders that will be made: {{ tidyFoldersToCreate.join(", ") }}
          </p>
          <v-list density="compact">
            <v-list-item
              v-for="proposal in tidyMoves"
              :key="proposal.externalId"
              :data-testid="`cohort-targets-tidy-move-${proposal.externalId}`"
              :subtitle="`${proposal.from ?? 'No folder'} → ${proposal.to}`"
              :title="proposal.label"
            >
              <template #prepend>
                <v-checkbox-btn
                  :data-testid="`cohort-targets-tidy-pick-${proposal.externalId}`"
                  :model-value="tidyPicked.has(proposal.externalId)"
                  @update:model-value="toggleTidyPick(proposal.externalId)"
                />
              </template>
            </v-list-item>
          </v-list>
          <v-alert
            v-if="tidyFailures.length"
            class="mt-2"
            data-testid="cohort-targets-tidy-failures"
            density="compact"
            type="error"
          >
            <p
              v-for="failure in tidyFailures"
              :key="failure.externalId"
              class="mb-0"
            >
              {{ failure.label }}: {{ failure.message }}
            </p>
          </v-alert>
        </base-modal>

        <base-modal
          :model-value="deleting !== null"
          :save-disabled="typedName !== deleting?.label"
          :save-loading="writing"
          save-label="Delete for good"
          save-testid="cohort-target-delete-confirm"
          show-save
          testid="cohort-target-delete-dialog"
          :title="`Delete ${deleting?.label ?? ''}`"
          @cancel="deleting = null"
          @save="confirmDelete"
          @update:model-value="(open) => { if (!open) deleting = null }"
        >
          <p class="mb-3">
            Brevo cannot bring a deleted list back. Type <strong>{{ deleting?.label }}</strong> to delete it.
          </p>
          <v-text-field
            v-model="typedName"
            data-testid="cohort-target-delete-name"
            label="The list's name"
          />
          <v-alert
            v-if="writeRefusal"
            class="mt-2"
            data-testid="cohort-target-write-refusal"
            density="compact"
            type="error"
          >
            {{ writeRefusal }}
          </v-alert>
        </base-modal>

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
            label="Folder"
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
