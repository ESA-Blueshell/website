import type BaseModel from "@/models/BaseModel";

export type FormAnswer = 'open' | 'radio' | 'checkbox';

export default interface EventSignUpModel extends BaseModel {
  eventId?: number;
  fullName?: string;
  discord?: string;
  email?: string;
  formAnswers?: FormAnswer[];
}

export const defaultEventSignUp: EventSignUpModel = {
  id: 0,
  type: 'EventSignUpDTO',
  eventId: undefined,
  fullName: '',
  discord: '',
  email: '',
  formAnswers: [],
};
