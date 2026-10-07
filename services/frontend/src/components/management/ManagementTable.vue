<template>
  <div
    v-if="phone && $slots.phone"
    class="mg-rows"
    :data-testid="testid"
  >
    <div
      v-if="$slots.count || $slots.filters || $slots.search || searchText || orders.length > 0"
      class="mg-table__bar"
    >
      <span
        v-if="$slots.count || searchText"
        class="mg-table__count"
      ><slot name="count">{{ counted }}</slot></span>
      <slot name="filters" />
      <filter-picker
        v-if="orders.length > 0"
        any-label="As listed"
        label="Order by"
        :model-value="orderKey"
        :options="orders"
        :testid="`${testid ?? 'table'}-order`"
        @update:model-value="pickOrder"
      />
      <span class="mg-table__search">
        <slot name="search">
          <search-box
            v-if="searchText"
            v-model="query"
            :label="searchLabel"
            :testid="testid ? `${testid}-search` : undefined"
          />
        </slot>
      </span>
    </div>
    <div
      v-if="headerState"
      class="mg-rows__head"
    >
      <row-check
        :checked="headerState === 'checked'"
        :indeterminate="headerState === 'indeterminate'"
        :label="`Select the ${rows.length} shown`"
        :testid="testid ? `${testid}-select-shown` : undefined"
        @toggle="emit('toggleShown')"
      />
      <span>{{ selectionLine || `Select the ${rows.length} shown` }}</span>
      <button
        v-if="offerAll || allSelected"
        class="mg-table__all-act"
        :data-testid="testid ? `${testid}-select-all` : undefined"
        type="button"
        @click="offerAll ? emit('selectAll') : emit('clearSelection')"
      >
        {{ offerAll ? `Select all ${total}` : "Clear the selection" }}
      </button>
    </div>
    <p
      v-if="kept.length === 0 && ($slots.empty || rows.length > 0)"
      class="mg-table__empty"
    >
      <template v-if="rows.length > 0">
        Nothing matches the search.
      </template>
      <slot
        v-else
        name="empty"
      />
    </p>
    <template
      v-for="row in ordered"
      :key="rowKey(row)"
    >
      <slot
        name="phone"
        :row="row"
      />
    </template>
  </div>
  <div
    v-else
    class="mg-table"
    :data-testid="testid"
  >
    <div
      v-if="$slots.count || $slots.filters || $slots.search || searchText"
      class="mg-table__bar"
    >
      <span
        v-if="$slots.count || searchText"
        class="mg-table__count"
      ><slot name="count">{{ counted }}</slot></span>
      <slot name="filters" />
      <span class="mg-table__search">
        <slot name="search">
          <search-box
            v-if="searchText"
            v-model="query"
            :label="searchLabel"
            :testid="testid ? `${testid}-search` : undefined"
          />
        </slot>
      </span>
    </div>
    <div
      ref="scroller"
      class="mg-table__scroll"
      :style="height > 0 ? {maxHeight: `${height}px`} : undefined"
      @scroll.passive="onScroll"
      @wheel.passive="passOn"
    >
      <table :class="{'mg-table__locked': shares.length > 0}">
        <colgroup v-if="shares.length > 0">
          <col
            v-for="(share, index) in shares"
            :key="index"
            :style="{width: `${share}%`}"
          >
        </colgroup>
        <thead>
          <tr>
            <th
              v-if="$slots.check"
              class="mg-table__check"
            >
              <row-check
                v-if="headerState"
                :checked="headerState === 'checked'"
                :indeterminate="headerState === 'indeterminate'"
                :label="`Select the ${rows.length} shown`"
                :testid="testid ? `${testid}-select-shown` : undefined"
                @toggle="emit('toggleShown')"
              />
              <span
                v-else
                class="mg-table__said"
              >Select</span>
            </th>
            <th
              v-for="column in columns"
              :key="column.key"
              :aria-sort="column.key === sorted.key ? (sorted.descending ? 'descending' : 'ascending') : undefined"
            >
              <button
                v-if="column.sortable || column.sortBy"
                :aria-label="column.key === sorted.key ? `${column.label}, sorted ${sorted.descending ? 'descending' : 'ascending'}` : `Sort by ${column.label}`"
                class="mg-table__sort"
                :class="{'mg-table__sort--on': column.key === sorted.key}"
                :data-testid="column.testid ?? (testid ? `${testid}-sort-${column.key}` : undefined)"
                type="button"
                @click="sort(column)"
              >
                {{ column.label }}
                <svg
                  aria-hidden="true"
                  fill="none"
                  viewBox="0 0 12 12"
                >
                  <path
                    :d="column.key !== sorted.key ? UNSORTED : sorted.descending ? DESCENDING : ASCENDING"
                    stroke="currentColor"
                    :stroke-width="column.key === sorted.key ? 1.4 : 1.2"
                  />
                </svg>
              </button>
              <template v-else>
                {{ column.label }}
              </template>
            </th>
            <th v-if="$slots.acts">
              <span class="mg-table__said">Actions</span>
            </th>
          </tr>
          <tr
            v-if="offerAll || allSelected"
            class="mg-table__all"
          >
            <th :colspan="columns.length + 1 + ($slots.acts ? 1 : 0)">
              {{ selectionLine }}
              <button
                class="mg-table__all-act"
                :data-testid="testid ? `${testid}-select-all` : undefined"
                type="button"
                @click="offerAll ? emit('selectAll') : emit('clearSelection')"
              >
                {{ offerAll ? `Select all ${total}` : "Clear the selection" }}
              </button>
            </th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="kept.length === 0 && ($slots.empty || rows.length > 0)">
            <td
              class="mg-table__empty"
              :colspan="columns.length + ($slots.check ? 1 : 0) + ($slots.acts ? 1 : 0)"
            >
              <template v-if="rows.length > 0">
                Nothing matches the search.
              </template>
              <slot
                v-else
                name="empty"
              />
            </td>
          </tr>
          <tr
            v-if="before > 0"
            aria-hidden="true"
            class="mg-table__gap"
            :style="{height: `${before}px`}"
          />
          <tr
            v-for="row in drawn"
            :key="rowKey(row)"
            :class="{'mg-table__row--opens': to && to(row)}"
            :data-row="true"
            :data-testid="rowTestid?.(row)"
            @click="open(row, $event)"
          >
            <td
              v-if="$slots.check"
              class="mg-table__check"
            >
              <slot
                name="check"
                :row="row"
              />
            </td>
            <td
              v-for="column in columns"
              :key="column.key"
              :class="{'mg-table__wrap': column.wrap}"
            >
              <slot
                :name="column.key"
                :row="row"
              />
            </td>
            <td
              v-if="$slots.acts"
              class="mg-table__acts"
            >
              <span>
                <slot
                  name="acts"
                  :row="row"
                />
              </span>
            </td>
          </tr>
          <tr
            v-if="after > 0"
            aria-hidden="true"
            class="mg-table__gap"
            :style="{height: `${after}px`}"
          />
        </tbody>
      </table>
    </div>
  </div>
