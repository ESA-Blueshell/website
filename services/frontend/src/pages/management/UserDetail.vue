<script lang="ts" setup>
/* One person, on one page with tabs rather than modals. The tab is in the address, so a link
   opens the tab it names. */
import {computed, ref, watch} from "vue"
import {useRoute} from "vue-router"
import StateMark from "@/components/island/StateMark.vue"
import MembershipPanel from "@/components/management/MembershipPanel.vue"
import {type MemberPeriodContribution, contributionEmailLabels, listMemberContributions, recordPayment, withdrawPayment} from "@/domains/contribution"
import {type MembershipResponse, type UserDetailResponse, highestRoleLabel, listMembershipsFor, readUser} from "@/domains/user"
import {feeTypeLabels} from "@/utils/feePreview"
import {memberTypeLabel} from "@/utils/memberType"
import {formatDate} from "@/utils/timestamps"

defineOptions({name: "UserDetailPage"})

const TABS = [
  {key: "overview", label: "Overview"},
  {key: "membership", label: "Membership"},
  {key: "contributions", label: "Contributions"},
] as const

const route = useRoute()
const id = computed(() => Number(route.params.id))
const tab = computed(() => (typeof route.params.tab === "string" && route.params.tab !== "" ? route.params.tab : "overview"))

const person = ref<UserDetailResponse | null>(null)
const memberships = ref<MembershipResponse[]>([])
const periods = ref<MemberPeriodContribution[]>([])
const loaded = ref(false)
const said = ref<{periodId: number; text: string} | null>(null)

const current = computed(() => memberships.value.find((one) => !one.endDate) ?? null)
const since = computed(() => memberships.value.map((one) => one.startDate).sort()[0] ?? null)
const standing = computed(() => (current.value ? "Member" : memberships.value.length > 0 ? "Former member" : "Never a member"))
const incasso = computed(() => (current.value ? (current.value.incasso ? "Pays by incasso" : "Pays by transfer") : null))
const latest = computed(() => periods.value[0] ?? null)

const euro = (amount: number) => `€ ${amount.toFixed(2)}`
const feeOf = (period: MemberPeriodContribution) =>
  period.feeType && period.fee != null ? `${feeTypeLabels[period.feeType]}, ${euro(period.fee)}` : "Owes nothing"

const load = async () => {
  const [found, held, owed] = await Promise.all([readUser(id.value), listMembershipsFor(id.value), listMemberContributions(id.value)])
  person.value = found
  memberships.value = held
  periods.value = owed
  loaded.value = true
}

const reloadMemberships = async () => {
  memberships.value = await listMembershipsFor(id.value)
  periods.value = await listMemberContributions(id.value)
}

/** Records or withdraws the payment and says so on the row, as the paid toggle used to. */
const togglePayment = async (period: MemberPeriodContribution) => {
  const answered = period.paid ? await withdrawPayment(id.value, period.periodId) : await recordPayment(id.value, period.periodId)
  if (!answered.ok) {
    said.value = {periodId: period.periodId, text: answered.reason}
    return
  }
  periods.value = await listMemberContributions(id.value)
  said.value = {periodId: period.periodId, text: period.paid ? "Payment withdrawn." : "Payment recorded."}
}

watch(id, load, {immediate: true})
</script>

