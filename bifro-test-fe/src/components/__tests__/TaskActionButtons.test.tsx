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
import {SimpleTaskActions, TaskActionButtons} from '../TaskActionButtons'
import {TaskStatusValues} from '../../features/task'

describe('TaskActionButtons | component tests', () => {
    describe('INIT state', () => {
        it('render_withINITStatus_showsEditAssignDeleteButtons', () => {
            // given
            const onViewDetail = vi.fn()
            const onEdit = vi.fn()
            const onAssign = vi.fn()
            const onDelete = vi.fn()

            // when
            render(
                <TaskActionButtons
                    status={TaskStatusValues.INIT}
                    onViewDetail={onViewDetail}
                    onEdit={onEdit}
                    onAssign={onAssign}
                    onDelete={onDelete}
                />
            )

            // then - Ant Design button with icon: "icon-name button-text"
            expect(screen.getByRole('button', {name: 'eye Detail'})).toBeInTheDocument()
            expect(screen.getByRole('button', {name: 'edit Edit'})).toBeInTheDocument()
            expect(screen.getByRole('button', {name: 'deployment-unit Assign'})).toBeInTheDocument()
            expect(screen.getByRole('button', {name: 'delete Delete'})).toBeInTheDocument()
            expect(screen.queryByRole('button', {name: 'stop Stop'})).not.toBeInTheDocument()
            expect(screen.queryByRole('button', {name: 'check-circle Confirm'})).not.toBeInTheDocument()
        })

        it('clickEdit_callsOnEdit', async () => {
            // given
            const user = userEvent.setup()
            const onEdit = vi.fn()

            render(
                <TaskActionButtons status={TaskStatusValues.INIT} onEdit={onEdit}/>
            )

            // when
            await user.click(screen.getByRole('button', {name: 'edit Edit'}))

            // then
            expect(onEdit).toHaveBeenCalledTimes(1)
        })

        it('clickAssign_callsOnAssign', async () => {
            // given
            const user = userEvent.setup()
            const onAssign = vi.fn()

            render(
                <TaskActionButtons status={TaskStatusValues.INIT} onAssign={onAssign}/>
            )

            // when
            await user.click(screen.getByRole('button', {name: 'deployment-unit Assign'}))

            // then
            expect(onAssign).toHaveBeenCalledTimes(1)
        })
    })

    describe('ASSIGNED state', () => {
        it('render_withASSIGNEDStatus_showsReassignConfirmDeleteButtons', () => {
            // given
            const onViewDetail = vi.fn()
            const onAssign = vi.fn()
            const onConfirm = vi.fn()
            const onDelete = vi.fn()

            // when
            render(
                <TaskActionButtons
                    status={TaskStatusValues.ASSIGNED}
                    onViewDetail={onViewDetail}
                    onAssign={onAssign}
                    onConfirm={onConfirm}
                    onDelete={onDelete}
                />
            )

            // then
            expect(screen.getByRole('button', {name: 'eye Detail'})).toBeInTheDocument()
            expect(screen.getByRole('button', {name: 'deployment-unit Reassign'})).toBeInTheDocument()
            expect(screen.getByRole('button', {name: 'check-circle Confirm'})).toBeInTheDocument()
            expect(screen.getByRole('button', {name: 'delete Delete'})).toBeInTheDocument()
            expect(screen.queryByRole('button', {name: 'edit Edit'})).not.toBeInTheDocument()
            expect(screen.queryByRole('button', {name: 'stop Stop'})).not.toBeInTheDocument()
        })

        it('clickConfirm_callsOnConfirm', async () => {
            // given
            const user = userEvent.setup()
            const onConfirm = vi.fn()

            render(
                <TaskActionButtons
                    status={TaskStatusValues.ASSIGNED}
                    onConfirm={onConfirm}
                />
            )

            // when
            await user.click(screen.getByRole('button', {name: 'check-circle Confirm'}))

            // then
            expect(onConfirm).toHaveBeenCalledTimes(1)
        })
    })

    describe('running state', () => {
        it.each([
            TaskStatusValues.START,
            TaskStatusValues.CONNECTING,
            TaskStatusValues.INIT_PUB_CLIENT,
            TaskStatusValues.INIT_SUB_CLIENT,
            TaskStatusValues.STARTING,
            TaskStatusValues.ONGOING,
        ])('render_with%sStatus_showsStopAndDetailButtons', (status) => {
            // given
            const onViewDetail = vi.fn()
            const onStop = vi.fn()

            // when
            render(
                <TaskActionButtons
                    status={status}
                    onViewDetail={onViewDetail}
                    onStop={onStop}
                />
            )

            // then
            expect(screen.getByRole('button', {name: 'eye Detail'})).toBeInTheDocument()
            expect(screen.getByRole('button', {name: 'stop Stop'})).toBeInTheDocument()
            expect(screen.queryByRole('button', {name: 'edit Edit'})).not.toBeInTheDocument()
            expect(screen.queryByRole('button', {name: 'deployment-unit Assign'})).not.toBeInTheDocument()
            expect(screen.queryByRole('button', {name: 'delete Delete'})).not.toBeInTheDocument()
        })

        it('clickStop_callsOnStop', async () => {
            // given
            const user = userEvent.setup()
            const onStop = vi.fn()

            render(
                <TaskActionButtons
                    status={TaskStatusValues.ONGOING}
                    onStop={onStop}
                />
            )

            // when
            await user.click(screen.getByRole('button', {name: 'stop Stop'}))

            // then
            expect(onStop).toHaveBeenCalledTimes(1)
        })

        it('render_withSHUTTINGStatus_showsDisabledStoppingButton', () => {
            // given
            const onStop = vi.fn()

            // when
            render(
                <TaskActionButtons
                    status={TaskStatusValues.SHUTTING}
                    onStop={onStop}
                    onDelete={vi.fn()}
                />
            )

            // then
            expect(screen.getByRole('button', {name: 'stop Stopping'})).toBeDisabled()
            expect(screen.queryByRole('button', {name: 'delete Delete'})).not.toBeInTheDocument()
        })
    })

    describe('STOPPED/SHUTDOWN state', () => {
        it.each([TaskStatusValues.STOPPED, TaskStatusValues.SHUTDOWN])(
            'render_with%sStatus_showsDetailDeleteButtons',
            (status) => {
                // given
                const onViewDetail = vi.fn()
                const onDelete = vi.fn()

                // when
                render(
                    <TaskActionButtons
                        status={status}
                        onViewDetail={onViewDetail}
                        onDelete={onDelete}
                    />
                )

                // then
                expect(screen.getByRole('button', {name: 'eye Detail'})).toBeInTheDocument()
                expect(screen.getByRole('button', {name: 'delete Delete'})).toBeInTheDocument()
                expect(screen.queryByRole('button', {name: 'edit Edit'})).not.toBeInTheDocument()
                expect(screen.queryByRole('button', {name: 'deployment-unit Assign'})).not.toBeInTheDocument()
                expect(screen.queryByRole('button', {name: 'stop Stop'})).not.toBeInTheDocument()
            }
        )
    })

    describe('conditional rendering', () => {
        it('render_withoutViewDetail_hidesDetailButton', () => {
            // when
            render(
                <TaskActionButtons status={TaskStatusValues.INIT}/>
            )

            // then
            expect(screen.queryByRole('button', {name: 'eye Detail'})).not.toBeInTheDocument()
        })

        it('render_withoutEdit_hidesEditButton', () => {
            // when
            render(
                <TaskActionButtons status={TaskStatusValues.INIT} onEdit={undefined}/>
            )

            // then
            expect(screen.queryByRole('button', {name: 'edit Edit'})).not.toBeInTheDocument()
        })

        it('render_withoutDelete_hidesDeleteButton', () => {
            // when
            render(
                <TaskActionButtons status={TaskStatusValues.STOPPED} onDelete={undefined}/>
            )

            // then
            expect(screen.queryByRole('button', {name: 'delete Delete'})).not.toBeInTheDocument()
        })
    })

    describe('SimpleTaskActions', () => {
        it('render_withAllProps_showsAllButtons', () => {
            // given
            const onEdit = vi.fn()
            const onConfirm = vi.fn()
            const onAssign = vi.fn()
            const onDelete = vi.fn()

            // when
            render(
                <SimpleTaskActions
                    onEdit={onEdit}
                    onConfirm={onConfirm}
                    onAssign={onAssign}
                    onDelete={onDelete}
                />
            )

            // then
            expect(screen.getByRole('button', {name: 'edit Edit'})).toBeInTheDocument()
            expect(screen.getByRole('button', {name: 'check-circle Confirm'})).toBeInTheDocument()
            expect(screen.getByRole('button', {name: 'deployment-unit Assign'})).toBeInTheDocument()
            expect(screen.getByRole('button', {name: 'delete Delete'})).toBeInTheDocument()
        })

        it('render_withOnlyEdit_showsOnlyEditButton', () => {
            // given
            const onEdit = vi.fn()

            // when
            render(<SimpleTaskActions onEdit={onEdit}/>)

            // then
            expect(screen.getByRole('button', {name: 'edit Edit'})).toBeInTheDocument()
            expect(screen.queryByRole('button', {name: 'check-circle Confirm'})).not.toBeInTheDocument()
            expect(screen.queryByRole('button', {name: 'deployment-unit Assign'})).not.toBeInTheDocument()
            expect(screen.queryByRole('button', {name: 'delete Delete'})).not.toBeInTheDocument()
        })
    })
})
