package logisim.learner.analysis;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.Simulator;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.gui.log.Loggable;

/**
 * 时序/竞争静态+运行时检查：
 *  1. 在"组合有向图"（时序元件为边界、分线器按位映射透明化）上
 *     检测组合反馈环；
 *  2. 检测同一源经不同深度路径重新汇聚到同一组合元件的潜在冒险
 *     （注意：只是潜在风险，不代表已经观察到毛刺）；
 *  3. 运行时：simulator 振荡状态、已观测端口（Loggable）出现 E/x 值。
 * 诊断去重且限量，避免大图时输出爆炸。
 */
public final class RaceAnalyzer implements CircuitAnalyzer {

    /** 潜在冒险诊断上限。 */
    private static final int MAX_HAZARD_DIAGS = 20;
    /** 运行时 E/x 诊断上限。 */
    private static final int MAX_RUNTIME_DIAGS = 20;
    /** 冒险深度搜索上限（元件跳数）。 */
    private static final int MAX_DEPTH = 32;

    /** 组合有向图：节点为元件，边表示"输出经导线网到达输入"。 */
    private static final class CombGraph {
        private final Map<Component, Set<Component>> adj =
                new IdentityHashMap<Component, Set<Component>>();

        void addEdge(Component from, Component to) {
            Set<Component> set = adj.get(from);
            if (set == null) {
                set = new LinkedHashSet<Component>();
                adj.put(from, set);
            }
            set.add(to);
        }

        Set<Component> next(Component c) {
            Set<Component> set = adj.get(c);
            return set == null ? new LinkedHashSet<Component>() : set;
        }

        Set<Component> nodes() {
            Set<Component> all = new LinkedHashSet<Component>(adj.keySet());
            for (Set<Component> s : adj.values()) all.addAll(s);
            return all;
        }
    }

    public List<Diagnostic> analyze(CircuitState state) {
        CircuitNetlist netlist = new NetlistBuilder().build(state.getCircuit());
        List<Diagnostic> out = new ArrayList<Diagnostic>();
        CombGraph graph = buildGraph(netlist);
        Set<Component> inLoop = detectFeedback(graph, out);
        detectHazards(graph, out);
        runtimeChecks(state, inLoop, out);
        return out;
    }

    /** 时序元件和子电路是本级组合图的分析边界。 */
    static boolean isSequential(Component comp) {
        return comp.getFactory() instanceof com.cburch.logisim.circuit.SubcircuitFactory
                || comp.getFactory().getClass().getName()
                        .startsWith("com.cburch.logisim.std.memory.");
    }

    private CombGraph buildGraph(CircuitNetlist netlist) {
        CombGraph g = new CombGraph();
        for (Net net : netlist.getNets()) {
            List<End> drivers = new ArrayList<End>();
            List<End> readers = new ArrayList<End>();
            for (End e : net.getEnds()) {
                if (e.drives() && !e.isSplitterEnd()) drivers.add(e);
                if (e.reads() && !e.isSplitterEnd()
                        && !isSequential(e.getComponent())) readers.add(e);
            }
            for (End d : drivers) {
                for (End r : readers) {
                    g.addEdge(d.getComponent(), r.getComponent());
                }
            }
        }
        // 分线器透明化：仅合并端 0 与共享位的分出端之间互通，
        // 分出端彼此不连，避免把总线拆/并制造假环。
        for (Component comp : netlist.getComponents()) {
            SplitterBitMap bits = netlist.splitterBits(comp);
            if (bits == null) continue;
            List<End> ends = netlist.endsOf(comp);
            Net spine = null;
            Map<Integer, Net> branchNets = new HashMap<Integer, Net>();
            for (End e : ends) {
                Net n = netlist.netOf(e);
                if (n == null) continue;
                if (e.getIndex() == 0) spine = n;
                else branchNets.put(Integer.valueOf(e.getIndex()), n);
            }
            for (Map.Entry<Integer, Net> en : branchNets.entrySet()) {
                if (!bits.endSharesWithSpine(en.getKey().intValue())) continue;
                if (spine != null) linkThrough(netlist, g, spine, comp, en.getValue());
                if (spine != null) linkThrough(netlist, g, en.getValue(), comp, spine);
            }
        }
        return g;
    }

