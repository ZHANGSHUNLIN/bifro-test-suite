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

package org.apache.bifromq.testsuite.app.controller.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import org.apache.bifromq.testsuite.audit.application.AuditLogService;
import org.apache.bifromq.testsuite.web.ApiResponse;
import org.apache.bifromq.testsuite.web.PageInfo;
import org.apache.bifromq.testsuite.app.database.pojo.WaveformProfile;
import org.apache.bifromq.testsuite.app.database.service.WaveformProfileService;
import org.apache.bifromq.testsuite.web.ApiException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class WaveformProfileControllerTest {

    @Mock
    private WaveformProfileService profileService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private ServerWebExchange exchange;

    @InjectMocks
    private WaveformProfileController controller;

    

    @Test
    void list_noKeyword_returnsAllProfiles() {
        WaveformProfile p = WaveformProfile.builder().id("1").name("test").build();
        when(profileService.list(isNull(), isNull(), any())).thenReturn(Flux.just(p));
        when(profileService.count(isNull(), isNull())).thenReturn(Mono.just(1L));

        ApiResponse<PageInfo<WaveformProfile>> resp = controller.list(null, null, 1, 20).block();

        assertThat(resp).isNotNull();
        assertThat(resp.isSuccess()).isTrue();
        assertThat(resp.getData().getContent()).hasSize(1);
        assertThat(resp.getData().getContent().get(0).getId()).isEqualTo("1");
        assertThat(resp.getData().getTotalElements()).isEqualTo(1);
    }

    @Test
    void list_withKeyword_filtersProfiles() {
        WaveformProfile p = WaveformProfile.builder().id("2").name("peak_load").build();
        when(profileService.list(eq("peak"), isNull(), any())).thenReturn(Flux.just(p));
        when(profileService.count("peak", null)).thenReturn(Mono.just(1L));

        ApiResponse<PageInfo<WaveformProfile>> resp = controller.list("peak", null, 1, 20).block();

        assertThat(resp).isNotNull();
        assertThat(resp.isSuccess()).isTrue();
        assertThat(resp.getData().getContent().get(0).getName()).isEqualTo("peak_load");
    }

    

    @Test
    void getById_found_returnsProfile() {
        WaveformProfile p = WaveformProfile.builder().id("abc").name("test").build();
        when(profileService.getById("abc")).thenReturn(Mono.just(p));

        ApiResponse<WaveformProfile> resp = controller.getById("abc").block();

        assertThat(resp).isNotNull();
        assertThat(resp.isSuccess()).isTrue();
        assertThat(resp.getData().getId()).isEqualTo("abc");
    }

    @Test
    void getById_notFound_throwsApiException() {
        when(profileService.getById("xyz")).thenReturn(Mono.error(new ApiException("not found")));

        assertThatThrownBy(() -> controller.getById("xyz").block())
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("not found");
    }

    

    @Test
    void getPreview_returnsDownsampledPoints() {
        List<long[]> preview = List.of(new long[] {0L, 100L}, new long[] {5000L, 200L});
        when(profileService.getPreviewData("id1")).thenReturn(Mono.just(preview));

        ApiResponse<List<long[]>> resp = controller.getPreview("id1").block();

        assertThat(resp).isNotNull();
        assertThat(resp.isSuccess()).isTrue();
        assertThat(resp.getData()).hasSize(2);
    }

    

    @Test
    void deleteById_success_returnsSuccessResponse() {
        when(profileService.deleteById("id1")).thenReturn(Mono.empty());
        when(auditLogService.record(eq(exchange), any(), any(), eq("id1"), eq(true), any()))
            .thenReturn(Mono.empty());

        ApiResponse<Void> resp = controller.deleteById("id1", exchange).block();

        assertThat(resp).isNotNull();
        assertThat(resp.isSuccess()).isTrue();
    }

    @Test
    void deleteById_notFound_throwsApiException() {
        when(profileService.deleteById("missing")).thenReturn(Mono.error(new ApiException("not found")));

        assertThatThrownBy(() -> controller.deleteById("missing", exchange).block())
            .isInstanceOf(ApiException.class);
    }
}
