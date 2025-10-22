<template>
  <div
    v-if="member.image"
    class="mx-auto overflow-hidden"
    :style="{ minHeight: 'clamp(240px, 38vw, 440px)' }"
  >
    <v-row
      no-gutters
      align="center"
    >
      <v-col
        v-if="!reverse"
        cols="12"
        md="5"
        class="d-flex order-1 order-md-1"
      >
        <div :class="['w-100', 'fade-edge', 'img-box', 'round-start']">
          <v-img
            :src="member.image"
            cover
            class="w-100"
            eager
          />
        </div>
      </v-col>

      <v-col
        cols="12"
        md="7"
        :class="['order-2', reverse ? 'order-md-1' : 'order-md-2']"
      >
        <div :class="['pa-2', 'pa-md-2', reverse ? 'text-md-end' : 'text-md-start']">
          <p class="text-h4 text-md-h3">
            {{ member.name }}
          </p>
          <p class="text-subtitle-1 mt-1 mb-4">
            {{ member.title }}
          </p>
          <p>
            {{ member.description }}
          </p>
        </div>
      </v-col>

      <v-col
        v-if="reverse"
        cols="12"
        md="5"
        class="d-flex order-1 order-md-2"
      >
        <div :class="['w-100', 'fade-edge', 'reverse', 'img-box', 'round-end']">
          <v-img
            :src="member.image"
            cover
            class="w-100"
            eager
          />
        </div>
      </v-col>
    </v-row>
  </div>

  <div
    v-else
    class="mx-auto"
  >
    <div class="mx-auto">
      <div :class="['pa-2', 'pa-md-2', 'text-end', reverse ? 'text-md-end' : 'text-md-start']">
        <p class="text-h4 text-md-h3">
          {{ member.name }}
        </p>
        <p class="text-subtitle-1 mt-1 mb-4">
          {{ member.title }}
        </p>
        <p>{{ member.description }}</p>
      </div>
    </div>
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
.fade-edge {
  position: relative;
  overflow: hidden;

  --fade-start: 70%;
  --fade-end: 100%;

  mask-image: linear-gradient(
      to right,
      rgba(0, 0, 0, 1) 0%,
      rgba(0, 0, 0, 1) var(--fade-start),
      rgba(0, 0, 0, 0) var(--fade-end)
  );
  -webkit-mask-image: linear-gradient(
      to right,
      rgba(0, 0, 0, 1) 0%,
      rgba(0, 0, 0, 1) var(--fade-start),
      rgba(0, 0, 0, 0) var(--fade-end)
  );
  mask-repeat: no-repeat;
  -webkit-mask-repeat: no-repeat;
}

.fade-edge.reverse {
  mask-image: linear-gradient(
      to left,
      rgba(0, 0, 0, 1) 0%,
      rgba(0, 0, 0, 1) var(--fade-start),
      rgba(0, 0, 0, 0) var(--fade-end)
  );
  -webkit-mask-image: linear-gradient(
      to left,
      rgba(0, 0, 0, 1) 0%,
      rgba(0, 0, 0, 1) var(--fade-start),
      rgba(0, 0, 0, 0) var(--fade-end)
  );
}

@media (max-width: 960px) {
  .fade-edge,
  .fade-edge.reverse {
    --fade-start: 78%;

    mask-image: linear-gradient(
        to bottom,
        rgba(0, 0, 0, 1) 0%,
        rgba(0, 0, 0, 1) var(--fade-start),
        rgba(0, 0, 0, 0) var(--fade-end)
    );
    -webkit-mask-image: linear-gradient(
        to bottom,
        rgba(0, 0, 0, 1) 0%,
        rgba(0, 0, 0, 1) var(--fade-start),
        rgba(0, 0, 0, 0) var(--fade-end)
    );
  }
}

@media (max-width: 960px) {
  .rounded-mobile-top {
    --radius-lg: var(--v-border-radius-lg, 24px);
    border-radius: var(--radius-lg) var(--radius-lg) 0 0 !important;
  }
}

.img-box {
  --radius-lg: var(--v-border-radius-lg, 24px);
  overflow: hidden;
}

.round-start {
  border-start-start-radius: var(--radius-lg);
  border-end-start-radius: var(--radius-lg);
}

.round-end {
  border-start-end-radius: var(--radius-lg);
  border-end-end-radius: var(--radius-lg);
}

@media (max-width: 960px) {
  .round-start,
  .round-end {
    border-radius: var(--radius-lg) var(--radius-lg) 0 0 !important;
  }
}

</style>

