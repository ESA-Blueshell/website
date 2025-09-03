/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { EventSignUpDTO } from '../models/EventSignUpDTO';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class EventSignUpControllerService {
    /**
     * @param eventId
     * @param requestBody
     * @param accessToken
     * @returns EventSignUpDTO OK
     * @throws ApiError
     */
    public static updateSignUp(
        eventId: number,
        requestBody: EventSignUpDTO,
        accessToken?: string,
    ): CancelablePromise<EventSignUpDTO> {
        return __request(OpenAPI, {
            method: 'PUT',
            url: '/events/{eventId}/signups',
            path: {
                'eventId': eventId,
            },
            query: {
                'accessToken': accessToken,
            },
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * @param id
     * @returns any OK
     * @throws ApiError
     */
    public static getAllSignUps(
        id: number,
    ): CancelablePromise<{
        parallel?: boolean;
    }> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/events/{id}/signups',
            path: {
                'id': id,
            },
        });
    }
    /**
     * @param id
     * @param requestBody
     * @returns EventSignUpDTO OK
     * @throws ApiError
     */
    public static createSignup(
        id: number,
        requestBody: EventSignUpDTO,
    ): CancelablePromise<EventSignUpDTO> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/events/{id}/signups',
            path: {
                'id': id,
            },
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * @returns EventSignUpDTO OK
     * @throws ApiError
     */
    public static getMySignUps(): CancelablePromise<Array<EventSignUpDTO>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/events/signups',
        });
    }
    /**
     * @param accessToken
     * @returns EventSignUpDTO OK
     * @throws ApiError
     */
    public static getSignUpByAccessToken(
        accessToken: string,
    ): CancelablePromise<EventSignUpDTO> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/events/signups/byAccessToken/{accessToken}',
            path: {
                'accessToken': accessToken,
            },
        });
    }
    /**
     * @param eventSignupId
     * @param accessToken
     * @returns void
     * @throws ApiError
     */
    public static deleteSignup(
        eventSignupId: number,
        accessToken?: string,
    ): CancelablePromise<void> {
        return __request(OpenAPI, {
            method: 'DELETE',
            url: '/events/signups/{eventSignupId}',
            path: {
                'eventSignupId': eventSignupId,
            },
            query: {
                'accessToken': accessToken,
            },
        });
    }
}
