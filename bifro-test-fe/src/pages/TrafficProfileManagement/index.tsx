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

import React, {useCallback, useEffect, useRef, useState} from 'react';
import {
    Button,
    Card,
    Descriptions,
    Form,
    Input,
    message,
    Modal,
    Popconfirm,
    Select,
    Space,
    Spin,
    Steps,
    Table,
    Tabs,
    Typography,
} from 'antd';
import {DeleteOutlined, EditOutlined, EyeOutlined, PlusOutlined, ReloadOutlined, SearchOutlined} from '@ant-design/icons';
import {useTranslation} from 'react-i18next';
import type {ColumnsType} from 'antd/es/table';
import {Line, LineChart, ResponsiveContainer, Tooltip as ReTooltip, XAxis, YAxis} from 'recharts';
import type {WaveformProfile} from '../../features/profile';
import {
    createProfile,
    deleteProfile,
    getProfile,
    getProfilePreview,
    listProfilesPage,
    updateProfile
} from '../../features/profile';
import type {WaveformEditorValue} from '../../components/WaveformEditor';
import {WaveformEditor} from '../../components/WaveformEditor';
import {formatDateTime} from '../../utils/taskUtils';
import groupApi from '../../features/group';
import GroupManagementPage from '../GroupManagementPage';
import type {MqttGroup} from '../../features/group';
import {useTablePagination} from '../../hooks/useTablePagination';

interface ProfileGroupListItem {
    id: string;
    name: string;
    description?: string;
    profileCount?: number;
    createdAt?: string;
}

const {Text} = Typography;
const DEFAULT_GROUP_NAMES = new Set(['Default Group', String.fromCharCode(0x9ed8, 0x8ba4, 0x5206, 0x7ec4)]);

/* ─────────────────── Utility functions ─────────────────── */

function fmtDuration(ms: number): string {
    if (ms < 1000) return `${ms}ms`;
    const sec = Math.round(ms / 1000);
    if (sec < 60) return `${sec}s`;
    const min = Math.floor(sec / 60);
    const s = sec % 60;
    return s > 0 ? `${min}m ${s}s` : `${min}m`;
}

function fmtNumber(n?: number): string {
    if (n == null) return '—';
    return n.toLocaleString();
}

function calcIntegral(dataPoints?: number[][]): number {
    if (!dataPoints || dataPoints.length < 2) return 0;
    let sum = 0;
    for (let i = 0; i < dataPoints.length - 1; i++) {
        sum += (dataPoints[i][1] + dataPoints[i + 1][1]) / 2
            * (dataPoints[i + 1][0] - dataPoints[i][0]) / 1000;
    }
    return Math.round(sum);
}

/* ─────────────────── Sparkline ─────────────────── */

interface SparklineProps {
    profileId: string;
}

const Sparkline: React.FC<SparklineProps> = ({profileId}) => {
    const [points, setPoints] = useState<{ t: number; v: number }[] | null>(null);

    useEffect(() => {
        let cancelled = false;
        getProfilePreview(profileId)
            .then(data => {
                if (!cancelled) setPoints(data.map(([t, v]) => ({t, v})));
            })
            .catch(() => {
                if (!cancelled) setPoints([]);
            });
        return () => {
            cancelled = true;
        };
    }, [profileId]);

    if (points === null) return <Spin size="small"/>;
    if (points.length === 0) return <Text type="secondary">—</Text>;

    return (
        <LineChart width={120} height={32} data={points} margin={{top: 2, right: 2, bottom: 2, left: 2}}>
            <Line type="monotone" dataKey="v" stroke="#1677ff" dot={false} strokeWidth={1.5}/>
        </LineChart>
    );
};

/* ─────────────────── Detail modal ─────────────────── */

interface DetailModalProps {
    profile: WaveformProfile | null;
    onClose: () => void;
}

