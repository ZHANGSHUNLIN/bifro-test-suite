import {createBrowserRouter} from 'react-router-dom';
import MainLayout from '../layouts/MainLayout';
import Home from '../pages/Home';
import TaskManagement from '../pages/TaskManagement/index';
import MqttInstanceManagement from '../pages/MqttInstanceManagement';
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
                    path: 'mqtt-instances',
                    element: <MqttInstanceManagement/>
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