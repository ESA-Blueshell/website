<script lang="ts" setup>
/* One person, on one page with tabs rather than modals. The tab is in the address, so a link
   opens the tab it names. */
import {computed, ref, watch} from "vue"
import {useRoute, useRouter} from "vue-router"
import ConfirmDialog from "@/components/island/ConfirmDialog.vue"
import CutButton from "@/components/island/CutButton.vue"
import CutRow from "@/components/island/CutRow.vue"
import FactList from "@/components/island/FactList.vue"
import NoticeBox from "@/components/island/NoticeBox.vue"
import PageTabs from "@/components/island/PageTabs.vue"
import RoleMark from "@/components/island/RoleMark.vue"
import StateMark from "@/components/island/StateMark.vue"
import StateTag from "@/components/island/StateTag.vue"
import AddressForm from "@/components/form/AddressForm.vue"
import UserForm from "@/components/form/UserForm.vue"
import ListHead from "@/components/management/ListHead.vue"
import ManagementPage from "@/components/management/ManagementPage.vue"
import ManagementTable, {type TableColumn} from "@/components/management/ManagementTable.vue"
import MandatePanel from "@/components/management/MandatePanel.vue"
import MembershipPanel from "@/components/management/MembershipPanel.vue"
import MiniButton from "@/components/management/MiniButton.vue"
import RecoveryAction from "@/components/management/RecoveryAction.vue"
import {AccountSecurityPanel} from "@/domains/auth"
import {type TokenPurpose, listPendingActivations} from "@/domains/recovery"
import {type MemberPeriodContribution, contributionEmailLabels, listMemberContributions, maskedIban, recordPayment, withdrawPayment} from "@/domains/contribution"
import {type AddressResponse, MEMBERSHIP_WORDS, type MembershipResponse, type OwnMandateResponse, type RoleStanding, type UserDetailResponse, deleteUser, highestRoleLabel, listMembershipsFor, membershipStateOf, readAddress, readMandateOf, readUser} from "@/domains/user"
import UserRolesPanel from "@/domains/user/components/UserRolesPanel.vue"
import {$handleNetworkError} from "@/plugins/handleNetworkError"
import store from "@/plugins/store"
import {type EditableUser, toEditableUser} from "@/utils/editableUser"
import {feeTypeLabels} from "@/utils/feePreview"
import {memberTypeLabel} from "@/utils/memberType"
import {formatDay, formatMoment} from "@/utils/timestamps"

defineOptions({name: "UserDetailPage"})

const TABS = ["Overview", "Membership", "Contributions", "Profile", "Address", "Account", "Roles"]

const PERIOD_COLUMNS: TableColumn[] = [
  {key: "period", label: "Period"},
  {key: "fee", label: "Fee type"},
  {key: "amount", label: "Amount"},
  {key: "paid", label: "Paid"},
  {key: "lastEmail", label: "Last payment email", wrap: true},
]

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
/** A mandate authorised before the membership started, which has no membership to be shown on yet. */
const pendingMandate = ref<OwnMandateResponse | null>(null)
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

const base = computed(() => `/management/users/${id.value}`)
const tabs = computed(() => TABS.map((label) => ({label, to: label === "Overview" ? base.value : `${base.value}/${label.toLowerCase()}`})))

/** "2026-2027", or the one year a period starts and ends in. */
const periodName = (period: {startDate: string; endDate: string}) => {
  const [from, until] = [period.startDate.slice(0, 4), period.endDate.slice(0, 4)]
  return from === until ? from : `${from}-${until}`
}

const typeName = computed(() => (latestMembership.value ? memberTypeLabel(latestMembership.value.memberType) : ""))
const eyebrow = computed(() => [standing.value, typeName.value].filter(Boolean).join(" · "))
const contact = computed(() => (person.value
  ? [person.value.username, person.value.email, person.value.discord ? `@${person.value.discord}` : ""].filter(Boolean).join(" · ")
  : ""))
const topRole = computed(() => {
  const role = person.value ? highestRoleLabel(person.value.roles) : ""
  return role.charAt(0).toUpperCase() + role.slice(1)
})

const membershipLine = computed(() => (current.value && since.value
  ? `${typeName.value} since ${formatDay(since.value)}, ${current.value.incasso ? "pays by incasso" : "pays by transfer"}`
  : memberships.value.length > 0 ? `Ended ${formatDay(latestMembership.value?.endDate)}` : "Has never been a member"))
const contributionLine = computed(() => (latest.value
  ? `${latest.value.paid ? "Paid" : "Not paid"} ${periodName(latest.value)}${latest.value.lastEmailAt ? `. Last payment email ${formatDay(latest.value.lastEmailAt)}` : ""}`
  : "No period as a member yet"))
