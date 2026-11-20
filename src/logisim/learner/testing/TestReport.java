package logisim.learner.testing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TestReport {
    private final List<TestResult> results;
    private final boolean functional;
    private final String generationDescription;
    private final boolean cancelled;

    public TestReport(List<TestResult> results, boolean functional,
            String generationDescription, boolean cancelled) {
        this.results = Collections.unmodifiableList(new ArrayList<TestResult>(results));
        this.functional = functional;
        this.generationDescription = generationDescription;
        this.cancelled = cancelled;
    }

    public List<TestResult> getResults() {
        return results;
    }

    public boolean isFunctional() {
        return functional;
    }

    public String getGenerationDescription() {
        return generationDescription;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public int count(TestResult.Status status) {
        int count = 0;
        for (TestResult result : results) {
            if (result.getStatus() == status) {
                count++;
            }
        }
        return count;
    }
}
