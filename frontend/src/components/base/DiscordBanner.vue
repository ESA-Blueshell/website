<template>
  <div :style="backgroundStyle">
    <v-container class="pa-0">
      <v-row
        align="center"
        class="mx-auto container flex-nowrap"
      >
        <v-col
          class="flex-shrink-1"
          cols="auto"
        >
          <p
            class="text-white text-h5 text-sm-h4 font-weight-thin mb-0"
            style="float: left"
          >
            Join us on our Discord server
          </p>
        </v-col>

        <v-spacer />

        <v-col cols="auto">
          <v-btn
            color="primary"
            href="https://discord.gg/23YMFQy"
            target="_blank"
          >
            <img
              src="@/assets/discord.svg"
              alt="discord icon"
              style="width: 35px"
            >
          </v-btn>
        </v-col>
      </v-row>

      <v-row
        v-if="discordData"
        class="mx-auto pt-4 container"
      >
        <v-col
          :md="hasChannels ? 5 : 12"
          cols="12"
        >
          <p class="text-h6 text-sm-h5 text-white mb-2">
            {{ discordData!.presence_count }} people now online on discord
          </p>

          <div
            class="overflow-hidden"
            style="border: 1px solid #A8FF00;border-radius: 10px"
          >
            <div
              class="overflow-y-auto"
              style="max-height: 205px"
            >
              <v-container class="px-0 pt-2">
                <v-row justify="start">
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
              </v-container>
            </div>
          </div>
        </v-col>

        <v-spacer />

        <v-col
          v-if="hasChannels"
          cols="12"
          md="5"
        >
          <p class="text-h5 text-white mb-2">
            Active public VCs
          </p>

          <v-container
            class="overflow-y-auto pa-0"
            style="max-height: 205px"
          >
            <v-row
              v-for="[channelId, channelName] in channelEntries"
              :key="channelId"
              class="mb-2"
              style="border: 1px solid #A8FF00;border-radius: 10px"
            >
              <v-col
                class="discord-membership-entry"
                cols="12"
              >
                <v-icon
                  color="white"
                  icon="mdi-volume-high"
                  size="20"
                  style="margin: 6px !important; margin-right: 10px !important;"
                />
                <span
                  class="text-h6 text-white font-italic font-weight-thin"
                  v-text="channelName"
                />
              </v-col>

              <discord-user
                v-for="membership in membersInVC[channelId]"
                :key="membership.username"
                :avatar-url="membership.avatar_url"
                :half-width="true"
                :status="membership.status"
                :username="membership.username"
              />
            </v-row>
          </v-container>
        </v-col>
      </v-row>
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
.container {
  max-width: 1100px;
  height: 100%;
}

.v-btn {
  padding: 10px !important;
  width: 65px !important;
  height: 65px !important;
  min-width: 0 !important;
}

.v-row {
  margin: 0;
}
</style>
