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

import React, {createContext, useCallback, useContext, useEffect, useState} from 'react';

type ThemeMode = 'light' | 'dark';

interface ThemeContextValue {
    themeMode: ThemeMode;
    toggleTheme: () => void;
    isDark: boolean;
}

const STORAGE_KEY = 'bifro-theme-mode';

const ThemeContext = createContext<ThemeContextValue>({
    themeMode: 'light',
    toggleTheme: () => {
    },
    isDark: false,
});

export const ThemeProvider: React.FC<{ children: React.ReactNode }> = ({children}) => {
    const [themeMode, setThemeMode] = useState<ThemeMode>(() => {
        const saved = localStorage.getItem(STORAGE_KEY);
        return (saved === 'dark' || saved === 'light') ? saved : 'light';
    });

    useEffect(() => {
        localStorage.setItem(STORAGE_KEY, themeMode);
        document.documentElement.setAttribute('data-theme', themeMode);
    }, [themeMode]);

    const toggleTheme = useCallback(() => {
        setThemeMode(prev => prev === 'light' ? 'dark' : 'light');
    }, []);

    return (
        <ThemeContext.Provider value={{themeMode, toggleTheme, isDark: themeMode === 'dark'}}>
            {children}
        </ThemeContext.Provider>
    );
};

export const useTheme = () => useContext(ThemeContext);
