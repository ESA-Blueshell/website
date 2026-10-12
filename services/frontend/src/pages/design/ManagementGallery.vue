<script lang="ts" setup>
/* The parts every management page is built from, on one page and in both halves of the theme.
   Dev only: the route that reaches it is registered only when `import.meta.env.DEV`. */
import {computed, ref} from "vue"
import CountBadge from "@/components/island/CountBadge.vue"
import FactList from "@/components/island/FactList.vue"
import FilterBar from "@/components/island/FilterBar.vue"
import FilterPicker from "@/components/island/FilterPicker.vue"
import FoldOut from "@/components/island/FoldOut.vue"
import FullList from "@/components/island/FullList.vue"
import RoleMark from "@/components/island/RoleMark.vue"
import SearchBox from "@/components/island/SearchBox.vue"
import SelectionBar from "@/components/island/SelectionBar.vue"
import SortHeader from "@/components/island/SortHeader.vue"
import StateMark, {type StateKind} from "@/components/island/StateMark.vue"
import {JobExecutionStatus} from "@/domains/jobs"

defineOptions({name: "ManagementGallery"})

const dark = ref(true)

const search = ref("")
const status = ref<string | null>(null)
// Picker values come from the generated SDK, never restated here.
const statuses = Object.values(JobExecutionStatus).map((value) => ({key: value, label: value.toLowerCase()}))

const kinds: StateKind[] = ["in-sync", "missing", "extra", "unreachable", "not-created", "not-compared"]

const facts = [
  {label: "Members", value: "[ 211 ]", sub: "[ 9 ] pending their first contribution"},
  {label: "New this period", value: "[ 23 ]", sub: "Since 1 Sep 2026"},
  {label: "Needs a look", value: "[ 6 ] people"},
]

const sortedBy = ref<"name" | "when">("when")
const descending = ref(true)
const sortBy = (column: "name" | "when") => {
  // A time column starts newest first; any other starts from the top of the alphabet.
  descending.value = sortedBy.value === column ? !descending.value : column === "when"
  sortedBy.value = column
}

const people = Array.from({length: 5000}, (_, index) => ({id: index + 1, name: `Member ${index + 1}`, when: 5000 - index}))
const sorted = computed(() => {
  const rows = [...people].sort((a, b) => (sortedBy.value === "name" ? a.name.localeCompare(b.name, undefined, {numeric: true}) : a.when - b.when))
  return descending.value ? rows.reverse() : rows
})

const selected = ref(2)
const folded = ref(true)
</script>

<template>
  <div :data-theme="dark ? 'dark' : 'light'">
    <div
      class="island gallery"
      :class="{'island-dark': dark}"
      data-testid="management-gallery"
    >
      <header class="gallery__head">
        <h1 class="gallery__title">
          Management's parts
        </h1>
        <button
          class="gallery__theme"
          data-testid="management-gallery-theme"
          type="button"
          @click="dark = !dark"
        >
          {{ dark ? "Read it light" : "Read it dark" }}
        </button>
      </header>

      <section class="gallery__set">
        <h2 class="gallery__what">
          Filters
        </h2>
        <filter-bar
          :active="search !== '' || status !== null"
          testid="gallery-filters"
          @clear="search = ''; status = null"
        >
          <search-box
            v-model="search"
            label="Search jobs"
            testid="gallery-search"
          />
          <filter-picker
            v-model="status"
            label="Status"
            :options="statuses"
            testid="gallery-status"
          />
        </filter-bar>
      </section>

      <section class="gallery__set">
        <h2 class="gallery__what">
          Facts and counts
        </h2>
        <fact-list :facts="facts" />
        <div class="gallery__row">
          <count-badge
            :count="7"
            said="alerts"
          />
        </div>
      </section>

      <section class="gallery__set">
        <h2 class="gallery__what">
          State and role marks
        </h2>
        <div class="gallery__row">
          <state-mark
            v-for="kind in kinds"
            :key="kind"
            :kind="kind"
          />
        </div>
        <div class="gallery__row">
          <role-mark role="Admin" />
          <role-mark role="Board" />
          <role-mark role="Treasurer" />
          <role-mark role="Sitecie" />
        </div>
      </section>

      <section class="gallery__set gallery__set--wide">
        <h2 class="gallery__what">
          Full-length list, sortable
        </h2>
        <div class="gallery__heads">
          <sort-header
            :direction="sortedBy === 'name' ? (descending ? 'desc' : 'asc') : null"
            label="Name"
            testid="gallery-sort-name"
            @sort="sortBy('name')"
          />
          <sort-header
            :direction="sortedBy === 'when' ? (descending ? 'desc' : 'asc') : null"
            label="Joined"
            testid="gallery-sort-when"
            @sort="sortBy('when')"
          />
        </div>
        <full-list
          class="gallery__list"
          :height="320"
          :row-height="44"
          :row-key="(row) => row.id"
          :rows="sorted"
          testid="gallery-list"
        >
          <template #row="{row}">
            <span class="gallery__cell">{{ row.name }}</span>
          </template>
        </full-list>
        <selection-bar
          :count="selected"
          testid="gallery-selection"
          @clear="selected = 0"
        >
          <button
            class="gallery__theme"
            type="button"
          >
            Archive
          </button>
        </selection-bar>
      </section>

      <section class="gallery__set gallery__set--wide">
        <h2 class="gallery__what">
          Fold-out
        </h2>
        <fold-out
          v-model:open="folded"
          label="Run a job"
          testid="gallery-fold"
        >
          <p class="gallery__note">
            Work that opens in place, so the list under it stays in view.
          </p>
        </fold-out>
      </section>
    </div>
  </div>
</template>

<style scoped>
.gallery {
  min-height: 100vh;
  padding: 2rem clamp(1rem, 4vw, 4rem) 6rem;
  background-color: var(--color-ground);
  color: var(--color-chalk);
}

.gallery__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 2rem;
}

.gallery__title {
  font-family: var(--font-display);
  font-size: clamp(1.6rem, 4vw, 2.6rem);
  text-transform: uppercase;
}

.gallery__theme {
  padding: 0.4rem 0.9rem;
  font-family: var(--font-bitmap);
  font-size: 0.72rem;
  color: var(--color-chalk);
  text-transform: uppercase;
  cursor: pointer;
  background: none;
  border: 1px solid var(--color-hairline);
}

.gallery__set {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 1rem;
  margin-bottom: 2.5rem;
}

.gallery__set--wide {
  align-items: stretch;
  max-width: 48rem;
}

.gallery__what {
  font-family: var(--font-bitmap);
  font-size: 0.75rem;
  color: var(--color-ash);
  text-transform: uppercase;
}

.gallery__row,
.gallery__heads {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 1.2rem;
}

.gallery__heads {
  font-size: 0.75rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-ash);
}

.gallery__list {
  background-color: var(--band-ground);
}

.gallery__cell {
  padding: 0 1rem;
}

.gallery__note {
  color: var(--color-ash);
}
</style>
