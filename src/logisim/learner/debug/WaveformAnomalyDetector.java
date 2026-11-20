package logisim.learner.debug;

import java.util.List;

public final class WaveformAnomalyDetector {
    private WaveformAnomalyDetector() {
    }

    public static boolean isShortPulse(List<SignalSample> history, int middleIndex) {
        if (middleIndex <= 0 || middleIndex + 1 >= history.size()) {
            return false;
        }
        SignalSample before = history.get(middleIndex - 1);
        SignalSample middle = history.get(middleIndex);
        SignalSample after = history.get(middleIndex + 1);
        return before.getTick() == middle.getTick() && middle.getTick() == after.getTick()
                && before.getValue().isFullyDefined() && middle.getValue().isFullyDefined()
                && after.getValue().isFullyDefined()
                && before.getValue().equals(after.getValue())
                && !before.getValue().equals(middle.getValue());
    }

    public static int findPulseStart(WaveformSelection selection) {
        List<SignalSample> history = selection.getHistory();
        int selected = selection.getSampleIndex();
        for (int middle = selected - 1; middle <= selected + 1; middle++) {
            if (isShortPulse(history, middle)) {
                return middle - 1;
            }
        }
        return -1;
    }
}
