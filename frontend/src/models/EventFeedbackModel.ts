import type BaseModel from "@/models/BaseModel";

export default interface EventFeedbackModel extends BaseModel {
  id: number;
  feedback?: string;
  eventId: number;
}

export const defaultEventFeedback: EventFeedbackModel = {
  type: 'EventFeedbackDTO',
  id: 0,
  feedback: '',
  eventId: 0,
};