const DetailModal: React.FC<DetailModalProps> = ({profile, onClose}) => {
    const {t} = useTranslation();
    const [previewData, setPreviewData] = useState<{ t: number; v: number }[] | null>(null);
    const profileId = profile?.id;

    useEffect(() => {
        if (!profileId) {
            setPreviewData(null);
            return;
        }
        let cancelled = false;
        setPreviewData(null);
        getProfilePreview(profileId)
            .then(data => {
                if (!cancelled) setPreviewData(data.map(([t, v]) => ({t, v})));
            })
            .catch(() => {
                if (!cancelled) setPreviewData([]);
            });
        return () => {
            cancelled = true;
        };
    }, [profileId]);

    if (!profile) return null;

    const integral = profile.integral ?? calcIntegral(profile.dataPoints);

    return (
        <Modal
            open={!!profile}
            title={profile.name}
            onCancel={onClose}
            footer={<Button onClick={onClose}>{t('common.close')}</Button>}
            width={800}
        >
            <Descriptions column={3} size="small" style={{marginBottom: 16}}>
                <Descriptions.Item label={t('profile.detail.duration')}>{fmtDuration(profile.totalDurationMs)}</Descriptions.Item>
                <Descriptions.Item label={t('profile.detail.maxQps')}>{fmtNumber(profile.maxQps)}</Descriptions.Item>
                <Descriptions.Item label={t('profile.detail.peakQps')}>{fmtNumber(profile.peakQps)}</Descriptions.Item>
                <Descriptions.Item
                    label={t('profile.detail.avgQps')}>{fmtNumber(profile.avgQps != null ? Math.round(profile.avgQps) : undefined)}</Descriptions.Item>
                <Descriptions.Item label={t('profile.detail.integral')}>
                    <Text strong style={{color: '#1677ff', fontSize: 15}}>{fmtNumber(integral)} {t('profile.detail.count')}</Text>
                </Descriptions.Item>
                {profile.targetTotalCount != null && (
                    <Descriptions.Item label={t('profile.detail.targetCount')}>{fmtNumber(profile.targetTotalCount)} {t('profile.detail.count')}</Descriptions.Item>
                )}
                {profile.description && (
                    <Descriptions.Item label={t('common.description')} span={3}>{profile.description}</Descriptions.Item>
                )}
                <Descriptions.Item label={t('common.createdAt')} span={3}>
                    {formatDateTime(profile.createdAt) || '—'}
                </Descriptions.Item>
            </Descriptions>

            {previewData === null ? (
                <div style={{textAlign: 'center', padding: 32}}><Spin/></div>
            ) : previewData.length === 0 ? (
                <Text type="secondary">{t('common.noData')}</Text>
            ) : (
                <ResponsiveContainer width="100%" height={240}>
                    <LineChart data={previewData} margin={{top: 8, right: 16, bottom: 8, left: 8}}>
                        <XAxis
                            dataKey="t"
                            tickFormatter={v => fmtDuration(v as number)}
                            tick={{fontSize: 11}}
                        />
                        <YAxis tick={{fontSize: 11}}/>
                        <ReTooltip
                            formatter={(v: number) => [t('profile.waveform.tooltipQps', {v}), t('profile.waveform.columns.qps')]}
                            labelFormatter={v => t('profile.labelFormatter.time', {v: fmtDuration(v as number)})}
                        />
                        <Line type="monotone" dataKey="v" stroke="#1677ff" dot={false} strokeWidth={2}/>
                    </LineChart>
                </ResponsiveContainer>
            )}
        </Modal>
    );
};

/* ─────────────────── Create/Edit profile modal (two steps) ─────────────────── */

interface CreateModalProps {
    open: boolean;
    groupOptions: { label: string; value: string }[];
    defaultGroup?: string;
    editingProfile?: WaveformProfile | null;
    onClose: () => void;
    onSuccess: () => void;
}

const DEFAULT_DURATION_MS = 30 * 60_000;
const DEFAULT_MAX_QPS = 500;

