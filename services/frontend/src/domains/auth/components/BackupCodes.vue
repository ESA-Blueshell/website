<template>
  <div
    class="backup-codes"
    data-testid="backup-codes"
  >
    <div class="backup-codes__grid">
      <span
        v-for="code in codes"
        :key="code"
        class="backup-codes__code"
        data-testid="backup-code"
      >{{ code }}</span>
    </div>
    <div class="backup-codes__acts">
      <cut-button
        download="blueshell-backup-codes.txt"
        :href="download"
        testid="backup-codes-download-btn"
      >
        Download
      </cut-button>
      <cut-button
        testid="backup-codes-copy-btn"
        tone="quiet"
        @click="copy"
      >
        {{ copied ? "Copied" : "Copy" }}
      </cut-button>
    </div>
  </div>
</template>

<script lang="ts" setup>
import {computed, ref} from "vue"
import CutButton from "@/components/island/CutButton.vue"

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
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.backup-codes__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 2px;
  max-width: 30rem;
}

.backup-codes__code {
  padding: 0.7rem 1rem;
  background-color: var(--band-ground);
  font-family: var(--font-bitmap);
  font-size: 1rem;
  letter-spacing: 0.04em;
  color: var(--color-chalk);
}

.backup-codes__acts {
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem;
}

@media (max-width: 767px) {
  .backup-codes__code {
    padding: 0.6rem 0.7rem;
    letter-spacing: 0;
  }
}
</style>
