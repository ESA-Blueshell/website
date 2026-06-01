<template>
  <div :style="backgroundStyle">
    <v-container class="discord-section py-10">
      <v-sheet
        class="discord-widget mx-auto"
        rounded="xl"
      >
        <!-- Header: branding + live count on the left, join CTA on the right -->
        <div class="discord-widget__header">
          <div class="discord-widget__brand">
            <v-icon
              class="discord-widget__logo"
              size="44"
            >
              custom:discord
            </v-icon>
            <div class="discord-widget__heading">
              <p class="text-h5 text-sm-h4 text-white mb-0">
                Join us on our Discord server
              </p>
              <p
                v-if="discordData"
                class="discord-widget__online mb-0"
              >
                <span class="discord-widget__dot" />
                {{ discordData!.presence_count }} online now
              </p>
            </div>
          </div>

          <v-btn
            class="discord-widget__join"
            color="primary"
            href="https://discord.gg/23YMFQy"
            prepend-icon="custom:discord"
            rounded="pill"
            size="large"
            target="_blank"
          >
            Join Discord
          </v-btn>
        </div>

        <!-- Body: online members + active voice channels -->
        <v-row
          v-if="discordData"
          class="discord-widget__body"
        >
          <v-col
            :md="hasChannels ? 6 : 12"
            cols="12"
          >
            <p class="discord-widget__label">
              Online members
            </p>
            <div class="discord-widget__panel">
              <v-row
                class="ma-0"
                justify="start"
              >
                <discord-user
                  v-for="membership in discordData!.members"
                  :key="membership.username"
                  :avatar-url="membership.avatar_url"
                  :half-width="hasChannels"
                  :status="membership.status"
                  :username="membership.username"
                />
                <discord-user
                  v-if="discordData!.members.length > 99"
                  :custom-text="'+' + (discordData!.presence_count - discordData!.members.length) + ' more'"
                  :half-width="hasChannels"
                />
              </v-row>
            </div>
          </v-col>

          <v-col
            v-if="hasChannels"
            cols="12"
            md="6"
          >
            <p class="discord-widget__label">
              Active public VCs
            </p>
            <div class="discord-widget__panel">
              <div
                v-for="[channelId, channelName] in channelEntries"
                :key="channelId"
                class="discord-widget__vc"
              >
                <div class="discord-widget__vc-name">
                  <v-icon
                    color="white"
                    icon="mdi-volume-high"
                    size="20"
                  />
                  <span
                    class="text-subtitle-1 text-white font-weight-medium"
                    v-text="channelName"
                  />
                </div>
                <v-row class="ma-0">
                  <discord-user
                    v-for="membership in membersInVC[channelId]"
                    :key="membership.username"
                    :avatar-url="membership.avatar_url"
                    :half-width="true"
                    :status="membership.status"
                    :username="membership.username"
                  />
                </v-row>
              </div>
            </div>
          </v-col>
        </v-row>
      </v-sheet>
    </v-container>
  </div>
</template>

<script lang="ts" setup>
import {computed, onMounted, ref} from "vue"
import axios from "axios"
import {useTheme} from "vuetify"
import DiscordUser from "@/components/base/DiscordUser.vue"
import type {SnowflakeType, WidgetChannel, WidgetMember, WidgetResponse} from "@/services/api"

const theme = useTheme()

const backgroundStyle = computed(() => {
  const colors = theme.current.value.colors as Record<string, string> | undefined
  return {background: colors?.wallpaper ?? ""}
})

const discordData = ref<WidgetResponse | null>(null)
const channels = ref<Record<SnowflakeType, string>>({})
const membersInVC = ref<Record<SnowflakeType, WidgetMember[]>>({})

function shuffleArray<T>(array: T[]): void {
  for (let i = array.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
    ;[array[i]!, array[j]!] = [array[j]!, array[i]!]
  }
}

const channelEntries = computed<[SnowflakeType, string][]>(() =>
  Object.entries(channels.value) as [SnowflakeType, string][],
)
const hasChannels = computed(() => channelEntries.value.length > 0)

onMounted(async () => {
  try {
    // (Optional) Keep your existing side-effect if needed:
    // await getGuildWidget({ path: { guild_id: '324285132133629963' } })

    const {data} = await axios.get<WidgetResponse>(
      "https://discordapp.com/api/guilds/324285132133629963/widget.json",
    )
    discordData.value = data

    if (discordData.value?.members) {
      shuffleArray(discordData.value.members)
    }

    const membersInAChannel = (discordData.value?.members ?? []).filter(
        (m: WidgetMember) => !!m.channel_id,
      )

    ;(discordData.value?.channels ?? []).forEach((channel: WidgetChannel) => {
      const membersInThisChannel = membersInAChannel.filter(
        (m: WidgetMember) => m.channel_id === channel.id,
      )
      if (membersInThisChannel.length > 0) {
        channels.value[channel.id] = channel.name
        membersInVC.value[channel.id] = membersInThisChannel
      }
    })
  } catch (err) {
    console.error("Failed to load Discord widget", err)
  }
})
</script>

<style lang="scss" scoped>
.discord-section {
  max-width: 1100px;
}

.discord-widget {
  background: transparent;

  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    flex-wrap: wrap;
    gap: 16px;
  }

  &__brand {
    display: flex;
    align-items: center;
    gap: 16px;
    min-width: 0;
  }

  &__logo {
    color: rgb(var(--v-theme-primary));
    flex: 0 0 auto;
  }

  &__heading {
    min-width: 0;
  }

  &__online {
    display: flex;
    align-items: center;
    gap: 8px;
    color: rgba(255, 255, 255, 0.7);
    font-size: 0.95rem;
  }

  &__dot {
    width: 9px;
    height: 9px;
    border-radius: 50%;
    background: #3ba55d;
    box-shadow: 0 0 0 3px rgba(59, 165, 93, 0.25);
  }

  &__join {
    flex: 0 0 auto;
  }

  &__body {
    margin-top: 24px;
  }

  &__label {
    color: #fff;
    font-size: 1.1rem;
    margin-bottom: 8px;
  }

  &__panel {
    max-height: 230px;
    overflow-y: auto;
  }

  &__vc {
    & + & {
      margin-top: 8px;
    }
  }

  &__vc-name {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 4px 4px 8px;
  }
}
</style>
