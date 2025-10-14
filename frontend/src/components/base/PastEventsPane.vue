<script lang="ts" setup>
import {onMounted, ref, watch} from "vue"
import {DateTime} from "luxon"
import {type AdvancedCommittee, type Event, type EventSignUp, findEvents, type PageMetadata} from "@/services/api"
import EventList from "@/components/common/lists/EventList.vue"
import {useRoute, useRouter} from "vue-router"

const props = defineProps<{
  committees: AdvancedCommittee[]
  eventSignUps: EventSignUp[]
  pageSize?: number
}>()

const route = useRoute()
const router = useRouter()

const pastEvents = ref<Event[]>([])
const pageMeta = ref<PageMetadata>()
const isLoading = ref(false)

const currentPage = ref(1)

function coercePage(raw: unknown): number {
  const n = Number(raw ?? 1)
  return Number.isFinite(n) && n > 0 ? Math.floor(n) : 1
}

function replaceUrlQuerySilently(next: number) {
  const q = {...route.query, page: String(next)}
  const href = router.resolve({path: route.path, query: q}).href
  globalThis.history.replaceState(globalThis.history.state, "", href)
}

async function loadPast(pageOneIndexed = 1) {
  isLoading.value = true
  try {
    const pageZeroIndexed = Math.max(0, pageOneIndexed - 1)
    const resp = await findEvents({
      query: {
        to: DateTime.local().startOf("day").toISO(),
        page: pageZeroIndexed,
        size: props.pageSize ?? 10,
        sort: ["startTime,desc"],
      },
    })
    pastEvents.value = resp.data?.content ?? []
    pageMeta.value = resp.data!.page!
  } finally {
    isLoading.value = false
  }
}

onMounted(async () => {
  currentPage.value = coercePage(route.query.page)
  if (route.query.page == null) replaceUrlQuerySilently(currentPage.value)
  await loadPast(currentPage.value)
})

watch(currentPage, (p, old) => {
  if (p === old) return
  replaceUrlQuerySilently(p)
  void loadPast(p)
})

watch(
  () => route.query.page,
  (qp) => {
    const next = coercePage(qp)
    if (next !== currentPage.value) {
      currentPage.value = next
      void loadPast(next)
    }
  },
)
</script>

<template>
  <div>
    <p class="mx-3 mb-2 text-h3 text-center">
      Past Events
    </p>
    <v-divider class="my-3" />

    <div
      v-if="(pageMeta?.totalPages ?? 1) > 1"
      class="pagination-wrap mb-4 mx-4"
    >
      <v-pagination
        v-model="currentPage"
        :length="pageMeta?.totalPages ?? 1"
        :total-visible="(currentPage > 5 && currentPage < (pageMeta?.totalPages - 4)) ? 9 : 10 "
      />
    </div>

    <event-list
      :committees="committees"
      :event-sign-ups="eventSignUps"
      :events="pastEvents"
    />

    <div
      v-if="(pageMeta?.totalPages ?? 1) > 1"
      class="pagination-wrap mb-4 mx-4"
    >
      <v-pagination
        v-model="currentPage"
        :length="pageMeta?.totalPages ?? 1"
        :total-visible="(currentPage > 5 && currentPage < (pageMeta?.totalPages - 4)) ? 9 : 10 "
      />
    </div>

    <div
      v-if="isLoading"
      class="text-center my-4"
    >
      <v-progress-circular indeterminate />
    </div>
  </div>
</template>

<style lang="scss">
.pagination-wrap {
  display: flex;
  justify-content: center;
}
</style>
