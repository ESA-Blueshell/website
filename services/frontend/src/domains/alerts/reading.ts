import {type Alert, AlertKind} from "./adapters/alerts"

const counted = (count: number, one: string, many: string): string => `${count} ${count === 1 ? one : many}`

/** What an alert says, composed here from what the api sends. */
export function alertTitle(alert: Alert): string {
  switch (alert.kind) {
    case AlertKind.JOB_DEAD:
      return `${counted(alert.count, "job is", "jobs are")} dead`
    case AlertKind.EXCEPTION_OPEN:
      return `${counted(alert.count, "exception is", "exceptions are")} open`
  }
}

/** An alert as a short row: what is wrong, what it is about, and the page it comes from. */
export function alertRow(alert: Alert): {name: string; meta: string; from: string} {
  switch (alert.kind) {
    case AlertKind.JOB_DEAD:
      return {name: alertTitle(alert), meta: "Every retry failed", from: "Jobs"}
    case AlertKind.EXCEPTION_OPEN:
      return {name: alertTitle(alert), meta: "Not resolved yet", from: "Exceptions"}
  }
}

/** Where an alert is dealt with. */
export function alertLink(alert: Alert): string {
  switch (alert.kind) {
    case AlertKind.JOB_DEAD:
      return "/management/jobs?status=DEAD"
    case AlertKind.EXCEPTION_OPEN:
      return "/management/exceptions"
  }
}
