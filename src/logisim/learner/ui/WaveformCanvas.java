package logisim.learner.ui;

import com.cburch.logisim.data.Value;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import javax.swing.JPanel;
import logisim.learner.debug.SignalDescriptor;
import logisim.learner.debug.SignalSample;
import logisim.learner.debug.WaveformAnomalyDetector;
import logisim.learner.debug.WaveformModel;
import logisim.learner.debug.WaveformSelection;
import logisim.learner.i18n.Messages;

public final class WaveformCanvas extends JPanel {
    private static final int LABEL_WIDTH = 190;
    private static final int ROW_HEIGHT = 64;
    private static final int TOP_MARGIN = 34;
    private static final int RIGHT_MARGIN = 40;

    private WaveformModel.Snapshot snapshot;
    private WaveformSelection selection;
    private Consumer<WaveformSelection> selectionListener = value -> { };
    private int pixelsPerStep = 24;

    public WaveformCanvas(WaveformModel.Snapshot snapshot) {
        this.snapshot = snapshot;
        setBackground(Color.WHITE);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                selectAt(event.getX(), event.getY());
            }
        });
    }

    public void setSelectionListener(Consumer<WaveformSelection> selectionListener) {
        this.selectionListener = selectionListener == null ? value -> { } : selectionListener;
    }

    public WaveformSelection getSelection() {
        return selection;
    }

    public void clearSelection() {
        selection = null;
        repaint();
    }

    public void setSnapshot(WaveformModel.Snapshot snapshot) {
        this.snapshot = snapshot;
        if (selection != null && (!snapshot.getSignals().contains(selection.getSignal())
                || selection.getSample().getSequence() < firstSequence())) {
            selection = null;
        }
        updatePreferredSize();
        revalidate();
        repaint();
    }

    public void setPixelsPerStep(int pixelsPerStep) {
        this.pixelsPerStep = pixelsPerStep;
        updatePreferredSize();
        revalidate();
        repaint();
    }

    private void updatePreferredSize() {
        long first = firstSequence();
        long steps = Math.max(8, snapshot.getSequence() - first + 2);
        int width = (int) Math.min(Integer.MAX_VALUE - 1,
                LABEL_WIDTH + steps * pixelsPerStep + RIGHT_MARGIN);
        int height = Math.max(220, TOP_MARGIN + snapshot.getSignals().size() * ROW_HEIGHT + 36);
        setPreferredSize(new Dimension(width, height));
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (!snapshot.hasSamples()) {
            g.setColor(Color.GRAY);
            g.drawString(Messages.get("waveform.empty"), 24, 40);
            g.dispose();
            return;
        }
        long first = firstSequence();
        long last = Math.max(first + 1, snapshot.getSequence() + 1);
        paintGrid(g, first, last);
        paintTickMarkers(g, first);
        int row = 0;
        for (SignalDescriptor signal : snapshot.getSignals()) {
            paintSignal(g, row++, signal, snapshot.getHistory(signal), first);
        }
        paintSelection(g, first);
        g.dispose();
    }

    private void paintGrid(Graphics2D g, long first, long last) {
        int height = TOP_MARGIN + snapshot.getSignals().size() * ROW_HEIGHT;
        int labelEvery = Math.max(1, (80 + pixelsPerStep - 1) / pixelsPerStep);
        g.setFont(getFont().deriveFont(10f));
        for (long sequence = first; sequence <= last; sequence++) {
            int x = x(sequence, first);
            g.setColor(new Color(238, 238, 238));
            g.drawLine(x, TOP_MARGIN - 8, x, height);
            if ((sequence - first) % labelEvery == 0) {
                g.setColor(Color.GRAY);
                g.drawString(Long.toString(sequence), x + 2, 16);
            }
        }
        g.setColor(new Color(215, 215, 215));
        g.drawLine(0, TOP_MARGIN - 8, getWidth(), TOP_MARGIN - 8);
    }

    private void paintTickMarkers(Graphics2D g, long first) {
        Map<Long, Long> ticks = new TreeMap<Long, Long>();
        for (SignalDescriptor signal : snapshot.getSignals()) {
            SignalSample previous = null;
            for (SignalSample sample : snapshot.getHistory(signal)) {
                if (previous != null && sample.getTick() != previous.getTick()) {
                    ticks.put(sample.getTick(), sample.getSequence());
                }
                previous = sample;
            }
        }
        int height = TOP_MARGIN + snapshot.getSignals().size() * ROW_HEIGHT;
        g.setColor(new Color(120, 155, 205));
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                10f, new float[] {4f, 4f}, 0f));
        for (Map.Entry<Long, Long> tick : ticks.entrySet()) {
            int x = x(tick.getValue(), first);
            g.drawLine(x, 22, x, height);
            g.drawString("t" + tick.getKey(), x + 3, 30);
        }
        g.setStroke(new BasicStroke());
    }

    private void paintSignal(Graphics2D g, int row, SignalDescriptor signal,
            List<SignalSample> history, long first) {
        int y = TOP_MARGIN + row * ROW_HEIGHT;
        g.setColor(new Color(35, 35, 35));
        g.setFont(getFont().deriveFont(12f));
        g.drawString(signal.getName(), 10, y + 19);
        g.setColor(Color.GRAY);
        g.setFont(getFont().deriveFont(10f));
        g.drawString(signal.getComponent().getFactory().getDisplayName()
                + " " + signal.getComponent().getLocation(), 10, y + 36);
        g.setColor(new Color(230, 230, 230));
        g.drawLine(0, y + ROW_HEIGHT, getWidth(), y + ROW_HEIGHT);
        if (history.isEmpty()) {
            return;
        }
        for (int i = 0; i < history.size(); i++) {
            SignalSample sample = history.get(i);
            int left = x(sample.getSequence(), first);
            int right = i + 1 < history.size()
                    ? x(history.get(i + 1).getSequence(), first) : left + pixelsPerStep;
            paintSegment(g, signal, sample.getValue(), left, right, y);
            if (i + 1 < history.size()) {
                Value next = history.get(i + 1).getValue();
                if (!sample.getValue().equals(next)) {
                    g.setColor(color(next));
                    g.drawLine(right, y + 10, right, y + 45);
                }
            }
        }
        g.setColor(new Color(185, 45, 150));
        for (int i = 1; i + 1 < history.size(); i++) {
            if (WaveformAnomalyDetector.isShortPulse(history, i)) {
                int center = x(history.get(i).getSequence(), first);
                Polygon marker = new Polygon(
                        new int[] {center, center + 5, center, center - 5},
                        new int[] {y + 47, y + 52, y + 57, y + 52}, 4);
                g.fillPolygon(marker);
            }
        }
    }

    private void paintSelection(Graphics2D g, long first) {
        if (selection == null) {
            return;
        }
        int row = snapshot.getSignals().indexOf(selection.getSignal());
        if (row < 0) {
            return;
        }
        int y = TOP_MARGIN + row * ROW_HEIGHT;
        int selectedX = x(selection.getSample().getSequence(), first);
        g.setColor(new Color(185, 45, 150));
        g.setStroke(new BasicStroke(2f));
        g.drawLine(selectedX, y + 4, selectedX, y + ROW_HEIGHT - 4);
        g.drawRect(1, y + 1, getWidth() - 2, ROW_HEIGHT - 2);
    }

    private void selectAt(int mouseX, int mouseY) {
        if (mouseY < TOP_MARGIN) {
            return;
        }
        int row = (mouseY - TOP_MARGIN) / ROW_HEIGHT;
        int localY = (mouseY - TOP_MARGIN) % ROW_HEIGHT;
        if (row < 0 || row >= snapshot.getSignals().size() || localY < 4 || localY > 58) {
            return;
        }
        SignalDescriptor signal = snapshot.getSignals().get(row);
        List<SignalSample> history = snapshot.getHistory(signal);
        long first = firstSequence();
        int best = -1;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < history.size(); i++) {
            int left = x(history.get(i).getSequence(), first);
            int right = i + 1 < history.size()
                    ? x(history.get(i + 1).getSequence(), first) : left + pixelsPerStep;
            if (mouseX < left - 4 || mouseX > right + 4) {
                continue;
            }
            int candidate = i;
            int distance = Math.abs(mouseX - left);
            if (i + 1 < history.size() && Math.abs(mouseX - right) <= 5
                    && !history.get(i).getValue().equals(history.get(i + 1).getValue())) {
                candidate = i + 1;
                distance = Math.abs(mouseX - right);
            }
            if (distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        if (best >= 0) {
            selection = new WaveformSelection(signal, history, best);
            repaint();
            selectionListener.accept(selection);
        }
    }

    private void paintSegment(Graphics2D g, SignalDescriptor signal, Value value,
            int left, int right, int y) {
        g.setColor(color(value));
        g.setStroke(new BasicStroke(2f));
        if (value.getWidth() == 1 && value.isFullyDefined()) {
            int lineY = value == Value.TRUE ? y + 12 : y + 43;
            g.drawLine(left, lineY, right, lineY);
            return;
        }
        if (value.getWidth() == 1) {
            g.drawLine(left, y + 28, right, y + 28);
            if (right - left > 12) {
                g.drawString(value.isErrorValue() ? "E" : "x", left + 4, y + 24);
            }
            return;
        }
        g.drawLine(left, y + 17, right, y + 17);
        g.drawLine(left, y + 40, right, y + 40);
        g.drawLine(left + 3, y + 17, left + 7, y + 40);
        String text = value.toDisplayString(signal.getRadix());
        FontMetrics metrics = g.getFontMetrics();
        if (right - left > metrics.stringWidth(text) + 10) {
            g.drawString(text, left + (right - left - metrics.stringWidth(text)) / 2, y + 33);
        }
    }

    private Color color(Value value) {
        if (value.isErrorValue()) {
            return new Color(200, 45, 45);
        }
        if (!value.isFullyDefined()) {
            return new Color(220, 135, 20);
        }
        return new Color(0, 125, 90);
    }

    private long firstSequence() {
        long first = Long.MAX_VALUE;
        for (SignalDescriptor signal : snapshot.getSignals()) {
            List<SignalSample> history = snapshot.getHistory(signal);
            if (!history.isEmpty()) {
                first = Math.min(first, history.get(0).getSequence());
            }
        }
        return first == Long.MAX_VALUE ? 0 : first;
    }

    private int x(long sequence, long first) {
        return (int) (LABEL_WIDTH + (sequence - first) * pixelsPerStep);
    }
}
