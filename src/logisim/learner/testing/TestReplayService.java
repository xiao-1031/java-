package logisim.learner.testing;

import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.Simulator;
import com.cburch.logisim.comp.Component;
import logisim.learner.debug.DebugController;
import logisim.learner.debug.SignalDescriptor;
import logisim.learner.debug.WaveformModel;

public final class TestReplayService {
    private final DebugController controller;
    private final WaveformModel waveformModel;

    public TestReplayService(DebugController controller, WaveformModel waveformModel) {
        this.controller = controller;
        this.waveformModel = waveformModel;
    }

    public Component replay(TestResult result, TestSuite suite)
            throws TestExecutionException, InterruptedException {
        controller.pause();
        CircuitState source = controller.getCurrentState();
        CircuitState initial = suite.getVectors().get(0).isResetBefore()
                ? new CircuitState(controller.getProject(), source.getCircuit())
                : source.cloneState();
        controller.getProject().setCircuitState(initial);
        Simulator simulator = controller.getProject().getSimulator();
        boolean wasTicking = simulator.isTicking();
        final SimulationDriver driver = new SimulationDriver(initial);
        try {
            waveformModel.reset();
            for (SignalDescriptor descriptor : controller.getAvailableSignals()) {
                boolean testPin = descriptor.getInstancePath().isEmpty()
                        && isTestPin(descriptor, suite);
                boolean anomaly = result.getAnomalySignal() != null
                        && descriptor.equals(result.getAnomalySignal());
                if (testPin || anomaly || TimingMonitor.shouldObserve(descriptor)) {
                    controller.addSignal(descriptor);
                }
            }
            waveformModel.setRecording(true);
            SimulationDriver.StepListener listener = new SimulationDriver.StepListener() {
                @Override
                public void afterStep(long tick, int propagationStep) {
                    waveformModel.capture(driver.getState(), false);
                }
            };
            for (int i = 0; i <= result.getIndex(); i++) {
                TestVector vector = suite.getVectors().get(i);
                if (vector.isResetBefore()) {
                    driver.reset();
                    waveformModel.capture(driver.getState(), true);
                }
                driver.applyInputs(suite.getInputs(), vector.getInputs(), listener);
                for (int tick = 0; tick < vector.getTicks(); tick++) {
                    waveformModel.onTick();
                    driver.tick(1, listener);
                }
            }
            controller.getProject().repaintCanvas();
            return result.getAnomalySignal() == null ? problematicPin(result, suite)
                    : result.getAnomalySignal().getComponent();
        } finally {
            controller.pause();
            simulator.setIsTicking(wasTicking);
        }
    }

    private boolean isTestPin(SignalDescriptor descriptor, TestSuite suite) {
        for (TestSignal signal : suite.getSignals()) {
            if (descriptor.getComponent() == signal.getPin()) {
                return true;
            }
        }
        return false;
    }

    private Component problematicPin(TestResult result, TestSuite suite) {
        String name = result.getProblematicSignal();
        if (name == null) {
            return null;
        }
        for (TestSignal signal : suite.getOutputs()) {
            if (signal.getName().equals(name)) {
                return signal.getPin();
            }
        }
        return null;
    }
}
