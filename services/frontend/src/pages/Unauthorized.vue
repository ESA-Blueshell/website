<template>
  <v-main>
    <div
      class="text-body-1 mx-auto mt-16 text-center"
      style="max-width: 800px;"
    >
      <h1 class="text-h4 text-sm-h3 mb-4">
        Not allowed
      </h1>
      <p class="text-h6 text-sm-h5">
        Your account doesn't have permission to access
        <strong>{{ serviceLabel }}</strong>.
      </p>
      <p class="mt-3 text-body-1">
        If you think this is a mistake, ask board to bump your role.
      </p>
      <div class="mt-8 d-flex justify-center ga-3 flex-wrap">
        <v-btn
          color="primary"
          variant="elevated"
          to="/myapps"
        >
          Back to My Apps
        </v-btn>
        <v-btn
          variant="outlined"
          to="/"
        >
          Home
        </v-btn>
      </div>
    </div>
  </v-main>
</template>

<script lang="ts">
import {defineComponent} from "vue"

const SERVICE_LABELS: Record<string, string> = {
  "traefik.esa-blueshell.nl": "the Traefik dashboard",
  "vault.esa-blueshell.nl": "Vault",
  "headlamp.esa-blueshell.nl": "Headlamp (Kubernetes dashboard)",
  "stalwart.esa-blueshell.nl": "the Stalwart mail admin",
}

export default defineComponent({
  name: "Unauthorized",
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
