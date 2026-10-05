<template>
  <v-main>
    <div class="apps">
      <h1 class="apps__title">
        My Apps
      </h1>

      <p
        v-if="loading"
        class="apps__said"
      >
        Loading
      </p>

      <ul
        v-else-if="services.length > 0"
        class="apps__grid"
      >
        <li
          v-for="service in services"
          :key="service.id"
        >
          <a
            class="apps__tile"
            :href="service.url"
            rel="noopener noreferrer"
            target="_blank"
          >
            <img
              alt=""
              class="apps__icon"
              height="48"
              :src="service.iconUrl"
              width="48"
            >
            <span class="apps__name">{{ service.name }}</span>
            <span class="apps__about">{{ service.description }}</span>
          </a>
        </li>
      </ul>

      <p
        v-else
        class="apps__said"
      >
        No services available.
      </p>
    </div>
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

<style scoped>
.apps {
  max-width: 72rem;
  margin: 0 auto;
  padding: 2.5rem 1rem;
}

.apps__title {
  margin: 0 0 1.5rem;
  font-family: "Shellhouse One", sans-serif;
  font-size: 2rem;
  text-transform: uppercase;
}

.apps__said {
  color: var(--color-ash);
}

.apps__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(14rem, 1fr));
  gap: 1rem;
  margin: 0;
  padding: 0;
  list-style: none;
}

.apps__tile {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.3rem;
  min-height: 10rem;
  padding: 1rem;
  text-align: center;
  text-decoration: none;
  color: inherit;
  background-color: var(--color-surface);
  border-top: 3px solid transparent;
}

.apps__tile:hover,
.apps__tile:focus-visible {
  border-top-color: var(--color-brand);
}

.apps__icon {
  margin-bottom: 0.5rem;
  object-fit: contain;
}

.apps__name {
  font-weight: 600;
}

.apps__about {
  font-size: 0.85rem;
  color: var(--color-ash);
}
</style>
