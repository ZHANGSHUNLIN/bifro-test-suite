
package com.baidu.iot.test.suite.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for StageResult.
 */
class StageResultTest {

    @Nested
    class SuccessTests {

        @Test
        void testSuccess_withoutMessage_shouldCreateSuccessfulResult() {
            // given - none

            // when
            StageResult result = StageResult.success();

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getMessage()).isNull();
            assertThat(result.getError()).isNull();
        }

        @Test
        void testSuccess_withMessage_shouldCreateSuccessfulResultWithMessage() {
            // given
            String message = "Stage completed successfully";

            // when
            StageResult result = StageResult.success(message);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getMessage()).isEqualTo(message);
            assertThat(result.getError()).isNull();
        }

        @Test
        void testSuccess_withEmptyMessage_shouldCreateSuccessfulResult() {
            // given
            String message = "";

            // when
            StageResult result = StageResult.success(message);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getMessage()).isEmpty();
            assertThat(result.getError()).isNull();
        }
    }

    @Nested
    class FailureTests {

        @Test
        void testFailure_withMessage_shouldCreateFailedResult() {
            // given
            String message = "Stage failed";

            // when
            StageResult result = StageResult.failure(message);

            // then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).isEqualTo(message);
            assertThat(result.getError()).isNull();
        }

        @Test
        void testFailure_withThrowable_shouldCreateFailedResult() {
            // given
            Throwable error = new RuntimeException("Something went wrong");

            // when
            StageResult result = StageResult.failure(error);

            // then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).isEqualTo(error.getMessage());
            assertThat(result.getError()).isEqualTo(error);
        }

        @Test
        void testFailure_withMessageAndThrowable_shouldCreateFailedResult() {
            // given
            String message = "Custom failure message";
            Throwable error = new IllegalStateException("Invalid state");

            // when
            StageResult result = StageResult.failure(message, error);

            // then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).isEqualTo(message);
            assertThat(result.getError()).isEqualTo(error);
        }

        @Test
        void testFailure_withNullMessage_shouldCreateFailedResult() {
            // given
            Throwable error = new RuntimeException("Error");

            // when
            StageResult result = StageResult.failure((String) null);

            // then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).isNull();
            assertThat(result.getError()).isNull();
        }
    }

    @Nested
    class WithDataTests {

        @Test
        void testWithData_shouldAddDataToResult() {
            // given
            StageResult result = StageResult.success();
            String key = "clientId";
            String value = "client-123";

            // when
            StageResult modifiedResult = result.withData(key, value);

            // then
            assertThat(modifiedResult).isSameAs(result);
            assertThat(result.getData().get(key)).isEqualTo(value);
        }

        @Test
        void testWithData_withMultipleKeys_shouldAddAllData() {
            // given
            StageResult result = StageResult.success();

            // when
            result.withData("key1", "value1")
                    .withData("key2", "value2")
                    .withData("key3", "value3");

            // then
            assertThat(result.getData()).hasSize(3);
            assertThat(result.getData().get("key1")).isEqualTo("value1");
            assertThat(result.getData().get("key2")).isEqualTo("value2");
            assertThat(result.getData().get("key3")).isEqualTo("value3");
        }

        @Test
        void testWithData_shouldOverwriteExistingKey() {
            // given
            StageResult result = StageResult.success();
            result.withData("key", "oldValue");

            // when
            result.withData("key", "newValue");

            // then
            assertThat(result.getData().get("key")).isEqualTo("newValue");
            assertThat(result.getData()).hasSize(1);
        }

        @Test
        void testGetData_shouldReturnMap() {
            // given
            StageResult result = StageResult.success();

            // when
            Map<String, Object> data = result.getData();

            // then
            assertThat(data).isNotNull();
            assertThat(data).isEmpty();
        }
    }

    @Nested
    class ToStringTests {

        @Test
        void testToString_forSuccessWithoutMessage() {
            // given
            StageResult result = StageResult.success();

            // when
            String str = result.toString();

            // then
            assertThat(str).isEqualTo("StageResult{success=true, message='null', error=null}");
        }

        @Test
        void testToString_forSuccessWithMessage() {
            // given
            StageResult result = StageResult.success("All good");

            // when
            String str = result.toString();

            // then
            assertThat(str).isEqualTo("StageResult{success=true, message='All good', error=null}");
        }

        @Test
        void testToString_forFailureWithoutError() {
            // given
            StageResult result = StageResult.failure("Failed");

            // when
            String str = result.toString();

            // then
            assertThat(str).isEqualTo("StageResult{success=false, message='Failed', error=null}");
        }

        @Test
        void testToString_forFailureWithError() {
            // given
            Throwable error = new RuntimeException("Error");
            StageResult result = StageResult.failure(error);

            // when
            String str = result.toString();

            // then
            assertThat(str).isEqualTo("StageResult{success=false, message='Error', error=RuntimeException}");
        }

        @Test
        void testToString_forFailureWithMessageAndError() {
            // given
            Throwable error = new IllegalStateException("State error");
            StageResult result = StageResult.failure("Custom message", error);

            // when
            String str = result.toString();

            // then
            assertThat(str).isEqualTo("StageResult{success=false, message='Custom message', error=IllegalStateException}");
        }

        @Test
        void testToString_forDifferentErrorTypes() {
            // given
            StageResult nullPointerResult = StageResult.failure(new NullPointerException());
            StageResult illegalArgResult = StageResult.failure(new IllegalArgumentException());
            StageResult runtimeResult = StageResult.failure(new RuntimeException());

            // when
            String nullPointerStr = nullPointerResult.toString();
            String illegalArgStr = illegalArgResult.toString();
            String runtimeStr = runtimeResult.toString();

            // then
            assertThat(nullPointerStr).contains("NullPointerException");
            assertThat(illegalArgStr).contains("IllegalArgumentException");
            assertThat(runtimeStr).contains("RuntimeException");
        }
    }

    @Nested
    class ChainingTests {

        @Test
        void testChainingWithData_shouldWork() {
            // given
            StageResult result = StageResult.success();

            // when
            StageResult finalResult = result
                    .withData("key1", "value1")
                    .withData("key2", "value2")
                    .withData("key3", "value3");

            // then
            assertThat(finalResult).isSameAs(result);
            assertThat(result.getData()).hasSize(3);
        }
    }

    @Nested
    class ComparisonTests {

        @Test
        void testSuccessVsFailure_shouldHaveDifferentSuccessStatus() {
            // given
            StageResult success = StageResult.success();
            StageResult failure = StageResult.failure("Failed");

            // when/then
            assertThat(success.isSuccess()).isTrue();
            assertThat(failure.isSuccess()).isFalse();
        }

        @Test
        void testSuccessWithMessageVsWithout_shouldBothBeSuccess() {
            // given
            StageResult successWithMsg = StageResult.success("With message");
            StageResult successWithoutMsg = StageResult.success();

            // when/then
            assertThat(successWithMsg.isSuccess()).isTrue();
            assertThat(successWithoutMsg.isSuccess()).isTrue();
        }
    }

    @Nested
    class ErrorTypeTests {

        @Test
        void testFailure_withDifferentExceptionTypes() {
            // given
            Exception[] exceptions = {
                    new NullPointerException(),
                    new IllegalArgumentException(),
                    new IllegalStateException(),
                    new RuntimeException(),
                    new Exception("Generic exception")
            };

            // when/then
            for (Exception e : exceptions) {
                StageResult result = StageResult.failure(e);
                assertThat(result.isSuccess()).isFalse();
                assertThat(result.getError()).isSameAs(e);
            }
        }

        @Test
        void testFailure_withNullError() {
            // given
            StageResult result = StageResult.failure((String) null);

            // when
            Throwable error = result.getError();

            // then
            assertThat(error).isNull();
        }
    }
}
