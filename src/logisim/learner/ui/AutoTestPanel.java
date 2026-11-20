package logisim.learner.ui;

import com.cburch.logisim.comp.Component;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.data.Value;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import logisim.learner.debug.DebugController;
import logisim.learner.debug.WaveformModel;
import logisim.learner.i18n.Messages;
import logisim.learner.testing.CircuitReference;
import logisim.learner.testing.CsvTestVectorLoader;
import logisim.learner.testing.CsvTestVectorWriter;
import logisim.learner.testing.DifferentialTestRunner;
import logisim.learner.testing.TestReplayService;
import logisim.learner.testing.TestReport;
import logisim.learner.testing.TestResult;
import logisim.learner.testing.TestRunner;
import logisim.learner.testing.TestSignal;
import logisim.learner.testing.TestSuite;
import logisim.learner.testing.TestSuiteFactory;
import logisim.learner.testing.TestVector;

public final class AutoTestPanel extends JPanel {
    private final DebugController controller;
    private final WaveformModel waveformModel;
    private final CircuitLocator locator;
    private final JSpinner maxCases = new JSpinner(new SpinnerNumberModel(256, 1, 100000, 1));
    private final JSpinner ticks = new JSpinner(new SpinnerNumberModel(1, 0, 1000, 1));
    private final JSpinner seed = new JSpinner(new SpinnerNumberModel(1L, 0L, Long.MAX_VALUE, 1L));
    private final JCheckBox resetBefore = new JCheckBox(Messages.get("test.reset"), true);
    private final JButton run = new JButton(Messages.get("test.run"));
    private final JButton differential = new JButton(Messages.get("test.differential"));
    private final JButton cancel = new JButton(Messages.get("test.cancel"));
    private final JButton importCsv = new JButton(Messages.get("test.import"));
    private final JButton replay = new JButton(Messages.get("test.replay"));
    private final JButton locate = new JButton(Messages.get("test.locate"));
    private final JButton export = new JButton(Messages.get("test.export"));
    private final JButton exportScript = new JButton(Messages.get("test.exportScript"));
    private final JLabel signalSummary = new JLabel();
    private final JLabel status = new JLabel();
    private final JProgressBar progress = new JProgressBar(0, 100);
    private final DefaultTableModel tableModel;
    private final JTable table;
    private final List<TestResult> rows = new ArrayList<TestResult>();
    private TestSuite suite;
    private TestReport report;
    private TestWorker worker;
    private Runnable onReplay = () -> { };

    public AutoTestPanel(DebugController controller, WaveformModel waveformModel,
            CircuitLocator locator) {
        super(new BorderLayout(6, 6));
        this.controller = controller;
        this.waveformModel = waveformModel;
        this.locator = locator;
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        add(createSettings(), BorderLayout.NORTH);
        tableModel = new DefaultTableModel(new Object[] {
            Messages.get("test.column.index"), Messages.get("test.column.inputs"),
            Messages.get("test.column.expected"), Messages.get("test.column.actual"),
            Messages.get("test.column.status"), Messages.get("test.column.reason") }, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table = new JTable(tableModel) {
            @Override
            public java.awt.Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
                java.awt.Component component = super.prepareRenderer(renderer, row, column);
                if (row < rows.size()) {
                    TestResult.Status resultStatus = rows.get(row).getStatus();
                    if (resultStatus == TestResult.Status.FAILED || resultStatus == TestResult.Status.ERROR) {
                        component.setBackground(new Color(255, 225, 225));
                    } else if (resultStatus == TestResult.Status.ANOMALY) {
                        component.setBackground(new Color(255, 242, 210));
                    } else {
                        component.setBackground(new Color(230, 247, 236));
                    }
                }
                return component;
            }
        };
        table.getSelectionModel().addListSelectionListener(event -> updateButtons());
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(createBottom(), BorderLayout.SOUTH);
        run.addActionListener(event -> startAutomatic());
        differential.addActionListener(event -> startDifferential());
        cancel.addActionListener(event -> cancelTest());
        importCsv.addActionListener(event -> importAndRun());
        replay.addActionListener(event -> replaySelected());
        locate.addActionListener(event -> locateSelected());
        export.addActionListener(event -> exportReport());
        exportScript.addActionListener(event -> exportScript());
        refreshCircuit();
    }

