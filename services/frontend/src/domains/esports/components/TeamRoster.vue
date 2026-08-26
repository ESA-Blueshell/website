<script lang="ts" setup>
import {computed, onBeforeUnmount, onMounted, ref} from "vue"
import {useTheme} from "vuetify"
import {$require} from "@/plugins/require"
import type {TeamRoster} from "../adapters/esports"

defineOptions({name: "TeamRoster"})

const props = withDefaults(defineProps<{
  team: TeamRoster
  /** Alternated down the page, so a column of teams does not read as one block. */
  nameRight?: boolean
}>(), {nameRight: false})

const theme = useTheme()

const background = computed<string>(() =>
  props.team.image ? $require(`@/assets/${props.team.image}`) : "",
)

const scrim = computed<string>(() =>
  theme.global.current.value.dark ? "rgba(0, 0, 0, 0.62)" : "rgba(255, 255, 255, 0.82)",
)

/**
 * The roster grouped the way the pages have always read: players, then substitutes, then
 * coaches, each named once above its rows.
 */
const GROUPS = [
  {role: "PLAYER", one: "Player", many: "Players"},
  {role: "SUBSTITUTE", one: "Substitute", many: "Substitutes"},
  {role: "COACH", one: "Coach", many: "Coaches"},
] as const

const groups = computed(() =>
  GROUPS.map((group) => ({
    ...group,
    members: props.team.members.filter((member) => member.role === group.role),
  })).filter((group) => group.members.length > 0),
)

// Entry animation is driven by the element coming into view rather than by page load, so a
// team further down the page still arrives rather than having already happened.
const root = ref<HTMLElement | null>(null)
const shown = ref<boolean>(false)
let observer: IntersectionObserver | null = null

onMounted(() => {
  if (typeof IntersectionObserver === "undefined") {
    shown.value = true
    return
  }
  observer = new IntersectionObserver(
    (entries) => {
      if (entries.some((entry) => entry.isIntersecting)) {
        shown.value = true
        observer?.disconnect()
      }
    },
    {rootMargin: "-10% 0px"},
  )
  if (root.value) observer.observe(root.value)
})

onBeforeUnmount(() => observer?.disconnect())
</script>

<template>
  <section
    ref="root"
    class="team-roster"
    :class="{'team-roster--shown': shown, 'team-roster--mirrored': nameRight}"
    :data-testid="`team-roster-${team.id}`"
    :style="{backgroundImage: background ? `url(${background})` : undefined}"
  >
    <div
      class="team-roster__scrim"
      :style="{background: scrim}"
    >
      <v-container class="team-roster__inner">
        <div class="team-roster__name">
          <h2 data-testid="team-roster-name">
            {{ team.name }}
          </h2>
        </div>

        <div class="team-roster__members">
          <div
            v-for="group in groups"
            :key="group.role"
            class="team-roster__group"
          >
            <p class="team-roster__group-label">
              {{ group.members.length > 1 ? group.many : group.one }}
            </p>
            <p
              v-for="(member, index) in group.members"
              :key="`${group.role}-${member.handle}`"
              class="team-roster__member"
              :style="{'--stagger': `${index * 60}ms`}"
            >
              {{ member.handle }}
            </p>
          </div>
        </div>
      </v-container>
    </div>
  </section>
</template>

<style lang="scss" scoped>
.team-roster {
  background-position: center;
  background-size: cover;
}

.team-roster__scrim {
  // The scrim lifts as the block arrives, so the photograph reads first and the roster
  // settles on top of it.
  backdrop-filter: blur(1px);
  transition: background 400ms ease;
}

.team-roster__inner {
  display: flex;
  align-items: center;
  gap: 32px;
  padding-block: 64px;
}

.team-roster--mirrored .team-roster__inner {
  flex-direction: row-reverse;
}

.team-roster__name {
  flex: 0 0 34%;
  min-width: 0;

  h2 {
    margin: 0;
    font-size: clamp(2rem, 5vw, 3.75rem);
    font-style: italic;
    font-weight: 700;
    line-height: 1.05;
    overflow-wrap: anywhere;
  }
}

.team-roster--mirrored .team-roster__name h2 {
  text-align: right;
}

.team-roster__members {
  flex: 1 1 auto;
  min-width: 0;
}

.team-roster__group + .team-roster__group {
  margin-top: 18px;
  padding-top: 14px;
  border-top: thin solid rgba(var(--v-border-color), var(--v-border-opacity));
}

.team-roster__group-label {
  margin: 0 0 4px;
  font-size: 0.8125rem;
  font-style: italic;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  opacity: 0.7;
}

.team-roster__member {
  margin: 0;
  padding: 4px 0;
  font-size: 1.25rem;
  font-weight: 500;
  transition: transform 200ms ease, opacity 200ms ease;

  &:hover {
    transform: translateX(6px);
    opacity: 1;
  }
}

// ── Arrival ───────────────────────────────────────────────────────────────────
//
// The name slides in from its own side and the roster follows a beat later, one row at a
// time. Everything starts in its final position for a reader who has motion turned off, so
// nothing depends on the animation having run.
.team-roster__name,
.team-roster__member {
  opacity: 0;
  transform: translateY(14px);
  transition:
    opacity 500ms ease var(--stagger, 0ms),
    transform 500ms cubic-bezier(0.22, 1, 0.36, 1) var(--stagger, 0ms);
}

.team-roster__name {
  transform: translateX(-24px);
}

.team-roster--mirrored .team-roster__name {
  transform: translateX(24px);
}

.team-roster--shown .team-roster__name,
.team-roster--shown .team-roster__member {
  opacity: 1;
  transform: none;
}

@media (max-width: 960px) {
  .team-roster__inner,
  .team-roster--mirrored .team-roster__inner {
    flex-direction: column;
    text-align: center;
    padding-block: 48px;
  }

  .team-roster__name {
    flex: 0 0 auto;
  }

  .team-roster--mirrored .team-roster__name h2 {
    text-align: center;
  }

  .team-roster__members {
    width: 100%;
  }
}

@media (prefers-reduced-motion: reduce) {
  .team-roster__name,
  .team-roster__member {
    opacity: 1;
    transform: none;
    transition: none;
  }

  .team-roster__member:hover {
    transform: none;
  }
}
</style>
