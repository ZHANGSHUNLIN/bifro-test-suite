import {createBrowserRouter} from 'react-router-dom';
import MainLayout from '../layouts/MainLayout';
import Home from '../pages/Home';
import TaskManagement from '../pages/TaskManagement/index';
import MqttBrokerManagement from '../pages/MqttBrokerManagement';
import ClusterManagement from '../pages/ClusterManagement';

const router = createBrowserRouter([
        {
            path: '/',
            element: <MainLayout/>,
            children: [
                {
                    index: true,
                    element: <Home/>
                },
                {
                    path: 'tasks',
                    element: <TaskManagement/>
                },
                {
                    path: 'mqtt-brokers',
                    element: <MqttBrokerManagement/>
                },
                {
                    path: 'cluster',
                    element: <ClusterManagement/>
                }
            ]
        }
    ],
    {
        basename: '/admin'
    });

export default router;