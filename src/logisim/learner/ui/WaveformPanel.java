package logisim.learner.ui;

import com.cburch.logisim.data.Value;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Window;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import logisim.learner.debug.DebugController;
import logisim.learner.debug.SignalDescriptor;
import logisim.learner.debug.SignalInsightService;
import logisim.learner.debug.SignalSample;
import logisim.learner.debug.WaveformModel;
import logisim.learner.i18n.Messages;

public final class WaveformPanel extends JPanel {
    private final DebugController controller;
    private final WaveformModel model;
    private final SignalInsightService insightService = new SignalInsightService();
    private final CircuitLocator locator;
    private final DefaultListModel<SignalDescriptor> availableModel
            = new DefaultListModel<SignalDescriptor>();
    private final DefaultListModel<SignalDescriptor> selectedModel
            = new DefaultListModel<SignalDescriptor>();
    private final JList<SignalDescriptor> availableList = new JList<SignalDescriptor>(availableModel);
    private final JList<SignalDescriptor> selectedList = new JList<SignalDescriptor>(selectedModel);
    private final WaveformCanvas canvas;
    private final WaveformInsightPanel insightPanel;
    private final JToggleButton recordButton;

    public WaveformPanel(DebugController controller, WaveformModel model) {
        this(controller, model, new CircuitLocator(controller.getProject()));
    }

    public WaveformPanel(DebugController controller, WaveformModel model, CircuitLocator locator) {
        super(new BorderLayout(6, 6));
        this.controller = controller;
        this.model = model;
        this.locator = locator;
        this.canvas = new WaveformCanvas(model.snapshot());
        this.insightPanel = new WaveformInsightPanel(locator);
        this.recordButton = new JToggleButton(Messages.get("control.record"));
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        add(createToolbar(), BorderLayout.NORTH);
        add(createSignalLists(), BorderLayout.WEST);
        JPanel center = new JPanel(new BorderLayout());
        center.add(new JScrollPane(canvas), BorderLayout.CENTER);
        center.add(insightPanel, BorderLayout.SOUTH);
        add(center, BorderLayout.CENTER);
        JPanel notes = new JPanel(new BorderLayout());
        notes.add(new JLabel(Messages.get("waveform.axis")), BorderLayout.NORTH);
        notes.add(new JLabel(Messages.get("waveform.limit")), BorderLayout.SOUTH);
        add(notes, BorderLayout.SOUTH);
        canvas.setSelectionListener(selection -> insightPanel.setInsight(
                insightService.inspect(controller.getCurrentState(), selection)));
        refreshAvailable();
        refreshSelected();
    }

