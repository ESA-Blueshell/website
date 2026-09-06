/**
 * Starting and ending memberships in bulk. A membership belongs to the user domain rather than
 * to contribution: what one costs is a contribution's business, whether somebody holds one is
 * this domain's.
 *
 * Re-exported under the names the dialogs read them by, so a component names an intention
 * rather than an endpoint.
 */
import {endMemberships, previewBulkEnd, previewBulkStart, startMemberships} from "@/services/api"

export const readMembershipStart = previewBulkStart
export const readMembershipEnd = previewBulkEnd
export const startTheMemberships = startMemberships
export const endTheMemberships = endMemberships
