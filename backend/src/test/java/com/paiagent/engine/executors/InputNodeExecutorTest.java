package com.paiagent.engine.executors;

import com.paiagent.engine.ExecutionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InputNodeExecutorTest {

    private InputNodeExecutor executor;
    private ExecutionContext ctx;

    @BeforeEach
    void setUp() {
        executor = new InputNodeExecutor();
        ctx = new ExecutionContext();
    }

    @Test
    void passesUserInputUnderDefaultKey() {
        Map<String, Object> data = Map.of("_userInput", "hello");
        Map<String, Object> output = executor.execute(data, ctx);
        assertEquals("hello", output.get("output"));
    }

    @Test
    void passesUserInputUnderCustomVariableName() {
        Map<String, Object> data = Map.of(
            "_userInput", "hello",
            "variableName", "topic"
        );
        Map<String, Object> output = executor.execute(data, ctx);
        assertEquals("hello", output.get("topic"));
    }

    @Test
    void emptyInputWhenNotRequired() {
        Map<String, Object> data = Map.of("_userInput", "");
        Map<String, Object> output = executor.execute(data, ctx);
        assertEquals("", output.get("output"));
    }

    @Test
    void throwsWhenRequiredAndEmpty() {
        Map<String, Object> data = Map.of(
            "_userInput", "",
            "required", true
        );
        assertThrows(IllegalArgumentException.class, () -> executor.execute(data, ctx));
    }

    @Test
    void passesWhenRequiredAndNotEmpty() {
        Map<String, Object> data = Map.of(
            "_userInput", "valid input",
            "required", true
        );
        Map<String, Object> output = executor.execute(data, ctx);
        assertEquals("valid input", output.get("output"));
    }
}
