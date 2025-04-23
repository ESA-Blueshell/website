<template>
  <v-main>
    <top-banner title="Board" />
    <div class="mx-3">
      <div
        class="mx-auto my-10"
        style="max-width: 800px"
      >
        <template
          v-for="(board, boardIndex) in boards"
          :key="board.name"
        >
          <!-- First board without transition -->
          <template v-if="boardIndex === 0">
            <div class="board-section">
              <h2 class="text-h4 text-center my-4">
                {{ board.name }}
              </h2>
              <v-img
                v-if="board.boardImage"
                :src="board.boardImage"
                style="border-radius: 10px"
                class="mb-6"
              />

              <template
                v-for="(member, i) in board.members"
                :key="member.name"
              >
                <div
                  v-if="member.image"
                  class="mb-12 mt-16"
                  :style="getMemberCardStyle(i, member.image)"
                >
                  <div
                    class="member-info"
                    :style="getMemberInfoStyle(i)"
                  >
                    <p
                      class="text-h2"
                      :class="{ 'text-right': i % 2 === 1 }"
                    >
                      {{ member.name }}
                    </p>
                    <p
                      class="text-subtitle-1 mt-n6"
                      :class="{ 'text-right': i % 2 === 1 }"
                    >
                      {{ member.title }}
                    </p>
                    <p :class="{ 'text-right': i % 2 === 1 }">
                      {{ member.description }}
                    </p>
                  </div>
                </div>
                <div
                  v-else
                  class="mb-12 mt-16 member-info"
                >
                  <p
                    class="text-h2"
                    :class="{ 'text-right': i % 2 === 1 }"
                  >
                    {{ member.name }}
                  </p>
                  <p
                    class="text-subtitle-1 mt-6"
                    :class="{ 'text-right': i % 2 === 1 }"
                  >
                    {{ member.title }}
                  </p>
                  <p :class="{ 'text-right': i % 2 === 1 }">
                    {{ member.description }}
                  </p>
                </div>
              </template>
            </div>
          </template>

          <template v-else>
            <div
              class="board-section"
              style="cursor: pointer"
            >
              <h2
                class="text-h4 text-center my-4 clickable"
                @click="toggleBoard(boardIndex)"
              >
                {{ board.name }}
                <v-icon v-if="boardIndex !== 0">
                  {{ expandedBoards[boardIndex] ? 'mdi-chevron-up' : 'mdi-chevron-down' }}
                </v-icon>
              </h2>

              <v-expand-transition>
                <div v-show="expandedBoards[boardIndex]">
                  <v-img
                    v-if="board.boardImage"
                    :src="board.boardImage"
                    style="border-radius: 10px"
                    class="mb-6"
                  />

                  <template
                    v-for="(member, i) in board.members"
                    :key="member.name"
                  >
                    <div
                      v-if="member.image"
                      class="mb-12 mt-16"
                      :style="getMemberCardStyle(i, member.image)"
                    >
                      <div
                        class="member-info"
                        :style="getMemberInfoStyle(i)"
                      >
                        <p
                          class="text-h2"
                          :class="{ 'text-right': i % 2 === 1 }"
                        >
                          {{ member.name }}
                        </p>
                        <p
                          class="text-subtitle-1 mt-n6"
                          :class="{ 'text-right': i % 2 === 1 }"
                        >
                          {{ member.title }}
                        </p>
                        <p :class="{ 'text-right': i % 2 === 1 }">
                          {{ member.description }}
                        </p>
                      </div>
                    </div>
                    <div
                      v-else
                      class="mb-12 mt-16 member-info"
                    >
                      <p
                        class="text-h2"
                        :class="{ 'text-right': i % 2 === 1 }"
                      >
                        {{ member.name }}
                      </p>
                      <p
                        class="text-subtitle-1 mt-6"
                        :class="{ 'text-right': i % 2 === 1 }"
                      >
                        {{ member.title }}
                      </p>
                      <p :class="{ 'text-right': i % 2 === 1 }">
                        {{ member.description }}
                      </p>
                    </div>
                  </template>
                </div>
              </v-expand-transition>
            </div>
          </template>
        </template>
      </div>
    </div>
  </v-main>
