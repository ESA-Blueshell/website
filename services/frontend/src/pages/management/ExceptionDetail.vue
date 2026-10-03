<script lang="ts" setup>
/* One recorded fault: where it was thrown, how often, its latest message and trace, and what it
   concerned. Resolving it holds until it fires again. */
import {computed, onMounted, ref} from "vue"
import {useRoute} from "vue-router"
import StateMark from "@/components/island/StateMark.vue"
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
  <div
    class="exception"
    data-testid="exception-detail"
  >
    <router-link
      class="exception__back"
      to="/management/exceptions"
    >
      Exceptions
    </router-link>

    <p
      v-if="loaded && !fault"
      class="exception__note"
      data-testid="exception-detail-missing"
    >
      There is no exception {{ id }}.
    </p>

    <template v-if="fault">
      <header class="exception__head">
        <h1 class="exception__title">
          {{ shortType(fault.exceptionType) }}
        </h1>
        <state-mark :kind="fault.resolvedAt ? 'in-step' : 'unreachable'">
          {{ fault.resolvedAt ? "Resolved" : "Open" }}
        </state-mark>
        <button
          v-if="!fault.resolvedAt"
          class="exception__action"
          data-testid="exception-resolve"
          type="button"
          @click="resolve"
        >
          Mark resolved
        </button>
      </header>

      <p class="exception__note">
        {{ fault.exceptionType }}
      </p>

      <dl
        class="exception__facts"
        data-testid="exception-facts"
      >
        <div
          v-for="fact in facts"
          :key="fact.label"
        >
          <dt>{{ fact.label }}</dt>
          <dd>{{ fact.value }}</dd>
        </div>
      </dl>

      <section class="exception__part">
        <h2>What it concerned last</h2>
        <p data-testid="exception-concern">
          {{ fault.latestSource === "JOB" ? "The job" : "The request" }}
          <router-link
            v-if="concernLink(fault)"
            data-testid="exception-concern-link"
            :to="concernLink(fault) ?? ''"
          >
            {{ fault.latestConcern }}
          </router-link>
          <span v-else>{{ fault.latestConcern }}</span>
        </p>
      </section>

      <section class="exception__part">
        <h2>Latest message</h2>
        <p data-testid="exception-message">
          {{ fault.latestMessage || "It carried no message." }}
        </p>
        <pre
          v-if="fault.latestStackTrace"
          class="exception__trace"
          data-testid="exception-stacktrace"
        >{{ fault.latestStackTrace }}</pre>
      </section>
    </template>
  </div>
</template>

<style scoped>
.exception {
  display: flex;
  flex-direction: column;
  gap: 1.2rem;
  max-width: 64rem;
  padding: 2rem 2.4rem 3rem;
}

.exception__back {
  align-self: flex-start;
  font-size: 0.84rem;
  color: var(--color-brand);
}

.exception__head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 1rem;
}

.exception__title {
  margin: 0;
  font-family: var(--font-display);
  font-size: clamp(1.3rem, 3vw, 1.9rem);
}

.exception__action {
  margin-left: auto;
  padding: 0.45rem 0.9rem;
  border: 1px solid var(--color-hairline);
  background: none;
  font: inherit;
  font-size: 0.86rem;
  color: var(--color-chalk);
  cursor: pointer;
}

.exception__note {
  margin: 0;
  color: var(--color-ash);
  overflow-wrap: anywhere;
}

.exception__facts {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(12rem, 1fr));
  gap: 0.9rem 1.5rem;
  margin: 0;
}

.exception__facts dt {
  font-size: 0.7rem;
  letter-spacing: 0.2em;
  text-transform: uppercase;
  color: var(--color-eyebrow);
}

.exception__facts dd {
  margin: 0.2rem 0 0;
  overflow-wrap: anywhere;
}

.exception__part h2 {
  margin: 0 0 0.5rem;
  font-size: 0.8rem;
  letter-spacing: 0.2em;
  text-transform: uppercase;
  color: var(--color-ash);
}

.exception__part p {
  margin: 0;
  overflow-wrap: anywhere;
}

.exception__part a {
  color: var(--color-brand);
}

.exception__trace {
  max-height: 32rem;
  margin-top: 0.8rem;
  overflow: auto;
  padding: 1rem;
  font-size: 0.78rem;
  white-space: pre;
  background: var(--band-ground);
  border: 1px solid var(--color-hairline);
}

@media (max-width: 839px) {
  .exception {
    padding: 1.2rem 1.1rem 2rem;
  }
}
</style>
