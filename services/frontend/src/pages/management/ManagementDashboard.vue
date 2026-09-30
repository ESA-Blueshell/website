<script lang="ts" setup>
/* Management's first page: the reader's alerts and a block per area, each linking to the page it
   sums up. Admin figures are read only for an admin, so the board never calls an admin endpoint. */
import {computed, onMounted, ref} from "vue"
import {AlertKind, alertLink, alertTitle, useAlerts} from "@/domains/alerts"
import {fetchCohorts} from "@/domains/cohorts"
import {type PeriodStanding, readPeriodStanding} from "@/domains/contribution"
import {type EmailStats, loadEmailStats} from "@/domains/emails"
import {type EventResponse, readEventPage} from "@/domains/events"
import {loadExceptions} from "@/domains/exceptions"
import {type JobStats, loadJobStats} from "@/domains/jobs"
import store from "@/plugins/store"
import {formatDateNoSeconds} from "@/utils/timestamps"

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

const membership = computed(() => (standing.value
  ? [
      {label: "Members", value: standing.value.members},
      {label: "Paid", value: standing.value.paid},
      {label: "Still to pay", value: standing.value.stillToPay},
      {label: "Pending their first contribution", value: standing.value.pendingFirstContribution},
    ]
  : []))

const mailFigures = computed(() => (mail.value
  ? [
      {label: "Sent", value: mail.value.totalCount},
      {label: "Delivered", value: mail.value.deliveredCount},
      {label: "Opened", value: mail.value.openedCount},
      {label: "Failed or bounced", value: mail.value.failedCount + mail.value.bouncedCount},
    ]
  : []))

const jobFigures = computed(() => (jobs.value
  ? [
      {label: "Queued", value: jobs.value.queuedCount},
      {label: "Running", value: jobs.value.runningCount},
      {label: "Failed", value: jobs.value.failedCount},
      {label: "Dead", value: jobs.value.deadCount},
    ]
  : []))

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

