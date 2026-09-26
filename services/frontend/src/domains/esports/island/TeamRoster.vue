<script lang="ts">
/** One part of a line-up, and who played it. */
export interface RosterGroup {
  role: string
  one: string
  many: string
  members: {handle: string; roleTitle?: string | null; name?: string | null; description?: string | null}[]
}
</script>

<script lang="ts" setup>
import MarkdownView from "@/components/island/MarkdownView.vue"

/**
 * A team's line-up as its open slice reveals it: players, then substitutes, then coaches, each
 * member by handle with the part they played, their name where they said it may be shown, and a
 * word about them. Drawn by the game page and by the team's edit page preview alike.
 */
defineOptions({name: "TeamRoster"})

defineProps<{groups: RosterGroup[]}>()
</script>

<template>
  <span
    v-for="group in groups"
    :key="group.role"
    class="slice__group"
  >
    <span class="slice__group-label">
      {{ group.members.length === 1 ? group.one : group.many }}
    </span>
    <span class="slice__entries">
      <span
        v-for="(member, at) in group.members"
        :key="`${member.handle}-${at}`"
        class="slice__entry"
      >
        <span class="slice__entry-handle">{{ member.handle }}</span>
        <!-- What they did in the team's own words, beside the part they played. -->
        <span
          v-if="member.roleTitle"
          class="slice__entry-role"
        >{{ member.roleTitle }}</span>
        <!-- Only ever present for a member who said their name may be shown. -->
        <span
          v-if="member.name"
          class="slice__entry-name"
        >{{ member.name }}</span>
        <markdown-view
          v-if="member.description"
          class="slice__entry-note"
          :source="member.description"
        />
      </span>
    </span>
  </span>
</template>

<style scoped src="./sliceEntries.css"></style>
