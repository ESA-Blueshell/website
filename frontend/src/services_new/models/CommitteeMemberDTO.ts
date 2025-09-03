/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { AdvancedUserDTO } from './AdvancedUserDTO';
import type { SimpleUserDTO } from './SimpleUserDTO';
export type CommitteeMemberDTO = {
    id?: number;
    role?: string;
    userId?: number;
    user?: (SimpleUserDTO | AdvancedUserDTO);
    committeeId?: number;
};

