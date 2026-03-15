/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL: string
  readonly VITE_CLUSTER_TASK_URL: string
  readonly VITE_TASK_URL: string
  readonly VITE_BROKER_URL: string
  readonly MODE: 'development' | 'staging' | 'production' | string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}