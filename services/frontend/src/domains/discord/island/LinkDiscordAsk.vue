<script lang="ts" setup>
/* Asks somebody to link their Discord account while roles wait for them that need it. */
import {computed, onMounted, ref} from "vue"
import {listMyUnlinkedRoles} from "../adapters/unlinked"

defineOptions({name: "LinkDiscordAsk"})

const {linked} = defineProps<{linked: boolean}>()

const roles = ref<string[]>([])

const named = computed(() => roles.value.length === 1
  ? roles.value[0]
  : `${roles.value.slice(0, -1).join(", ")} and ${roles.value.at(-1)}`)

onMounted(async () => {
  roles.value = await listMyUnlinkedRoles()
})
</script>

<template>
  <p
    v-if="!linked && roles.length > 0"
    class="link-discord"
    data-testid="link-discord-ask"
    role="status"
  >
    Link your Discord account to get your {{ roles.length === 1 ? "role" : "roles" }} on the server: {{ named }}.
    Pick your account under Discord below.
  </p>
</template>

<style scoped>
.link-discord {
  margin: 0;
  padding: 0.7rem 0.9rem;
  border-left: 3px solid var(--color-brand);
  background: var(--color-surface);
}
</style>