<template>
  <div
    class="dash"
    data-testid="management-dashboard"
  >
    <h1 class="dash__title">
      Overview
    </h1>

    <div class="dash__grid">
      <section
        class="dash__block dash__block--wide"
        data-testid="dashboard-alerts"
      >
        <header class="dash__head">
          <h2>Alerts</h2>
          <router-link to="/management/alerts">
            All alerts ({{ shown.length }})
          </router-link>
        </header>
        <p
          v-if="shown.length === 0"
          class="dash__quiet"
        >
          Nothing needs you right now.
        </p>
        <ul class="dash__list">
          <li
            v-for="alert in shown.slice(0, 4)"
            :key="alert.key"
          >
            <router-link :to="alertLink(alert)">
              {{ alertTitle(alert) }}
            </router-link>
          </li>
        </ul>
      </section>

      <section
        class="dash__block"
        data-testid="dashboard-membership"
      >
        <header class="dash__head">
          <h2>Membership</h2>
          <router-link to="/management/users">
            Users
          </router-link>
        </header>
        <p
          v-if="standing"
          class="dash__quiet"
        >
          This period, since {{ standing.startDate }}
        </p>
        <p
          v-else
          class="dash__quiet"
        >
          There is no contribution period yet.
        </p>
        <dl class="dash__figures">
          <div
            v-for="figure in membership"
            :key="figure.label"
          >
            <dt>{{ figure.label }}</dt>
            <dd>{{ figure.value }}</dd>
          </div>
        </dl>
      </section>

      <section
        class="dash__block"
        data-testid="dashboard-events"
      >
        <header class="dash__head">
          <h2>Events</h2>
          <router-link to="/events">
            Events
          </router-link>
        </header>
        <p class="dash__quiet">
          {{ awaitingCount }} awaiting approval
        </p>
        <ul class="dash__list">
          <li
            v-for="event in awaiting"
            :key="event.id"
          >
            <router-link :to="`/events/${event.id}`">
              {{ event.title }}
            </router-link>
          </li>
        </ul>
        <p class="dash__quiet">
          Coming up
        </p>
        <ul class="dash__list">
          <li
            v-for="event in upcoming"
            :key="event.id"
          >
            <router-link :to="`/events/${event.id}`">
              {{ event.title }}
            </router-link>
            <span class="dash__when">{{ formatDateNoSeconds(event.startTime) }}</span>
          </li>
        </ul>
      </section>

      <section
        class="dash__block"
        data-testid="dashboard-mail"
      >
        <header class="dash__head">
          <h2>Mail</h2>
          <router-link to="/management/mail/sent">
            Sent mail
          </router-link>
        </header>
        <dl class="dash__figures">
          <div
            v-for="figure in mailFigures"
            :key="figure.label"
          >
            <dt>{{ figure.label }}</dt>
            <dd>{{ figure.value }}</dd>
          </div>
        </dl>
      </section>

      <section
        class="dash__block"
        data-testid="dashboard-platforms"
      >
        <header class="dash__head">
          <h2>Platforms</h2>
          <router-link to="/management/platforms/brevo">
            Brevo
          </router-link>
        </header>
        <dl class="dash__figures">
          <div>
            <dt>Cohorts</dt>
            <dd>{{ cohortCount }}</dd>
          </div>
          <div>
            <dt>Out of step</dt>
            <dd>{{ drifting }}</dd>
          </div>
          <div>
            <dt>Without a list</dt>
            <dd>{{ withoutList }}</dd>
          </div>
        </dl>
      </section>

      <section
        v-if="admin"
        class="dash__block"
        data-testid="dashboard-system"
      >
        <header class="dash__head">
          <h2>System <span class="dash__admin">@Admin</span></h2>
          <router-link to="/management/jobs">
            Jobs
          </router-link>
        </header>
        <dl class="dash__figures">
          <div
            v-for="figure in jobFigures"
            :key="figure.label"
          >
            <dt>{{ figure.label }}</dt>
            <dd>{{ figure.value }}</dd>
          </div>
          <div>
            <dt>
              <router-link to="/management/exceptions">
                Open exceptions
              </router-link>
            </dt>
            <dd>{{ openExceptions }}</dd>
          </div>
        </dl>
      </section>
    </div>
  </div>
</template>

<style scoped>
.dash {
  display: flex;
  flex-direction: column;
  gap: 1.2rem;
  padding: 2rem 2.4rem 3rem;
}

.dash__title {
  margin: 0;
  font-family: var(--font-display);
  font-size: clamp(1.4rem, 3vw, 2rem);
}

.dash__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 18rem), 1fr));
  gap: 1rem;
}

.dash__block {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  min-width: 0;
  padding: 1rem 1.1rem;
  background-color: var(--band-ground);
  border: 1px solid var(--color-hairline);
}

.dash__block--wide {
  grid-column: 1 / -1;
}

.dash__head {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  justify-content: space-between;
  gap: 0.5rem;
}

.dash__head h2 {
  margin: 0;
  font-size: 0.8rem;
  letter-spacing: 0.2em;
  text-transform: uppercase;
  color: var(--color-eyebrow);
}

.dash a {
  color: var(--color-brand);
  font-size: 0.88rem;
  overflow-wrap: anywhere;
}

.dash__admin {
  letter-spacing: 0.04em;
  text-transform: none;
  color: var(--color-warning);
}

.dash__quiet {
  margin: 0;
  font-size: 0.86rem;
  color: var(--color-ash);
}

.dash__list {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  margin: 0;
  padding: 0;
  list-style: none;
}

.dash__when {
  margin-left: 0.5rem;
  font-size: 0.8rem;
  color: var(--color-ash);
}

.dash__figures {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.7rem 1rem;
  margin: 0;
}

.dash__figures dt {
  font-size: 0.72rem;
  color: var(--color-ash);
}

.dash__figures dd {
  margin: 0.1rem 0 0;
  font-family: var(--font-display);
  font-size: 1.5rem;
}

@media (max-width: 839px) {
  .dash {
    padding: 1.2rem 1.1rem 2rem;
  }
}
</style>
