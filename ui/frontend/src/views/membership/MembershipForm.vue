<template>
  <v-main>
    <top-banner title="Membership Form" />

    <div
      v-if="!succeeded"
      class="mx-3 pb-10"
    >
      <v-form
        ref="form"
        class="mx-auto mt-10"
        style="max-width: 600px"
      >
        <v-row>
          <v-col cols="4">
            <v-text-field
              ref="initials"
              v-model="form.initials"
              :disabled="loggedIn"
              :rules="initialsRules"
              label="Initials"
            />
          </v-col>
          <v-col cols="8">
            <v-text-field
              ref="firstName"
              v-model="form.firstName"
              :disabled="loggedIn"
              :rules="firstNameRules"
              label="First name"
            />
          </v-col>
        </v-row>
        <v-row class="mt-n7 mb-n5">
          <v-col cols="4">
            <v-text-field
              ref="prefix"
              v-model="form.prefix"
              :disabled="loggedIn"
              label="Surname Prefix"
            />
          </v-col>
          <v-col cols="8">
            <v-text-field
              ref="lastName"
              v-model="form.lastName"
              :disabled="loggedIn"
              :rules="lastNameRules"
              label="Surname"
            />
          </v-col>
        </v-row>
        <v-row>
          <nationality-select
            v-model="form.nationality"
            cols="4"
          />
          <country-select />
        </v-row>
        <v-row>
          <v-col cols="6">
            <v-text-field
              ref="username"
              v-model="form.username"
              :disabled="loggedIn"
              :rules="usernameRules"
              label="Username"
            />
          </v-col>
          <v-col cols="6">
            <v-text-field
              ref="email"
              v-model="form.email"
              :disabled="loggedIn"
              :rules="emailRules"
              label="Email"
              required
            />
          </v-col>
        </v-row>
        <v-row>
          <v-col cols="12">
            <v-text-field
              ref="discord"
              v-model="form.discord"
              label="Discord Username"
              :rules="discordRules"
            />
          </v-col>
        </v-row>
        <v-row v-if="!loggedIn">
          <v-col cols="6">
            <v-text-field
              v-model="form.password"
              :rules="passwordRules"
              label="Password"
              :append-icon="showPass ? 'mdi-eye' : 'mdi-eye-off'"
              :type="showPass ? 'text' : 'password'"
              @click:append="showPass = !showPass"
            />
          </v-col>
          <v-col cols="6">
            <v-text-field
              v-model="form.passwordAgain"
              :rules="[ v => !!v || 'Password is required', v => v===form.password || 'The passwords should be the same' ]"
              label="Password (repeated)"
              :append-icon="showPass ? 'mdi-eye' : 'mdi-eye-off'"
              :type="showPass ? 'text' : 'password'"
              @click:append="showPass = !showPass"
            />
          </v-col>
        </v-row>
        <v-row>
          <v-col cols="4">
            <v-text-field
              ref="postalCode"
              v-model="form.postalCode"
              label="Postal Code"
              :rules="postalCodeRules"
            />
          </v-col>
          <v-col cols="8">
            <v-text-field
              ref="city"
              v-model="form.city"
              label="Place of Residence"
              :rules="cityRules"
            />
          </v-col>
        </v-row>
        <v-row>
          <v-col cols="4">
            <v-text-field
              ref="dateOfBirth"
              v-model="form.dateOfBirth"
              label="Date of Birth"
              :rules="dateOfBirthRules"
              type="date"
            />
          </v-col>
          <v-col cols="8">
            <v-phone-input
              v-model="form.phoneNumber"
              :mode="'international'"
              :default-country="'nl'"
              :rules="phoneNumberRules"
              class="v-text-field__input"
              placeholder="Phone Number"
            />
          </v-col>
        </v-row>
        <v-row>
          <v-col cols="6">
            <v-checkbox
              v-model="form.ehbo"
              label="I have a EHBO Diploma"
            />
          </v-col>
          <v-col cols="6">
            <v-checkbox
              v-model="form.bhv"
              label="I have a BHV Diploma"
            />
          </v-col>
        </v-row>
        <v-row>
          <v-col cols="12">
            <v-checkbox
              v-model="form.newsletter"
              label="I want subscribe to the newsletter of Blueshell E-sports"
            />
          </v-col>
        </v-row>
        <v-spacer />
        <v-sheet
          class="pa-4"
          style="border-radius: 10px"
        >
          <strong>Membership conditions</strong><br>
          The undersigned hereby declares to be a member of Blueshell E-Sports Association Enschede until further
          notice. He/she hereby agrees to the Statutes, privacy policy and the Domestic Regulations (Huishoudelijk
          reglement) of this association. Cancellation must take place no later than four weeks before the beginning of
          the new academic year.
          <br>
          <br>
          <document-table />
          <br>
          <contribution-period is-form />
          <v-row
            class="mt-4"
            style="width: 100%;"
          >
            <v-input
              ref="signature"
              v-model="form.signature"
              :rules="signatureRules"
              hide-details="auto"
            >
              <v-row class="d-flex justify-center mb-1">
                <VueSignaturePad
                  ref="signaturePad"
                  style="aspect-ratio: 5/3"
                  :width="'100%'"
                  :options="{backgroundColor: 'rgba(255,255,255)'}"
                  :scale-to-device-pixel-ratio="true"
                />
              </v-row>
            </v-input>
          </v-row>
          <v-row class="d-flex justify-end mt-4">
            <v-btn
              type="button"
              class="btn btn-danger"
              @click="clearSignature"
            >
              Clear
            </v-btn>
          </v-row>
          <v-row>
            <v-col cols="6">
              <v-text-field
                ref="signatureCity"
                v-model="form.signatureCity"
                label="Place"
                :rules="signatureCityRules"
              />
            </v-col>
            <v-col cols="6">
              <v-text-field
                ref="signatureDate"
                v-model="form.signatureDate"
                type="date"
                label="Date"
                :rules="signatureDateRules"
                :disabled="true"
              />
            </v-col>
          </v-row>
        </v-sheet>
        <v-row>
          <v-spacer />
          <v-col cols="auto">
            <v-btn
              :loading="clicked"
              color="primary"
              @click="submitForm"
            >
              Submit
            </v-btn>
          </v-col>
        </v-row>
      </v-form>
    </div>

    <div
      v-else-if="succeeded"
      class="mx-auto my-10"
      style="max-width: 600px"
    >
      <p class="text-center text-subtitle-1 font-weight-lifght">
        Your membership form has successfully been submitted!
      </p>
    </div>
  </v-main>