</template>

<script lang="ts" setup generic="T">
/* A Management table: flat rows a step off the page, sortable heads, a row's own acts at its end,
   and a row that has a page opens it when pressed anywhere. On a phone it hands each row to the page to draw as
   a ManagementRow, where a page gives one. */
import {computed, nextTick, onMounted, ref, useTemplateRef, watch} from "vue"
import {useRouter} from "vue-router"
import FilterPicker from "@/components/island/FilterPicker.vue"
import SearchBox from "@/components/island/SearchBox.vue"
import RowCheck from "@/components/management/RowCheck.vue"
import {usePhone} from "@/composables/usePhone"

/** What a column orders its rows by. A row with nothing to order by goes last, either way round. */
export type SortValue = string | number | boolean | null | undefined

export interface TableColumn<T = unknown> {
  /** Names the cell's slot and, where the column sorts, what it sorts by. */
  key: string
  label: string
  /** The page orders the rows itself and hears `sort`; for a list the server orders. */
  sortable?: boolean
  /** What the table orders its rows by under this head, for a list held whole on the page. */
  sortBy?(row: T): SortValue
  /** The first press puts the newest first, as a date reads; the second turns it round. */
  newestFirst?: boolean
  /** Lets the cell's text run over several lines. */
  wrap?: boolean
  testid?: string
}

