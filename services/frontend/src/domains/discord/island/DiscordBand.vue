<script setup lang="ts">
import {computed, onBeforeUnmount, onMounted, ref} from "vue"
import BandHead from "@/components/island/BandHead.vue"
import SocialMark from "@/components/island/SocialMark.vue"
import {DISCORD_INVITE, SOCIAL_GLYPHS} from "@/components/island/socialGlyphs"
import {type DiscordRooms, howFull, liveOf, SERVER_NAME, type VoiceRoom, watchDiscordRooms} from "../rooms"
import voiceGlyph from "@/assets/discord/voice.webp"
import lockedGlyph from "@/assets/discord/voice-locked.webp"
import VoicePeople from "./VoicePeople.vue"

/**
 * The Discord band: an invitation to look in, and one widget in Discord's own chrome listing
 * the voice rooms.
 *
 * The widget's Join server is the band's one way in, so the head carries no button. Discord's
 * palette is its own and is the same in both themes, and the widget is the one thing on the
 * page with rounded corners. It lists only the rooms somebody is in, each joined in Discord
 * itself. Where Discord says nothing, the widget is its head and invite alone.
 */
/* Follows the server as the api pushes it. An answer of nothing keeps the last one rather than emptying the widget. */
const rooms = ref<DiscordRooms | null>(null)

let stop: () => void
onMounted(() => {
  stop = watchDiscordRooms(answer => {
    if (answer !== null || rooms.value === null) rooms.value = answer
  })
})
onBeforeUnmount(() => stop())

const live = computed(() => liveOf(rooms.value))

const glyphOf = (room: VoiceRoom) => {
  const url = `url(${room.locked ? lockedGlyph : voiceGlyph})`
  return {maskImage: url, WebkitMaskImage: url}
}

</script>

<template>
  <section
    class="discord-band"
    data-testid="home-discord"
  >
    <span
      aria-hidden="true"
      class="discord-band__wash"
    />
    <div class="discord-band__inner">
      <band-head
        eyebrow="Not quite sure yet?"
        heading="Come check the vibes"
      />

      <div
        class="widget"
        :class="{'widget--invite': !rooms}"
        data-testid="home-discord-widget"
      >
        <div class="widget__head">
          <social-mark
            class="widget__mark"
            :glyph="SOCIAL_GLYPHS.discord"
            :size="18"
          />
          <span class="widget__server">{{ rooms?.server ?? SERVER_NAME }}</span>
          <span
            v-if="live"
            class="widget__live"
            data-testid="home-discord-live"
          ><span class="widget__dot" />{{ live }}</span>
          <a
            class="widget__cta"
            data-testid="home-discord-join"
            :href="DISCORD_INVITE"
            rel="noopener"
            target="_blank"
          >Join server</a>
        </div>

        <ul
          v-if="rooms && rooms.rooms.length > 0"
          class="widget__rooms"
        >
          <li
            v-for="room in rooms.rooms"
            :key="room.id"
            class="widget__room"
            :class="{'widget__room--locked': room.locked}"
            :data-testid="`home-discord-room-${room.id}`"
          >
            <!-- Discord's own voice glyphs, as a mask so the colour is the room's: green while
                 somebody is in it. -->
            <span
              aria-hidden="true"
              class="widget__glyph"
              :class="{'widget__glyph--live': room.people.length > 0}"
              :style="glyphOf(room)"
            />
            <span class="widget__room-words">
              <span class="widget__room-name">{{ room.locked ? `${room.name} · members only` : room.name }}</span>
              <voice-people
                v-if="room.people.length > 0"
                class="widget__room-who"
                :people="room.people"
              />
              <span
                v-else
                class="widget__room-who widget__room-empty"
              >nobody yet, start it</span>
            </span>
            <span class="widget__count">{{ howFull(room) }}</span>
            <!-- A members-only room still opens in Discord, which asks for membership from there. -->
            <a
              :aria-label="room.locked ? `Join ${room.name}, which opens with membership` : `Join ${room.name}`"
              class="widget__join"
              :href="room.href"
              rel="noopener"
              target="_blank"
            >Join</a>
          </li>
        </ul>
        <p
          v-if="rooms && !rooms.rooms.some(room => room.people.length > 0)"
          class="widget__quiet"
          data-testid="home-discord-quiet"
        >
          Nobody is in voice right now.
        </p>
      </div>
    </div>
  </section>
