<script lang="ts" setup>
import { onMounted, ref } from "vue"
import { CohortSubjectType } from "@/services/api"
import UserPicker from "@/components/form/fields/UserPicker.vue"
import {
  fetchDrift,
  linkUserToExternal,
  removeExternalMember,
  triggerReconcile,
  type DriftReport,
  type ExternalUserConflict,
  type ExtraRow,
  type TargetSystem,
} from "../adapters/cohorts"

const props = defineProps<{
  cohortId: number
  subjectId: number
  system: TargetSystem
  subjectType: CohortSubjectType
}>()

const report = ref<DriftReport | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)
const removing = ref<string | null>(null)
const reconciling = ref(false)
const linkingExternalUserId = ref<string | null>(null)
const linkUserId = ref<number | undefined>(undefined)
const linkSubmitting = ref(false)
const linkConflict = ref<ExternalUserConflict | null>(null)

onMounted(() => void load())

async function load() {
  loading.value = true
  error.value = null
  try {
    report.value = await fetchDrift(props.subjectId, props.system)
  } catch (e: unknown) {
    error.value = (e as Error)?.message ?? "Failed to load drift"
  } finally {
    loading.value = false
  }
}

async function reconcile() {
  reconciling.value = true
  error.value = null
  try {
    await triggerReconcile(props.cohortId)
    await load()
  } catch (e: unknown) {
    error.value = (e as Error)?.message ?? "Failed to enqueue reconcile"
  } finally {
    reconciling.value = false
  }
}

async function removeExternal(externalUserId: string) {
  removing.value = externalUserId
  error.value = null
  try {
    await removeExternalMember(props.cohortId, externalUserId)
    await load()
  } catch (e: unknown) {
    error.value = (e as Error)?.message ?? "Failed to enqueue removal"
  } finally {
    removing.value = null
  }
}

function openLinkDialog(externalUserId: string) {
  linkingExternalUserId.value = externalUserId
  linkUserId.value = undefined
  linkConflict.value = null
}

function closeLinkDialog() {
  linkingExternalUserId.value = null
  linkUserId.value = undefined
  linkConflict.value = null
}

async function submitLink() {
  if (linkUserId.value == null || linkingExternalUserId.value == null) return
  linkSubmitting.value = true
  linkConflict.value = null
  try {
    const result = await linkUserToExternal(
      props.subjectId,
      linkUserId.value,
      props.system,
      linkingExternalUserId.value,
    )
    if (result.type === "conflict") {
      linkConflict.value = result.conflict
      return
    }
    closeLinkDialog()
    await load()
  } catch (e: unknown) {
    error.value = (e as Error)?.message ?? "Failed to link user"
  } finally {
    linkSubmitting.value = false
  }
}

function managementHrefFor(type: CohortSubjectType): string {
  switch (type) {
    case CohortSubjectType.COMMITTEE_MEMBERS:
      return "/committees/manage"
    case CohortSubjectType.PERIOD_PAYERS:
    case CohortSubjectType.PERIOD_MEMBERS:
    case CohortSubjectType.PERIOD_ACTIVE_MEMBERS:
      return "/contributions/manage"
    case CohortSubjectType.NEWSLETTER_SUBSCRIBERS:
    case CohortSubjectType.CUSTOM:
    default:
      return "/members/manage"
  }
}

