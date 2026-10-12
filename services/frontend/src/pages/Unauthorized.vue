<template>
  <main class="site-main">
    <div
      class="unauthorized"
    >
      <h1 class="site-lead unauthorized__title">
        Not allowed
      </h1>
      <p class="site-lead">
        Your account doesn't have permission to access
        <strong>{{ serviceLabel }}</strong>.
      </p>
      <p class="unauthorized__hint">
        If you think this is a mistake, ask board to bump your role.
      </p>
      <div class="unauthorized__acts">
        <cut-button
          href="/myapps"
          tone="solid"
        >
          Back to My Apps
        </cut-button>
        <cut-button href="/">
          Home
        </cut-button>
      </div>
    </div>
  </main>
</template>

<script lang="ts">
import {defineComponent} from "vue"
import CutButton from "@/components/island/CutButton.vue"

const SERVICE_LABELS: Record<string, string> = {
  "traefik.esa-blueshell.nl": "the Traefik dashboard",
  "vault.esa-blueshell.nl": "Vault",
  "headlamp.esa-blueshell.nl": "Headlamp (Kubernetes dashboard)",
  "stalwart.esa-blueshell.nl": "the Stalwart mail admin",
}

export default defineComponent({
  name: "Unauthorized",
  components: {CutButton},
  computed: {
    serviceParam(): string {
      const raw = this.$route.query.service
      return Array.isArray(raw) ? (raw[0] ?? "") : (raw ?? "")
    },
    serviceLabel(): string {
      return SERVICE_LABELS[this.serviceParam] || this.serviceParam || "this service"
    },
  },
})
</script>

<style scoped>
.unauthorized__acts {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 0.75rem;
  margin-top: 2rem;
}

.unauthorized {
  max-width: 800px;
  margin: 64px auto 0;
  padding-inline: 16px;
  text-align: center;
}

.unauthorized__title {
  margin-bottom: 16px;
}

.unauthorized__hint {
  margin-top: 12px;
}
</style>