    /** from 网上的驱动 → 分线器 s → to 网上的读取。 */
    private void linkThrough(CircuitNetlist netlist, CombGraph g,
            Net from, Component s, Net to) {
        for (End d : from.getEnds()) {
            if (!d.drives() || d.isSplitterEnd()) continue;
            if (d.getComponent() == s) continue;
            g.addEdge(d.getComponent(), s);
        }
        for (End r : to.getEnds()) {
            if (!r.reads() || r.isSplitterEnd()) continue;
            if (r.getComponent() == s || isSequential(r.getComponent())) continue;
            g.addEdge(s, r.getComponent());
        }
    }

    /** Tarjan 强连通分量；每个规模>1 的分量（或自环）报一次组合反馈环。 */
    private Set<Component> detectFeedback(CombGraph g, List<Diagnostic> out) {
        Set<Component> inLoop = new HashSet<Component>();
        Map<Component, Integer> index = new IdentityHashMap<Component, Integer>();
        Map<Component, Integer> low = new IdentityHashMap<Component, Integer>();
        Deque<Component> stack = new ArrayDeque<Component>();
        Set<Component> onStack = new HashSet<Component>();
        int[] counter = { 0 };
        for (Component c : g.nodes()) {
            if (!index.containsKey(c)) {
                strongConnect(c, g, index, low, stack, onStack, counter,
                        inLoop, out);
            }
        }
        return inLoop;
    }

    private void strongConnect(Component v, CombGraph g,
            Map<Component, Integer> index, Map<Component, Integer> low,
            Deque<Component> stack, Set<Component> onStack, int[] counter,
            Set<Component> inLoop, List<Diagnostic> out) {
        index.put(v, Integer.valueOf(counter[0]));
        low.put(v, Integer.valueOf(counter[0]));
        counter[0]++;
        stack.push(v);
        onStack.add(v);
        for (Component w : g.next(v)) {
            if (!index.containsKey(w)) {
                strongConnect(w, g, index, low, stack, onStack, counter,
                        inLoop, out);
                low.put(v, Integer.valueOf(
                        Math.min(low.get(v).intValue(), low.get(w).intValue())));
            } else if (onStack.contains(w)) {
                low.put(v, Integer.valueOf(
                        Math.min(low.get(v).intValue(), index.get(w).intValue())));
            }
        }
        if (low.get(v).equals(index.get(v))) {
            List<Component> scc = new ArrayList<Component>();
            Component w;
            do {
                w = stack.pop();
                onStack.remove(w);
                scc.add(w);
            } while (w != v);
            boolean selfLoop = scc.size() == 1 && g.next(v).contains(v);
            if (scc.size() > 1 || selfLoop) {
                inLoop.addAll(scc);
                StringBuilder sb = new StringBuilder();
                sb.append("检测到组合逻辑反馈环（");
                for (Component c : scc) {
                    sb.append(c.getFactory().getDisplayName())
                      .append(" @").append(c.getLocation()).append("  ");
                }
                sb.append("）：组合元件的输出不经寄存器又回到了自己的输入。"
                        + "交叉耦合的 RS 锁存器是有意为之的例外；"
                        + "否则环路会导致仿真振荡或行为依赖传播顺序。"
                        + "若这不是存储结构，请断开环路或插入寄存器。");
                out.add(new Diagnostic(Diagnostic.Severity.WARNING,
                        "combinational-loop", scc.get(0), scc.get(0).getLocation(),
                        sb.toString()));
            }
        }
    }

    /**
     * 组合延迟成本：分线器与 Pin/Probe 等连接/观测节点只起连通作用，
     * 不计门延迟；其余普通组合元件计 1 级。路径仍保留这些 0 成本节点
     * 以便定位。
     */
    private static int delayCost(Component comp) {
        String name = comp.getFactory().getName();
        if ("Splitter".equals(name) || "Pin".equals(name) || "Probe".equals(name)) {
            return 0;
        }
        return 1;
    }

