<template>
  <nav
    aria-label="Pages"
    class="pager"
    :data-testid="testid"
  >
    <span class="pager__said">{{ label }}</span>
    <cut-button
      :disabled="page <= 1"
      small
      :testid="testid ? `${testid}-previous` : undefined"
      tone="quiet"
      @click="page -= 1"
    >
      Previous
    </cut-button>
    <cut-button
      :disabled="page >= totalPages"
      small
      :testid="testid ? `${testid}-next` : undefined"
      tone="quiet"
      @click="page += 1"
    >
      Next
    </cut-button>
  </nav>
</template>

<script lang="ts" setup>
/* Under a table that is read a page at a time: which rows these are, and the way to the pages
   either side. */
import CutButton from "@/components/island/CutButton.vue"

const {totalPages, label, testid = undefined} = defineProps<{
  totalPages: number
  /** Which rows are shown, such as "1 to 50 of 312". */
  label: string
  testid?: string
}>()

const page = defineModel<number>("page", {required: true})
</script>

<style scoped>
.pager {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 0.5rem;
  padding-top: 0.8rem;
  font-size: 0.85rem;
  color: var(--color-ash);
}

.pager__said {
  margin-right: auto;
}
</style>