    public void setOnReplay(Runnable onReplay) {
        this.onReplay = onReplay == null ? () -> { } : onReplay;
    }

    private JPanel createSettings() {
        JPanel panel = new JPanel(new BorderLayout());
        JPanel settings = new JPanel(new FlowLayout(FlowLayout.LEFT));
        settings.add(new JLabel(Messages.get("test.maxCases")));
        settings.add(maxCases);
        settings.add(new JLabel(Messages.get("test.ticks")));
        settings.add(ticks);
        settings.add(resetBefore);
        settings.add(new JLabel(Messages.get("test.seed")));
        settings.add(seed);
        settings.add(importCsv);
        settings.add(run);
        settings.add(differential);
        settings.add(cancel);
        panel.add(settings, BorderLayout.NORTH);
        JPanel state = new JPanel(new BorderLayout());
        state.add(signalSummary, BorderLayout.WEST);
        state.add(status, BorderLayout.CENTER);
        state.add(progress, BorderLayout.EAST);
        panel.add(state, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createBottom() {
        JPanel panel = new JPanel(new BorderLayout());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(replay);
        buttons.add(locate);
        buttons.add(exportScript);
        buttons.add(export);
        panel.add(buttons, BorderLayout.NORTH);
        panel.add(new JLabel(Messages.get("test.note")), BorderLayout.SOUTH);
        return panel;
    }

    public final void refreshCircuit() {
        cancelTest();
        suite = null;
        report = null;
        rows.clear();
        tableModel.setRowCount(0);
        progress.setValue(0);
        if (controller.getCurrentState() != null) {
            List<TestSignal> signals = TestSignal.scan(controller.getCurrentState().getCircuit());
            int inputs = 0;
            int outputs = 0;
            for (TestSignal signal : signals) {
                if (signal.isInput()) {
                    inputs++;
                } else {
                    outputs++;
                }
            }
            signalSummary.setText(Messages.format("test.signals", inputs, outputs));
        } else {
            signalSummary.setText(Messages.get("test.noCircuit"));
        }
        status.setText(Messages.get("test.ready"));
        updateButtons();
    }

    public void dispose() {
        cancelTest();
    }

    private void startAutomatic() {
        if (controller.getCurrentState() == null) {
            return;
        }
        try {
            List<TestSignal> signals = TestSignal.scan(controller.getCurrentState().getCircuit());
            startSuite(automaticSuite(signals));
        } catch (RuntimeException exception) {
            showError(exception);
        }
    }

    private TestSuite automaticSuite(List<TestSignal> signals) {
        return TestSuiteFactory.automatic(signals,
                ((Number) maxCases.getValue()).intValue(), ((Number) ticks.getValue()).intValue(),
                resetBefore.isSelected(), ((Number) seed.getValue()).longValue());
    }

    private void startDifferential() {
        if (controller.getCurrentState() == null) {
            return;
        }
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            List<TestSignal> signals = TestSignal.scan(controller.getCurrentState().getCircuit());
            CircuitReference reference = CircuitReference.open(chooser.getSelectedFile(), signals);
            try {
                startSuite(automaticSuite(signals), reference);
            } catch (RuntimeException exception) {
                reference.close();
                throw exception;
            }
        } catch (Exception exception) {
            showError(exception);
        }
    }

    private void importAndRun() {
        if (controller.getCurrentState() == null) {
            return;
        }
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            List<TestSignal> signals = TestSignal.scan(controller.getCurrentState().getCircuit());
            TestSuite imported = CsvTestVectorLoader.load(chooser.getSelectedFile(), signals,
                    ((Number) ticks.getValue()).intValue(), resetBefore.isSelected());
            startSuite(imported);
        } catch (RuntimeException exception) {
            showError(exception);
        }
    }

    private void startSuite(TestSuite nextSuite) {
        startSuite(nextSuite, null);
    }