</template>

<script>
import TopBanner from "@/components/banners/TopBanner.vue";
import {ref} from "vue";
import { DateTime } from 'luxon';
import {VPhoneInput} from "v-phone-input";
import {$handleNetworkError} from "@/plugins/handleNetworkError";
import ContributionPeriodComponent from "@/components/ContributionPeriodComponent.vue";
import {$goto} from "@/plugins/goto";
import NationalitySelect from "@/components/select/NationalitySelect.vue";
import CountrySelect from "@/components/select/CountrySelect.vue";
import DocumentTable from "@/components/DocumentTable.vue";

export default {
  components: {
    CountrySelect,
    NationalitySelect,
    DocumentTable,
    ContributionPeriod: ContributionPeriodComponent,
    VPhoneInput,
    TopBanner: TopBanner,
  },
  setup() {
    const signaturePad = ref(null);
    const phone = ref('');
    return {phone, signaturePad};
  },
  data: () => ({
    clicked: false,
    succeeded: false,
    showPass: false,
    loggedIn: false,
    form: {
      username: null,
      password: null,
      passwordAgain: null,
      email: null,
      firstName: null,
      lastName: null,
      initials: null,

      postalCode: null,
      phoneNumber: null,
      city: null,
      dateOfBirth: null,
      discord: null,

      ehbo: false,
      bhv: false,
      newsletter: false,

      signatureCity: null,
      signatureDate: DateTime.now().toISODate(),
      signature: null,
    },
    usernameRules: [
      v => !!v || 'Username is required',
      v => /^[a-zA-Z0-9]+$/.test(v) || 'Username must only contain alphanumeric characters',
    ],
    initialsRules: [
      v => !!v || 'Initials are required',
    ],
    firstNameRules: [
      v => !!v || 'First name is required',
    ],
    lastNameRules: [
      v => !!v || 'Surname is required',
    ],
    passwordRules: [
      v => !!v || 'Password is required',
      (v) => v.length >= 8 || 'Password must be at least 8 characters',
    ],
    dateOfBirthRules: [
      v => !!v || 'Date of birth is required',
    ],
    phoneNumberRules: [
      v => !!v || 'Phone number is required',
      v => !!v || !!v.match(/^[+]?[(]?[0-9]{3}[)]?[-\s.]?[0-9]{3}[-\s.]?[0-9]{4,6}$/) || 'Fill in a correct phone number'
    ],
    emailRules: [
      v => !!v || 'Email is required',
      v => (!!v && /^[a-zA-Z0-9_!#$%&’*+/=?`{|}~^.-]+@[a-zA-Z0-9.-]+$/.test(v)) || 'Enter a valid e-mail address',
      v => (!/student/i.test(v)) || 'You may not use your student email to sign up',
    ],
    discordRules: [
      v => !!v || 'Discord Username is required',
    ],
    postalCodeRules: [
      v => !!v || 'Postal code is required',
    ],
    cityRules: [
      v => !!v || 'Place of residence is required',
    ],
    signatureCityRules: [
      v => !!v || 'Place is required',
    ],
    signatureDateRules: [
      v => !!v || 'Date is required',
    ],
    signatureRules: [
      v => !!v || 'Signature is required',
    ]
  }),
  mounted() {
    const login = this.$store.getters.getLogin;
    this.loggedIn = !!login;
    if (login) {

      this.$http
        .get(`users/${login.userId}`, {headers: {'Authorization': `Bearer ${login.token}`}})
        .then(response => {
          Object.assign(this.form, response.data)
        })
    }
  },
  methods: {
    clearSignature() {
      this.signaturePad.clearSignature()
    },
    async submitForm() {
      const {isEmpty, data} = this.signaturePad.saveSignature('image/png')
      if (isEmpty) {
        this.form.signature = null;
      } else {
        const image = new Image();
        image.src = data;

        // Create a promise to await the image loading and scaling process
        const scaledData = await new Promise((resolve) => {
          image.onload = () => {
            const canvas = document.createElement("canvas");
            canvas.width = 500;  // Set the canvas width to your desired scaling
            canvas.height = 300; // Set the canvas height to your desired scaling

            // Get the 2D drawing context
            const ctx = canvas.getContext("2d");

            // Scale and draw the image on the canvas
            ctx.drawImage(image, 0, 0, 500, 300);

            // Get the scaled image data
            const scaledImageData = canvas.toDataURL();
            resolve(scaledImageData);
          };
        });

        // Set the scaled signature to the form (Base64 part only)
        this.form.signature = scaledData.split(",")[1];
      }

      const {valid} = await this.$refs.form.validate()

      if (!valid) {
        return;
      }

      this.clicked = true;

      const login = this.$store.getters.getLogin;

      var request = null;
      if (login) {
        request = this.$http.put(`users/${login.userId}`, this.form, {headers: {'Authorization': `Bearer ${login.token}`}})
      } else {
        request = this.$http.post('users', this.form)
      }
      request
        .then(() => {
          this.succeeded = true;
          const login = this.$store.getters.getLogin;
          // If succesfull update the roles so that the user is now a member
          if (login) {
            this.$http
              .get(`users/${login.userId}`, {headers: {'Authorization': `Bearer ${login.token}`}})
              .then(response => {
                this.$store.commit('setRoles', response.data.roles)
                $goto('/')
              })
          }
        })
        .catch(e => {
          if (e.response?.status === 400) {
            this.$store.commit('setStatusSnackbarMessage', e.response.data)
          } else {
            $handleNetworkError(e)
          }
        })
        .finally(() => {
          this.clicked = false;
        });
    },
  }
};
</script>

<style scoped>
.v-col:first-child {
  padding-left: 0;
}

.v-col:last-child {
  padding-right: 0;
}
</style>