    /**
     * 潜在冒险：从每个源出发记录到达各组合元件的不同延迟深度的真实路径，
     * 每个节点最多保留两条不同深度的路径作为证据（PathWitness），
     * 同一汇出现两种以上深度即重新汇聚（reconvergent fanout）。
     */
    private void detectHazards(CombGraph g, List<Diagnostic> out) {
        int emitted = 0;
        Set<String> seen = new HashSet<String>();
        for (Component source : g.nodes()) {
            if (emitted >= MAX_HAZARD_DIAGS) break;
            // 每个节点：深度 -> 到达该深度的一条真实路径（最多两条不同深度）
            Map<Component, Map<Integer, List<Component>>> witnesses =
                    new IdentityHashMap<Component, Map<Integer, List<Component>>>();
            Deque<Component> stack = new ArrayDeque<Component>();
            Deque<Integer> dstack = new ArrayDeque<Integer>();
            Deque<List<Component>> pstack = new ArrayDeque<List<Component>>();
            stack.push(source);
            dstack.push(Integer.valueOf(0));
            List<Component> startPath = new ArrayList<Component>();
            startPath.add(source);
            pstack.push(startPath);
            Map<Integer, List<Component>> srcDepths =
                    new HashMap<Integer, List<Component>>();
            srcDepths.put(Integer.valueOf(0), startPath);
            witnesses.put(source, srcDepths);
            while (!stack.isEmpty()) {
                Component cur = stack.pop();
                int depth = dstack.pop().intValue();
                List<Component> path = pstack.pop();
                for (Component nxt : g.next(cur)) {
                    int nd = depth + delayCost(nxt);
                    if (nd > MAX_DEPTH) continue;
                    Map<Integer, List<Component>> byDepth = witnesses.get(nxt);
                    if (byDepth == null) {
                        byDepth = new HashMap<Integer, List<Component>>();
                        witnesses.put(nxt, byDepth);
                    }
                    Integer key = Integer.valueOf(nd);
                    if (byDepth.containsKey(key)) continue;
                    if (byDepth.size() >= 2) continue; // 已记录足够证据，抑制路径爆炸
                    List<Component> npath = new ArrayList<Component>(path);
                    npath.add(nxt);
                    byDepth.put(key, npath);
                    stack.push(nxt);
                    dstack.push(key);
                    pstack.push(npath);
                }
            }
            for (Map.Entry<Component, Map<Integer, List<Component>>> en
                    : witnesses.entrySet()) {
                if (emitted >= MAX_HAZARD_DIAGS) break;
                Component sink = en.getKey();
                if (sink == source || en.getValue().size() < 2) continue;
                String key = System.identityHashCode(source) + "->"
                        + System.identityHashCode(sink);
                if (!seen.add(key)) continue;
                emitted++;
                List<Map.Entry<Integer, List<Component>>> sorted =
                        new ArrayList<Map.Entry<Integer, List<Component>>>(
                                en.getValue().entrySet());
                Collections.sort(sorted,
                        new Comparator<Map.Entry<Integer, List<Component>>>() {
                            public int compare(
                                    Map.Entry<Integer, List<Component>> a,
                                    Map.Entry<Integer, List<Component>> b) {
                                return a.getKey().intValue() - b.getKey().intValue();
                            }
                        });
                Map.Entry<Integer, List<Component>> shortE = sorted.get(0);
                Map.Entry<Integer, List<Component>> longE =
                        sorted.get(sorted.size() - 1);
                int shortDepth = shortE.getKey().intValue();
                int longDepth = longE.getKey().intValue();
                ComponentPath shortPath =
                        new ComponentPath(shortE.getValue(), shortDepth);
                ComponentPath longPath =
                        new ComponentPath(longE.getValue(), longDepth);
                List<ComponentPath> paths = new ArrayList<ComponentPath>();
                paths.add(shortPath);
                paths.add(longPath);
                out.add(new Diagnostic(Diagnostic.Severity.INFO,
                        "potential-hazard", sink, sink.getLocation(),
                        "信号从 " + source.getFactory().getDisplayName()
                        + " @" + source.getLocation() + " 出发，经两条不同延迟的路径"
                        + "重新汇聚到 " + sink.getFactory().getDisplayName()
                        + " @" + sink.getLocation() + "：\n"
                        + "  短路径（" + shortDepth + " 级门延迟）："
                        + shortPath.describe() + "\n"
                        + "  长路径（" + longDepth + " 级门延迟）："
                        + longPath.describe() + "\n"
                        + "两条路径的深度差为 " + (longDepth - shortDepth)
                        + " 级门延迟。这是潜在的冒险（hazard）：输入变化瞬间"
                        + "输出可能出现短暂毛刺。"
                        + "注意这只是结构上的潜在风险，不代表已经观察到毛刺；"
                        + "对时钟驱动的同步电路通常无害，但若该输出直接驱动时钟、"
                        + "清零等边沿敏感端，请增加冗余项或改为同步设计。",
                        paths));
            }
        }
    }