function isKnown(row: ExtraRow): boolean {
  return row.kind === "KNOWN_LOCAL_USER" && row.userId != null
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
          v-if="error"
          class="mb-3"
          density="compact"
          type="error"
        >
          {{ error }}
        </v-alert>

        <v-progress-linear
          v-if="loading || reconciling"
          class="mb-3"
          indeterminate
        />

        <div class="d-flex align-center justify-space-between mb-3">
          <span
            v-if="report?.lastReconciledAt"
            class="text-caption text-medium-emphasis"
          >
            Last reconciled {{ new Date(report.lastReconciledAt).toLocaleString() }}
          </span>
          <span
            v-else
            class="text-caption text-medium-emphasis"
          >
            Never reconciled
          </span>
          <v-btn
            :loading="reconciling"
            density="compact"
            variant="tonal"
            @click="reconcile"
          >
            Reconcile now
          </v-btn>
        </div>

        <template v-if="report">
          <p
            v-if="!report.externalCohortId"
            class="text-body-2 text-medium-emphasis ma-0"
          >
            This mapping has not been materialised yet — no external target exists to inspect.
          </p>

          <p
            v-else-if="report.extras.length === 0 && report.missing.length === 0"
            class="text-body-2 text-medium-emphasis ma-0"
          >
            No drift detected.
          </p>

          <template v-else>
            <div
              v-if="report.extras.length"
              class="mb-4"
            >
              <p class="text-overline mb-1">
                Extra in external system ({{ report.extras.length }})
              </p>
              <v-table density="compact">
                <thead>
                  <tr>
                    <th>External id</th>
                    <th>Label</th>
                    <th>Local match</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  <tr
                    v-for="row in report.extras"
                    :key="row.externalUserId"
                    :data-testid="`drift-extra-${row.externalUserId}`"
                  >
                    <td class="text-caption text-medium-emphasis">
                      {{ row.externalUserId }}
                    </td>
                    <td>{{ row.label ?? "—" }}</td>
                    <td>
                      <template v-if="isKnown(row)">
                        <a
                          :data-testid="`drift-extra-user-link-${row.userId}`"
                          :href="managementHrefFor(subjectType)"
                          class="text-primary"
                          target="_blank"
                        >
                          {{ row.fullName ?? row.email ?? `User #${row.userId}` }}
                        </a>
                        <v-chip
                          v-if="row.softDeleted"
                          class="ml-1"
                          color="warning"
                          size="x-small"
                          variant="tonal"
                        >
                          Deleted
                        </v-chip>
                      </template>
                      <span
                        v-else
                        class="text-medium-emphasis"
                      >Unknown</span>
                    </td>
                    <td class="text-right">
                      <v-btn
                        :data-testid="`drift-extra-remove-${row.externalUserId}`"
                        :loading="removing === row.externalUserId"
                        color="error"
                        size="x-small"
                        variant="text"
                        @click="removeExternal(row.externalUserId)"
                      >
                        Remove
                      </v-btn>
                      <v-btn
                        v-if="!isKnown(row)"
                        :data-testid="`drift-extra-link-${row.externalUserId}`"
                        class="ml-1"
                        size="x-small"
                        variant="text"
                        @click="openLinkDialog(row.externalUserId)"
                      >
                        Link to user
                      </v-btn>
                    </td>
                  </tr>
                </tbody>
              </v-table>
            </div>

            <div v-if="report.missing.length">
              <p class="text-overline mb-1">
                Missing from external system ({{ report.missing.length }})
              </p>
              <p class="text-body-2 text-medium-emphasis mb-1">
                These members are desired locally but absent externally. The sync queue resolves them automatically.
              </p>
              <v-list density="compact">
                <v-list-item
                  v-for="row in report.missing"
                  :key="row.userId"
                  :data-testid="`drift-missing-${row.userId}`"
                  :subtitle="row.hasExternalMapping ? 'Has external id mapping — sync pending' : 'No external id mapping yet'"
                  :title="`User #${row.userId}`"
                />
              </v-list>
            </div>
          </template>
        </template>
      </v-expansion-panel-text>
    </v-expansion-panel>
  </v-expansion-panels>

  <v-dialog
    :model-value="linkingExternalUserId != null"
    max-width="440"
    @update:model-value="(value: boolean) => { if (!value) closeLinkDialog() }"
  >
    <v-card title="Link external user to local account">
      <v-card-text>
        <p class="text-body-2 text-medium-emphasis mb-3">
          External id: <code>{{ linkingExternalUserId }}</code>
        </p>

        <user-picker
          v-model="linkUserId"
          label="Local user"
          required
        />

        <v-alert
          v-if="linkConflict"
          class="mt-2"
          density="compact"
          type="warning"
          variant="tonal"
        >
          That external id is already linked to
          <strong>{{ linkConflict.existingUserFullName ?? `User #${linkConflict.existingUserId}` }}</strong>.
          Resolve the existing mapping first or choose a different user.
        </v-alert>
      </v-card-text>

      <v-card-actions>
        <v-spacer />
        <v-btn
          variant="text"
          @click="closeLinkDialog"
        >
          Cancel
        </v-btn>
        <v-btn
          :disabled="linkUserId == null || linkSubmitting"
          :loading="linkSubmitting"
          color="primary"
          variant="flat"
          @click="submitLink"
        >
          Link
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>
