<template>
  <top-banner
    title="Infrastructure"
    height="200px"
    m-height="150px"
  />
  <v-container class="py-8">
    <v-row>
      <v-col
        v-for="service in services"
        :key="service.name"
        cols="12"
        sm="6"
        lg="3"
      >
        <v-card
          :href="service.url"
          class="infra-card h-100"
          rel="noopener noreferrer"
          target="_blank"
          variant="outlined"
        >
          <v-card-item>
            <template #prepend>
              <v-icon
                :icon="service.icon"
                color="primary"
                size="32"
              />
            </template>
            <v-card-title>{{ service.name }}</v-card-title>
          </v-card-item>
          <v-card-text class="text-medium-emphasis">
            {{ service.description }}
          </v-card-text>
          <v-card-actions>
            <v-spacer />
            <v-btn
              :href="service.url"
              color="primary"
              rel="noopener noreferrer"
              target="_blank"
              variant="tonal"
            >
              Open
              <v-icon
                end
                icon="mdi-open-in-new"
              />
            </v-btn>
          </v-card-actions>
        </v-card>
      </v-col>
    </v-row>
  </v-container>
</template>

<script lang="ts" setup>
import TopBanner from "@/components/common/banners/TopBanner.vue"

defineOptions({name: "InfraPortalPage"})

const baseDomain = globalThis.location.hostname

const services = [
  {
    name: "Traefik",
    icon: "mdi-routes",
    description: "Edge proxy — routing rules, TLS certificates, and real-time request metrics.",
    url: `https://traefik.${baseDomain}`,
  },
  {
    name: "Grafana",
    icon: "mdi-chart-line",
    description: "Dashboards and alerting — application metrics, logs, and alert rules.",
    url: `https://grafana.${baseDomain}`,
  },
  {
    name: "Vault",
    icon: "mdi-lock",
    description: "Secrets management — encrypted storage for API keys and deployment secrets.",
    url: `https://vault.${baseDomain}`,
  },
  {
    name: "Status",
    icon: "mdi-check-network",
    description: "Uptime monitoring — service availability and response time history.",
    url: `https://status.${baseDomain}`,
  },
]
</script>

<style lang="scss" scoped>
.infra-card {
  transition: border-color 0.2s ease;

  &:hover {
    border-color: rgb(var(--v-theme-primary));
  }
}
</style>
