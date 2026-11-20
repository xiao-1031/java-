package logisim.learner.core;

import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.Projects;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import javax.swing.SwingUtilities;

public final class PluginController {
    private static PluginController instance;

    private final Map<Project, ProjectSession> sessions = new WeakHashMap<Project, ProjectSession>();
    private final PropertyChangeListener projectListListener = new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent event) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    synchronizeSessions();
                }
            });
        }
    };

    private PluginController() {
    }

    public static synchronized void install() {
        if (instance != null) {
            return;
        }
        instance = new PluginController();
        Projects.addPropertyChangeListener(Projects.projectListProperty,
                instance.projectListListener);
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                instance.synchronizeSessions();
            }
        });
    }

    private void synchronizeSessions() {
        Set<Project> open = new HashSet<Project>(Projects.getOpenProjects());
        for (Project project : open) {
            if (!sessions.containsKey(project)) {
                sessions.put(project, new ProjectSession(project));
            }
        }
        Iterator<Map.Entry<Project, ProjectSession>> iterator
                = sessions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Project, ProjectSession> entry = iterator.next();
            if (!open.contains(entry.getKey())) {
                entry.getValue().dispose();
                iterator.remove();
            }
        }
    }
}
