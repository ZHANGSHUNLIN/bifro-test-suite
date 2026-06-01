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

import {describe, expect, it, vi} from 'vitest'
import {render, screen} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import TaskList from '../TaskList'
import {type TaskListItem, TaskStatusValues, TaskTypeValues} from '../../../features/task'

describe('TaskList | component tests', () => {
    const mockTask: TaskListItem = {
        id: '1',
        taskId: 'task1234',
        taskName: 'Test Task',
        taskType: TaskTypeValues.PUBSUB,
        protocol: 'mqtt',
        group: 'group1',
        brokers: [{host: 'localhost', port: 1883, brokerId: 'broker1'}],
        totalClientCount: 1000,
        status: TaskStatusValues.ONGOING,
        createTime: '2024-01-01T00:00:00Z',
    }

    const mockTaskInit: TaskListItem = {
        ...mockTask,
        status: TaskStatusValues.INIT,
    }

    const mockTaskAssigned: TaskListItem = {
        ...mockTask,
        status: TaskStatusValues.ASSIGNED,
    }

    const mockTaskStarting: TaskListItem = {
        ...mockTask,
        status: TaskStatusValues.STARTING,
    }

    const mockTaskStart: TaskListItem = {
        ...mockTask,
        status: TaskStatusValues.START,
    }

    const mockTaskShutting: TaskListItem = {
        ...mockTask,
        status: TaskStatusValues.SHUTTING,
    }

    const groupOptions = [
        {label: 'Group 1', value: 'group1'},
        {label: 'Group 2', value: 'group2'},
    ]

    const defaultProps = {
        tasks: [mockTask],
        groupSelectOptions: groupOptions,
        onViewDetail: vi.fn(),
        onEdit: vi.fn(),
        onDelete: vi.fn().mockResolvedValue(undefined),
        onConfirm: vi.fn().mockResolvedValue(undefined),
        onAssign: vi.fn(),
        onStop: vi.fn().mockResolvedValue(undefined),
        onBatchDelete: vi.fn().mockResolvedValue(undefined),
        onSearch: vi.fn(),
    }

    describe('render tests', () => {
        it('render_withTasks_showsTable', () => {
            render(<TaskList {...defaultProps} />)

            expect(screen.getAllByText('Task Name').length).toBeGreaterThan(0)
            expect(screen.getAllByText('Task Type').length).toBeGreaterThan(0)
            expect(screen.getAllByText('Protocol').length).toBeGreaterThan(0)
            expect(screen.getAllByText('Client Count').length).toBeGreaterThan(0)
            expect(screen.getAllByText('Status').length).toBeGreaterThan(0)
            expect(screen.getAllByText('Actions').length).toBeGreaterThan(0)
        })

        it('render_withTasks_showsTaskData', () => {
            render(<TaskList {...defaultProps} tasks={[mockTask]}/>)

            expect(screen.getByText('Test Task')).toBeInTheDocument()
            expect(screen.getByText('task1234')).toBeInTheDocument()
            expect(screen.getByText('1000')).toBeInTheDocument()
        })

        it('render_withoutTaskGroups_showsEmptyStateAndDisablesCreate', () => {
            render(<TaskList {...defaultProps} tasks={[]} groupSelectOptions={[]} onAdd={vi.fn()}/>)

            expect(screen.getByText('No task groups')).toBeInTheDocument()
            expect(screen.getByRole('button', {name: /create task/i})).toBeDisabled()
        })
    })

    describe('search feature', () => {
        it('typeInTaskNameInput_updatesValue', async () => {
            const user = userEvent.setup()
            render(<TaskList {...defaultProps} />)

            const input = screen.getByPlaceholderText('Search task name')
            await user.type(input, 'My Task')

            expect(input).toHaveValue('My Task')
        })

        it('pressEnterInTaskNameInput_callsOnSearch', async () => {
            const user = userEvent.setup()
            const onSearch = vi.fn()
            render(<TaskList {...defaultProps} onSearch={onSearch}/>)

            const input = screen.getByPlaceholderText('Search task name')
            await user.type(input, 'Test Task{Enter}')

            expect(onSearch).toHaveBeenCalled()
        })

        it('clickSearchButton_callsOnSearch', async () => {
            const user = userEvent.setup()
            const onSearch = vi.fn()
            render(<TaskList {...defaultProps} onSearch={onSearch}/>)

            const searchButtons = screen.getAllByText('Search')
            await user.click(searchButtons[0])

            expect(onSearch).toHaveBeenCalled()
        })
    })

    describe('row actions', () => {
        it('clickCopyName_whenClipboardApiIsUnavailable_usesFallbackCopy', async () => {
            const user = userEvent.setup()
            const originalClipboard = navigator.clipboard
            const execCommand = vi.fn().mockReturnValue(true)
            Object.defineProperty(document, 'execCommand', {
                configurable: true,
                value: execCommand,
            })
            Object.defineProperty(navigator, 'clipboard', {
                configurable: true,
                value: undefined,
            })

            render(<TaskList {...defaultProps} tasks={[mockTask]}/>)

            const copyNameButton = screen.getByLabelText('Copy name')
            await user.click(copyNameButton)

            expect(execCommand).toHaveBeenCalledWith('copy')
            Object.defineProperty(navigator, 'clipboard', {
                configurable: true,
                value: originalClipboard,
            })
        })

        it('clickCopyId_whenClipboardApiIsUnavailable_usesFallbackCopy', async () => {
            const user = userEvent.setup()
            const originalClipboard = navigator.clipboard
            const execCommand = vi.fn().mockReturnValue(true)
            Object.defineProperty(document, 'execCommand', {
                configurable: true,
                value: execCommand,
            })
            Object.defineProperty(navigator, 'clipboard', {
                configurable: true,
                value: undefined,
            })

            render(<TaskList {...defaultProps} tasks={[mockTask]}/>)

            const copyIdButton = screen.getByLabelText('Copy ID')
            await user.click(copyIdButton)

            expect(execCommand).toHaveBeenCalledWith('copy')
            Object.defineProperty(navigator, 'clipboard', {
                configurable: true,
                value: originalClipboard,
            })
        })

        it('clickViewDetail_callsOnViewDetail', async () => {
            const user = userEvent.setup()
            const onViewDetail = vi.fn()
            render(<TaskList {...defaultProps} tasks={[mockTask]} onViewDetail={onViewDetail}/>)

            const detailButtons = screen.getAllByText('Detail')
            await user.click(detailButtons[0])

            expect(onViewDetail).toHaveBeenCalledWith('1', 'task1234')
        })

        it('render_withINITStatus_showsEditAndAssignButtons', () => {
            render(<TaskList {...defaultProps} tasks={[mockTaskInit]}/>)

            expect(screen.getAllByText('Detail').length).toBeGreaterThan(0)
            expect(screen.getAllByText('Edit').length).toBeGreaterThan(0)
            expect(screen.getAllByText('Assign').length).toBeGreaterThan(0)
        })

        it('render_withASSIGNEDStatus_showsReassignAndConfirmButtons', () => {
            render(<TaskList {...defaultProps} tasks={[mockTaskAssigned]}/>)

            expect(screen.getAllByText('Detail').length).toBeGreaterThan(0)
            expect(screen.getAllByText('Reassign').length).toBeGreaterThan(0)
            expect(screen.getAllByText('Confirm').length).toBeGreaterThan(0)
        })

        it('render_withONGOINGStatus_showsStopButton', () => {
            render(<TaskList {...defaultProps} tasks={[mockTask]}/>)

            expect(screen.getAllByText('Detail').length).toBeGreaterThan(0)
            expect(screen.getAllByText('Stop').length).toBeGreaterThan(0)
        })

        it('render_withSTARTINGStatus_showsStopButton', () => {
            render(<TaskList {...defaultProps} tasks={[mockTaskStarting]}/>)

            expect(screen.getAllByText('Detail').length).toBeGreaterThan(0)
            expect(screen.getAllByText('Stop').length).toBeGreaterThan(0)
        })

        it('render_withSTARTStatus_showsStopButton', () => {
            render(<TaskList {...defaultProps} tasks={[mockTaskStart]}/>)

            expect(screen.getAllByText('Detail').length).toBeGreaterThan(0)
            expect(screen.getAllByText('Stop').length).toBeGreaterThan(0)
        })

        it('render_withSHUTTINGStatus_showsDisabledStoppingButton', () => {
            render(<TaskList {...defaultProps} tasks={[mockTaskShutting]}/>)

            const stoppingButtons = screen.getAllByRole('button', {name: 'stop Stopping'})
            expect(stoppingButtons.length).toBeGreaterThan(0)
            stoppingButtons.forEach((button) => expect(button).toBeDisabled())
            expect(screen.queryByText('Delete')).not.toBeInTheDocument()
        })
    })

    describe('row selection', () => {
        it('render_withSelectableStatus_showsCheckbox', () => {
            render(<TaskList {...defaultProps} tasks={[mockTaskInit]}/>)

            const checkboxes = screen.getAllByRole('checkbox')
            expect(checkboxes.length).toBeGreaterThan(0)
        })

        it('render_withNonSelectableStatus_disablesCheckbox', () => {
            render(<TaskList {...defaultProps} tasks={[mockTask]}/>)

            const checkboxes = screen.getAllByRole('checkbox')
            checkboxes.forEach((checkbox) => {
                expect(checkbox).toBeDisabled()
            })
        })
    })

    describe('Broker display', () => {
        it('render_withSingleBroker_showsBrokerInfo', () => {
            render(<TaskList {...defaultProps} tasks={[mockTask]}/>)

            expect(screen.getByText('localhost:1883')).toBeInTheDocument()
        })

        it('render_withNoBrokerName_showsHostPort', () => {
            const taskWithNoBrokerName: TaskListItem = {
                ...mockTask,
                brokers: [{host: 'example.com', port: 1883}],
            }

            render(<TaskList {...defaultProps} tasks={[taskWithNoBrokerName]}/>)

            expect(screen.getByText('example.com:1883')).toBeInTheDocument()
        })
    })
})
