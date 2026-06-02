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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class StageResultTest {

    @Nested
    class SuccessTests {

        @Test
        void testSuccess_withoutMessage_shouldCreateSuccessfulResult() {
            

            
            StageResult result = StageResult.success();

            
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getMessage()).isNull();
            assertThat(result.getError()).isNull();
        }

        @Test
        void testSuccess_withMessage_shouldCreateSuccessfulResultWithMessage() {
            
            String message = "Stage completed successfully";

            
            StageResult result = StageResult.success(message);

            
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getMessage()).isEqualTo(message);
            assertThat(result.getError()).isNull();
        }

        @Test
        void testSuccess_withEmptyMessage_shouldCreateSuccessfulResult() {
            
            String message = "";

            
            StageResult result = StageResult.success(message);

            
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getMessage()).isEmpty();
            assertThat(result.getError()).isNull();
        }
    }

    @Nested
    class FailureTests {

        @Test
        void testFailure_withMessage_shouldCreateFailedResult() {
            
            String message = "Stage failed";

            
            StageResult result = StageResult.failure(message);

            
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).isEqualTo(message);
            assertThat(result.getError()).isNull();
        }

        @Test
        void testFailure_withThrowable_shouldCreateFailedResult() {
            
            Throwable error = new RuntimeException("Something went wrong");

            
            StageResult result = StageResult.failure(error);

            
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).isEqualTo(error.getMessage());
            assertThat(result.getError()).isEqualTo(error);
        }

        @Test
        void testFailure_withMessageAndThrowable_shouldCreateFailedResult() {
            
            String message = "Custom failure message";
            Throwable error = new IllegalStateException("Invalid state");

            
            StageResult result = StageResult.failure(message, error);

            
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).isEqualTo(message);
            assertThat(result.getError()).isEqualTo(error);
        }

        @Test
        void testFailure_withNullMessage_shouldCreateFailedResult() {
            
            Throwable error = new RuntimeException("Error");

            
            StageResult result = StageResult.failure((String) null);

            
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).isNull();
            assertThat(result.getError()).isNull();
        }
    }

    @Nested
    class WithDataTests {

        @Test
        void testWithData_shouldAddDataToResult() {
            
            StageResult result = StageResult.success();
            String key = "clientId";
            String value = "client-123";

            
            StageResult modifiedResult = result.withData(key, value);

            
            assertThat(modifiedResult).isSameAs(result);
            assertThat(result.getData().get(key)).isEqualTo(value);
        }

        @Test
        void testWithData_withMultipleKeys_shouldAddAllData() {
            
            StageResult result = StageResult.success();

            
            result.withData("key1", "value1")
                .withData("key2", "value2")
                .withData("key3", "value3");

            
            assertThat(result.getData()).hasSize(3);
            assertThat(result.getData().get("key1")).isEqualTo("value1");
            assertThat(result.getData().get("key2")).isEqualTo("value2");
            assertThat(result.getData().get("key3")).isEqualTo("value3");
        }

        @Test
        void testWithData_shouldOverwriteExistingKey() {
            
            StageResult result = StageResult.success();
            result.withData("key", "oldValue");

            
            result.withData("key", "newValue");

            
            assertThat(result.getData().get("key")).isEqualTo("newValue");
            assertThat(result.getData()).hasSize(1);
        }

        @Test
        void testGetData_shouldReturnMap() {
            
            StageResult result = StageResult.success();

            
            Map<String, Object> data = result.getData();

            
            assertThat(data).isNotNull();
            assertThat(data).isEmpty();
        }
    }

    @Nested
    class ToStringTests {

        @Test
        void testToString_forSuccessWithoutMessage() {
            
            StageResult result = StageResult.success();

            
            String str = result.toString();

            
            assertThat(str).isEqualTo("StageResult{success=true, message='null', error=null}");
        }

        @Test
        void testToString_forSuccessWithMessage() {
            
            StageResult result = StageResult.success("All good");

            
            String str = result.toString();

            
            assertThat(str).isEqualTo("StageResult{success=true, message='All good', error=null}");
        }

        @Test
        void testToString_forFailureWithoutError() {
            
            StageResult result = StageResult.failure("Failed");

            
            String str = result.toString();

            
            assertThat(str).isEqualTo("StageResult{success=false, message='Failed', error=null}");
        }

        @Test
        void testToString_forFailureWithError() {
            
            Throwable error = new RuntimeException("Error");
            StageResult result = StageResult.failure(error);

            
            String str = result.toString();

            
            assertThat(str).isEqualTo("StageResult{success=false, message='Error', error=RuntimeException}");
        }

        @Test
        void testToString_forFailureWithMessageAndError() {
            
            Throwable error = new IllegalStateException("State error");
            StageResult result = StageResult.failure("Custom message", error);

            
            String str = result.toString();

            
            assertThat(str).isEqualTo(
                "StageResult{success=false, message='Custom message', error=IllegalStateException}");
        }

        @Test
        void testToString_forDifferentErrorTypes() {
            
            StageResult nullPointerResult = StageResult.failure(new NullPointerException());
            StageResult illegalArgResult = StageResult.failure(new IllegalArgumentException());
            StageResult runtimeResult = StageResult.failure(new RuntimeException());

            
            String nullPointerStr = nullPointerResult.toString();
            String illegalArgStr = illegalArgResult.toString();
            String runtimeStr = runtimeResult.toString();

            
            assertThat(nullPointerStr).contains("NullPointerException");
            assertThat(illegalArgStr).contains("IllegalArgumentException");
            assertThat(runtimeStr).contains("RuntimeException");
        }
    }

    @Nested
    class ChainingTests {

        @Test
        void testChainingWithData_shouldWork() {
            
            StageResult result = StageResult.success();

            
            StageResult finalResult = result
                .withData("key1", "value1")
                .withData("key2", "value2")
                .withData("key3", "value3");

            
            assertThat(finalResult).isSameAs(result);
            assertThat(result.getData()).hasSize(3);
        }
    }

    @Nested
    class ComparisonTests {

        @Test
        void testSuccessVsFailure_shouldHaveDifferentSuccessStatus() {
            
            StageResult success = StageResult.success();
            StageResult failure = StageResult.failure("Failed");

            
            assertThat(success.isSuccess()).isTrue();
            assertThat(failure.isSuccess()).isFalse();
        }

        @Test
        void testSuccessWithMessageVsWithout_shouldBothBeSuccess() {
            
            StageResult successWithMsg = StageResult.success("With message");
            StageResult successWithoutMsg = StageResult.success();

            
            assertThat(successWithMsg.isSuccess()).isTrue();
            assertThat(successWithoutMsg.isSuccess()).isTrue();
        }
    }

    @Nested
    class ErrorTypeTests {

        @Test
        void testFailure_withDifferentExceptionTypes() {
            
            Exception[] exceptions = {
                new NullPointerException(),
                new IllegalArgumentException(),
                new IllegalStateException(),
                new RuntimeException(),
                new Exception("Generic exception")
            };

            
            for (Exception e : exceptions) {
                StageResult result = StageResult.failure(e);
                assertThat(result.isSuccess()).isFalse();
                assertThat(result.getError()).isSameAs(e);
            }
        }

        @Test
        void testFailure_withNullError() {
            
            StageResult result = StageResult.failure((String) null);

            
            Throwable error = result.getError();

            
            assertThat(error).isNull();
        }
    }
}
