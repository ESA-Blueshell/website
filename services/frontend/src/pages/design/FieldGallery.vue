<script lang="ts" setup>
/* Every island field on one page, in both halves of the theme. Dev only: the route that
   reaches it is registered only when `import.meta.env.DEV`. */
import {ref} from "vue"
import CohortPicker from "@/components/form/fields/CohortPicker.vue"
import ContributionPeriodPicker from "@/components/form/fields/ContributionPeriodPicker.vue"
import CountrySelect from "@/components/form/fields/CountrySelect.vue"
import EnumPicker from "@/components/form/fields/EnumPicker.vue"
import EventPicker from "@/components/form/fields/EventPicker.vue"
import MemberTypeSelect from "@/components/form/fields/MemberTypeSelect.vue"
import NationalitySelect from "@/components/form/fields/NationalitySelect.vue"
import UserPicker from "@/components/form/fields/UserPicker.vue"
import UserSelect from "@/components/form/fields/UserSelect.vue"
import CheckBox from "@/components/island/CheckBox.vue"
import FileInput from "@/components/island/FileInput.vue"
import FormControl from "@/components/island/FormControl.vue"
import FormField from "@/components/island/FormField.vue"
import RadioGroup from "@/components/island/RadioGroup.vue"

const text = ref("")
const email = ref("joris@blueshell.nl")
const password = ref("")
const said = ref("A description, as long as it needs to be.")
const written = ref("## What we play\n\nA **bold** claim, a [link](https://blueshell.nl) and 🍝.")
const phone = ref("")
const country = ref<string | null>("NL")
const nationality = ref<string | null>("NL")
const born = ref("")
const starts = ref("19:30")
const fee = ref("30.00")
const agreed = ref(false)
const keeps = ref(true)
const pick = ref("weekly")
const picture = ref<File | null>(null)

const wrong = ref("")
const wrongDate = ref("")
const wrongFee = ref("")

const person = ref<number | undefined>(undefined)
const member = ref<number | undefined>(undefined)
const cohort = ref<number | undefined>(undefined)
const period = ref<number | undefined>(undefined)
const event = ref<number | undefined>(undefined)
const state = ref<string | undefined>(undefined)

const dark = ref(true)
</script>

