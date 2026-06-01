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

import {beforeEach, describe, expect, it, vi} from 'vitest';

async function loadI18n() {
    vi.resetModules();
    return import('./index');
}

function setNavigatorLanguages(languages: string[], language = languages[0] ?? 'en-US') {
    Object.defineProperty(navigator, 'languages', {
        configurable: true,
        value: languages,
    });
    Object.defineProperty(navigator, 'language', {
        configurable: true,
        value: language,
    });
}

describe('i18n language preference', () => {
    beforeEach(() => {
        localStorage.clear();
        setNavigatorLanguages(['en-US'], 'en-US');
    });

    it('initializes from browser language when preference is auto', async () => {
        localStorage.setItem('bifro_language_mode', 'auto');
        localStorage.setItem('bifro_language', 'zh');
        setNavigatorLanguages(['en-US', 'zh-CN'], 'en-US');

        const {default: i18n} = await loadI18n();

        expect(i18n.language).toBe('en');
    });

    it('keeps explicit user language ahead of browser language', async () => {
        localStorage.setItem('bifro_language_mode', 'manual');
        localStorage.setItem('bifro_language', 'zh');
        setNavigatorLanguages(['en-US'], 'en-US');

        const {default: i18n} = await loadI18n();

        expect(i18n.language).toBe('zh');
    });

    it('switches to auto by clearing manual language and using browser language', async () => {
        localStorage.setItem('bifro_language_mode', 'manual');
        localStorage.setItem('bifro_language', 'zh');
        setNavigatorLanguages(['en-US'], 'en-US');
        const {getLanguagePreference, setLanguagePreference} = await loadI18n();

        setLanguagePreference('auto');

        expect(localStorage.getItem('bifro_language')).toBeNull();
        expect(localStorage.getItem('bifro_language_mode')).toBe('auto');
        expect(getLanguagePreference()).toBe('auto');
    });
});
