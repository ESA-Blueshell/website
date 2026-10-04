<template>
  <div
    v-if="phone && $slots.phone"
    class="mg-rows"
    :data-testid="testid"
  >
    <div
      v-if="$slots.count || $slots.filters || $slots.search"
      class="mg-table__bar"
    >
      <span
        v-if="$slots.count"
        class="mg-table__count"
      ><slot name="count" /></span>
      <slot name="filters" />
      <span class="mg-table__search"><slot name="search" /></span>
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
      v-if="rows.length === 0 && $slots.empty"
      class="mg-table__empty"
    >
      <slot name="empty" />
    </p>
    <template
      v-for="row in rows"
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
      v-if="$slots.count || $slots.filters || $slots.search"
      class="mg-table__bar"
    >
      <span
        v-if="$slots.count"
        class="mg-table__count"
      ><slot name="count" /></span>
      <slot name="filters" />
      <span class="mg-table__search"><slot name="search" /></span>
    </div>
    <div
      ref="scroller"
      class="mg-table__scroll"
      :style="height > 0 ? {maxHeight: `${height}px`} : undefined"
      @scroll.passive="onScroll"
    >
      <table>
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
              :aria-sort="column.key === sortKey ? (descending ? 'descending' : 'ascending') : undefined"
            >
              <button
                v-if="column.sortable"
                :aria-label="column.key === sortKey ? `${column.label}, sorted ${descending ? 'descending' : 'ascending'}` : `Sort by ${column.label}`"
                class="mg-table__sort"
                :class="{'mg-table__sort--on': column.key === sortKey}"
                :data-testid="column.testid"
                type="button"
                @click="emit('sort', column.key)"
              >
                {{ column.label }}
                <svg
                  aria-hidden="true"
                  fill="none"
                  viewBox="0 0 12 12"
                >
                  <path
                    :d="column.key !== sortKey ? UNSORTED : descending ? DESCENDING : ASCENDING"
                    stroke="currentColor"
                    :stroke-width="column.key === sortKey ? 1.4 : 1.2"
                  />
                </svg>
              </button>
              <template v-else>
                {{ column.label }}
              </template>
            </th>
            <th v-if="$slots.acts || to">
              <span class="mg-table__said">More</span>
            </th>
          </tr>
          <tr
            v-if="offerAll || allSelected"
            class="mg-table__all"
          >
            <th :colspan="columns.length + 1 + ($slots.acts || to ? 1 : 0)">
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
          <tr v-if="rows.length === 0 && $slots.empty">
            <td
              class="mg-table__empty"
              :colspan="columns.length + ($slots.check ? 1 : 0) + ($slots.acts || to ? 1 : 0)"
            >
              <slot name="empty" />
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
            <td
              v-else-if="to"
              class="mg-table__go"
            >
              <router-link
                v-if="to(row)"
                aria-label="Open"
                tabindex="-1"
                :to="to(row) ?? ''"
              >
                <go-arrow />
              </router-link>
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
/* A Management table: flat rows a step off the page, sortable heads, and at its end either a
   row's own acts or the arrow to its page. On a phone it hands each row to the page to draw as
   a ManagementRow, where a page gives one. */
import {computed, nextTick, onMounted, ref, useTemplateRef, watch} from "vue"
import {useRouter} from "vue-router"
import GoArrow from "@/components/management/GoArrow.vue"
import RowCheck from "@/components/management/RowCheck.vue"
import {usePhone} from "@/composables/usePhone"

export interface TableColumn {
  /** Names the cell's slot and, where the column sorts, what it sorts by. */
  key: string
  label: string
  sortable?: boolean
  /** Lets the cell's text run over several lines. */
  wrap?: boolean
  testid?: string
}

const {
  columns, rows, rowKey, sortKey = "", descending = false, to = undefined, height = 0, testid = undefined, rowTestid = undefined,
  headerState = undefined, total = 0, selectedCount = 0,
} = defineProps<{
  columns: TableColumn[]
  rows: T[]
  rowKey: (row: T) => string | number
  sortKey?: string
  descending?: boolean
  /** A row's own page, or nothing for a row that has none. A press anywhere on the row opens it,
   * and the row ends in the arrow unless the page fills the acts slot. */
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
}>()

const emit = defineEmits<{
  sort: [key: string]
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
  if (rows.length <= WHOLE_UP_TO) return {from: 0, until: rows.length}
  const from = Math.max(0, Math.floor(scrolled.value / rowHeight.value) - OVERSCAN)
  const until = Math.min(rows.length, Math.ceil((scrolled.value + windowHeight.value) / rowHeight.value) + OVERSCAN)
  return {from, until}
})
const drawn = computed(() => rows.slice(span.value.from, span.value.until))
const before = computed(() => span.value.from * rowHeight.value)
const after = computed(() => (rows.length - span.value.until) * rowHeight.value)

const measure = () => {
  const box = scroller.value
  if (!box) return
  if (box.clientHeight > 0) windowHeight.value = box.clientHeight
  const heights = [...box.querySelectorAll<HTMLElement>("tr[data-row]")].map((row) => row.offsetHeight).filter((one) => one > 0)
  if (heights.length > 0) rowHeight.value = heights.reduce((sum, one) => sum + one, 0) / heights.length
}

const onScroll = () => {
  const box = scroller.value
  if (!box) return
  scrolled.value = box.scrollTop
  if (box.scrollTop + box.clientHeight >= box.scrollHeight - NEAR_END) emit("more")
}

onMounted(measure)
watch(() => rows.length, () => void nextTick(measure))

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
/* Every table stands in the same hairline box under the same head, so two lists never differ
   at the top. A table too wide for its page scrolls in that box, never the page. */
.mg-table__scroll {
  /* As tall as the window leaves, and the rows scroll inside it under a head that stays.
     Contained, so a scroll that reaches either end stops there: an elastic end drags the head
     off the rows and hands the rest of the gesture to the page. */
  max-height: calc(100vh - 7rem);
  overflow: auto;
  overscroll-behavior: none;
  /* Solid, rows and the lines between them alike: the page's pattern stays behind the table. */
  background-color: var(--color-ground);
  box-shadow: inset 0 0 0 1px var(--color-hairline);
}

.mg-table thead {
  position: sticky;
  top: 0;
  z-index: 1;
  background: var(--color-surface);
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
}

.mg-table__count {
  align-self: center;
  margin-right: auto;
  padding-block: 0.6rem;
  padding-left: 0.2rem;
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

/* The filters and the search stand shoulder to shoulder, lower than a form's fields. */
.mg-table__bar :deep(.filter-bar) {
  gap: 0;
  margin-top: 0;
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

/* One ground for every control on the bar, solid as the table under them, and a line between
   each and the next so they read as separate. */
.mg-table__search :deep(.search-box),
.mg-table__bar :deep(.island-field__box) {
  background-color: var(--color-surface);
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

table {
  overflow-x: auto;
  box-shadow: inset 0 0 0 1px var(--color-hairline);
}

.mg-table thead {
  background: var(--color-surface);
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

.mg-table__go {
  width: 3.4rem;
  padding-left: 0;
  color: var(--color-ash);
}

.mg-table__go a {
  display: flex;
  justify-content: flex-end;
}

.mg-table__go svg {
  flex: none;
}

tbody tr:hover .mg-table__go {
  color: var(--color-chalk);
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

.mg-table__sort:hover,
.mg-table__sort--on {
  color: var(--color-chalk);
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
</style>
