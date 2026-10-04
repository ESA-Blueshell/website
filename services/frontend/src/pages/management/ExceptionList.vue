<script lang="ts" setup>
/* The api's recorded faults, newest first: one row per fault however often it fired. */
import {computed, onMounted, ref, watch} from "vue"
import FilterBar from "@/components/island/FilterBar.vue"
import FilterPicker from "@/components/island/FilterPicker.vue"
import SearchBox from "@/components/island/SearchBox.vue"
import StateMark from "@/components/island/StateMark.vue"
import ManagementPage from "@/components/management/ManagementPage.vue"
import ManagementRow from "@/components/management/ManagementRow.vue"
import ManagementTable, {type TableColumn} from "@/components/management/ManagementTable.vue"
import {type RecordedException, loadExceptions, matchesSearch, shortPlace, shortType} from "@/domains/exceptions"
import {formatMoment} from "@/utils/timestamps"

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

const COLUMNS: TableColumn[] = [
  {key: "what", label: "Exception", wrap: true},
  {key: "times", label: "Times"},
  {key: "seen", label: "Last seen"},
  {key: "state", label: "State"},
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
  <management-page
    eyebrow="System"
    testid="exception-list"
    title="Exceptions"
  >
    <template #lede>
      Every exception the api did not handle, one row per type and place however often it fired.
    </template>

    <management-table
      class="exceptions__table"
      :columns="COLUMNS"
      :row-key="(fault) => fault.id"
      :row-testid="(fault) => `exception-row-${fault.id}`"
      :rows="shown"
      testid="exception-table"
      :to="(fault) => `/management/exceptions/${fault.id}`"
    >
      <template #count>
        {{ shown.length }} of {{ faults.length }} exceptions
      </template>
      <template #filters>
        <filter-bar
          :active="filtered"
          testid="exception-filters"
          @clear="clear"
        >
          <filter-picker
            v-model="state"
            label="State"
            :options="states"
            testid="exception-state"
          />
        </filter-bar>
      </template>
      <template #search>
        <search-box
          v-model="search"
          label="Search exceptions"
          testid="exception-search"
        />
      </template>
      <template
        v-if="loaded"
        #empty
      >
        <span data-testid="exception-list-empty">No exceptions to show.</span>
      </template>
      <template #what="{row}">
        <router-link
          class="mg-name"
          :to="`/management/exceptions/${row.id}`"
        >
          {{ shortType(row.exceptionType) }}
        </router-link>
        <span class="mg-sub">{{ shortPlace(row.thrownAt) }} · {{ row.latestConcern }}</span>
        <span
          v-if="row.latestMessage"
          class="mg-why"
        >{{ row.latestMessage }}</span>
      </template>
      <template #times="{row}">
        {{ row.occurrences }}×
      </template>
      <template #seen="{row}">
        {{ formatMoment(row.lastSeenAt) }}
      </template>
      <template #state="{row}">
        <state-mark :kind="row.resolvedAt ? 'in-step' : 'extra'">
          {{ row.resolvedAt ? "Resolved" : "Open" }}
        </state-mark>
      </template>
      <template #phone="{row}">
        <management-row
          :meta="`${shortPlace(row.thrownAt)} · ${row.occurrences}× · ${formatMoment(row.lastSeenAt)}`"
          :name="shortType(row.exceptionType)"
          :testid="`exception-row-${row.id}`"
          :to="`/management/exceptions/${row.id}`"
        >
          <state-mark :kind="row.resolvedAt ? 'in-step' : 'extra'">
            {{ row.resolvedAt ? "Resolved" : "Open" }}
          </state-mark>
        </management-row>
      </template>
    </management-table>
  </management-page>
</template>

<style scoped>
.exceptions__table {
  margin-top: 1.2rem;
}
</style>
