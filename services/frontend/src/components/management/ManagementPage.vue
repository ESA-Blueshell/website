<template>
  <div
    class="mg-page"
    :data-testid="testid"
  >
    <back-bar
      v-if="back"
      :label="back.label"
      :testid="testid ? `${testid}-back` : undefined"
      :to="back.to"
    />
    <management-head
      :eyebrow="eyebrow"
      :title="title"
    >
      <template
        v-if="$slots.lede"
        #default
      >
        <slot name="lede" />
      </template>
      <template
        v-if="$slots.actions"
        #actions
      >
        <slot name="actions" />
      </template>
    </management-head>
    <div class="mg-page__body">
      <slot />
    </div>
  </div>
</template>

<script lang="ts" setup>
/* A Management page: the way back where it sits inside another, its head, and its body at the
   portal's margins. */
import BackBar from "@/components/island/BackBar.vue"
import ManagementHead from "@/components/management/ManagementHead.vue"

const {eyebrow, title, back = undefined, testid = undefined} = defineProps<{
  eyebrow: string
  title: string
  /** The page this one sits inside. */
  back?: {to: string; label: string}
  testid?: string
}>()
</script>

<style scoped>
.mg-page__body {
  padding: 0 2.4rem 3rem;
}

/* The bar keeps the portal's margin, where the island centres its own. */
.mg-page :deep(.back-bar__column) {
  max-width: none;
  padding-inline: 2.4rem;
}

@media (--phone) {
  .mg-page__body {
    padding: 0 1.1rem 2rem;
  }

  .mg-page :deep(.back-bar__column) {
    padding-inline: 1.1rem;
  }
}
</style>
