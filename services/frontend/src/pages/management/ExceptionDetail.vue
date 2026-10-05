<script lang="ts" setup>
/* One recorded fault: where it was thrown, how often, its latest message and trace, and what it
   concerned. Resolving it holds until it fires again. */
import {computed, onMounted, ref} from "vue"
import {useRoute} from "vue-router"
import CutButton from "@/components/island/CutButton.vue"
import StateMark from "@/components/island/StateMark.vue"
import ListHead from "@/components/management/ListHead.vue"
import ManagementPage from "@/components/management/ManagementPage.vue"
import PairList from "@/components/management/PairList.vue"
import {type RecordedException, concernLink, loadException, markResolved, shortType} from "@/domains/exceptions"
import store from "@/plugins/store"
import {formatDate} from "@/utils/timestamps"

defineOptions({name: "ExceptionDetailPage"})

const route = useRoute()
const id = computed(() => Number(route.params.id))

const fault = ref<RecordedException | null>(null)
const loaded = ref(false)

const load = async () => {
  fault.value = await loadException(id.value)
  loaded.value = true
}

const facts = computed(() => {
  const one = fault.value
  if (!one) return []
  return [
    {label: "Thrown at", value: one.thrownAt},
    {label: "Times", value: String(one.occurrences)},
    {label: "First seen", value: formatDate(one.firstSeenAt)},
    {label: "Last seen", value: formatDate(one.lastSeenAt)},
    ...(one.resolvedAt ? [{label: "Resolved", value: formatDate(one.resolvedAt)}] : []),
  ]
})

const resolve = async () => {
  const resolved = await markResolved(id.value)
  if (!resolved.ok) {
    store.commit("setStatusSnackbarMessage", resolved.reason)
    return
  }
  await load()
}

onMounted(load)
</script>

<template>
  <management-page
    v-if="loaded && !fault"
    :back="{to: '/management/exceptions', label: 'Exceptions'}"
    eyebrow="System"
    testid="exception-detail"
    title="No such exception"
  >
    <p
      class="exception__note"
      data-testid="exception-detail-missing"
    >
      There is no exception {{ id }}.
    </p>
  </management-page>
  <management-page
    v-else-if="fault"
    :back="{to: '/management/exceptions', label: 'Exceptions'}"
    eyebrow="System · Exception"
    testid="exception-detail"
    :title="shortType(fault.exceptionType)"
  >
    <template #lede>
      {{ fault.exceptionType }}
    </template>
    <template
      v-if="!fault.resolvedAt"
      #actions
    >
      <cut-button
        testid="exception-resolve"
        @click="resolve"
      >
        Mark resolved
      </cut-button>
    </template>

    <p class="exception__state">
      <state-mark :kind="fault.resolvedAt ? 'in-sync' : 'extra'">
        {{ fault.resolvedAt ? "Resolved" : "Open" }}
      </state-mark>
    </p>
    <pair-list
      :pairs="facts"
      testid="exception-facts"
    />

    <list-head title="What it concerned last" />
    <p
      class="exception__line"
      data-testid="exception-concern"
    >
      {{ fault.latestSource === "JOB" ? "The job" : "The request" }}
      <router-link
        v-if="concernLink(fault)"
        class="exception__link"
        data-testid="exception-concern-link"
        :to="concernLink(fault) ?? ''"
      >
        {{ fault.latestConcern }}
      </router-link>
      <span v-else>{{ fault.latestConcern }}</span>
    </p>

    <list-head title="Latest message" />
    <p
      class="exception__line"
      data-testid="exception-message"
    >
      {{ fault.latestMessage || "It carried no message." }}
    </p>
    <pre
      v-if="fault.latestStackTrace"
      class="exception__trace"
      data-testid="exception-stacktrace"
    >{{ fault.latestStackTrace }}</pre>
  </management-page>
</template>

<style scoped>
.exception__state {
  padding: 1rem 0 0.8rem;
}

.exception__line {
  margin-bottom: 0.8rem;
  font-size: 0.92rem;
}

.exception__link {
  color: var(--color-brand-ink);
  text-decoration: underline;
  text-underline-offset: 3px;
}

.exception__note {
  margin-top: 1.4rem;
  color: var(--color-ash);
}

.exception__trace {
  max-height: 28rem;
  overflow: auto;
  padding: 1rem 1.2rem;
  font-family: ui-monospace, monospace;
  font-size: 0.78rem;
  line-height: 1.5;
  white-space: pre;
  color: var(--color-ash);
  background-color: var(--color-pit);
  box-shadow: inset 0 0 0 1px var(--color-hairline);
}
</style>
