import {z} from "zod"
import {DateTime} from "luxon"

export const zNonEmpty = (label = "This field") =>
  z.string().min(1, `${label} is required`)

export const zAlphaNum = (label = "Value") =>
  z.string().regex(/^[a-zA-Z0-9]+$/, "Use only letters and numbers")

export const zEmailStrict = () =>
  z.email("Enter a valid e-mail address")

export const zNoStudentEmail = () =>
  z.string().refine((v: string) => !/student/i.test(v), "You may not use your student email to sign up")

export const zEmailNoStudent = () =>
  zEmailStrict().refine((v: string) => !/student/i.test(v), "You may not use your student email to sign up")

export const zDateISORequired = (label = "Date") =>
  z.string()
    .min(1, `${label} is required`)
    .refine(v => DateTime.fromISO(v).isValid, "Enter a valid date")

export const zRequiredPassword = (min = 8, max = 100) =>
  z.string()
    .min(min, `Must be at least ${min} characters`)
    .max(max, `Must be at most ${max} characters`)
    .refine((v: string) => /[a-z]/.test(v), "Include a lowercase letter")
    .refine((v: string) => /[A-Z]/.test(v), "Include an uppercase letter")
    .refine((v: string) => /\d/.test(v), "Include a number")
    .refine((v: string) => /[@$!%*?&]/.test(v), "Include a special char (@$!%*?&)")
    .refine((v: string) => v !== "", "This field is required")

export const zOptionalPassword = (min = 8, max = 100) =>
  z.union([z.literal(""), zRequiredPassword(min, max)])

export const zOptionalString = () => z.string().optional()
export const zBoolean = () => z.boolean()
