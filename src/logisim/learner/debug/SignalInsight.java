package logisim.learner.debug;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import logisim.learner.analysis.ComponentPath;
import logisim.learner.analysis.Diagnostic;

public final class SignalInsight {
    public enum Kind {
        SHORT_PULSE, ERROR_VALUE, UNKNOWN_VALUE, STRUCTURAL_RISK, NORMAL
    }

    private final WaveformSelection selection;
    private final Kind kind;
    private final String summary;
    private final String explanation;
    private final List<Diagnostic> relatedDiagnostics;
    private final List<ComponentPath> paths;

    public SignalInsight(WaveformSelection selection, Kind kind, String summary,
            String explanation, List<Diagnostic> relatedDiagnostics, List<ComponentPath> paths) {
        this.selection = selection;
        this.kind = kind;
        this.summary = summary;
        this.explanation = explanation;
        this.relatedDiagnostics = Collections.unmodifiableList(
                new ArrayList<Diagnostic>(relatedDiagnostics));
        this.paths = Collections.unmodifiableList(new ArrayList<ComponentPath>(paths));
    }

    public WaveformSelection getSelection() {
        return selection;
    }

    public Kind getKind() {
        return kind;
    }

    public String getSummary() {
        return summary;
    }

    public String getExplanation() {
        return explanation;
    }

    public List<Diagnostic> getRelatedDiagnostics() {
        return relatedDiagnostics;
    }

    public List<ComponentPath> getPaths() {
        return paths;
    }
}
