<script lang="ts" setup>
/* The addresses written email can go out from. Board reads the list; an admin adds, edits and
   removes them, and their SMTP logins go to Vault without ever being shown again. */
import {computed, onMounted, ref} from "vue"
import CheckBox from "@/components/island/CheckBox.vue"
import ConfirmDialog from "@/components/island/ConfirmDialog.vue"
import CountInput from "@/components/island/CountInput.vue"
import CutButton from "@/components/island/CutButton.vue"
import FormField from "@/components/island/FormField.vue"
import ModalDialog from "@/components/island/ModalDialog.vue"
import RadioGroup from "@/components/island/RadioGroup.vue"
import StateMark from "@/components/island/StateMark.vue"
import TextInput from "@/components/island/TextInput.vue"
import ManagementPage from "@/components/management/ManagementPage.vue"
import ManagementRow from "@/components/management/ManagementRow.vue"
import ManagementTable, {type TableColumn} from "@/components/management/ManagementTable.vue"
import {
  type SendingAddress,
  SmtpSecurity,
  addAddress,
  loadSendingAddresses,
  removeAddress,
  saveAddress,
  securityLabel,
  usualPort,
} from "@/domains/emails"
import store from "@/plugins/store"

defineOptions({name: "SendingAddressesPage"})

const isAdmin = computed(() => store.getters.isAdmin === true)
const addresses = ref<SendingAddress[]>([])
const loaded = ref(false)

const COLUMNS: TableColumn<SendingAddress>[] = [
  {key: "address", label: "Address", wrap: true, sortBy: (one) => one.address},
  {key: "server", label: "Server", sortBy: (one) => one.host},
  {key: "first", label: "Default", sortBy: (one) => Number(one.isDefault)},
]

const securities = (Object.values(SmtpSecurity) as SmtpSecurity[]).map((one) => ({key: one, label: securityLabel(one)}))
const serverOf = (one: SendingAddress) => `${one.host}:${one.port} · ${securityLabel(one.security)}`

const load = async () => {
  addresses.value = await loadSendingAddresses()
  loaded.value = true
}

/** The form, for a new address or the one being edited. */
const editing = ref<SendingAddress | null>(null)
const open = ref(false)
const working = ref(false)
const failure = ref<string | null>(null)
const form = ref({address: "", displayName: "", host: "", port: "587", security: SmtpSecurity.STARTTLS as string, username: "", password: "", isDefault: false})

const start = (one: SendingAddress | null) => {
  editing.value = one
  form.value = one
    ? {address: one.address, displayName: one.displayName, host: one.host, port: String(one.port), security: one.security, username: "", password: "", isDefault: one.isDefault}
    : {address: "", displayName: "", host: "", port: "587", security: SmtpSecurity.STARTTLS, username: "", password: "", isDefault: addresses.value.length === 0}
  failure.value = null
  open.value = true
}

// A port left at the usual one for the old security follows the new one.
const pickSecurity = (security: string | null) => {
  if (!security) return
  if (form.value.port === String(usualPort(form.value.security as SmtpSecurity))) form.value.port = String(usualPort(security as SmtpSecurity))
  form.value.security = security
}

// A kept login is only sent to the server it was tried on, so a moved server asks for it again.
const moved = computed(() => {
  const was = editing.value
  return was !== null && (form.value.host.trim() !== was.host || Number(form.value.port) !== was.port || form.value.security !== was.security)
})
const loginHint = computed(() => {
  if (!editing.value) return ""
  return moved.value ? "The server changed, so fill in the login for it" : "Leave the username and password empty to keep the login there is"
})

const filled = computed(() => {
  const {address, displayName, host, port, username, password} = form.value
  const given = username !== "" && password !== ""
  const login = editing.value && !moved.value ? given || (username === "" && password === "") : given
  return address.trim() !== "" && displayName.trim() !== "" && host.trim() !== "" && Number(port) > 0 && login
})

const save = async () => {
  if (working.value || !filled.value) return
  working.value = true
  const {username, password, port, security, ...rest} = form.value
  const body = {...rest, port: Number(port), security: security as SmtpSecurity, username: username || undefined, password: password || undefined}
  const answered = editing.value ? await saveAddress(editing.value.id, body) : await addAddress(body)
  working.value = false
  if (!answered.ok) return void (failure.value = answered.reason)
  open.value = false
  store.commit("setStatusSnackbarMessage", `${answered.saved.address} is saved.`)
  await load()
}

const removing = ref(false)
const removeFailure = ref<string | null>(null)
const askRemove = () => {
  removeFailure.value = null
  removing.value = true
}
const remove = async () => {
  const gone = editing.value
  if (!gone || working.value) return
  working.value = true
  const answered = await removeAddress(gone.id)
  working.value = false
  if (!answered.ok) return void (removeFailure.value = answered.reason)
  removing.value = false
  open.value = false
  store.commit("setStatusSnackbarMessage", `${gone.address} is removed.`)
  await load()
}

onMounted(load)
</script>