const profileLine = computed(() => (person.value
  ? [person.value.email, person.value.phoneNumber, person.value.discordId ? `@${person.value.discord}` : "No Discord linked"].filter(Boolean).join(" · ")
  : ""))
const addressLine = computed(() => {
  if (person.value?.addressId == null) return "No address"
  const held = address.value
  if (held.opened === false) return "On file, but it cannot be shown"
  return [held.street && `${held.street} ${held.houseNumber ?? ""}`.trim(), held.city].filter(Boolean).join(", ") || "Address on file"
})
const accountLine = computed(() => (person.value
  ? [person.value.locked ? "Locked" : "", person.value.twoFactorOn ? "Two-factor on" : "No two-factor", person.value.awaitingReenrolment ? "waiting to set up again" : "",
      "password reset, activation and restore"].filter(Boolean).join(" · ")
  : ""))

const facts = computed(() => [
  {
    label: "Membership",
    value: [standing.value, typeName.value.toLowerCase()].filter(Boolean).join(", "),
    sub: since.value ? `Since ${formatDay(since.value)}${incasso.value ? ` · ${incasso.value}` : ""}` : "",
    testid: "user-standing",
  },
  latest.value
    ? {label: `Contribution ${periodName(latest.value)}`, value: latest.value.paid ? "Paid" : "Not paid", sub: latest.value.paidAt ? `Recorded ${formatDay(latest.value.paidAt)}` : ""}
    : {label: "Contribution", value: "None yet", sub: "No period as a member"},
  {label: "Account", value: person.value?.twoFactorOn ? "Two-factor on" : "No two-factor", sub: person.value?.locked ? "Locked" : "Open"},
])

