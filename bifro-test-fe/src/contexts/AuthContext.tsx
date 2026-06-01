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

import React, {createContext, useCallback, useContext, useEffect, useMemo, useState} from 'react';
import authApi from '../features/auth';
import type {AuthUser, LoginRequest} from '../features/auth';
import {AUTH_EXPIRED_EVENT, HttpRequestError} from '../utils/request';

interface AuthContextValue {
    user: AuthUser | null;
    loading: boolean;
    login: (request: LoginRequest) => Promise<AuthUser>;
    logout: () => Promise<void>;
    refresh: () => Promise<AuthUser | null>;
    hasRole: (role: string) => boolean;
}

const AuthContext = createContext<AuthContextValue | null>(null);
const ANONYMOUS_USER: AuthUser = {enabled: true, authenticated: false, username: null, roles: []};

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({children}) => {
    const [user, setUser] = useState<AuthUser | null>(null);
    const [loading, setLoading] = useState(true);

    const refresh = useCallback(async () => {
        try {
            const nextUser = await authApi.me();
            setUser(nextUser);
            return nextUser;
        } catch (error) {
            if (error instanceof HttpRequestError && (error.status === 401 || error.status === 403)) {
                setUser(ANONYMOUS_USER);
                return null;
            }
            throw error;
        }
    }, []);

    useEffect(() => {
        refresh()
            .catch(() => setUser(ANONYMOUS_USER))
            .finally(() => setLoading(false));
    }, [refresh]);

    useEffect(() => {
        const handleAuthExpired = () => {
            setUser(ANONYMOUS_USER);
            setLoading(false);
        };
        window.addEventListener(AUTH_EXPIRED_EVENT, handleAuthExpired);
        return () => window.removeEventListener(AUTH_EXPIRED_EVENT, handleAuthExpired);
    }, []);

    const login = useCallback(async (request: LoginRequest) => {
        const nextUser = await authApi.login(request);
        setUser(nextUser);
        return nextUser;
    }, []);

    const logout = useCallback(async () => {
        try {
            await authApi.logout();
        } finally {
            setUser(ANONYMOUS_USER);
        }
    }, []);

    const hasRole = useCallback((role: string) => {
        if (!user?.enabled) {
            return true;
        }
        return user.roles.includes(role);
    }, [user]);

    const value = useMemo(() => ({
        user,
        loading,
        login,
        logout,
        refresh,
        hasRole,
    }), [hasRole, loading, login, logout, refresh, user]);

    return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export const useAuth = () => {
    const context = useContext(AuthContext);
    if (!context) {
        throw new Error('useAuth must be used within AuthProvider');
    }
    return context;
};
