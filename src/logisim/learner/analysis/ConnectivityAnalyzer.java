package logisim.learner.analysis;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.BitWidth;

/**
 * 连通性静态检查：位宽冲突、多驱动、悬空输入、悬挂输出。
 * 诊断均含中文教学解释与具体端口坐标。
 */
public final class ConnectivityAnalyzer implements CircuitAnalyzer {

    public List<Diagnostic> analyze(CircuitState state) {
        CircuitNetlist netlist = new NetlistBuilder().build(state.getCircuit());
        List<Diagnostic> out = new ArrayList<Diagnostic>();
        for (Net net : netlist.getNets()) {
            checkWidthConflict(net, out);
            checkDrivers(net, out);
            checkFloatingInputs(net, out);
            checkDanglingOutputs(net, out);
        }
        return out;
    }

    /** 同一网络中出现不同位宽的端口 → 位宽冲突。 */
    private void checkWidthConflict(Net net, List<Diagnostic> out) {
        Set<BitWidth> widths = net.widths();
        if (widths.size() <= 1) return;
        StringBuilder sb = new StringBuilder();
        sb.append("这条导线网络上连接了不同位宽的端口（");
        boolean first = true;
        for (BitWidth w : widths) {
            if (!first) sb.append(" 与 ");
            sb.append(w.getWidth()).append(" 位");
            first = false;
        }
        sb.append("）。Logisim 会把它标为红色错误线：信号位数对不上，"
                + "数据无法正确传递。请检查各端口的数据位宽属性，"
                + "或用分线器显式拆分/合并总线。涉及端口：");
        for (End e : net.getEnds()) {
            sb.append("\n  - ").append(e.describe())
              .append("，位宽 ").append(e.getWidth().getWidth());
        }
        End any = net.getEnds().get(0);
        out.add(new Diagnostic(Diagnostic.Severity.ERROR, "width-conflict",
                any.getComponent(), any.getLocation(), sb.toString(),
                netComponents(net), Collections.<ComponentPath>emptyList()));
    }

    /** 网络上全部相关非分线器元件（去重），供点击定位整个冲突网络。 */
    private static Collection<Component> netComponents(Net net) {
        LinkedHashSet<Component> related = new LinkedHashSet<Component>();
        for (End e : net.getEnds()) {
            if (!e.isSplitterEnd()) related.add(e.getComponent());
        }
        return related;
    }

    /** 多驱动：≥2 个独占输出为确定错误；含三态输出则降级为提示。 */
    private void checkDrivers(Net net, List<Diagnostic> out) {
        List<End> drivers = new ArrayList<End>();
        for (End e : net.drivers()) {
            if (e.isSplitterEnd()) continue; // 分线器只是中继
            drivers.add(e);
        }
        if (drivers.size() <= 1) return;
        int exclusive = 0;
        for (End e : drivers) {
            if (!KnownQuirks.isNonExclusiveDriver(e)) exclusive++;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("这条网络上有 ").append(drivers.size()).append(" 个输出端同时驱动：");
        for (End e : drivers) {
            sb.append("\n  - ").append(e.describe());
        }
        if (exclusive >= 2) {
            sb.append("\n其中至少两个是独占输出：若它们给出不同的值，"
                    + "导线上会出现红色 E（冲突错误）。请只保留一个驱动源，"
                    + "或改用三态输出并保证同一时刻只有一个使能。");
            out.add(new Diagnostic(Diagnostic.Severity.ERROR, "multiple-drivers",
                    drivers.get(0).getComponent(), drivers.get(0).getLocation(),
                    sb.toString(), netComponents(net),
                    Collections.<ComponentPath>emptyList()));
        } else {
            sb.append("\n它们包含三态（非独占）输出：这常见于总线结构，本身合法，"
                    + "但必须保证各使能信号互斥——同一时刻至多一个输出有效，"
                    + "否则同样会产生 E 冲突。");
            out.add(new Diagnostic(Diagnostic.Severity.INFO, "shared-tristate-bus",
                    drivers.get(0).getComponent(), drivers.get(0).getLocation(),
                    sb.toString(), netComponents(net),
                    Collections.<ComponentPath>emptyList()));
        }
    }

    /** 有读取端却没有任何驱动源的网络 → 悬空输入。 */
    private void checkFloatingInputs(Net net, List<Diagnostic> out) {
        List<End> drivers = net.drivers();
        int realDrivers = 0;
        for (End e : drivers) {
            if (!e.isSplitterEnd()) {
                realDrivers++;
            }
        }
        if (realDrivers > 0) return;
        for (End e : net.readers()) {
            if (e.isSplitterEnd()
                    || KnownQuirks.isOptionalUnconnectedInput(e.getComponent(), e.getIndex())) {
                continue;
            }
            if (KnownQuirks.isCircuitOutputPin(e.getComponent())) {
                out.add(new Diagnostic(Diagnostic.Severity.WARNING,
                        "undriven-output-pin", e.getComponent(), e.getLocation(),
                        "这个输出引脚（output 属性为 true）所在的导线网络没有任何信号源驱动它，"
                        + "仿真时它将显示蓝色 x（未知）。输出引脚负责把电路内部的值"
                        + "呈现给上层电路或观测者，请把要观测/输出的信号接到它上面。"));
            } else {
                out.add(new Diagnostic(Diagnostic.Severity.WARNING,
                        "floating-input", e.getComponent(), e.getLocation(),
                        "这个输入端口连接的导线网络没有任何输出端驱动它，"
                        + "仿真时读到的是蓝色 x（未知值），并会沿数据通路继续传播。"
                        + "常见原因是漏接导线、导线看起来相连其实没搭上，"
                        + "或漏接常量/电源/地。请检查该端口的连线。"));
            }
        }
    }

    /** 有驱动端却没有任何读取端 → 悬挂输出（提示级）。 */
    private void checkDanglingOutputs(Net net, List<Diagnostic> out) {
        int readers = 0;
        for (End e : net.readers()) {
            if (!e.isSplitterEnd()) readers++;
        }
        if (readers > 0) return;
        for (End e : net.drivers()) {
            if (e.isSplitterEnd()) continue;
            out.add(new Diagnostic(Diagnostic.Severity.INFO, "dangling-output",
                    e.getComponent(), e.getLocation(),
                    "这个输出端口驱动的导线网络没有被任何输入端读取，"
                    + "信号到这里就断了。如果它本应参与后续运算，"
                    + "请检查是否漏连；如果是有意预留，可以忽略本提示。"));
        }
    }
}
