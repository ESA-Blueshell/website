import BaseService from "./BaseService.ts";
import type EventSignUpModel from "@/models/EventSignUpModel.ts";

export default class EventSignUpService extends BaseService {
  constructor() {
    super("/events");
  }

  async getEventSignUps(eventId: number): Promise<EventSignUpModel[]> {
    return this.get(`/${eventId}/signups`);
  }

  async getSignups(): Promise<EventSignUpModel[]> {
    return this.get("/signups");
  }

  async createSignUp(eventId: number, signUp: EventSignUpModel): Promise<EventSignUpModel> {
    return this.post(signUp, `/${eventId}/signups`);
  }

  async updateSignUp(eventId: number, signUp: EventSignUpModel): Promise<EventSignUpModel> {
    return this.put(signUp, `/${eventId}/signups`);
  }

  async deleteSignUp(eventSignUpId: number, accessToken?: string): Promise<void> {
    return this.delete(`/signups/${eventSignUpId}`, { accessToken });
  }

  async getByAccessToken(accessToken: string): Promise<EventSignUpModel> {
    return this.get(`/signups/byAccessToken/${accessToken}`);
  }
}
