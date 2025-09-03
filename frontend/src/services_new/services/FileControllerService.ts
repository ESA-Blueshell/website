/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class FileControllerService {
    /**
     * @param userId
     * @returns binary OK
     * @throws ApiError
     */
    public static downloadProfilePicture(
        userId: number,
    ): CancelablePromise<Blob> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/users/{userId}/profilePicture',
            path: {
                'userId': userId,
            },
        });
    }
    /**
     * @param membershipId
     * @returns binary OK
     * @throws ApiError
     */
    public static downloadSignature(
        membershipId: number,
    ): CancelablePromise<Blob> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/memberships/{membershipId}/signature',
            path: {
                'membershipId': membershipId,
            },
        });
    }
    /**
     * @param eventId
     * @returns binary OK
     * @throws ApiError
     */
    public static downloadBanner(
        eventId: number,
    ): CancelablePromise<Blob> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/events/{eventId}/banner',
            path: {
                'eventId': eventId,
            },
        });
    }
    /**
     * @param eventPictureId
     * @returns binary OK
     * @throws ApiError
     */
    public static downloadEventPicture(
        eventPictureId: number,
    ): CancelablePromise<Blob> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/eventPictures/{eventPictureId}',
            path: {
                'eventPictureId': eventPictureId,
            },
        });
    }
    /**
     * @param filename
     * @returns binary OK
     * @throws ApiError
     */
    public static downloadFile(
        filename: string,
    ): CancelablePromise<Blob> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/download/{filename}',
            path: {
                'filename': filename,
            },
        });
    }
}
