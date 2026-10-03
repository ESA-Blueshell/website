<script lang="ts" setup>
/* The api's recorded faults, newest first: one row per fault however often it fired. */
import {computed, onMounted, ref, watch} from "vue"
import FilterBar from "@/components/island/FilterBar.vue"
import FilterPicker from "@/components/island/FilterPicker.vue"
import SearchBox from "@/components/island/SearchBox.vue"
import StateMark from "@/components/island/StateMark.vue"
import {type RecordedException, loadExceptions, matchesSearch, shortPlace, shortType} from "@/domains/exceptions"
import {formatDateNoSeconds} from "@/utils/timestamps"

defineOptions({name: "ExceptionListPage"})

const faults = ref<RecordedException[]>([])
const loaded = ref(false)
const search = ref("")
// Open is where an admin starts: a resolved fault needs nobody until it fires again.
const state = ref<string | null>("open")

const states = [
  {key: "open", label: "Open"},
  {key: "resolved", label: "Resolved"},
]

const load = async () => {
  faults.value = await loadExceptions(state.value === null ? null : state.value === "resolved")
  loaded.value = true
}

const shown = computed(() => faults.value.filter((fault) => matchesSearch(fault, search.value)))

const filtered = computed(() => search.value !== "" || state.value !== null)

const clear = () => {
  search.value = ""
  state.value = null
}

watch(state, load)
onMounted(load)
</script>

<template>
  <div
    class="exceptions"
    data-testid="exception-list"
  >
    <h1 class="exceptions__title">
      Exceptions
    </h1>
    <p class="exceptions__note">
      Every exception the api did not handle, one row per type and place however often it fired.
    </p>

    <filter-bar
      :active="filtered"
      testid="exception-filters"
      @clear="clear"
    >
      <search-box
        v-model="search"
        label="Search exceptions"
        testid="exception-search"
      />
      <filter-picker
        v-model="state"
        label="State"
        :options="states"
        testid="exception-state"
      />
    </filter-bar>

    <p
      v-if="loaded && shown.length === 0"
      class="exceptions__note"
      data-testid="exception-list-empty"
    >
      No exceptions to show.
    </p>

    <ul class="exceptions__rows">
      <li
        v-for="fault in shown"
        :key="fault.id"
      >
        <router-link
          class="exceptions__row"
          :data-testid="`exception-row-${fault.id}`"
          :to="`/management/exceptions/${fault.id}`"
        >
          <span class="exceptions__what">
            <strong>{{ shortType(fault.exceptionType) }}</strong>
            <span class="exceptions__where">{{ shortPlace(fault.thrownAt) }} · {{ fault.latestConcern }}</span>
            <span
              v-if="fault.latestMessage"
              class="exceptions__message"
            >{{ fault.latestMessage }}</span>
          </span>
          <span class="exceptions__count">{{ fault.occurrences }}×</span>
          <span class="exceptions__when">{{ formatDateNoSeconds(fault.lastSeenAt) }}</span>
          <state-mark :kind="fault.resolvedAt ? 'in-step' : 'unreachable'">
            {{ fault.resolvedAt ? "Resolved" : "Open" }}
          </state-mark>
        </router-link>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.exceptions {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  max-width: 64rem;
  padding: 2rem 2.4rem 3rem;
}

.exceptions__title {
  margin: 0;
  font-family: var(--font-display);
  font-size: clamp(1.4rem, 3vw, 2rem);
}

.exceptions__note {
  margin: 0;
  color: var(--color-ash);
}

.exceptions__rows {
  display: flex;
  flex-direction: column;
  margin: 0;
  padding: 0;
  list-style: none;
  border-top: 1px solid var(--color-hairline);
}

.exceptions__row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto 7rem;
  align-items: center;
  gap: 1rem;
  padding: 0.8rem 0.4rem;
  color: var(--color-chalk);
  text-decoration: none;
  border-bottom: 1px solid var(--color-hairline);
}

.exceptions__row:hover {
  background: color-mix(in oklab, var(--color-chalk) 5%, transparent);
}

.exceptions__what {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  min-width: 0;
}

.exceptions__where,
.exceptions__message {
  overflow: hidden;
  font-size: 0.84rem;
  color: var(--color-ash);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.exceptions__count,
.exceptions__when {
  font-size: 0.84rem;
  color: var(--color-ash);
  white-space: nowrap;
}

@media (max-width: 839px) {
  .exceptions {
    padding: 1.2rem 1.1rem 2rem;
  }

  .exceptions__row {
    grid-template-columns: minmax(0, 1fr) auto;
  }

  .exceptions__when {
    display: none;
  }
}
</style>
