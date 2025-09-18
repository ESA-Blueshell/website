import TeamMember from "./TeamMember";

interface Team {
  name: string;
  bg: string;
  players: TeamMember[];
  coaches: TeamMember[];
  substitutes: TeamMember[];
}
