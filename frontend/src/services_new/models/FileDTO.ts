/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
export type FileDTO = {
    id?: number;
    name?: string;
    url?: string;
    uploaderId?: number;
    createdAt?: string;
    mediaType?: string;
    size?: number;
    fileName?: string;
    fileType?: FileDTO.fileType;
    base64Content?: string;
};
export namespace FileDTO {
    export enum fileType {
        DOCUMENT = 'DOCUMENT',
        SIGNATURE = 'SIGNATURE',
        PROFILE_PICTURE = 'PROFILE_PICTURE',
        EVENT_BANNER = 'EVENT_BANNER',
        EVENT_PICTURE = 'EVENT_PICTURE',
        SPONSOR_PICTURE = 'SPONSOR_PICTURE',
    }
}

