<script setup lang="ts">
import {computed, onMounted, ref} from "vue"
import BandHead from "@/components/island/BandHead.vue"
import SocialMark from "@/components/island/SocialMark.vue"
import {DISCORD_INVITE, SOCIAL_GLYPHS} from "@/components/island/socialGlyphs"
import {type DiscordRooms, howFull, readDiscordRooms, whoIsIn} from "../rooms"

/**
 * The Discord band: an invitation to look in, and one widget in Discord's own chrome listing
 * the voice rooms.
 *
 * The widget's Join server is the band's one way in, so the head carries no button. Discord's
 * palette is its own and is the same in both themes, and the widget is the one thing on the
 * page with rounded corners. Where Discord says nothing, the widget is its head and invite alone.
 */
const rooms = ref<DiscordRooms | null>(null)

onMounted(async () => {
  rooms.value = await readDiscordRooms()
})

const live = computed(() => {
  const said = rooms.value
  if (said?.online === undefined) return ""
  return said.members === undefined
    ? `${said.online} online`
    : `${said.online} online of ${said.members} members`
})

const anyLocked = computed(() => rooms.value?.rooms.some(room => room.locked) ?? false)
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
        :class="{'widget--invite': !rooms || rooms.rooms.length === 0}"
        data-testid="home-discord-widget"
      >
        <div class="widget__head">
          <social-mark
            class="widget__mark"
            :glyph="SOCIAL_GLYPHS.discord"
            :size="18"
          />
          <span class="widget__server">{{ rooms?.server ?? "ESA Blueshell" }}</span>
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
            <svg
              v-if="room.locked"
              aria-hidden="true"
              class="widget__glyph"
              fill="none"
              viewBox="0 0 16 16"
            >
              <rect
                height="6.4"
                rx="1"
                stroke="currentColor"
                stroke-width="1.2"
                width="9.6"
                x="3.2"
                y="7"
              />
              <path
                d="M5.4 7V5.4a2.6 2.6 0 0 1 5.2 0V7"
                stroke="currentColor"
                stroke-width="1.2"
              />
            </svg>
            <svg
              v-else
              aria-hidden="true"
              class="widget__glyph"
              fill="none"
              viewBox="0 0 16 16"
            >
              <path
                d="M2 6h2.6L8 3v10L4.6 10H2z"
                fill="currentColor"
              />
              <path
                d="M10.6 6.2a2.6 2.6 0 0 1 0 3.6M12.6 4.4a5 5 0 0 1 0 7.2"
                stroke="currentColor"
                stroke-width="1.2"
              />
            </svg>
            <span class="widget__room-words">
              <span class="widget__room-name">{{ room.name }}</span>
              <span class="widget__room-who">{{ whoIsIn(room) }}</span>
            </span>
            <span class="widget__count">{{ howFull(room) }}</span>
            <!-- A members-only room still offers a way in: the invite, and membership from there. -->
            <a
              :aria-label="room.locked ? `Join ${room.name}, which opens with membership` : `Join ${room.name}`"
              class="widget__join"
              :href="DISCORD_INVITE"
              rel="noopener"
              target="_blank"
            >Join</a>
          </li>
        </ul>

        <p
          v-if="anyLocked"
          class="widget__foot"
        >
          A locked room opens with membership. Everything else is open to anybody.
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
  width: 13px;
  height: 13px;
  color: #949ba4;
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
  display: block;
  font-size: 0.75rem;
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

.widget__foot {
  margin-top: 0.5rem;
  padding-top: 0.5rem;
  border-top: 1px solid #3f4147;
  font-size: 0.74rem;
  line-height: 1.4;
  color: #949ba4;
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
