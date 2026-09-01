<template>
  <div
    v-if="member.image"
    :style="{ minHeight: 'clamp(240px, 38vw, 440px)' }"
    class="mx-auto overflow-hidden"
  >
    <v-row
      align="center"
      no-gutters
    >
      <v-col
        :class="['d-flex', 'seat-photo', reverse ? 'seat-photo--end' : 'seat-photo--start']"
        cols="12"
        data-testid="board-seat-photo"
        md="5"
      >
        <div :class="['w-100', 'fade-edge', 'img-box', reverse ? 'reverse' : '', reverse ? 'round-end' : 'round-start']">
          <v-img
            :src="member.image"
            class="w-100"
            cover
            eager
          />
        </div>
      </v-col>

      <v-col
        :class="['seat-blurb', reverse ? 'seat-blurb--start' : 'seat-blurb--end']"
        cols="12"
        data-testid="board-seat-blurb"
        md="7"
      >
        <div :class="['pa-2', 'pa-md-2', reverse ? 'text-md-end' : 'text-md-start']">
          <p class="text-h2 font-name">
            {{ member.name }}
          </p>
          <p class="text-subtitle-1 mt-n6">
            {{ member.title }}
          </p>
          <p>
            {{ member.description }}
          </p>
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
        <p class="text-h2 font-name">
          {{ member.name }}
        </p>
        <p class="text-subtitle-1 mt-n6">
          {{ member.title }}
        </p>
        <p>{{ member.description }}</p>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
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

<style lang="scss" scoped>
// The row places its own columns rather than borrowing Vuetify's `order-md-*`: Tailwind
// generates `.order-1`/`.order-2` from anything it scans into a later cascade layer, which
// beats them, and every seat's photograph then sits on the left.
.seat-photo {
  order: 1;
}

.seat-blurb {
  order: 2;
}

@media (min-width: 840px) {
  .seat-photo--start,
  .seat-blurb--start {
    order: 1;
  }

  .seat-photo--end,
  .seat-blurb--end {
    order: 2;
  }
}

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

// Below md the columns stack, so the photograph fades downwards into the blurb under it.
@media (max-width: 839.98px) {
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

@media (max-width: 839.98px) {
  .round-start,
  .round-end {
    border-radius: var(--radius-lg) var(--radius-lg) 0 0 !important;
  }
}
</style>
