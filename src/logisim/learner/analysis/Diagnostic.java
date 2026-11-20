package logisim.learner.analysis;

import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

public final class Diagnostic {
    public enum Severity {
        ERROR, WARNING, INFO
    }

    private final Severity severity;
    private final String type;
    private final Component component;
    private final String componentName;
    private final Location location;
    private final String explanation;
    private final List<Component> relatedComponents;
    private final List<ComponentPath> paths;

    public Diagnostic(Severity severity, String type, Component component,
            Location location, String explanation) {
        this(severity, type, component, location, explanation,
                Collections.<Component>emptyList(), Collections.<ComponentPath>emptyList());
    }

    public Diagnostic(Severity severity, String type, Component component,
            Location location, String explanation, List<ComponentPath> paths) {
        this(severity, type, component, location, explanation,
                Collections.<Component>emptyList(), paths);
    }

    public Diagnostic(Severity severity, String type, Component component,
            Location location, String explanation, Collection<Component> relatedComponents,
            List<ComponentPath> paths) {
        this.severity = severity;
        this.type = type;
        this.component = component;
        this.componentName = component == null ? "当前电路"
                : component.getFactory().getDisplayName();
        this.location = location == null && component != null ? component.getLocation() : location;
        this.explanation = explanation;
        LinkedHashSet<Component> related = new LinkedHashSet<Component>();
        if (relatedComponents != null) {
            related.addAll(relatedComponents);
        }
        if (paths != null) {
            for (ComponentPath path : paths) {
                related.addAll(path.getComponents());
            }
        }
        if (related.isEmpty() && component != null) {
            related.add(component);
        }
        this.relatedComponents = Collections.unmodifiableList(new ArrayList<Component>(related));
        this.paths = paths == null ? Collections.<ComponentPath>emptyList()
                : Collections.unmodifiableList(new ArrayList<ComponentPath>(paths));
    }

    public Severity getSeverity() {
        return severity;
    }

    public String getType() {
        return type;
    }

    public Component getComponent() {
        return component;
    }

    public String getComponentName() {
        return componentName;
    }

    public Location getLocation() {
        return location;
    }

    public String getExplanation() {
        return explanation;
    }

    public List<Component> getRelatedComponents() {
        return relatedComponents;
    }

    public List<ComponentPath> getPaths() {
        return paths;
    }
}
