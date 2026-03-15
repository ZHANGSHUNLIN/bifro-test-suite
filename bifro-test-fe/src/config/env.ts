// 环境配置
export interface EnvironmentConfig {
  apiBaseUrl: string;
  clusterTaskUrl: string;
  taskUrl: string;
  brokerUrl: string;
}

// 开发环境配置
const developmentConfig: EnvironmentConfig = {
  apiBaseUrl: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8081/api',
  clusterTaskUrl: import.meta.env.VITE_CLUSTER_TASK_URL || 'http://localhost:8081/api/task',
  taskUrl: import.meta.env.VITE_TASK_URL || 'http://localhost:8081/api/task',
  brokerUrl: import.meta.env.VITE_BROKER_URL || 'http://localhost:8081/api/broker',
};

// 预发布环境配置
const stagingConfig: EnvironmentConfig = {
  apiBaseUrl: import.meta.env.VITE_API_BASE_URL || 'https://staging-api.yourdomain.com/api',
  clusterTaskUrl: import.meta.env.VITE_CLUSTER_TASK_URL || 'https://staging-api.yourdomain.com/api/task',
  taskUrl: import.meta.env.VITE_TASK_URL || 'https://staging-api.yourdomain.com/api/task',
  brokerUrl: import.meta.env.VITE_BROKER_URL || 'https://staging-api.yourdomain.com/api/broker',
};

// 生产环境配置
const productionConfig: EnvironmentConfig = {
  apiBaseUrl: import.meta.env.VITE_API_BASE_URL || 'https://api.yourdomain.com/api',
  clusterTaskUrl: import.meta.env.VITE_CLUSTER_TASK_URL || 'https://api.yourdomain.com/api/task',
  taskUrl: import.meta.env.VITE_TASK_URL || 'https://api.yourdomain.com/api/task',
  brokerUrl: import.meta.env.VITE_BROKER_URL || 'https://api.yourdomain.com/api/broker',
};

// 环境类型
export type Environment = 'development' | 'staging' | 'production';

// 获取当前环境
export const getEnvironment = (): Environment => {
  const mode = import.meta.env.MODE as Environment;
  return mode && ['development', 'staging', 'production'].includes(mode) ? mode : 'development';
};

// 获取对应环境的配置
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

// 导出当前环境配置
export const envConfig: EnvironmentConfig = getEnvConfig();

// 导出环境判断函数
export const isDevelopment = (): boolean => getEnvironment() === 'development';
export const isStaging = (): boolean => getEnvironment() === 'staging';
export const isProduction = (): boolean => getEnvironment() === 'production';

// 导出环境变量（方便直接使用）
export const API_BASE_URL = envConfig.apiBaseUrl;
export const CLUSTER_TASK_URL = envConfig.clusterTaskUrl;
export const TASK_URL = envConfig.taskUrl;
export const BROKER_URL = envConfig.brokerUrl;

// 日志当前环境信息（仅在开发环境显示）
if (isDevelopment()) {
  console.log('Environment:', getEnvironment());
  console.log('API Base URL:', API_BASE_URL);
}