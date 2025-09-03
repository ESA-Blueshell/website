/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { BaseDTO } from './BaseDTO';
export type SocialDTO = (BaseDTO & {
    id?: string;
    title?: string;
    text?: string;
    url?: string;
    platforms?: Array<'FACEBOOK' | 'LINKEDIN' | 'TWITTER' | 'INSTAGRAM'>;
});

