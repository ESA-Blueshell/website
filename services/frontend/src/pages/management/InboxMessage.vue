<script lang="ts" setup>
/* One received message with its whole conversation, oldest first, and the board's answer below it:
   a reply in the site's template threaded with the conversation, or marking it handled. */
import {computed, onMounted, ref, watch} from "vue"
import {useRoute} from "vue-router"
import CutButton from "@/components/island/CutButton.vue"
import FormField from "@/components/island/FormField.vue"
import MarkdownEditor from "@/components/island/MarkdownEditor.vue"
import TaskLayout from "@/components/island/TaskLayout.vue"
import ManagementPage from "@/components/management/ManagementPage.vue"
import {
  type Conversation,
  ConversationKind,
  authorOf,
  conversationSummary,
  inboxStateWord,
  listReplyTo,
  markHandled,
  readConversation,
  sendReply,
  ReplyToPicker,
} from "@/domains/mail"
import store from "@/plugins/store"
import {formatMoment} from "@/utils/timestamps"

defineOptions({name: "InboxMessagePage"})

const route = useRoute()
const id = computed(() => Number(route.params.id))

const conversation = ref<Conversation | null>(null)
const loaded = ref(false)
const replyOptions = ref<string[]>([])
const reply = ref("")
const replyTo = ref<string | null>(null)
const acting = ref(false)
const failure = ref<string | null>(null)

const message = computed(() => conversation.value?.message ?? null)
const sender = computed(() => message.value && (message.value.senderName ?? message.value.fromName ?? message.value.fromAddress))

const load = async () => {
  loaded.value = false
  conversation.value = await readConversation(id.value)
  loaded.value = true
}

const act = async (how: "reply" | "handled") => {
  if (acting.value || (how === "reply" && reply.value.trim() === "")) return
  acting.value = true
  const answered = how === "reply" ? await sendReply(id.value, reply.value, replyTo.value) : await markHandled(id.value)
  acting.value = false
  if (!answered.ok) {
    failure.value = answered.reason
    return
  }
  failure.value = null
  conversation.value = answered.saved
  if (how === "reply") {
    reply.value = ""
    store.commit("setStatusSnackbarMessage", "The reply is on its way. It shows in Sent.")
  }
}

watch(id, load)
onMounted(async () => {
  const [, replies] = await Promise.all([load(), listReplyTo()])
  replyOptions.value = replies
  replyTo.value = replies[0] ?? null
})
</script>

