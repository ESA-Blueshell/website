import type BaseModel from "@/models/BaseModel";
import type SimpleCommitteeModel from "@/models/committee/SimpleCommitteeModel.ts";
import type FormQuestionModel from "@/models/FormQuestionModel.ts"
import type FileModel from "@/models/FileModel.ts"

export default interface EventModel extends BaseModel {
  id?: number;
  committeeId?: number;
  committee?: SimpleCommitteeModel;
  title: string;
  description: string;
  location?: string;
  startTime: string;
  endTime: string;
  memberPrice?: number;
  publicPrice?: number;
  visible?: boolean;
  membersOnly?: boolean;
  signUp?: boolean;
  banner?: FileModel;
  signUpForm?: FormQuestionModel[];
  googleId?: string;
}

export const defaultEvent: EventModel = {
  type: 'EventDTO',
  id: undefined,
  committeeId: undefined,
  committee: undefined,
  title: '',
  description: '',
  location: '',
  startTime: '',
  endTime: '',
  memberPrice: 0,
  publicPrice: 0,
  visible: false,
  membersOnly: false,
  signUp: false,
  banner: undefined,
  signUpForm: [],
};
