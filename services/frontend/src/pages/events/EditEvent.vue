<script lang="ts" setup>
import {computed, onMounted, ref} from "vue"
import {useRoute, useRouter} from "vue-router"
import TopBanner from "@/components/common/banners/TopBanner.vue"
import EventForm from "@/components/form/EventForm.vue"
import {type EventResponse, findEventById} from "@/services/api"

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
 * The event list, named rather than gone back to. One entry back is wherever the reader came from,
 * and a reader who was bounced through `/login` on the way here came from the login page.
 */
function onSuccess() {
  router.replace("/events")
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
