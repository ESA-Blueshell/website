<template>
  <v-main>
    <v-container class="my-10">
      <p class="text-h4 font-weight-light mb-6">
        My Apps
      </p>

      <v-row v-if="loading">
        <v-col
          v-for="n in 4"
          :key="n"
          cols="12"
          sm="6"
          md="3"
        >
          <v-skeleton-loader type="card" />
        </v-col>
      </v-row>

      <v-row v-else-if="services.length > 0">
        <v-col
          v-for="service in services"
          :key="service.id"
          cols="12"
          sm="6"
          md="3"
        >
          <v-card
            :href="service.url"
            target="_blank"
            rel="noopener noreferrer"
            hover
            class="d-flex flex-column align-center pa-4"
            height="160"
          >
            <v-img
              :src="service.iconUrl"
              width="48"
              height="48"
              class="mb-3"
            />
            <v-card-title class="text-body-1 font-weight-medium pa-0">
              {{ service.name }}
            </v-card-title>
            <v-card-subtitle class="text-caption pa-0 text-center mt-1">
              {{ service.description }}
            </v-card-subtitle>
          </v-card>
        </v-col>
      </v-row>

      <p
        v-else
        class="text-body-1 text-medium-emphasis"
      >
        No services available.
      </p>
    </v-container>
  </v-main>
</template>

<script setup lang="ts">
import {ref, onMounted} from "vue"

interface ServiceEntry {
  id: string
  name: string
  url: string
  iconUrl: string
  description: string
}

const services = ref<ServiceEntry[]>([])
const loading = ref(true)

onMounted(async () => {
  try {
    const res = await fetch("/api/me/services", {credentials: "include"})
    if (res.ok) {
      services.value = await res.json()
    }
  } finally {
    loading.value = false
  }
})
</script>