const {
  columns, rows, rowKey, sortKey = "", descending = false, to = undefined, height = 0, testid = undefined, rowTestid = undefined,
  headerState = undefined, total = 0, selectedCount = 0, searchText = undefined, searchLabel = "Search", startSort = undefined,
} = defineProps<{
  // The rows say what a row is; a page's columns need not name it.
  columns: TableColumn<NoInfer<T>>[]
  rows: T[]
  rowKey: (row: T) => string | number
  sortKey?: string
  descending?: boolean
  /** The order the table opens in, under one of its own heads; the reader can turn it round or let it go. */
  startSort?: {key: string; descending?: boolean}
  /** A row's own page, or nothing for a row that has none. A press anywhere on the row opens it. */
  to?: (row: T) => string | null
  /** How tall the window the rows scroll in may grow, in px; 0 leaves it at what the page's own window leaves. */
  height?: number
  testid?: string
  rowTestid?: (row: T) => string
  /** How the rows shown stand against the selection. Gives the head its own tick, which takes them all. */
  headerState?: "checked" | "indeterminate" | "unchecked"
  /** Every row there is, shown or filtered away: what "select all" reaches past the ones shown. */
  total?: number
  /** How many are selected, shown or not. */
  selectedCount?: number
  /** What a row is found by. Gives the table a search of its own, where the page brings none. */
  searchText?: (row: T) => string
  searchLabel?: string
}>()

const emit = defineEmits<{
  sort: [key: string]
  /** An order picked on a phone, where there are no heads to press: a column and a way round, or none. */
  order: [key: string | null, descending: boolean]
  toggleShown: []
  selectAll: []
  clearSelection: []
  /** The reader is nearing the end of the rows held, for a page that reads more as it goes. */
  more: []
}>()

/* Once everything shown is ticked the head offers the rest, as a mail list does; once everything
   is, it says so and offers to let go. Neither where what is shown is all there is. */
const offerAll = computed(() => headerState === "checked" && total > rows.length && selectedCount < total)
const allSelected = computed(() => headerState === "checked" && total > rows.length && selectedCount >= total)
const selectionLine = computed(() => {
  if (offerAll.value) return `All ${rows.length} shown are selected.`
  return allSelected.value ? `All ${total} are selected.` : ""
})

const phone = usePhone()
const router = useRouter()

/* The table's own search: the rows whose text holds what is typed, whatever the case. Each row's
   text is worked out once as the rows arrive, not again for every letter typed. */
const query = ref("")
const texts = computed(() => (searchText ? rows.map((row) => searchText(row).toLowerCase()) : []))
const kept = computed(() => {
  const found = texts.value
  const wanted = query.value.trim().toLowerCase()
  if (!searchText || wanted === "") return rows
  return rows.filter((_, index) => found[index]?.includes(wanted))
})
/* The table's own order, for the columns that say what they order by: a head pressed once puts
   its rows in order, again turns them round, and a third time leaves them as the page gave them. */
const own = ref<{key: string; descending: boolean} | null>(startSort ? {key: startSort.key, descending: startSort.descending === true} : null)
const sorted = computed(() => own.value ?? {key: sortKey, descending})
const sort = (column: TableColumn<T>) => {
  if (!column.sortBy) {
    own.value = null
    return emit("sort", column.key)
  }
  const first = column.newestFirst === true
  if (own.value?.key !== column.key) own.value = {key: column.key, descending: first}
  else own.value = own.value.descending === first ? {key: column.key, descending: !first} : null
}
const ranked = (value: SortValue): string | number | null => {
  if (value === null || value === undefined || value === "") return null
  return typeof value === "boolean" ? Number(!value) : value
}
const ordered = computed(() => {
  const by = columns.find((one) => one.key === own.value?.key)?.sortBy
  if (!own.value || !by) return kept.value
  const turn = own.value.descending ? -1 : 1
  return kept.value
    .map((row) => ({row, value: ranked(by(row))}))
    .sort((a, b) => {
      if (a.value === null || b.value === null) return Number(a.value === null) - Number(b.value === null)
      if (typeof a.value === "number" && typeof b.value === "number") return (a.value - b.value) * turn
      return String(a.value).localeCompare(String(b.value), undefined, {numeric: true, sensitivity: "base"}) * turn
    })
    .map((one) => one.row)
})

/* On a phone there are no heads to press, so the same orders are offered in a picker, each column
   both ways round, in the way round its head would take first. */
const orders = computed(() => columns.filter((one) => one.sortBy || one.sortable).flatMap((one) => {
  const first = one.sortBy ? one.newestFirst === true : false
  return [
    {key: `${one.key}:${first ? "down" : "up"}`, label: one.label},
    {key: `${one.key}:${first ? "up" : "down"}`, label: `${one.label}, reversed`},
  ]
}))
const orderKey = computed(() => (sorted.value.key ? `${sorted.value.key}:${sorted.value.descending ? "down" : "up"}` : null))
const pickOrder = (picked: string | null) => {
  const [key, way] = picked?.split(":") ?? [null, "up"]
  const column = columns.find((one) => one.key === key)
  if (key && column?.sortBy) {
    own.value = {key, descending: way === "down"}
    return
  }
  own.value = null
  emit("order", key ?? null, way === "down")
}

