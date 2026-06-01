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

import {describe, expect, it} from 'vitest'
import {render, screen} from '@testing-library/react'
import PipelineSteps from '../PipelineSteps'

describe('PipelineSteps | component tests', () => {
    describe('snapshot mode', () => {
        it('render_snapshots_hidesInvisibleStages', () => {
            render(
                <PipelineSteps
                    snapshots={[
                        {key: 'visible-stage', label: 'Visible Stage', status: 'RUNNING', visible: true},
                        {key: 'hidden-stage', label: 'Hidden Stage', status: 'DONE', visible: false},
                    ]}
                />
            )

            expect(screen.getByText('Visible Stage')).toBeInTheDocument()
            expect(screen.queryByText('Hidden Stage')).not.toBeInTheDocument()
        })

        it('render_snapshots_allInvisible_returnsNull', () => {
            const {container} = render(
                <PipelineSteps
                    snapshots={[
                        {key: 'hidden-stage-1', label: 'Hidden Stage 1', status: 'DONE', visible: false},
                        {key: 'hidden-stage-2', label: 'Hidden Stage 2', status: 'SKIPPED', visible: false},
                    ]}
                />
            )

            expect(container.firstChild).toBeNull()
        })
    })

    describe('CONN type', () => {
        it('render_CONN_type_showsCorrectSteps', () => {
            render(<PipelineSteps taskType="CONN" currentStage="ONGOING"/>)
            expect(screen.getByText('Initialize Clients')).toBeInTheDocument()
            expect(screen.getByText('Running')).toBeInTheDocument()
            expect(screen.getByText('Cleaning Up')).toBeInTheDocument()
            expect(screen.getByText('Done')).toBeInTheDocument()
        })
    })

    describe('PUBSUB type', () => {
        it('render_PUBSUB_type_showsCorrectSteps', () => {
            render(<PipelineSteps taskType="PUBSUB" currentStage="ONGOING"/>)
            expect(screen.getByText('Initialize Publishers')).toBeInTheDocument()
            expect(screen.getByText('Initialize Subscribers')).toBeInTheDocument()
            expect(screen.getByText('Establish Publish Connections')).toBeInTheDocument()
            expect(screen.getByText('Establish Subscribe Connections')).toBeInTheDocument()
            expect(screen.getByText('Start Subscribing')).toBeInTheDocument()
            expect(screen.getByText('Wait for Ready')).toBeInTheDocument()
            expect(screen.getByText('Start Pub/Sub')).toBeInTheDocument()
            expect(screen.getByText('Running')).toBeInTheDocument()
            expect(screen.getByText('Cleaning Up')).toBeInTheDocument()
            expect(screen.getByText('Done')).toBeInTheDocument()
        })

        it('render_PUBSUB_pubOnlyTemplate_hidesSubscribeStages', () => {
            render(<PipelineSteps taskType="PUBSUB" currentStage="ONGOING" template="PUBSUB_PUB_ONLY"/>)

            expect(screen.getByText('Initialize Publishers')).toBeInTheDocument()
            expect(screen.getByText('Establish Publish Connections')).toBeInTheDocument()
            expect(screen.queryByText('Initialize Subscribers')).not.toBeInTheDocument()
            expect(screen.queryByText('Establish Subscribe Connections')).not.toBeInTheDocument()
            expect(screen.queryByText('Start Subscribing')).not.toBeInTheDocument()
        })

        it('render_PUBSUB_subOnlyTemplate_hidesPublishStages', () => {
            render(<PipelineSteps taskType="PUBSUB" currentStage="SUBSCRIBE_CLIENT" template="PUBSUB_SUB_ONLY"/>)

            expect(screen.getByText('Initialize Subscribers')).toBeInTheDocument()
            expect(screen.getByText('Establish Subscribe Connections')).toBeInTheDocument()
            expect(screen.getByText('Start Subscribing')).toBeInTheDocument()
            expect(screen.queryByText('Initialize Publishers')).not.toBeInTheDocument()
            expect(screen.queryByText('Establish Publish Connections')).not.toBeInTheDocument()
        })
    })

    describe('terminal state', () => {
        it('render_withSHUTDOWN_showsAllStepLabels', () => {
            render(<PipelineSteps taskType="CONN" currentStage="SHUTDOWN"/>)
            // All step labels still shown in terminal state
            expect(screen.getByText('Initialize Clients')).toBeInTheDocument()
            expect(screen.getByText('Done')).toBeInTheDocument()
        })

        it('render_withSTOPPED_showsAllStepLabels', () => {
            render(<PipelineSteps taskType="CONN" currentStage="STOPPED"/>)
            expect(screen.getByText('Initialize Clients')).toBeInTheDocument()
            expect(screen.getByText('Done')).toBeInTheDocument()
        })
    })
})
