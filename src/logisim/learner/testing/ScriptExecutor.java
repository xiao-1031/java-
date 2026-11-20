package logisim.learner.testing;

public interface ScriptExecutor {
    interface ProgressListener {
        void resultProduced(TestResult result, int totalCases);
    }

    TestReport execute(TestSuite suite) throws InterruptedException;
}