    private void startSuite(TestSuite nextSuite, CircuitReference reference) {
        controller.pause();
        this.suite = nextSuite;
        this.report = null;
        rows.clear();
        tableModel.setRowCount(0);
        progress.setValue(0);
        String mode = reference != null ? "test.mode.differential"
                : nextSuite.hasExpectedOutputs() ? "test.mode.functional" : "test.mode.scan";
        status.setText(nextSuite.getGenerationDescription() + " | " + Messages.get(mode));
        CircuitState isolated = controller.getCurrentState().cloneState();
        worker = new TestWorker(nextSuite, isolated, reference);
        worker.addPropertyChangeListener(event -> {
            if ("progress".equals(event.getPropertyName())) {
                progress.setValue(worker.getProgress());
            }
        });
        worker.execute();
        updateButtons();
    }

    private void cancelTest() {
        if (worker != null) {
            worker.cancel(true);
            status.setText(Messages.get("test.cancelling"));
        }
    }

    private void addResult(TestResult result) {
        rows.add(result);
        tableModel.addRow(new Object[] {result.getIndex() + 1, result.getVector().formatInputs(),
            expectedText(result.getVector()), actualText(result), statusText(result),
            result.getReason() });
    }

    private String expectedText(TestVector vector) {
        if (!vector.hasExpectedOutputs()) {
            return "—";
        }
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, Integer> entry : vector.getExpectedOutputs().entrySet()) {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(entry.getKey()).append("=0x")
                    .append(Integer.toHexString(entry.getValue()).toUpperCase());
        }
        return text.toString();
    }

    private String actualText(TestResult result) {
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, Value> entry : result.getActualOutputs().entrySet()) {
            if (text.length() > 0) {
                text.append(", ");
            }
            TestSignal signal = findOutput(entry.getKey());
            text.append(entry.getKey()).append("=")
                    .append(signal == null ? entry.getValue().toDisplayString() : signal.format(entry.getValue()));
        }
        return text.length() == 0 ? "—" : text.toString();
    }

    private String statusText(TestResult result) {
        if (result.getStatus() == TestResult.Status.CLEAN) {
            return Messages.get(result.getVector().hasExpectedOutputs()
                    ? "test.status.pass" : "test.status.clean");
        }
        if (result.getStatus() == TestResult.Status.ANOMALY) {
            return Messages.get("test.status.anomaly");
        }
        if (result.getStatus() == TestResult.Status.FAILED) {
            return Messages.get("test.status.failed");
        }
        return Messages.get("test.status.error");
    }

    private void replaySelected() {
        TestResult selected = selectedResult();
        if (selected == null || suite == null || worker != null) {
            return;
        }
        status.setText(Messages.get("test.replaying"));
        replay.setEnabled(false);
        new SwingWorker<Component, Void>() {
            @Override
            protected Component doInBackground() throws Exception {
                return new TestReplayService(controller, waveformModel).replay(selected, suite);
            }

            @Override
            protected void done() {
                try {
                    Component component = get();
                    onReplay.run();
                    if (component != null) {
                        locator.locate(component, Messages.format("test.replayLocation", selected.getIndex() + 1));
                    }
                    status.setText(Messages.get("test.replayDone"));
                } catch (Exception exception) {
                    showError(exception);
                }
                updateButtons();
            }
        }.execute();
    }

    private void locateSelected() {
        TestResult selected = selectedResult();
        if (selected == null || selected.getProblematicSignal() == null) {
            return;
        }
        TestSignal signal = findOutput(selected.getProblematicSignal());
        if (signal != null) {
            locator.locate(signal.getPin(), Messages.format("test.replayLocation", selected.getIndex() + 1));
        }
    }

    private TestResult selectedResult() {
        int row = table.getSelectedRow();
        return row < 0 || row >= rows.size() ? null : rows.get(row);
    }

    private TestSignal findOutput(String name) {
        if (suite != null) {
            for (TestSignal signal : suite.getOutputs()) {
                if (signal.getName().equals(name)) {
                    return signal;
                }
            }
        }
        return null;
    }

    private File chooseSaveFile(String defaultName) {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(defaultName));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        File file = chooser.getSelectedFile();
        if (file.exists() && JOptionPane.showConfirmDialog(this, file.getAbsolutePath(),
                Messages.get("dialog.error"), JOptionPane.OK_CANCEL_OPTION)
                != JOptionPane.OK_OPTION) {
            return null;
        }
        return file;
    }

    private void exportScript() {
        if (suite == null) {
            return;
        }
        File file = chooseSaveFile("timing-script.csv");
        if (file == null) {
            return;
        }
        try {
            CsvTestVectorWriter.write(file, suite);
            status.setText(Messages.format("test.scriptExported", file.getAbsolutePath()));
        } catch (IOException exception) {
            showError(exception);
        }
    }

    private void exportReport() {
        if (report == null || rows.isEmpty()) {
            return;
        }
        File file = chooseSaveFile("test-report.csv");
        if (file == null) {
            return;
        }
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8))) {
            writer.write('\ufeff');
            writer.write("index,inputs,expected,actual,status,reason");
            writer.newLine();
            for (TestResult result : rows) {
                writer.write((result.getIndex() + 1) + "," + csv(result.getVector().formatInputs())
                        + "," + csv(expectedText(result.getVector())) + "," + csv(actualText(result))
                        + "," + csv(statusText(result)) + "," + csv(result.getReason()));
                writer.newLine();
            }
            status.setText(Messages.format("test.exported", file.getAbsolutePath()));
        } catch (IOException exception) {
            showError(exception);
        }
    }

    private void showSummary() {
        if (report == null) {
            status.setText(Messages.get("test.cancelled"));
            return;
        }
        status.setText(Messages.format("test.summary", rows.size(),
                report.count(TestResult.Status.CLEAN), report.count(TestResult.Status.ANOMALY),
                report.count(TestResult.Status.FAILED), report.count(TestResult.Status.ERROR)));
    }

    private void updateButtons() {
        boolean running = worker != null;
        run.setEnabled(!running);
        differential.setEnabled(!running);
        importCsv.setEnabled(!running);
        cancel.setEnabled(running);
        TestResult selected = selectedResult();
        replay.setEnabled(!running && selected != null);
        locate.setEnabled(!running && selected != null && selected.getProblematicSignal() != null);
        exportScript.setEnabled(!running && suite != null);
        export.setEnabled(!running && report != null && !rows.isEmpty());
    }

    private void showError(Exception exception) {
        Throwable cause = exception instanceof java.util.concurrent.ExecutionException
                ? exception.getCause() : exception;
        JOptionPane.showMessageDialog(this, cause.getMessage(),
                Messages.get("dialog.error"), JOptionPane.ERROR_MESSAGE);
    }

    private String csv(String value) {
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private final class TestWorker extends SwingWorker<TestReport, TestResult> {
        private final TestSuite testSuite;
        private final CircuitState isolatedState;
        private final CircuitReference reference;

        TestWorker(TestSuite testSuite, CircuitState isolatedState, CircuitReference reference) {
            this.testSuite = testSuite;
            this.isolatedState = isolatedState;
            this.reference = reference;
        }

        @Override
        protected TestReport doInBackground() throws Exception {
            logisim.learner.testing.ScriptExecutor.ProgressListener listener = (result, total) -> {
                publish(result);
                setProgress((result.getIndex() + 1) * 100 / Math.max(1, total));
            };
            if (reference == null) {
                return new TestRunner(isolatedState).run(testSuite, listener);
            }
            try {
                return new DifferentialTestRunner(new TestRunner(isolatedState), reference)
                        .run(testSuite, listener);
            } finally {
                reference.close();
            }
        }

        @Override
        protected void process(List<TestResult> chunks) {
            if (worker != this) {
                return;
            }
            for (TestResult result : chunks) {
                addResult(result);
            }
        }

        @Override
        protected void done() {
            if (worker != this) {
                return;
            }
            try {
                report = get();
                showSummary();
            } catch (CancellationException exception) {
                status.setText(Messages.get("test.cancelled"));
            } catch (Exception exception) {
                showError(exception);
            } finally {
                worker = null;
                updateButtons();
            }
        }
    }
}
