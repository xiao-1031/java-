package logisim.learner.testing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TestSuite {
    private final List<TestSignal> signals;
    private final List<TestVector> vectors;
    private final String generationDescription;

    public TestSuite(List<TestSignal> signals, List<TestVector> vectors,
            String generationDescription) {
        this.signals = Collections.unmodifiableList(new ArrayList<TestSignal>(signals));
        this.vectors = Collections.unmodifiableList(new ArrayList<TestVector>(vectors));
        this.generationDescription = generationDescription;
    }

    public List<TestSignal> getSignals() {
        return signals;
    }

    public List<TestSignal> getInputs() {
        return filter(true);
    }

    public List<TestSignal> getOutputs() {
        return filter(false);
    }

    private List<TestSignal> filter(boolean input) {
        List<TestSignal> result = new ArrayList<TestSignal>();
        for (TestSignal signal : signals) {
            if (signal.isInput() == input) {
                result.add(signal);
            }
        }
        return Collections.unmodifiableList(result);
    }

    public List<TestVector> getVectors() {
        return vectors;
    }

    public String getGenerationDescription() {
        return generationDescription;
    }

    public boolean hasExpectedOutputs() {
        for (TestVector vector : vectors) {
            if (vector.hasExpectedOutputs()) {
                return true;
            }
        }
        return false;
    }
}
