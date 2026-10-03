<script lang="ts" setup>
/* What needs someone, each linked to where it is dealt with. An alert clears itself once its cause
   is gone; hiding one hides it for the reader only. */
import {onMounted, ref} from "vue"
import FoldOut from "@/components/island/FoldOut.vue"
import {type Alert, alertLink, alertTitle, hideAlert, showAlert, useAlerts} from "@/domains/alerts"
import store from "@/plugins/store"
import {formatDateNoSeconds} from "@/utils/timestamps"

defineOptions({name: "AlertListPage"})

const {shown, hidden, refresh} = useAlerts()
const loaded = ref(false)
const hiddenOpen = ref(false)

const toggle = async (alert: Alert) => {
  const answered = alert.hidden ? await showAlert(alert.key) : await hideAlert(alert.key)
  if (!answered.ok) {
    store.commit("setStatusSnackbarMessage", answered.reason)
    return
  }
  await refresh()
}

onMounted(async () => {
  await refresh()
  loaded.value = true
})
</script>

<template>
  <div
    class="alerts"
    data-testid="alert-list"
  >
    <h1 class="alerts__title">
      Alerts
    </h1>
    <p class="alerts__note">
      What needs someone, from every page in Management. Each alert clears itself once it is dealt with.
    </p>

    <p
      v-if="loaded && shown.length === 0"
      class="alerts__note"
      data-testid="alert-list-empty"
    >
      Nothing needs you right now.
    </p>

    <ul class="alerts__rows">
      <li
        v-for="alert in shown"
        :key="alert.key"
        class="alerts__row"
        :data-testid="`alert-${alert.key}`"
      >
        <span class="alerts__what">
          <strong>{{ alertTitle(alert) }}</strong>
          <span
            v-if="alert.since"
            class="alerts__when"
          >Since {{ formatDateNoSeconds(alert.since) }}</span>
        </span>
        <router-link
          class="alerts__go"
          :data-testid="`alert-open-${alert.key}`"
          :to="alertLink(alert)"
        >
          Deal with it
        </router-link>
        <button
          class="alerts__hide"
          :data-testid="`alert-hide-${alert.key}`"
          type="button"
          @click="toggle(alert)"
        >
          Hide for me
        </button>
      </li>
    </ul>

    <fold-out
      v-if="hidden.length > 0"
      v-model:open="hiddenOpen"
      :label="`Hidden for you (${hidden.length})`"
      testid="alert-hidden"
    >
      <ul class="alerts__rows">
        <li
          v-for="alert in hidden"
          :key="alert.key"
          class="alerts__row"
          :data-testid="`alert-${alert.key}`"
        >
          <span class="alerts__what">{{ alertTitle(alert) }}</span>
          <router-link
            class="alerts__go"
            :to="alertLink(alert)"
          >
            Deal with it
          </router-link>
          <button
            class="alerts__hide"
            :data-testid="`alert-show-${alert.key}`"
            type="button"
            @click="toggle(alert)"
          >
            Show again
          </button>
        </li>
      </ul>
    </fold-out>
  </div>
</template>

<style scoped>
.alerts {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  max-width: 60rem;
  padding: 2rem 2.4rem 3rem;
}

.alerts__title {
  margin: 0;
  font-family: var(--font-display);
  font-size: clamp(1.4rem, 3vw, 2rem);
}

.alerts__note {
  margin: 0;
  color: var(--color-ash);
}

.alerts__rows {
  display: flex;
  flex-direction: column;
  margin: 0;
  padding: 0;
  list-style: none;
  border-top: 1px solid var(--color-hairline);
}

.alerts__row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.6rem 1.2rem;
  padding: 0.9rem 0.4rem;
  border-bottom: 1px solid var(--color-hairline);
  border-left: 3px solid var(--color-warning);
}

.alerts__what {
  display: flex;
  flex: 1 1 18rem;
  flex-direction: column;
  gap: 0.2rem;
  padding-left: 0.6rem;
}

.alerts__when {
  font-size: 0.8rem;
  color: var(--color-ash);
}

.alerts__go {
  color: var(--color-brand);
  font-size: 0.88rem;
}

.alerts__hide {
  padding: 0.35rem 0.8rem;
  border: 1px solid var(--color-hairline);
  background: none;
  font: inherit;
  font-size: 0.82rem;
  color: var(--color-ash);
  cursor: pointer;
}

@media (max-width: 839px) {
  .alerts {
    padding: 1.2rem 1.1rem 2rem;
  }
}
</style>
