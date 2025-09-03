/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { BaseDTO } from './BaseDTO';
import type { FileDTO } from './FileDTO';
import type { FormQuestionDTO } from './FormQuestionDTO';
import type { SimpleCommitteeDTO } from './SimpleCommitteeDTO';
export type EventDTO = (BaseDTO & {
    id?: number;
    committeeId?: number;
    committee?: SimpleCommitteeDTO;
    title?: string;
    startTime?: string;
    endTime?: string;
    memberPrice?: string;
    publicPrice?: string;
    visible?: boolean;
    membersOnly?: boolean;
    signUp?: boolean;
    banner?: FileDTO;
    signUpForm?: Array<FormQuestionDTO>;
    description?: string;
    location?: string;
} & {
    title: string;
    startTime: string;
    endTime: string;
    description: string;
});

