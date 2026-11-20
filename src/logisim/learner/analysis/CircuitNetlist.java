package logisim.learner.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;

/**
 * NetlistBuilder 的构建结果：全部网络、元件与端口的索引。
 */
public final class CircuitNetlist {
    private final Circuit circuit;
    private final List<Net> nets;
    private final List<End> ends;
    private final List<Component> components;
    private final Map<Location, Net> netByPoint;
    private final Map<Component, SplitterBitMap> splitters;

    CircuitNetlist(Circuit circuit, List<Net> nets, List<End> ends,
            List<Component> components, Map<Location, Net> netByPoint,
            Map<Component, SplitterBitMap> splitters) {
        this.circuit = circuit;
        this.nets = nets;
        this.ends = ends;
        this.components = components;
        this.netByPoint = netByPoint;
        this.splitters = splitters;
    }

    public Circuit getCircuit() {
        return circuit;
    }

    public List<Net> getNets() {
        return Collections.unmodifiableList(nets);
    }

    public List<End> getEnds() {
        return Collections.unmodifiableList(ends);
    }

    public List<Component> getComponents() {
        return Collections.unmodifiableList(components);
    }

    /** 某坐标点所属网络；该点不在任何导线上时返回 null。 */
    public Net netAt(Location loc) {
        return netByPoint.get(loc);
    }

    public Net netOf(End end) {
        return netByPoint.get(end.getLocation());
    }

    public boolean isSplitter(Component comp) {
        return splitters.containsKey(comp);
    }

    /** 分线器的位映射；非分线器返回 null。 */
    public SplitterBitMap splitterBits(Component comp) {
        return splitters.get(comp);
    }

    List<End> endsOf(Component comp) {
        List<End> ret = new ArrayList<End>();
        for (End e : ends) {
            if (e.getComponent() == comp) ret.add(e);
        }
        return ret;
    }

    static Map<Component, SplitterBitMap> newSplitterMap() {
        return new IdentityHashMap<Component, SplitterBitMap>();
    }
}
