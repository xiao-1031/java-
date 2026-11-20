package logisim.learner.analysis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.EndData;
import com.cburch.logisim.data.Location;

/**
 * 由 Circuit 构建网络表：以"点"为单位做并查集，
 * 合并以下三类连接：
 *  1. 共用端点的导线；
 *  2. 端点落在另一条导线段上的交叉/搭接（T 型连接）；
 *  3. 位于导线段上（而非端点）的元件端口。
 * 分线器各端保持独立网络，仅记录位映射供有向图透明化使用。
 */
public final class NetlistBuilder {

    private static final class UnionFind {
        private final Map<Location, Location> parent = new HashMap<Location, Location>();

        void add(Location loc) {
            if (!parent.containsKey(loc)) parent.put(loc, loc);
        }

        Location find(Location loc) {
            Location p = parent.get(loc);
            if (p == null) return null;
            while (!p.equals(parent.get(p))) p = parent.get(p);
            Location cur = loc;
            while (!cur.equals(p)) {
                Location next = parent.get(cur);
                parent.put(cur, p);
                cur = next;
            }
            return p;
        }

        void union(Location a, Location b) {
            Location ra = find(a);
            Location rb = find(b);
            if (ra != null && rb != null && !ra.equals(rb)) parent.put(ra, rb);
        }
    }

    public CircuitNetlist build(Circuit circuit) {
        Set<Wire> wires = circuit.getWires();
        Set<Component> comps = circuit.getNonWires();

        UnionFind uf = new UnionFind();
        // 所有关键点：导线端点 + 全部非导线元件的端口位置
        List<Location> endPoints = new ArrayList<Location>();
        Map<Component, List<EndData>> compEnds =
                new LinkedHashMap<Component, List<EndData>>();
        for (Wire w : wires) {
            uf.add(w.getEndLocation(0));
            uf.add(w.getEndLocation(1));
        }
        for (Component comp : comps) {
            List<EndData> list = comp.getEnds();
            compEnds.put(comp, list);
            if (list == null) continue;
            for (EndData ed : list) {
                if (ed == null) continue;
                uf.add(ed.getLocation());
                endPoints.add(ed.getLocation());
            }
        }

        // 每条导线：把它的两个端点、以及落在导线段上的所有关键点并入同一网
        for (Wire w : wires) {
            Location a = w.getEndLocation(0);
            Location b = w.getEndLocation(1);
            uf.union(a, b);
            for (Location p : endPoints) {
                if (!p.equals(a) && !p.equals(b) && w.contains(p)) {
                    uf.union(a, p);
                }
            }
        }
        // 导线端点落在另一条导线段上（T 型搭接）
        List<Wire> wireList = new ArrayList<Wire>(wires);
        for (Wire w : wireList) {
            for (int i = 0; i < 2; i++) {
                Location p = w.getEndLocation(i);
                for (Wire other : wireList) {
                    if (other == w) continue;
                    if (other.contains(p)) {
                        uf.union(p, other.getEndLocation(0));
                    }
                }
            }
        }

        // 生成 Net 对象
        Map<Location, Net> netByRoot = new HashMap<Location, Net>();
        Map<Location, Net> netByPoint = new HashMap<Location, Net>();
        List<Net> nets = new ArrayList<Net>();
        for (Location p : uf.parent.keySet()) {
            Location root = uf.find(p);
            Net net = netByRoot.get(root);
            if (net == null) {
                net = new Net(nets.size());
                netByRoot.put(root, net);
                nets.add(net);
            }
            net.addPoint(p);
            netByPoint.put(p, net);
        }

        // 端口封装，分线器识别与位映射
        Map<Component, SplitterBitMap> splitters =
                CircuitNetlist.newSplitterMap();
        List<End> allEnds = new ArrayList<End>();
        List<Component> compList = new ArrayList<Component>(compEnds.keySet());
        for (Component comp : compList) {
            boolean isSplitter = SplitterBitMap.isSplitter(comp);
            if (isSplitter) {
                SplitterBitMap map = SplitterBitMap.read(comp);
                if (map != null) splitters.put(comp, map);
            }
            List<EndData> list = compEnds.get(comp);
            if (list == null) continue;
            for (int i = 0; i < list.size(); i++) {
                EndData ed = list.get(i);
                if (ed == null) continue;
                End end = new End(comp, i, ed, isSplitter);
                allEnds.add(end);
                Net net = netByPoint.get(ed.getLocation());
                if (net != null) net.addEnd(end);
            }
        }

        return new CircuitNetlist(circuit, nets, allEnds, compList,
                netByPoint, splitters);
    }
}
