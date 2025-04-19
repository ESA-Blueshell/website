import BaseService from "./BaseService.ts";
import type EventModel from "@/models/EventModel.ts";

export default class EventService extends BaseService {
  constructor() {
    super("/events");
  }

  async createEvent(event: EventModel): Promise<EventModel> {
    return this.post(event);
  }

  async getEvent(id: number): Promise<EventModel> {
    return this.get(`/${id}`);
  }

  async getEvents(from?: string, to?: string): Promise<EventModel[]> {
    return this.get("", { from, to });
  }

  async getUpcomingEvents(editable = false): Promise<EventModel[]> {
    return this.get("/upcoming", { editable });
  }

  async getPastEvents(editable = false): Promise<EventModel[]> {
    return this.get("/past", { editable });
  }

  async updateEvent(id: number, event: EventModel): Promise<EventModel> {
    return this.put(event, `/${id}`);
  }

  async deleteEvent(id: number): Promise<void> {
    return this.delete(`/${id}`);
  }
}
