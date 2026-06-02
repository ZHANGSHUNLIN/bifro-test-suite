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

export interface PlaceholderGuideItem {
    key: string;
    descKey: string;
}

interface PlaceholderSpec {
    sample: string;
    pattern: RegExp;
    descKey: string;
}

const PAYLOAD_PLACEHOLDERS: PlaceholderSpec[] = [
    {sample: '{{timestamp_ms}}', pattern: /^timestamp_ms$/, descKey: 'task.form.placeholderDesc.timestamp_ms'},
    {sample: '{{timestamp_s}}', pattern: /^timestamp_s$/, descKey: 'task.form.placeholderDesc.timestamp_s'},
    {sample: '{{index}}', pattern: /^index$/, descKey: 'task.form.placeholderDesc.index'},
    {sample: '{{client_id}}', pattern: /^client_id$/, descKey: 'task.form.placeholderDesc.client_id'},
    {sample: '{{task_id}}', pattern: /^task_id$/, descKey: 'task.form.placeholderDesc.task_id'},
    {sample: '{{uuid}}', pattern: /^uuid$/, descKey: 'task.form.placeholderDesc.uuid'},
    {sample: '{{random_text:N}}', pattern: /^random_text:\d+$/, descKey: 'task.form.placeholderDesc.random_text'},
    {sample: '{{random_int:min:max}}', pattern: /^random_int:-?\d+:-?\d+$/, descKey: 'task.form.placeholderDesc.random_int'},
];

export const payloadPlaceholderGuide = (): PlaceholderGuideItem[] =>
    PAYLOAD_PLACEHOLDERS.map(({sample, descKey}) => ({key: sample, descKey}));

const AUTH_PLACEHOLDERS: PlaceholderSpec[] = [
    {sample: '{{client_id}}', pattern: /^client_id$/, descKey: 'task.form.authPlaceholderDesc.client_id'},
    {sample: '{{client_id_short}}', pattern: /^client_id_short$/, descKey: 'task.form.authPlaceholderDesc.client_id_short'},
    {sample: '{{index}}', pattern: /^index$/, descKey: 'task.form.authPlaceholderDesc.index'},
    {sample: '{{task_id}}', pattern: /^task_id$/, descKey: 'task.form.authPlaceholderDesc.task_id'},
    {sample: '{{node_id}}', pattern: /^node_id$/, descKey: 'task.form.authPlaceholderDesc.node_id'},
];

export const authPlaceholderGuide = (): PlaceholderGuideItem[] =>
    AUTH_PLACEHOLDERS.map(({sample, descKey}) => ({key: sample, descKey}));

export type TemplateValidationResult =
    | {valid: true}
    | {valid: false; reason: 'required' | 'unclosed' | 'unknown'; placeholder?: string};

export function validatePayloadTemplate(template?: string): TemplateValidationResult {
    if (!template || template.trim() === '') {
        return {valid: false, reason: 'required'};
    }
    const placeholderRe = /\{\{([^}]+)}}/g;
    let match: RegExpExecArray | null;
    while ((match = placeholderRe.exec(template)) !== null) {
        const placeholder = match[1].trim();
        if (!PAYLOAD_PLACEHOLDERS.some(({pattern}) => pattern.test(placeholder))) {
            return {valid: false, reason: 'unknown', placeholder};
        }
    }
    const openCount = (template.match(/\{\{/g) || []).length;
    const closeCount = (template.match(/}}/g) || []).length;
    if (openCount !== closeCount) {
        return {valid: false, reason: 'unclosed'};
    }
    return {valid: true};
}
