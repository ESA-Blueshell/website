<script lang="ts" setup>
import {computed, onMounted, ref, watch} from "vue"
import {useRoute, useRouter} from "vue-router"
import ManagerCard from "@/components/common/cards/ManagerCard.vue"
import TopBanner from "@/components/common/banners/TopBanner.vue"
import {$handleNetworkError} from "@/plugins/handleNetworkError"
import {
  type CohortSubjectSummary,
  CohortSubjectCategory,
  findCohortSubjects,
} from "@/services/api"
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
  [CohortSubjectCategory.OTHER]: "Other",
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

const visibleSubjects = computed<CohortSubjectSummary[]>(() => {
  if (category.value == null) return []
  return subjects.value
    .filter((subject) => subject.category === category.value)
    .slice()
    .sort((leftSubject, rightSubject) => leftSubject.label.localeCompare(rightSubject.label))
})

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

        <manager-card
          :eyebrow="categoryLabel"
          :subtitle="`${visibleSubjects.length} cohort${visibleSubjects.length === 1 ? '' : 's'}`"
        >
          <v-list
            data-testid="cohort-subject-list"
            density="comfortable"
          >
            <v-list-item
              v-if="!loading && visibleSubjects.length === 0"
              subtitle="They appear automatically when the engine first encounters them."
              title="No cohorts yet."
            />
            <v-list-item
              v-for="subject in visibleSubjects"
              :key="subject.id"
              :data-testid="`cohort-subject-row-${subject.id}`"
              role="button"
              tabindex="0"
              @click="openSubject(subject)"
              @keydown.enter.prevent="openSubject(subject)"
              @keydown.space.prevent="openSubject(subject)"
            >
              <v-list-item-title class="subject-title">
                {{ subject.label }}
              </v-list-item-title>
              <v-list-item-subtitle>
                {{ subject.memberCount }} members · {{ subject.mappingCount }} sync target{{ subject.mappingCount === 1 ? "" : "s" }}
              </v-list-item-subtitle>
              <template #append>
                <v-icon icon="mdi-chevron-right" />
              </template>
            </v-list-item>
          </v-list>
        </manager-card>
      </div>
    </v-container>
  </v-main>
</template>

<style lang="scss" scoped>
.category-page {
  max-width: 980px;
}

.subject-title {
  font-size: 15px;
  font-weight: 600;
}
</style>
