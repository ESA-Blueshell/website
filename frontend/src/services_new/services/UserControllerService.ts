/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { ActivationRequest } from '../models/ActivationRequest';
import type { AdvancedUserDTO } from '../models/AdvancedUserDTO';
import type { PasswordResetRequest } from '../models/PasswordResetRequest';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class UserControllerService {
    /**
     * @param userId
     * @returns AdvancedUserDTO OK
     * @throws ApiError
     */
    public static getById(
        userId: number,
    ): CancelablePromise<AdvancedUserDTO> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/users/{userId}',
            path: {
                'userId': userId,
            },
        });
    }
    /**
     * @param userId
     * @param requestBody
     * @returns AdvancedUserDTO OK
     * @throws ApiError
     */
    public static update(
        userId: number,
        requestBody: AdvancedUserDTO,
    ): CancelablePromise<AdvancedUserDTO> {
        return __request(OpenAPI, {
            method: 'PUT',
            url: '/users/{userId}',
            path: {
                'userId': userId,
            },
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * @param userId
     * @returns void
     * @throws ApiError
     */
    public static delete(
        userId: number,
    ): CancelablePromise<void> {
        return __request(OpenAPI, {
            method: 'DELETE',
            url: '/users/{userId}',
            path: {
                'userId': userId,
            },
        });
    }
    /**
     * @param userId
     * @param role
     * @returns AdvancedUserDTO OK
     * @throws ApiError
     */
    public static toggleRole(
        userId: number,
        role: 'ANONYMOUS' | 'GUEST' | 'COMPANY' | 'MEMBER' | 'VEGAN' | 'COMMITTEE' | 'BOARD' | 'TREASURER' | 'ADMIN' | 'SYSTEM',
    ): CancelablePromise<AdvancedUserDTO> {
        return __request(OpenAPI, {
            method: 'PUT',
            url: '/users/{userId}/roles',
            path: {
                'userId': userId,
            },
            query: {
                'role': role,
            },
        });
    }
    /**
     * @param userId
     * @returns AdvancedUserDTO OK
     * @throws ApiError
     */
    public static toggleMembership(
        userId: number,
    ): CancelablePromise<AdvancedUserDTO> {
        return __request(OpenAPI, {
            method: 'PUT',
            url: '/users/{userId}/membership/toggle',
            path: {
                'userId': userId,
            },
        });
    }
    /**
     * @param id
     * @param isMember
     * @returns AdvancedUserDTO OK
     * @throws ApiError
     */
    public static updateMembership(
        id: number,
        isMember: boolean = false,
    ): CancelablePromise<AdvancedUserDTO> {
        return __request(OpenAPI, {
            method: 'PUT',
            url: '/users/{id}/membership',
            path: {
                'id': id,
            },
            query: {
                'isMember': isMember,
            },
        });
    }
    /**
     * @param isMember
     * @returns AdvancedUserDTO OK
     * @throws ApiError
     */
    public static getAll(
        isMember: boolean,
    ): CancelablePromise<Array<AdvancedUserDTO>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/users',
            query: {
                'isMember': isMember,
            },
        });
    }
    /**
     * @param requestBody
     * @returns AdvancedUserDTO OK
     * @throws ApiError
     */
    public static create(
        requestBody: AdvancedUserDTO,
    ): CancelablePromise<AdvancedUserDTO> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/users',
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * @param username
     * @returns any OK
     * @throws ApiError
     */
    public static resetPassword(
        username: string,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/users/reset',
            query: {
                'username': username,
            },
        });
    }
    /**
     * @param requestBody
     * @returns any OK
     * @throws ApiError
     */
    public static setPassword(
        requestBody: PasswordResetRequest,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/users/password',
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * @param requestBody
     * @returns any OK
     * @throws ApiError
     */
    public static activate(
        requestBody: ActivationRequest,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/users/activate',
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * @param email
     * @returns AdvancedUserDTO OK
     * @throws ApiError
     */
    public static getFromBrevo(
        email: string,
    ): CancelablePromise<AdvancedUserDTO> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/users/brevo',
            query: {
                'email': email,
            },
        });
    }
}
