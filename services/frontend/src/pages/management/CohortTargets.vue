<script lang="ts" setup>
import {onMounted, ref} from "vue"
import TopBanner from "@/components/common/banners/TopBanner.vue"
import ManagerCard from "@/components/common/cards/ManagerCard.vue"
import {useTargetOverview} from "@/domains/cohorts/composables/useTargetOverview"
import {TargetSystem} from "@/services/api"
import type {ExternalTarget} from "@/domains/cohorts/adapters/cohorts"
import BaseModal from "@/components/common/modals/BaseModal.vue"

defineOptions({name: "CohortTargets"})

const {
  loading,
  errorMessage,
  descriptor,
  targets,
  search,
  folders,
  folderNames,
  unlinkedCount,
  canMove,
  moving,
  load,
  move,
} = useTargetOverview()

/** The target being filed elsewhere, and where to. */
const movingTarget = ref<ExternalTarget | null>(null)
const destination = ref<string | null>(null)

function openMove(target: ExternalTarget) {
  movingTarget.value = target
  destination.value = target.folderLabel ?? null
}

async function confirmMove() {
  const target = movingTarget.value
  if (!target || !destination.value) return
  if (await move(TargetSystem.BREVO, target, destination.value)) movingTarget.value = null
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
          :subtitle="`${targets.length} ${descriptor?.kind === 'LIST' ? 'lists' : 'targets'} in ${folders.length} folder${folders.length === 1 ? '' : 's'} · ${unlinkedCount} linked to nothing`"
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
              <template #append>
                <div class="d-flex align-center gap-2">
                  <span class="text-caption text-medium-emphasis">
                    {{ target.memberCount ?? "—" }} contacts
                  </span>
                  <!-- A target nothing points at is either finished with or a mistake. -->
                  <v-btn
                    v-if="canMove"
                    :data-testid="`cohort-target-move-${target.externalId}`"
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
          :model-value="movingTarget !== null"
          :save-disabled="!destination || destination === movingTarget?.folderLabel"
          :save-loading="moving !== null"
          save-label="Move"
          save-testid="cohort-target-move-confirm"
          show-save
          testid="cohort-target-move-dialog"
          :title="`Move ${movingTarget?.label ?? ''}`"
          @cancel="movingTarget = null"
          @save="confirmMove"
          @update:model-value="(open) => { if (!open) movingTarget = null }"
        >
          <!-- Only folders the system actually has: a name that is not one of these would
               be refused, so it is not offered. -->
          <v-select
            v-model="destination"
            data-testid="cohort-target-move-folder"
            :items="folderNames"
            label="Folder"
          />
        </base-modal>
      </div>
    </v-container>
  </v-main>
</template>

<style lang="scss" scoped>
.cohort-targets-page {
  max-width: 980px;
}
</style>