</template>

<style scoped>
.discord-band {
  position: relative;
  isolation: isolate;
  width: 100%;
  overflow: hidden;
  background-color: var(--band-ground);
}

/* The board wash, in blurple: the band belongs to something with a colour of its own. */
.discord-band__wash {
  position: absolute;
  inset: 0;
  z-index: -1;
  pointer-events: none;
  background:
    radial-gradient(132% 175% at 6% 0, color-mix(in oklab, #5865f2 var(--board-wash-on), transparent) 0%,
      color-mix(in oklab, #5865f2 var(--board-wash), transparent) 40%, transparent 82%),
    linear-gradient(to bottom, color-mix(in oklab, #5865f2 var(--board-wash), transparent) 0%, transparent 62%);
}

.discord-band__inner {
  width: 100%;
  max-width: 72rem;
  margin: 0 auto;
  padding: 1.5rem 2rem 1.75rem;
}

/* Discord's own chrome, the same in both themes, and centred wider than the text column. */
.widget {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  width: 100%;
  max-width: 820px;
  /* The room list scrolls inside it past this, so a busy evening keeps the band its height. */
  max-height: 21.5rem;
  margin: 1.1rem auto 0;
  padding: 0.75rem 0.85rem 0.8rem;
  overflow: hidden;
  border-radius: 14px;
  background: #2b2d31;
  color: #dbdee1;
  font-family: var(--font-body);
}

.widget__head {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  padding: 0.1rem 0.15rem 0.5rem;
  border-bottom: 1px solid #3f4147;
}

.widget--invite .widget__head {
  padding-bottom: 0.1rem;
  border-bottom: 0;
}

.widget__mark {
  flex: none;
  color: #949ba4;
}

.widget__server {
  flex-grow: 1;
  font-size: 0.92rem;
  font-weight: 600;
}

.widget__live {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  font-size: 0.72rem;
  color: #949ba4;
}

.widget__dot {
  width: 6px;
  height: 6px;
  border-radius: 9999px;
  background: #3ba55d;
}

.widget__cta {
  padding: 0.32rem 0.85rem;
  border-radius: 999px;
  background: #5865f2;
  font-size: 0.72rem;
  font-weight: 500;
  color: #fff;
}

.widget__cta:hover,
.widget__cta:focus-visible {
  background: #4752c4;
}

.widget__rooms {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  gap: 0.1rem;
  min-height: 0;
  overflow-y: auto;
}

.widget__rooms::-webkit-scrollbar {
  width: 8px;
}

.widget__rooms::-webkit-scrollbar-thumb {
  border-radius: 4px;
  background: #1a1b1e;
}

.widget__room {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  padding: 0.45rem 0.6rem;
  border-radius: 8px;
}

.widget__room:hover {
  background: #35373c;
}

.widget__room--locked {
  opacity: 0.72;
}

.widget__glyph {
  flex: none;
  width: 16px;
  height: 16px;
  background-color: #949ba4;
  mask-position: center;
  mask-repeat: no-repeat;
  mask-size: contain;
}

/* Discord's own green for a room somebody is talking in. */
.widget__glyph--live {
  background-color: #23a55a;
}

.widget__room-words {
  flex-grow: 1;
  min-width: 0;
}

.widget__room-name {
  display: block;
  font-size: 0.85rem;
  color: #dbdee1;
}

.widget__room-who {
  margin-top: 0.2rem;
}

.widget__room-empty {
  display: block;
  font-size: 0.75rem;
  color: #949ba4;
}

.widget__quiet {
  padding: 0.45rem 0.6rem;
  font-size: 0.8rem;
  color: #949ba4;
}

.widget__count {
  font-size: 0.72rem;
  white-space: nowrap;
  color: #949ba4;
}

.widget__join {
  margin-left: auto;
  padding: 0.25rem 0.7rem;
  border-radius: 999px;
  background: #3f4147;
  font-size: 0.7rem;
  color: #dbdee1;
}

.widget__join:hover,
.widget__join:focus-visible {
  background: #4e5058;
}


@media (max-width: 767px) {
  .discord-band__inner {
    padding: 1.25rem 1.25rem 1.5rem;
  }

  .widget__count {
    display: none;
  }
}
</style>
