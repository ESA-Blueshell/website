/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { FileDTO } from './FileDTO';
import type { MembershipDTO } from './MembershipDTO';
import type { SimpleUserDTO } from './SimpleUserDTO';
export type AdvancedUserDTO = (SimpleUserDTO & {
    initials?: string;
    roles?: Array<'ANONYMOUS' | 'GUEST' | 'COMPANY' | 'MEMBER' | 'VEGAN' | 'COMMITTEE' | 'BOARD' | 'TREASURER' | 'ADMIN' | 'SYSTEM'>;
    dateOfBirth?: string;
    phoneNumber?: string;
    postalCode?: string;
    address?: string;
    city?: string;
    country?: string;
    nationality?: string;
    signature?: FileDTO;
    membership?: MembershipDTO;
    newsletter?: boolean;
    photoConsent?: boolean;
    ehbo?: boolean;
    bhv?: boolean;
    enabled?: boolean;
    incasso?: boolean;
    createdAt?: string;
    gender?: string;
    study?: string;
    studentNumber?: string;
} & {
    id: number;
    username: string;
    discord: string;
    firstName: string;
    lastName: string;
    email: string;
    initials: string;
    dateOfBirth: string;
    phoneNumber: string;
    postalCode: string;
    address: string;
    city: string;
    country: string;
    nationality: string;
    signature: FileDTO;
    newsletter: boolean;
    photoConsent: boolean;
    ehbo: boolean;
    bhv: boolean;
    incasso: boolean;
});

