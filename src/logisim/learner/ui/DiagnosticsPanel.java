package logisim.learner.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import logisim.learner.analysis.CircuitAnalyzer;
import logisim.learner.analysis.Diagnostic;
import logisim.learner.debug.DebugController;
import logisim.learner.i18n.Messages;

public final class DiagnosticsPanel extends JPanel {
    private final DebugController controller;
    private final List<CircuitAnalyzer> analyzers;
    private final CircuitLocator locator;
    private final DefaultTableModel tableModel;
    private final JTable table;
    private final JLabel summary = new JLabel(Messages.get("analysis.empty"));
    private List<Diagnostic> currentDiagnostics = java.util.Collections.emptyList();

    public DiagnosticsPanel(DebugController controller, List<CircuitAnalyzer> analyzers,
            boolean showRiskNote) {
        this(controller, analyzers, showRiskNote, new CircuitLocator(controller.getProject()));
    }

    public DiagnosticsPanel(DebugController controller, List<CircuitAnalyzer> analyzers,
            boolean showRiskNote, CircuitLocator locator) {
        super(new BorderLayout(6, 6));
        this.controller = controller;
        this.analyzers = analyzers;
        this.locator = locator;
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        JButton analyze = new JButton(Messages.get("analysis.run"));
        analyze.addActionListener(event -> runAnalysis());
        JButton locate = new JButton(Messages.get("diagnostics.locate"));
        locate.addActionListener(event -> locateSelected());
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        toolbar.add(analyze);
        toolbar.add(locate);
        toolbar.add(summary);
        add(toolbar, BorderLayout.NORTH);
        tableModel = new DefaultTableModel(new Object[] {
            Messages.get("analysis.column.severity"), Messages.get("analysis.column.type"),
            Messages.get("analysis.column.component"), Messages.get("analysis.column.location"),
            Messages.get("analysis.column.explanation") }, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table = new JTable(tableModel) {
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
                Component component = super.prepareRenderer(renderer, row, column);
                Object explanation = getValueAt(row, 4);
                if (component instanceof javax.swing.JComponent && explanation != null) {
                    ((javax.swing.JComponent) component).setToolTipText(explanation.toString());
                }
                return component;
            }
        };
        table.getColumnModel().getColumn(0).setPreferredWidth(55);
        table.getColumnModel().getColumn(1).setPreferredWidth(115);
        table.getColumnModel().getColumn(2).setPreferredWidth(110);
        table.getColumnModel().getColumn(3).setPreferredWidth(85);
        table.getColumnModel().getColumn(4).setPreferredWidth(520);
        add(new JScrollPane(table), BorderLayout.CENTER);
        if (showRiskNote) {
            add(new JLabel(Messages.get("analysis.note")), BorderLayout.SOUTH);
        }
    }

    private void runAnalysis() {
        List<Diagnostic> diagnostics = new ArrayList<Diagnostic>();
        if (controller.getCurrentState() != null) {
            for (CircuitAnalyzer analyzer : analyzers) {
                try {
                    diagnostics.addAll(analyzer.analyze(controller.getCurrentState()));
                } catch (RuntimeException exception) {
                    diagnostics.add(new Diagnostic(Diagnostic.Severity.ERROR, "分析失败",
                            null, null, "分析器未能完成检查：" + exception.getMessage()));
                }
            }
        }
        showDiagnostics(diagnostics);
    }

    public void clear() {
        currentDiagnostics = java.util.Collections.emptyList();
        tableModel.setRowCount(0);
        summary.setText(Messages.get("analysis.empty"));
        locator.clear();
    }

    private void showDiagnostics(List<Diagnostic> diagnostics) {
        currentDiagnostics = java.util.Collections.unmodifiableList(
                new ArrayList<Diagnostic>(diagnostics));
        tableModel.setRowCount(0);
        int errors = 0;
        int warnings = 0;
        int notes = 0;
        for (Diagnostic diagnostic : diagnostics) {
            String severity;
            if (diagnostic.getSeverity() == Diagnostic.Severity.ERROR) {
                errors++;
                severity = Messages.get("analysis.severity.error");
            } else if (diagnostic.getSeverity() == Diagnostic.Severity.WARNING) {
                warnings++;
                severity = Messages.get("analysis.severity.warning");
            } else {
                notes++;
                severity = Messages.get("analysis.severity.info");
            }
            tableModel.addRow(new Object[] {severity, displayType(diagnostic.getType()),
                diagnostic.getComponentName(),
                diagnostic.getLocation() == null ? "—" : diagnostic.getLocation().toString(),
                diagnostic.getExplanation() });
        }
        if (diagnostics.isEmpty()) {
            summary.setText(Messages.get("analysis.noIssues"));
        } else {
            summary.setText(Messages.format("analysis.summary", diagnostics.size(),
                    errors, warnings, notes));
        }
    }

    private void locateSelected() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= currentDiagnostics.size()) {
            return;
        }
        Diagnostic diagnostic = currentDiagnostics.get(row);
        com.cburch.logisim.comp.Component component = diagnostic.getComponent();
        if (component == null && !diagnostic.getRelatedComponents().isEmpty()) {
            component = diagnostic.getRelatedComponents().get(0);
        }
        if (component != null) {
            locator.locate(component, displayType(diagnostic.getType()));
        }
    }

    private String displayType(String type) {
        switch (type) {
        case "width-conflict": return "位宽冲突";
        case "multiple-drivers": return "多驱动冲突";
        case "shared-tristate-bus": return "三态共享总线";
        case "undriven-output-pin": return "输出引脚未驱动";
        case "floating-input": return "输入悬空";
        case "dangling-output": return "未使用的输出";
        case "combinational-loop": return "组合反馈环";
        case "potential-hazard": return "潜在竞争冒险";
        case "sim-oscillation": return "仿真振荡";
        case "runtime-error-value": return "运行时错误值 E";
        case "runtime-unknown-value": return "运行时未知值 x";
        default: return type;
        }
    }
}
