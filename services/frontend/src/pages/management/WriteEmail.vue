<script lang="ts" setup>
/* Writing an email in the association's template: to groups, roles and people in one picker, a
   subject, the message in the site's editor and where replies go. The preview is rendered by the
   api the way the send renders it. */
import {vFirstField} from "@/utils/firstField"
import {computed, onBeforeUnmount, onMounted, ref, watch} from "vue"
import {useRouter} from "vue-router"
import ChipPicker from "@/components/island/ChipPicker.vue"
import CutButton from "@/components/island/CutButton.vue"
import FormField from "@/components/island/FormField.vue"
import MarkdownEditor from "@/components/island/MarkdownEditor.vue"
import SearchPicker from "@/components/island/SearchPicker.vue"
import TextInput from "@/components/island/TextInput.vue"
import ListHead from "@/components/management/ListHead.vue"
import ManagementPage from "@/components/management/ManagementPage.vue"
import {SITE_SENDER, fromOptions, loadSendingAddresses, renderWritten} from "@/domains/emails"
import {
  type Addressee,
  AddresseeKind,
  type Audience,
  type ReachResponse,
  addresseeKey,
  addresseeOf,
  listAudiences,
  listReplyTo,
  readReach,
  Role,
  sendTest,
  sendWritten,
  ReplyToPicker,
} from "@/domains/mail"
import {type UserDetailResponse, listUsers} from "@/domains/user"
import store from "@/plugins/store"

defineOptions({name: "WriteEmailPage"})

const PREVIEW_DELAY = 500
// Roles a person holds on the site; anonymous and the system account are no audience.
const ROLES = (Object.values(Role) as Role[]).filter((one) => one !== Role.ANONYMOUS && one !== Role.SYSTEM)

const router = useRouter()
const audiences = ref<Audience[]>([])
const people = ref<UserDetailResponse[]>([])
const replyOptions = ref<string[]>([])
const to = ref<Addressee[]>([])
const subject = ref("")
const message = ref("")
const replyTo = ref<string | null>(null)
const senders = ref<ReturnType<typeof fromOptions>["options"]>([])
const from = ref(SITE_SENDER)
const reach = ref<ReachResponse | null>(null)
const preview = ref<string | null>(null)
const sending = ref(false)
const said = ref<string | null>(null)
const failure = ref<string | null>(null)
let previewTimer: ReturnType<typeof setTimeout> | undefined

const roleLabel = (role: Role) => `@${role.charAt(0)}${role.slice(1).toLowerCase()}`

const options = computed(() => [
  ...audiences.value.map((one) => ({key: addresseeKey({kind: AddresseeKind.COHORT, id: one.key}), label: one.label, note: "Group"})),
  ...ROLES.map((one) => ({key: addresseeKey({kind: AddresseeKind.ROLE, id: one}), label: roleLabel(one), note: "Role"})),
  ...people.value.map((one) => ({key: addresseeKey({kind: AddresseeKind.PERSON, id: String(one.id)}), label: one.fullName, note: `@${one.username}`})),
])
const chosen = computed(() => to.value.map((one) => options.value.find((option) => option.key === addresseeKey(one))
  ?? {key: addresseeKey(one), label: one.id}))
const chosenKeys = computed(() => new Set(chosen.value.map((one) => one.key)))
const pickable = computed(() => options.value.filter((one) => !chosenKeys.value.has(one.key)))
const ready = computed(() => subject.value.trim() !== "" && message.value.trim() !== "")
const recipients = computed(() => reach.value?.recipients ?? 0)
const reachSaid = computed(() => {
  const reached = `Reaches ${recipients.value} ${recipients.value === 1 ? "person" : "people"}`
  const left = reach.value?.withoutEmail ?? 0
  return left === 0 ? `${reached}.` : `${reached}; ${left} without an email address ${left === 1 ? "is" : "are"} left out.`
})

const add = (keys: string[]) => {
  const picked = keys.map(addresseeOf).filter((one): one is Addressee => one !== null)
  to.value = [...to.value, ...picked]
}
const remove = (key: string) => {
  to.value = to.value.filter((one) => addresseeKey(one) !== key)
}

const body = () => ({
  to: to.value,
  subject: subject.value,
  message: message.value,
  replyTo: replyTo.value ?? undefined,
  from: from.value === SITE_SENDER ? undefined : Number(from.value),
})

const finish = (answered: {ok: true} | {ok: false; reason: string}, done: () => void) => {
  if (!answered.ok) {
    failure.value = answered.reason
    return
  }
  failure.value = null
  done()
}

const send = async () => {
  if (sending.value || !ready.value || recipients.value === 0) return
  sending.value = true
  const answered = await sendWritten(body())
  sending.value = false
  finish(answered, () => {
    store.commit("setStatusSnackbarMessage", `Queued ${recipients.value} email${recipients.value === 1 ? "" : "s"}.`)
    void router.push("/management/mail/sent")
  })
}

