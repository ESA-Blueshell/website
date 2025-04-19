<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import TopBanner from '@/components/banners/TopBanner.vue';
import EventForm from '@/components/events/EventForm.vue';
import { EventService } from '@/services';
import type { EventModel } from '@/models';
import { useRouter} from "vue-router";

const eventService = new EventService();
const route = useRoute();
const router = useRouter();
// This holds the event data once fetched (or newly created)
const eventData = ref<EventModel | null>(null);

// Reactive title for top banner
const headerTitle = ref('');

// Determine if we are editing (route has an `id`) or creating a new event
const isEditing = computed(() => Boolean(route.params.id));

onMounted(async () => {
  if (isEditing.value) {
    // We are editing
    headerTitle.value = 'Edit EventModel';
    try {
      const id = Number(route.params.id);
      eventData.value = await eventService.getEvent(id);
    } catch (err) {
      console.error('Error fetching event:', err);
      // Optionally handle or redirect on error
    }
  } else {
    // We are creating
    headerTitle.value = 'Create EventModel';
    // Initialize a blank event object
    eventData.value = {
      id: undefined,
      title: '',
      description: '',
      location: '',
      startTime: '',
      endTime: '',
      visible: false,
      membersOnly: false,
      signUp: false,
      banner: undefined,
      signUpForm: [],
    } as EventModel;
  }
});

function onSuccess() {
  router.back();
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
          v-if="eventData"
          ref="form"
          :initial-event="eventData"
          @success="onSuccess"
          @title="(newTitle) => headerTitle = isEditing ? `Edit ${newTitle}` : `Create ${newTitle}`"
        />
      </div>
    </div>
  </v-main>
</template>

<style scoped>
</style>
