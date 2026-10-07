<script lang="ts" setup>
/* The addresses the site sends from and reads, kept by the board: whether each sends and is read by
   its last check, a test now, and the default that sends the site's own mail. Logins go to Vault
   and are never shown again. */
import {computed, onMounted, ref} from "vue"
import CheckBox from "@/components/island/CheckBox.vue"
import ConfirmDialog from "@/components/island/ConfirmDialog.vue"
import CountInput from "@/components/island/CountInput.vue"
import CutButton from "@/components/island/CutButton.vue"
import FormField from "@/components/island/FormField.vue"
import FormSection from "@/components/island/FormSection.vue"
import ModalDialog from "@/components/island/ModalDialog.vue"
import RadioGroup from "@/components/island/RadioGroup.vue"
import StateMark from "@/components/island/StateMark.vue"
import TextInput from "@/components/island/TextInput.vue"
import ManagementPage from "@/components/management/ManagementPage.vue"
import ManagementRow from "@/components/management/ManagementRow.vue"
import ManagementTable, {type TableColumn} from "@/components/management/ManagementTable.vue"
import {
  type MailProtocol,
  type SendingAddress,
  MailSecurity,
  addAddress,
  checkAddress,
  loadSendingAddresses,
  readState,
  removeAddress,
  saveAddress,
  securityLabel,
  sendState,
  usualPort,
} from "@/domains/emails"
import store from "@/plugins/store"
import {formatMoment} from "@/utils/timestamps"

defineOptions({name: "SendingAddressesPage"})

const addresses = ref<SendingAddress[]>([])
const loaded = ref(false)
const said = (message: string) => store.commit("setStatusSnackbarMessage", message)

const COLUMNS: TableColumn<SendingAddress>[] = [
  {key: "address", label: "Address", wrap: true, sortBy: (one) => one.address},
  {key: "sends", label: "Sends", sortBy: (one) => sendState(one).word},
  {key: "read", label: "Read", sortBy: (one) => readState(one).word},
  {key: "checked", label: "Checked", sortBy: (one) => one.checkedAt ?? ""},
  {key: "test", label: ""},
]

const securities = (Object.values(MailSecurity) as MailSecurity[]).map((one) => ({key: one, label: securityLabel(one)}))
const serverOf = (host: string, port: number, security: MailSecurity) => `${host}:${port} · ${securityLabel(security)}`

const load = async () => {
  addresses.value = await loadSendingAddresses()
  loaded.value = true
}

/** A test now: the address's servers tried with the login kept, and the row redrawn with what they said. */
const testing = ref<number | null>(null)
const test = async (one: SendingAddress) => {
  if (testing.value != null) return
  testing.value = one.id
  const answered = await checkAddress(one.id)
  testing.value = null
  if (!answered.ok) return said(answered.reason)
  addresses.value = addresses.value.map((row) => (row.id === one.id ? answered.saved : row))
  const sends = sendState(answered.saved).kind === "in-sync"
  const read = !answered.saved.imapHost || readState(answered.saved).kind === "in-sync"
  said(sends && read ? `${one.address} sends${answered.saved.imapHost ? " and is read" : ""}.` : `${one.address} has a problem; see its row.`)
}

/** The form, for a new address or the one being edited. */
const blank = () => ({
  address: "", displayName: "", host: "", port: "587", security: MailSecurity.STARTTLS as string,
  imapHost: "", imapPort: "993", imapSecurity: MailSecurity.SSL as string,
  username: "", password: "", isDefault: addresses.value.length === 0,
})
const editing = ref<SendingAddress | null>(null)
const open = ref(false)
const working = ref(false)
const failure = ref<string | null>(null)
const form = ref(blank())

const start = (one: SendingAddress | null) => {
  editing.value = one
  form.value = one
    ? {
        address: one.address, displayName: one.displayName, host: one.host, port: String(one.port), security: one.security,
        imapHost: one.imapHost ?? "", imapPort: String(one.imapPort ?? 993), imapSecurity: one.imapSecurity ?? MailSecurity.SSL,
        username: "", password: "", isDefault: one.isDefault,
      }
    : blank()
  failure.value = null
  open.value = true
}

// A port left at the usual one for the old security follows the new one.
const pickSecurity = (protocol: MailProtocol, security: string | null) => {
  if (!security) return
  const [portKey, securityKey] = protocol === "SMTP" ? (["port", "security"] as const) : (["imapPort", "imapSecurity"] as const)
  if (form.value[portKey] === String(usualPort(protocol, form.value[securityKey] as MailSecurity))) {
    form.value[portKey] = String(usualPort(protocol, security as MailSecurity))
  }
  form.value[securityKey] = security
}

// A kept login is only sent to the servers it was tried on, so a moved server asks for it again.
const moved = computed(() => {
  const was = editing.value
  if (was === null) return false
  const {host, port, security, imapHost, imapPort, imapSecurity} = form.value
  const smtpMoved = host.trim() !== was.host || Number(port) !== was.port || security !== was.security
  const reading = imapHost.trim() === "" ? null : `${imapHost.trim()}:${imapPort}:${imapSecurity}`
  const wasReading = was.imapHost ? `${was.imapHost}:${was.imapPort}:${was.imapSecurity}` : null
  return smtpMoved || reading !== wasReading
})
const loginHint = computed(() => {
  if (!editing.value) return ""
  return moved.value ? "A server changed, so fill in the login for it" : "Leave the username and password empty to keep the login there is"
})

