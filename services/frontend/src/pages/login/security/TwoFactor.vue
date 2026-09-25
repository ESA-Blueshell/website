<template>
  <account-frame
    :crumb="SECURITY"
    eyebrow="Security"
    heading="Two-factor"
    island-content
  >
    <task-layout
      aside-title="Lost your phone?"
      data-testid="security-two-factor"
    >
      <template v-if="standing?.on">
        <notice-box
          v-if="standing.backupCodesLeft < LOW_BACKUP_CODES"
          testid="security-backup-codes-low"
          :title="`${plural(standing.backupCodesLeft, 'backup code')} left`"
          tone="warning"
        >
          Make new ones before you run out. The old ones stop working.
        </notice-box>
        <fact-list
          :columns="2"
          :facts="facts"
        />
        <backup-codes
          v-if="freshCodes.length"
          :codes="freshCodes"
        />

        <h2 class="two-factor__head">
          Change it
        </h2>
        <div class="two-factor__rows">
          <cut-row
            meta="Ten fresh codes; the old ones stop working"
            title="New backup codes"
          >
            <template #glyph>
              <security-glyph name="log" />
            </template>
            <template #end>
              <cut-button
                testid="security-new-backup-codes-btn"
                @click="withStepUp(makeNewCodes)"
              >
                Make new codes
              </cut-button>
            </template>
          </cut-row>
          <cut-row
            meta="New phone, or moving to another app"
            testid="security-replace-two-factor-btn"
            title="Replace authenticator app"
            to="/account/security/two-factor/set-up?replace=1"
          >
            <template #glyph>
              <security-glyph name="phone" />
            </template>
          </cut-row>
        </div>

        <div
          v-if="standing.mayTurnOff"
          class="two-factor__danger"
        >
          <div>
            <p class="two-factor__danger-title">
              Turn off two-factor
            </p>
            <p class="two-factor__note">
              Your password alone signs you in again. Trusted browsers are forgotten.
            </p>
          </div>
          <cut-button
            testid="security-turn-off-two-factor-btn"
            tone="danger"
            @click="withStepUp(turnOff)"
          >
            Turn off
          </cut-button>
        </div>
        <p
          v-else
          class="two-factor__note"
        >
          Your role needs two-factor, so it can be replaced but not turned off.
        </p>
      </template>

      <template v-else-if="standing">
        <p class="two-factor__lede">
          Off. Add a code from your phone to signing in, so a password alone does not get into your account.
        </p>
        <div>
          <cut-button
            :href="SET_UP"
            testid="security-set-up-two-factor-btn"
            tone="solid"
          >
            Set up two-factor
          </cut-button>
        </div>
      </template>

      <template #aside>
        <p>
          Sign in with a backup code, then replace the app here. No codes either? An admin resets
          two-factor after checking it is you.
        </p>
      </template>
    </task-layout>

    <step-up-dialog
      v-model="stepUpOpen"
      :two-factor-on="standing?.on === true"
      @proved="stepUpProved"
    />
  </account-frame>
</template>

<script lang="ts" setup>
import {computed, onMounted, ref} from "vue"
import {useStore} from "vuex"
import AccountFrame from "@/components/common/AccountFrame.vue"
import CutButton from "@/components/island/CutButton.vue"
import CutRow from "@/components/island/CutRow.vue"
import FactList, {type Fact} from "@/components/island/FactList.vue"
import NoticeBox from "@/components/island/NoticeBox.vue"
import TaskLayout from "@/components/island/TaskLayout.vue"
import {
  BackupCodes,
  formatSecurityDay,
  LOW_BACKUP_CODES,
  newBackupCodes,
  readTwoFactor,
  removeTwoFactor,
  SecurityGlyph,
  StepUpDialog,
  type TwoFactorStanding,
  useStepUp,
  type Written,
} from "@/domains/auth"
import type {TypedStore} from "@/plugins/store"

const SECURITY = {label: "Security", to: "/account/security"}
const SET_UP = "/account/security/two-factor/set-up"
const ALL_CODES = 10

const store = useStore() as TypedStore
const tell = (message: string) => store.commit("setStatusSnackbarMessage", message)
const {open: stepUpOpen, attempt: withStepUp, proved: stepUpProved} = useStepUp(tell)

const standing = ref<TwoFactorStanding | null>(null)
const freshCodes = ref<string[]>([])

const plural = (n: number, one: string) => `${n} ${n === 1 ? one : `${one}s`}`

const facts = computed<Fact[]>(() => [
  {
    label: "Authenticator app",
    value: standing.value?.since ? `On since ${formatSecurityDay(standing.value.since)}` : "On",
    sub: "Asked at every sign-in and step-up",
  },
  {
    label: "Backup codes",
    value: `${standing.value?.backupCodesLeft ?? 0} of ${ALL_CODES} left`,
    share: (standing.value?.backupCodesLeft ?? 0) / ALL_CODES,
    testid: "security-backup-codes-left",
  },
])

const refresh = async () => {
  standing.value = await readTwoFactor()
  if (standing.value) store.commit("setTwoFactor", standing.value)
}

const makeNewCodes = async (): Promise<Written<unknown>> => {
  const result = await newBackupCodes()
  if (result.ok) {
    freshCodes.value = result.value
    await refresh()
  }
  return result
}

const turnOff = async (): Promise<Written<unknown>> => {
  const result = await removeTwoFactor()
  if (result.ok) {
    await refresh()
    tell("Two-factor authentication is off.")
  }
  return result
}

onMounted(refresh)
</script>

<style scoped>
.two-factor__head {
  margin-top: 1.4rem;
  font-family: var(--font-body);
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  color: var(--color-eyebrow);
}

.two-factor__rows {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.two-factor__danger {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 1rem 2rem;
  margin-top: 1.5rem;
  padding: 1.1rem 1.3rem;
  background-color: color-mix(in oklab, var(--color-danger) 8%, transparent);
}

.two-factor__danger-title {
  font-family: var(--font-display);
  font-size: 1rem;
  text-transform: uppercase;
}

.two-factor__note {
  margin-top: 0.25rem;
  font-size: 0.88rem;
  color: var(--color-ash);
}

.two-factor__lede {
  font-size: 1.02rem;
  line-height: 1.6;
}
</style>
