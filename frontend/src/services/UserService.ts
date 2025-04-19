import BaseService from "./BaseService.ts";
import type {AdvancedUserModel} from "@/models";
import type ActivationRequest from "@/models/requests/ActivationRequest";
import type PasswordResetRequest from "@/models/requests/PasswordResetRequest";
import type {Role} from "@/models/enums/Role";

export default class UserService extends BaseService {
  constructor() {
    super("/users");
  }

  async getUsers(isMember?: boolean): Promise<AdvancedUserModel[]> {
    return this.get("", {isMember});
  }

  async getUser(id: number): Promise<AdvancedUserModel> {
    return this.get(`/${id}`);
  }

  async createUser(user: AdvancedUserModel): Promise<AdvancedUserModel> {
    return this.post(user);
  }

  async updateUser(id: number, user: AdvancedUserModel): Promise<AdvancedUserModel> {
    return this.put(user, `/${id}`);
  }

  async resetPassword(username: string): Promise<void> {
    return this.post(null, "/reset", {username});
  }

  async activate(request: ActivationRequest): Promise<void> {
    return this.post(request, "/activate");
  }

  async setPassword(request: PasswordResetRequest): Promise<void> {
    return this.post(request, "/password");
  }

  async toggleRole(userId: number, role: Role): Promise<AdvancedUserModel> {
    return this.put(null, `/${userId}/roles`, {role});
  }

  async deleteUser(userId: number): Promise<void> {
    return this.delete(`/${userId}`);
  }

  async getFromBrevo(email: string): Promise<AdvancedUserModel> {
    return this.get("/brevo", {email});
  }
}
