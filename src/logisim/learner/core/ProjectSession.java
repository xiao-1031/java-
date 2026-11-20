package logisim.learner.core;

import com.cburch.logisim.proj.Project;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import logisim.learner.debug.DebugController;
import logisim.learner.debug.WaveformModel;
import logisim.learner.i18n.Messages;
import logisim.learner.ui.LearnerDebugFrame;

public final class ProjectSession {
    private static final Object MENU_KEY = new Object();

    private final Project project;
    private final WaveformModel waveformModel;
    private final DebugController debugController;
    private final JMenu menu;
    private LearnerDebugFrame debugFrame;

    public ProjectSession(Project project) {
        this.project = project;
        this.waveformModel = new WaveformModel();
        this.debugController = new DebugController(project, waveformModel);
        this.menu = createMenu();
        attachMenu();
    }

    private JMenu createMenu() {
        JMenu result = new JMenu(Messages.get("menu.debug"));
        result.add(createItem("menu.open", new Runnable() {
            @Override
            public void run() {
                showDebugger();
            }
        }));
        result.addSeparator();
        result.add(createItem("menu.start", new Runnable() {
            @Override
            public void run() {
                debugController.start();
            }
        }));
        result.add(createItem("menu.pause", new Runnable() {
            @Override
            public void run() {
                debugController.pause();
            }
        }));
        result.add(createItem("menu.step", new Runnable() {
            @Override
            public void run() {
                debugController.step();
            }
        }));
        result.add(createItem("menu.tick", new Runnable() {
            @Override
            public void run() {
                debugController.tickOnce();
            }
        }));
        result.add(createItem("menu.reset", new Runnable() {
            @Override
            public void run() {
                debugController.reset();
            }
        }));
        return result;
    }

    private JMenuItem createItem(String key, final Runnable action) {
        JMenuItem item = new JMenuItem(Messages.get(key));
        item.addActionListener(event -> action.run());
        return item;
    }

    private void attachMenu() {
        JFrame frame = project.getFrame();
        if (frame == null || frame.getJMenuBar() == null) {
            return;
        }
        JMenuBar menuBar = frame.getJMenuBar();
        if (menuBar.getClientProperty(MENU_KEY) != null) {
            return;
        }
        int index = Math.max(0, menuBar.getMenuCount() - 1);
        menuBar.add(menu, index);
        menuBar.putClientProperty(MENU_KEY, this);
        menuBar.revalidate();
    }

    private void showDebugger() {
        if (debugFrame == null) {
            debugFrame = new LearnerDebugFrame(project, debugController, waveformModel);
        }
        debugFrame.setVisible(true);
        debugFrame.toFront();
    }

    public void dispose() {
        debugController.dispose();
        if (debugFrame != null) {
            debugFrame.dispose();
            debugFrame = null;
        }
        JFrame frame = project.getFrame();
        if (frame != null && frame.getJMenuBar() != null) {
            JMenuBar menuBar = frame.getJMenuBar();
            menuBar.remove(menu);
            menuBar.putClientProperty(MENU_KEY, null);
            menuBar.revalidate();
        }
    }
}
