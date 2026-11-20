package logisim.learner.analysis;

import com.cburch.logisim.comp.Component;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ComponentPath {
    private final List<Component> components;
    private final int depth;

    public ComponentPath(List<Component> components, int depth) {
        if (components == null || components.isEmpty()) {
            throw new IllegalArgumentException("A path needs at least one component");
        }
        this.components = Collections.unmodifiableList(new ArrayList<Component>(components));
        this.depth = depth;
    }

    public List<Component> getComponents() {
        return components;
    }

    public int getDepth() {
        return depth;
    }

    public String describe() {
        StringBuilder text = new StringBuilder();
        for (Component component : components) {
            if (text.length() > 0) {
                text.append(" → ");
            }
            text.append(component.getFactory().getDisplayName())
                    .append(" @").append(component.getLocation());
        }
        return text.toString();
    }

    @Override
    public String toString() {
        return depth + " 级：" + describe();
    }
}
