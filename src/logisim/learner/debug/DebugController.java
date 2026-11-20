package logisim.learner.debug;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.Simulator;
import com.cburch.logisim.circuit.SimulatorEvent;
import com.cburch.logisim.circuit.SimulatorListener;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.ProjectEvent;
import com.cburch.logisim.proj.ProjectListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public final class DebugController implements SimulatorListener, ProjectListener {
    public interface Listener {
        void controllerChanged();
    }

    private final Project project;
    private final Simulator simulator;
    private final WaveformModel waveformModel;
    private final CopyOnWriteArrayList<Listener> listeners
            = new CopyOnWriteArrayList<Listener>();
    private volatile CircuitState currentState;
    private volatile List<SignalDescriptor> availableSignals
            = Collections.emptyList();
    private volatile boolean pauseAfterPropagation;
    private volatile boolean tickPauseRequested;
    private boolean disposed;

    public DebugController(Project project, WaveformModel waveformModel) {
        this.project = project;
        this.simulator = project.getSimulator();
        this.waveformModel = waveformModel;
        project.addProjectListener(this);
        simulator.addSimulatorListener(this);
        refreshCircuit();
    }

    public Project getProject() {
        return project;
    }

    public void addListener(Listener listener) {
        listeners.addIfAbsent(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public List<SignalDescriptor> getAvailableSignals() {
        return new ArrayList<SignalDescriptor>(availableSignals);
    }

    public CircuitState getCurrentState() {
        return currentState;
    }

    public String getCurrentCircuitName() {
        Circuit circuit = project.getCurrentCircuit();
        return circuit == null ? "—" : circuit.getName();
    }

    public boolean isRunning() {
        return simulator.isRunning();
    }

    public boolean isTicking() {
        return simulator.isTicking();
    }

    public boolean isOscillating() {
        return simulator.isOscillating();
    }

    public void start() {
        pauseAfterPropagation = false;
        tickPauseRequested = false;
        simulator.setIsRunning(true);
    }

    public void pause() {
        pauseAfterPropagation = false;
        tickPauseRequested = false;
        simulator.setIsRunning(false);
    }

    public void step() {
        pauseAfterPropagation = false;
        tickPauseRequested = false;
        simulator.setIsRunning(false);
        simulator.step();
    }

    public void tickOnce() {
        pauseAfterPropagation = false;
        tickPauseRequested = true;
        simulator.setIsRunning(true);
        simulator.tick();
    }

    public void reset() {
        pauseAfterPropagation = false;
        tickPauseRequested = false;
        waveformModel.clearSamples();
        simulator.requestReset();
    }

    public final void refreshCircuit() {
        CircuitState nextState = project.getCircuitState();
        if (nextState != currentState) {
            currentState = nextState;
            waveformModel.reset();
        }
        rescanSignals();
    }

    public void rescanSignals() {
        CircuitState state = currentState;
        if (state == null || state.getCircuit() == null) {
            availableSignals = Collections.emptyList();
            fireChanged();
            return;
        }
        List<SignalDescriptor> found = SignalDescriptor.scan(state);
        Set<Component> components = new HashSet<Component>();
        for (SignalDescriptor signal : found) {
            components.add(signal.getComponent());
        }
        availableSignals = Collections.unmodifiableList(found);
        waveformModel.retainComponents(components);
        fireChanged();
    }

    public void addSignal(SignalDescriptor signal) {
        CircuitState state = currentState;
        if (state != null) {
            waveformModel.addSignal(signal, state);
        }
    }

    public void removeSignal(SignalDescriptor signal) {
        waveformModel.removeSignal(signal);
    }

    @Override
    public void propagationCompleted(SimulatorEvent event) {
        if (disposed) {
            return;
        }
        try {
            waveformModel.capture(currentState, false);
        } catch (RuntimeException ignored) {
            // Circuit edits can invalidate a probe between two propagation callbacks.
        }
        if (pauseAfterPropagation) {
            pauseAfterPropagation = false;
            simulator.setIsRunning(false);
        }
        fireChanged();
    }

    @Override
    public void tickCompleted(SimulatorEvent event) {
        waveformModel.onTick();
        if (tickPauseRequested) {
            tickPauseRequested = false;
            pauseAfterPropagation = true;
        }
    }

    @Override
    public void simulatorStateChanged(SimulatorEvent event) {
        fireChanged();
    }

    @Override
    public void projectChanged(ProjectEvent event) {
        int action = event.getAction();
        if (action == ProjectEvent.ACTION_SET_STATE
                || action == ProjectEvent.ACTION_SET_CURRENT
                || action == ProjectEvent.ACTION_SET_FILE) {
            refreshCircuit();
        }
    }

    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        project.removeProjectListener(this);
        simulator.removeSimulatorListener(this);
        listeners.clear();
        waveformModel.reset();
    }

    private void fireChanged() {
        for (Listener listener : listeners) {
            listener.controllerChanged();
        }
    }
}
