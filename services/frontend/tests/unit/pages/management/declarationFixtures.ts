import {DeclarationState, type DeclarationResponse} from "@/services/api"

/** A declaration as the api answers it, submitted by Ann Vos with one expense booked to an event. */
export const answered = (id: number, state: DeclarationState, fields: Partial<DeclarationResponse> = {}): DeclarationResponse => ({
  id,
  state,
  submitterId: 12,
  submitter: "Ann Vos",
  confirmed: true,
  createdAt: "2026-10-01T10:00:00Z",
  expenses: [{
    id: id * 10,
    paidOn: "2026-09-30",
    description: "Pizza for the committee meeting",
    amountCents: 1250,
    bookedTo: {kind: "EVENT", id: 7, name: "Autumn LAN party"},
    receipts: [{id: 501, name: "bill.webp", mediaType: "image/webp", url: "/declarations/receipts/501"}],
  }],
  receipts: [],
  steps: [
    {state: DeclarationState.DRAFT, at: "2026-10-01T10:00:00Z", by: "Ann Vos"},
    {state: DeclarationState.SUBMITTED, at: "2026-10-02T10:00:00Z", by: "Ann Vos"},
  ],
  ...fields,
})
