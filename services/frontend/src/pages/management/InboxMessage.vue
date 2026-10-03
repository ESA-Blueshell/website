<script lang="ts" setup>
/* One received message with its whole conversation, oldest first, and the board's answer below it:
   a reply in the site's template threaded with the conversation, or marking it handled. */
import {computed, onMounted, ref, watch} from "vue"
import {useRoute} from "vue-router"
import FormField from "@/components/island/FormField.vue"
import MarkdownEditor from "@/components/island/MarkdownEditor.vue"
import SearchPicker from "@/components/island/SearchPicker.vue"
import TaskLayout from "@/components/island/TaskLayout.vue"
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
} from "@/domains/mail"
import store from "@/plugins/store"
import {formatDateNoSeconds} from "@/utils/timestamps"

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
const replyPickerOptions = computed(() => replyOptions.value.map((one) => ({key: one, label: one})))

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
  <div
    class="conversation"
    data-testid="inbox-message"
  >
    <router-link
      class="conversation__back"
      to="/management/mail/inbox"
    >
      Inbox
    </router-link>

    <p
      v-if="loaded && !conversation"
      class="conversation__note"
      data-testid="inbox-message-missing"
    >
      There is no message {{ id }} in the inbox.
    </p>

    <template v-if="conversation && message">
      <header>
        <p
          class="conversation__eyebrow"
          data-testid="inbox-message-state"
        >
          Inbox · {{ inboxStateWord(message) }}
        </p>
        <h1 class="conversation__title">
          {{ message.subject || "(no subject)" }}
        </h1>
        <p class="conversation__note">
          {{ conversationSummary(conversation) }}
        </p>
      </header>

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
              <span>{{ formatDateNoSeconds(item.at) }}</span>
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
            label="Replies go to"
            testid="inbox-reply-to"
          >
            <template #default="{controlId, labelId}">
              <search-picker
                :control-id="controlId"
                :labelled-by="labelId"
                :options="replyPickerOptions"
                :selected-key="replyTo"
                testid-prefix="inbox-reply-to-picker"
                @pick="(key: string) => replyTo = key"
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
            <button
              class="conversation__action conversation__action--main"
              data-testid="inbox-send-reply"
              :disabled="acting || reply.trim() === ''"
              type="submit"
            >
              Send the reply
            </button>
            <button
              v-if="message.state === 'NEW'"
              class="conversation__action"
              data-testid="inbox-mark-handled"
              :disabled="acting"
              type="button"
              @click="act('handled')"
            >
              Mark handled without a reply
            </button>
          </div>
          <p
            v-if="message.handledAt"
            class="conversation__note"
            data-testid="inbox-message-handled"
          >
            {{ inboxStateWord(message) }} {{ formatDateNoSeconds(message.handledAt) }}<template v-if="message.handledByName">
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
              <span>{{ formatDateNoSeconds(mail.at) }}</span>
            </li>
          </ul>
          <p v-else>
            None.
          </p>
        </template>
      </task-layout>
    </template>
  </div>
</template>

<style scoped>
.conversation {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  max-width: 76rem;
  padding: 2rem 2.4rem 3rem;
}

.conversation__back {
  align-self: flex-start;
  font-size: 0.84rem;
  color: var(--color-brand);
}

.conversation__eyebrow,
.conversation__aside-title {
  margin: 0;
  font-size: 11px;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  color: var(--color-eyebrow, var(--color-ash));
}

.conversation__title {
  margin: 0;
  font-family: var(--font-display);
  font-size: clamp(1.4rem, 3vw, 2rem);
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

.conversation__action {
  padding: 0.45rem 0.9rem;
  border: 1px solid var(--color-hairline);
  background: none;
  font: inherit;
  font-size: 0.86rem;
  color: var(--color-chalk);
  cursor: pointer;
}

.conversation__action--main {
  border-color: var(--color-brand);
  color: var(--color-brand);
}

.conversation__action:disabled {
  opacity: 0.45;
  cursor: default;
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

@media (max-width: 839px) {
  .conversation {
    padding: 1.2rem 1.1rem 2rem;
  }
}
</style>
