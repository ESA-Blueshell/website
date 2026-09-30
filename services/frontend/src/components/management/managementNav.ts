/** Who a reader is, as far as Management's navigation is concerned. */
export interface ManagementReader {
  board: boolean
  admin: boolean
}

/** One page in Management's navigation. */
export interface ManagementEntry {
  label: string
  to: string
  /** Only an admin opens it; the sidebar marks it. */
  adminOnly?: boolean
  /** Its place in the phone's bottom bar, where it has one. */
  tab?: "members"
}

/** A group of pages, named for the job they share. */
export interface ManagementGroup {
  label: string | null
  adminOnly?: boolean
  entries: ManagementEntry[]
}

const GROUPS: ManagementGroup[] = [
  {
    label: "Members",
    entries: [
      {label: "Users", to: "/management/users", tab: "members"},
      {label: "Account recovery", to: "/management/recovery"},
      {label: "Addresses", to: "/management/addresses"},
    ],
  },
  {label: "Mail", entries: [{label: "Sent", to: "/management/mail/sent"}]},
  {label: "Platforms", entries: [{label: "Brevo", to: "/management/platforms/brevo"}]},
  {
    label: "System",
    adminOnly: true,
    entries: [
      {label: "Jobs", to: "/management/jobs", adminOnly: true},
      {label: "Exceptions", to: "/management/exceptions", adminOnly: true},
    ],
  },
]

/** The groups and pages this reader may open, in the sidebar's order; what they cannot open is left out. */
export const managementFor = (reader: ManagementReader): ManagementGroup[] =>
  reader.board || reader.admin
    ? GROUPS.map((group) => ({...group, entries: group.entries.filter((entry) => !entry.adminOnly || reader.admin)}))
      .filter((group) => group.entries.length > 0)
    : []

/** Whether [path] is on the page [entry] leads to, or one below it. */
export const isOn = (path: string, entry: ManagementEntry): boolean => path === entry.to || path.startsWith(`${entry.to}/`)

/** Where Management opens: the first page the reader may use. */
export const firstPageFor = (reader: ManagementReader): string | null => managementFor(reader)[0]?.entries[0]?.to ?? null
