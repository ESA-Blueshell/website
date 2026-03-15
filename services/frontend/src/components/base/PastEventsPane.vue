<script lang="ts" setup>
import {computed, nextTick, onBeforeUnmount, onMounted, ref, watch} from "vue"
import {DateTime} from "luxon"
import {type CommitteeDetailResponse, type EventResponse, type EventSignUpResponse, findEvents, type PageMetadata} from "@/services/api"
import EventList from "@/components/common/lists/EventList.vue"
import {useRoute, useRouter} from "vue-router"

type Event = EventResponse
type EventSignUp = EventSignUpResponse
type CommitteeOption = Pick<CommitteeDetailResponse, "id" | "name">

const props = defineProps<{
  committees: CommitteeOption[]
  eventSignUps: EventSignUp[]
  pageSize?: number
}>()

const route = useRoute()
const router = useRouter()

const pastEvents = ref<Event[]>([])
const pageMeta = ref<PageMetadata>()
const isLoading = ref(false)

const currentPage = ref(1)

const mainDiv = ref<HTMLElement>()
const containerWidth = ref(0)
let ro: ResizeObserver | null = null

function measure() {
  containerWidth.value = mainDiv.value?.getBoundingClientRect().width ?? 0
}

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

  // SETUP: ResizeObserver (no nested onMounted, and measure is defined)
  await nextTick()
  measure()                                              // initialize width once element exists
  if (mainDiv.value) {
    ro = new ResizeObserver(() => {
      // Use rAF to avoid layout thrash if you want; cheap enough as-is too
      measure()
    })
    ro.observe(mainDiv.value)
  }
})

onBeforeUnmount(() => {                                  // NEW: clean up
  ro?.disconnect()
  ro = null
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

const SLOT_PX = 58

const totalSlots = computed(() => {
  const w = containerWidth.value
  return Math.max(0, Math.floor(w / SLOT_PX))
})

const buttonsFitting = computed(() => Math.max(1, totalSlots.value - 2))

const totalVisible = computed(() => {
  const totalPages = pageMeta.value?.totalPages ?? 0
  const current = Math.max(1, currentPage.value ?? 1)
  const cap = buttonsFitting.value

  // If everything fits, show everything.
  if (totalPages <= cap) return totalPages

  const half = Math.floor(cap / 2)

  const needsLeftEllipsis = current > half
  const needsRightEllipsis = current < (totalPages - (half - 1))

  const ellipsisCount = (needsLeftEllipsis ? 1 : 0) + (needsRightEllipsis ? 1 : 0)

  // Show what fits minus how many ellipses we render.
  return Math.max(1, cap - ellipsisCount)
})
</script>


<template>
  <div ref="mainDiv">
    <p class="mx-3 mb-2 text-h3 text-center align-center">
      Past Events
    </p>

    <div
      v-if="(pageMeta?.totalPages ?? 1) > 1"
      class="mb-4 mx-4"
    >
      <v-pagination
        v-model="currentPage"
        :length="pageMeta?.totalPages ?? 1"
        :total-visible="totalVisible"
        class="w-100"
      />
    </div>

    <event-list
      :committees="committees"
      :event-sign-ups="eventSignUps"
      :events="pastEvents"
    />

    <div
      v-if="(pageMeta?.totalPages ?? 1) > 1"
      class="mb-4 mx-4"
    >
      <v-pagination
        v-model="currentPage"
        :length="pageMeta?.totalPages ?? 1"
        :total-visible="totalVisible"
        class="w-100"
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
.v-application .v-pagination ul,
.v-application .v-pagination__list {
  padding-left: 0 !important;
}
</style>
