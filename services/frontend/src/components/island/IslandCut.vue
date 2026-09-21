<script lang="ts">
/** How much a way on insists: solid for the one to take, quiet for the aside, plain for the rest. */
export type CutTone = "solid" | "plain" | "quiet"
</script>

<script lang="ts" setup>
import {computed} from "vue"
import {useMotionAllowed} from "@/components/island/useMotionAllowed"

defineOptions({name: "IslandCut"})

const {href = "", tone = "plain", away = false, testid = undefined} = defineProps<{
  /** Where it leads. A path is followed by the router; anything else is a plain link. Omit for
   * something the page handles itself, which is drawn as a button. */
  href?: string
  tone?: CutTone
  /** Opens a new tab, which is only ever right for somewhere that is not the site. */
  away?: boolean
  testid?: string
}>()

/** The sweep explains the press rather than decorating it, so it shortens instead of stopping. */
const motion = useMotionAllowed()
const sweep = computed<string>(() => `${motion.duration(0.32)}s`)

const inside = computed<boolean>(() => href.startsWith("/"))
/*
 * Three elements written out rather than one chosen by `is`: a dynamic component took the
 * router link as a value, and what it rendered was an anchor with no address in it. What a
 * press does is worth three branches.
 */
const routed = computed<boolean>(() => href !== "" && inside.value)

const tones = computed(() => ["island-cut", `island-cut--${tone}`])
</script>

<template>
  <router-link
    v-if="routed"
    :class="tones"
    :style="{'--sweep': sweep}"
    :data-testid="testid"
    :to="href"
  >
    <span><slot /></span>
  </router-link>

  <a
    v-else-if="href !== ''"
    :class="tones"
    :style="{'--sweep': sweep}"
    :data-testid="testid"
    :href="href"
    :rel="away ? 'noopener' : undefined"
    :target="away ? '_blank' : undefined"
  >
    <span><slot /></span>
  </a>

  <button
    v-else
    :class="tones"
    :style="{'--sweep': sweep}"
    :data-testid="testid"
    type="button"
  >
    <span><slot /></span>
  </button>
</template>

<style scoped>
/* The house cut, with the accent sweeping in from the left rather than a colour swapping. */
.island-cut {
  position: relative;
  display: inline-flex;
  align-items: center;
  overflow: hidden;
  padding: 0.62rem 1.35rem;
  border: 0;
  clip-path: polygon(0.7rem 0, 100% 0, calc(100% - 0.7rem) 100%, 0 100%);
  background-color: color-mix(in oklab, var(--color-chalk) 8%, transparent);
  font-family: var(--font-display);
  font-size: 0.72rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--color-chalk);
  white-space: nowrap;
  cursor: pointer;
}

.island-cut::before {
  content: "";
  position: absolute;
  inset: 0;
  background-color: var(--color-brand);
  transform-origin: left center;
  scale: 0 1;
  transition: scale var(--sweep, 0.32s) var(--ease-out-quint);
}

.island-cut > span {
  position: relative;
}

.island-cut:hover::before,
.island-cut:focus-visible::before {
  scale: 1 1;
}

.island-cut--solid {
  background-color: var(--color-brand);
  color: var(--color-void);
}

.island-cut--solid::before {
  background-color: var(--color-acid);
}

/* Tinted, not outlined: the clip-path cuts an inset border into a line through the label. */
.island-cut--quiet {
  background-color: color-mix(in oklab, var(--color-chalk) 4%, transparent);
  color: var(--color-ash);
}

.island-cut--quiet:hover,
.island-cut--quiet:focus-visible {
  color: var(--color-chalk);
}

@media (max-width: 767px) {
  .island-cut {
    padding: 0.55rem 1rem;
    font-size: 0.68rem;
  }
}
</style>