const CreateProfileModal: React.FC<CreateModalProps> = ({
                                                            open,
                                                            groupOptions,
                                                            defaultGroup,
                                                            editingProfile,
                                                            onClose,
                                                            onSuccess,
                                                        }) => {
    const {t} = useTranslation();
    const isEdit = !!editingProfile;
    const [step, setStep] = useState(0);
    const [form] = Form.useForm();
    const [loading, setLoading] = useState(false);

    // Step-1 form values saved before Form.Items unmount (Ant Design deregisters on unmount)
    const [savedMeta, setSavedMeta] = useState<{ name: string; description?: string; group: string } | null>(null);

    const [editorValue, setEditorValue] = useState<WaveformEditorValue>({
        totalDurationMs: DEFAULT_DURATION_MS,
        maxQps: DEFAULT_MAX_QPS,
        dataPoints: [[0, 0], [DEFAULT_DURATION_MS, 0]],
    });

    // Edit mode: load full profile data into editor on open
    useEffect(() => {
        if (open && editingProfile) {
            form.setFieldsValue({
                name: editingProfile.name,
                description: editingProfile.description,
                group: editingProfile.group,
            });
            // Fetch all dataPoints (list API may not include them)
            getProfile(editingProfile.id).then(full => {
                setEditorValue({
                    totalDurationMs: full.totalDurationMs,
                    maxQps: full.maxQps ?? DEFAULT_MAX_QPS,
                    dataPoints: full.dataPoints ?? [[0, 0], [full.totalDurationMs, 0]],
                    targetTotalCount: full.targetTotalCount ?? undefined,
                });
            }).catch(() => {
                setEditorValue({
                    totalDurationMs: editingProfile.totalDurationMs,
                    maxQps: editingProfile.maxQps ?? DEFAULT_MAX_QPS,
                    dataPoints: [[0, 0], [editingProfile.totalDurationMs, 0]],
                });
            });
        } else if (open) {
            form.setFieldsValue({group: defaultGroup || undefined});
        }
    }, [open, editingProfile, defaultGroup, form]);

    const reset = () => {
        setStep(0);
        form.resetFields();
        setSavedMeta(null);
        setEditorValue({
            totalDurationMs: DEFAULT_DURATION_MS,
            maxQps: DEFAULT_MAX_QPS,
            dataPoints: [[0, 0], [DEFAULT_DURATION_MS, 0]],
        });
    };

    const handleCancel = () => {
        reset();
        onClose();
    };

    // Step 1 → Step 2: snapshot form values before unmount
    const handleNext = async () => {
        const values = await form.validateFields();
        setSavedMeta({
            name: values.name.trim(),
            description: values.description?.trim(),
            group: values.group as string,
        });
        setStep(1);
    };

    // Step 2 → save
    const handleSave = async () => {
        if (!savedMeta) return;
        setLoading(true);
        try {
            const req = {
                name: savedMeta.name,
                description: savedMeta.description,
                group: savedMeta.group,
                dataPoints: editorValue.dataPoints,
                totalDurationMs: editorValue.totalDurationMs,
                maxQps: editorValue.maxQps,
                targetTotalCount: editorValue.targetTotalCount,
            };
            if (isEdit && editingProfile) {
                await updateProfile(editingProfile.id, req);
                message.success(`${savedMeta.name} ${t('common.success')}`);
            } else {
                await createProfile(req);
                message.success(`${savedMeta.name} ${t('common.success')}`);
            }
            reset();
            onSuccess();
            onClose();
        } catch (e: unknown) {
            message.error((e as Error).message || t('common.operationFailed'));
        } finally {
            setLoading(false);
        }
    };

    const footer = step === 0
        ? [
            <Button key="cancel" onClick={handleCancel}>{t('common.cancel')}</Button>,
            <Button key="next" type="primary" onClick={handleNext}>{t('common.confirm')} →</Button>,
        ]
        : [
            <Button key="cancel" onClick={handleCancel}>{t('common.cancel')}</Button>,
            <Button key="prev" onClick={() => setStep(0)}>← {t('common.reset')}</Button>,
            <Button key="save" type="primary" loading={loading} onClick={handleSave}>
                {isEdit ? t('common.save') : t('common.save')}
            </Button>,
        ];

    return (
        <Modal
            open={open}
            title={isEdit ? `${t('common.edit')}: ${editingProfile?.name}` : t('profile.createProfile')}
            onCancel={handleCancel}
            footer={footer}
            width={step === 0 ? 480 : 1100}
            destroyOnClose
        >
            <Steps
                current={step}
                size="small"
                style={{marginBottom: 24}}
                items={[{title: t('common.name')}, {title: t('common.description')}]}
            />

            {/* ── Step 1: Basic info ── */}
            {step === 0 && (
                <Form form={form} layout="vertical">
                    <Form.Item
                        name="name"
                        label={t('profile.profileName')}
                        rules={[
                            {required: true, message: t('profile.profileName')},
                            {max: 100, message: t('profile.profileName')},
                        ]}
                    >
                        <Input placeholder={t('profile.profileName')}/>
                    </Form.Item>
                    <Form.Item
                        name="group"
                        label={t('common.group')}
                        rules={[{required: true, message: t('common.group')}]}
                    >
                        <Select
                            placeholder={t('common.group')}
                            options={groupOptions}
                        />
                    </Form.Item>
                    <Form.Item name="description" label={t('common.description')}>
                        <Input.TextArea rows={2} placeholder={t('common.description')}/>
                    </Form.Item>
                </Form>
            )}

            {/* ── Step 2: Draw curve (3 params embedded at editor top) ── */}
            {step === 1 && (
                <WaveformEditor value={editorValue} onChange={setEditorValue}/>
            )}
        </Modal>
    );
};

