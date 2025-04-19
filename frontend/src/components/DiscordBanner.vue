<template>
  <div :style="{background: $vuetify.theme.computedThemes[$vuetify.theme.global.name].colors.wallpaper }">
    <v-container class="pa-0">
      <v-row
        class="mx-auto container flex-nowrap"
        align="center"
      >
        <v-col
          cols="auto"
          class="flex-shrink-1"
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
              :src="$require('@/assets/discord.svg')"
              style="width: 35px"
              alt="discord icon"
            >
          </v-btn>
        </v-col>
      </v-row>
      <v-row
        v-if="discordData"
        class="mx-auto pt-4 container"
      >
        <v-col
          cols="12"
          :md="Object.entries(channels).length > 0 ? 5 : 12"
        >
          <p class="text-h6 text-sm-h5 text-white mb-2">
            {{ discordData.presence_count }} people now online on discord
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
                <v-row style="justify-content: center">
                  <discord-user
                    v-for="membership in discordData.members"
                    :key="membership.username"
                    :username="membership.username"
                    :status="membership.status"
                    :avatar-url="membership.avatar_url"
                    :half-width="Object.entries(channels).length > 0"
                  />
                  <discord-user
                    v-if="discordData.members.length > 99"
                    :custom-text="'+ '+(discordData.presence_count - discordData.members.length)+' more'"
                  />
                </v-row>
              </v-container>
            </div>
          </div>
        </v-col>

        <v-spacer />

        <v-col
          v-if="Object.entries(channels).length > 0"
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
              v-for="[channelId,channelName] in Object.entries(channels)"
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
                  size="20"
                  style="margin: 6px !important; margin-right: 10px !important;"
                  icon="mdi-volume-high"
                />
                <span
                  class="text-h6 text-white font-italic font-weight-thin"
                  v-text="channelName"
                />
              </v-col>

              <discord-user
                v-for="membership in membersInVC[channelId]"
                :key="membership.username"
                :username="membership.username"
                :status="membership.status"
                :avatar-url="membership.avatar_url"
                :half-width="true"
              />
            </v-row>
          </v-container>
        </v-col>
      </v-row>
    </v-container>
  </div>
</template>

<script>
import DiscordUser from "@/components/DiscordUser.vue";
import {$require} from "@/plugins/require.js";

export default {
  name: "DiscordBanner",
  components: {DiscordUser: DiscordUser},
  data: () => ({
    discordData: null,
    channels: {},
    membersInVC: {},
  }),
  mounted() {
    this.$http.get('https://discordapp.com/api/guilds/324285132133629963/widget.json')
      .then(response => {
        this.discordData = response.data
        this.shuffleArray(this.discordData.members)

        const membersInAChannel = this.discordData.members.filter(membership => membership.channel_id)

        this.discordData.channels.forEach(channel => {
          const membersInThisChannel = membersInAChannel.filter(membership => membership.channel_id === channel.id);
          if (membersInThisChannel.length > 0) {
            this.channels[channel.id] = channel.name
            this.membersInVC[channel.id] = membersInThisChannel
          }
        })


      })
  },
  methods: {
    $require,
    shuffleArray(array) {
      for (let i = array.length - 1; i > 0; i--) {
        const j = Math.floor(Math.random() * (i + 1));
        const temp = array[i];
        array[i] = array[j];
        array[j] = temp;
      }
    }

  }
}
</script>

<style scoped>

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
