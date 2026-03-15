// 集群管理相关的 TypeScript 类型定义

// 内存信息

export interface MemoryInfo {
  max: number;
  total: number;
  used: number;
  free: number;
}

// CPU 信息
export interface CpuInfo {
  processors: number;
  loadAverage: number;
}

// 集群节点信息
export interface ClusterNodeInfo {
  nodeId: string;
  host: string;
  timestamp: number;
  memory: MemoryInfo;
  cpu: CpuInfo;
}

// 节点状态枚举
export const NodeStatus = {
  ONLINE: 'ONLINE',
  OFFLINE: 'OFFLINE',
  UNSTABLE: 'UNSTABLE'
} as const;

export type NodeStatus = typeof NodeStatus[keyof typeof NodeStatus];

// 节点列表项（用于表格显示）
export interface NodeListItem {
  nodeId: string;
  host: string;
  status: NodeStatus;
  lastHeartbeat: string;
  memory: {
    used: number;
    total: number;
    usageRate: number;
  };
  cpu: {
    processors: number;
    loadAverage: number;
  };
}

// 集群统计信息
export interface ClusterStatistics {
  totalNodes: number;
  onlineNodes: number;
  offlineNodes: number;
  totalMemory: number;
  usedMemory: number;
  averageCpuLoad: number;
}


// 节点详情响应（包含任务状态）
export interface NodeDetailResponse {
  clusterNodeInfo: ClusterNodeInfo;
  taskStage: Record<string, string>; // 任务ID到状态映射
  alive: boolean;
  nextPing: number;
}