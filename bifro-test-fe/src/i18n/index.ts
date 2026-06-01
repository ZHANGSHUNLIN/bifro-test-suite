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

import i18n from 'i18next';
import {initReactI18next} from 'react-i18next';

import en from './locales/en.json';
import zh from './locales/zh.json';

export const AUTO_LANGUAGE = 'auto' as const;

export const SUPPORTED_LANGUAGES = [
    {code: 'en', label: 'English'},
    {code: 'zh', label: '中文'},
] as const;

export const LANGUAGE_OPTIONS = [
    {code: AUTO_LANGUAGE, labelKey: 'common.followBrowser'},
    ...SUPPORTED_LANGUAGES,
] as const;

export type LanguageCode = (typeof SUPPORTED_LANGUAGES)[number]['code'];
export type LanguagePreference = LanguageCode | typeof AUTO_LANGUAGE;

const STORAGE_KEY = 'bifro_language';
const MODE_STORAGE_KEY = 'bifro_language_mode';
const DEFAULT_LANGUAGE: LanguageCode = 'en';

function normalizeLanguage(lang: string | null | undefined): LanguageCode | null {
    if (!lang) {
        return null;
    }

    const normalized = lang.toLowerCase();
    if (normalized.startsWith('zh')) {
        return 'zh';
    }
    if (normalized.startsWith('en')) {
        return 'en';
    }
    return null;
}

function normalizeLanguagePreference(lang: string | null | undefined): LanguagePreference | null {
    if (lang === AUTO_LANGUAGE) {
        return AUTO_LANGUAGE;
    }
    return normalizeLanguage(lang);
}

function getBrowserLanguage(): LanguageCode {
    if (typeof navigator === 'undefined') {
        return DEFAULT_LANGUAGE;
    }

    const browserLanguages = navigator.languages?.length ? navigator.languages : [navigator.language];
    return browserLanguages
        .map(language => normalizeLanguage(language))
        .find((language): language is LanguageCode => language !== null) ?? DEFAULT_LANGUAGE;
}

export function getLanguagePreference(): LanguagePreference {
    if (typeof window === 'undefined') {
        return AUTO_LANGUAGE;
    }

    const savedMode = window.localStorage.getItem(MODE_STORAGE_KEY);
    if (savedMode === AUTO_LANGUAGE) {
        return AUTO_LANGUAGE;
    }

    const savedLanguage = normalizeLanguagePreference(window.localStorage.getItem(STORAGE_KEY));
    return savedLanguage ?? AUTO_LANGUAGE;
}

function getInitialLanguage(): LanguageCode {
    const preference = getLanguagePreference();
    return preference === AUTO_LANGUAGE ? getBrowserLanguage() : preference;
}

export function getResolvedLanguage(lang: string | null | undefined): LanguageCode {
    return normalizeLanguage(lang) ?? DEFAULT_LANGUAGE;
}

export function getResolvedLocale(lang: string | null | undefined): string {
    return getResolvedLanguage(lang) === 'zh' ? 'zh-CN' : 'en-US';
}

i18n.use(initReactI18next).init({
    resources: {
        en: {translation: en},
        zh: {translation: zh},
    },
    lng: getInitialLanguage(),
    fallbackLng: DEFAULT_LANGUAGE,
    supportedLngs: SUPPORTED_LANGUAGES.map(({code}) => code),
    interpolation: {
        escapeValue: false,
    },
});

export function setLanguagePreference(preference: LanguagePreference): void {
    if (typeof window !== 'undefined') {
        if (preference === AUTO_LANGUAGE) {
            window.localStorage.setItem(MODE_STORAGE_KEY, AUTO_LANGUAGE);
            window.localStorage.removeItem(STORAGE_KEY);
        } else {
            window.localStorage.setItem(MODE_STORAGE_KEY, 'manual');
            window.localStorage.setItem(STORAGE_KEY, preference);
        }
    }

    void i18n.changeLanguage(preference === AUTO_LANGUAGE ? getBrowserLanguage() : preference);
}

export function setLanguage(lang: LanguageCode): void {
    setLanguagePreference(lang);
}

export default i18n;
