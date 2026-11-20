package logisim.learner.testing;

import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Value;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DifferentialTestRunner implements ScriptExecutor {
    private final ScriptExecutor deviceUnderTest;
    private final ScriptExecutor referenceModel;

    public DifferentialTestRunner(ScriptExecutor deviceUnderTest,
            ScriptExecutor referenceModel) {
        if (deviceUnderTest == null || referenceModel == null) {
            throw new IllegalArgumentException("DUT 和参考模型都不能为空");
        }
        this.deviceUnderTest = deviceUnderTest;
        this.referenceModel = referenceModel;
    }

    @Override
    public TestReport execute(TestSuite suite) throws InterruptedException {
        return run(suite, null);
    }

    public TestReport run(TestSuite suite, ScriptExecutor.ProgressListener listener)
            throws InterruptedException {
        TestReport referenceReport = referenceModel.execute(suite);
        TestReport actualReport = deviceUnderTest.execute(suite);
        List<TestResult> results = new ArrayList<TestResult>();
        int total = suite.getVectors().size();
        for (int i = 0; i < total; i++) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }
            if (i >= actualReport.getResults().size()
                    || i >= referenceReport.getResults().size()) {
                break;
            }
            TestResult result = compare(suite, actualReport.getResults().get(i),
                    referenceReport.getResults().get(i));
            results.add(result);
            if (listener != null) {
                listener.resultProduced(result, total);
            }
        }
        boolean cancelled = actualReport.isCancelled() || referenceReport.isCancelled()
                || results.size() < total;
        String description = "随机脚本对拍：" + suite.getGenerationDescription()
                + "；逐观测点比较全部输出";
        return new TestReport(results, true, description, cancelled);
    }

    private TestResult compare(TestSuite suite, TestResult actual, TestResult reference) {
        if (reference.getStatus() == TestResult.Status.ERROR) {
            return result(actual, actual.getVector(), TestResult.Status.ERROR,
                    "参考模型执行失败：" + reference.getReason(), null);
        }
        Map<String, Integer> expected = referenceOutputs(suite, reference);
        if (expected == null) {
            return result(actual, actual.getVector(), TestResult.Status.ERROR,
                    "参考模型在本观测点产生缺失、E 或 x 输出，不能作为金色参考", null);
        }
        TestVector vector = actual.getVector().withExpectedOutputs(expected);
        String mismatch = findMismatch(suite, actual.getActualOutputs(), expected);
        if (mismatch != null) {
            TestResult.Status status = actual.getStatus() == TestResult.Status.ANOMALY
                    ? TestResult.Status.ANOMALY : TestResult.Status.FAILED;
            String reason = "对拍失败：" + mismatch;
            if (actual.getStatus() == TestResult.Status.ANOMALY) {
                reason += "\n同时检测到时序异常：" + actual.getReason();
            }
            return new TestResult(actual.getIndex(), vector, actual.getActualOutputs(),
                    status, reason, actual.getProblematicSignal(), actual.getAnomalySignal());
        }
        if (actual.getStatus() != TestResult.Status.CLEAN) {
            return new TestResult(actual.getIndex(), vector, actual.getActualOutputs(),
                    actual.getStatus(), actual.getReason(), actual.getProblematicSignal(),
                    actual.getAnomalySignal());
        }
        return new TestResult(actual.getIndex(), vector, actual.getActualOutputs(),
                TestResult.Status.CLEAN, "对拍一致：" + expected.size() + " 个输出与参考模型相同",
                null, null);
    }

    private Map<String, Integer> referenceOutputs(TestSuite suite, TestResult reference) {
        Map<String, Integer> values = new LinkedHashMap<String, Integer>();
        for (TestSignal signal : suite.getOutputs()) {
            Value value = reference.getActualOutputs().get(signal.getName());
            if (value == null || value.isErrorValue() || !value.isFullyDefined()) {
                return null;
            }
            values.put(signal.getName(), value.toIntValue());
        }
        return values;
    }

    private String findMismatch(TestSuite suite, Map<String, Value> actual,
            Map<String, Integer> expected) {
        for (TestSignal signal : suite.getOutputs()) {
            Value expectedValue = Value.createKnown(BitWidth.create(signal.getWidth()),
                    expected.get(signal.getName()).intValue());
            Value actualValue = actual.get(signal.getName());
            if (!expectedValue.equals(actualValue)) {
                return "本观测点输出 " + signal.getName() + "：参考 "
                        + signal.format(expectedValue) + "，实际 " + signal.format(actualValue);
            }
        }
        return null;
    }

    private TestResult result(TestResult original, TestVector vector,
            TestResult.Status status, String reason, String problematicSignal) {
        return new TestResult(original.getIndex(), vector, original.getActualOutputs(),
                status, reason, problematicSignal, original.getAnomalySignal());
    }
}
