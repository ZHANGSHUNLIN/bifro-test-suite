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

package org.apache.bifromq.testsuite.pipeline;

import java.util.concurrent.CompletableFuture;

public interface PipelineStage<C extends PipelineContext<?, ?>> {

    String getName();

    default String getLabel() {
        return getName();
    }
    
    default boolean isVisible() {
        return true;
    }

    default Object getTriggerEvent() {
        return null;
    }

    CompletableFuture<StageResult> execute(C context);

    default boolean canExecute(C context) {
        return !context.isCancelled();
    }
    
    default void onBefore(C context) {
    }
    
    default void onAfter(C context, StageResult result) {
    }
    
    default void onError(C context, Throwable error) {
    }

    default void onTimeout(C context, String timeoutMessage) {
    }

    default void onCancelled(C context) {
    }
    
    default CompletableFuture<Void> cancel(C context) {
        return CompletableFuture.completedFuture(null);
    }
}