    private JPanel createToolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        recordButton.setSelected(model.isRecording());
        recordButton.addActionListener(event -> {
            model.setRecording(recordButton.isSelected());
            if (recordButton.isSelected()) {
                model.capture(controller.getCurrentState(), true);
            }
        });
        toolbar.add(recordButton);
        toolbar.add(button("control.clear", event -> clearWaveform()));
        toolbar.add(button("control.rescan", event -> refreshAvailable()));
        toolbar.add(button("control.export", event -> exportCsv()));
        JSpinner zoom = new JSpinner(new SpinnerNumberModel(24, 8, 72, 4));
        zoom.setToolTipText(Messages.get("waveform.zoom"));
        zoom.addChangeListener(event -> canvas.setPixelsPerStep((Integer) zoom.getValue()));
        toolbar.add(new JLabel(Messages.get("waveform.zoom")));
        toolbar.add(zoom);
        return toolbar;
    }

    private JSplitPane createSignalLists() {
        availableList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        selectedList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        JPanel available = listPanel("signal.available", availableList);
        JPanel selected = listPanel("signal.selected", selectedList);
        JPanel availableButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        availableButtons.add(button("signal.add", event -> addSelectedSignals()));
        available.add(availableButtons, BorderLayout.SOUTH);
        JPanel selectedButtons = new JPanel(new BorderLayout());
        selectedButtons.add(button("signal.remove", event -> removeSelectedSignals()), BorderLayout.NORTH);
        JComboBox<String> radix = new JComboBox<String>(new String[] {
            Messages.get("radix.binary"), Messages.get("radix.hex") });
        radix.addActionListener(event -> setRadix(radix.getSelectedIndex() == 0 ? 2 : 16));
        selectedButtons.add(radix, BorderLayout.SOUTH);
        selected.add(selectedButtons, BorderLayout.SOUTH);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, available, selected);
        split.setResizeWeight(0.55);
        split.setPreferredSize(new java.awt.Dimension(255, 450));
        return split;
    }

    private JPanel listPanel(String titleKey, JList<SignalDescriptor> list) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(Messages.get(titleKey)));
        panel.add(new JScrollPane(list), BorderLayout.CENTER);
        return panel;
    }

    private JButton button(String key, java.awt.event.ActionListener listener) {
        JButton button = new JButton(Messages.get(key));
        button.addActionListener(listener);
        return button;
    }

    private void addSelectedSignals() {
        for (SignalDescriptor signal : availableList.getSelectedValuesList()) {
            controller.addSignal(signal);
        }
        refreshSelected();
    }

    private void removeSelectedSignals() {
        for (SignalDescriptor signal : selectedList.getSelectedValuesList()) {
            controller.removeSignal(signal);
        }
        refreshSelected();
    }

    private void setRadix(int radix) {
        for (SignalDescriptor signal : selectedList.getSelectedValuesList()) {
            signal.setRadix(radix);
        }
        selectedList.repaint();
        refreshSnapshot();
    }

    private void clearWaveform() {
        canvas.clearSelection();
        insightPanel.clearInsight();
        locator.clear();
        model.clearSamples();
    }

    public final void refreshAvailable() {
        insightService.invalidate();
        controller.rescanSignals();
        availableModel.clear();
        for (SignalDescriptor signal : controller.getAvailableSignals()) {
            availableModel.addElement(signal);
        }
    }

    public void refreshSelected() {
        selectedModel.clear();
        for (SignalDescriptor signal : model.snapshot().getSignals()) {
            selectedModel.addElement(signal);
        }
        refreshSnapshot();
        if (canvas.getSelection() == null) {
            insightPanel.clearInsight();
            locator.clear();
        }
    }

    public void refreshSnapshot() {
        recordButton.setSelected(model.isRecording());
        canvas.setSnapshot(model.snapshot());
    }

    public boolean selectedSignalsMatchSnapshot() {
        List<SignalDescriptor> signals = model.snapshot().getSignals();
        if (signals.size() != selectedModel.size()) {
            return false;
        }
        for (int i = 0; i < signals.size(); i++) {
            if (signals.get(i) != selectedModel.get(i)) {
                return false;
            }
        }
        return true;
    }

    private void exportCsv() {
        WaveformModel.Snapshot snapshot = model.snapshot();
        Window window = SwingUtilities.getWindowAncestor(this);
        if (!snapshot.hasSamples()) {
            JOptionPane.showMessageDialog(window, Messages.get("export.empty"));
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("waveform.csv"));
        if (chooser.showSaveDialog(window) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        if (file.exists() && JOptionPane.showConfirmDialog(window, file.getAbsolutePath(),
                Messages.get("dialog.error"), JOptionPane.OK_CANCEL_OPTION)
                != JOptionPane.OK_OPTION) {
            return;
        }
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8))) {
            writer.write('\ufeff');
            writer.write("signal,sequence,tick,value,radix");
            writer.newLine();
            for (SignalDescriptor signal : snapshot.getSignals()) {
                for (SignalSample sample : snapshot.getHistory(signal)) {
                    writer.write(csv(signal.getName()) + "," + sample.getSequence() + ","
                            + sample.getTick() + "," + csv(display(sample.getValue(), signal))
                            + "," + signal.getRadix());
                    writer.newLine();
                }
            }
            JOptionPane.showMessageDialog(window,
                    Messages.format("export.success", file.getAbsolutePath()));
        } catch (IOException exception) {
            JOptionPane.showMessageDialog(window,
                    Messages.format("export.failure", exception.getMessage()),
                    Messages.get("dialog.error"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private String display(Value value, SignalDescriptor signal) {
        return value.toDisplayString(signal.getRadix());
    }

    private String csv(String value) {
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
