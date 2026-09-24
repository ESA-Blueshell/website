<template>
  <div data-testid="backup-codes">
    <p class="mb-3">
      Each code works once, in place of your authenticator app. Keep them somewhere other than your phone;
      they are shown this once.
    </p>
    <v-sheet
      class="backup-codes pa-4 mb-3"
      rounded
      border
    >
      <code
        v-for="code in codes"
        :key="code"
        data-testid="backup-code"
      >{{ code }}</code>
    </v-sheet>
    <div class="d-flex ga-2">
      <v-btn
        data-testid="backup-codes-copy-btn"
        prepend-icon="mdi-content-copy"
        variant="outlined"
        @click="copy"
      >
        {{ copied ? "Copied" : "Copy" }}
      </v-btn>
      <v-btn
        :href="download"
        data-testid="backup-codes-download-btn"
        download="blueshell-backup-codes.txt"
        prepend-icon="mdi-download"
        variant="outlined"
      >
        Download
      </v-btn>
    </div>
  </div>
</template>

<script lang="ts" setup>
import {computed, ref} from "vue"

const props = defineProps<{ codes: string[] }>()

const copied = ref(false)
const text = computed(() => `ESA Blueshell backup codes\n\n${props.codes.join("\n")}\n`)
const download = computed(() => `data:text/plain;charset=utf-8,${encodeURIComponent(text.value)}`)

const copy = async () => {
  await navigator.clipboard?.writeText(text.value)
  copied.value = true
}
</script>

<style scoped>
.backup-codes {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.5rem 1.5rem;
  font-size: 1.05rem;
}
</style>
