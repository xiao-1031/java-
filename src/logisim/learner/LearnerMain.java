package logisim.learner;

import com.cburch.logisim.Main;
import java.util.Locale;
import logisim.learner.core.PluginController;

public final class LearnerMain {
    private static final String SUPPORTED_VERSION = "2.7.1";

    private LearnerMain() {
    }

    public static void main(String[] args) {
        if (!SUPPORTED_VERSION.equals(Main.VERSION_NAME)) {
            throw new IllegalStateException("Logisim Learner requires Logisim 2.7.1, found "
                    + Main.VERSION_NAME);
        }
        if (!hasLocaleArgument(args)) {
            Locale.setDefault(Locale.SIMPLIFIED_CHINESE);
        }
        PluginController.install();
        Main.main(args);
    }

    private static boolean hasLocaleArgument(String[] args) {
        for (String arg : args) {
            if ("-locale".equals(arg)) {
                return true;
            }
        }
        return false;
    }
}