const load = async () => {
  const [found, held, owed] = await Promise.all([readUser(id.value), listMembershipsFor(id.value), listMemberContributions(id.value)])
  person.value = found
  memberships.value = held
  periods.value = owed
  profile.value = found ? toEditableUser(found) : null
  address.value = found?.addressId == null ? {} : await readAddress(found.addressId).catch(() => ({}))
  // Only somebody without a running membership can have a mandate waiting for one.
  const waiting = found && !held.some((one) => !one.endDate) ? await readMandateOf(id.value) : null
  pendingMandate.value = waiting?.pending ? waiting : null
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
  <management-page
    v-if="loaded && !person"
    :back="{to: '/management/users', label: 'Users'}"
    eyebrow="Members"
    testid="user-detail"
    title="Nobody here"
  >
    <p
      class="person__note"
      data-testid="user-detail-missing"
    >
      There is nobody with number {{ id }}.
    </p>
  </management-page>
  <management-page
    v-else-if="person"
    :back="{to: '/management/users', label: 'Users'}"
    :eyebrow="eyebrow"
    testid="user-detail"
    :title="person.fullName"
  >
    <template #lede>
      {{ contact }}
    </template>

    <page-tabs
      :entries="tabs"
      label="About this person"
      testid="user-tab"
    />

    <div
      v-if="tab === 'overview'"
      data-testid="user-overview"
    >
      <fact-list
        class="person__facts"
        :facts="facts"
      />
      <div class="person__rows">
        <cut-row
          :meta="membershipLine"
          testid="user-row-membership"
          title="Membership"
          :to="`${base}/membership`"
        >
          <template #end>
            <state-tag :tone="membershipState === 'current' ? 'ok' : membershipState === 'pending' ? 'warn' : 'quiet'">
              {{ standing }}
            </state-tag>
          </template>
        </cut-row>
        <cut-row
          :meta="contributionLine"
          testid="user-row-contributions"
          title="Contributions"
          :to="`${base}/contributions`"
        >
          <template
            v-if="latest"
            #end
          >
            <state-tag :tone="latest.paid ? 'ok' : 'warn'">
              {{ latest.paid ? "Paid" : "Not paid" }}
            </state-tag>
          </template>
        </cut-row>
        <cut-row
          :meta="profileLine"
          testid="user-row-profile"
          title="Profile"
          :to="`${base}/profile`"
        />
        <cut-row
          :meta="addressLine"
          testid="user-row-address"
          title="Address"
          :to="`${base}/address`"
        />
        <cut-row
          :meta="accountLine"
          testid="user-row-account"
          title="Account"
          :to="`${base}/account`"
        />
        <cut-row
          :meta="person.roles.join(', ')"
          testid="user-row-roles"
          title="Roles"
          :to="`${base}/roles`"
        >
          <template #end>
            <role-mark :role="topRole" />
          </template>
        </cut-row>
      </div>

      <list-head title="Danger zone" />
      <notice-box
        title="Delete this user"
        tone="danger"
      >
        <div class="person__danger">
          <p>Their account goes. It can be restored from Account recovery for a while.</p>
          <cut-button
            testid="user-delete"
            tone="danger"
            @click="deleteOpen = true"
          >
            Delete user
          </cut-button>
        </div>
      </notice-box>
      <confirm-dialog
        confirm-label="Delete user"
        :open="deleteOpen"
        :question="`${person.fullName}'s account goes. It can be restored from Account recovery for a while.`"
        testid="user-delete-dialog"
        :title="`Delete ${person.fullName}?`"
        working-label="Deleting"
        @confirm="confirmDelete"
        @update:open="deleteOpen = $event"
      />
    </div>

    <div
      v-else-if="tab === 'membership'"
      class="person__stack"
      data-testid="user-membership"
    >
      <p
        v-if="incasso"
        class="person__note"
        data-testid="user-incasso"
      >
        {{ incasso }}
      </p>
      <p
        v-if="pendingMandate"
        class="person__note"
        data-testid="user-pending-mandate"
      >
        Pending online mandate for the account {{ maskedIban(pendingMandate) }}, authorised on {{ pendingMandate.signedOn }}.
        Its PDF is available once the membership starts.
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
      <section v-if="profile">
        <list-head title="Details" />
        <user-form
          ref="profileForm"
          v-model="profile"
          :options="{includeMemberProfile: true, updateKind: 'board', createVia: 'board'}"
          @submitted="load"
        />
        <div class="person__acts">
          <cut-button
            testid="user-profile-save"
            tone="solid"
            @click="saveProfile"
          >
            Save details
          </cut-button>
          <span
            v-if="profileSaved"
            class="person__said"
            role="status"
          >{{ profileSaved }}</span>
        </div>
      </section>
    </div>

    <div
      v-else-if="tab === 'address'"
      class="person__stack"
      data-testid="user-address"
    >
      <section>
        <p
          v-if="address.opened === false"
          class="person__note"
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
      <section>
        <list-head title="Help them in" />
        <div class="person__acts">
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
      <!-- Only an admin may read an account's security, so nobody else is shown an empty heading. -->
      <section v-if="isAdmin">
        <list-head title="Security" />
        <account-security-panel :user-id="id" />
      </section>
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
      <management-table
        v-else
        class="person__periods"
        :columns="PERIOD_COLUMNS"
        :row-key="(period) => period.periodId"
        :row-testid="(period) => `user-period-${period.periodId}`"
        search-label="Search periods"
        :search-text="(period) => periodName(period)"
        :rows="periods"
      >
        <template #period="{row}">
          <span class="mg-name">{{ periodName(row) }}</span>
          <span class="mg-sub">{{ formatDay(row.startDate) }} to {{ formatDay(row.endDate) }}</span>
        </template>
        <template #fee="{row}">
          {{ row.feeType && row.fee != null ? feeTypeLabels[row.feeType] : "Owes nothing" }}
        </template>
        <template #amount="{row}">
          <span :class="{'mg-quiet': row.fee == null}">{{ row.fee != null ? euro(row.fee) : "·" }}</span>
        </template>
        <template #paid="{row}">
          <state-mark :kind="row.paid ? 'in-step' : 'extra'">
            {{ row.paid ? `Paid${row.paidAt ? ` ${formatDay(row.paidAt)}` : ""}` : "Not paid" }}
          </state-mark>
          <span
            v-if="said?.periodId === row.periodId"
            class="mg-why"
            :data-testid="`user-period-said-${row.periodId}`"
            role="status"
          >{{ said.text }}</span>
        </template>
        <template #lastEmail="{row}">
          <template v-if="row.lastEmailAt && row.lastEmailKind">
            {{ formatMoment(row.lastEmailAt) }}
            <span class="mg-sub">{{ contributionEmailLabels[row.lastEmailKind] }}</span>
          </template>
          <span
            v-else
            class="mg-quiet"
          >None yet</span>
        </template>
        <template #acts="{row}">
          <mini-button
            :testid="`user-period-toggle-${row.periodId}`"
            @click="togglePayment(row)"
          >
            {{ row.paid ? "Withdraw payment" : "Record payment" }}
          </mini-button>
        </template>
      </management-table>
    </div>
  </management-page>
</template>

<style scoped>
.person__facts {
  padding: 1.1rem 0 1.2rem;
}

.person__rows {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.person__danger {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.8rem 2rem;
}

.person__stack {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding-top: 1rem;
}

.person__periods {
  margin-top: 1.2rem;
}

.person__acts {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.6rem 1rem;
}

.person__note,
.person__said {
  font-size: 0.9rem;
  color: var(--color-ash);
}

.person__note {
  margin-top: 1rem;
}
</style>
