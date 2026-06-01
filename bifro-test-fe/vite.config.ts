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

import {defineConfig} from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'
import {readFileSync} from 'fs'

// Frontend version number read directly from package.json version field, format fe-<version>.
// Same code produces stable consistent build artifacts, suitable for MD5 signature verification.
const pkgVersion: string = JSON.parse(readFileSync(new URL('./package.json', import.meta.url), 'utf-8')).version;

// https://vite.dev/config/
export default defineConfig({
    plugins: [react()],
    base: '/admin/',
    define: {
        __FE_VERSION__: JSON.stringify(`fe-${pkgVersion}`),
    },
    resolve: {
        alias: {
            '@': path.resolve(__dirname, './src'),
        },
    },
    build: {
        chunkSizeWarningLimit: 1100,
        rollupOptions: {
            output: {
                manualChunks: {
                    'react-vendor': ['react', 'react-dom', 'react-router-dom'],
                    'antd-vendor': ['antd', '@ant-design/icons'],
                },
            },
        },
    },
})
