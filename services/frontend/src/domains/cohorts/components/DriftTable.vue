<script lang="ts" setup>
import { CohortSubjectType } from "@/services/api"
import type { DriftReport, ExtraRow } from "../types"

defineProps<{
  report: DriftReport
  subjectType: CohortSubjectType
}>()

const emit = defineEmits<{
  remove: [{ cohortId: number; externalUserId: string }]
  link: [{ externalUserId: string }]
}>()

/** Maps subject type to the most relevant admin management page. */
function managementHrefFor(type: CohortSubjectType): string {
  switch (type) {
    case CohortSubjectType.COMMITTEE_MEMBERS:
      return `/committees/manage`
    case CohortSubjectType.PERIOD_PAYERS:
    case CohortSubjectType.PERIOD_MEMBERS:
    case CohortSubjectType.PERIOD_ACTIVE_MEMBERS:
      return `/contributions/manage`
    case CohortSubjectType.NEWSLETTER_SUBSCRIBERS:
    case CohortSubjectType.CUSTOM:
    default:
      return `/members/manage`
  }
}

function isKnown(row: ExtraRow): row is Extract<ExtraRow, { kind: "KNOWN_LOCAL_USER" }> {
  return row.kind === "KNOWN_LOCAL_USER"
}
</script>

<template>
  <div>
    <template v-if="!report.externalCohortId">
      <p class="text-body-2 text-medium-emphasis ma-0">
        This mapping has not been materialised yet — no external target exists to inspect.
      </p>
    </template>

    <template v-else-if="report.extras.length === 0 && report.missing.length === 0">
      <p class="text-body-2 text-medium-emphasis ma-0">
        No drift detected.
      </p>
    </template>

    <template v-else>
      <!-- Extras -->
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
                  color="error"
                  size="x-small"
                  variant="text"
                  @click="emit('remove', { cohortId: report.cohortId, externalUserId: row.externalUserId })"
                >
                  Remove
                </v-btn>
                <v-btn
                  v-if="!isKnown(row)"
                  :data-testid="`drift-extra-link-${row.externalUserId}`"
                  class="ml-1"
                  size="x-small"
                  variant="text"
                  @click="emit('link', { externalUserId: row.externalUserId })"
                >
                  Link to user
                </v-btn>
              </td>
            </tr>
          </tbody>
        </v-table>
      </div>

      <!-- Missing -->
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
  </div>
</template>
