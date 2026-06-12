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

import React, {useCallback, useEffect, useMemo, useRef, useState} from 'react';
import {message, Modal, Progress, Space, Spin, Tabs, Tag, Tooltip} from 'antd';
import {useTranslation} from 'react-i18next';
import {useTaskData} from '../../features/task/model';
import {
    AdvancedConfigPanel,
    StressParamsPanel,
    TaskConfigPanel
} from './components/TaskDetailModal/TaskBasicInfoSection';
import TaskSubTasksSection from './components/TaskDetailModal/TaskSubTasksSection';
import InstanceDetailModal from './components/TaskDetailModal/InstanceDetailModal';
import NodeMetricsModal from './components/TaskDetailModal/NodeMetricsModal';
import groupApi from '../../features/group';
import {listProfiles} from '../../features/profile';
import type {WaveformProfile} from '../../features/profile';
import type {SubTaskDetail, TaskConfig, TaskDetailResponse} from '../../features/task';
import {getStatusColor, getStatusText, isTaskTerminal} from '../../utils/taskUtils';

// Data cache interface
interface CacheData {
    basicInfo: TaskDetailResponse | null;
    taskConfig: TaskConfig | null;
    subTasks: Record<string, SubTaskDetail> | null;
    timestamp: number;
}

interface TaskDetailModalProps {
    visible: boolean;
    taskId: string | null;
    onClose: () => void;
    onTaskDetailLoaded?: (detail: TaskDetailResponse) => void;
}