<template>
  <management-page
    eyebrow="Mail"
    testid="sending-addresses"
    title="Sending addresses"
  >
    <template #lede>
      The addresses a written email can go out from. Security and payment emails always go out from the site's own address.
    </template>
    <template
      v-if="isAdmin"
      #actions
    >
      <cut-button
        testid="sending-addresses-add"
        tone="solid"
        @click="start(null)"
      >
        Add an address
      </cut-button>
    </template>

    <management-table
      class="sending__table"
      :columns="COLUMNS"
      :row-key="(one) => one.id"
      :row-testid="(one) => `sending-address-${one.id}`"
      :rows="addresses"
      :search-text="(one) => `${one.address} ${one.displayName} ${one.host}`"
      search-label="Search addresses"
      testid="sending-addresses-table"
    >
      <template
        v-if="loaded"
        #empty
      >
        <span data-testid="sending-addresses-empty">No addresses are added. Written emails go out from the site's own address.</span>
      </template>
      <template #address="{row}">
        <button
          v-if="isAdmin"
          class="mg-name sending__edit"
          :data-testid="`sending-address-edit-${row.id}`"
          type="button"
          @click="start(row)"
        >
          {{ row.address }}
        </button>
        <span
          v-else
          class="mg-name"
        >{{ row.address }}</span>
        <span class="mg-sub">{{ row.displayName }}</span>
        <span
          v-if="!row.loginKept"
          class="mg-why"
        >No login is kept, so nothing can be sent from it.</span>
      </template>
      <template #server="{row}">
        {{ serverOf(row) }}
      </template>
      <template #first="{row}">
        <state-mark
          v-if="row.isDefault"
          kind="in-sync"
        >
          Default
        </state-mark>
      </template>
      <template #phone="{row}">
        <management-row
          :meta="`${row.displayName} · ${serverOf(row)}`"
          :name="row.address"
          :testid="`sending-address-${row.id}`"
        >
          <state-mark
            v-if="row.isDefault"
            kind="in-sync"
          >
            Default
          </state-mark>
          <cut-button
            v-if="isAdmin"
            small
            @click="start(row)"
          >
            Edit
          </cut-button>
        </management-row>
      </template>
    </management-table>

    <modal-dialog
      cancel-testid="sending-address-cancel"
      :open="open"
      testid="sending-address-dialog"
      :title="editing ? `Edit ${editing.address}` : 'Add an address'"
      @update:open="open = $event"
    >
      <form
        id="sending-address-form"
        class="sending__form"
        @submit.prevent="save"
      >
        <form-field
          v-slot="field"
          label="Address"
          testid="sending-address-address"
        >
          <text-input
            v-model="form.address"
            :control-id="field.controlId"
            testid="sending-address-address-input"
            type="email"
          />
        </form-field>
        <form-field
          v-slot="field"
          hint="What a reader sees beside the address"
          label="Name"
          testid="sending-address-name"
        >
          <text-input
            v-model="form.displayName"
            :control-id="field.controlId"
            testid="sending-address-name-input"
          />
        </form-field>
        <div class="sending__pair">
          <form-field
            v-slot="field"
            label="SMTP server"
            testid="sending-address-host"
          >
            <text-input
              v-model="form.host"
              :control-id="field.controlId"
              testid="sending-address-host-input"
            />
          </form-field>
          <form-field
            v-slot="field"
            label="Port"
            testid="sending-address-port"
          >
            <count-input
              v-model="form.port"
              :control-id="field.controlId"
              testid="sending-address-port-input"
              :min="1"
            />
          </form-field>
        </div>
        <form-field
          label="Security"
          testid="sending-address-security"
        >
          <radio-group
            :model-value="form.security"
            :options="securities"
            testid="sending-address-security"
            @update:model-value="pickSecurity"
          />
        </form-field>
        <form-field
          v-slot="field"
          :hint="loginHint"
          label="Username"
          testid="sending-address-username"
        >
          <text-input
            v-model="form.username"
            :control-id="field.controlId"
            testid="sending-address-username-input"
          />
        </form-field>
        <form-field
          v-slot="field"
          hint="Kept in Vault and never shown again"
          label="Password"
          testid="sending-address-password"
        >
          <text-input
            v-model="form.password"
            :control-id="field.controlId"
            testid="sending-address-password-input"
            type="password"
          />
        </form-field>
        <check-box
          v-model="form.isDefault"
          label="Picked first when writing an email"
          testid="sending-address-default"
        />
        <p class="sending__note">
          The login is tried on the server before it is saved. Mail from an address on another domain only arrives when that domain allows this server to send for it.
        </p>
        <p
          v-if="failure"
          class="sending__failure"
          data-testid="sending-address-failure"
          role="alert"
        >
          {{ failure }}
        </p>
      </form>
      <template #footer>
        <cut-button
          v-if="editing"
          :disabled="working"
          testid="sending-address-remove"
          tone="danger"
          @click="askRemove"
        >
          Remove
        </cut-button>
        <cut-button
          :disabled="working || !filled"
          form="sending-address-form"
          submit
          testid="sending-address-save"
          tone="solid"
        >
          {{ working ? "Saving" : editing ? "Save" : "Add" }}
        </cut-button>
      </template>
    </modal-dialog>

    <confirm-dialog
      :failure="removeFailure"
      :open="removing"
      :question="`Remove ${editing?.address}? Its login is deleted from Vault, and emails still waiting to go out from it fail.`"
      testid="sending-address-remove-dialog"
      title="Remove the address"
      :working="working"
      @confirm="remove"
      @update:open="removing = $event"
    />
  </management-page>
</template>

<style scoped>
.sending__table {
  margin-top: 1.2rem;
}

.sending__edit {
  text-align: left;
}

.sending__form {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
}

.sending__pair {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 7rem;
  gap: 0.6rem;
}

.sending__note {
  font-size: 0.88rem;
  color: var(--color-ash);
}

.sending__failure {
  font-size: 0.88rem;
  color: var(--color-danger);
}
</style>
