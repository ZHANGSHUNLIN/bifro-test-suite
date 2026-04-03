

package com.baidu.iot.test.suite.stats.recoder;

import com.baidu.iot.test.suite.stats.recorder.StatsRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for StatsRecorder.
 */
class StatsRecorderTest {

    private StatsRecorder recorder;

    @BeforeEach
    void setUp() {
        recorder = new StatsRecorder();
    }

    @Test
    void testUpdateSuccess_shouldUpdateCountAndLatency() {
        // given
        long latency = 100;
        TimeUnit timeUnit = TimeUnit.MILLISECONDS;

        // when
        recorder.updateSuccess(latency, timeUnit);
        recorder.updateSuccess(200, timeUnit);
        recorder.updateSuccess(300, timeUnit);

        // then
        assertThat(recorder.getTotalSuccessCount()).isEqualTo(3);
        assertThat(recorder.getTotalLatency()).isEqualTo(600);
    }

    @Test
    void testUpdateSuccess_shouldCalculateMeanLatency() {
        // given
        TimeUnit timeUnit = TimeUnit.MILLISECONDS;

        // when
        recorder.updateSuccess(100, timeUnit);
        recorder.updateSuccess(200, timeUnit);
        recorder.updateSuccess(300, timeUnit);

        // then
        assertThat(recorder.getMeanLatency()).isEqualTo(200.0);
    }

    @Test
    void testGenResult_withNoRecords_shouldReturnEmptyResult() {
        // given
        Duration duration = Duration.ofSeconds(10);

        // when
        var result = recorder.genResult(duration);

        // then
        assertThat(result.getCount()).isZero();
        assertThat(result.getMeanLatency()).isZero();
        assertThat(result.getQps()).isZero();
    }

    @Test
    void testGenResult_withRecords_shouldCalculateCorrectly() {
        // given
        Duration duration = Duration.ofSeconds(10);
        TimeUnit timeUnit = TimeUnit.MILLISECONDS;

        // when
        for (int i = 0; i < 100; i++) {
            recorder.updateSuccess(100 + i, timeUnit);
        }
        var result = recorder.genResult(duration);

        // then
        assertThat(result.getCount()).isEqualTo(100);
        assertThat(result.getMeanLatency()).isEqualTo(149.5);
        assertThat(result.getQps()).isEqualTo(10.0);
        assertThat(result.getMinLatency()).isEqualTo(100);
        assertThat(result.getMaxLatency()).isEqualTo(199);
    }

    @Test
    void testGenResult_shouldCalculateQpsCorrectly() {
        // given
        Duration duration = Duration.ofMillis(1000);  // 1 second
        TimeUnit timeUnit = TimeUnit.MILLISECONDS;

        // when
        for (int i = 0; i < 1000; i++) {
            recorder.updateSuccess(50, timeUnit);
        }
        var result = recorder.genResult(duration);

        // then
        assertThat(result.getQps()).isEqualTo(1000.0);
    }

    @Test
    void testGenResult_shouldCalculatePercentiles() {
        // given
        Duration duration = Duration.ofSeconds(10);
        TimeUnit timeUnit = TimeUnit.MILLISECONDS;

        // when - add 1000 values from 1 to 1000
        for (int i = 1; i <= 1000; i++) {
            recorder.updateSuccess(i, timeUnit);
        }
        var result = recorder.genResult(duration);

        // then - approximate percentile values
        assertThat(result.getMedianLatency()).isBetween(500.0, 501.0);
        assertThat(result.getP95Latency()).isBetween(950.0, 951.0);
        assertThat(result.getP99Latency()).isBetween(990.0, 991.0);
    }

    @Test
    void testReset_shouldClearAllData() {
        // given
        TimeUnit timeUnit = TimeUnit.MILLISECONDS;
        recorder.updateSuccess(100, timeUnit);
        recorder.updateSuccess(200, timeUnit);
        assertThat(recorder.getTotalSuccessCount()).isEqualTo(2);

        // when
        recorder.reset();

        // then
        assertThat(recorder.getTotalSuccessCount()).isZero();
        assertThat(recorder.getTotalLatency()).isZero();
        assertThat(recorder.getMeanLatency()).isZero();
        assertThat(recorder.getSumOfStandardDeviation()).isZero();
        assertThat(recorder.getLatencyFrequencyMap()).isEmpty();
    }

