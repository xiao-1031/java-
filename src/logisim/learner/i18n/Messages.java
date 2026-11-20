package logisim.learner.i18n;

import com.cburch.logisim.util.LocaleManager;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

public final class Messages {
    private static final String BUNDLE_NAME = "logisim.learner.i18n.messages";

    private Messages() {
    }

    public static String get(String key) {
        return bundle().getString(key);
    }

    public static String format(String key, Object... arguments) {
        return MessageFormat.format(get(key), arguments);
    }

    private static ResourceBundle bundle() {
        Locale locale = LocaleManager.getLocale();
        return ResourceBundle.getBundle(BUNDLE_NAME, locale, new Utf8Control());
    }

    private static final class Utf8Control extends ResourceBundle.Control {
        @Override
        public ResourceBundle newBundle(String baseName, Locale locale, String format,
                ClassLoader loader, boolean reload) throws IOException {
            if (!"java.properties".equals(format)) {
                return null;
            }
            String bundleName = toBundleName(baseName, locale);
            String resourceName = toResourceName(bundleName, "properties");
            InputStream stream = null;
            if (reload) {
                URL url = loader.getResource(resourceName);
                if (url != null) {
                    URLConnection connection = url.openConnection();
                    if (connection != null) {
                        connection.setUseCaches(false);
                        stream = connection.getInputStream();
                    }
                }
            } else {
                stream = loader.getResourceAsStream(resourceName);
            }
            if (stream == null) {
                return null;
            }
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return new PropertyResourceBundle(reader);
            }
        }
    }
}