<template>
  <!-- The switch holds both ways whatever the site's theme: dark is pinned on the island, and
       light comes from the theme attribute island.css reads off an ancestor. -->
  <div :data-theme="dark ? 'dark' : 'light'">
    <div
      class="island gallery"
      :class="{'island-dark': dark}"
    >
      <header class="gallery__head">
        <h1 class="gallery__title">
          The island's fields
        </h1>
        <button
          class="gallery__theme"
          type="button"
          @click="dark = !dark"
        >
          {{ dark ? "Read it light" : "Read it dark" }}
        </button>
      </header>

      <section class="gallery__set">
        <h2 class="gallery__what">
          Typed
        </h2>
        <div class="gallery__rows">
          <form-control
            v-model="text"
            hint="What a hint reads like under a field."
            kind="text"
            label="Initials*"
          />
          <form-control
            v-model="email"
            kind="email"
            label="E-mail*"
          />
          <form-control
            v-model="password"
            kind="password"
            label="Password*"
          />
          <form-control
            v-model="wrong"
            :error-messages="['This field is required']"
            kind="text"
            label="Refused*"
          />
          <form-control
            v-model="text"
            disabled
            kind="text"
            label="Off"
          />
          <form-control
            v-model="said"
            kind="textarea"
            label="A longer answer"
          />
        </div>
      </section>

      <section class="gallery__set">
        <h2 class="gallery__what">
          Chosen
        </h2>
        <div class="gallery__rows">
          <form-control
            v-model="phone"
            kind="phone"
            label="Phone Number*"
          />
          <form-control
            v-model="country"
            kind="country"
            label="Country*"
          />
          <form-control
            v-model="nationality"
            kind="nationality"
            label="Nationality*"
          />
          <country-select
            v-model="country"
            label="Country, as its own field"
          />
          <nationality-select
            v-model="nationality"
            label="Nationality, as its own field"
          />
          <user-picker
            v-model="person"
            label="Somebody, from the whole listing"
          />
          <user-picker
            v-model="person"
            label="Only a member"
            members-only
          />
          <user-select
            v-model="member"
            label="Somebody, searched by the api"
            :users="[]"
          />
          <member-type-select
            v-model="state"
            label="Kind of member"
          />
          <enum-picker
            v-model="state"
            label="One of a fixed set"
            :values="['CONTRIBUTION_PAID', 'CONTRIBUTION_DUE', 'NO_CONTRIBUTION']"
          />
          <cohort-picker
            v-model="cohort"
            label="Cohort"
          />
          <contribution-period-picker
            v-model="period"
            label="Contribution period"
          />
          <event-picker
            v-model="event"
            label="Event"
          />
        </div>
      </section>

      <section class="gallery__set">
        <h2 class="gallery__what">
          Numbers, days and times
        </h2>
        <div class="gallery__rows">
          <form-control
            v-model="born"
            kind="date"
            label="Date of Birth*"
          />
          <form-control
            v-model="wrongDate"
            :error-messages="['Date is required']"
            kind="date"
            label="A day, refused*"
          />
          <form-control
            v-model="starts"
            kind="time"
            label="Starts at*"
          />
          <form-control
            v-model="fee"
            hint="Two places, never below nothing."
            kind="money"
            label="Contribution*"
          />
          <form-control
            v-model="wrongFee"
            :error-messages="['Say what it costs']"
            kind="money"
            label="A price, refused*"
          />
        </div>
      </section>

      <section class="gallery__set">
        <h2 class="gallery__what">
          Ticked, picked and uploaded
        </h2>
        <div class="gallery__rows">
          <check-box
            v-model="agreed"
            label="I have read and agree to the Privacy Policy."
          />
          <check-box
            v-model="keeps"
            hint="Small print sits under the box."
            label="Keep me signed in on this machine."
          />
          <radio-group
            v-model="pick"
            label="How often"
            :options="[
              {value: 'weekly', label: 'Every week'},
              {value: 'monthly', label: 'Every month'},
              {value: 'never', label: 'Never'},
            ]"
          />
          <form-field
            label="A picture"
            variant="stacked"
          >
            <file-input
              v-model="picture"
              accept="image/*"
              say="Pick a picture"
            />
          </form-field>
        </div>
      </section>

      <section class="gallery__set">
        <h2 class="gallery__what">
          Written and read at once
        </h2>
        <form-control
          v-model="written"
          kind="markdown"
          label="Description*"
        />
      </section>
    </div>
  </div>
</template>

<style scoped>
.gallery {
  min-height: 100vh;
  padding: 2rem clamp(1rem, 4vw, 4rem) 6rem;
  background-color: var(--color-ground);
}

.gallery__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 2rem;
}

.gallery__title {
  font-family: var(--font-display);
  font-size: clamp(1.6rem, 4vw, 2.6rem);
  color: var(--color-chalk);
  text-transform: uppercase;
}

.gallery__theme {
  padding: 0.4rem 0.9rem;
  font-family: var(--font-bitmap);
  font-size: 0.72rem;
  color: var(--color-chalk);
  text-transform: uppercase;
  cursor: pointer;
  background: none;
  border: 1px solid var(--color-hairline);
}

.gallery__set {
  margin-bottom: 2.5rem;
}

.gallery__what {
  margin-bottom: 0.8rem;
  font-family: var(--font-bitmap);
  font-size: 0.75rem;
  color: var(--color-ash);
  text-transform: uppercase;
}

.gallery__rows {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(20rem, 1fr));
  gap: 0.6rem 1.5rem;
  align-items: start;
}
</style>
