<script lang="ts" setup>
/* One person, on one page with tabs rather than modals. The tab is in the address, so a link
   opens the tab it names. */
import {computed, ref, watch} from "vue"
import {useRoute, useRouter} from "vue-router"
import StateMark from "@/components/island/StateMark.vue"
import DeletionConfirmationDialog from "@/components/common/modals/DeletionConfirmationDialog.vue"
import AddressForm from "@/components/form/AddressForm.vue"
import UserForm from "@/components/form/UserForm.vue"
import MandatePanel from "@/components/management/MandatePanel.vue"
import MembershipPanel from "@/components/management/MembershipPanel.vue"
import RecoveryAction from "@/components/management/RecoveryAction.vue"
import {AccountSecurityPanel} from "@/domains/auth"
import {type TokenPurpose, listPendingActivations} from "@/domains/recovery"
import {type MemberPeriodContribution, contributionEmailLabels, listMemberContributions, recordPayment, withdrawPayment} from "@/domains/contribution"
import {type AddressResponse, MEMBERSHIP_WORDS, type MembershipResponse, type RoleStanding, type UserDetailResponse, deleteUser, highestRoleLabel, listMembershipsFor, membershipStateOf, readAddress, readUser} from "@/domains/user"
import UserRolesPanel from "@/domains/user/components/UserRolesPanel.vue"
import {$handleNetworkError} from "@/plugins/handleNetworkError"
import store from "@/plugins/store"
import {type EditableUser, toEditableUser} from "@/utils/editableUser"
import {feeTypeLabels} from "@/utils/feePreview"
import {memberTypeLabel} from "@/utils/memberType"
import {formatDate} from "@/utils/timestamps"

defineOptions({name: "UserDetailPage"})

const TABS = [
  {key: "overview", label: "Overview"},
  {key: "membership", label: "Membership"},
  {key: "contributions", label: "Contributions"},
  {key: "profile", label: "Profile"},
  {key: "account", label: "Account"},
  {key: "roles", label: "Roles"},
] as const

const route = useRoute()
const router = useRouter()
const id = computed(() => Number(route.params.id))
const tab = computed(() => (typeof route.params.tab === "string" && route.params.tab !== "" ? route.params.tab : "overview"))

const person = ref<UserDetailResponse | null>(null)
const memberships = ref<MembershipResponse[]>([])
const periods = ref<MemberPeriodContribution[]>([])
const loaded = ref(false)
const said = ref<{periodId: number; text: string} | null>(null)
const profile = ref<EditableUser | null>(null)
const profileForm = ref<InstanceType<typeof UserForm> | null>(null)
const profileSaved = ref<string | null>(null)
const address = ref<Partial<AddressResponse>>({})
const activation = ref<TokenPurpose | null>(null)
const deleteOpen = ref(false)
const isAdmin = computed(() => store.getters.isAdmin === true)

const current = computed(() => memberships.value.find((one) => !one.endDate) ?? null)
/** The membership a mandate is recorded on: the running one, else the newest. */
const latestMembership = computed(() => current.value ?? [...memberships.value].sort((a, b) => b.startDate.localeCompare(a.startDate))[0] ?? null)
const since = computed(() => memberships.value.map((one) => one.startDate).sort()[0] ?? null)
const membershipState = computed(() => membershipStateOf(memberships.value))
const standing = computed(() => MEMBERSHIP_WORDS[membershipState.value])
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
  profile.value = found ? toEditableUser(found) : null
  address.value = found?.addressId == null ? {} : await readAddress(found.addressId).catch(() => ({}))
  activation.value = found && !found.enabled ? (await listPendingActivations().catch(() => ({} as Record<number, TokenPurpose>)))[found.id] ?? null : null
  loaded.value = true
}

const saveProfile = async () => {
  profileSaved.value = (await profileForm.value?.save()) != null ? "Saved." : null
}

const onRolesChanged = (standing: RoleStanding) => {
  if (person.value) person.value = {...person.value, roles: standing.roles}
}

const confirmDelete = async () => {
  deleteOpen.value = false
  try {
    await deleteUser(id.value)
    await router.push("/management/users")
  } catch (error) {
    // The account is still there, so the page stays.
    $handleNetworkError(error)
  }
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
  // A first payment makes a pending membership active, so the standing is read again with it.
  const [held, owed] = await Promise.all([listMembershipsFor(id.value), listMemberContributions(id.value)])
  memberships.value = held
  periods.value = owed
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
        <state-mark
          :kind="membershipState === 'current' ? 'in-step' : membershipState === 'pending' ? 'not-created' : memberships.length > 0 ? 'missing' : 'not-compared'"
          testid="user-standing"
        >
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
        <mandate-panel
          v-if="latestMembership"
          :membership-id="latestMembership.id"
          @changed="reloadMemberships"
        />
        <membership-panel
          :user-id="id"
          @changed="reloadMemberships"
        />
      </div>

      <div
        v-else-if="tab === 'profile'"
        class="person__stack"
        data-testid="user-profile"
      >
        <section
          v-if="profile"
          class="person__block"
        >
          <h2>Details</h2>
          <user-form
            ref="profileForm"
            v-model="profile"
            :options="{includeMemberProfile: true, updateKind: 'board', createVia: 'board'}"
            @submitted="load"
          />
          <button
            class="person__action"
            data-testid="user-profile-save"
            type="button"
            @click="saveProfile"
          >
            Save details
          </button>
          <span
            v-if="profileSaved"
            class="person__said"
            role="status"
          >{{ profileSaved }}</span>
        </section>
        <section class="person__block">
          <h2>Address</h2>
          <p
            v-if="address.opened === false"
            data-testid="user-address-unopened"
          >
            This address cannot be shown. Saving writes it anew.
          </p>
          <address-form
            v-model="address"
            data-testid="user-address-form"
            show-submit
            submit-text="Save address"
            :user-id="id"
            @submitted="load"
          />
        </section>
      </div>

      <div
        v-else-if="tab === 'account'"
        class="person__stack"
        data-testid="user-account"
      >
        <section class="person__block">
          <h2>Emails</h2>
          <div class="person__emails">
            <recovery-action
              action="password"
              :user="person"
            />
            <recovery-action
              v-if="activation"
              action="activation"
              :pending-activation="activation"
              :user="person"
              @done="load"
            />
          </div>
        </section>
        <section class="person__block">
          <h2>Security</h2>
          <account-security-panel :user-id="id" />
        </section>
        <section class="person__block">
          <h2>Delete</h2>
          <p class="person__note">
            Deleting anonymises the account; it can be restored from Account recovery for a while.
          </p>
          <button
            class="person__action person__action--danger"
            data-testid="user-delete"
            type="button"
            @click="deleteOpen = true"
          >
            Delete this account
          </button>
        </section>
        <deletion-confirmation-dialog
          v-model="deleteOpen"
          :message="`Are you sure you want to delete ${person.fullName}?`"
          title="Confirm User Deletion"
          @confirm="confirmDelete"
        />
      </div>

      <div
        v-else-if="tab === 'roles'"
        data-testid="user-roles"
      >
        <user-roles-panel
          :editable="isAdmin"
          :user-id="id"
          @changed="onRolesChanged"
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

.person__emails {
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem;
}

.person__stack {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.person__action--danger {
  border-color: var(--color-error, #e5484d);
  color: var(--color-error, #e5484d);
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
