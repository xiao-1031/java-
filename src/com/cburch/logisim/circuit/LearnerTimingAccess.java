package com.cburch.logisim.circuit;

public final class LearnerTimingAccess {
    private LearnerTimingAccess() {
    }

    public static void reset(CircuitState state) {
        Propagator propagator = state.getPropagator();
        propagator.reset();
        propagator.propagate();
    }

    public static void step(CircuitState state) {
        state.getPropagator().step(null);
    }

    public static boolean hasPendingSteps(CircuitState state) {
        return state.getPropagator().isPending();
    }
}
