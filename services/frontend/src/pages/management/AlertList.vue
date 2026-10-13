<script lang="ts" setup>
import {computed, onMounted, ref} from "vue"
import FactList from "@/components/island/FactList.vue"
import ListHead from "@/components/management/ListHead.vue"
import ManagementPage from "@/components/management/ManagementPage.vue"
import ManagementRow from "@/components/management/ManagementRow.vue"
import ManagementTable, {type TableColumn} from "@/components/management/ManagementTable.vue"
import MiniButton from "@/components/management/MiniButton.vue"
import {type Alert, alertLink, alertRow, alertTitle, hideAlert, showAlert, useAlerts} from "@/domains/alerts"
import store from "@/plugins/store"
import {formatMoment} from "@/utils/timestamps"

defineOptions({name: "AlertListPage"})

const {shown, hidden, unread, refresh} = useAlerts()
const loaded = ref(false)

const COLUMNS: TableColumn<Alert>[] = [
  {key: "from", label: "From", sortBy: (alert) => alertRow(alert).from},
  {key: "what", label: "Alert", wrap: true, sortBy: alertTitle},
  {key: "since", label: "Since", newestFirst: true, sortBy: (alert) => (alert.since ? Date.parse(alert.since) : null)},
]

const facts = computed(() => {
  const oldest = shown.value.map((one) => one.since).filter((since): since is string => !!since).sort()[0]
  return [
    {label: "Open", value: String(shown.value.length), sub: ""},
    {label: "Oldest", value: oldest ? formatMoment(oldest) : "None", sub: ""},
    {label: "Hidden by you", value: String(hidden.value.length), sub: "Not fixed yet, only hidden for you"},
  ]
})

const toggle = async (alert: Alert) => {
  const answered = alert.hidden ? await showAlert(alert.key) : await hideAlert(alert.key)
  if (!answered.ok) {
    store.commit("setStatusSnackbarMessage", answered.reason)
    return
  }
  await refresh()
}

onMounted(async () => {
  await refresh()
  loaded.value = true
})
</script>

<template>
  <management-page
    eyebrow="Management"
    testid="alert-list"
    title="Alerts"
  >
    <template #lede>
      Problems that need someone to act. An alert goes away by itself once the problem is fixed.
    </template>

    <fact-list
      class="alerts__facts"
      :facts="facts"
    />

    <management-table
      :columns="COLUMNS"
      label="Alerts"
      :row-key="(alert) => alert.key"
      :row-testid="(alert) => `alert-${alert.key}`"
      :search-text="(alert) => `${alertRow(alert).from} ${alertTitle(alert)}`"
      :rows="shown"
      testid="alert-table"
      :to="alertLink"
    >
      <template
        v-if="loaded"
        #empty
      >
        <span
          v-if="unread"
          data-testid="alert-list-unread"
        >
          The alerts could not be read.
          <mini-button
            testid="alert-list-reload"
            @click="refresh"
          >
            Try again
          </mini-button>
        </span>
        <span
          v-else
          data-testid="alert-list-empty"
        >No open alerts.</span>
      </template>
      <template #from="{row}">
        <span class="alerts__from">{{ alertRow(row).from }}</span>
      </template>
      <template #what="{row}">
        <router-link
          class="alerts__title"
          :to="alertLink(row)"
        >
          {{ alertTitle(row) }}
        </router-link>
      </template>
      <template #since="{row}">
        <span :class="{'mg-quiet': !row.since}">{{ row.since ? formatMoment(row.since) : "·" }}</span>
      </template>
      <template #acts="{row}">
        <mini-button
          :testid="`alert-open-${row.key}`"
          :to="alertLink(row)"
        >
          Open
        </mini-button>
        <mini-button
          :testid="`alert-hide-${row.key}`"
          @click="toggle(row)"
        >
          Hide
        </mini-button>
      </template>
      <template #phone="{row}">
        <management-row
          :meta="alertRow(row).meta"
          :name="alertRow(row).name"
          :testid="`alert-${row.key}`"
          :to="alertLink(row)"
        >
          {{ alertRow(row).from }}
          <template #acts>
            <mini-button
              :testid="`alert-hide-${row.key}`"
              @click="toggle(row)"
            >
              Hide
            </mini-button>
          </template>
        </management-row>
      </template>
    </management-table>

    <template v-if="hidden.length > 0">
      <list-head :title="`Hidden · ${hidden.length}`" />
      <management-table
        :columns="COLUMNS.slice(0, 2)"
        label="Hidden alerts"
        :row-key="(alert) => alert.key"
        :row-testid="(alert) => `alert-${alert.key}`"
        :search-text="(alert) => `${alertRow(alert).from} ${alertTitle(alert)}`"
        :rows="hidden"
        testid="alert-hidden"
        :to="alertLink"
      >
        <template #from="{row}">
          <span class="alerts__from">{{ alertRow(row).from }}</span>
        </template>
        <template #what="{row}">
          {{ alertTitle(row) }}
        </template>
        <template #acts="{row}">
          <mini-button
            :testid="`alert-show-${row.key}`"
            @click="toggle(row)"
          >
            Make visible
          </mini-button>
        </template>
        <template #phone="{row}">
          <management-row
            :meta="alertRow(row).meta"
            :name="alertRow(row).name"
            :testid="`alert-${row.key}`"
          >
            {{ alertRow(row).from }}
            <template #acts>
              <mini-button
                :testid="`alert-show-${row.key}`"
                @click="toggle(row)"
              >
                Make visible
              </mini-button>
            </template>
          </management-row>
        </template>
      </management-table>
    </template>
  </management-page>
</template>

<style scoped>
.alerts__facts {
  padding: 1.1rem 0 1.2rem;
}

.alerts__from {
  font-family: var(--font-bitmap);
  font-size: 0.66rem;
  white-space: nowrap;
  color: var(--color-ash);
}

.alerts__title {
  font-weight: 600;
  color: var(--color-chalk);
}

.alerts__title:hover {
  text-decoration: underline;
  text-underline-offset: 3px;
}
</style>
