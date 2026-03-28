import {useState, useCallback, useMemo} from 'react';
import {message} from 'antd';
import taskApi from '../../../services/taskApi';
import type {TaskListItem, TaskDetailResponse} from '../../../types/task';

/**
 * 分页状态管理
 */
interface PaginationState {
    current: number;
    pageSize: number;
    total: number;
}

/**
 * 优化的任务数据 Hook
 */
export const useTaskDataEnhanced = () => {
    // 任务列表状态
    const [data, setData] = useState<TaskListItem[]>([]);
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState<Error | null>(null);

    // 分页状态
    const [pagination, setPagination] = useState<PaginationState>({
        current: 1,
        pageSize: 10,
        total: 0
    });

    // 过滤状态
    const [filters, setFilters] = useState<{
        taskName?: string;
        taskType?: string;
    }>({});

    /**
     * 加载任务列表
     */
    const loadTasks = useCallback(async () => {
        setIsLoading(true);
        setError(null);

        try {
            const pageInfo = await taskApi.getAllTasks(
                filters.taskName,
                filters.taskType,
                undefined,
                pagination.current,
                pagination.pageSize
            );

            setData(pageInfo.content || []);
            setPagination(prev => ({
                ...prev,
                total: pageInfo.totalElements || 0
            }));
        } catch (err: any) {
            const errorMessage = err.message || '加载任务列表失败';
            setError(err instanceof Error ? err : new Error(errorMessage));
            message.error(errorMessage);
            console.error('Failed to load tasks:', err);
        } finally {
            setIsLoading(false);
        }
    }, [filters.taskName, filters.taskType, pagination.current, pagination.pageSize]);

    /**
     * 处理分页变化
     */
    const handlePageChange = useCallback((page: number, pageSize: number) => {
        setPagination({
            current: page,
            pageSize,
            total: pagination.total
        });
    }, [pagination.total]);

    /**
     * 处理过滤条件变化
     */
    const handleFilterChange = useCallback((newFilters: {taskName?: string; taskType?: string}) => {
        setFilters(newFilters);
        setPagination(prev => ({...prev, current: 1})); // 重置到第一页
    }, []);

    /**
     * 刷新当前页
     */
    const refetch = useCallback(() => {
        loadTasks();
    }, [loadTasks]);

    /**
     * 删除任务后从列表中移除
     */
    const removeTaskFromList = useCallback((id: string) => {
        setData(prev => prev.filter(item => item.id !== id));
        if (pagination.total > 0) {
            setPagination(prev => ({...prev, total: prev.total - 1}));
        }
    }, [pagination.total]);

    /**
     * 添加任务后添加到列表
     */
    const addTaskToList = useCallback((task: TaskListItem) => {
        setData(prev => [task, ...prev]);
        setPagination(prev => ({...prev, total: prev.total + 1}));
    }, []);

    /**
     * 更新任务后更新列表
     */
    const updateTaskInList = useCallback((updatedTask: TaskListItem) => {
        setData(prev => prev.map(item =>
            item.id === updatedTask.id ? updatedTask : item
        ));
    }, []);

    /**
     * 批量删除后更新列表
     */
    const batchRemoveTasks = useCallback((ids: string[]) => {
        setData(prev => prev.filter(item => !ids.includes(item.id || '')));
        setPagination(prev => ({...prev, total: prev.total - ids.length}));
    }, []);

    // 优化：使用 useMemo 缓存空状态检查
    const isEmpty = useMemo(() => data.length === 0, [data.length]);
    const hasData = useMemo(() => data.length > 0, [data.length]);

    return {
        data,
        isLoading,
        error,
        pagination,
        filters,
        loadTasks,
        refetch,
        handlePageChange,
        handleFilterChange,
        removeTaskFromList,
        addTaskToList,
        updateTaskInList,
        batchRemoveTasks,
        isEmpty,
        hasData
    };
};

/**
 * 任务详情 Hook
 */
export const useTaskDetail = (taskId: string) => {
    const [detail, setDetail] = useState<TaskDetailResponse | null>(null);
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState<Error | null>(null);

    const loadDetail = useCallback(async () => {
        setIsLoading(true);
        setError(null);

        try {
            const result = await taskApi.getTaskDetails(taskId);
            setDetail(result);
        } catch (err: any) {
            const errorMessage = err.message || '加载任务详情失败';
            setError(err instanceof Error ? err : new Error(errorMessage));
            message.error(errorMessage);
            console.error('Failed to load task detail:', err);
        } finally {
            setIsLoading(false);
        }
    }, [taskId]);

    return {
        detail,
        isLoading,
        error,
        loadDetail,
        refetch: loadDetail
    };
};

/**
 * 任务操作 Hook（添加、更新、删除等）
 */
export const useTaskOperations = () => {
    const [isOperating, setIsOperating] = useState(false);

    const addTask = useCallback(async (taskRequest: any) => {
        setIsOperating(true);
        try {
            return await taskApi.addTask(taskRequest);
        } finally {
            setIsOperating(false);
        }
    }, []);

    const updateTask = useCallback(async (id: string, taskRequest: any) => {
        setIsOperating(true);
        try {
            return await taskApi.updateTask(id, taskRequest);
        } finally {
            setIsOperating(false);
        }
    }, []);

    const deleteTask = useCallback(async (id: string) => {
        setIsOperating(true);
        try {
            return await taskApi.deleteTask(id);
        } finally {
            setIsOperating(false);
        }
    }, []);

    const confirmTask = useCallback(async (id: string) => {
        setIsOperating(true);
        try {
            return await taskApi.confirmTask(id);
        } finally {
            setIsOperating(false);
        }
    }, []);

    const assignTask = useCallback(async (taskId: string, allocationRequest?: any) => {
        setIsOperating(true);
        try {
            return await taskApi.assignTask(taskId, allocationRequest);
        } finally {
            setIsOperating(false);
        }
    }, []);

    const stopTask = useCallback(async (id: string) => {
        setIsOperating(true);
        try {
            return await taskApi.stopTask(id);
        } finally {
            setIsOperating(false);
        }
    }, []);

    const batchDeleteTask = useCallback(async (ids: string[]) => {
        setIsOperating(true);
        try {
            return await taskApi.batchDeleteTask(ids);
        } finally {
            setIsOperating(false);
        }
    }, []);

    return {
        isOperating,
        addTask,
        updateTask,
        deleteTask,
        confirmTask,
        assignTask,
        stopTask,
        batchDeleteTask
    };
};
