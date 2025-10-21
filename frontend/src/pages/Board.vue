<template>
  <v-main>
    <top-banner title="Board" />

    <div
      class="mx-auto px-4"
      style="max-width: 960px"
    >
      <template
        v-for="(board, boardIndex) in boards"
        :key="board.name"
      >
        <section class="my-3">
          <template v-if="boardIndex === 0">
            <h1 class="text-center">
              {{ board.name }}
            </h1>

            <v-img
              v-if="board.boardImage"
              :src="board.boardImage"
              class="rounded-lg mb-6"
              cover
            />

            <template
              v-for="(member, i) in board.members"
              :key="member.name"
            >
              <board-member-row
                :member="member"
                :reverse="i % 2 === 1"
              />
            </template>
          </template>

          <template v-else>
            <v-card
              role="button"
              :aria-expanded="String(expandedBoards[boardIndex])"
              style="display:flex; align-items:center; justify-content:space-between; cursor:pointer;"
              class="px-5"
              @click="toggleBoard(boardIndex)"
            >
              <h2>{{ board.name }}</h2>
              <v-icon
                size="24"
                color="grey-darken-1"
              >
                {{ expandedBoards[boardIndex] ? "mdi-chevron-up" : "mdi-chevron-down" }}
              </v-icon>
            </v-card>

            <v-expand-transition>
              <div
                v-show="expandedBoards[boardIndex]"
                class="mt-5"
              >
                <v-img
                  v-if="board.boardImage"
                  :src="board.boardImage"
                  class="rounded-lg mb-6"
                  cover
                />

                <template
                  v-for="(member, i) in board.members"
                  :key="member.name"
                >
                  <board-member-row
                    :member="member"
                    :reverse="i % 2 === 1"
                  />
                </template>
              </div>
            </v-expand-transition>
          </template>
        </section>
      </template>
    </div>
  </v-main>
</template>

<script lang="ts" setup>
import {reactive, ref} from "vue"
import TopBanner from "@/components/common/banners/TopBanner.vue"
import {$require} from "@/plugins/require.ts"
import BoardMemberRow from "@/components/common/rows/BoardMemberRow.vue"

interface Member {
  name: string
  title: string
  description?: string
  image?: string
}

interface Board {
  name: string
  boardImage?: string
  members: Member[]
}

