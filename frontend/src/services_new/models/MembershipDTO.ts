/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { FileDTO } from './FileDTO';
export type MembershipDTO = {
    id?: number;
    userId: number;
    memberType?: MembershipDTO.memberType;
    date?: string;
    city?: string;
    country?: string;
    signature?: FileDTO;
    startDate?: string;
    endDate?: string;
};
export namespace MembershipDTO {
    export enum memberType {
        ALUMNI = 'ALUMNI',
        HONORARY = 'HONORARY',
        REGULAR = 'REGULAR',
        NONE = 'NONE',
    }
}

