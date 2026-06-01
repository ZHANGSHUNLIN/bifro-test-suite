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

// Environment configuration
export interface EnvironmentConfig {
    apiBaseUrl: string;
}

// Development environment config
const developmentConfig: EnvironmentConfig = {
    apiBaseUrl: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8081/api',
};

// Pre-release environment config
const stagingConfig: EnvironmentConfig = {
    apiBaseUrl: import.meta.env.VITE_API_BASE_URL || '/api',
};

// Production environment config
const productionConfig: EnvironmentConfig = {
    apiBaseUrl: import.meta.env.VITE_API_BASE_URL || '/api',
};

// Environment types
export type Environment = 'development' | 'staging' | 'production';

// Get current environment
export const getEnvironment = (): Environment => {
    const mode = import.meta.env.MODE as Environment;
    return mode && ['development', 'staging', 'production'].includes(mode) ? mode : 'development';
};

// Get config for the environment
export const getEnvConfig = (): EnvironmentConfig => {
    const env = getEnvironment();
    switch (env) {
        case 'staging':
            return stagingConfig;
        case 'production':
            return productionConfig;
        default:
            return developmentConfig;
    }
};

// Export current environment config
export const envConfig: EnvironmentConfig = getEnvConfig();

// Export environment check functions
export const isDevelopment = (): boolean => getEnvironment() === 'development';
export const isStaging = (): boolean => getEnvironment() === 'staging';
export const isProduction = (): boolean => getEnvironment() === 'production';

export const API_BASE_URL = envConfig.apiBaseUrl;

// Log current environment info (only shown in development)
if (isDevelopment()) {
    console.log('Environment:', getEnvironment());
    console.log('API Base URL:', API_BASE_URL);
}
