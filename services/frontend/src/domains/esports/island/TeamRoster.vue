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
import $markdownToHtml from "@/plugins/markdownToHtml.ts"

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
        <!-- Written by an admin, but read on a public page, so it is sanitised. -->
        <span
          v-if="member.description"
          class="slice__entry-note"
          v-html="$markdownToHtml(member.description)"
        />
      </span>
    </span>
  </span>
</template>

<style scoped>
/* The slice band dresses what a page slots into it; this is drawn one level further down, where
   its slotted rules do not reach, so it carries the same rules itself. */
.slice__group {
  display: block;
}

.slice__group-label {
  display: block;
  font-size: 0.6rem;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: var(--color-ash);
}

.slice__entries {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem 1.5rem;
  margin-top: 0.3rem;
}

.slice__entry {
  display: flex;
  min-width: 0;
  flex-direction: column;
  line-height: 1.15;
}

.slice__entry-handle {
  font-size: 0.95rem;
  color: var(--color-chalk);
}

.slice__entry-name {
  font-size: 0.7rem;
  letter-spacing: 0.01em;
  color: color-mix(in oklab, var(--color-ash) 85%, transparent);
}

.slice__entry-role {
  display: block;
  font-size: 0.7rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: color-mix(in oklab, var(--accent) 82%, var(--color-chalk));
}

.slice__entry-note {
  display: block;
  margin-top: 0.15rem;
  max-width: 22rem;
  font-size: 0.72rem;
  line-height: 1.35;
  color: color-mix(in oklab, var(--color-ash) 92%, transparent);
}

.slice__entry-note :deep(p) {
  display: inline;
  margin: 0;
}
</style>
