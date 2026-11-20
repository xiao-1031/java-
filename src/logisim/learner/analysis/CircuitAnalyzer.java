package logisim.learner.analysis;

import com.cburch.logisim.circuit.CircuitState;
import java.util.List;

public interface CircuitAnalyzer {
    List<Diagnostic> analyze(CircuitState state);
}
