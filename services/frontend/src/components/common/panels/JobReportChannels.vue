<script lang="ts" setup>
import {onMounted, ref} from "vue"
import {nameMentions} from "@/domains/discord"

/** The Discord channels a job run names, read as the server names them. */
const {channels} = defineProps<{channels: string[]}>()

const named = ref<string[]>(channels.map(() => "#…"))

onMounted(async () => {
  const nameOf = await nameMentions({users: [], roles: [], channels})
  named.value = channels.map(id => nameOf("channel", id).said)
})
</script>

<template>
  <span data-testid="job-report-channels">{{ named.join(", ") }}</span>
</template>