const test = async () => {
  if (sending.value || !ready.value) return
  sending.value = true
  const answered = await sendTest(body())
  sending.value = false
  finish(answered, () => {
    said.value = "A test is on its way to you."
  })
}

watch(to, async (now) => {
  reach.value = now.length === 0 ? null : await readReach(now)
}, {deep: true})

watch([subject, message], () => {
  clearTimeout(previewTimer)
  previewTimer = setTimeout(async () => {
    preview.value = ready.value ? (await renderWritten(subject.value, message.value))?.html ?? null : null
  }, PREVIEW_DELAY)
})

onMounted(async () => {
  const [groups, everyone, replies, addresses] = await Promise.all([listAudiences(), listUsers().catch(() => []), listReplyTo(), loadSendingAddresses()])
  const offered = fromOptions(addresses)
  senders.value = offered.options
  from.value = offered.start
  audiences.value = groups
  people.value = everyone
  replyOptions.value = replies
  replyTo.value = replies[0] ?? null
})

onBeforeUnmount(() => clearTimeout(previewTimer))
</script>

<template>
  <management-page
    :back="{to: '/management/mail/sent', label: 'Sent'}"
    eyebrow="Mail"
    testid="write-email"
    title="Write an email"
  >
    <template #lede>
      The email goes out from the site in the association's template.
    </template>

    <div class="write">
      <form
        v-first-field
        class="write__form"
        @submit.prevent="send"
      >
        <form-field
          label="To"
          testid="write-to"
        >
          <template #default="{controlId, labelId}">
            <chip-picker
              :chip-testid="(key: string) => `write-to-${key}`"
              :chosen="chosen"
              :control-id="controlId"
              empty-note="Everybody is on it already."
              :labelled-by="labelId"
              :options="pickable"
              placeholder="Add a group, a role or a person"
              :remove-label="(name: string) => `Stop writing to ${name}`"
              testid-prefix="write-to-picker"
              @add="add"
              @remove="remove"
            />
          </template>
        </form-field>
        <p
          v-if="reach"
          class="write__note"
          data-testid="write-reach"
        >
          {{ reachSaid }}
        </p>

        <form-field
          v-slot="field"
          label="Subject"
          testid="write-subject"
        >
          <text-input
            v-model="subject"
            :control-id="field.controlId"
          />
        </form-field>

        <form-field
          label="Message"
          testid="write-message"
        >
          <template #default="{labelId}">
            <markdown-editor
              v-model="message"
              :labelled-by="labelId"
              min-height="18rem"
              testid="write-message-editor"
            />
          </template>
        </form-field>

        <form-field
          v-if="senders.length > 1"
          label="From"
          testid="write-from"
        >
          <template #default="{controlId, labelId}">
            <search-picker
              :control-id="controlId"
              :labelled-by="labelId"
              :options="senders"
              :selected-key="from"
              testid-prefix="write-from-picker"
              @pick="(key: string) => from = key"
            />
          </template>
        </form-field>

        <form-field
          label="Reply-to"
          testid="write-reply-to"
        >
          <template #default="{controlId, labelId}">
            <reply-to-picker
              v-model="replyTo"
              :control-id="controlId"
              :labelled-by="labelId"
              :offered="replyOptions"
              testid-prefix="write-reply-to-picker"
            />
          </template>
        </form-field>

        <p
          v-if="failure"
          class="write__failure"
          data-testid="write-failure"
          role="alert"
        >
          {{ failure }}
        </p>
        <p
          v-if="said"
          class="write__note"
          data-testid="write-said"
          role="status"
        >
          {{ said }}
        </p>

        <div class="write__acts">
          <cut-button
            :disabled="sending || !ready || recipients === 0"
            submit
            testid="write-send"
            tone="solid"
          >
            Send to {{ recipients }} {{ recipients === 1 ? "person" : "people" }}
          </cut-button>
          <cut-button
            :disabled="sending || !ready"
            testid="write-test"
            @click="test"
          >
            Send a test to me
          </cut-button>
          <cut-button
            href="/management/mail/sent"
            tone="quiet"
          >
            Cancel
          </cut-button>
        </div>
      </form>

      <section class="write__preview">
        <list-head title="Preview" />
        <iframe
          v-if="preview"
          class="write__frame"
          data-testid="write-preview"
          sandbox=""
          :srcdoc="preview"
          title="The email as it will arrive"
        />
        <p
          v-else
          class="write__note"
          data-testid="write-preview-empty"
        >
          The preview shows once there is a subject and a message.
        </p>
      </section>
    </div>
  </management-page>
</template>

<style scoped>
.write {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 2.5rem;
  align-items: start;
  padding-top: 1.2rem;
}

.write__form {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
}

.write__note {
  color: var(--color-ash);
}

.write__failure {
  color: var(--color-danger);
}

.write__frame {
  width: 100%;
  min-height: 40rem;
  border: 0;
  background-color: var(--color-pit);
}

.write__acts {
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem;
  margin-top: 0.6rem;
}

@media (--phone) {
  .write {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
