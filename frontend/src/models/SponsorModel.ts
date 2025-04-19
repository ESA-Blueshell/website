import type BaseModel from "@/models/BaseModel";


export default interface SponsorModel extends BaseModel {
  name: string;
  description: string;
}

export const defaultSponsor: SponsorModel = {
  type: 'SponsorDTO',
  name: '',
  description: '',
};
