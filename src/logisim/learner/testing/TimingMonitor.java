package logisim.learner.testing;

import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.std.wiring.Pin;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import logisim.learner.debug.SignalDescriptor;
import logisim.learner.debug.SignalSample;
import logisim.learner.debug.WaveformAnomalyDetector;

public final class TimingMonitor implements SimulationDriver.StepListener {
    private static final int MAX_ANOMALIES = 8;

    private final CircuitState state;
    private final List<TestSignal> outputs;
    private final LinkedHashMap<SignalDescriptor, List<SignalSample>> histories
            = new LinkedHashMap<SignalDescriptor, List<SignalSample>>();
    private final List<String> anomalies = new ArrayList<String>();
    private final Set<String> reported = new HashSet<String>();
    private long sequence;
    private SignalDescriptor anomalySignal;

    public TimingMonitor(CircuitState state, List<TestSignal> outputs) {
        this.state = state;
        this.outputs = outputs;
        for (SignalDescriptor signal : SignalDescriptor.scan(state)) {
            if (shouldObserve(signal)) {
                histories.put(signal, new ArrayList<SignalSample>());
            }
        }
        capture(0, 0, true);
    }

    @Override
    public void afterStep(long tick, int propagationStep) {
        capture(tick, propagationStep, false);
    }

    public boolean hasAnomalies() {
        return !anomalies.isEmpty();
    }

    public SignalDescriptor getAnomalySignal() {
        return anomalySignal;
    }

    public String getProblematicSignal() {
        if (anomalySignal == null || !anomalySignal.getInstancePath().isEmpty()) {
            return null;
        }
        for (TestSignal output : outputs) {
            if (output.getPin() == anomalySignal.getComponent()) {
                return output.getName();
            }
        }
        return null;
    }

    public String getReason() {
        StringBuilder text = new StringBuilder();
        int count = Math.min(3, anomalies.size());
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                text.append("\n");
            }
            text.append(anomalies.get(i));
        }
        if (anomalies.size() > count) {
            text.append("\n另有 ").append(anomalies.size() - count).append(" 项时序异常。");
        }
        return text.toString();
    }

    static boolean shouldObserve(SignalDescriptor signal) {
        Component component = signal.getComponent();
        if (component.getFactory() instanceof Pin) {
            return Boolean.TRUE.equals(component.getAttributeSet().getValue(Pin.ATTR_TYPE));
        }
        String factoryName = component.getFactory().getName();
        if ("Probe".equals(factoryName)) {
            return true;
        }
        String className = component.getFactory().getClass().getName();
        return className.startsWith("com.cburch.logisim.std.memory.")
                && !className.endsWith(".Ram") && !className.endsWith(".Rom");
    }

    private void capture(long tick, int propagationStep, boolean force) {
        Map<SignalDescriptor, Value> values
                = new LinkedHashMap<SignalDescriptor, Value>();
        boolean changed = force;
        for (SignalDescriptor signal : histories.keySet()) {
            Value value = signal.readValue(state);
            value = value == null ? Value.NIL : value;
            values.put(signal, value);
            List<SignalSample> history = histories.get(signal);
            if (!history.isEmpty()
                    && !Objects.equals(history.get(history.size() - 1).getValue(), value)) {
                changed = true;
            }
        }
        if (!changed) {
            return;
        }
        sequence++;
        for (Map.Entry<SignalDescriptor, Value> entry : values.entrySet()) {
            SignalDescriptor signal = entry.getKey();
            Value value = entry.getValue();
            List<SignalSample> history = histories.get(signal);
            history.add(new SignalSample(sequence, tick, value));
            if (!force) {
                checkValue(signal, value, tick, propagationStep);
                if (history.size() >= 3) {
                    checkPulse(signal, history, tick, propagationStep);
                }
            }
        }
    }

    private void checkValue(SignalDescriptor signal, Value value,
            long tick, int propagationStep) {
        String kind = value.isErrorValue() ? "E" : value.isFullyDefined() ? null : "x";
        if (kind == null || !reported.add(signal.getName() + ":value:" + kind)) {
            return;
        }
        addAnomaly(signal, "信号 " + signal.getName() + " 在 tick " + tick
                + "、传播步 " + propagationStep + " 出现 " + kind + " 值（"
                + value.toDisplayString() + "）。");
    }

    private void checkPulse(SignalDescriptor signal, List<SignalSample> history,
            long tick, int propagationStep) {
        int middle = history.size() - 2;
        if (!WaveformAnomalyDetector.isShortPulse(history, middle)
                || !reported.add(signal.getName() + ":pulse:" + tick)) {
            return;
        }
        SignalSample sample = history.get(middle);
        addAnomaly(signal, "信号 " + signal.getName() + " 在 tick " + tick
                + " 出现同拍短脉冲：稳定前的瞬态值为 " + sample.getValue().toDisplayString()
                + "（发现于传播步 " + propagationStep + "）。");
    }

    private void addAnomaly(SignalDescriptor signal, String message) {
        if (anomalySignal == null) {
            anomalySignal = signal;
        }
        if (anomalies.size() < MAX_ANOMALIES) {
            anomalies.add(message);
        }
    }
}
