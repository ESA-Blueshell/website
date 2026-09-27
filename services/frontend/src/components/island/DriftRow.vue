<script lang="ts">
/** One tile in the row. */
export interface DriftItem {
  id: string | number
  title: string
  href: string
  accent: string
  /** A line under the name, such as a game's channels. */
  sub?: string
  banner?: string | null
  srcset?: string
  /** The letters a plate shows where there is no banner. */
  initials: string
}
</script>

<script lang="ts" setup>
import {computed} from "vue"

defineOptions({name: "DriftRow"})

const {items, testidPrefix} = defineProps<{
  items: DriftItem[]
  testidPrefix: string
}>()

const emit = defineEmits<{go: [item: DriftItem]}>()

/** Enough copies that one full pass of the row is always wider than the band. */
const PASS_TILES = 8

/*
 * The row is two identical passes side by side and slides left by one pass, so the end of the
 * animation looks exactly like its start and the loop has no seam. A short list is repeated
 * until one pass is wide enough to fill the band on its own.
 */
const pass = computed<DriftItem[]>(() => {
  if (items.length === 0) return []
  const repeats = Math.ceil(PASS_TILES / items.length)
  return Array.from({length: repeats}, () => items).flat()
})

/** Only the first pass is announced; the second is the same tiles again, for the loop. */
const tiles = computed(() => [
  ...pass.value.map((item, at) => ({item, key: `a${at}`, echo: at >= items.length})),
  ...pass.value.map((item, at) => ({item, key: `b${at}`, echo: true})),
])

function follow(event: MouseEvent, item: DriftItem) {
  if (event.metaKey || event.ctrlKey || event.shiftKey || event.button !== 0) return
  event.preventDefault()
  emit("go", item)
}
</script>

<template>
  <div
    v-if="items.length > 0"
    class="drift-row"
    :data-testid="`${testidPrefix}-drift`"
  >
    <span
      aria-hidden="true"
      class="drift-row__fade drift-row__fade--back"
    />
    <span
      aria-hidden="true"
      class="drift-row__fade drift-row__fade--on"
    />
    <div class="drift-row__run">
      <a
        v-for="tile in tiles"
        :key="tile.key"
        :aria-hidden="tile.echo ? 'true' : undefined"
        class="drift-row__tile"
        :data-testid="tile.echo ? undefined : `${testidPrefix}-tile-${tile.item.id}`"
        :href="tile.item.href"
        :style="{'--accent': tile.item.accent}"
        :tabindex="tile.echo ? -1 : undefined"
        @click="follow($event, tile.item)"
      >
        <img
          v-if="tile.item.banner"
          alt=""
          class="drift-row__art"
          loading="lazy"
          sizes="336px"
          :src="tile.item.banner"
          :srcset="tile.item.srcset"
        >
        <span
          v-else
          aria-hidden="true"
          class="drift-row__plate"
        ><span>{{ tile.item.initials }}</span></span>
        <span
          aria-hidden="true"
          class="drift-row__shade"
        />
        <span class="drift-row__caption">
          <span class="drift-row__name">{{ tile.item.title }}</span>
          <span
            v-if="tile.item.sub"
            class="drift-row__sub"
          >{{ tile.item.sub }}</span>
        </span>
      </a>
    </div>
  </div>
</template>

<style scoped>
.drift-row {
  --cut: 14px;
  --tile-width: 21rem;
  --tile-gap: 14px;

  position: relative;
  overflow: hidden;
  padding: 0.25rem 0;
}

.drift-row__run {
  display: flex;
  gap: var(--tile-gap);
  width: max-content;
  animation: drift-row 60s linear infinite;
}

/* The pointer stops the row, so a tile can be read and pressed. So does focus. */
.drift-row:hover .drift-row__run,
.drift-row:focus-within .drift-row__run {
  animation-play-state: paused;
}

@keyframes drift-row {
  to {
    translate: calc(-50% - var(--tile-gap) / 2) 0;
  }
}

.drift-row__tile {
  position: relative;
  flex: none;
  width: var(--tile-width);
  height: 12rem;
  overflow: hidden;
  background-color: var(--color-surface);
  clip-path: polygon(var(--cut) 0, 100% 0, calc(100% - var(--cut)) 100%, 0 100%);
}

.drift-row__tile:focus-visible {
  outline: 2px solid var(--color-brand);
  outline-offset: -4px;
}

.drift-row__art {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  filter: saturate(0.5);
  transition: filter 400ms ease, scale 700ms cubic-bezier(0.22, 1, 0.36, 1);
}

.drift-row__tile:hover .drift-row__art,
.drift-row__tile:focus-visible .drift-row__art {
  filter: none;
  scale: 1.05;
}

.drift-row__plate {
  position: absolute;
  inset: 0;
  overflow: hidden;
  background:
    radial-gradient(110% 90% at 0 0, color-mix(in oklab, var(--accent) 30%, transparent), transparent 70%),
    repeating-linear-gradient(-62deg, transparent 0 22px, color-mix(in oklab, var(--accent) 7%, transparent) 22px 24px),
    var(--color-pit);
}

.drift-row__plate > span {
  position: absolute;
  top: -1rem;
  right: 0;
  font-family: var(--font-display);
  font-size: 7rem;
  line-height: 1;
  color: color-mix(in oklab, var(--accent) 22%, transparent);
}

.drift-row__shade {
  position: absolute;
  inset: 0;
  background: linear-gradient(to top, color-mix(in oklab, var(--color-ground) 92%, transparent) 6%, transparent 60%);
}

.drift-row__caption {
  position: absolute;
  right: 1.4rem;
  bottom: 0.9rem;
  left: 1.4rem;
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.drift-row__name {
  font-family: var(--font-display);
  font-size: 1.15rem;
  text-transform: uppercase;
  color: var(--color-chalk);
}

.drift-row__sub {
  font-size: 0.82rem;
  color: var(--color-ash);
}

.drift-row__fade {
  position: absolute;
  top: 0;
  bottom: 0;
  z-index: 2;
  width: 10rem;
  pointer-events: none;
}

.drift-row__fade--back {
  left: 0;
  background: linear-gradient(to right, var(--color-ground), transparent);
}

.drift-row__fade--on {
  right: 0;
  background: linear-gradient(to left, var(--color-ground), transparent);
}

@media (width < 768px) {
  .drift-row {
    --tile-width: 15rem;
  }

  .drift-row__tile {
    height: 8.6rem;
  }

  .drift-row__fade {
    width: 3rem;
  }
}

@media (prefers-reduced-motion: reduce) {
  .drift-row__run {
    animation: none;
  }
}
</style>
