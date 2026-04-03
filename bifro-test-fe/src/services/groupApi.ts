// MQTT Broker 分组管理相关的 API 服务
import { api } from '../utils/request';
import type { GroupListItem, MqttGroup, GroupRequest } from '../types/mqttGroup';
import type { PageInfo } from '../types/task';

// Broker 分组类型常量
export const GROUP_TYPE_BROKER = 'BROKER';

// MQTT Broker 分组管理 API
export const groupApi = {
    // 获取分组列表（分页）
    getAllGroups: (pageNum?: number, pageSize?: number) => {
        const params: Record<string, number | string> = { type: GROUP_TYPE_BROKER };
        if (pageNum !== undefined) {
            params.pageNum = pageNum;
        }
        if (pageSize !== undefined) {
            params.pageSize = pageSize;
        }
        return api.get<PageInfo<GroupListItem>>('/groups/list', { params });
    },

    // 获取所有分组（不分页，用于下拉选择）
    getAllGroupsForSelect: () => {
        return api.get<MqttGroup[]>('/groups/all', { params: { type: GROUP_TYPE_BROKER } });
    },

    // 获取或创建默认分组
    getOrCreateDefaultGroup: async () => {
        try {
            const allGroups = await groupApi.getAllGroupsForSelect();
            const defaultGroup = allGroups.find((g: MqttGroup) => g.name === '默认分组');
            if (defaultGroup) {
                return defaultGroup;
            }
            // 如果不存在，创建默认分组
            const newGroup = await groupApi.addGroup({ name: '默认分组', description: '系统默认分组' });
            return newGroup;
        } catch (error) {
            console.error('获取或创建默认分组失败:', error);
            throw error;
        }
    },

    // 获取分组详情
    getGroupDetail: (id: string) => {
        return api.get<MqttGroup>('/groups/:id', {
            params: { id }
        });
    },

    // 添加分组
    addGroup: (request: GroupRequest) => {
        return api.post<MqttGroup>('/groups', request, { params: { type: GROUP_TYPE_BROKER } });
    },

    // 更新分组
    updateGroup: (id: string, request: GroupRequest) => {
        return api.put<MqttGroup>('/groups/:id', request, {
            params: { id }
        });
    },

    // 删除分组
    deleteGroup: (id: string) => {
        return api.delete<void>('/groups/:id', {
            params: { id, type: GROUP_TYPE_BROKER }
        });
    }
};

export default groupApi;
