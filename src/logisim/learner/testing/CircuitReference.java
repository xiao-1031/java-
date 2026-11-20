package logisim.learner.testing;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CircuitReference implements ScriptExecutor, AutoCloseable {
    private static final class ReferenceLoader extends Loader {
        ReferenceLoader() {
            super(null);
        }

        @Override
        public void showError(String message) {
            throw new IllegalArgumentException("无法加载参考电路：" + message);
        }
    }

    private final Project project;
    private final CircuitState initialState;
    private final List<TestSignal> signals;

    private CircuitReference(Project project, Circuit circuit, List<TestSignal> signals) {
        this.project = project;
        this.initialState = new CircuitState(project, circuit);
        this.signals = signals;
    }

    public static CircuitReference open(File file, List<TestSignal> requiredSignals)
            throws IOException, com.cburch.logisim.file.LoadFailedException {
        LogisimFile logisimFile = new ReferenceLoader().openLogisimFile(file);
        Circuit circuit = logisimFile.getMainCircuit();
        if (circuit == null) {
            throw new IllegalArgumentException("参考文件没有主电路：" + file.getName());
        }
        List<TestSignal> referenceSignals = TestSignal.scan(circuit);
        validateInterface(requiredSignals, referenceSignals);
        return new CircuitReference(new Project(logisimFile), circuit, referenceSignals);
    }

    @Override
    public TestReport execute(TestSuite suite) throws InterruptedException {
        TestSuite localSuite = new TestSuite(signals, suite.getVectors(),
                suite.getGenerationDescription());
        return new TestRunner(initialState.cloneState()).execute(localSuite);
    }

    @Override
    public void close() {
        project.getSimulator().shutDown();
    }

    private static void validateInterface(List<TestSignal> required,
            List<TestSignal> reference) {
        Map<String, TestSignal> referenceByName = new HashMap<String, TestSignal>();
        for (TestSignal signal : reference) {
            referenceByName.put(signal.getName(), signal);
        }
        if (required.size() != reference.size()) {
            throw new IllegalArgumentException("参考电路接口数量不一致：DUT 有 "
                    + required.size() + " 个 Pin，参考电路有 " + reference.size() + " 个");
        }
        for (TestSignal signal : required) {
            TestSignal other = referenceByName.get(signal.getName());
            if (other == null) {
                throw new IllegalArgumentException("参考电路缺少同名 Pin：" + signal.getName()
                        + "。对拍要求输入、输出 Pin 的名称、方向和位宽完全一致");
            }
            if (signal.isInput() != other.isInput() || signal.getWidth() != other.getWidth()) {
                throw new IllegalArgumentException("参考电路 Pin " + signal.getName()
                        + " 的方向或位宽不一致：DUT 为 " + describe(signal)
                        + "，参考电路为 " + describe(other));
            }
        }
    }

    private static String describe(TestSignal signal) {
        return (signal.isInput() ? "输入/" : "输出/") + signal.getWidth() + " 位";
    }
}
