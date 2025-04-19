import type {Role} from "@/models/enums/Role";

export default interface JwtResponse {
  token: string;
  userId: number;
  username: string;
  expiration: number;
  roles: Role[];
}
