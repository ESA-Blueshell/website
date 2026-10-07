<template>
  <div
    class="dash"
    data-testid="management-dashboard"
  >
    <management-head
      eyebrow="Management"
      title="Dashboard"
    >
      {{ greeting }}. Here is what needs you and how the association stands.
    </management-head>
    <div class="dash__grid">
      <management-panel
        :link="`All ${shown.length} alerts`"
        testid="dashboard-alerts"
        title="Alerts for you"
        to="/management/alerts"
      >
        <p
          v-if="shown.length === 0"
          class="dash__quiet"
        >
          Nothing needs you right now.
        </p>
        <div class="dash__rows">
          <management-row
            v-for="alert in shown.slice(0, 3)"
            :key="alert.key"
            :meta="alertRow(alert).meta"
            :name="alertRow(alert).name"
            :to="alertLink(alert)"
          >
            <state-mark :kind="ALERT_MARKS[alert.kind]">
              {{ alertRow(alert).from }}
            </state-mark>
          </management-row>
        </div>
      </management-panel>

      <management-panel
        link="Contributions"
        testid="dashboard-membership"
        :title="standing ? `Members, ${periodName}` : 'Members'"
        to="/management/contributions"
      >
        <p
          v-if="!standing"
          class="dash__quiet"
        >
          There is no contribution period yet.
        </p>
        <fact-list
          v-else
          :columns="2"
          :facts="membership"
        />
      </management-panel>

      <management-panel
        link="Events to approve"
        testid="dashboard-events"
        title="Events"
        to="/management/events"
      >
        <p
          class="dash__sub"
          :class="{'dash__sub--waiting': awaitingCount > 0}"
          data-testid="dashboard-events-queue"
        >
          {{ awaitingCount }} awaiting approval
        </p>
        <div class="dash__rows">
          <management-row
            v-for="event in awaiting"
            :key="event.id"
            :meta="formatMoment(event.startTime)"
            :name="event.title"
            :to="`/events/${event.id}`"
          >
            <state-mark kind="extra">
              Awaiting approval
            </state-mark>
          </management-row>
        </div>
        <p class="dash__sub">
          Coming up
        </p>
        <pair-list :pairs="comingUp" />
      </management-panel>

      <management-panel
        link="Platforms"
        testid="dashboard-platforms"
        title="Platforms and mail"
        to="/management/platforms/brevo"
      >
        <pair-list :pairs="standings" />
      </management-panel>
    </div>
  </div>
</template>

<script lang="ts" setup>
/* Management's first page: the reader's alerts and a block per area, each linking to the page it
   sums up. Admin figures are read only for an admin, so the board never calls an admin endpoint. */
import {countOf} from "@/utils/countOf"
import {computed, onMounted, ref} from "vue"
import FactList from "@/components/island/FactList.vue"
import StateMark, {type StateKind} from "@/components/island/StateMark.vue"
import ManagementHead from "@/components/management/ManagementHead.vue"
import ManagementPanel from "@/components/management/ManagementPanel.vue"
import ManagementRow from "@/components/management/ManagementRow.vue"
import PairList, {type Pair} from "@/components/management/PairList.vue"
import {AlertKind, alertLink, alertRow, useAlerts} from "@/domains/alerts"
import {fetchCohorts} from "@/domains/cohorts"
import {type PeriodStanding, readPeriodStanding} from "@/domains/contribution"
import {type EmailStats, loadEmailStats} from "@/domains/emails"
import {type EventResponse, readEventPage} from "@/domains/events"
import {loadExceptions} from "@/domains/exceptions"
import {type JobStats, loadJobStats} from "@/domains/jobs"
import store from "@/plugins/store"
import {formatMoment} from "@/utils/timestamps"

defineOptions({name: "ManagementDashboard"})

const admin = computed(() => store.getters.isAdmin === true)
const {shown, refresh} = useAlerts()

const standing = ref<PeriodStanding | null>(null)
const awaiting = ref<EventResponse[]>([])
const awaitingCount = ref(0)
const upcoming = ref<EventResponse[]>([])
const mail = ref<EmailStats | null>(null)
const cohortCount = ref(0)
const jobs = ref<JobStats | null>(null)
const openExceptions = ref(0)

const drifting = computed(() => shown.value.filter((alert) => alert.kind === AlertKind.TARGET_DRIFT).length)
const withoutList = computed(() => shown.value.filter((alert) => alert.kind === AlertKind.COHORT_WITHOUT_LIST).length)

