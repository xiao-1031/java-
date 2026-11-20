package logisim.learner.debug;

import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.gui.log.Loggable;
import com.cburch.logisim.instance.StdAttr;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class SignalDescriptor {
    private final Component component;
    private final Object option;
    private final Loggable loggable;
    private final List<Component> instancePath;
    private final String name;
    private int radix;

    public SignalDescriptor(Component component, Object option, Loggable loggable) {
        this(Collections.<Component>emptyList(), "", component, option, loggable);
    }

    public SignalDescriptor(List<Component> instancePath, String pathName,
            Component component, Object option, Loggable loggable) {
        this.instancePath = Collections.unmodifiableList(
                new ArrayList<Component>(instancePath));
        this.component = component;
        this.option = option;
        this.loggable = loggable;
        String baseName = loggable.getLogName(option);
        if (baseName == null || baseName.trim().isEmpty()) {
            baseName = component.getFactory().getDisplayName() + component.getLocation();
        }
        this.name = pathName + (option == null ? baseName : baseName + "." + option);
        this.radix = 2;
    }

    public static List<SignalDescriptor> scan(CircuitState state) {
        List<SignalDescriptor> found = new ArrayList<SignalDescriptor>();
        scanState(state, Collections.<Component>emptyList(), "", found);
        Collections.sort(found, new Comparator<SignalDescriptor>() {
            @Override
            public int compare(SignalDescriptor left, SignalDescriptor right) {
                int byName = left.getName().compareToIgnoreCase(right.getName());
                if (byName != 0) {
                    return byName;
                }
                return left.getComponent().getLocation().toString().compareTo(
                        right.getComponent().getLocation().toString());
            }
        });
        return found;
    }

    private static void scanState(CircuitState state, List<Component> path,
            String pathName, List<SignalDescriptor> found) {
        for (Component component : state.getCircuit().getNonWires()) {
            Object feature = component.getFeature(Loggable.class);
            if (feature instanceof Loggable) {
                Loggable loggable = (Loggable) feature;
                try {
                    Object[] options = loggable.getLogOptions(state);
                    if (options == null || options.length == 0) {
                        found.add(new SignalDescriptor(path, pathName,
                                component, null, loggable));
                    } else {
                        for (Object option : options) {
                            found.add(new SignalDescriptor(path, pathName,
                                    component, option, loggable));
                        }
                    }
                } catch (RuntimeException ignored) {
                }
            }
            if (component.getFactory() instanceof SubcircuitFactory) {
                CircuitState substate = ((SubcircuitFactory) component.getFactory())
                        .getSubstate(state, component);
                List<Component> childPath = new ArrayList<Component>(path);
                childPath.add(component);
                String label = component.getAttributeSet().getValue(StdAttr.LABEL);
                String instanceName = label == null || label.trim().isEmpty()
                        ? component.getFactory().getDisplayName() + "@"
                                + component.getLocation() : label.trim();
                scanState(substate, childPath, pathName + instanceName + "/", found);
            }
        }
    }

    public Component getComponent() {
        return component;
    }

    public List<Component> getInstancePath() {
        return instancePath;
    }

    public String getName() {
        return name;
    }

    public int getRadix() {
        return radix;
    }

    public void setRadix(int radix) {
        if (radix != 2 && radix != 16) {
            throw new IllegalArgumentException("Only binary and hexadecimal are supported");
        }
        this.radix = radix;
    }

    public Value readValue(CircuitState state) {
        CircuitState owner = state;
        for (Component instance : instancePath) {
            Object data = owner.getData(instance);
            if (!(data instanceof CircuitState)) {
                return null;
            }
            owner = (CircuitState) data;
        }
        return loggable.getLogValue(owner, option);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof SignalDescriptor)) {
            return false;
        }
        SignalDescriptor that = (SignalDescriptor) other;
        return component == that.component && instancePath.equals(that.instancePath)
                && Objects.equals(option, that.option);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * System.identityHashCode(component) + instancePath.hashCode())
                + Objects.hashCode(option);
    }

    @Override
    public String toString() {
        return name + "  " + component.getFactory().getDisplayName()
                + " " + component.getLocation();
    }
}
