package logisim.learner.debug;

import com.cburch.logisim.data.Value;

public final class SignalSample {
    private final long sequence;
    private final long tick;
    private final Value value;

    public SignalSample(long sequence, long tick, Value value) {
        this.sequence = sequence;
        this.tick = tick;
        this.value = value;
    }

    public long getSequence() {
        return sequence;
    }

    public long getTick() {
        return tick;
    }

    public Value getValue() {
        return value;
    }
}
