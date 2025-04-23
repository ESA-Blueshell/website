<template>
  <v-sheet
    class="pa-4"
    style="border-radius: 10px"
  >
    <strong>Membership conditions</strong><br>
    The undersigned hereby declares to be a member of Blueshell E-Sports Association Enschede until further
    notice. He/she hereby agrees to the Statutes, privacy policy and the Domestic Regulations (Huishoudelijk
    reglement) of this association. Cancellation must take place no later than four weeks before the beginning of
    the new academic year.
    <br><br>
    <document-table />
    <br>
    <contribution-period
      v-model="localMembership.memberType"
      is-form
    />
    <v-row
      class="mt-4"
      style="width: 100%;"
    >
      <v-input
        ref="signature"
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
          ref="city"
          v-model="localMembership.city"
          label="Place"
          :rules="cityRules"
        />
      </v-col>
      <v-col cols="6">
        <v-text-field
          ref="date"
          v-model="localMembership.date"
          type="date"
          label="Date"
          :rules="dateRules"
          :disabled="true"
        />
      </v-col>
    </v-row>
  </v-sheet>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import { DateTime } from 'luxon';
import DocumentTable from "@/components/DocumentTable";
import ContributionPeriod from "@/components/ContributionPeriodComponent";
import type { MembershipModel } from "@/models/MembershipModel.ts";

export default defineComponent({
  name: "MembershipEdit",
  components: {
    DocumentTable,
    ContributionPeriod,
  },
  props: {
    modelValue: {
      type: Object as () => Membership,
      required: true,
    },
  },
  emits: ['update:modelValue'],
  data() {
    return {
      localMembership: { ...this.modelValue } as Membership,
      signatureRules: [
        (v: any) => !!v || 'Signature is required',
      ],
      cityRules: [
        (v: string) => !!v || 'Place is required',
      ],
      dateRules: [
        (v: string) => !!v || 'Date is required',
      ],
    };
  },
  watch: {
    modelValue: {
      handler(newVal: Membership) {
        this.localMembership = { ...newVal };
      },
      deep: true,
      immediate: true,
    },
    localMembership: {
      handler(newVal: Membership) {
        this.$emit('update:modelValue', newVal);
      },
      deep: true,
    },
  },
  created() {
    if (!this.localMembership.date) {
      this.localMembership.date = DateTime.now().toISODate();
    }
  },
  methods: {
    clearSignature() {
      (this.$refs.signaturePad as any).clearSignature();
      this.localMembership.signature = null;
    },
    async saveSignature() {
      const signaturePad = this.$refs.signaturePad as any;
      const { isEmpty, data } = signaturePad.saveSignature('image/png');
      if (isEmpty) {
        this.localMembership.signature = null;
      } else {
        const scaledData = await this.scaleSignature(data);
        this.localMembership.signature = scaledData.split(',')[1];
      }
      this.$emit('update:modelValue', this.localMembership);
    },
    scaleSignature(data: string): Promise<string> {
      return new Promise((resolve) => {
        const image = new Image();
        image.src = data;
        image.onload = () => {
          const canvas = document.createElement('canvas');
          canvas.width = 500;
          canvas.height = 300;
          const ctx = canvas.getContext('2d');
          if (ctx) {
            ctx.drawImage(image, 0, 0, 500, 300);
            resolve(canvas.toDataURL());
          }
        };
      });
    },
  },
});
</script>
