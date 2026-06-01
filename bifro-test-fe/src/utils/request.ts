/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import {envConfig} from '../config/env';

const DEFAULT_REQUEST_TIMEOUT_MS = 15000;
export const AUTH_EXPIRED_EVENT = 'bifro:auth-expired';

class RequestTimeoutError extends Error {
    constructor(timeoutMs: number) {
        super(`Request timed out after ${timeoutMs}ms`);
        this.name = 'RequestTimeoutError';
    }
}

export class HttpRequestError extends Error {
    status: number;

    constructor(status: number) {
        super(`HTTP error! status: ${status}`);
        this.name = 'HttpRequestError';
        this.status = status;
    }
}

const notifyAuthExpired = () => {
    window.dispatchEvent(new CustomEvent(AUTH_EXPIRED_EVENT));
};

const createTimeoutSignal = (timeoutMs: number, externalSignal?: AbortSignal): { signal: AbortSignal; cleanup: () => void } => {
    const timeoutController = new AbortController();
    const timeoutId = window.setTimeout(() => {
        timeoutController.abort(new RequestTimeoutError(timeoutMs));
    }, timeoutMs);

    if (!externalSignal) {
        return {
            signal: timeoutController.signal,
            cleanup: () => window.clearTimeout(timeoutId),
        };
    }

    const mergedController = new AbortController();
    const abortMerged = (reason?: unknown) => {
        if (!mergedController.signal.aborted) {
            mergedController.abort(reason);
        }
    };
    const handleTimeoutAbort = () => abortMerged(timeoutController.signal.reason);
    const handleExternalAbort = () => abortMerged(externalSignal.reason);

    timeoutController.signal.addEventListener('abort', handleTimeoutAbort, {once: true});
    externalSignal.addEventListener('abort', handleExternalAbort, {once: true});

    if (externalSignal.aborted) {
        abortMerged(externalSignal.reason);
    }

    return {
        signal: mergedController.signal,
        cleanup: () => {
            window.clearTimeout(timeoutId);
            timeoutController.signal.removeEventListener('abort', handleTimeoutAbort);
            externalSignal.removeEventListener('abort', handleExternalAbort);
        },
    };
};

// Common request function
export const request = async <T>(
    endpoint: string,
    {headers, params, ...customConfig}: RequestInit & { params?: Record<string, string | number | boolean> } = {}
): Promise<T> => {
    // Handle path params
    let url = endpoint;
    const pathParamKeys = new Set<string>();
    // If param mapping exists, replace URL placeholders
    if (params) {
        Object.entries(params).forEach(([key, value]) => {
            const placeholder = `:${key}`;
            if (url.includes(placeholder)) {
                pathParamKeys.add(key);
                url = url.replace(placeholder, encodeURIComponent(String(value)));
            }
        });
    }

    // Handle query params (non-path params)
    const searchParams = new URLSearchParams();
    if (params) {
        Object.entries(params).forEach(([key, value]) => {
            if (!pathParamKeys.has(key)) {
                searchParams.append(key, String(value));
            }
        });
    }

    const queryString = searchParams.toString();
    if (queryString) {
        url += (url.includes('?') ? '&' : '?') + queryString;
    }

    // Add base URL
    if (!url.startsWith('http')) {
        url = `${envConfig.apiBaseUrl}${url}`;
    }

    const {signal, cleanup} = createTimeoutSignal(DEFAULT_REQUEST_TIMEOUT_MS, customConfig.signal ?? undefined);
    const config: RequestInit = {
        method: 'GET',
        credentials: 'include',
        ...customConfig,
        signal,
        headers: {
            'Content-Type': 'application/json',
            ...headers,
        },
    };

    try {
        const response = await fetch(url, config);

        if (!response.ok) {
            if (response.status === 401 || response.status === 403) {
                notifyAuthExpired();
            }
            throw new HttpRequestError(response.status);
        }

        // Skip JSON parsing for 204 No Content
        if (response.status === 204) {
            return {} as T;
        }

        const result = await response.json();

        // Parse ApiResponse format
        if (result && typeof result === 'object' && 'code' in result) {
            if (result.code === 200) {
                return result.data as T;
            } else {
                // Support both message and msg field names
                const errorMessage = result.message || result.msg || `API error! code: ${result.code}`;
                throw new Error(errorMessage);
            }
        }

        // Non-ApiResponse format: return directly
        return result as T;
    } catch (error) {
        console.error(`API request failed: ${url}`, error);
        throw error;
    } finally {
        cleanup();
    }
};

// More elegant request method wrappers
interface RequestOptions extends RequestInit {
    params?: Record<string, string | number | boolean>;
}

export const api = {
    // GET request
    get: <T>(url: string, options?: RequestOptions) => request<T>(url, {...options, method: 'GET'}),

    // POST request
    post: <T>(url: string, data?: any, options?: RequestOptions) => request<T>(url, {
        ...options,
        method: 'POST',
        body: data ? JSON.stringify(data) : undefined
    }),

    // PUT request
    put: <T>(url: string, data?: any, options?: RequestOptions) => request<T>(url, {
        ...options,
        method: 'PUT',
        body: data ? JSON.stringify(data) : undefined
    }),

    // DELETE request (supports query params)
    delete: <T>(url: string, options?: RequestOptions) => request<T>(url, {...options, method: 'DELETE'}),

    // DELETE request (with body)
    deleteWithBody: <T>(url: string, data?: any, options?: RequestOptions) => request<T>(url, {
        ...options,
        method: 'DELETE',
        body: data ? JSON.stringify(data) : undefined
    }),

    // PATCH request
    patch: <T>(url: string, data?: any, options?: RequestOptions) => request<T>(url, {
        ...options,
        method: 'PATCH',
        body: data ? JSON.stringify(data) : undefined
    })
};
