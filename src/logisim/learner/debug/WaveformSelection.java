package logisim.learner.debug;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class WaveformSelection {
    private final SignalDescriptor signal;
    private final List<SignalSample> history;
    private final int sampleIndex;

    public WaveformSelection(SignalDescriptor signal, List<SignalSample> history, int sampleIndex) {
        if (signal == null || history == null || sampleIndex < 0 || sampleIndex >= history.size()) {
            throw new IllegalArgumentException("Invalid waveform selection");
        }
        this.signal = signal;
        this.history = Collections.unmodifiableList(new ArrayList<SignalSample>(history));
        this.sampleIndex = sampleIndex;
    }

    public SignalDescriptor getSignal() {
        return signal;
    }

    public List<SignalSample> getHistory() {
        return history;
    }

    public int getSampleIndex() {
        return sampleIndex;
    }

    public SignalSample getSample() {
        return history.get(sampleIndex);
    }
}
