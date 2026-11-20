package logisim.learner.debug;

import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Value;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public final class WaveformModel {
    public interface Listener {
        void waveformChanged();
    }

    public static final class Snapshot {
        private final List<SignalDescriptor> signals;
        private final Map<SignalDescriptor, List<SignalSample>> histories;
        private final long sequence;
        private final long tick;
        private final boolean recording;

        private Snapshot(WaveformModel model) {
            this.signals = Collections.unmodifiableList(
                    new ArrayList<SignalDescriptor>(model.histories.keySet()));
            Map<SignalDescriptor, List<SignalSample>> copy
                    = new LinkedHashMap<SignalDescriptor, List<SignalSample>>();
            for (SignalDescriptor signal : signals) {
                copy.put(signal, Collections.unmodifiableList(
                        new ArrayList<SignalSample>(model.histories.get(signal))));
            }
            this.histories = Collections.unmodifiableMap(copy);
            this.sequence = model.sequence;
            this.tick = model.tick;
            this.recording = model.recording;
        }

        public List<SignalDescriptor> getSignals() {
            return signals;
        }

        public List<SignalSample> getHistory(SignalDescriptor signal) {
            List<SignalSample> history = histories.get(signal);
            return history == null ? Collections.<SignalSample>emptyList() : history;
        }

        public long getSequence() {
            return sequence;
        }

        public long getTick() {
            return tick;
        }

        public boolean isRecording() {
            return recording;
        }

        public boolean hasSamples() {
            for (List<SignalSample> history : histories.values()) {
                if (!history.isEmpty()) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final int MAX_SAMPLES_PER_SIGNAL = 2000;

    private final LinkedHashMap<SignalDescriptor, List<SignalSample>> histories
            = new LinkedHashMap<SignalDescriptor, List<SignalSample>>();
    private final CopyOnWriteArrayList<Listener> listeners
            = new CopyOnWriteArrayList<Listener>();
    private long sequence;
    private long tick;
    private boolean recording;

    public void addListener(Listener listener) {
        listeners.addIfAbsent(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public synchronized void addSignal(SignalDescriptor signal, CircuitState state) {
        if (histories.containsKey(signal)) {
            return;
        }
        histories.put(signal, new ArrayList<SignalSample>());
        capture(state, true);
        fireChanged();
    }

    public synchronized void removeSignal(SignalDescriptor signal) {
        if (histories.remove(signal) != null) {
            fireChanged();
        }
    }

    public synchronized void retainComponents(Set<Component> components) {
        boolean changed = false;
        List<SignalDescriptor> signals = new ArrayList<SignalDescriptor>(histories.keySet());
        for (SignalDescriptor signal : signals) {
            if (!components.contains(signal.getComponent())) {
                histories.remove(signal);
                changed = true;
            }
        }
        if (changed) {
            fireChanged();
        }
    }

    public synchronized boolean capture(CircuitState state, boolean force) {
        if (state == null || histories.isEmpty() || (!recording && !force)) {
            return false;
        }
        boolean changed = force;
        if (!changed) {
            for (Map.Entry<SignalDescriptor, List<SignalSample>> entry : histories.entrySet()) {
                List<SignalSample> history = entry.getValue();
                Value next = read(entry.getKey(), state);
                if (history.isEmpty() || !Objects.equals(history.get(history.size() - 1).getValue(), next)) {
                    changed = true;
                    break;
                }
            }
        }
        if (!changed) {
            return false;
        }
        sequence++;
        for (Map.Entry<SignalDescriptor, List<SignalSample>> entry : histories.entrySet()) {
            List<SignalSample> history = entry.getValue();
            history.add(new SignalSample(sequence, tick, read(entry.getKey(), state)));
            if (history.size() > MAX_SAMPLES_PER_SIGNAL) {
                history.remove(0);
            }
        }
        fireChanged();
        return true;
    }

    private Value read(SignalDescriptor signal, CircuitState state) {
        Value value = signal.readValue(state);
        return value == null ? Value.NIL : value;
    }

    public synchronized void onTick() {
        tick++;
    }

    public synchronized void setRecording(boolean recording) {
        if (this.recording != recording) {
            this.recording = recording;
            fireChanged();
        }
    }

    public synchronized boolean isRecording() {
        return recording;
    }

    public synchronized void clearSamples() {
        histories.values().forEach(List::clear);
        sequence = 0;
        tick = 0;
        fireChanged();
    }

    public synchronized void reset() {
        histories.clear();
        sequence = 0;
        tick = 0;
        fireChanged();
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(this);
    }

    private void fireChanged() {
        for (Listener listener : listeners) {
            listener.waveformChanged();
        }
    }
}
