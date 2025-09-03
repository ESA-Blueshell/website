/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type TelemetryDTO = {
    id?: string;
    url?: string;
    platform?: TelemetryDTO.platform;
    createdAt?: string;
};
export namespace TelemetryDTO {
    export enum platform {
        FACEBOOK = 'FACEBOOK',
        LINKEDIN = 'LINKEDIN',
        TWITTER = 'TWITTER',
        INSTAGRAM = 'INSTAGRAM',
    }
}

