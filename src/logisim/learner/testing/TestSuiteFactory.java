package logisim.learner.testing;

import java.util.ArrayList;
import java.util.List;

public final class TestSuiteFactory {

    private TestSuiteFactory() {
    }

    public static TestSuite automatic(List<TestSignal> signals, int maxCases,
            int ticks, boolean resetBefore, long seed) {
        List<TestSignal> inputs = new ArrayList<TestSignal>();
        for (TestSignal signal : signals) {
            if (signal.isInput()) {
                inputs.add(signal);
            }
        }
        List<TestVector> vectors = new TemporalInputGenerator().generate(
                inputs, maxCases, ticks, resetBefore, seed);
        String description = "连续时序测试：" + vectors.size() + " 个观测点，每点 "
                + ticks + " tick，固定种子 " + seed + "；仅首个观测点可按配置复位";
        return new TestSuite(signals, vectors, description);
    }
}
