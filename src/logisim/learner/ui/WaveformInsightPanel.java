package logisim.learner.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import logisim.learner.analysis.ComponentPath;
import logisim.learner.debug.SignalInsight;
import logisim.learner.i18n.Messages;

public final class WaveformInsightPanel extends JPanel {
    private final CircuitLocator locator;
    private final JLabel summary = new JLabel(Messages.get("insight.empty"));
    private final JTextArea explanation = new JTextArea();
    private final JComboBox<ComponentPath> paths = new JComboBox<ComponentPath>();
    private final JButton locate = new JButton(Messages.get("insight.locate"));
    private final JButton locatePath = new JButton(Messages.get("insight.locatePath"));
    private final JButton nextNode = new JButton(Messages.get("insight.nextNode"));
    private final JButton clear = new JButton(Messages.get("insight.clear"));
    private SignalInsight insight;
    private int nodeIndex;

    public WaveformInsightPanel(CircuitLocator locator) {
        super(new BorderLayout(5, 5));
        this.locator = locator;
        setBorder(BorderFactory.createTitledBorder(Messages.get("insight.title")));
        summary.setFont(summary.getFont().deriveFont(java.awt.Font.BOLD));
        add(summary, BorderLayout.NORTH);
        explanation.setEditable(false);
        explanation.setLineWrap(true);
        explanation.setWrapStyleWord(true);
        explanation.setOpaque(false);
        explanation.setRows(5);
        add(new JScrollPane(explanation), BorderLayout.CENTER);
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controls.add(locate);
        controls.add(paths);
        controls.add(locatePath);
        controls.add(nextNode);
        controls.add(clear);
        add(controls, BorderLayout.SOUTH);
        locate.addActionListener(event -> locateSignal());
        locatePath.addActionListener(event -> locateFirstNode());
        nextNode.addActionListener(event -> locateNextNode());
        clear.addActionListener(event -> locator.clear());
        paths.addActionListener(event -> {
            nodeIndex = 0;
            updateButtons();
        });
        updateButtons();
    }

    public void setInsight(SignalInsight insight) {
        this.insight = insight;
        this.nodeIndex = 0;
        summary.setText(insight.getSummary());
        explanation.setText(insight.getExplanation());
        explanation.setCaretPosition(0);
        paths.removeAllItems();
        for (ComponentPath path : insight.getPaths()) {
            paths.addItem(path);
        }
        updateButtons();
    }

    public void clearInsight() {
        insight = null;
        nodeIndex = 0;
        summary.setText(Messages.get("insight.empty"));
        explanation.setText("");
        paths.removeAllItems();
        updateButtons();
    }

    private void locateSignal() {
        if (insight != null) {
            locator.locate(insight.getSelection().getSignal().getComponent(), insight.getSummary());
        }
    }

    private void locateFirstNode() {
        nodeIndex = 0;
        locateCurrentNode();
    }

    private void locateNextNode() {
        if (nodeIndex + 1 < currentPathSize()) {
            nodeIndex++;
            locateCurrentNode();
        }
    }

    private void locateCurrentNode() {
        ComponentPath path = (ComponentPath) paths.getSelectedItem();
        if (path != null && nodeIndex < path.getComponents().size()) {
            locator.locate(path.getComponents().get(nodeIndex), insight.getSummary()
                    + "（路径节点 " + (nodeIndex + 1) + "/" + path.getComponents().size() + "）");
        }
        updateButtons();
    }

    private int currentPathSize() {
        ComponentPath path = (ComponentPath) paths.getSelectedItem();
        return path == null ? 0 : path.getComponents().size();
    }

    private void updateButtons() {
        locate.setEnabled(insight != null);
        boolean hasPaths = insight != null && !insight.getPaths().isEmpty();
        paths.setEnabled(hasPaths);
        locatePath.setEnabled(hasPaths);
        nextNode.setEnabled(hasPaths && nodeIndex + 1 < currentPathSize());
    }
}
