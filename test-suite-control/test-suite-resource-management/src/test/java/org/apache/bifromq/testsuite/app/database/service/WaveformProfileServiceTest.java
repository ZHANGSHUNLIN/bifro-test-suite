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

package org.apache.bifromq.testsuite.app.database.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import org.apache.bifromq.testsuite.app.database.pojo.WaveformProfile;
import org.apache.bifromq.testsuite.app.database.repository.WaveformProfileRepository;
import org.apache.bifromq.testsuite.web.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class WaveformProfileServiceTest {

    @Mock
    private WaveformProfileRepository repository;

    @InjectMocks
    private WaveformProfileService service;

    @BeforeEach
    void setUp() {
        
        try {
            java.lang.reflect.Field f = WaveformProfileService.class.getDeclaredField("objectMapper");
            f.setAccessible(true);
            f.set(service, new ObjectMapper());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    

    @Test
    void list_noKeyword_callsFindAll() {
        WaveformProfile p = WaveformProfile.builder().id("1").name("test").build();
        when(repository.findAll()).thenReturn(Flux.just(p));

        List<WaveformProfile> result = service.list(null).collectList().block();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("1");
    }

    @Test
    void list_withKeyword_callsFindByName() {
        WaveformProfile p = WaveformProfile.builder().id("1").name("peak").build();
        when(repository.findByNameContainingIgnoreCase("peak")).thenReturn(Flux.just(p));

        List<WaveformProfile> result = service.list("peak").collectList().block();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("peak");
    }

    

    @Test
    void getById_found_returnsProfile() {
        WaveformProfile p = WaveformProfile.builder().id("abc").name("test").build();
        when(repository.findById("abc")).thenReturn(Mono.just(p));

        WaveformProfile result = service.getById("abc").block();

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("abc");
    }

    @Test
    void getById_notFound_throwsApiException() {
        when(repository.findById("xyz")).thenReturn(Mono.empty());

        assertThatThrownBy(() -> service.getById("xyz").block())
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("error.profile.notFound");
    }

    

    @Test
    void parseGrafanaJson_oldFormat_parsesDataPoints() {
        String json = "[{\"target\":\"qps\",\"datapoints\":[[100.0,0],[200.0,5000],[150.0,10000]]}]";
        List<long[]> points = service.parseGrafanaJson(json);

        assertThat(points).hasSize(3);
        assertThat(points.get(0)).containsExactly(0L, 100L);
        assertThat(points.get(1)).containsExactly(5000L, 200L);
        assertThat(points.get(2)).containsExactly(10000L, 150L);
    }

    @Test
    void parseGrafanaJson_oldFormat_sortsAndConvertsToRelative() {
        
        String json = "[{\"target\":\"qps\",\"datapoints\":[[50.0,10000],[100.0,0],[200.0,5000]]}]";
        List<long[]> points = service.parseGrafanaJson(json);

        assertThat(points).hasSize(3);
        assertThat(points.get(0)[0]).isEqualTo(0L);    
        assertThat(points.get(1)[0]).isEqualTo(5000L);
        assertThat(points.get(2)[0]).isEqualTo(10000L);
    }

    

    @Test
    void parseGrafanaJson_newFormat_parsesFrames() {
        String json = "{\"results\":{\"A\":{\"frames\":[{\"data\":{\"values\":[[0,5000,10000],[100,200,150]]}}]}}}";
        List<long[]> points = service.parseGrafanaJson(json);

        assertThat(points).hasSize(3);
        assertThat(points.get(0)).containsExactly(0L, 100L);
        assertThat(points.get(1)).containsExactly(5000L, 200L);
        assertThat(points.get(2)).containsExactly(10000L, 150L);
    }

    

    @Test
    void parseGrafanaJson_unsupportedFormat_throwsApiException() {
        assertThatThrownBy(() -> service.parseGrafanaJson("{\"unknown\":\"data\"}"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("error.profile.notFound");
    }

    @Test
    void parseGrafanaJson_invalidJson_throwsApiException() {
        assertThatThrownBy(() -> service.parseGrafanaJson("not json {{{"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("error.profile.notFound");
    }

    

    @Test
    void importFromGrafana_duplicateName_throwsApiException() {
        when(repository.existsByName("existing")).thenReturn(Mono.just(true));

        String json = "[{\"target\":\"q\",\"datapoints\":[[100.0,0],[200.0,5000]]}]";
        assertThatThrownBy(() -> service.importFromGrafana(json, "existing", "", "user").block())
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("error.profile.nameExists");
    }

    

    @Test
    void getPreviewData_fewerThan500Points_returnsAllPoints() {
        List<long[]> dataPoints = List.of(new long[] {0L, 100L}, new long[] {1000L, 200L});
        WaveformProfile p = WaveformProfile.builder()
            .id("x")
            .name("test")
            .dataPoints(dataPoints)
            .build();
        when(repository.findById("x")).thenReturn(Mono.just(p));

        List<long[]> result = service.getPreviewData("x").block();

        assertThat(result).hasSize(2);
    }
}