    @Test
    void testUpdateSuccess_shouldBuildLatencyFrequencyMap() {
        // given
        TimeUnit timeUnit = TimeUnit.MILLISECONDS;

        // when
        recorder.updateSuccess(100, timeUnit);
        recorder.updateSuccess(100, timeUnit);  // duplicate latency
        recorder.updateSuccess(200, timeUnit);
        recorder.updateSuccess(300, timeUnit);
        recorder.updateSuccess(300, timeUnit);
        recorder.updateSuccess(300, timeUnit);  // 3 times

        // then
        assertThat(recorder.getLatencyFrequencyMap().get(100L)).isEqualTo(2);
        assertThat(recorder.getLatencyFrequencyMap().get(200L)).isEqualTo(1);
        assertThat(recorder.getLatencyFrequencyMap().get(300L)).isEqualTo(3);
    }

    @Test
    void testGenResult_shouldCalculateStandardDeviation() {
        // given
        Duration duration = Duration.ofSeconds(10);
        TimeUnit timeUnit = TimeUnit.MILLISECONDS;

        // when - values: 90, 100, 110 (mean = 100)
        // Standard deviation calculated using Welford's algorithm
        recorder.updateSuccess(90, timeUnit);
        recorder.updateSuccess(100, timeUnit);
        recorder.updateSuccess(110, timeUnit);
        var result = recorder.genResult(duration);

        // then - The incremental algorithm gives ~8.16 for 90,100,110
        assertThat(result.getStandardDeviation()).isEqualTo(8.16);
    }

    @Test
    void testGenResult_shouldGenerateBucketCounts() {
        // given
        Duration duration = Duration.ofSeconds(10);
        TimeUnit timeUnit = TimeUnit.MILLISECONDS;

        // when - add values in different buckets
        // Buckets are: 20ms, 50ms, 100ms, 200ms, 500ms, 1000ms, 2000ms, 5000ms, 10000ms, 60000ms
        for (int i = 0; i < 5; i++) {
            recorder.updateSuccess(10 + i, timeUnit);  // 10-14ms - first bucket (20ms)
        }
        for (int i = 0; i < 3; i++) {
            recorder.updateSuccess(25 + i, timeUnit);  // 25-27ms - second bucket (50ms)
        }
        for (int i = 0; i < 2; i++) {
            recorder.updateSuccess(60 + i, timeUnit);  // 60-61ms - third bucket (100ms)
        }
        var result = recorder.genResult(duration);

        // then
        assertThat(result.getBucketCounts()).isNotNull();
        assertThat(result.getBucketCounts().length).isGreaterThan(0);
        // Check bucket counts - values are distributed according to bucket boundaries
        assertThat(result.getBucketCounts()[0][1]).isEqualTo(5.0);
        assertThat(result.getBucketCounts()[1][1]).isEqualTo(3.0);
        assertThat(result.getBucketCounts()[2][1]).isEqualTo(2.0);
    }

    @Test
    void testGenResult_timestamp_shouldBeSet() {
        // given
        Duration duration = Duration.ofSeconds(10);
        TimeUnit timeUnit = TimeUnit.MILLISECONDS;
        long beforeTimestamp = System.currentTimeMillis();
        recorder.updateSuccess(100, timeUnit);

        // when
        var result = recorder.genResult(duration);
        long afterTimestamp = System.currentTimeMillis();

        // then
        assertThat(result.getTimestamp()).isBetween(beforeTimestamp, afterTimestamp);
    }

    @Test
    void testUpdateSuccess_withSingleValue_shouldHaveZeroStdDev() {
        // given
        Duration duration = Duration.ofSeconds(10);
        TimeUnit timeUnit = TimeUnit.MILLISECONDS;

        // when
        recorder.updateSuccess(100, timeUnit);
        var result = recorder.genResult(duration);

        // then
        assertThat(result.getStandardDeviation()).isEqualTo(0.0);
    }

    @Test
    void testUpdateSuccess_mixedLatencies_shouldCalculateCorrectly() {
        // given
        TimeUnit timeUnit = TimeUnit.MILLISECONDS;

        // when - mix of small and large latencies
        recorder.updateSuccess(1, timeUnit);
        recorder.updateSuccess(1000, timeUnit);
        recorder.updateSuccess(500, timeUnit);
        recorder.updateSuccess(250, timeUnit);
        recorder.updateSuccess(750, timeUnit);

        // then
        assertThat(recorder.getTotalSuccessCount()).isEqualTo(5);
        assertThat(recorder.getTotalLatency()).isEqualTo(2501);
        assertThat(recorder.getMeanLatency()).isEqualTo(500.2);
    }
}
