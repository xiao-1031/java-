package logisim.learner.ui;

import com.cburch.logisim.proj.Project;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.Collections;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import logisim.learner.analysis.ConnectivityAnalyzer;
import logisim.learner.analysis.RaceAnalyzer;
import logisim.learner.debug.DebugController;
import logisim.learner.debug.WaveformModel;
import logisim.learner.i18n.Messages;

public final class LearnerDebugFrame extends JFrame {
    private final Project project;
    private final DebugController controller;
    private final WaveformModel waveformModel;
    private final WaveformPanel waveformPanel;
    private final AutoTestPanel autoTestPanel;
    private final DiagnosticsPanel healthPanel;
    private final DiagnosticsPanel riskPanel;
    private final JLabel circuitLabel = new JLabel();
    private final JLabel runningLabel = new JLabel();
    private final JLabel clockLabel = new JLabel();
    private final JLabel oscillationLabel = new JLabel();
    private String lastCircuitName;

    private final DebugController.Listener controllerListener
            = new DebugController.Listener() {
        @Override
        public void controllerChanged() {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    refreshController();
                }
            });
        }
    };
    private final WaveformModel.Listener waveformListener
            = new WaveformModel.Listener() {
        @Override
        public void waveformChanged() {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    if (!waveformPanel.selectedSignalsMatchSnapshot()) {
                        waveformPanel.refreshSelected();
                    } else {
                        waveformPanel.refreshSnapshot();
                    }
                }
            });
        }
    };

    public LearnerDebugFrame(Project project, DebugController controller,
            WaveformModel waveformModel) {
        this.project = project;
        this.controller = controller;
        this.waveformModel = waveformModel;
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        setLayout(new BorderLayout());
        add(createControls(), BorderLayout.NORTH);
        CircuitLocator locator = new CircuitLocator(project);
        waveformPanel = new WaveformPanel(controller, waveformModel, locator);
        autoTestPanel = new AutoTestPanel(controller, waveformModel, locator);
        healthPanel = new DiagnosticsPanel(controller,
                Collections.singletonList(new ConnectivityAnalyzer()), false, locator);
        riskPanel = new DiagnosticsPanel(controller,
                Collections.singletonList(new RaceAnalyzer()), true, locator);
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab(Messages.get("tab.waveform"), waveformPanel);
        tabs.addTab(Messages.get("tab.autotest"), autoTestPanel);
        tabs.addTab(Messages.get("tab.health"), healthPanel);
        tabs.addTab(Messages.get("tab.risk"), riskPanel);
        autoTestPanel.setOnReplay(() -> tabs.setSelectedComponent(waveformPanel));
        add(tabs, BorderLayout.CENTER);
        controller.addListener(controllerListener);
        waveformModel.addListener(waveformListener);
        setSize(1120, 700);
        setLocationRelativeTo(project.getFrame());
        refreshController();
    }

    private JPanel createControls() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.add(button("control.start", new Runnable() {
            @Override
            public void run() {
                controller.start();
            }
        }));
        panel.add(button("control.pause", new Runnable() {
            @Override
            public void run() {
                controller.pause();
            }
        }));
        panel.add(button("control.step", new Runnable() {
            @Override
            public void run() {
                controller.step();
            }
        }));
        panel.add(button("control.tick", new Runnable() {
            @Override
            public void run() {
                controller.tickOnce();
            }
        }));
        panel.add(button("control.reset", new Runnable() {
            @Override
            public void run() {
                controller.reset();
            }
        }));
        panel.add(circuitLabel);
        panel.add(runningLabel);
        panel.add(clockLabel);
        panel.add(oscillationLabel);
        return panel;
    }

    private JButton button(String key, final Runnable action) {
        JButton button = new JButton(Messages.get(key));
        button.addActionListener(event -> action.run());
        return button;
    }

    private void refreshController() {
        String circuitName = controller.getCurrentCircuitName();
        setTitle(Messages.format("frame.title", project.getLogisimFile().getDisplayName()));
        circuitLabel.setText(Messages.format("status.circuit", circuitName));
        runningLabel.setText(Messages.get(controller.isRunning()
                ? "status.running" : "status.paused"));
        clockLabel.setText(Messages.get(controller.isTicking()
                ? "status.ticking" : "status.notTicking"));
        oscillationLabel.setText(Messages.get(controller.isOscillating()
                ? "status.oscillating" : "status.normal"));
        if (!circuitName.equals(lastCircuitName)) {
            lastCircuitName = circuitName;
            waveformPanel.refreshAvailable();
            waveformPanel.refreshSelected();
            autoTestPanel.refreshCircuit();
            healthPanel.clear();
            riskPanel.clear();
        }
    }

    @Override
    public void dispose() {
        autoTestPanel.dispose();
        controller.removeListener(controllerListener);
        waveformModel.removeListener(waveformListener);
        super.dispose();
    }
}
