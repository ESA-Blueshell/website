<script lang="ts" setup>
/* How far everybody and members get into every channel of a game, set once for all of them. A
   change is written to Discord; where Discord has a channel otherwise, the channel says so and
   Discord is left as it is until the board sets it back. Each channel can be archived. */
import {computed, onMounted, ref} from "vue"
import CutButton from "@/components/island/CutButton.vue"
import FormField from "@/components/island/FormField.vue"
import SearchPicker from "@/components/island/SearchPicker.vue"
import store from "@/plugins/store"
import {
  ChannelAccess,
  type ChannelAccessPolicy,
  type GameAccessState,
  archiveGameChannel,
  readGameAccess,
  saveGameAccess,
} from "../adapters/channelAccess"

defineOptions({name: "GameAccessSection"})

const {code, testid} = defineProps<{
  code: string
  testid: string
}>()

const WORDS: Record<ChannelAccess, string> = {
  [ChannelAccess.HIDDEN]: "cannot see it",
  [ChannelAccess.READ]: "read",
  [ChannelAccess.WRITE]: "read and write",
}
const everyoneOptions = [
  {key: ChannelAccess.HIDDEN, label: "Cannot see the channels"},
  {key: ChannelAccess.READ, label: "Can read"},
  {key: ChannelAccess.WRITE, label: "Can read and write"},
]
const memberOptions = everyoneOptions.slice(1)

const state = ref<GameAccessState | null>(null)
const saving = ref(false)
const policy = computed<ChannelAccessPolicy | null>(() => state.value?.policy ?? null)
const said = (one: ChannelAccessPolicy) => `everybody ${WORDS[one.everyone]}, members ${WORDS[one.members]}`
const differing = computed(() => state.value?.channels.filter((one) => one.state.differs) ?? [])

const tell = (message: string) => store.commit("setStatusSnackbarMessage", message)

const save = async (next: ChannelAccessPolicy) => {
  if (saving.value) return
  saving.value = true
  const answered = await saveGameAccess(code, next)
  saving.value = false
  if (!answered.ok) return tell(answered.reason)
  state.value = answered.saved
}

const change = (part: "everyone" | "members", access: string) => {
  if (policy.value) void save({...policy.value, [part]: access as ChannelAccess})
}

const archive = async (id: string, name: string) => {
  if (saving.value) return
  saving.value = true
  const answered = await archiveGameChannel(id)
  saving.value = false
  if (!answered.ok) return tell(answered.reason)
  tell(`#${name} is in the archive.`)
  state.value = await readGameAccess(code)
}

onMounted(async () => {
  state.value = await readGameAccess(code)
})
</script>

<template>
  <div
    v-if="state && policy"
    class="game-access"
    :data-testid="testid"
  >
    <div class="game-access__choices">
      <form-field
        label="Everybody in the server"
        :testid="`${testid}-everyone-field`"
      >
        <template #default="{controlId, labelId}">
          <search-picker
            :control-id="controlId"
            :labelled-by="labelId"
            :options="everyoneOptions"
            :selected-key="policy.everyone"
            :testid-prefix="`${testid}-everyone`"
            @pick="(key: string) => change('everyone', key)"
          />
        </template>
      </form-field>
      <form-field
        label="Members"
        :testid="`${testid}-members-field`"
      >
        <template #default="{controlId, labelId}">
          <search-picker
            :control-id="controlId"
            :labelled-by="labelId"
            :options="memberOptions"
            :selected-key="policy.members"
            :testid-prefix="`${testid}-members`"
            @pick="(key: string) => change('members', key)"
          />
        </template>
      </form-field>
    </div>
    <p
      v-if="state.channels.length === 0"
      class="game-access__label"
    >
      The game has no channel yet; the access applies to every channel it gets.
    </p>
    <ul class="game-access__channels">
      <li
        v-for="channel in state.channels"
        :key="channel.id"
        class="game-access__channel"
        :data-testid="`${testid}-channel-${channel.id}`"
      >
        <span class="game-access__name">#{{ channel.name }}</span>
        <span
          v-if="channel.state.differs"
          class="game-access__label"
          :data-testid="`${testid}-differs-${channel.id}`"
        >On Discord {{ said(channel.state.actual) }}. Discord is left as it is.</span>
        <cut-button
          :disabled="saving"
          small
          :testid="`${testid}-archive-${channel.id}`"
          tone="quiet"
          @click="archive(channel.id, channel.name)"
        >
          Archive
        </cut-button>
      </li>
    </ul>
    <p
      v-if="differing.length"
      class="game-access__label"
    >
      <cut-button
        :disabled="saving"
        small
        :testid="`${testid}-rewrite`"
        tone="quiet"
        @click="save(policy)"
      >
        Set {{ differing.length === 1 ? "it" : "them" }} back on Discord
      </cut-button>
    </p>
  </div>
</template>

<style scoped>
.game-access {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
}

.game-access__choices {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.4rem 1.2rem;
}

@media (--phone) {
  .game-access__choices {
    grid-template-columns: minmax(0, 1fr);
  }
}

.game-access__label {
  margin: 0;
  font-size: 0.84rem;
  color: var(--color-ash);
}

.game-access__channels {
  display: flex;
  flex-direction: column;
  margin: 0;
  padding: 0;
  list-style: none;
}

.game-access__channel {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.6rem;
  padding: 0.5rem 0;
  border-bottom: 1px solid var(--color-hairline);
}

.game-access__name {
  font-weight: 600;
}
</style>