</template>

<script>
import TopBanner from "@/components/banners/TopBanner.vue";
import {$require} from "@/plugins/require";

export default {
  components: {TopBanner: TopBanner},
  data() {
    return {
      expandedBoards: {},
      boards: [
        {
          name: "8th Board",
          boardImage: $require('@/assets/board8pics/board8.jpg'),
          members: [
            {
              name: "Michal Rokita",
              title: "Chair",
              description:
                "Hello! I’m Michal 'udeyy' Rokita, 21 years old, and Chair of Blueshell. I was born in Warsaw, Poland, and spent much of my life in Belgium and the Netherlands before coming to Enschede to study Industrial Design Engineering at UT. I've always loved gaming (especially Valorant), but also enjoy sports like running and swimming. I’m excited to represent Blueshell and help it thrive!",
              image: $require("@/assets/board8pics/Michal.png"),
            },
            {
              name: "Joris Jonkers",
              title: "Secretary and Commissioner of External Affairs",
              description:
                "Heyoo! I’m Joris ‘ExtraToast’ Jonkers, 26 years old, studying Computer Science, and serving as Secretary and Commissioner of External Affairs. I’ve been gaming since childhood, from old consoles to PC gaming. I love working on old vehicles, collecting retro games, and going to concerts and festivals. I look forward to keeping Blueshell organized and connecting with more gaming enthusiasts!",
              image: $require("@/assets/board8pics/Joris.png"),
            },
            {
              name: "Chris Wong",
              title: "Treasurer and Commissioner of Esports affairs",
              description:
                "Hey there! I’m Chris 'FetaBass' Wong, 20 years old from Apeldoorn, studying TCS (and possibly switching to BIT). I grew up with Pokémon and moved on to titles like MapleStory, RuneScape, and Minecraft. Nowadays, I’m really into Valorant, League of Legends, and CS2. I’m excited to manage our finances and help foster our esports community!",
              image: $require("@/assets/board8pics/Chris.png"),
            },
            {
              name: "Yannick Sloot",
              title: "Commissioner of Internal Affairs",
              description:
                "Yoo guys! I’m Yannick 'Yank' Sloot, 20 years old from Biddinghuizen, studying International Business Administration at UT. I've been gaming since I was a kid—from the GBA to PC, with favorites like CS2, Terraria, and Dark Souls. Besides gaming, I enjoy music and traveling. I’m looking forward to ensuring everything runs smoothly within Blueshell!",
              image: $require("@/assets/board8pics/Yannick.png"),
            },
          ],
        },
        {
          name: "7th Board",
          boardImage: $require('@/assets/board7pics/board7.jpg'),
          members: [
            {
              name: "Reinout Strating",
              title: "Chair",
              description:
                "Hello, I'm Reinout Strating but you can call me whatever you want – there is no limit. I'm still searching for a suitable study, that's why this is my break year! I enjoy playing any type of games if I get invited. Always trying to have fun with others, whatever suits their boat. I joined Blueshell after a friend introduced me to it last year and got me instantly hooked with the friendliness and good times. It got me excited enough to join the board and here I am! CHAIR ready for service!",
              image: $require("@/assets/board7pics/reinout.jpg"),
            },
            {
              name: "Max Jansdam",
              title: "Secretary and Commissioner of the Esports Lounge",
              description:
                "Hey, I am Max Jansdam and I am the Secretary and Commissioner of the Esports Lounge affairs for this year. This year I will be a third-year Psychology bachelor. I joined Blueshell 2 years ago during corona with the idea of finding people to play smash ultimate and just dance with. While I didn’t join many events in the first year, I started joining and organizing more in my second year. After friends asked me to join the next board, I thought, why not? Let’s make this an amazing year!",
              image: $require("@/assets/board7pics/max.jpg"),
            },
            {
              name: "Jesse van Gameren",
              title: "Treasurer and Commissioner of Esports Affairs",
              description:
                "Hello fellow people! My name is Jesse van Gameren and I am the Treasurer and Commissioner of Esports affairs of Blueshell this year. Currently, I am in my third year of Industrial Engineering and Management. I became a member of Blueshell 2 years ago after being invited to a few events where I found a great community! I like to play games and do just about anything, as long as it's with friends. After joining more events and committees, I wanted to try something new – to learn more myself and give back to the community that gives me so much joy. Let’s make this a wonderful year to remember!",
              image: $require("@/assets/board7pics/jesse.jpg"),
            },
            {
              name: "Mitchell van Poecke",
              title: "Officer of Internal Affairs",
              description:
                "Hey, what's up! I am Mitchell van Poecke and this year I will be the Officer of Internal Affairs of Blueshell. I’m currently in my third year of Creative Technology and this will also be my third year with Blueshell. I enjoy going for a walk, working on game prototypes and, of course, playing games with friends. In my first year I attended most Minecraft events and a few game nights – which led me to join a committee. At first, the idea of a board year didn’t speak to me, but after a few months in a committee, I got excited and joined the board. I hope we can have another fun and exciting year together!",
              image: $require("@/assets/board7pics/mitchell.jpg"),
            },
            {
              name: "Sanne van Kooten",
              title: "Officer of External Affairs",
              description:
                "Hello! I am Sanne van Kooten, and I am this year's Officer of External Affairs! Currently, I’m a fourth-year Biomedical Engineering bachelor. In addition to being creative and visiting concerts, I also love gaming. I joined Blueshell in the second year of my studies after participating in the Nintendo pubquiz during the kick-off, and I was immediately enthusiastic. After joining a few committees, I decided it was time to do something bigger – like being a board member. I look forward to creating an even bigger gaming community with lots of fun events!",
              image: $require("@/assets/board7pics/sanne.jpg"),
            },
          ]
        },
        {
          name: "6th Board",
          boardImage: $require('@/assets/board6pics/board6.jpg'),
          members: [
            {
              name: 'Amber "Ambanana" Scholtz',
              title: "Chair",
              image: $require("@/assets/board6pics/amber.jpg"),
            },
            {
              name: 'Thomas "ItIsIThomas" Dekker',
              title: "Treasurer",
              image: $require("@/assets/board6pics/thomas.jpg"),
            },
            {
              name: 'Jelle "TheJellyMan" van Wezep',
              title: "Secretary and Commissioner of the Esports Lounge",
              image: $require("@/assets/board6pics/jelle.jpg"),
            },
            {
              name: 'Jonas "Clunky" Valentijn',
              title: "Commissioner of Esports Affairs",
              image: $require("@/assets/board6pics/jonas.jpg"),
            },
            {
              name: 'Roos "SkyeWolf" Kruk',
              title: "Commissioner of Internal Affairs",
              image: $require("@/assets/board6pics/roos.jpg"),
            },
            {
              name: 'Thijs "Darkneoteric" Willems',
              title: "Commissioner of External Affairs",
              image: $require("@/assets/board6pics/thijs.jpg"),
            },
          ],
        },
        {
          name: "5th Board",
          boardImage: $require('@/assets/board5pics/board5.jpg'),
          members: [
            {
              name: 'Daniël "thiefzz" Floor',
              title: "Chairman",
            },
            {
              name: 'Bob "Bobbuz" Even',
              title: "Treasurer and Commissioner of Esports Affairs",
            },
            {
              name: 'Louis "Poking" Hu',
              title: "Secretary",
            },
            {
              name: 'Jelle "Zenga" Idzenga',
              title: "Commissioner of Internal Affairs",
            },
            {
              name: 'Ìlayda "Vriendelijke kebab" Hotamis',
              title: "Commissioner of External Affairs",
            },
          ],
        },
        {
          name: "4th Board",
          members: [
            {
              name: 'Ali “Kelbinoh” Kalbiyev',
              title: "Chair",
            },
            {
              name: 'Tjebbe “Issie54” Iskander Sterck',
              title: "Treasurer",
            },
            {
              name: 'Ivo “King Cookie” Heitlager',
              title: "Secretary and Commissioner of Esports Affairs",
            },
            {
              name: 'Lucia "Luna" Kim',
              title: "Commissioner of Internal Affairs",
            },
            {
              name: 'Jose “Bear” Pratdesaba Lopez',
              title: "Commissioner of External Affairs",
            },
          ],
        },
        {
          name: "3rd Board",
          members: [
            {
              name: 'Jander “Thoran” Gilbers',
              title: "Chairman",
            },
            {
              name: 'Joran “MacVanish” Hagen',
              title: "Secretary/Treasurer",
            },
            {
              name: 'Andrei “ElDonte” Raureanu',
              title: "Commissioner of Esports",
            },
            {
              name: 'William “Lampekap88” Schaarman',
              title: "Commissioner of internal affairs",
            },
            {
              name: 'Allysha “Meavis” Sewradj',
              title: "Commissioner of external affairs",
            },
          ],
        },
        {
          name: "2nd Board",
          members: [
            {
              name: 'Jasper “JappieXD” van Harten',
              title: "Chairman",
            },
            {
              name: 'Kimberly “Agile Manifesto” Evertz',
              title: "Secretary",
            },
            {
              name: 'Mauk “Dawarfmaster” Muller',
              title: "Treasurer",
            },
            {
              name: 'Antal “TheMadJokerHD” van Dongen',
              title: "Commissioner of Internal Affairs",
            },
            {
              name: 'Maiander “Maiantie” Eigenraam',
              title: "Commissioner of External Affairs",
            },
          ],
        },
        {
          name: "1st Board",
          members: [
            {
              name: 'Thijs “Grootbuik” Lieverse',
              title: "Chairman",
            },
            {
              name: 'Jan-Berend “JB” Mooijaart',
              title: "Secretary",
            },
            {
              name: 'Anne “Iadri” Schrader',
              title: "Treasurer",
            },
            {
              name: 'Stijn “Salish” Overduin',
              title: "Commissioner of Internal Affairs",
            },
            {
              name: 'Idwer “Fangedsheep” de Vries',
              title: "Commissioner of External Affairs",
            },
          ],
        },
      ]
    }
  },
  created() {
    this.boards.forEach((_, index) => {
      this.expandedBoards[index] = index === 0;
    });
  },
  methods: {
    $require,
    toggleBoard(index) {
      if (index === 0) return;
      this.expandedBoards[index] = !this.expandedBoards[index];
    },
    getMemberCardStyle(i, image) {
      return {
        width: '100%',
        height: '400px',
        backgroundSize: 'contain',
        position: 'relative',
        backgroundImage: `linear-gradient(${(i % 2 === 0 ? '' : '-')}90deg, transparent 25%, ${this.$vuetify.theme.global.current.dark ? '#1e1e1e' : 'white'} 34%, transparent 26%), url('${image}')`,
        backgroundPosition: (i % 2 === 0 ? 'left' : 'right'),
        borderTopLeftRadius: (i % 2 === 0 ? '10px' : ''),
        borderBottomLeftRadius: (i % 2 === 0 ? '10px' : ''),
        borderTopRightRadius: (i % 2 === 0 ? '' : '10px'),
        borderBottomRightRadius: (i % 2 === 0 ? '' : '10px'),
      }
    },
    getMemberInfoStyle(i) {
      return {
        position: 'absolute',
        top: '50%',
        transform: 'translateY(-50%)',
        paddingLeft: (i % 2 === 0 ? '35%' : ''),
        paddingRight: (i % 2 === 1 ? '35%' : '')
      }
    }
  }
}
</script>

<style scoped>
</style>
