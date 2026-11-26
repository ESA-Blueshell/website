<script lang="ts" setup>
/* Committees and teams with no role yet, each beside the role and channel the server already has by
   its name. The board ticks the right ones and links them; each is linked as its form would. */
import {computed, onMounted, ref} from "vue"
import CheckBox from "@/components/island/CheckBox.vue"
import CutButton from "@/components/island/CutButton.vue"
import RoleMark from "@/components/island/RoleMark.vue"
import ListHead from "@/components/management/ListHead.vue"
import {type AdoptionMatch, adoptMatches, listMatches, refusedReason} from "../adapters/adoption"
import ChannelMark from "./ChannelMark.vue"

defineOptions({name: "RoleMatches"})

const emit = defineEmits<{(event: "linked"): void}>()

const matches = ref<AdoptionMatch[]>([])
const picked = ref<Set<string>>(new Set())
const working = ref(false)
const said = ref<string | null>(null)

const pickedCount = computed(() => matches.value.filter((one) => picked.value.has(one.key)).length)
const toggle = (key: string, on: boolean) => {
  const next = new Set(picked.value)
  if (on) next.add(key)
  else next.delete(key)
  picked.value = next
}

const load = async () => {
  matches.value = (await listMatches()) ?? []
  picked.value = new Set()
}

const link = async () => {
  if (working.value || pickedCount.value === 0) return
  working.value = true
  const answer = await adoptMatches(matches.value.filter((one) => picked.value.has(one.key)))
  working.value = false
  if (!answer.ok) {
    said.value = answer.reason
    return
  }
  const refused = answer.saved.refused.map((one) => `${one.label}: ${refusedReason(one)}`)
  said.value = [`${answer.saved.linked} linked.`, ...refused].join(" ")
  await load()
  emit("linked")
}

onMounted(load)
</script>

<template>
  <section
    v-if="matches.length > 0 || said"
    class="matches"
    data-testid="discord-matches"
  >
    <list-head title="Roles named as a committee or a team" />
    <p class="matches__note">
      These committees and teams have no role yet, and the server has a role by their name. Tick the ones to link.
    </p>
    <p
      v-if="said"
      class="matches__said"
      data-testid="discord-matches-said"
      role="status"
    >
      {{ said }}
    </p>
    <ul class="matches__list">
      <li
        v-for="match in matches"
        :key="match.key"
        class="matches__row"
        :data-testid="`discord-match-${match.key}`"
      >
        <check-box
          :label="match.label"
          :model-value="picked.has(match.key)"
          :testid="`discord-match-pick-${match.key}`"
          @update:model-value="(on: boolean) => toggle(match.key, on)"
        />
        <role-mark :role="match.roleName" />
        <channel-mark
          v-for="channel in match.channels"
          :id="channel.id"
          :key="channel.id"
          :name="channel.name"
        />
      </li>
    </ul>
    <cut-button
      v-if="matches.length > 0"
      :disabled="working || pickedCount === 0"
      testid="discord-matches-link"
      tone="solid"
      @click="link"
    >
      {{ working ? "Linking" : `Link ${pickedCount} ${pickedCount === 1 ? "role" : "roles"}` }}
    </cut-button>
  </section>
</template>

<style scoped>
.matches {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  margin-bottom: 1.6rem;
}

.matches__note,
.matches__said {
  color: var(--color-ash);
}

.matches__list {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  padding: 0;
  list-style: none;
}

.matches__row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.8rem;
}
</style>
