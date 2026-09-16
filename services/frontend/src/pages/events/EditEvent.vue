<script lang="ts" setup>
import {computed, onMounted, ref} from "vue"
import {useRoute, useRouter} from "vue-router"
import TopBanner from "@/components/common/banners/TopBanner.vue"
import EventForm from "@/components/form/EventForm.vue"
import {type EventResponse, findEventById} from "@/services/api"

const EVENT_LIST = "/events"

const route = useRoute()
const router = useRouter()
const event = ref<EventResponse>()

const headerTitle = ref("")
const isEditing = computed(() => Boolean(route.params.id))

onMounted(async () => {
  const id = Number(route.params.id)
  if (id) {
    headerTitle.value = "Edit Event"
    try {
      const resp = await findEventById({path: {id}})
      event.value = resp.data
    } catch (err) {
      console.error("Error fetching event:", err)
    }
  } else {
    headerTitle.value = "Create Event"
  }
})

/**
 * The page the reader came from, read once on arrival rather than gone back to blindly.
 *
 * `history.state.back` is the entry behind this one, which is the page holding the card they
 * opened, filters and season in its query. Two answers are refused: the login page, which a
 * reader bounced through on the way here has behind them and is the one place saving must not
 * land, and any address outside the spa. Neither leaves anywhere to return to, so both fall back
 * to the list the event is on.
 */
const returnTo = ((): string => {
  const back = router.options.history.state.back
  if (typeof back !== "string" || !back.startsWith("/") || back.startsWith("//")) return EVENT_LIST
  if (back.startsWith("/login")) return EVENT_LIST
  return back
})()

function onSuccess() {
  router.replace(returnTo)
}
</script>

<template>
  <v-main>
    <top-banner :title="headerTitle" />
    <div class="mb-8">
      <div
        class="mx-auto mt-10"
        style="max-width: 800px"
      >
        <event-form
          v-if="!isEditing"
          ref="form"
          @submitted="(ok: boolean) => { if (ok) onSuccess() }"
        />
        <event-form
          v-else-if="event"
          ref="form"
          v-model="event"
          @submitted="(ok: boolean) => { if (ok) onSuccess() }"
        />
      </div>
    </div>
  </v-main>
</template>

<style lang="scss" scoped>
</style>
