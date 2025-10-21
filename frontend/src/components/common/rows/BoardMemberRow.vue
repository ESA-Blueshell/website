<template>
  <!-- Image + text -->
  <div
    v-if="member.image"
    :class="[
      'member-card',
      'bg-surface',
      'rounded-lg',
      'elevation-1',
      'mx-auto',
      reverse && 'is-right'
    ]"
  >
    <div class="member-photo">
      <v-img
        :src="member.image"
        class="w-100 h-100"
      />
    </div>

    <!-- remove px-6; side padding now controlled in CSS -->
    <div class="member-info">
      <p :class="['text-h4','text-md-h3', reverse && 'text-right']">
        {{ member.name }}
      </p>
      <p :class="['text-subtitle-1','mt-1','mb-4', reverse && 'text-right']">
        {{ member.title }}
      </p>
      <p :class="[reverse && 'text-right']">
        {{ member.description }}
      </p>
    </div>
  </div>

  <!-- Text-only fallback -->
  <div
    v-else
    class="mx-auto px-6 bg-surface rounded-lg elevation-1"
  >
    <p :class="['text-h4','text-md-h3', reverse && 'text-right']">
      {{ member.name }}
    </p>
    <p :class="['text-subtitle-1','mt-1','mb-4', reverse && 'text-right']">
      {{ member.title }}
    </p>
    <p :class="[reverse && 'text-right']">
      {{ member.description }}
    </p>
  </div>
</template>

<script setup lang="ts">
interface Member {
  name: string
  title: string
  description?: string
  image?: string
}

defineProps<{
  member: Member
  reverse?: boolean
}>()
</script>

<style scoped lang="scss">
.member-card {
  --img-w: clamp(220px, 32vw, 420px);
  --gap: clamp(8px, 2vw, 20px);
  --fade: clamp(24px, 5vw, 72px);

  display: grid;
  grid-template-columns: var(--img-w) 1fr;
  grid-template-areas: "photo info";
  gap: var(--gap);
  align-items: center;

  min-height: clamp(240px, 38vw, 440px);
  position: relative;
  overflow: hidden;
}

.member-photo {
  grid-area: photo;
}

.member-info {
  grid-area: info;
}

.member-card.is-right {
  grid-template-columns: 1fr var(--img-w);
  grid-template-areas: "info photo";
}

.member-photo {
  position: relative;
  width: 100%;
  height: 100%;
}

.member-card:not(.is-right) .member-photo::after {
  content: "";
  position: absolute;
  inset: 0 0 0 auto;
  width: var(--fade);
  background: linear-gradient(to left, var(--v-theme-surface), transparent);
  pointer-events: none;
}

.member-card.is-right .member-photo::after {
  content: "";
  position: absolute;
  inset: 0 auto 0 0;
  width: var(--fade);
  background: linear-gradient(to right, var(--v-theme-surface), transparent);
  pointer-events: none;
}

.member-info {
  max-width: 65ch;
  padding-block: 1.25rem;
  padding-inline: 0.25rem 1.25rem;
}

.member-card.is-right .member-info {
  padding-inline: 1.25rem 0.25rem;
}

@media (max-width: 600px) {
  .member-card,
  .member-card.is-right {
    grid-template-columns: 1fr;
    grid-template-areas:
      "photo"
      "info";
    min-height: unset;
  }

  .member-photo::after {
    display: none;
  }
}
</style>
