import React from 'react';
import { Card, Row, Col, Statistic, Progress, Table, Typography } from 'antd';
import { ArrowUpOutlined, ArrowDownOutlined, UserOutlined, ShoppingOutlined, DollarOutlined, BarChartOutlined } from '@ant-design/icons';

const { Title } = Typography;

const Dashboard: React.FC = () => {
    // 统计数据
    const statsData = [
        {
            title: '总用户数',
            value: 12345,
            icon: <UserOutlined />,
            color: '#1890ff',
            growth: 12.5
        },
        {
            title: '商品总数',
            value: 4567,
            icon: <ShoppingOutlined />,
            color: '#52c41a',
            growth: 8.3
        },
        {
            title: '总销售额',
            value: 1234567,
            icon: <DollarOutlined />,
            color: '#faad14',
            prefix: '¥',
            growth: -2.1
        },
        {
            title: '订单总数',
            value: 7890,
            icon: <BarChartOutlined />,
            color: '#722ed1',
            growth: 5.6
        }
    ];

    // 表格数据
    const tableData = [
        { key: '1', name: '用户 A', age: 32, address: '地址 A' },
        { key: '2', name: '用户 B', age: 42, address: '地址 B' },
        { key: '3', name: '用户 C', age: 32, address: '地址 C' },
    ];

    const columns = [
        { title: '姓名', dataIndex: 'name', key: 'name' },
        { title: '年龄', dataIndex: 'age', key: 'age' },
        { title: '地址', dataIndex: 'address', key: 'address' },
    ];

    return (
        <div>
            <Title level={2}>仪表盘</Title>

            {/* 统计卡片 */}
            <Row gutter={[16, 16]} style={{ marginTop: 24 }}>
                {statsData.map((stat, index) => (
                    <Col xs={24} sm={12} lg={6} key={index}>
                        <Card>
                            <Statistic
                                title={stat.title}
                                value={stat.value}
                                prefix={stat.icon}
                                valueStyle={{ color: stat.color }}
                                suffix={
                                    <span style={{ fontSize: 14, color: stat.growth > 0 ? '#52c41a' : '#f5222d' }}>
                    {stat.growth > 0 ? <ArrowUpOutlined /> : <ArrowDownOutlined />}
                                        {Math.abs(stat.growth)}%
                  </span>
                                }
                            />
                        </Card>
                    </Col>
                ))}
            </Row>

            {/* 进度统计 */}
            <Row gutter={[16, 16]} style={{ marginTop: 24 }}>
                <Col xs={24} md={12}>
                    <Card title="任务完成进度">
                        <div style={{ marginBottom: 16 }}>
                            <div style={{ marginBottom: 8 }}>用户增长</div>
                            <Progress percent={70} strokeColor="#1890ff" />
                        </div>
                        <div style={{ marginBottom: 16 }}>
                            <div style={{ marginBottom: 8 }}>销售额</div>
                            <Progress percent={85} strokeColor="#52c41a" />
                        </div>
                        <div>
                            <div style={{ marginBottom: 8 }}>客户满意度</div>
                            <Progress percent={92} strokeColor="#722ed1" />
                        </div>
                    </Card>
                </Col>

                <Col xs={24} md={12}>
                    <Card title="最近用户">
                        <Table
                            dataSource={tableData}
                            columns={columns}
                            pagination={false}
                            size="small"
                        />
                    </Card>
                </Col>
            </Row>
        </div>
    );
};

export default Dashboard;