import React, { useState, useEffect } from 'react';
import { Modal, Form, Row, Col, Select, Card, Switch, Input, InputNumber, Button, Space } from 'antd';
import { MinusCircleOutlined, PlusOutlined } from '@ant-design/icons';
import { useTaskData } from './hooks';
import { formFieldGroups } from './form-schema';
import { TaskTypeValues, MqttQoSValues } from '../../types/task';
import type { TaskListItem, TaskRequest } from '../../types/task';

const { Option } = Select;

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
  const [form] = Form.useForm();
  const [currentTaskType, setCurrentTaskType] = useState<string>(TaskTypeValues.CONN);
  const [willEnabled, setWillEnabled] = useState<boolean>(false);
  const [authType, setAuthType] = useState<string>('normal');
  const { loadTaskDetail, loadBrokers } = useTaskData();
  const [brokers, setBrokers] = useState<any[]>([]);
  const [brokerLoading, setBrokerLoading] = useState(false);

  // 初始化表单
  useEffect(() => {
    if (visible) {
      if (editingTask) {
        loadTaskData(editingTask.id);
      } else {
        form.resetFields();
        setCurrentTaskType(TaskTypeValues.CONN);
        setWillEnabled(false);
        loadBrokerList();
      }
    }
  }, [visible, editingTask]);

  // 加载任务详情数据
  const loadTaskData = async (id: string) => {
    try {
      const detail = await loadTaskDetail(id);
      if (detail.mainTask) {
        form.setFieldsValue({
          ...detail.mainTask,
          taskName: detail.taskName,
          brokers: detail.brokers?.map((broker: { host: string; port: number }) => `${broker.host}:${broker.port}`) || [],
          willConfig: detail.mainTask.willConfig || { willFlag: false }
        });
        setCurrentTaskType(detail.mainTask.taskType);
        setWillEnabled(detail.mainTask.willConfig?.willFlag || false);
        setAuthType(detail.mainTask.authType || 'normal');
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
      setBrokers(brokerData);
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

      // 处理 brokers 数据结构
      const brokers = values.brokers || ['10.99.48.7:8883'];
      const brokerItems = brokers.map((b: string) => {
        const [host, port] = b.split(':');
        return { host, port: parseInt(port) };
      });

      // 构建任务请求
      const taskRequest: TaskRequest = {
        taskName: values.taskName,
        taskType: values.taskType,
        autoMultiAddress: values.autoMultiAddress || false,
        brokers: brokerItems,
        cleanSession: values.cleanSession !== false,
        totalClientCount: values.totalClientCount || 100,
        connectRate: values.connectRate || 1,
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
        lifecycleActions: values.lifecycleActions || [],
        lifecycleActionsConfig: values.lifecycleActionsConfig || {},
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
        isWildcard: values.wildcard || false,
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
    onCancel();
  };

  // 渲染表单字段
  const renderFormField = (field: any) => {
    const commonProps = {
      placeholder: field.placeholder,
      style: { width: '100%' },
      onChange: field.onChange, // 添加onChange回调支持
    };

    switch (field.type) {
      case 'input':
        return <Input {...commonProps} />;
      case 'inputNumber':
        return <InputNumber {...commonProps} min={field.min} max={field.max} step={field.step} />;
      case 'select':
        return (
          <Select {...commonProps} options={field.options}>
            {field.options?.map((opt: any) => (
              <Option key={opt.value} value={opt.value}>{opt.label}</Option>
            ))}
          </Select>
        );
      case 'switch':
        return <Switch onChange={field.onChange} />; // Switch组件单独的onChange属性
      case 'password':
        return <Input.Password {...commonProps} />;
      case 'textarea':
        return <Input.TextArea {...commonProps} rows={field.rows || 2} />;
      default:
        return <Input {...commonProps} />;
    }
  };

  // 渲染表单组
  const renderFormGroup = (title: string, fields: any[], grid = true) => (
    <Card title={title} size="small" style={{ marginBottom: 16 }}>
      {grid ? (
        <Row gutter={16}>
          {fields.map((field, index) => (
            <Col span={8} key={index}>
              <Form.Item
                name={field.name}
                label={field.label}
                rules={field.rules || []}
                valuePropName={field.type === 'switch' ? 'checked' : undefined}
                initialValue={field.initialValue}
              >
                {renderFormField(field)}
              </Form.Item>
            </Col>
          ))}
        </Row>
      ) : (
        fields.map((field, index) => (
          <Form.Item
            key={index}
            name={field.name}
            label={field.label}
            rules={field.rules || []}
            valuePropName={field.type === 'switch' ? 'checked' : undefined}
            initialValue={field.initialValue}
          >
            {renderFormField(field)}
          </Form.Item>
        ))
      )}
    </Card>
  );

  return (
    <Modal
      title={editingTask ? '编辑任务' : '添加任务'}
      open={visible}
      onOk={handleOk}
      onCancel={handleCancel}
      width={800}
    >
      <Form form={form} layout="vertical">
        {/* 基础配置 */}
        {renderFormGroup('基础配置', formFieldGroups.basic.map(field => {
          if (field.name === 'taskType') {
            return {
              ...field,
              onChange: (value: string) => {
                setCurrentTaskType(value);
                // 确保表单值更新后重新渲染
                form.setFieldsValue({ taskType: value });
              }
            };
          }
          return field;
        }))}

        {/* Broker选择 */}
        <Card title="服务器配置" size="small" style={{ marginBottom: 16 }}>
          <Form.Item
            name="brokers"
            label="选择Broker"
            initialValue={editingTask?.brokers?.map((b: any) => `${b.host}:${b.port}`) || []}
            rules={[{ required: true, message: '请选择至少一个Broker' }]}
          >
            <Select
              mode="multiple"
              placeholder="请选择Broker"
              loading={brokerLoading}
              options={brokers.map(broker => ({
                value: `${broker.host}:${broker.port}`,
                label: `${broker.name} (${broker.host}:${broker.port})`
              }))}
            />
          </Form.Item>
        </Card>


        {/* 发布/订阅相关配置 - 仅在 PUBSUB 类型时显示 */}
        {currentTaskType === TaskTypeValues.PUBSUB && (
            <>
              <Card title="发布/订阅配置" size="small" style={{marginBottom: 16}}>
                <Form.Item
                    name="topic"
                    label="主题"
                    rules={[{required: true, message: '请输入发布/订阅主题'}]}
                >
                  <Input placeholder="例如: /test/topic"/>
                </Form.Item>

                <Row gutter={16}>
                  <Col span={8}>
                    <Form.Item
                        name="qos"
                        label="QoS等级"
                        initialValue={MqttQoSValues.AT_MOST_ONCE}
                    >
                      <Select>
                        <Option value={MqttQoSValues.AT_MOST_ONCE}>QoS 0 (最多一次)</Option>
                        <Option value={MqttQoSValues.AT_LEAST_ONCE}>QoS 1 (至少一次)</Option>
                        <Option value={MqttQoSValues.EXACTLY_ONCE}>QoS 2 (恰好一次)</Option>
                      </Select>
                    </Form.Item>
                  </Col>
                  <Col span={8}>
                    <Form.Item
                        name="messageSize"
                        label="消息大小(字节)"
                        initialValue={32}
                        rules={[{
                          type: 'number',
                          min: 1,
                          max: 65536
                        }]}
                    >
                      <InputNumber type="number" style={{width: '100%'}}/>
                    </Form.Item>
                  </Col>
                  <Col span={8}>
                    <Form.Item
                        name="pubIntervalInMs"
                        label="发布间隔(毫秒)"
                        initialValue={10000}
                        rules={[{
                          type: 'number',
                          min: 10,
                          max: 60000
                        }]}
                    >
                      <InputNumber type="number" style={{width: '100%'}}/>
                    </Form.Item>
                  </Col>
                </Row>

              </Card>

              <Card title="Pub/Sub行为配置" size="small" style={{marginBottom: 16}}>
                <Row gutter={16}>
                  <Col span={12}>
                    <Form.Item
                        name="pubOnly"
                        label="仅发布模式"
                        valuePropName="checked"
                        initialValue={false}
                    >
                      <Switch/>
                    </Form.Item>
                  </Col>
                  <Col span={12}>
                    <Form.Item
                        name="subOnly"
                        label="仅订阅模式"
                        valuePropName="checked"
                        initialValue={false}
                    >
                      <Switch/>
                    </Form.Item>
                  </Col>
                </Row>
              </Card>
            </>
        )}


        {/* 认证配置 */}
        <Card title="认证配置" size="small" style={{ marginBottom: 16 }}>
          <Form.Item
            name="authType"
            label="认证类型"
            initialValue="normal"
          >
            <Select onChange={(value) => setAuthType(value)}>
              <Option value="normal">普通</Option>
              <Option value="byoc">BYOC</Option>
              <Option value="iotCore" disabled>IoT Core (待实现)</Option>
            </Select>
          </Form.Item>

          {authType === 'normal' && (
            <Row gutter={16}>
              <Col span={12}>
                <Form.Item
                  name="username"
                  label="用户名"
                >
                  <Input placeholder="用户名（可选）" />
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item
                  name="password"
                  label="密码"
                >
                  <Input.Password placeholder="密码（可选）" />
                </Form.Item>
              </Col>
            </Row>
          )}

          {authType === 'byoc' && (
            <Row gutter={16}>
              <Col span={12}>
                <Form.Item
                  name="tenantId"
                  label="租户ID"
                  rules={[{ required: true, message: 'BYOC认证需要租户ID' }]}
                >
                  <Input placeholder="租户ID" />
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item
                  name="thingIdPrefix"
                  label="Thing ID前缀"
                >
                  <Input placeholder="默认为demo_" />
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item
                  name="thingIdStartAt"
                  label="Thing ID起始值"
                  initialValue={0}
                >
                  <InputNumber min={0} style={{ width: '100%' }} />
                </Form.Item>
              </Col>
            </Row>
          )}
        </Card>

        {/* 连接通用配置 */}
        {renderFormGroup('连接通用配置', formFieldGroups.connection)}

        {/* 协议配置 */}
        {renderFormGroup('协议配置', formFieldGroups.protocol)}

        {/* 客户端ID配置 - 仅显示空Client ID允许选项，其他字段已在认证配置中显示 */}
        {renderFormGroup('客户端ID配置', formFieldGroups.clientId)}

        {/* 连接超时与重连配置 */}
        {renderFormGroup('连接超时与重连配置', formFieldGroups.timeout)}

        {/* 生命周期动作 */}
        <Card title="生命周期动作" size="small" style={{ marginBottom: 16 }}>
          <Form.List name="lifecycleActions">
            {(fields, { add, remove }) => (
              <>
                {fields.map(({ key, name, ...restField }) => (
                  <Space key={key} style={{ display: 'flex', marginBottom: 8 }} align="baseline">
                    <Form.Item
                      {...restField}
                      name={[name, 'action']}
                      rules={[{ required: true, message: '请选择动作' }]}
                    >
                      <Select placeholder="选择动作" style={{ width: 160 }}>
                        <Option value="CONNECT">连接</Option>
                        <Option value="DISCONNECT">断开</Option>
                        <Option value="PUBLISH">发布</Option>
                        <Option value="SUBSCRIBE">订阅</Option>
                      </Select>
                    </Form.Item>
                    <Form.Item
                      {...restField}
                      name={[name, 'delayInMs']}
                      rules={[{ required: true, message: '请输入延迟时间' }]}
                    >
                      <InputNumber type="number" style={{ width: '100%' }} placeholder="延迟(ms)" />
                    </Form.Item>
                    <MinusCircleOutlined onClick={() => remove(name)} />
                  </Space>
                ))}
                <Form.Item>
                  <Button type="dashed" onClick={() => add()} block icon={<PlusOutlined />}>
                    添加动作
                  </Button>
                </Form.Item>
              </>
            )}
          </Form.List>
        </Card>

        {/* Will配置 */}
        <Card title="Will配置" size="small" style={{ marginBottom: 16 }}>
          <Form.Item
            name={['willConfig', 'willFlag']}
            label="启用Will"
            valuePropName="checked"
            initialValue={false}
          >
            <Switch onChange={(checked) => setWillEnabled(checked)} />
          </Form.Item>
          {willEnabled && (
            <>
              <Row gutter={16}>
                <Col span={16}>
                  <Form.Item
                    name={['willConfig', 'willTopic']}
                    label="Will Topic"
                    initialValue="last/{clientId}"
                    rules={[{ required: true, message: '请输入Will Topic' }]}
                  >
                    <Input placeholder="例如: last/{clientId}" />
                  </Form.Item>
                </Col>
              </Row>
              <Row gutter={16}>
                <Col span={16}>
                  <Form.Item
                    name={['willConfig', 'willMessage']}
                    label="Will Message"
                    initialValue="last xxxxx"
                    rules={[{ required: true, message: '请输入Will消息内容' }]}
                  >
                    <Input placeholder="Will消息内容" />
                  </Form.Item>
                </Col>
                <Col span={8}>
                  <Form.Item
                    name={['willConfig', 'willQos']}
                    label="Will QoS"
                    initialValue={1}
                  >
                    <Select>
                      <Option value={0}>QoS 0</Option>
                      <Option value={1}>QoS 1</Option>
                      <Option value={2}>QoS 2</Option>
                    </Select>
                  </Form.Item>
                </Col>
              </Row>
              <Form.Item
                name={['willConfig', 'willRetain']}
                label="Will Retain"
                valuePropName="checked"
                initialValue={false}
              >
                <Switch />
              </Form.Item>
            </>
          )}
        </Card>
      </Form>
    </Modal>
  );
};

export default TaskEditor;