const filled = computed(() => {
  const {address, displayName, host, port, imapHost, imapPort, username, password} = form.value
  const given = username !== "" && password !== ""
  const login = editing.value && !moved.value ? given || (username === "" && password === "") : given
  const reading = imapHost.trim() === "" || Number(imapPort) > 0
  return address.trim() !== "" && displayName.trim() !== "" && host.trim() !== "" && Number(port) > 0 && reading && login
})

const save = async () => {
  if (working.value || !filled.value) return
  working.value = true
  const {username, password, port, security, imapHost, imapPort, imapSecurity, ...rest} = form.value
  const read = imapHost.trim() !== ""
  const body = {
    ...rest,
    port: Number(port),
    security: security as MailSecurity,
    imapHost: read ? imapHost.trim() : undefined,
    imapPort: read ? Number(imapPort) : undefined,
    imapSecurity: read ? (imapSecurity as MailSecurity) : undefined,
    username: username || undefined,
    password: password || undefined,
  }
  const answered = editing.value ? await saveAddress(editing.value.id, body) : await addAddress(body)
  working.value = false
  if (!answered.ok) return void (failure.value = answered.reason)
  open.value = false
  said(`${answered.saved.address} is saved.`)
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
  said(`${gone.address} is removed.`)
  await load()
}

onMounted(load)
</script>

<template>
  <management-page
    eyebrow="Mail"
    testid="sending-addresses"
    title="Addresses"
  >
    <template #lede>
      The addresses the site sends from and reads. The default one sends the site's own mail, such as security and payment emails.
    </template>
    <template #actions>
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
      :search-text="(one) => `${one.address} ${one.displayName} ${one.host} ${one.imapHost ?? ''}`"
      search-label="Search addresses"
      testid="sending-addresses-table"
    >
      <template
        v-if="loaded"
        #empty
      >
        <span data-testid="sending-addresses-empty">No addresses are added. The site's mail goes out from its configured address.</span>
      </template>
      <template #address="{row}">
        <button
          class="mg-name sending__edit"
          :data-testid="`sending-address-edit-${row.id}`"
          type="button"
          @click="start(row)"
        >
          {{ row.address }}
        </button>
        <span class="mg-sub">{{ row.displayName }}{{ row.isDefault ? " · Default" : "" }}</span>
      </template>
      <template #sends="{row}">
        <state-mark :kind="sendState(row).kind">
          {{ sendState(row).word }}
        </state-mark>
        <span class="mg-sub">{{ serverOf(row.host, row.port, row.security) }}</span>
        <span
          v-if="sendState(row).why"
          class="mg-why"
        >{{ sendState(row).why }}</span>
      </template>
      <template #read="{row}">
        <state-mark :kind="readState(row).kind">
          {{ readState(row).word }}
        </state-mark>
        <span
          v-if="row.imapHost && row.imapPort && row.imapSecurity"
          class="mg-sub"
        >{{ serverOf(row.imapHost, row.imapPort, row.imapSecurity) }}</span>
        <span
          v-if="readState(row).why"
          class="mg-why"
        >{{ readState(row).why }}</span>
      </template>
      <template #checked="{row}">
        {{ row.checkedAt ? formatMoment(row.checkedAt) : "Never" }}
      </template>
      <template #test="{row}">
        <cut-button
          :disabled="testing != null"
          small
          :testid="`sending-address-test-${row.id}`"
          @click="test(row)"
        >
          {{ testing === row.id ? "Testing" : "Test" }}
        </cut-button>
      </template>
      <template #phone="{row}">
        <management-row
          :meta="`${row.displayName} · ${sendState(row).word} · ${readState(row).word}`"
          :name="row.address"
          :testid="`sending-address-${row.id}`"
        >
          <cut-button
            :disabled="testing != null"
            small
            @click="test(row)"
          >
            Test
          </cut-button>
          <cut-button
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

        <form-section title="Sending (SMTP)">
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
                :min="1"
                testid="sending-address-port-input"
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
              @update:model-value="pickSecurity('SMTP', $event)"
            />
          </form-field>
        </form-section>

        <form-section title="Reading (IMAP)">
          <div class="sending__pair">
            <form-field
              v-slot="field"
              hint="Leave empty where the address is not read"
              label="IMAP server"
              testid="sending-address-imap-host"
            >
              <text-input
                v-model="form.imapHost"
                :control-id="field.controlId"
                testid="sending-address-imap-host-input"
              />
            </form-field>
            <form-field
              v-slot="field"
              label="Port"
              testid="sending-address-imap-port"
            >
              <count-input
                v-model="form.imapPort"
                :control-id="field.controlId"
                :min="1"
                testid="sending-address-imap-port-input"
              />
            </form-field>
          </div>
          <form-field
            label="Security"
            testid="sending-address-imap-security"
          >
            <radio-group
              :model-value="form.imapSecurity"
              :options="securities"
              testid="sending-address-imap-security"
              @update:model-value="pickSecurity('IMAP', $event)"
            />
          </form-field>
        </form-section>

        <form-section title="Login">
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
            hint="Used for SMTP and IMAP, kept in Vault and never shown again"
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
        </form-section>

        <check-box
          v-model="form.isDefault"
          label="Default: sends the site's own mail and is picked first when writing"
          testid="sending-address-default"
        />
        <p class="sending__note">
          The login is tried on both servers before it is saved. Mail from an address on another domain only arrives when that domain allows this server to send for it.
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