const ALERT_MARKS: Record<AlertKind, StateKind> = {
  [AlertKind.TARGET_DRIFT]: "extra",
  [AlertKind.COHORT_WITHOUT_LIST]: "not-created",
  [AlertKind.EMAIL_FAILED]: "extra",
  [AlertKind.JOB_DEAD]: "not-created",
  [AlertKind.EXCEPTION_OPEN]: "extra",
  [AlertKind.ROLE_AWAITING_TWO_FACTOR]: "missing",
  [AlertKind.DISCORD_BOT_PERMISSIONS]: "not-created",
  [AlertKind.BREVO_FOLDERS_SHARE_NAME]: "extra",
  [AlertKind.DISCORD_ROLES_ABOVE_BOT]: "unreachable",
  [AlertKind.DISCORD_CHANNELS_BEYOND_BOT]: "unreachable",
}

const hour = new Date().getHours()
const greeting = hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening"

/** "2026-2027", or the one year a period starts and ends in. */
const periodName = computed(() => {
  if (!standing.value) return ""
  const [from, until] = [standing.value.startDate.slice(0, 4), standing.value.endDate.slice(0, 4)]
  return from === until ? from : `${from}-${until}`
})

const membership = computed(() => (standing.value
  ? [
      // What needs somebody is the value, and what does not is the line under it.
      {label: "Still to pay", value: String(standing.value.stillToPay), sub: `${standing.value.paid} paid`, quiet: standing.value.stillToPay === 0},
      {
        label: "Pending their first contribution",
        value: String(standing.value.pendingFirstContribution),
        sub: countOf(standing.value.members, "member", "members"),
        quiet: standing.value.pendingFirstContribution === 0,
      },
    ]
  : []))

const comingUp = computed<Pair[]>(() => upcoming.value.map((event) => ({
  label: event.title, value: formatMoment(event.startTime), to: `/events/${event.id}`,
})))

/** How each platform and the mail stand, one line each; the admin's own lines last. */
const standings = computed<Pair[]>(() => {
  const lines: Pair[] = [
    {label: "Brevo", value: `${cohortCount.value} cohorts · ${drifting.value} out of sync · ${withoutList.value} without a list`, to: "/management/platforms/brevo"},
  ]
  if (mail.value) {
    lines.push({
      label: "Sent mail",
      value: `${mail.value.deliveredCount} delivered · ${mail.value.failedCount + mail.value.bouncedCount} failed or bounced`,
      to: "/management/mail/sent",
      testid: "dashboard-mail",
    })
  }
  if (jobs.value) {
    lines.push({
      label: "Jobs", value: `${jobs.value.deadCount} dead · ${jobs.value.failedCount} failed`, to: "/management/jobs", adminOnly: true, testid: "dashboard-system",
    })
    lines.push({label: "Exceptions", value: `${openExceptions.value} open`, to: "/management/exceptions", adminOnly: true})
  }
  return lines
})

const loadEvents = async () => {
  const soonest = ["startTime,asc"]
  const [waiting, coming] = await Promise.all([
    readEventPage({approved: false, page: 0, size: 3, sort: soonest}),
    readEventPage({approved: true, from: new Date().toISOString().slice(0, 19), page: 0, size: 3, sort: soonest}),
  ])
  awaiting.value = waiting.events
  awaitingCount.value = waiting.page?.totalElements ?? waiting.events.length
  upcoming.value = coming.events
}

onMounted(async () => {
  const reads: Promise<unknown>[] = [
    refresh(),
    readPeriodStanding().then((read) => (standing.value = read)),
    loadEvents(),
    loadEmailStats().then((read) => (mail.value = read)),
    fetchCohorts().then((read) => (cohortCount.value = read.length)),
  ]
  if (admin.value) {
    reads.push(
      loadJobStats().then((read) => (jobs.value = read)),
      loadExceptions(false).then((read) => (openExceptions.value = read.length)),
    )
  }
  await Promise.all(reads)
})
</script>

<style scoped>
.dash {
  padding-bottom: 2rem;
}

.dash__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1rem;
  margin-top: 1rem;
  padding: 0 2.4rem;
}

.dash__rows {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.dash__quiet {
  font-size: 0.9rem;
  color: var(--color-ash);
}

.dash__sub {
  font-size: 11px;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  color: var(--color-ash);
}

.dash__sub--waiting {
  color: var(--color-warning);
}

@media (--phone) {
  .dash__grid {
    grid-template-columns: minmax(0, 1fr);
    padding: 0 1.1rem;
  }
}
</style>
