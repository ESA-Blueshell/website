<script lang="ts" setup>
import {computed, onMounted, ref, watch} from "vue"
import {useRoute, useRouter} from "vue-router"
import TopBanner from "@/components/common/banners/TopBanner.vue"
import {$handleNetworkError} from "@/plugins/handleNetworkError"
import {
  type CohortSubjectSummary,
  CohortSubjectCategory,
  findCohortSubjects,
} from "@/services/api"
import {COHORT_TYPE_ORDER, cohortTypeLabel} from "@/domains/cohorts/cohortTypeLabels"
import {useTableSort} from "@/composables/useTableSort"
import store from "@/plugins/store"

defineOptions({name: "CohortCategoryPage"})

const route = useRoute()
const router = useRouter()

const subjects = ref<CohortSubjectSummary[]>([])
const loading = ref<boolean>(false)

const CATEGORY_LABELS: Record<CohortSubjectCategory, string> = {
  [CohortSubjectCategory.COMMITTEES]: "Committees",
  [CohortSubjectCategory.PERIODS]: "Periods",
  [CohortSubjectCategory.MEMBERS]: "Members",
}

const category = computed<CohortSubjectCategory | null>(() => {
  const raw = String(route.params.category ?? "").toUpperCase()
  return (Object.values(CohortSubjectCategory) as string[]).includes(raw)
    ? (raw as CohortSubjectCategory)
    : null
})

const categoryLabel = computed<string>(() =>
  category.value != null ? CATEGORY_LABELS[category.value] : "Cohorts",
)

/**
 * A row per cohort, carrying what it is as a value rather than as a heading above it.
 *
 * As a column the kind is one word per row and it sorts, where a group heading would put the
 * same words on screen twice. Unsorted, the rows read in the order the kinds are declared and
 * then by name; a column sort replaces that only once one is asked for.
 */
type CohortRow = CohortSubjectSummary & {typeLabel: string}

const rows = computed<CohortRow[]>(() => {
  if (category.value == null) return []
  return subjects.value
    .filter((subject) => subject.category === category.value)
    .map((subject) => ({...subject, typeLabel: cohortTypeLabel(subject.type)}))
    .sort((left, right) => {
      const byType = COHORT_TYPE_ORDER.indexOf(left.type) - COHORT_TYPE_ORDER.indexOf(right.type)
      return byType !== 0 ? byType : left.label.localeCompare(right.label)
    })
})

type SortKey = "label" | "typeLabel" | "memberCount" | "mappingCount"

const {sortedItems, toggleSort, sortIcon, ariaSort} = useTableSort<CohortRow, SortKey>(rows, {
  label: (a, b) => a.label.localeCompare(b.label),
  typeLabel: (a, b) => a.typeLabel.localeCompare(b.typeLabel),
  memberCount: (a, b) => a.memberCount - b.memberCount,
  mappingCount: (a, b) => a.mappingCount - b.mappingCount,
})

const COLUMNS: ReadonlyArray<{label: string; sortKey: SortKey; align?: string; width: string}> = [
  {label: "Cohort", sortKey: "label", width: "42%"},
  {label: "Kind", sortKey: "typeLabel", width: "26%"},
  {label: "Members", sortKey: "memberCount", align: "text-right", width: "12%"},
  {label: "Sync targets", sortKey: "mappingCount", align: "text-right", width: "14%"},
]

const refresh = async () => {
  loading.value = true
  try {
    const response = await findCohortSubjects()
    subjects.value = response.data ?? []
  } catch (error) {
    $handleNetworkError(error)
  } finally {
    loading.value = false
  }
}

const openSubject = (subject: CohortSubjectSummary) => {
  void router.push({name: "cohortSubjectDetail", params: {id: subject.id}})
}

onMounted(async () => {
  if (!store.getters.isAdmin) {
    await router.replace("/")
    return
  }
  await refresh()
})

watch(category, async (next) => {
  if (next == null) {
    await router.replace({name: "cohortDashboard"})
  }
})
</script>

<template>
  <v-main>
    <top-banner :title="categoryLabel" />

    <v-container>
      <div class="mx-auto my-3 category-page">
        <v-btn
          class="mb-3"
          data-testid="cohort-category-back"
          prepend-icon="mdi-arrow-left"
          size="small"
          variant="text"
          @click="router.push({name: 'cohortDashboard'})"
        >
          All categories
        </v-btn>

        <v-card
          class="manager-card"
          data-testid="cohort-subject-list"
          rounded="lg"
          variant="flat"
        >
          <v-card-text>
            <!-- One heading, carrying the count the way the member table does. The card used
                 to repeat the banner's own word above a subtitle that held the number. -->
            <div class="manager-heading mb-4">
              <v-badge
                color="primary"
                :content="rows.length"
                data-testid="cohort-subject-count"
              >
                <h2 class="ma-0">
                  {{ categoryLabel }} &ndash; Cohorts
                </h2>
              </v-badge>
            </div>

            <div style="overflow-x: auto">
              <v-table class="manager-table">
                <thead>
                  <tr>
                    <th
                      v-for="column in COLUMNS"
                      :key="column.sortKey"
                      :aria-sort="ariaSort(column.sortKey)"
                      :class="['sortable-header', column.align]"
                      :data-testid="`cohort-header-${column.sortKey}`"
                      role="button"
                      :style="`width: ${column.width}`"
                      tabindex="0"
                      @click="toggleSort(column.sortKey)"
                      @keydown.enter="toggleSort(column.sortKey)"
                      @keydown.space.prevent="toggleSort(column.sortKey)"
                    >
                      {{ column.label }}
                      <v-icon
                        :icon="sortIcon(column.sortKey)"
                        size="16"
                      />
                    </th>
                    <th style="width: 6%" />
                  </tr>
                </thead>

                <tbody>
                  <tr
                    v-for="subject in sortedItems"
                    :key="subject.id"
                    class="manager-table__row"
                    :data-testid="`cohort-subject-row-${subject.id}`"
                    role="button"
                    tabindex="0"
                    @click="openSubject(subject)"
                    @keydown.enter.prevent="openSubject(subject)"
                    @keydown.space.prevent="openSubject(subject)"
                  >
                    <td class="font-weight-medium">
                      {{ subject.label }}
                    </td>
                    <td class="text-medium-emphasis">
                      {{ subject.typeLabel }}
                    </td>
                    <td class="text-right">
                      {{ subject.memberCount }}
                    </td>
                    <td class="text-right">
                      {{ subject.mappingCount }}
                    </td>
                    <td class="text-right">
                      <v-icon
                        icon="mdi-chevron-right"
                        size="20"
                      />
                    </td>
                  </tr>

                  <tr v-if="!loading && rows.length === 0">
                    <td
                      class="text-center text-medium-emphasis py-6"
                      colspan="5"
                    >
                      No cohorts yet. They appear automatically when the engine first
                      encounters them.
                    </td>
                  </tr>
                </tbody>
              </v-table>
            </div>
          </v-card-text>
        </v-card>
      </div>
    </v-container>
  </v-main>
</template>

<style lang="scss" scoped>
.category-page {
  max-width: 980px;
}
</style>