const counted = computed(() => (kept.value.length === rows.length ? `Showing ${rows.length}` : `Showing ${kept.value.length} of ${rows.length}`))

/* Only the rows in the window, and a few either side, are in the document: a list of hundreds
   scrolls like a list of twenty. Short lists are drawn whole. A row's height is measured off the
   rows drawn, so the gaps that stand for the rest stay true as cells wrap. */
const WHOLE_UP_TO = 60
const OVERSCAN = 8
const NEAR_END = 400
const scroller = useTemplateRef<HTMLElement>("scroller")
const scrolled = ref(0)
const rowHeight = ref(50)
const windowHeight = ref(800)

const span = computed(() => {
  const count = kept.value.length
  if (count <= WHOLE_UP_TO) return {from: 0, until: count}
  const from = Math.max(0, Math.floor(scrolled.value / rowHeight.value) - OVERSCAN)
  const until = Math.min(count, Math.ceil((scrolled.value + windowHeight.value) / rowHeight.value) + OVERSCAN)
  return {from, until}
})
const drawn = computed(() => ordered.value.slice(span.value.from, span.value.until))
const before = computed(() => span.value.from * rowHeight.value)
const after = computed(() => (kept.value.length - span.value.until) * rowHeight.value)

/* A table sizes its columns to the rows in the document, and here those change as it scrolls. So
   the columns are measured once, off the first rows drawn, and held: each keeps its share of the
   width whatever scrolls into view. A list short enough to be drawn whole has no need. */
const shares = ref<number[]>([])
const hold = (box: HTMLElement) => {
  const widths = [...box.querySelectorAll<HTMLElement>("thead tr:first-child th")].map((head) => head.getBoundingClientRect().width)
  const whole = widths.reduce((sum, one) => sum + one, 0)
  if (whole > 0) shares.value = widths.map((one) => (one / whole) * 100)
}

const measure = () => {
  const box = scroller.value
  if (!box) return
  if (box.clientHeight > 0) windowHeight.value = box.clientHeight
  const heights = [...box.querySelectorAll<HTMLElement>("tr[data-row]")].map((row) => row.offsetHeight).filter((one) => one > 0)
  if (heights.length > 0) rowHeight.value = heights.reduce((sum, one) => sum + one, 0) / heights.length
  if (kept.value.length > WHOLE_UP_TO && shares.value.length === 0) hold(box)
}

/* The box stops dead at either end, so the head never drags off the rows. A scroll that starts
   with the box already at that end goes to the page instead, or a pointer over a table could not
   scroll the page at all. One that starts in the rows stays in the rows to its last drift: handed
   over half way, the page and the table's bar would move under a hand that is scrolling the rows. */
const WHEEL_UNIT = [1, 32, 800]
const NEW_SCROLL_AFTER = 180
let lastWheel = -NEW_SCROLL_AFTER
let forPage = false
const passOn = (event: WheelEvent) => {
  const box = event.currentTarget as HTMLElement
  const starts = event.timeStamp - lastWheel > NEW_SCROLL_AFTER
  lastWheel = event.timeStamp
  if (starts) forPage = event.deltaY > 0 ? box.scrollTop + box.clientHeight >= box.scrollHeight - 1 : box.scrollTop <= 0
  if (forPage) window.scrollBy(0, event.deltaY * (WHEEL_UNIT[event.deltaMode] ?? 1))
}

const onScroll = () => {
  const box = scroller.value
  if (!box) return
  scrolled.value = box.scrollTop
  if (box.scrollTop + box.clientHeight >= box.scrollHeight - NEAR_END) emit("more")
}

onMounted(measure)
watch(() => kept.value.length, (count) => {
  if (count <= WHOLE_UP_TO) shares.value = []
  void nextTick(measure)
})
// Other columns have other widths: let the table size them, then hold those.
watch(() => columns.map((one) => one.key).join(), () => {
  shares.value = []
  void nextTick(measure)
})

/* The whole row opens its page. What stands on the row and acts by itself, a tick, a button or
   a link elsewhere, keeps its own press. */
const open = (row: T, event: MouseEvent) => {
  const page = to?.(row)
  if (!page || (event.target as Element).closest("a, button, input, label, select")) return
  void router.push(page)
}