<template>
  <div
    class="person"
    data-testid="user-detail"
  >
    <router-link
      class="person__back"
      to="/management/users"
    >
      Users
    </router-link>

    <p
      v-if="loaded && !person"
      class="person__note"
      data-testid="user-detail-missing"
    >
      There is nobody with number {{ id }}.
    </p>

    <template v-if="person">
      <header class="person__head">
        <h1 class="person__title">
          {{ person.fullName }}
        </h1>
        <span class="person__note">@{{ person.username }}</span>
        <state-mark :kind="current ? 'in-step' : memberships.length > 0 ? 'missing' : 'not-compared'">
          {{ standing }}
        </state-mark>
      </header>

      <nav
        aria-label="About this person"
        class="person__tabs"
      >
        <router-link
          v-for="one in TABS"
          :key="one.key"
          :aria-current="tab === one.key ? 'page' : undefined"
          class="person__tab"
          :class="{'person__tab--on': tab === one.key}"
          :data-testid="`user-tab-${one.key}`"
          :to="one.key === 'overview' ? `/management/users/${id}` : `/management/users/${id}/${one.key}`"
        >
          {{ one.label }}
        </router-link>
      </nav>

      <div
        v-if="tab === 'overview'"
        class="person__grid"
        data-testid="user-overview"
      >
        <section class="person__block">
          <h2>Membership</h2>
          <p>{{ standing }}<span v-if="since"> since {{ since }}</span></p>
          <p v-if="current">
            {{ memberTypeLabel(current.memberType) }}
          </p>
          <p v-if="incasso">
            {{ incasso }}
          </p>
        </section>
        <section class="person__block">
          <h2>Contributions</h2>
          <p v-if="latest">
            {{ latest.startDate }} to {{ latest.endDate }}: {{ latest.paid ? "paid" : "not paid" }}
          </p>
          <p v-else>
            No period as a member yet.
          </p>
        </section>
        <section class="person__block">
          <h2>Profile</h2>
          <p>{{ person.email }}</p>
          <p v-if="person.phoneNumber">
            {{ person.phoneNumber }}
          </p>
          <p>{{ person.discordId ? `Discord: ${person.discord}` : "No Discord linked" }}</p>
          <p>{{ person.addressId == null ? "No address" : "Address on file" }}</p>
        </section>
        <section class="person__block">
          <h2>Account</h2>
          <p>{{ person.locked ? "Locked" : "Open" }}</p>
          <p>{{ person.twoFactorOn ? "Two-factor on" : "No two-factor" }}</p>
          <p v-if="person.awaitingReenrolment">
            Waiting to set up again
          </p>
        </section>
        <section class="person__block">
          <h2>Roles</h2>
          <p>{{ highestRoleLabel(person.roles) }}</p>
          <p class="person__note">
            {{ person.roles.join(", ") }}
          </p>
        </section>
      </div>

      <div
        v-else-if="tab === 'membership'"
        data-testid="user-membership"
      >
        <p
          v-if="incasso"
          class="person__note"
          data-testid="user-incasso"
        >
          {{ incasso }}
        </p>
        <membership-panel
          :user-id="id"
          @changed="reloadMemberships"
        />
      </div>

      <div
        v-else-if="tab === 'contributions'"
        data-testid="user-contributions"
      >
        <p
          v-if="periods.length === 0"
          class="person__note"
        >
          No period as a member yet.
        </p>
        <ul class="person__periods">
          <li
            v-for="period in periods"
            :key="period.periodId"
            class="person__period"
            :data-testid="`user-period-${period.periodId}`"
          >
            <span class="person__what">
              <strong>{{ period.startDate }} to {{ period.endDate }}</strong>
              <span class="person__note">{{ feeOf(period) }}</span>
            </span>
            <span>
              {{ period.paid ? `Paid${period.paidAt ? ` on ${formatDate(period.paidAt)}` : ""}` : "Not paid" }}
            </span>
            <span class="person__note">
              {{ period.lastEmailAt && period.lastEmailKind
                ? `${contributionEmailLabels[period.lastEmailKind]}, ${formatDate(period.lastEmailAt)}`
                : "No payment email yet" }}
            </span>
            <button
              class="person__action"
              :data-testid="`user-period-toggle-${period.periodId}`"
              type="button"
              @click="togglePayment(period)"
            >
              {{ period.paid ? "Withdraw payment" : "Record payment" }}
            </button>
            <span
              v-if="said?.periodId === period.periodId"
              class="person__said"
              :data-testid="`user-period-said-${period.periodId}`"
              role="status"
            >{{ said.text }}</span>
          </li>
        </ul>
      </div>
    </template>
  </div>
</template>

<style scoped>
.person {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  max-width: 64rem;
  padding: 2rem 2.4rem 3rem;
}

.person__back {
  align-self: flex-start;
  font-size: 0.84rem;
  color: var(--color-brand);
}

.person__head {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 0.5rem 1rem;
}

.person__title {
  margin: 0;
  font-family: var(--font-display);
  font-size: clamp(1.4rem, 3vw, 2rem);
}

.person__note {
  margin: 0;
  font-size: 0.86rem;
  color: var(--color-ash);
}

.person__tabs {
  display: flex;
  gap: 0.2rem;
  overflow-x: auto;
  border-bottom: 1px solid var(--color-hairline);
}

.person__tab {
  padding: 0.6rem 0.9rem;
  font-size: 0.9rem;
  color: var(--color-ash);
  text-decoration: none;
  white-space: nowrap;
}

.person__tab--on {
  color: var(--color-chalk);
  box-shadow: inset 0 -2px 0 var(--color-brand);
}

.person__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 16rem), 1fr));
  gap: 1rem;
}

.person__block {
  padding: 1rem;
  background-color: var(--band-ground);
  border: 1px solid var(--color-hairline);
}

.person__block h2 {
  margin: 0 0 0.5rem;
  font-size: 0.75rem;
  letter-spacing: 0.2em;
  text-transform: uppercase;
  color: var(--color-eyebrow);
}

.person__block p {
  margin: 0 0 0.25rem;
  overflow-wrap: anywhere;
}

.person__periods {
  display: flex;
  flex-direction: column;
  margin: 0;
  padding: 0;
  list-style: none;
  border-top: 1px solid var(--color-hairline);
}

.person__period {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem 1.2rem;
  padding: 0.8rem 0.4rem;
  border-bottom: 1px solid var(--color-hairline);
}

.person__what {
  display: flex;
  flex: 1 1 14rem;
  flex-direction: column;
}

.person__action {
  padding: 0.35rem 0.8rem;
  border: 1px solid var(--color-hairline);
  background: none;
  font: inherit;
  font-size: 0.84rem;
  color: var(--color-chalk);
  cursor: pointer;
}

.person__said {
  flex-basis: 100%;
  font-size: 0.84rem;
  color: var(--color-brand);
}

@media (max-width: 839px) {
  .person {
    padding: 1.2rem 1.1rem 2rem;
  }
}
</style>
