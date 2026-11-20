package logisim.learner.testing;

import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Value;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class TestRunner implements ScriptExecutor {
    private final CircuitState isolatedState;

    public TestRunner(CircuitState isolatedState) {
        this.isolatedState = isolatedState;
    }

    @Override
    public TestReport execute(TestSuite suite) throws InterruptedException {
        return run(suite, null);
    }

    public TestReport run(TestSuite suite, ScriptExecutor.ProgressListener listener)
            throws InterruptedException {
        List<TestResult> results = new ArrayList<TestResult>();
        SimulationDriver driver = new SimulationDriver(isolatedState);
        boolean cancelled = false;
        int index = 0;
        for (TestVector vector : suite.getVectors()) {
            if (Thread.currentThread().isInterrupted()) {
                cancelled = true;
                break;
            }
            TestResult result = execute(suite, vector, index++, driver);
            results.add(result);
            if (listener != null) {
                listener.resultProduced(result, suite.getVectors().size());
            }
        }
        return new TestReport(results, suite.hasExpectedOutputs(),
                suite.getGenerationDescription(), cancelled);
    }

    private TestResult execute(TestSuite suite, TestVector vector, int index,
            SimulationDriver driver) throws InterruptedException {
        Map<String, Value> actual = new java.util.LinkedHashMap<String, Value>();
        try {
            TimingMonitor monitor = new TimingMonitor(driver.getState(), suite.getOutputs());
            if (vector.isResetBefore()) {
                driver.reset();
            }
            driver.applyInputs(suite.getInputs(), vector.getInputs(), monitor);
            driver.tick(vector.getTicks(), monitor);
            actual = driver.readOutputs(suite.getOutputs());
            if (driver.isOscillating()) {
                return new TestResult(index, vector, actual, TestResult.Status.ANOMALY,
                        "仿真检测到振荡，输出无法稳定", null);
            }
            if (monitor.hasAnomalies()) {
                return new TestResult(index, vector, actual, TestResult.Status.ANOMALY,
                        monitor.getReason(), monitor.getProblematicSignal(),
                        monitor.getAnomalySignal());
            }
            for (TestSignal signal : suite.getOutputs()) {
                Value value = actual.get(signal.getName());
                if (value == null || value.isErrorValue() || !value.isFullyDefined()) {
                    return new TestResult(index, vector, actual, TestResult.Status.ANOMALY,
                            "输出 " + signal.getName() + " 为 "
                                    + signal.format(value) + "，请检查悬空、冲突或初始化",
                            signal.getName());
                }
            }
            for (Map.Entry<String, Integer> expected : vector.getExpectedOutputs().entrySet()) {
                TestSignal signal = findOutput(suite, expected.getKey());
                Value expectedValue = Value.createKnown(
                        BitWidth.create(signal.getWidth()), expected.getValue().intValue());
                Value actualValue = actual.get(expected.getKey());
                if (!expectedValue.equals(actualValue)) {
                    return new TestResult(index, vector, actual, TestResult.Status.FAILED,
                            "本观测点输出 " + expected.getKey() + "：期望 "
                                    + signal.format(expectedValue) + "，实际 "
                                    + signal.format(actualValue), expected.getKey());
                }
            }
            String reason = vector.hasExpectedOutputs() ? "本观测点输出符合预期"
                    : "逐拍、逐传播步检查未发现 E/x、短脉冲或振荡";
            return new TestResult(index, vector, actual, TestResult.Status.CLEAN, reason, null);
        } catch (TestExecutionException exception) {
            return new TestResult(index, vector, actual, TestResult.Status.ERROR,
                    exception.getMessage(), null);
        } catch (RuntimeException exception) {
            return new TestResult(index, vector, actual, TestResult.Status.ERROR,
                    "执行用例时发生异常：" + exception.getMessage(), null);
        }
    }

    private TestSignal findOutput(TestSuite suite, String name) {
        for (TestSignal signal : suite.getOutputs()) {
            if (signal.getName().equals(name)) {
                return signal;
            }
        }
        throw new IllegalArgumentException("未知输出引脚 " + name);
    }
}