<template>
  <management-page
    v-if="loaded && !conversation"
    :back="{to: '/management/mail/inbox', label: 'Inbox'}"
    eyebrow="Mail"
    testid="inbox-message"
    title="No such message"
  >
    <p
      class="conversation__note"
      data-testid="inbox-message-missing"
    >
      There is no message {{ id }} in the inbox.
    </p>
  </management-page>
  <management-page
    v-else-if="conversation && message"
    :back="{to: '/management/mail/inbox', label: 'Inbox'}"
    :eyebrow="`Inbox · ${inboxStateWord(message)}`"
    testid="inbox-message"
    :title="message.subject || '(no subject)'"
  >
    <template #lede>
      {{ conversationSummary(conversation) }}
    </template>

    <task-layout>
      <ol
        class="conversation__items"
        data-testid="inbox-message-items"
      >
        <li
          v-for="(item, index) in conversation.items"
          :key="index"
          class="conversation__item"
          :class="{
            'conversation__item--site': item.kind !== ConversationKind.RECEIVED,
            'conversation__item--current': item.inboxMessageId === message.id && item.kind === ConversationKind.RECEIVED,
          }"
          :data-testid="`inbox-message-item-${index}`"
        >
          <p class="conversation__meta">
            <span><b class="conversation__who">{{ authorOf(item, message) }}</b><template v-if="item.subject"> · {{ item.subject }}</template></span>
            <span>{{ formatMoment(item.at) }}</span>
          </p>
          <p
            v-if="item.body"
            class="conversation__body"
          >
            {{ item.body }}
          </p>
          <router-link
            v-if="item.emailId"
            class="conversation__link"
            :to="`/management/mail/sent/${item.emailId}`"
          >
            Open the email
          </router-link>
        </li>
      </ol>

      <form
        class="conversation__form"
        @submit.prevent="act('reply')"
      >
        <form-field
          label="Reply"
          testid="inbox-reply"
        >
          <template #default="{labelId}">
            <markdown-editor
              v-model="reply"
              :labelled-by="labelId"
              min-height="14rem"
              testid="inbox-reply-editor"
            />
          </template>
        </form-field>
        <form-field
          label="Reply-to"
          testid="inbox-reply-to"
        >
          <template #default="{controlId, labelId}">
            <reply-to-picker
              v-model="replyTo"
              :control-id="controlId"
              :labelled-by="labelId"
              :offered="replyOptions"
              testid-prefix="inbox-reply-to-picker"
            />
          </template>
        </form-field>
        <p
          v-if="failure"
          class="conversation__failure"
          data-testid="inbox-message-failure"
          role="alert"
        >
          {{ failure }}
        </p>
        <div class="conversation__acts">
          <cut-button
            :disabled="acting || reply.trim() === ''"
            submit
            testid="inbox-send-reply"
            tone="solid"
          >
            Send the reply
          </cut-button>
          <cut-button
            v-if="message.state === 'NEW'"
            :disabled="acting"
            testid="inbox-mark-handled"
            @click="act('handled')"
          >
            Mark handled without a reply
          </cut-button>
        </div>
        <p
          v-if="message.handledAt"
          class="conversation__note"
          data-testid="inbox-message-handled"
        >
          {{ inboxStateWord(message) }} {{ formatMoment(message.handledAt) }}<template v-if="message.handledByName">
            by {{ message.handledByName }}
          </template>
        </p>
      </form>

      <template #aside>
        <p class="conversation__aside-title">
          From
        </p>
        <p data-testid="inbox-message-from">
          {{ sender }}<br>{{ message.fromAddress }}
          <template v-if="message.senderUserId">
            <br>
            <router-link :to="`/management/users/${message.senderUserId}`">
              Open {{ sender }}
            </router-link>
          </template>
        </p>
        <p class="conversation__aside-title">
          Earlier mail with {{ sender }}
        </p>
        <ul
          v-if="conversation.earlier.length"
          class="conversation__earlier"
          data-testid="inbox-message-earlier"
        >
          <li
            v-for="(mail, index) in conversation.earlier"
            :key="index"
          >
            <router-link :to="mail.emailId ? `/management/mail/sent/${mail.emailId}` : `/management/mail/inbox/${mail.inboxMessageId}`">
              {{ mail.subject || "(no subject)" }}
            </router-link>
            <span>{{ formatMoment(mail.at) }}</span>
          </li>
        </ul>
        <p v-else>
          None.
        </p>
      </template>
    </task-layout>
  </management-page>
</template>

<style scoped>
.conversation__eyebrow,
.conversation__aside-title {
  margin: 0;
  font-size: 11px;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  color: var(--color-eyebrow, var(--color-ash));
}

.conversation__note {
  margin: 0;
  color: var(--color-ash);
}

.conversation__failure {
  margin: 0;
  color: var(--color-error, #e5484d);
}

.conversation__items {
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
  margin: 0;
  padding: 0;
  list-style: none;
}

.conversation__item {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  padding: 1rem 1.2rem;
  background-color: var(--band-ground);
  font-size: 0.95rem;
  line-height: 1.6;
}

.conversation__item--site {
  box-shadow: inset 3px 0 0 var(--color-brand);
  background: color-mix(in oklab, var(--color-brand) 8%, transparent);
}

.conversation__meta {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  margin: 0;
  font-size: 0.84rem;
  color: var(--color-ash);
}

.conversation__who {
  color: var(--color-chalk);
}

.conversation__body {
  margin: 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.conversation__link {
  font-size: 0.84rem;
  color: var(--color-brand);
}

.conversation__form {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  margin-top: 1rem;
}

.conversation__acts {
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem;
}

.conversation__earlier {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  margin: 0;
  padding: 0;
  list-style: none;
}

.conversation__earlier li {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
}
</style>
