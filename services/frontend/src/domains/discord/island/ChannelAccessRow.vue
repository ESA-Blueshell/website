<script lang="ts" setup>
/* How far everybody and members get into one of a game's channels. Changing it writes it to Discord;
   where Discord has been changed by hand, the row says so and leaves Discord as it is. */
import {computed, onMounted, ref} from "vue"
import CutButton from "@/components/island/CutButton.vue"
import SegmentedChoice from "@/components/island/SegmentedChoice.vue"
import store from "@/plugins/store"
import {ChannelAccess, type ChannelAccessPolicy, type ChannelAccessState, readChannelAccess, saveChannelAccess} from "../adapters/channelAccess"

defineOptions({name: "ChannelAccessRow"})

const {channelId, name, testid} = defineProps<{
  channelId: string
  name: string
  testid: string
}>()

const WORDS: Record<ChannelAccess, string> = {
  [ChannelAccess.HIDDEN]: "cannot see it",
  [ChannelAccess.READ]: "read",
  [ChannelAccess.WRITE]: "read and write",
}
const everyoneOptions = [
  {key: ChannelAccess.HIDDEN, label: "Hidden"},
  {key: ChannelAccess.READ, label: "Read"},
  {key: ChannelAccess.WRITE, label: "Write"},
]
const memberOptions = everyoneOptions.slice(1)

const state = ref<ChannelAccessState | null>(null)
const saving = ref(false)
const shown = computed<ChannelAccessPolicy | null>(() => state.value?.kept ?? state.value?.actual ?? null)
const said = (policy: ChannelAccessPolicy) => `everybody ${WORDS[policy.everyone]}, members ${WORDS[policy.members]}`

const save = async (policy: ChannelAccessPolicy) => {
  if (saving.value) return
  saving.value = true
  const answered = await saveChannelAccess(channelId, policy)
  saving.value = false
  if (!answered.ok) return store.commit("setStatusSnackbarMessage", answered.reason)
  state.value = answered.saved
}

const change = (part: "everyone" | "members", access: string) => {
  if (shown.value) void save({...shown.value, [part]: access as ChannelAccess})
}

onMounted(async () => {
  state.value = await readChannelAccess(channelId)
})
</script>

<template>
  <div
    v-if="shown"
    class="channel-access"
    :data-testid="testid"
  >
    <p class="channel-access__name">
      #{{ name }}
    </p>
    <div class="channel-access__choices">
      <span class="channel-access__label">Everybody</span>
      <segmented-choice
        :model-value="shown.everyone"
        :options="everyoneOptions"
        :testid-prefix="`${testid}-everyone`"
        @update:model-value="(key: string) => change('everyone', key)"
      />
      <span class="channel-access__label">Members</span>
      <segmented-choice
        :model-value="shown.members"
        :options="memberOptions"
        :testid-prefix="`${testid}-members`"
        @update:model-value="(key: string) => change('members', key)"
      />
    </div>
    <p
      v-if="state?.differs"
      class="channel-access__differs"
      :data-testid="`${testid}-differs`"
    >
      On Discord {{ said(state.actual) }}, where the site keeps {{ said(state.kept!) }}. Discord is left as it is.
      <cut-button
        tone="quiet"
        :testid="`${testid}-rewrite`"
        @click="save(state.kept!)"
      >
        Set it back on Discord
      </cut-button>
    </p>
  </div>
</template>

<style scoped>
.channel-access {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  padding: 0.6rem 0;
  border-bottom: 1px solid var(--color-hairline);
}

.channel-access__name {
  margin: 0;
  font-weight: 600;
}

.channel-access__choices {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 0.4rem 0.8rem;
  align-items: center;
}

.channel-access__label,
.channel-access__differs {
  font-size: 0.84rem;
  color: var(--color-ash);
}

.channel-access__differs {
  margin: 0;
}
</style>
