import type BaseModel from "@/models/BaseModel";

export default interface GuestModel extends BaseModel {
  id: number;
  name?: string;
  discord?: string;
  email?: string;
  createdAt?: string;
  accessToken?: string;
}

export const defaultGuest: GuestModel = {
  type: 'GuestDTO',
  id: 0,
  name: '',
  discord: '',
  email: '',
  createdAt: '',
  accessToken: '',
};
