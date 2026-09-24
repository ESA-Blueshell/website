<script lang="ts">
/** One cell: a banner or a plate, and a caption under it. */
export interface ArtCell {
  id: string | number
  title: string
  href: string
  accent: string
  banner?: string | null
  srcset?: string
  icon?: string | null
  /** The letters a plate shows where there is no banner. */
  initials: string
  /** A short line under the name, such as a game's channels. */
  sub?: string
  /** Names drawn as small tags under the caption. */
  chips?: string[]
  /** Kept for the record but no longer running: tagged, and toned down until pointed at. */
  archived?: boolean
}
</script>

<script lang="ts" setup>
defineOptions({name: "ArtCells"})

const {cells, testidPrefix} = defineProps<{
  cells: ArtCell[]
  testidPrefix: string
}>()

const emit = defineEmits<{go: [cell: ArtCell]}>()

function follow(event: MouseEvent, cell: ArtCell) {
  if (event.metaKey || event.ctrlKey || event.shiftKey || event.button !== 0) return
  event.preventDefault()
  emit("go", cell)
}
</script>

<template>
  <ul
    class="art-cells"
    :data-testid="`${testidPrefix}-cells`"
  >
    <li
      v-for="cell in cells"
      :key="cell.id"
    >
      <a
        class="art-cells__cell"
        :class="{'art-cells__cell--old': cell.archived}"
        :data-testid="`${testidPrefix}-cell-${cell.id}`"
        :href="cell.href"
        :style="{'--accent': cell.accent}"
        @click="follow($event, cell)"
      >
        <span class="art-cells__art">
          <img
            v-if="cell.banner"
            alt=""
            class="art-cells__banner"
            loading="lazy"
            sizes="(min-width: 768px) 280px, 50vw"
            :src="cell.banner"
            :srcset="cell.srcset"
          >
          <span
            v-else
            aria-hidden="true"
            class="art-cells__plate"
          ><span>{{ cell.initials }}</span></span>
          <img
            v-if="cell.icon"
            alt=""
            class="art-cells__icon"
            :src="cell.icon"
          >
          <span
            v-if="cell.archived"
            class="art-cells__tag"
          >Archived</span>
        </span>
        <span class="art-cells__caption">
          <span class="art-cells__name">{{ cell.title }}</span>
          <span
            v-if="cell.sub"
            class="art-cells__sub"
          >{{ cell.sub }}</span>
          <span
            v-if="cell.chips?.length"
            class="art-cells__chips"
          >
            <span
              v-for="chip in cell.chips"
              :key="chip"
              class="art-cells__chip"
            >{{ chip }}</span>
          </span>
        </span>
      </a>
    </li>
  </ul>
</template>

<style scoped>
.art-cells {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 1.6rem 2px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.art-cells__cell {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  min-width: 0;
  color: inherit;
  text-decoration: none;
}

.art-cells__cell:focus-visible {
  outline: 2px solid var(--color-brand);
  outline-offset: 3px;
}

.art-cells__art {
  position: relative;
  display: block;
  aspect-ratio: 16 / 9;
  overflow: hidden;
  background-color: var(--color-surface);
}

.art-cells__banner {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: scale 620ms cubic-bezier(0.22, 1, 0.36, 1), filter 400ms ease;
}

.art-cells__cell:hover .art-cells__banner {
  scale: 1.04;
}

.art-cells__cell--old .art-cells__banner {
  filter: saturate(0.35) brightness(0.8);
}

.art-cells__cell--old:hover .art-cells__banner,
.art-cells__cell--old:focus-visible .art-cells__banner {
  filter: none;
}

.art-cells__plate {
  position: absolute;
  inset: 0;
  overflow: hidden;
  background:
    radial-gradient(110% 90% at 0 0, color-mix(in oklab, var(--accent) 30%, transparent), transparent 70%),
    repeating-linear-gradient(-62deg, transparent 0 22px, color-mix(in oklab, var(--accent) 7%, transparent) 22px 24px),
    var(--color-pit);
}

.art-cells__plate > span {
  position: absolute;
  top: -1rem;
  right: 0;
  font-family: var(--font-display);
  font-size: 6rem;
  line-height: 1;
  color: color-mix(in oklab, var(--accent) 22%, transparent);
}

.art-cells__icon {
  position: absolute;
  bottom: 0.7rem;
  left: 0.8rem;
  width: 2.2rem;
  height: 2.2rem;
  object-fit: contain;
}

.art-cells__tag {
  position: absolute;
  top: 0.6rem;
  right: 0.6rem;
  padding: 0.26rem 0.55rem;
  font-size: 0.68rem;
  font-weight: 600;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--color-ash);
  background-color: color-mix(in oklab, var(--color-ground) 70%, transparent);
  border: 1px solid currentcolor;
}

.art-cells__caption {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  padding-inline: 0.1rem;
}

.art-cells__name {
  font-family: var(--font-display);
  font-size: 1.05rem;
  line-height: 1.1;
  text-transform: uppercase;
  color: var(--color-chalk);
}

.art-cells__sub {
  display: -webkit-box;
  overflow: hidden;
  font-size: 0.85rem;
  line-height: 1.4;
  color: var(--color-ash);
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.art-cells__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
  margin-top: 0.3rem;
}

.art-cells__chip {
  padding: 0.18rem 0.5rem;
  font-size: 0.72rem;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--color-chalk);
  border: 1px solid color-mix(in oklab, var(--color-chalk) 28%, transparent);
}

@media (width < 768px) {
  .art-cells {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 1.1rem 2px;
  }

  .art-cells__name {
    font-size: 0.88rem;
  }

  .art-cells__sub {
    font-size: 0.78rem;
  }

  .art-cells__plate > span {
    font-size: 4rem;
  }
}
</style>
