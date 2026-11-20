package logisim.learner.ui;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.circuit.WireSet;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.EndData;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.gui.generic.CanvasPane;
import com.cburch.logisim.gui.main.Canvas;
import com.cburch.logisim.gui.main.Frame;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.util.StringUtil;
import java.awt.Color;
import java.awt.Rectangle;
import javax.swing.SwingUtilities;

public final class CircuitLocator {
    private final Project project;

    public CircuitLocator(Project project) {
        this.project = project;
    }

    public void locate(Component component, String reason) {
        if (component == null) {
            return;
        }
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> locate(component, reason));
            return;
        }
        Frame frame = project.getFrame();
        Circuit circuit = project.getCurrentCircuit();
        if (frame == null || circuit == null || !circuit.contains(component)) {
            return;
        }
        frame.setEditorView(Frame.EDIT_LAYOUT);
        frame.viewComponentAttributes(circuit, component);
        Canvas canvas = frame.getCanvas();
        Wire wire = findConnectedWire(circuit, component);
        canvas.setHighlightedWires(wire == null ? WireSet.EMPTY : circuit.getWireSet(wire));
        scrollToComponent(canvas, component);
        String label = (reason == null ? "已定位" : reason) + "："
                + component.getFactory().getDisplayName() + " @" + component.getLocation();
        canvas.setErrorMessage(StringUtil.constantGetter(label), new Color(185, 45, 150));
        frame.setVisible(true);
        frame.toFront();
    }

    public void clear() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::clear);
            return;
        }
        Frame frame = project.getFrame();
        if (frame == null) {
            return;
        }
        frame.viewComponentAttributes(null, null);
        Canvas canvas = frame.getCanvas();
        canvas.setHighlightedWires(WireSet.EMPTY);
        canvas.setErrorMessage(null);
    }

    private Wire findConnectedWire(Circuit circuit, Component component) {
        for (int pass = 0; pass < 2; pass++) {
            boolean outputOnly = pass == 0;
            for (EndData end : component.getEnds()) {
                if (outputOnly && !end.isOutput()) {
                    continue;
                }
                for (Wire wire : circuit.getWires()) {
                    if (wire.contains(end.getLocation())) {
                        return wire;
                    }
                }
            }
        }
        return null;
    }

    private void scrollToComponent(Canvas canvas, Component component) {
        CanvasPane pane = (CanvasPane) SwingUtilities.getAncestorOfClass(CanvasPane.class, canvas);
        double zoom = pane == null ? 1.0 : pane.getZoomFactor();
        Bounds bounds = component.getBounds();
        int x = (int) Math.round(bounds.getX() * zoom) - 30;
        int y = (int) Math.round(bounds.getY() * zoom) - 30;
        int width = Math.max(20, (int) Math.round(bounds.getWidth() * zoom)) + 60;
        int height = Math.max(20, (int) Math.round(bounds.getHeight() * zoom)) + 60;
        canvas.scrollRectToVisible(new Rectangle(x, y, width, height));
    }
}