const ASCENDING = "M3 7.5 6 4.5l3 3"
const DESCENDING = "M3 4.5 6 7.5l3-3"
const UNSORTED = "M3.5 5 6 2.5 8.5 5M3.5 7 6 9.5 8.5 7"
</script>

<style scoped>
/* Every table stands under the same head, so two lists never differ at the top. A table too
   wide for its page scrolls in its own box, never the page. */
.mg-table__scroll {
  /* As tall as the window leaves, and the rows scroll inside it under a head that stays.
     No elastic end, which drags the head off the rows; passOn hands the page what is left. */
  max-height: calc(100vh - 7rem);
  overflow: auto;
  overscroll-behavior: none;
  /* Solid, rows and the lines between them alike: the page's pattern stays behind the table. */
  background-color: var(--color-ground);
}

.mg-table thead {
  position: sticky;
  top: 0;
  z-index: 1;
  background: var(--color-raised);
}

/* The line between the bar and the list, on the head row's top edge so the rows never cover it. */
.mg-table__bar + .mg-table__scroll thead th {
  box-shadow: inset 0 1px 0 color-mix(in oklab, var(--color-chalk) 22%, transparent);
}

.mg-table__gap td,
tr.mg-table__gap {
  padding: 0;
  border: 0;
  background: none;
}

/* What the list is narrowed by sits on the table's own top edge: how many at the start, the
   filters and the search at the far end, flush with the corner. */
.mg-table__bar {
  display: flex;
  flex-wrap: wrap;
  align-items: stretch;
  justify-content: flex-end;
  background-color: var(--color-raised);
}

/* The count first, then the filters, the search, and Clear filters last. */
.mg-table__bar :deep(.filter-bar) {
  display: contents;
}

.mg-table__search {
  order: 2;
}

.mg-table__bar :deep(.filter-bar__clear) {
  order: 3;
  padding: 0 0.8rem;
  border-left: 1px solid color-mix(in oklab, var(--color-chalk) 16%, transparent);
}

.mg-table__count {
  align-self: center;
  margin-right: auto;
  padding-block: 0.6rem;
  padding-left: 1.4rem;
  font-size: 0.85rem;
  white-space: nowrap;
  color: var(--color-ash);
}

.mg-table__count :deep(b) {
  font-weight: inherit;
}

.mg-table__search {
  display: flex;
}

/* The filters and the search stand shoulder to shoulder, lower than a form's fields. A filter in
   the bar never carries a line, set or not: Clear filters says one is set. */
.mg-table__bar :deep(.picker__field:not(:focus-within)) {
  border-bottom-color: transparent;
}

.mg-table__bar :deep(.picker__search) {
  padding-top: 0.95rem;
  padding-bottom: 0.2rem;
}

.mg-table__bar :deep(.island-field--inside .island-field__label) {
  top: 0.3rem;
}

.mg-table__search :deep(.search-box:not(:focus-within)) {
  border-bottom-color: transparent;
}

.mg-rows .mg-table__bar {
  padding-bottom: 0.5rem;
}

.mg-rows .mg-table__search {
  flex: 1 1 100%;
}

/* Every control takes the bar's own ground, and a line between each and the next keeps them apart. */
.mg-table__search :deep(.search-box),
.mg-table__bar :deep(.island-field__box) {
  background-color: transparent;
}

/* The picker stands on its field's ground; a ground of its own would cover the label. */
.mg-table__bar :deep(.picker__field) {
  background-color: transparent;
}

.mg-table__search,
.mg-table__bar :deep(.filter-picker) {
  border-left: 1px solid color-mix(in oklab, var(--color-chalk) 16%, transparent);
}

/* The search takes the height of the filters beside it, never more. */
.mg-table__search :deep(.search-box__input) {
  padding-block: 0.3rem;
}

.mg-rows .mg-table__bar {
  padding: 0.6rem 1rem;
}

.mg-rows .mg-table__search {
  flex: 1 1 100%;
  margin-left: 0;
}

.mg-table--boxed {
  overflow-y: auto;
}

.mg-table--boxed thead {
  position: sticky;
  top: 0;
  z-index: 1;
}

table {
  overflow-x: auto;
}

table {
  width: 100%;
  border-collapse: separate;
  border-spacing: 0 2px;
  /* The spacing also stands above the head row; pulled back so the head meets the bar. */
  margin-top: -2px;
}

th {
  padding: 0.5rem 0.9rem;
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.3em;
  text-align: left;
  text-transform: uppercase;
  white-space: nowrap;
  color: var(--color-ash);
}