const boards = ref<Board[]>([
  {
    name: "9th Board",
    boardImage: $require("@/assets/board9/board9.jpg"),
    members: [
      {
        name: "Emma Dokter",
        title: "Chair",
        description:
          "Hi everyone, My name is Emma 'LyndisLuna' Dokter, and I’m the 9th Chair of our beloved Blueshell. I am a Psychology student here at the University of Twente, and I have always been passionate about games, whether that is a console game like the Legend of Zelda, or a MOBA like League of Legends. I am a part of the League team “Pandora” in our association, and also created the cozy games committee “ChillCie” last year. I am extremely grateful for the opportunity to be Blueshell’s chair, and can’t wait to have an amazing year with everyone in the association. So if you see me around, don’t hesitate to come have a chat with me!",
        image: $require("@/assets/board9/Emma.jpg"),
      },
      {
        name: "Viktor Petrov",
        title: "Secretary",
        description:
          "Hello fellow gamers! I'm Viktor 'AriosFury' Petrov, this year's Secretary of Blueshell, now in my third year of Mechanical Engineering at UT. I joined the association on a whim last year, wanting to find people in Enschede to play Valorant with. Although originally I didn't participate in many events, I slowly found my crowd - especially by playing with the awesome BS Waterboarders - and began being at every event I had time for. Later, I joined some committees which I also found pretty fun! Blueshell gave me an amazing community and some very good friends, so I wanted to contribute to the growth of the association by becoming board. Hopefully, we'd all have a very fun year and I'm waiting to see you at the events!",
        image: $require("@/assets/board9/Viktor.jpg"),
      },
      {
        name: "Taha Aydin",
        title: "Treasurer",
        description:
          "Hi my name is Taha 'Talpa' Aydin,\nThis year ill be the treasurer for this lovely association. I am 25 years old have been living most of my life in Amsterdam en Hoofddorp. A year ago I decide to move to Enschede to study Health science at the University of Twente. I started loving gaming when I was young by playing a lot of Nintendo games after that I got addicted to shooters and league of legends. I am excited to manage the finances and make it a fun year for all of us.\n",
        image: $require("@/assets/board9/Taha.jpg"),
      },
      {
        name: "Sylwia Siekman",
        title: "Commissioner of Internal Affairs",
        description:
          "Hi! I am Sylwia 'SylWorld' Siekman and I will be the Commissioner of Internal Affairs this year for Blueshell. I am currently working on my 3rd year of Psychology. I have been always active around the University of Twente, from organising events to CCP. I joined Blueshell two years ago and created my own committee with a friend, SacrifiCie. Besides playing Valorant till 2am, I also play a lot of other genres so you can talk with me about anything. Furthermore, I have every month a new hyperfixation. Oh yeah, be warned, I am a yapper. Let's have another great year!\n",
        image: $require("@/assets/board9/Sylwia.jpg"),
      },
      {
        name: "Boris Kusters",
        title: "Commissioner of External Affairs",
        description:
          "Hello everyone! I'm Boris 'JakobDutch' Kusters, and I'm excited to be the Commissioner of External Affairs for the 9th Board of Blueshell. I just recently graduated from Creative Media & Game Technologies at Saxion. When I'm not busy building up my own company, Marketing Maatwerk, you can usually find me at the gym or playing CS2 with the guys from BS HyperS. I joined Blueshell about four years ago, but I truly became active after I joined HyperS. Through this community, I've found many new close friends, and I hope to meet a lot more of you this year!",
        image: $require("@/assets/board9/Boris.jpg"),
      },
      {
        name: "Rene Hammink",
        title: "Commissioner of Esports Affairs",
        description:
          "Hello! I am René 'Mr. Pancake^-^' Hammink and I am proud to be the commisioner of Esports affairs of the 9th board of Blueshell. In my free time I like to develop games or do related projects and not so coinsidentally I am in my second year of Creative Media and Gaming Technologies at Saxion. Apart from playing or making videogames I like to skateboard, play DnD and ponder about philosophical questions or topics. I joined Blueshell in 2022 when I came to Enschede, mostly being interested in the Esports teams within Blueshell. Now after a few years of being a part of this awesome community I found myself more active and wanting to give back to it, and together with the rest of my boardmates I think we'll be able to do just that!",
        image: $require("@/assets/board9/Rene.jpg"),
      },
    ],
  },
  {
    name: "8th Board",
    boardImage: $require("@/assets/board8/board8.jpg"),
    members: [
      {
        name: "Michal Rokita",
        title: "Chair",
        description:
          "Hello! I’m Michal 'udeyy' Rokita, 21 years old, and Chair of Blueshell. I was born in Warsaw, Poland, and spent much of my life in Belgium and the Netherlands before coming to Enschede to study Industrial Design Engineering at UT. I've always loved gaming (especially Valorant), but also enjoy sports like running and swimming. I’m excited to represent Blueshell and help it thrive!",
        image: $require("@/assets/board8/Michal.png"),
      },
      {
        name: "Joris Jonkers",
        title: "Secretary and Commissioner of External Affairs",
        description:
          "Heyoo! I’m Joris ‘ExtraToast’ Jonkers, 26 years old, studying Computer Science, and serving as Secretary and Commissioner of External Affairs. I’ve been gaming since childhood, from old consoles to PC gaming. I love working on old vehicles, collecting retro games, and going to concerts and festivals. I look forward to keeping Blueshell organized and connecting with more gaming enthusiasts!",
        image: $require("@/assets/board8/Joris.png"),
      },
      {
        name: "Chris Wong",
        title: "Treasurer and Commissioner of Esports affairs",
        description:
          "Hey there! I’m Chris 'FetaBass' Wong, 20 years old from Apeldoorn, studying TCS (and possibly switching to BIT). I grew up with Pokémon and moved on to titles like MapleStory, RuneScape, and Minecraft. Nowadays, I’m really into Valorant, League of Legends, and CS2. I’m excited to manage our finances and help foster our esports community!",
        image: $require("@/assets/board8/Chris.png"),
      },
      {
        name: "Yannick Sloot",
        title: "Commissioner of Internal Affairs",
        description:
          "Yoo guys! I’m Yannick 'Yank' Sloot, 20 years old from Biddinghuizen, studying International Business Administration at UT. I've been gaming since I was a kid—from the GBA to PC, with favorites like CS2, Terraria, and Dark Souls. Besides gaming, I enjoy music and traveling. I’m looking forward to ensuring everything runs smoothly within Blueshell!",
        image: $require("@/assets/board8/Yannick.png"),
      },
    ],
  },
  {
    name: "7th Board",
    boardImage: $require("@/assets/board7/board7.jpg"),
    members: [
      {
        name: "Reini Strating",
        title: "Chair",
        description:
          "Hello, I'm Reini Strating but you can call me whatever you want – there is no limit. I'm still searching for a suitable study, that's why this is my break year! I enjoy playing any type of games if I get invited. Always trying to have fun with others, whatever suits their boat. I joined Blueshell after a friend introduced me to it last year and got me instantly hooked with the friendliness and good times. It got me excited enough to join the board and here I am! CHAIR ready for base-service.yaml!",
        image: $require("@/assets/board7/reinout.jpg"),
      },
      {
        name: "Max Jansdam",
        title: "Secretary and Commissioner of the Esports Lounge",
        description:
          "Hey, I am Max Jansdam and I am the Secretary and Commissioner of the Esports Lounge affairs for this year. This year I will be a third-year Psychology bachelor. I joined Blueshell 2 years ago during corona with the idea of finding people to play smash ultimate and just dance with. While I didn’t join many events in the first year, I started joining and organizing more in my second year. After friends asked me to join the next board, I thought, why not? Let’s make this an amazing year!",
        image: $require("@/assets/board7/max.jpg"),
      },
      {
        name: "Jesse van Gameren",
        title: "Treasurer and Commissioner of Esports Affairs",
        description:
          "Hello fellow people! My name is Jesse van Gameren and I am the Treasurer and Commissioner of Esports affairs of Blueshell this year. Currently, I am in my third year of Industrial Engineering and Management. I became a member of Blueshell 2 years ago after being invited to a few events where I found a great community! I like to play games and do just about anything, as long as it's with friends. After joining more events and committees, I wanted to try something new – to learn more myself and give back to the community that gives me so much joy. Let’s make this a wonderful year to remember!",
        image: $require("@/assets/board7/jesse.jpg"),
      },
      {
        name: "Mitchell van Poecke",
        title: "Officer of Internal Affairs",
        description:
          "Hey, what's up! I am Mitchell van Poecke and this year I will be the Officer of Internal Affairs of Blueshell. I’m currently in my third year of Creative Technology and this will also be my third year with Blueshell. I enjoy going for a walk, working on game prototypes and, of course, playing games with friends. In my first year I attended most Minecraft events and a few game nights – which led me to join a committee. At first, the idea of a board year didn’t speak to me, but after a few months in a committee, I got excited and joined the board. I hope we can have another fun and exciting year together!",
        image: $require("@/assets/board7/mitchell.jpg"),
      },
      {
        name: "Sanne van Kooten",
        title: "Officer of External Affairs",
        description:
          "Hello! I am Sanne van Kooten, and I am this year's Officer of External Affairs! Currently, I’m a fourth-year Biomedical Engineering bachelor. In addition to being creative and visiting concerts, I also love gaming. I joined Blueshell in the second year of my studies after participating in the Nintendo pubquiz during the kick-off, and I was immediately enthusiastic. After joining a few committees, I decided it was time to do something bigger – like being a board member. I look forward to creating an even bigger gaming community with lots of fun events!",
        image: $require("@/assets/board7/sanne.jpg"),
      },
    ],
  },
  {
    name: "6th Board",
    boardImage: $require("@/assets/board6/board6.jpg"),
    members: [
      {name: "Amber \"Ambanana\" Scholtz", title: "Chair", image: $require("@/assets/board6/amber.jpg")},
      {name: "Thomas \"ItIsIThomas\" Dekker", title: "Treasurer", image: $require("@/assets/board6/thomas.jpg")},
      {
        name: "Jelle \"TheJellyMan\" van Wezep",
        title: "Secretary and Commissioner of the Esports Lounge",
        image: $require("@/assets/board6/jelle.jpg"),
      },
      {
        name: "Jonas \"Clunky\" Valentijn",
        title: "Commissioner of Esports Affairs",
        image: $require("@/assets/board6/jonas.jpg"),
      },
      {
        name: "Roos \"SkyeWolf\" Kruk",
        title: "Commissioner of Internal Affairs",
        image: $require("@/assets/board6/roos.jpg"),
      },
      {
        name: "Thijs \"Darkneoteric\" Willems",
        title: "Commissioner of External Affairs",
        image: $require("@/assets/board6/thijs.jpg"),
      },
    ],
  },
  {
    name: "5th Board",
    boardImage: $require("@/assets/board5/board5.jpg"),
    members: [
      {name: "Daniël \"thiefzz\" Floor", title: "Chairman"},
      {name: "Bob \"Bobbuz\" Even", title: "Treasurer and Commissioner of Esports Affairs"},
      {name: "Louis \"Poking\" Hu", title: "Secretary"},
      {name: "Jelle \"Zenga\" Idzenga", title: "Commissioner of Internal Affairs"},
      {name: "Ìlayda \"Vriendelijke kebab\" Hotamis", title: "Commissioner of External Affairs"},
    ],
  },
  {
    name: "4th Board",
    members: [
      {name: "Ali “Kelbinoh” Kalbiyev", title: "Chair"},
      {name: "Tjebbe “Issie54” Iskander Sterck", title: "Treasurer"},
      {name: "Ivo “King Cookie” Heitlager", title: "Secretary and Commissioner of Esports Affairs"},
      {name: "Lucia \"Luna\" Kim", title: "Commissioner of Internal Affairs"},
      {name: "Jose “Bear” Pratdesaba Lopez", title: "Commissioner of External Affairs"},
    ],
  },
  {
    name: "3rd Board",
    members: [
      {name: "Jander “Thoran” Gilbers", title: "Chairman"},
      {name: "Joran “MacVanish” Hagen", title: "Secretary/Treasurer"},
      {name: "Andrei “ElDonte” Raureanu", title: "Commissioner of Esports"},
      {name: "William “Lampekap88” Schaarman", title: "Commissioner of internal affairs"},
      {name: "Allysha “Meavis” Sewradj", title: "Commissioner of external affairs"},
    ],
  },
  {
    name: "2nd Board",
    members: [
      {name: "Jasper “JappieXD” van Harten", title: "Chairman"},
      {name: "Kimberly “Agile Manifesto” Evertz", title: "Secretary"},
      {name: "Mauk “Dawarfmaster” Muller", title: "Treasurer"},
      {name: "Antal “TheMadJokerHD” van Dongen", title: "Commissioner of Internal Affairs"},
      {name: "Maiander “Maiantie” Eigenraam", title: "Commissioner of External Affairs"},
    ],
  },
  {
    name: "1st Board",
    members: [
      {name: "Thijs “Grootbuik” Lieverse", title: "Chairman"},
      {name: "Jan-Berend “JB” Mooijaart", title: "Secretary"},
      {name: "Anne “Iadri” Schrader", title: "Treasurer"},
      {name: "Stijn “Salish” Overduin", title: "Commissioner of Internal Affairs"},
      {name: "Idwer “Fangedsheep” de Vries", title: "Commissioner of External Affairs"},
    ],
  },
])

const expandedBoards = reactive<boolean[]>([])
boards.value.forEach((_, index: number) => (expandedBoards[index] = index === 0))

function toggleBoard(index: number): void {
  if (index === 0) return
  expandedBoards[index] = !expandedBoards[index]
}
</script>
