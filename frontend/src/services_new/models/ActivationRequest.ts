/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type ActivationRequest = {
    token: string;
    resetType: ActivationRequest.resetType;
    username?: string;
    password?: string;
};
export namespace ActivationRequest {
    export enum resetType {
        USER_ACTIVATION = 'USER_ACTIVATION',
        MEMBER_ACTIVATION = 'MEMBER_ACTIVATION',
        PASSWORD_RESET = 'PASSWORD_RESET',
    }
}