td {
  padding: 0.4rem 0.9rem;
  font-size: 0.9rem;
  line-height: 1.3;
  white-space: nowrap;
  color: var(--color-chalk);
  background-color: var(--color-pit);
  transition: background-color 220ms ease;
}

tbody tr:hover td {
  background-color: var(--color-surface);
}

td:first-child {
  position: relative;
  padding-left: 1.4rem;
  font-size: 0.8rem;
  color: var(--color-ash);
}

th:first-child {
  padding-left: 1.4rem;
}

/* The leaning bar that marks the row under the pointer. */
td:first-child::before {
  content: "";
  position: absolute;
  top: 0.4rem;
  bottom: 0.4rem;
  left: 0.5rem;
  width: 3px;
  background: var(--color-brand);
  transform: skewX(-12deg);
  scale: 1 0;
  transition: scale 320ms var(--ease-out-quint);
}

tbody tr:hover td:first-child::before {
  scale: 1 1;
}

.mg-table__row--opens {
  cursor: pointer;
}

.mg-table__wrap {
  white-space: normal;
}

/* Held columns: a cell longer than its column is cut short with an ellipsis, never pushed wider. */
.mg-table__locked {
  table-layout: fixed;
}

.mg-table__locked td,
.mg-table__locked td :deep(.mg-name),
.mg-table__locked td :deep(.mg-sub) {
  overflow: hidden;
  text-overflow: ellipsis;
}

.mg-table__check {
  width: 2.6rem;
  padding-right: 0;
}

.mg-table__acts {
  padding-top: 0.15rem;
  padding-bottom: 0.15rem;
  text-align: right;
}

.mg-table__acts > span {
  display: flex;
  justify-content: flex-end;
  gap: 0.35rem;
}

.mg-table__sort {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0;
  font: inherit;
  letter-spacing: inherit;
  text-transform: inherit;
  color: inherit;
  cursor: pointer;
}

.mg-table__sort:hover {
  color: var(--color-chalk);
}

/* The table's one accent: the head the list is ordered by. The lit blue in the dark, since the
   plain brand blue falls short of 4.5:1 on the raised ground at this size. */
.mg-table__sort--on {
  color: var(--color-brand-lit);
}

:where([data-theme="light"]) .mg-table__sort--on {
  color: var(--color-brand-ink);
}

.mg-table__sort svg {
  width: 13px;
  height: 13px;
  opacity: 0.45;
}

.mg-table__sort--on svg {
  opacity: 1;
}

.mg-table__all th {
  padding: 0.55rem 1.4rem;
  font-size: 0.86rem;
  font-weight: 400;
  letter-spacing: 0;
  text-transform: none;
  color: var(--color-chalk);
  background-color: color-mix(in oklab, var(--color-brand) 12%, var(--band-ground));
}

.mg-table__all-act {
  margin-left: 0.6rem;
  font: inherit;
  font-weight: 600;
  color: var(--color-brand-ink);
  cursor: pointer;
}

.mg-table__all-act:hover {
  text-decoration: underline;
  text-underline-offset: 3px;
}

.mg-rows__head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.4rem 0.8rem;
  padding: 0.7rem 1rem;
  font-size: 0.86rem;
  color: var(--color-ash);
  background-color: var(--band-ground);
}

.mg-table__empty,
td.mg-table__empty {
  padding: 1.2rem 1.4rem;
  font-size: 0.9rem;
  color: var(--color-ash);
}

td.mg-table__empty::before {
  display: none;
}

.mg-table__said {
  position: absolute;
  left: -9999px;
}

.mg-rows {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
</style>

<!-- What a page writes inside a cell or a row. Unscoped: a slot's content belongs to the page. -->
<style>
.mg-name {
  font-family: var(--font-display);
  font-size: 0.92rem;
  font-weight: 400;
  letter-spacing: 0.02em;
  text-transform: uppercase;
  color: var(--color-chalk);
}

a.mg-name:hover {
  text-decoration: underline;
  text-underline-offset: 3px;
}

.mg-sub {
  display: block;
  font-size: 0.78rem;
  line-height: 1.25;
  color: var(--color-ash);
}

.mg-why {
  display: block;
  font-size: 0.84rem;
  white-space: normal;
  color: var(--color-ash);
}

.mg-quiet {
  color: var(--color-ash);
}

/* Several marks in one cell, each on a line of its own. */
.mg-marks {
  display: inline-flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.25rem;
}
</style>