    /** 运行时检查：振荡状态 + Loggable 观测值中的 E/x。 */
    private void runtimeChecks(CircuitState state, Set<Component> inLoop,
            List<Diagnostic> out) {
        boolean oscillating = false;
        try {
            if (state.getProject() != null) {
                Simulator sim = state.getProject().getSimulator();
                if (sim != null) oscillating = sim.isOscillating();
            }
        } catch (Throwable t) {
            // 仿真器不可用时跳过运行时检查
        }
        if (oscillating) {
            out.add(new Diagnostic(Diagnostic.Severity.ERROR,
                    "sim-oscillation", null, null,
                    "仿真器检测到振荡：电路状态在每个传播步后仍在变化，无法稳定。"
                    + "这几乎总是组合反馈环（如无延迟的反相器自环）导致的。"
                    + "请结合组合反馈环诊断定位环路，断开它或插入寄存器。"));
        }
        CircuitNetlist netlist = new NetlistBuilder().build(state.getCircuit());
        int emitted = 0;
        for (Component comp : netlist.getComponents()) {
            if (emitted >= MAX_RUNTIME_DIAGS) break;
            if (inLoop.contains(comp)) continue; // 环内元件已由静态环诊断覆盖
            Object feature;
            try {
                feature = comp.getFeature(Loggable.class);
            } catch (Throwable t) {
                continue;
            }
            if (!(feature instanceof Loggable)) continue;
            Loggable log = (Loggable) feature;
            Object[] options;
            try {
                options = log.getLogOptions(state);
            } catch (Throwable t) {
                continue;
            }
            Object[] opts = options == null ? new Object[] { null } : options;
            for (Object opt : opts) {
                if (emitted >= MAX_RUNTIME_DIAGS) break;
                Value v;
                try {
                    v = log.getLogValue(state, opt);
                } catch (Throwable t) {
                    continue;
                }
                if (v == null) continue;
                if (v.isErrorValue()) {
                    emitted++;
                    out.add(new Diagnostic(Diagnostic.Severity.WARNING,
                            "runtime-error-value", comp, comp.getLocation(),
                            "运行时观测：该元件的观测值当前为 E（冲突错误）。"
                            + "通常是同一导线被多个输出同时驱动且取值不同，"
                            + "请检查多驱动诊断中列出的网络。"));
                } else if (v.isUnknown()) {
                    emitted++;
                    out.add(new Diagnostic(Diagnostic.Severity.INFO,
                            "runtime-unknown-value", comp, comp.getLocation(),
                            "运行时观测：该元件的观测值当前为 x（未知）。"
                            + "常见于输入悬空或电路尚未完全复位；"
                            + "若伴随悬空输入诊断，请先处理连线问题。"));
                }
            }
        }
    }
}
