import React, { useState, useEffect } from 'react';
import { Modal, Form, Row, Col, Select, Switch, Input, InputNumber, Button, Tag, message, Tabs, Divider } from 'antd';
import { SettingOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { useTaskData } from './hooks';
import { TaskTypeValues, MqttQoSValues, TaskTemplateValues } from '../../types/task';
import type { TaskListItem, TaskRequest } from '../../types/task';
import taskGroupApi from '../../services/taskGroupApi';
import taskApi from '../../services/taskApi';
import type { TaskGroup } from '../../types/taskGroup';
import groupApi from '../../services/groupApi';
import type { MqttGroup } from '../../types/mqttGroup';

interface TaskEditorProps {
  visible: boolean;
  editingTask: TaskListItem | null;
  onCancel: () => void;
  onOk: (taskId: string | undefined, taskRequest: TaskRequest) => Promise<void>;
}

const TaskEditor: React.FC<TaskEditorProps> = ({
  visible,
  editingTask,
  onCancel,
  onOk
}) => {
  const navigate = useNavigate();
  const [form] = Form.useForm();
  const [currentTaskType, setCurrentTaskType] = useState<string>(TaskTypeValues.CONN);
  const [willEnabled, setWillEnabled] = useState<boolean>(false);
  const [authType, setAuthType] = useState<string>('normal');
  const { loadTaskDetail, loadBrokers } = useTaskData();
  const [brokers, setBrokers] = useState<any[]>([]);
  const [brokerLoading, setBrokerLoading] = useState(false);
  const [taskGroupSelectOptions, setTaskGroupSelectOptions] = useState<{ label: string; value: string }[]>([]);
  const [brokerGroupSelectOptions, setBrokerGroupSelectOptions] = useState<{ label: string; value: string }[]>([]);
  const [selectedBrokerGroup, setSelectedBrokerGroup] = useState<string>('');
  const [templateOptions, setTemplateOptions] = useState<Array<{ value: string; label: string; type: string }>>([]);

  // 加载任务模板选项
  const loadTemplateOptions = async () => {
    try {
      const templates = await taskApi.getTemplates();
      setTemplateOptions(templates);
    } catch (error) {
      console.error('加载任务模板选项失败:', error);
    }
  };

  // 加载任务分组选项
  const loadTaskGroupSelectOptions = async () => {
    try {
      const defaultGroup = await taskGroupApi.getOrCreateDefaultGroup();
      const allGroups = await taskGroupApi.getAllGroupsForSelect();
      const otherGroups = allGroups.filter((g: TaskGroup) => g.name !== '默认分组');
      const sortedGroups = [defaultGroup, ...otherGroups];
      const options = sortedGroups.map((g: TaskGroup) => ({ label: g.name, value: g.id }));
      setTaskGroupSelectOptions(options);
      return defaultGroup.id;
    } catch (error) {
      console.error('加载任务分组选项失败:', error);
      message.error('加载任务分组选项失败');
      return '';
    }
  };

  // 加载broker分组选项
  const loadBrokerGroupSelectOptions = async () => {
    try {
      const defaultGroup = await groupApi.getOrCreateDefaultGroup();
      const allGroups = await groupApi.getAllGroupsForSelect();
      const otherGroups = allGroups.filter((g: MqttGroup) => g.name !== '默认分组');
      const sortedGroups = [defaultGroup, ...otherGroups];
      const options = sortedGroups.map((g: MqttGroup) => ({ label: g.name, value: g.id }));
      setBrokerGroupSelectOptions(options);
      return defaultGroup.id;
    } catch (error) {
      console.error('加载broker分组选项失败:', error);
      message.error('加载broker分组选项失败');
      return '';
    }
  };

  // 初始化表单
  useEffect(() => {
    if (visible) {
      const initForm = async () => {
        const [defaultTaskGroupId, defaultBrokerGroupId] = await Promise.all([
          loadTaskGroupSelectOptions(),
          loadBrokerGroupSelectOptions()
        ]);
        loadTemplateOptions();
        if (editingTask) {
          loadTaskData(editingTask.id);
          loadBrokerList();
        } else {
          form.resetFields();
          form.setFieldsValue({
            template: TaskTemplateValues.CONN_STANDARD,
            group: defaultTaskGroupId || ''
          });
          setCurrentTaskType(TaskTypeValues.CONN);
          setWillEnabled(false);
          setSelectedBrokerGroup(defaultBrokerGroupId || '');
          loadBrokerList();
        }
      };
      initForm();
    }
  }, [visible, editingTask]);

  // 当分组改变时，重新加载 broker 列表
  useEffect(() => {
    if (visible) {
      loadBrokerList();
    }
  }, [selectedBrokerGroup, visible]);

  // 加载任务详情数据
  const loadTaskData = async (id: string) => {
    try {
      const detail = await loadTaskDetail(id);
      if (detail.mainTask) {
        form.setFieldsValue({
          ...detail.mainTask,
          taskName: detail.taskName,
          group: detail.group || '',
          template: detail.mainTask.template || (detail.mainTask.taskType === TaskTypeValues.CONN ? TaskTemplateValues.CONN_STANDARD : TaskTemplateValues.PUBSUB_STANDARD),
          brokers: detail.brokers?.map((broker: { host: string; port: number; brokerId?: string }) => broker.brokerId || `${broker.host}:${broker.port}`) || [],
          willConfig: detail.mainTask.willConfig || { willFlag: false },
          autoMultiAddress: detail.mainTask.enableAutoMultiAddress || false,
          wildcard: detail.mainTask.wildcard || false,
          mqtt5: detail.mainTask.mqtt5 || false,
          emptyClientId: detail.mainTask.emptyClientId || false
        });
        setCurrentTaskType(detail.mainTask.taskType);
        setWillEnabled(detail.mainTask.willConfig?.willFlag || false);
        setAuthType(detail.mainTask.authType || 'normal');
        if (detail.brokers && detail.brokers.length > 0) {
          const brokerGroup = detail.brokers[0]?.group || '';
          setSelectedBrokerGroup(brokerGroup);
        }
      }
    } catch (error) {
      console.error('Failed to load task detail:', error);
    }
  };

  // 加载broker列表
  const loadBrokerList = async () => {
    setBrokerLoading(true);
    try {
      const brokerData = await loadBrokers();
      const filtered = selectedBrokerGroup
        ? brokerData.filter((broker: any) => broker.group === selectedBrokerGroup)
        : brokerData;
      setBrokers(filtered);
    } catch (error) {
      console.error('Failed to load brokers:', error);
    } finally {
      setBrokerLoading(false);
    }
  };

  // 处理表单提交
  const handleOk = async () => {
    try {
      const values = await form.validateFields();
      const selectedBrokerIds: string[] = values.brokers || [];
      const brokerItems = selectedBrokerIds.map((brokerId: string) => {
        const found = brokers.find((b: any) => b.brokerId === brokerId);
        return found
          ? { brokerId: found.brokerId, host: found.host, port: found.port }
          : { brokerId, host: brokerId, port: 1883 };
      });

      const taskRequest: TaskRequest = {
        taskName: values.taskName,
        taskType: values.taskType,
        template: values.template || (values.taskType === TaskTypeValues.CONN ? TaskTemplateValues.CONN_STANDARD : TaskTemplateValues.PUBSUB_STANDARD),
        group: values.group || '',
        autoMultiAddress: values.autoMultiAddress || false,
        brokers: brokerItems,
        cleanSession: values.cleanSession !== false,
        totalClientCount: values.totalClientCount || 100,
        connectRate: values.connectRate || 100,
        disconnectRate: values.disconnectRate || 2000,
        fanOut: values.fanOut || 1,
        fanIn: values.fanIn || 1,
        topic: values.topic || 'b/t',
        qos: values.qos || 0,
        fixedTopic: values.fixedTopic || false,
        messageSize: values.messageSize || 32,
        pubIntervalInMs: values.pubIntervalInMs || 10000,
        stressDurationInSec: values.stressDurationInSec || 60,
        stageTimeoutInSec: values.stageTimeoutInSec || 30,
        delayAfterReadyInSec: values.delayAfterReadyInSec || 1,
        skipStatsPeriod: values.skipStatsPeriod || 0,
        tagPeriodIntervalInSec: values.tagPeriodIntervalInSec || 30,
        retain: values.retain || false,
        authType: values.authType || 'normal',
        expiryIntervalInSec: values.expiryIntervalInSec || 120,
        pubOnly: values.pubOnly || false,
        subOnly: values.subOnly || false,
        exceptionEnds: values.exceptionEnds !== false,
        willConfig: values.willConfig || { willFlag: false },
        thingIdStartAt: values.thingIdStartAt || 0,
        thingIdPrefix: values.thingIdPrefix || null,
        protocol: values.protocol || 'tcp',
        username: values.username || '',
        password: values.password || '',
        tenantId: values.tenantId || null,
        keepAliveInSec: values.keepAliveInSec || 120,
        ackTimeoutInSec: values.ackTimeoutInSec || 120,
        reconnectMaxAttempts: values.reconnectMaxAttempts || 10,
        reconnectIntervalInMs: values.reconnectIntervalInMs || 5000,
        connectTimeoutInMs: values.connectTimeoutInMs || 10000,
        maxInflightQueue: values.maxInflightQueue || 200,
        wildcard: values.wildcard || false,
        mqtt5: values.mqtt5 || false,
        isEmptyClientId: values.emptyClientId || false
      };

      await onOk(editingTask?.taskId, taskRequest);
      form.resetFields();
    } catch (error) {
      console.error('Form validation failed:', error);
    }
  };

  // 处理取消
  const handleCancel = () => {
    form.resetFields();
    setCurrentTaskType(TaskTypeValues.CONN);
    setWillEnabled(false);
    setSelectedBrokerGroup('');
    onCancel();
  };

  const tabItems = [
    {
      key: 'basic',
      label: '基本设置',
      children: (
        <>
          {/* 任务基础信息 */}
          <Row gutter={16}>
            <Col span={10}>
              <Form.Item name="taskName" label="任务名称" rules={[{ required: true, message: '请输入任务名称' }]}>
                <Input placeholder="请输入任务名称" />
              </Form.Item>
            </Col>
            <Col span={7}>
              <Form.Item name="taskType" label="任务类型" initialValue={TaskTypeValues.CONN} rules={[{ required: true }]}>
                <Select options={[
                  { label: '连接', value: TaskTypeValues.CONN },
                  { label: '发布/订阅', value: TaskTypeValues.PUBSUB },
                ]} />
              </Form.Item>
            </Col>
            <Col span={7}>
              <Form.Item name="template" label="任务模板" initialValue={TaskTemplateValues.CONN_STANDARD} rules={[{ required: true }]}>
                <Select options={templateOptions.filter(t => t.type === currentTaskType).map(t => ({ label: t.label, value: t.value }))} />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item name="group" label="任务分组">
                <Select
                  placeholder="选择任务分组（可选）"
                  allowClear
                  options={taskGroupSelectOptions}
                  dropdownRender={(menu) => (
                    <>
                      {menu}
                      <Button type="link" icon={<SettingOutlined />} style={{ fontSize: 12 }} onClick={() => navigate('/tasks?tab=groups')}>
                        管理任务分组
                      </Button>
                    </>
                  )}
                />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="protocol" label="协议类型" initialValue="tcp" rules={[{ required: true }]}>
                <Select options={[{ label: 'TCP', value: 'tcp' }]} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="authType" label="认证类型" initialValue="normal">
                <Select
                  onChange={(value) => setAuthType(value)}
                  options={[
                    { label: '普通', value: 'normal' },
                    { label: 'BYOC', value: 'byoc' },
                    { label: 'IoT Core（待实现）', value: 'iotCore', disabled: true },
                  ]}
                />
              </Form.Item>
            </Col>
          </Row>

          {/* 认证详情 */}
          {authType === 'normal' && (
            <Row gutter={16}>
              <Col span={12}>
                <Form.Item name="username" label="用户名">
                  <Input placeholder="用户名（可选）" />
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item name="password" label="密码">
                  <Input.Password placeholder="密码（可选）" />
                </Form.Item>
              </Col>
            </Row>
          )}
          {authType === 'byoc' && (
            <Row gutter={16}>
              <Col span={8}>
                <Form.Item name="tenantId" label="租户 ID" rules={[{ required: true, message: 'BYOC认证需要租户ID' }]}>
                  <Input placeholder="租户ID" />
                </Form.Item>
              </Col>
              <Col span={8}>
                <Form.Item name="thingIdPrefix" label="Thing ID 前缀">
                  <Input placeholder="默认为 demo_" />
                </Form.Item>
              </Col>
              <Col span={8}>
                <Form.Item name="thingIdStartAt" label="Thing ID 起始值" initialValue={0}>
                  <InputNumber min={0} style={{ width: '100%' }} />
                </Form.Item>
              </Col>
            </Row>
          )}

          <Divider plain>Broker 配置</Divider>

          <Row gutter={16}>
            <Col span={10}>
              <Form.Item label="Broker 分组（筛选用）">
                <Select
                  placeholder="选择 Broker 分组（可选）"
                  value={selectedBrokerGroup || undefined}
                  onChange={(value) => {
                    setSelectedBrokerGroup(value ?? '');
                    form.setFieldsValue({ brokers: [] });
                  }}
                  allowClear
                  options={brokerGroupSelectOptions}
                  dropdownRender={(menu) => (
                    <>
                      {menu}
                      <Button type="link" icon={<SettingOutlined />} style={{ fontSize: 12 }} onClick={() => navigate('/mqtt-instances?tab=groups')}>
                        管理 Broker 分组
                      </Button>
                    </>
                  )}
                />
              </Form.Item>
            </Col>
            <Col span={14} style={{ paddingTop: 30 }}>
              <span style={{ color: '#8c8c8c', fontSize: 12 }}>同一任务只能使用同一分组内的 Broker</span>
            </Col>
          </Row>
          <Form.Item
            name="brokers"
            label="选择 Broker"
            rules={[{ required: true, message: '请选择至少一个 Broker' }]}
          >
            <Select
              mode="multiple"
              placeholder="请选择 Broker"
              loading={brokerLoading}
              options={brokers.map(broker => ({
                value: broker.brokerId,
                label: (
                  <span>
                    {broker.name} ({broker.host}:{broker.port})
                    {broker.group && brokerGroupSelectOptions.find(opt => opt.value === broker.group) && (
                      <Tag color="blue" style={{ marginLeft: 8 }}>
                        {brokerGroupSelectOptions.find(opt => opt.value === broker.group)?.label}
                      </Tag>
                    )}
                  </span>
                )
              }))}
            />
          </Form.Item>
        </>
      )
    },
    {
      key: 'stress',
      label: '压测参数',
      children: (
        <>
          <Divider plain>客户端配置</Divider>
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item name="totalClientCount" label="客户端数量" initialValue={100} rules={[{ required: true }]}>
                <InputNumber min={1} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="connectRate" label="连接速率（个/秒）" initialValue={100}>
                <InputNumber min={1} step={0.1} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="disconnectRate" label="断开速率（个/秒）" initialValue={2000}>
                <InputNumber min={0.1} step={0.1} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>

          <Divider plain>时间参数</Divider>
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item name="stressDurationInSec" label="测试时长（秒）" initialValue={60}>
                <InputNumber min={1} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="stageTimeoutInSec" label="阶段超时（秒）" initialValue={30}>
                <InputNumber min={1} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="delayAfterReadyInSec" label="准备后延迟（秒）" initialValue={1}>
                <InputNumber min={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>

          {/* 发布/订阅参数 - 仅 PUBSUB 类型显示 */}
          {currentTaskType === TaskTypeValues.PUBSUB && (
            <>
              <Divider plain>发布/订阅</Divider>
              <Form.Item name="topic" label="主题" rules={[{ required: true, message: '请输入发布/订阅主题' }]}>
                <Input placeholder="例如: /test/topic" />
              </Form.Item>
              <Row gutter={16}>
                <Col span={8}>
                  <Form.Item name="qos" label="QoS 等级" initialValue={MqttQoSValues.AT_MOST_ONCE}>
                    <Select options={[
                      { label: 'QoS 0（最多一次）', value: MqttQoSValues.AT_MOST_ONCE },
                      { label: 'QoS 1（至少一次）', value: MqttQoSValues.AT_LEAST_ONCE },
                      { label: 'QoS 2（恰好一次）', value: MqttQoSValues.EXACTLY_ONCE },
                    ]} />
                  </Form.Item>
                </Col>
                <Col span={8}>
                  <Form.Item name="messageSize" label="消息大小（字节）" initialValue={32} rules={[{ type: 'number', min: 1, max: 65536 }]}>
                    <InputNumber style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
                <Col span={8}>
                  <Form.Item name="pubIntervalInMs" label="发布间隔（毫秒）" initialValue={10000} rules={[{ type: 'number', min: 10, max: 60000 }]}>
                    <InputNumber style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
              </Row>
              <Row gutter={16}>
                <Col span={8}>
                  <Form.Item name="pubOnly" label="仅发布模式" valuePropName="checked" initialValue={false}>
                    <Switch />
                  </Form.Item>
                </Col>
                <Col span={8}>
                  <Form.Item name="subOnly" label="仅订阅模式" valuePropName="checked" initialValue={false}>
                    <Switch />
                  </Form.Item>
                </Col>
              </Row>
            </>
          )}

          <Divider plain>统计配置</Divider>
          <Row gutter={16}>
            <Col span={6}>
              <Form.Item name="fanOut" label="Fan Out" initialValue={1}>
                <InputNumber min={1} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="fanIn" label="Fan In" initialValue={1}>
                <InputNumber min={1} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="skipStatsPeriod" label="跳过统计周期" initialValue={0}>
                <InputNumber min={0} max={100} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="tagPeriodIntervalInSec" label="标签周期（秒）" initialValue={30}>
                <InputNumber min={1} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
        </>
      )
    },
    {
      key: 'advanced',
      label: '高级配置',
      children: (
        <>
          <Divider plain>协议选项</Divider>
          <Row gutter={16}>
            <Col span={6}>
              <Form.Item name="cleanSession" label="Clean Session" valuePropName="checked" initialValue={true}>
                <Switch />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="mqtt5" label="MQTT 5.0" valuePropName="checked" initialValue={false}>
                <Switch />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="autoMultiAddress" label="自动多地址" valuePropName="checked" initialValue={true}>
                <Switch />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="wildcard" label="通配符主题" valuePropName="checked" initialValue={false}>
                <Switch />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={6}>
              <Form.Item name="emptyClientId" label="允许空 ClientID" valuePropName="checked" initialValue={false}>
                <Switch />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="retain" label="Retain 消息" valuePropName="checked" initialValue={false}>
                <Switch />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="fixedTopic" label="固定 Topic" valuePropName="checked" initialValue={false}>
                <Switch />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="exceptionEnds" label="异常时终止任务" valuePropName="checked" initialValue={true}>
                <Switch />
              </Form.Item>
            </Col>
          </Row>

          <Divider plain>连接参数</Divider>
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item name="keepAliveInSec" label="保活时间（秒）" initialValue={120}>
                <InputNumber min={0} max={3600} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="expiryIntervalInSec" label="消息过期时间（秒）" initialValue={120}>
                <InputNumber min={0} max={86400} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="maxInflightQueue" label="最大队列大小" initialValue={200}>
                <InputNumber min={10} max={10000} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>

          <Divider plain>超时与重连</Divider>
          <Row gutter={16}>
            <Col span={6}>
              <Form.Item name="connectTimeoutInMs" label="连接超时（毫秒）" initialValue={10000}>
                <InputNumber min={100} max={60000} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="ackTimeoutInSec" label="ACK 超时（秒）" initialValue={120}>
                <InputNumber min={1} max={3600} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="reconnectMaxAttempts" label="最大重连次数" initialValue={10}>
                <InputNumber min={1} max={100} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="reconnectIntervalInMs" label="重连间隔（毫秒）" initialValue={5000}>
                <InputNumber min={100} max={30000} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>

          <Divider plain>Will 配置</Divider>
          <Row gutter={16}>
            <Col span={6}>
              <Form.Item name={['willConfig', 'willFlag']} label="启用 Will" valuePropName="checked" initialValue={false}>
                <Switch onChange={(checked) => setWillEnabled(checked)} />
              </Form.Item>
            </Col>
          </Row>
          {willEnabled && (
            <>
              <Row gutter={16}>
                <Col span={16}>
                  <Form.Item name={['willConfig', 'willTopic']} label="Will Topic" initialValue="last/{clientId}" rules={[{ required: true, message: '请输入 Will Topic' }]}>
                    <Input placeholder="例如: last/{clientId}" />
                  </Form.Item>
                </Col>
                <Col span={8}>
                  <Form.Item name={['willConfig', 'willQos']} label="Will QoS" initialValue={1}>
                    <Select options={[
                      { label: 'QoS 0', value: 0 },
                      { label: 'QoS 1', value: 1 },
                      { label: 'QoS 2', value: 2 },
                    ]} />
                  </Form.Item>
                </Col>
              </Row>
              <Row gutter={16}>
                <Col span={16}>
                  <Form.Item name={['willConfig', 'willMessage']} label="Will Message" initialValue="last xxxxx" rules={[{ required: true, message: '请输入 Will 消息内容' }]}>
                    <Input placeholder="Will 消息内容" />
                  </Form.Item>
                </Col>
                <Col span={8}>
                  <Form.Item name={['willConfig', 'willRetain']} label="Will Retain" valuePropName="checked" initialValue={false}>
                    <Switch />
                  </Form.Item>
                </Col>
              </Row>
            </>
          )}
        </>
      )
    }
  ];

  return (
    <Modal
      title={editingTask ? '编辑任务' : '添加任务'}
      open={visible}
      onOk={handleOk}
      onCancel={handleCancel}
      width={900}
      styles={{ body: { maxHeight: '72vh', overflowY: 'auto', padding: '8px 24px 16px' } }}
    >
      <Form
        form={form}
        layout="vertical"
        onValuesChange={(changedValues) => {
          if ('taskType' in changedValues) {
            const newType = changedValues.taskType;
            setCurrentTaskType(newType);
            const defaultTemplate = newType === TaskTypeValues.CONN
              ? TaskTemplateValues.CONN_STANDARD
              : TaskTemplateValues.PUBSUB_STANDARD;
            form.setFieldsValue({ template: defaultTemplate });
          }
          if ('willConfig' in changedValues && changedValues.willConfig?.willFlag !== undefined) {
            setWillEnabled(changedValues.willConfig.willFlag);
          }
        }}
      >
        <Tabs defaultActiveKey="basic" size="small" items={tabItems} />
      </Form>
    </Modal>
  );
};

export default TaskEditor;
