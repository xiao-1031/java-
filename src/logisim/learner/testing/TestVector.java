package logisim.learner.testing;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TestVector {
    private final Map<String, Integer> inputs;
    private final Map<String, Integer> expectedOutputs;
    private final int ticks;
    private final boolean resetBefore;

    public TestVector(Map<String, Integer> inputs, Map<String, Integer> expectedOutputs,
            int ticks, boolean resetBefore) {
        if (ticks < 0) {
            throw new IllegalArgumentException("Tick count cannot be negative");
        }
        this.inputs = Collections.unmodifiableMap(new LinkedHashMap<String, Integer>(inputs));
        this.expectedOutputs = Collections.unmodifiableMap(
                new LinkedHashMap<String, Integer>(expectedOutputs));
        this.ticks = ticks;
        this.resetBefore = resetBefore;
    }

    public Map<String, Integer> getInputs() {
        return inputs;
    }

    public Map<String, Integer> getExpectedOutputs() {
        return expectedOutputs;
    }

    public int getTicks() {
        return ticks;
    }

    public boolean isResetBefore() {
        return resetBefore;
    }

    public boolean hasExpectedOutputs() {
        return !expectedOutputs.isEmpty();
    }

    public TestVector withExpectedOutputs(Map<String, Integer> newExpectedOutputs) {
        return new TestVector(inputs, newExpectedOutputs, ticks, resetBefore);
    }

    public String formatInputs() {
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, Integer> entry : inputs.entrySet()) {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(entry.getKey()).append("=0x")
                    .append(Integer.toHexString(entry.getValue()).toUpperCase());
        }
        return text.toString();
    }
}
