<template>
  <div
    class="mg-page"
    :data-testid="testid"
  >
    <management-head
      :eyebrow="eyebrow"
      :testid="testid ? `${testid}-head` : undefined"
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
    <back-bar
      v-if="back"
      :label="back.label"
      :testid="testid ? `${testid}-back` : undefined"
      :to="back.to"
    />
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

/* The way back stands under the head. Its chevrons rest inside the margin, there being no
   band to their left to hang into, and reach back to the edge under the pointer as they do on
   the site. */
.mg-page :deep(.back-bar) {
  margin-top: 0.8rem;
  border-top: 1px solid var(--color-hairline);
}

.mg-page :deep(.back-bar__column > :first-child) {
  width: 30px;
  margin-left: 0;
}

.mg-page :deep(.back-bar:hover .back-bar__column > :first-child),
.mg-page :deep(.back-bar:focus-visible .back-bar__column > :first-child) {
  width: calc(30px + 2.4rem);
  margin-left: -2.4rem;
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