const TaskDetailModal: React.FC<TaskDetailModalProps> = ({
                                                             visible,
                                                             taskId,
                                                             onClose,
                                                             onTaskDetailLoaded
                                                         }) => {
    const {t} = useTranslation();
    // Data cache (using sessionStorage)
    const cacheRef = useRef<Map<string, CacheData>>(new Map());
    const [useCache] = useState(true);

    // Basic info
    const [basicInfo, setBasicInfo] = useState<TaskDetailResponse | null>(null);
    const [taskConfig, setTaskConfig] = useState<TaskConfig | null>(null);
    const [basicLoading, setBasicLoading] = useState(false);

    // Subtask info (loaded on demand)
    const [subTaskDetails, setSubTaskDetails] = useState<Record<string, SubTaskDetail>>({});
    const [subTasksLoaded, setSubTasksLoaded] = useState(false);
    const [subTasksLoading] = useState(false);

    // Currently active Tab
    const [activeTabKey, setActiveTabKey] = useState('basic');

    // Global loading progress
    const [globalLoading, setGlobalLoading] = useState(0);

    // Polling interval
    const pollingInterval = 2000;

    const {loadTaskBasicInfo, loadTaskConfig, loadTaskSubTasks} = useTaskData();
    const [groupOptions, setGroupOptions] = useState<{ label: string; value: string }[]>([]);
    const [trafficProfiles, setTrafficProfiles] = useState<WaveformProfile[]>([]);

    // Read data from cache
    const loadFromCache = useCallback((cacheKey: string): CacheData | null => {
        if (!useCache) return null;
        const cached = cacheRef.current.get(cacheKey);
        if (cached && Date.now() - cached.timestamp < 5 * 60 * 1000) { // 5min cache TTL
            return cached;
        }
        return null;
    }, [useCache]);

    // Save data to cache
    const saveToCache = useCallback((cacheKey: string, data: Partial<CacheData>) => {
        if (!useCache) return;
        const existing = cacheRef.current.get(cacheKey) || {
            basicInfo: null,
            taskConfig: null,
            subTasks: null,
            timestamp: Date.now()
        };
        cacheRef.current.set(cacheKey, {
            ...existing,
            ...data,
            timestamp: Date.now()
        });
    }, [useCache]);

    // Subtask Tab state
    const [showNodeMetricsModal, setShowNodeMetricsModal] = useState(false);
    const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);
    const [selectedNodeName, setSelectedNodeName] = useState<string>('');

    // Instance details Modal state
    const [instanceDetailVisible, setInstanceDetailVisible] = useState(false);
    const [instanceDetailNodeId, setInstanceDetailNodeId] = useState<string | null>(null);
    const [instanceDetailNodeName, setInstanceDetailNodeName] = useState<string>('');

    // Assemble complete taskDetail
    const taskDetail: TaskDetailResponse | null = useMemo(() => {
        if (!basicInfo) return null;
        return {
            success: true,
            taskId: basicInfo.taskId,
            taskName: basicInfo.taskName,
            group: basicInfo.group,
            mainTaskView: basicInfo.mainTaskView,
            publishProfile: basicInfo.publishProfile,
            brokers: basicInfo.brokers,
            createTime: basicInfo.createTime,
            startTime: basicInfo.startTime,
            endTime: basicInfo.endTime,
            subTaskDetails: Object.keys(subTaskDetails).length > 0 ? subTaskDetails : undefined,
        };
    }, [basicInfo, subTaskDetails]);

    // Load basic info
    useEffect(() => {
        if (!visible || !taskId) return;

        setGlobalLoading(20);
        setSubTaskDetails({});
        setSubTasksLoaded(false);
        setActiveTabKey('config');

        // Try loading from cache
        const cacheKey = `task_${taskId}`;
        const cached = loadFromCache(cacheKey);

        if (cached?.basicInfo) {
            setBasicInfo(cached.basicInfo);
            setTaskConfig(cached.taskConfig || null);
            onTaskDetailLoaded?.(cached.basicInfo);
            setGlobalLoading(0);
        } else {
            setTaskConfig(null);
            setBasicLoading(true);
            Promise.all([loadTaskBasicInfo(taskId), loadTaskConfig(taskId)]).then(([info, config]) => {
                const detail: TaskDetailResponse = {
                    success: true,
                    taskId: info.taskId,
                    taskName: info.taskName,
                    group: info.group,
                    mainTaskView: info.mainTaskView,
                    publishProfile: info.publishProfile,
                    brokers: info.brokers || [],
                    createTime: info.createTime,
                    startTime: info.startTime,
                    endTime: info.endTime,
                };
                setBasicInfo(detail);
                setTaskConfig(config);
                onTaskDetailLoaded?.(detail);
                saveToCache(cacheKey, {basicInfo: detail, taskConfig: config});
            }).catch(error => {
                message.error(t('task.msg.loadBasicFailed'));
                console.error('Failed to load task basic info:', error);
            }).finally(() => {
                setBasicLoading(false);
                setGlobalLoading(0);
            });
        }

        // Reset state
        return () => {
            setShowNodeMetricsModal(false);
            setSelectedNodeId(null);
            setSelectedNodeName('');
            setInstanceDetailVisible(false);
            setInstanceDetailNodeId(null);
            setInstanceDetailNodeName('');
        };
    }, [visible, taskId, loadTaskBasicInfo, loadTaskConfig, loadFromCache, saveToCache, onTaskDetailLoaded]);

    // Poll to refresh task status
    useEffect(() => {
        const taskStage = basicInfo?.mainTaskView?.taskWorkStage;
        if (!visible || !taskId || !taskStage) return;

        // Skip polling if task has terminated
        if (isTaskTerminal(taskStage)) return;

        const timer = setInterval(() => {
            loadTaskBasicInfo(taskId).then(info => {
                const detail: TaskDetailResponse = {
                    success: true,
                    taskId: info.taskId,
                    taskName: info.taskName,
                    group: info.group,
                    mainTaskView: info.mainTaskView,
                    publishProfile: info.publishProfile,
                    brokers: info.brokers || [],
                    createTime: info.createTime,
                    startTime: info.startTime,
                    endTime: info.endTime,
                };
                setBasicInfo(detail);
                saveToCache(`task_${taskId}`, {basicInfo: detail});

                // Auto-refresh when subtasks were previously loaded to avoid state desync
                if (subTasksLoaded) {
                    loadTaskSubTasks(taskId).then(data => {
                        const details = data.subTaskDetails || {};
                        setSubTaskDetails(details);
                        saveToCache(`task_${taskId}`, {subTasks: details});
                    }).catch(error => {
                        console.error('Failed to poll subtasks:', error);
                    });
                }
            }).catch(error => {
                console.error('Failed to poll task basic info:', error);
            });
        }, pollingInterval);

        return () => clearInterval(timer);
    }, [
        visible, taskId,
        basicInfo?.mainTaskView?.taskWorkStage,
        subTasksLoaded, loadTaskBasicInfo, loadTaskSubTasks, saveToCache, pollingInterval
    ]);

    // Load group options
    useEffect(() => {
        if (visible) {
            groupApi.getAllGroupsForSelect('BROKER').then(allGroups => {
                const options = allGroups.map((g) => ({
                    label: g.name,
                    value: g.id
                }));
                setGroupOptions(options);
            }).catch(error => {
                console.error('Failed to load group options:', error);
            });
        }
    }, [visible]);

    // Load traffic profiles for name display in task detail.
    useEffect(() => {
        if (visible) {
            listProfiles().then(setTrafficProfiles).catch(error => {
                setTrafficProfiles([]);
                console.error('Failed to load traffic profiles:', error);
            });
        }
    }, [visible]);

    // Tab switch handler
    const handleTabChange = useCallback((key: string) => {
        setActiveTabKey(key);

        if (key === 'subtasks' && !subTasksLoaded && taskId) {
            setGlobalLoading(30);

            const cacheKey = `task_${taskId}`;
            const cached = loadFromCache(cacheKey);

            if (cached?.subTasks) {
                setSubTaskDetails(cached.subTasks);
                setSubTasksLoaded(true);
                setGlobalLoading(0);
            } else {
                loadTaskSubTasks(taskId).then((data) => {
                    const details = data.subTaskDetails || {};
                    setSubTaskDetails(details);
                    setSubTasksLoaded(true);
                    saveToCache(cacheKey, {subTasks: details});
                }).catch(error => {
                    message.error(t('task.msg.loadSubtasksFailed'));
                    console.error('Failed to load subtasks:', error);
                }).finally(() => {
                    setGlobalLoading(0);
                });
            }
        }
    }, [taskId, subTasksLoaded, loadTaskSubTasks, loadFromCache, saveToCache]);

    // Keyboard shortcut handler
    useEffect(() => {
        if (!visible) return;

        const handleKeyDown = (e: KeyboardEvent) => {
            // ESC closes Modal
            if (e.key === 'Escape') {
                onClose();
                return;
            }

            // Ctrl/Cmd + 1-4 switch Tab
            if ((e.ctrlKey || e.metaKey) && e.key >= '1' && e.key <= '4') {
                e.preventDefault();
                const tabs = ['config', 'stress', 'advanced', 'subtasks'];
                const tabIndex = parseInt(e.key) - 1;
                if (tabs[tabIndex]) {
                    setActiveTabKey(tabs[tabIndex]);
                    handleTabChange(tabs[tabIndex]);
                }
            }
        };

        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [visible, onClose, handleTabChange]);

    // Handle "Live Monitor" button click
    const handleShowMetrics = useCallback((record: { nodeId: string; nodeName: string }) => {
        setSelectedNodeId(record.nodeId);
        setSelectedNodeName(record.nodeName);
        setShowNodeMetricsModal(true);
    }, []);

    // Handle "Instance Details" button click
    const handleShowInstances = useCallback((record: { nodeId: string; nodeName: string }) => {
        setInstanceDetailNodeId(record.nodeId);
        setInstanceDetailNodeName(record.nodeName);
        setInstanceDetailVisible(true);
    }, []);

    // Handle subtask refresh (called by state flow Tab)
    const handleRefreshSubTasks = useCallback(async () => {
        if (!taskId) return;

        try {
            const data = await loadTaskSubTasks(taskId);
            const details = data.subTaskDetails || {};
            setSubTaskDetails(details);
        } catch (error) {
            message.error(t('task.msg.refreshSubtaskFailed'));
            console.error('Failed to refresh subtasks:', error);
        }
    }, [taskId, loadTaskSubTasks]);

    // Tab config (4 tabs: Task Config / Stress Params / Advanced Config / Subtasks)
    const tabItems = useMemo(() => [
        {
            key: 'config',
            label: <span>{t('task.editor.tabs.config')}</span>,
            children: <TaskConfigPanel taskDetail={taskDetail} taskConfig={taskConfig} groupOptions={groupOptions}/>
        },
        {
            key: 'stress',
            label: <span>{t('task.editor.tabs.stressParams')}</span>,
            children: <StressParamsPanel
                taskDetail={taskDetail}
                taskConfig={taskConfig}
                trafficProfiles={trafficProfiles}
            />
        },
        {
            key: 'advanced',
            label: <span>{t('task.editor.tabs.advancedConfig')}</span>,
            children: <AdvancedConfigPanel taskDetail={taskDetail} taskConfig={taskConfig}/>
        },
        {
            key: 'subtasks',
            label: <span>{t('task.detail.tabs.subtasks')}</span>,
            children: <TaskSubTasksSection
                subTaskDetails={subTaskDetails}
                taskDetail={taskDetail}
                subTasksLoading={subTasksLoading}
                onShowMetrics={handleShowMetrics}
                onShowInstances={handleShowInstances}
                onRefresh={handleRefreshSubTasks}
            />
        },
    ], [
        taskDetail, taskConfig, groupOptions,
        trafficProfiles,
        subTaskDetails, subTasksLoading,
        handleShowMetrics, handleShowInstances, handleRefreshSubTasks,
    ]);

    return (
        <>
            <Modal
                title={
                    <Space size={8}>
                        <span>{taskDetail?.taskName || taskId || t('task.title')}</span>
                        {taskDetail?.mainTaskView?.taskWorkStage && (
                            <Tag
                                color={getStatusColor(taskDetail.mainTaskView.taskWorkStage)}
                                style={{margin: 0, fontSize: 12}}
                            >
                                {getStatusText(taskDetail.mainTaskView.taskWorkStage)}
                            </Tag>
                        )}
                    </Space>
                }
                open={visible}
                onCancel={onClose}
                footer={null}
                width={1200}
                keyboard
                closeIcon={
                    <Tooltip title={t('task.detailModal.escClose')}>
                        <span>✕</span>
                    </Tooltip>
                }
            >
                {/* Global loading progress bar */}
                {globalLoading > 0 && (
                    <div style={{position: 'absolute', top: 0, left: 0, right: 0, zIndex: 1000}}>
                        <Progress
                            percent={globalLoading}
                            status="active"
                            showInfo={false}
                            strokeColor="#1890ff"
                            size="small"
                        />
                    </div>
                )}

                {/* Keyboard shortcut tips */}
                <div style={{
                    position: 'absolute',
                    top: -40,
                    right: 0,
                    fontSize: 12,
                    color: '#999',
                    background: '#f5f5f5',
                    padding: '4px 8px',
                    borderRadius: 4
                }}>
                    <Space>
                        <span>{t('task.detailModal.escClose')}</span>
                        <span>|</span>
                        <span>{t('task.detailModal.switchTab')}</span>
                    </Space>
                </div>

                {basicLoading ? (
                    <div style={{display: 'flex', justifyContent: 'center', padding: 60}}>
                        <Spin size="large"/>
                        <div style={{marginTop: 16, color: '#666'}}>{t('common.loading')}</div>
                    </div>
                ) : (
                    taskDetail && (
                        <Tabs
                            activeKey={activeTabKey}
                            items={tabItems}
                            onChange={handleTabChange}
                            animated={{inkBar: true, tabPane: false}}
                        />
                    )
                )}
            </Modal>

            {/* Sub-Modals */}
            <InstanceDetailModal
                visible={instanceDetailVisible}
                nodeId={instanceDetailNodeId || ''}
                taskId={taskDetail?.taskId || ''}
                nodeName={instanceDetailNodeName}
                onClose={() => setInstanceDetailVisible(false)}
            />
            <NodeMetricsModal
                visible={showNodeMetricsModal}
                nodeId={selectedNodeId || ''}
                taskId={taskDetail?.taskId || ''}
                nodeName={selectedNodeName}
                isTaskCompleted={isTaskTerminal(taskDetail?.mainTaskView?.taskWorkStage ?? '')}
                onClose={() => setShowNodeMetricsModal(false)}
            />
        </>
    );
};

export default TaskDetailModal;