/* ─────────────────── Main page ─────────────────── */

const TrafficProfileManagement: React.FC = () => {
    const {t} = useTranslation();
    const [profiles, setProfiles] = useState<WaveformProfile[]>([]);
    const [loading, setLoading] = useState(false);
    const [keyword, setKeyword] = useState('');
    const [selectedGroup, setSelectedGroup] = useState('');
    const [groupSelectOptions, setGroupSelectOptions] = useState<{ label: string; value: string }[]>([]);
    const [activeTab, setActiveTab] = useState('profiles');
    const [createOpen, setCreateOpen] = useState(false);
    const [editingProfile, setEditingProfile] = useState<WaveformProfile | null>(null);
    const [detailProfile, setDetailProfile] = useState<WaveformProfile | null>(null);
    const initialLoadRef = useRef(false);
    const {
        currentPage,
        pageSize,
        applyPageInfo,
        getPageAfterDelete,
        getTablePagination,
    } = useTablePagination({defaultPageSize: 10, totalLabel: t('common.total')});

    const sortGroupOptions = useCallback((options: { label: string; value: string }[]) => {
        return [...options].sort((a, b) => {
            const aDefault = DEFAULT_GROUP_NAMES.has(a.label);
            const bDefault = DEFAULT_GROUP_NAMES.has(b.label);
            if (aDefault !== bDefault) {
                return aDefault ? -1 : 1;
            }
            return 0;
        });
    }, []);

    const getDefaultGroup = useCallback((options = groupSelectOptions) => {
        return options[0]?.value || '';
    }, [groupSelectOptions]);

    const fetchProfiles = useCallback(async (
        kw?: string,
        page: number = currentPage,
        size: number = pageSize,
        group: string = selectedGroup
    ) => {
        setLoading(true);
        try {
            const pageInfo = await listProfilesPage(kw, page, size, group || undefined);
            setProfiles(pageInfo.content || []);
            applyPageInfo(pageInfo, page, size);
        } catch (e: unknown) {
            message.error((e as Error).message || t('profile.msg.loadFailed'));
        } finally {
            setLoading(false);
        }
    }, [applyPageInfo, currentPage, pageSize, selectedGroup, t]);

    const loadGroupSelectOptions = useCallback(async () => {
        try {
            const allGroups = await groupApi.getAllGroupsForSelect('PROFILE');
            const options = sortGroupOptions(allGroups.map((g: MqttGroup) => ({
                label: g.name,
                value: g.id,
            })));
            setGroupSelectOptions(options);
            return options;
        } catch (error) {
            console.error('Failed to load profile group options:', error);
            return [];
        }
    }, [sortGroupOptions]);

    useEffect(() => {
        if (initialLoadRef.current) {
            return;
        }
        let cancelled = false;
        loadGroupSelectOptions().then(options => {
            if (cancelled) {
                return;
            }
            initialLoadRef.current = true;
            const nextGroup = getDefaultGroup(options);
            setSelectedGroup(nextGroup);
            fetchProfiles(undefined, 1, pageSize, nextGroup);
        });
        return () => {
            cancelled = true;
        };
        // Initial load only; table actions explicitly reload with current filters.
    }, [fetchProfiles, getDefaultGroup, loadGroupSelectOptions, pageSize]);

    const handleSearch = () => fetchProfiles(keyword.trim() || undefined, 1, pageSize, selectedGroup);
    const handleReset = () => {
        setKeyword('');
        const nextGroup = getDefaultGroup();
        setSelectedGroup(nextGroup);
        fetchProfiles(undefined, 1, pageSize, nextGroup);
    };
    const handleGroupChange = (group: string) => {
        const nextGroup = group || getDefaultGroup();
        setSelectedGroup(nextGroup);
        fetchProfiles(keyword.trim() || undefined, 1, pageSize, nextGroup);
    };
    const handleTabChange = (key: string) => {
        setActiveTab(key);
        if (key === 'profiles') {
            loadGroupSelectOptions().then(options => {
                const nextGroup = selectedGroup && options.some(option => option.value === selectedGroup)
                    ? selectedGroup
                    : getDefaultGroup(options);
                setSelectedGroup(nextGroup);
                fetchProfiles(keyword.trim() || undefined, currentPage, pageSize, nextGroup);
            });
        }
    };

    const handleDelete = async (id: string, name: string) => {
        try {
            await deleteProfile(id);
            message.success(`${name} ${t('common.success')}`);
            await fetchProfiles(keyword.trim() || undefined, getPageAfterDelete(), pageSize, selectedGroup);
        } catch (e: unknown) {
            message.error((e as Error).message || t('common.operationFailed'));
        }
    };

    const columns: ColumnsType<WaveformProfile> = [
        {
            title: t('profile.columns.name'),
            dataIndex: 'name',
            key: 'name',
            width: 180,
            ellipsis: true,
            render: (name: string) => <Text strong>{name}</Text>,
        },
        {
            title: t('common.group'),
            dataIndex: 'group',
            key: 'group',
            width: 120,
            render: (group: string) => group
                ? (groupSelectOptions.find(opt => opt.value === group)?.label || group)
                : <Text type="secondary">—</Text>,
        },
        {
            title: t('profile.columns.duration'),
            dataIndex: 'totalDurationMs',
            key: 'totalDurationMs',
            width: 90,
            render: (v: number) => fmtDuration(v),
        },
        {
            title: t('profile.columns.maxQps'),
            dataIndex: 'maxQps',
            key: 'maxQps',
            width: 100,
            render: (v?: number) => fmtNumber(v),
        },
        {
            title: t('profile.columns.integral'),
            key: 'integral',
            width: 110,
            render: (_, record) => {
                const integral = record.integral ?? calcIntegral(record.dataPoints);
                return (
                    <Text strong style={{color: '#1677ff'}}>
                        {fmtNumber(integral)}
                    </Text>
                );
            },
        },
        {
            title: t('profile.columns.targetCount'),
            dataIndex: 'targetTotalCount',
            key: 'targetTotalCount',
            width: 100,
            render: (v?: number) => v != null
                ? <Text style={{color: '#52c41a'}}>{fmtNumber(v)}</Text>
                : <Text type="secondary">—</Text>,
        },
        {
            title: t('common.description'),
            key: 'preview',
            width: 140,
            render: (_, record) => <Sparkline profileId={record.id}/>,
        },
        {
            title: t('common.description'),
            dataIndex: 'description',
            key: 'description',
            width: 220,
            ellipsis: true,
            render: (v: string) => v || <Text type="secondary">—</Text>,
        },
        {
            title: t('common.actions'),
            key: 'action',
            width: 220,
            render: (_, record) => (
                <Space.Compact size="small">
                    <Button
                        type="link"
                        icon={<EyeOutlined/>}
                        onClick={() => setDetailProfile(record)}
                    >
                        {t('common.detail')}
                    </Button>
                    <Button
                        type="link"
                        icon={<EditOutlined/>}
                        onClick={() => setEditingProfile(record)}
                    >
                        {t('common.edit')}
                    </Button>
                    <Popconfirm
                        title={`${t('common.deleteConfirm')} ${record.name}`}
                        description={t('common.deleteConfirm')}
                        onConfirm={() => handleDelete(record.id, record.name)}
                        okText={t('common.delete')}
                        cancelText={t('common.cancel')}
                        okButtonProps={{danger: true}}
                    >
                        <Button type="link" danger icon={<DeleteOutlined/>}>
                            {t('common.delete')}
                        </Button>
                    </Popconfirm>
                </Space.Compact>
            ),
        },
    ];

    return (
        <div>
            <Card>
                <Tabs
                    activeKey={activeTab}
                    onChange={handleTabChange}
                    items={[
                        {key: 'profiles', label: t('profile.title')},
                        {key: 'groups', label: t('profile.tabs.groups')},
                    ]}
                />
                {activeTab === 'groups' ? (
                    <GroupManagementPage<ProfileGroupListItem>
                        groupType="PROFILE"
                        countField="profileCount"
                        countColumnTitleKey="profile.columns.profileCount"
                        createTitleKey="profile.groupTitle"
                        loadErrorLogLabel="profile"
                    />
                ) : (
                    <>
                <div style={{display: 'flex', justifyContent: 'flex-end', marginBottom: 16, gap: 12}}>
                    <div style={{display: 'flex', gap: 12, flex: 1}}>
                        <Input
                            placeholder={t('common.search')}
                            value={keyword}
                            onChange={e => setKeyword(e.target.value)}
                            onPressEnter={handleSearch}
                            style={{width: 220}}
                            allowClear
                            prefix={<SearchOutlined style={{color: '#bfbfbf'}}/>}
                            onClear={handleReset}
                        />
                        <Button type="primary" icon={<SearchOutlined/>} onClick={handleSearch}>
                            {t('common.search')}
                        </Button>
                        {keyword && (
                            <Button onClick={handleReset}>{t('common.reset')}</Button>
                        )}
                        <Select
                            placeholder={t('common.group')}
                            style={{width: 150}}
                            value={selectedGroup || undefined}
                            onChange={handleGroupChange}
                            options={groupSelectOptions}
                            disabled={groupSelectOptions.length === 0}
                        />
                    </div>
                    <Button
                        icon={<ReloadOutlined/>}
                        onClick={() => {
                            loadGroupSelectOptions().then(options => {
                                const nextGroup = selectedGroup && options.some(option => option.value === selectedGroup)
                                    ? selectedGroup
                                    : getDefaultGroup(options);
                                setSelectedGroup(nextGroup);
                                fetchProfiles(keyword.trim() || undefined, currentPage, pageSize, nextGroup);
                            });
                        }}
                    >
                        {t('common.refresh')}
                    </Button>
                    <Button
                        type="primary"
                        icon={<PlusOutlined/>}
                        onClick={() => setCreateOpen(true)}
                    >
                        {t('profile.createProfile')}
                    </Button>
                </div>
                <Spin spinning={loading}>
                    <Table
                        rowKey="id"
                        columns={columns}
                        dataSource={profiles}
                        size="small"
                        scroll={{x: 1060}}
                        pagination={getTablePagination((page, size) =>
                            fetchProfiles(keyword.trim() || undefined, page, size, selectedGroup))}
                    />
                </Spin>
                    </>
                )}
            </Card>

            {/* Create modal */}
            <CreateProfileModal
                open={createOpen}
                groupOptions={groupSelectOptions}
                defaultGroup={selectedGroup || undefined}
                onClose={() => setCreateOpen(false)}
                onSuccess={() => fetchProfiles(keyword.trim() || undefined, 1, pageSize, selectedGroup)}
            />

            {/* Edit modal */}
            <CreateProfileModal
                open={!!editingProfile}
                groupOptions={groupSelectOptions}
                editingProfile={editingProfile}
                onClose={() => setEditingProfile(null)}
                onSuccess={() => fetchProfiles(keyword.trim() || undefined, currentPage, pageSize, selectedGroup)}
            />

            {/* Detail modal */}
            <DetailModal
                profile={detailProfile}
                onClose={() => setDetailProfile(null)}
            />
        </div>
    );
};

export default TrafficProfileManagement;
