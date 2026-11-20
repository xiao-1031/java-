package logisim.learner.testing;

import com.cburch.logisim.data.Value;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import logisim.learner.debug.SignalDescriptor;

public final class TestResult {
    public enum Status {
        CLEAN, ANOMALY, FAILED, ERROR
    }

    private final int index;
    private final TestVector vector;
    private final Map<String, Value> actualOutputs;
    private final Status status;
    private final String reason;
    private final String problematicSignal;
    private final SignalDescriptor anomalySignal;

    public TestResult(int index, TestVector vector, Map<String, Value> actualOutputs,
            Status status, String reason, String problematicSignal) {
        this(index, vector, actualOutputs, status, reason, problematicSignal, null);
    }

    public TestResult(int index, TestVector vector, Map<String, Value> actualOutputs,
            Status status, String reason, String problematicSignal,
            SignalDescriptor anomalySignal) {
        this.index = index;
        this.vector = vector;
        this.actualOutputs = Collections.unmodifiableMap(
                new LinkedHashMap<String, Value>(actualOutputs));
        this.status = status;
        this.reason = reason;
        this.problematicSignal = problematicSignal;
        this.anomalySignal = anomalySignal;
    }

    public int getIndex() {
        return index;
    }

    public TestVector getVector() {
        return vector;
    }

    public Map<String, Value> getActualOutputs() {
        return actualOutputs;
    }

    public Status getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public String getProblematicSignal() {
        return problematicSignal;
    }

    public SignalDescriptor getAnomalySignal() {
        return anomalySignal;
    }
}
