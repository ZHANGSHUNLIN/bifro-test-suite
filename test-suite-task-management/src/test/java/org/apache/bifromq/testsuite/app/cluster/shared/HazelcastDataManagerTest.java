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

package org.apache.bifromq.testsuite.app.cluster.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import org.apache.bifromq.testsuite.app.bean.NodeInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HazelcastDataManagerTest {

    @Mock
    private HazelcastInstance hazelcastInstance;
    @Mock
    private IMap<String, NodeInfo> nodeInfoMap;

    @Test
    void constructorShouldRejectMissingHazelcastInstance() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> new HazelcastDataManager(null));

        assertThat(exception.getMessage()).isEqualTo("hazelcastInstance must not be null");
    }

    @Test
    void mapShouldUseInjectedHazelcastInstanceAndCacheWrapperByAddress() {
        when(hazelcastInstance.getName()).thenReturn("hz-test");
        when(hazelcastInstance.<String, NodeInfo>getMap(ShareDataAddr.CLUSTER_NODE_INFO.getAddr()))
            .thenReturn(nodeInfoMap);

        HazelcastDataManager manager = new HazelcastDataManager(hazelcastInstance);

        HazelcastDataManager.IMapWrapper<String, NodeInfo> first =
            manager.map(ShareDataAddr.CLUSTER_NODE_INFO);
        HazelcastDataManager.IMapWrapper<String, NodeInfo> second =
            manager.map(ShareDataAddr.CLUSTER_NODE_INFO);

        assertThat(first).isSameAs(second);
        verify(hazelcastInstance, times(1)).getMap(ShareDataAddr.CLUSTER_NODE_INFO.getAddr());
    }
}
