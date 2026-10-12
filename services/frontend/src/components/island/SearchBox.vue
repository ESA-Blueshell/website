<script lang="ts" setup>
/* A page's one search box, labelled with what it searches, such as "Search jobs". */
const {label, testid = undefined} = defineProps<{
  label: string
  testid?: string
}>()

const value = defineModel<string>({default: ""})
</script>

<template>
  <span
    class="search-box"
    role="search"
  >
    <svg
      aria-hidden="true"
      class="search-box__glyph"
      fill="none"
      viewBox="0 0 16 16"
    >
      <circle
        cx="7"
        cy="7"
        r="4.6"
        stroke="currentColor"
        stroke-width="1.3"
      />
      <path
        d="m10.5 10.5 3.5 3.5"
        stroke="currentColor"
        stroke-width="1.3"
      />
    </svg>
    <input
      v-model="value"
      :aria-label="label"
      class="search-box__input"
      :data-testid="testid"
      :placeholder="label"
      type="search"
    >
  </span>
</template>

<style scoped>
.search-box {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  width: 19rem;
  max-width: 100%;
  padding: 0 0.9rem;
  color: var(--color-ash);
  background: color-mix(in oklab, var(--color-chalk) 5%, transparent);
  border-bottom: 1px solid color-mix(in oklab, var(--color-chalk) 30%, transparent);
}

.search-box:focus-within {
  border-bottom-color: var(--color-brand);
}

.search-box__glyph {
  width: 16px;
  height: 16px;
  flex: none;
}

.search-box__input {
  flex: 1 1 auto;
  min-width: 0;
  padding: 0.7rem 0;
  border: 0;
  background: none;
  color: var(--color-chalk);
  font: inherit;
  font-size: 0.95rem;
  outline: none;
}

.search-box__input::placeholder {
  color: var(--color-ash);
}
</style>
