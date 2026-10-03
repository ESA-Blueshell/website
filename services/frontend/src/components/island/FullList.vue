<script lang="ts" setup generic="T">
/* Every row of a long list, held in memory and drawn only where the reader is looking, so
   thousands stay responsive. Rows are one fixed height; sorting is the caller's, on `rows`. */
import {computed, ref} from "vue"

const {rows, rowHeight = 48, height = 480, overscan = 6, rowKey, testid = undefined} = defineProps<{
  rows: T[]
  rowKey: (row: T) => string | number
  rowHeight?: number
  /** The window's own height; the page scrolls around it. */
  height?: number
  /** Rows drawn beyond each edge, so a quick scroll does not show blanks. */
  overscan?: number
  testid?: string
}>()

const scrolled = ref(0)

const first = computed(() => Math.max(0, Math.floor(scrolled.value / rowHeight) - overscan))
const last = computed(() => Math.min(rows.length, Math.ceil((scrolled.value + height) / rowHeight) + overscan))
const drawn = computed(() => rows.slice(first.value, last.value).map((row, offset) => ({row, index: first.value + offset})))

const onScroll = (event: Event) => {
  scrolled.value = (event.target as HTMLElement).scrollTop
}
</script>

<template>
  <div
    class="full-list"
    :data-testid="testid"
    role="list"
    :style="{height: `${height}px`}"
    @scroll.passive="onScroll"
  >
    <div
      class="full-list__track"
      :style="{height: `${rows.length * rowHeight}px`}"
    >
      <div
        v-for="item in drawn"
        :key="rowKey(item.row)"
        class="full-list__row"
        role="listitem"
        :style="{height: `${rowHeight}px`, transform: `translateY(${item.index * rowHeight}px)`}"
      >
        <slot
          :index="item.index"
          name="row"
          :row="item.row"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.full-list {
  position: relative;
  overflow-y: auto;
  overscroll-behavior: contain;
}

.full-list__track {
  position: relative;
}

.full-list__row {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  display: flex;
  align-items: center;
  border-bottom: 1px solid var(--color-hairline);
}
</style>
