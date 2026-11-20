package logisim.learner.testing;

import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.LearnerTimingAccess;
import com.cburch.logisim.data.Value;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SimulationDriver {
    public interface StepListener {
        void afterStep(long tick, int propagationStep);
    }

    private static final int MAX_PROPAGATION_STEPS = 1000;

    private final CircuitState state;
    private long tick;

    public SimulationDriver(CircuitState state) {
        this.state = state;
        state.getPropagator();
    }

    public CircuitState getState() {
        return state;
    }

    public long getTick() {
        return tick;
    }

    public void reset() {
        LearnerTimingAccess.reset(state);
    }

    public void applyInputs(List<TestSignal> signals, Map<String, Integer> values)
            throws TestExecutionException, InterruptedException {
        applyInputs(signals, values, null);
    }

    public void applyInputs(List<TestSignal> signals, Map<String, Integer> values,
            StepListener listener) throws TestExecutionException, InterruptedException {
        for (TestSignal signal : signals) {
            Integer value = values.get(signal.getName());
            if (value == null) {
                throw new TestExecutionException("用例缺少输入 " + signal.getName());
            }
            signal.write(state, value.intValue());
        }
        advance(listener);
    }

    public void tick(int count) throws TestExecutionException, InterruptedException {
        tick(count, null);
    }

    public void tick(int count, StepListener listener)
            throws TestExecutionException, InterruptedException {
        for (int i = 0; i < count; i++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }
            tick++;
            state.getPropagator().tick();
            advance(listener);
        }
    }

    public Map<String, Value> readOutputs(List<TestSignal> signals) {
        Map<String, Value> values = new LinkedHashMap<String, Value>();
        for (TestSignal signal : signals) {
            values.put(signal.getName(), signal.read(state));
        }
        return values;
    }

    public boolean isOscillating() {
        return state.getPropagator().isOscillating();
    }

    private void advance(StepListener listener)
            throws TestExecutionException, InterruptedException {
        int step = 0;
        do {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }
            LearnerTimingAccess.step(state);
            step++;
            if (listener != null) {
                listener.afterStep(tick, step);
            }
            if (step >= MAX_PROPAGATION_STEPS && LearnerTimingAccess.hasPendingSteps(state)) {
                throw new TestExecutionException("传播超过 " + MAX_PROPAGATION_STEPS
                        + " 步仍未稳定，疑似组合反馈环或振荡（tick " + tick + "）");
            }
        } while (LearnerTimingAccess.hasPendingSteps(state));
    }
}
