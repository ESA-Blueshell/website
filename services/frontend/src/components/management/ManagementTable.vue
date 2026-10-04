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
      class="mg-table__scroll"
      :class="{'mg-table__scroll--boxed': height > 0}"
      :style="height > 0 ? {maxHeight: `${height}px`} : undefined"
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
            v-for="row in rows"
            :key="rowKey(row)"
            :data-testid="rowTestid?.(row)"
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
                aria-label="Open"
                tabindex="-1"
                :to="to(row)"
              >
                <go-arrow />
              </router-link>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script lang="ts" setup generic="T">
/* A Management table: flat rows a step off the page, sortable heads, and at its end either a
   row's own acts or the arrow to its page. On a phone it hands each row to the page to draw as
   a ManagementRow, where a page gives one. */
import {computed} from "vue"
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
  /** A row's own page. Gives every row the arrow, unless the page fills the acts slot. */
  to?: (row: T) => string
  /** A window this tall that the rows scroll in, under a head that stays; 0 lets the page scroll. */
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

const emit = defineEmits<{sort: [key: string]; toggleShown: []; selectAll: []; clearSelection: []}>()

/* Once everything shown is ticked the head offers the rest, as a mail list does; once everything
   is, it says so and offers to let go. Neither where what is shown is all there is. */
const offerAll = computed(() => headerState === "checked" && total > rows.length && selectedCount < total)
const allSelected = computed(() => headerState === "checked" && total > rows.length && selectedCount >= total)
const selectionLine = computed(() => {
  if (offerAll.value) return `All ${rows.length} shown are selected.`
  return allSelected.value ? `All ${total} are selected.` : ""
})

const phone = usePhone()

const ASCENDING = "M3 7.5 6 4.5l3 3"
const DESCENDING = "M3 4.5 6 7.5l3-3"
const UNSORTED = "M3.5 5 6 2.5 8.5 5M3.5 7 6 9.5 8.5 7"
</script>

<style scoped>
/* Every table stands in the same hairline box under the same head, so two lists never differ
   at the top. A table too wide for its page scrolls in that box, never the page. */
.mg-table {
  box-shadow: inset 0 0 0 1px var(--color-hairline);
}

.mg-table__scroll {
  overflow-x: auto;
}

.mg-table thead {
  background: var(--color-surface);
}

/* Contained, so a scroll that reaches either end stops there: an elastic end drags the head
   that stays off the rows, and hands the rest of the gesture to the page. */
.mg-table__scroll--boxed {
  overflow-y: auto;
  overscroll-behavior: none;
}

.mg-table__scroll--boxed thead {
  position: sticky;
  top: 0;
  z-index: 1;
}

/* What the list is narrowed by sits on the table itself: how many, the filters, and the search
   at the far end, flush with the corner. */
.mg-table__bar {
  display: flex;
  flex-wrap: wrap;
  align-items: stretch;
  gap: 0.5rem;
  padding-left: 1.4rem;
  background: var(--color-surface);
  box-shadow: inset 0 -1px 0 var(--color-hairline);
}

.mg-table__count {
  align-self: center;
  margin-right: 0.6rem;
  font-size: 0.85rem;
  white-space: nowrap;
  color: var(--color-ash);
}

.mg-table__count :deep(b) {
  color: var(--color-chalk);
}

.mg-table__search {
  display: flex;
  margin-left: auto;
}

.mg-table__bar :deep(.filter-bar) {
  margin-top: 0;
}

/* The search takes the height of the filters beside it, never more. */
.mg-table__search :deep(.search-box__input) {
  padding-block: 0.4rem;
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
  padding: 0.65rem 0.9rem;
  font-size: 0.9rem;
  white-space: nowrap;
  color: var(--color-chalk);
  background-color: var(--band-ground);
  transition: background-color 220ms ease;
}

tbody tr:hover td {
  background-color: color-mix(in oklab, var(--color-surface) 94%, transparent);
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
  top: 0.55rem;
  bottom: 0.55rem;
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

.mg-table__wrap {
  white-space: normal;
}

.mg-table__check {
  width: 2.6rem;
  padding-right: 0;
}

.mg-table__acts {
  padding-top: 0.2rem;
  padding-bottom: 0.2rem;
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
  font-size: 0.98rem;
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
  font-size: 0.8rem;
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
