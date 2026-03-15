import type TeamMember from "./TeamMember"

export default interface Team {
  name: string;
  bg: string;
  players: TeamMember[];
  coaches?: TeamMember[];
  substitutes?: TeamMember[];
}
