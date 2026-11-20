package logisim.learner.debug;

import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.data.Value;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import logisim.learner.analysis.ComponentPath;
import logisim.learner.analysis.ConnectivityAnalyzer;
import logisim.learner.analysis.Diagnostic;
import logisim.learner.analysis.RaceAnalyzer;

public final class SignalInsightService {
    private CircuitState cachedState;
    private List<Diagnostic> cachedDiagnostics = Collections.emptyList();

    public void invalidate() {
        cachedState = null;
        cachedDiagnostics = Collections.emptyList();
    }

    public SignalInsight inspect(CircuitState state, WaveformSelection selection) {
        List<Diagnostic> related = relatedDiagnostics(state, selection);
        List<ComponentPath> paths = pathsOf(related);
        int pulseStart = WaveformAnomalyDetector.findPulseStart(selection);
        if (pulseStart >= 0) {
            return pulseInsight(selection, related, paths, pulseStart);
        }
        Value value = selection.getSample().getValue();
        if (value.isErrorValue()) {
            return new SignalInsight(selection, SignalInsight.Kind.ERROR_VALUE,
                    "错误值 E：优先检查驱动或位宽冲突",
                    basicDescription(selection)
                            + "\nE 表示错误/冲突，不是普通的模拟毛刺。请检查以下相关诊断：\n"
                            + evidence(related, "multiple-drivers", "width-conflict",
                                    "runtime-error-value"),
                    related, paths);
        }
        if (!value.isFullyDefined()) {
            return new SignalInsight(selection, SignalInsight.Kind.UNKNOWN_VALUE,
                    "未知值 x：检查悬空输入或电路初始化",
                    basicDescription(selection)
                            + "\nx 表示当前值未知，不等于检测到毛刺。常见原因是漏接、未驱动或尚未复位。\n"
                            + evidence(related, "floating-input", "undriven-output-pin",
                                    "runtime-unknown-value"),
                    related, paths);
        }
        if (!paths.isEmpty()) {
            return new SignalInsight(selection, SignalInsight.Kind.STRUCTURAL_RISK,
                    "此次跳变存在竞争冒险结构风险",
                    basicDescription(selection)
                            + "\n该信号关联不同深度的重汇聚路径。路径证据如下：\n"
                            + evidence(related, "potential-hazard"),
                    related, paths);
        }
        return new SignalInsight(selection, SignalInsight.Kind.NORMAL,
                "已选中信号：" + selection.getSignal().getName(),
                basicDescription(selection)
                        + "\n未识别出同 tick 短脉冲或关联的重汇聚路径。可点击“定位异常点”查看信号源。",
                related, paths);
    }

    private SignalInsight pulseInsight(WaveformSelection selection, List<Diagnostic> related,
            List<ComponentPath> paths, int start) {
        List<SignalSample> history = selection.getHistory();
        SignalSample before = history.get(start);
        SignalSample middle = history.get(start + 1);
        SignalSample after = history.get(start + 2);
        int radix = selection.getSignal().getRadix();
        StringBuilder explanation = new StringBuilder(basicDescription(selection));
        explanation.append("\n采样序列：")
                .append(before.getValue().toDisplayString(radix)).append(" → ")
                .append(middle.getValue().toDisplayString(radix)).append(" → ")
                .append(after.getValue().toDisplayString(radix))
                .append("；传播序号 ").append(before.getSequence()).append(" → ")
                .append(middle.getSequence()).append(" → ").append(after.getSequence())
                .append("，均位于 tick ").append(middle.getTick())
                .append("。仅持续一个传播步的短脉冲符合毛刺的波形表现。");
        if (paths.isEmpty()) {
            explanation.append("\n未找到关联的重汇聚路径；也可能来自外部输入、时序逻辑或 Logisim 的事件合并，不能仅凭此波形确定唯一原因。");
        } else {
            explanation.append("\n以下不同深度的传播路径可解释该短脉冲：\n")
                    .append(evidence(related, "potential-hazard"));
        }
        return new SignalInsight(selection, SignalInsight.Kind.SHORT_PULSE,
                "检测到同 tick 短脉冲（疑似毛刺）", explanation.toString(), related, paths);
    }

    private List<Diagnostic> relatedDiagnostics(CircuitState state, WaveformSelection selection) {
        if (state == null) {
            return Collections.emptyList();
        }
        if (state != cachedState) {
            cachedState = state;
            cachedDiagnostics = new ArrayList<Diagnostic>();
            try {
                cachedDiagnostics.addAll(new ConnectivityAnalyzer().analyze(state));
                cachedDiagnostics.addAll(new RaceAnalyzer().analyze(state));
            } catch (RuntimeException ignored) {
                cachedDiagnostics = Collections.emptyList();
            }
        }
        List<Diagnostic> related = new ArrayList<Diagnostic>();
        for (Diagnostic diagnostic : cachedDiagnostics) {
            if (diagnostic.getRelatedComponents().contains(selection.getSignal().getComponent())) {
                related.add(diagnostic);
            }
        }
        return related;
    }

    private List<ComponentPath> pathsOf(List<Diagnostic> related) {
        List<ComponentPath> paths = new ArrayList<ComponentPath>();
        for (Diagnostic diagnostic : related) {
            if (!"potential-hazard".equals(diagnostic.getType())) {
                continue;
            }
            for (ComponentPath path : diagnostic.getPaths()) {
                if (!paths.contains(path)) {
                    paths.add(path);
                }
            }
        }
        return paths;
    }

    private String basicDescription(WaveformSelection selection) {
        SignalSample sample = selection.getSample();
        return "信号：" + selection.getSignal().getName()
                + "\n元件：" + selection.getSignal().getComponent().getFactory().getDisplayName()
                + " @" + selection.getSignal().getComponent().getLocation()
                + "\n采样：传播序号 " + sample.getSequence() + "，tick " + sample.getTick()
                + "，值 " + sample.getValue().toDisplayString(selection.getSignal().getRadix());
    }

    private String evidence(List<Diagnostic> diagnostics, String... preferredTypes) {
        StringBuilder text = new StringBuilder();
        for (String type : preferredTypes) {
            for (Diagnostic diagnostic : diagnostics) {
                if (type.equals(diagnostic.getType()) && text.indexOf(diagnostic.getExplanation()) < 0) {
                    if (text.length() > 0) {
                        text.append("\n");
                    }
                    text.append("• ").append(diagnostic.getExplanation());
                    if (text.length() > 3500) {
                        return text.substring(0, 3500) + "…";
                    }
                }
            }
        }
        if (text.length() == 0) {
            return "没有匹配到更具体的结构诊断，请结合电路体检继续检查。";
        }
        return text.toString();
    }
}
