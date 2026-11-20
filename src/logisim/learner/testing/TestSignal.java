package logisim.learner.testing;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.std.wiring.Pin;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class TestSignal {
    private final Component pin;
    private final String name;
    private final int width;
    private final boolean input;

    private TestSignal(Component pin, String name, int width, boolean input) {
        this.pin = pin;
        this.name = name;
        this.width = width;
        this.input = input;
    }

    public static List<TestSignal> scan(Circuit circuit) {
        List<Component> pins = new ArrayList<Component>();
        for (Component component : circuit.getNonWires()) {
            if (component.getFactory() instanceof Pin) {
                pins.add(component);
            }
        }
        Collections.sort(pins, new Comparator<Component>() {
            @Override
            public int compare(Component left, Component right) {
                int byY = Integer.compare(left.getLocation().getY(), right.getLocation().getY());
                return byY != 0 ? byY : Integer.compare(left.getLocation().getX(), right.getLocation().getX());
            }
        });
        List<TestSignal> signals = new ArrayList<TestSignal>();
        Set<String> usedNames = new HashSet<String>();
        for (Component pin : pins) {
            boolean input = !Boolean.TRUE.equals(pin.getAttributeSet().getValue(Pin.ATTR_TYPE));
            String label = pin.getAttributeSet().getValue(StdAttr.LABEL);
            String name = label == null || label.trim().isEmpty()
                    ? (input ? "输入@" : "输出@") + pin.getLocation() : label.trim();
            if (!usedNames.add(name)) {
                name = name + "@" + pin.getLocation();
                usedNames.add(name);
            }
            int width = pin.getAttributeSet().getValue(StdAttr.WIDTH).getWidth();
            signals.add(new TestSignal(pin, name, width, input));
        }
        return Collections.unmodifiableList(signals);
    }

    public Component getPin() {
        return pin;
    }

    public String getName() {
        return name;
    }

    public int getWidth() {
        return width;
    }

    public boolean isInput() {
        return input;
    }

    public Value read(CircuitState state) {
        return ((Pin) pin.getFactory()).getValue(state.getInstanceState(pin));
    }

    public void write(CircuitState state, int value) {
        if (!input) {
            throw new IllegalStateException("Cannot drive output pin " + name);
        }
        ((Pin) pin.getFactory()).setValue(state.getInstanceState(pin),
                Value.createKnown(BitWidth.create(width), value));
        state.markComponentAsDirty(pin);
    }

    public String format(Value value) {
        if (value == null) {
            return "null";
        }
        return value.isFullyDefined() ? value.toHexString() : value.toDisplayString();
    }

    @Override
    public String toString() {
        return name + "[" + width + "]";
    }
}
