package io.cresco.logger;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

/**
 * Bootstraps Pax Logging 2.x (log4j2 backend, exporting SLF4J 2.x) and configures it via
 * Configuration Admin (PID {@code org.ops4j.pax.logging}) using the log4j2 properties format.
 *
 * Originally based on the Pax Logging sample activator by Alin Dreghiciu; migrated from
 * Pax Logging 1.10.4 / log4j1 (SLF4J 1.7) to Pax Logging 2.2.8 / log4j2 (SLF4J 2.0).
 */
public final class Activator implements BundleActivator {

    private List<String> levelList;
    private Bundle loggerBackend; // pax-logging-log4j2
    private Bundle loggerAPI;     // pax-logging-api
    private Bundle osgiService;   // org.osgi.service.cm

    public void start(final BundleContext bundleContext) throws Exception {

        levelList = new ArrayList<>();
        for (String l : new String[]{"OFF", "FATAL", "ERROR", "WARN", "INFO", "DEBUG", "TRACE", "ALL"}) {
            levelList.add(l);
        }

        // Seed the log config before the backend starts so log4j2 reads it on activation.
        updateConfiguration(bundleContext, "%d{dd MMM yyyy HH:mm:ss,SSS} %5p [%t] - %m%n");

        osgiService = installInternalBundleJars(bundleContext, "org.osgi.service.cm-1.6.0.jar");
        osgiService.start();

        loggerAPI = installInternalBundleJars(bundleContext, "pax-logging-api-2.2.8.jar");
        loggerBackend = installInternalBundleJars(bundleContext, "pax-logging-log4j2-2.2.8.jar");
        loggerAPI.start();
        loggerBackend.start();
    }

    public void stop(final BundleContext bundleContext) throws Exception {
        // Stop the pax bundles; do NOT rewrite the log config here (that would clobber any
        // per-plugin levels set at runtime via PluginAdmin on a bundle refresh/redeploy).
        if (loggerBackend != null) loggerBackend.stop();
        if (loggerAPI != null) loggerAPI.stop();
        if (osgiService != null) osgiService.stop();
    }

    private Bundle installInternalBundleJars(BundleContext context, String bundleName) throws Exception {
        URL bundleURL = getClass().getClassLoader().getResource(bundleName);
        if (bundleURL == null) {
            // Fail the bundle start rather than killing the JVM (the previous code called System.exit).
            throw new IOException("logger: embedded bundle not found on classpath: " + bundleName);
        }
        return context.installBundle(bundleURL.toString(),
                getClass().getClassLoader().getResourceAsStream(bundleName));
    }

    /**
     * Write the Pax Logging 2.x / log4j2 configuration to PID {@code org.ops4j.pax.logging}.
     * Keys use the {@code log4j2.} prefix (log4j2 properties format), as in Karaf's
     * org.ops4j.pax.logging.cfg. Sets a Console + File appender and quiets noisy third-party loggers.
     */
    private void updateConfiguration(BundleContext bundleContext, final String pattern) throws IOException {

        String rootLogLevel = System.getProperty("root_log_level", "INFO").toUpperCase();
        if (!levelList.contains(rootLogLevel)) {
            rootLogLevel = "INFO";
        }

        String logDir;
        String cresco_data_location = System.getProperty("cresco_data_location");
        if (cresco_data_location != null) {
            logDir = Paths.get(cresco_data_location, "cresco-logs").toAbsolutePath().normalize().toString();
        } else {
            logDir = "cresco-data/cresco-logs";
        }
        String logFile = logDir + "/main.log";
        String logFilePattern = logDir + "/main-%d{yyyy-MM-dd}-%i.log.gz";

        Hashtable<String, Object> p = new Hashtable<>();

        // Root logger -> Console + rolling File
        p.put("log4j2.rootLogger.level", rootLogLevel);
        p.put("log4j2.rootLogger.appenderRef.Console.ref", "Console");
        p.put("log4j2.rootLogger.appenderRef.RollingFile.ref", "RollingFile");

        // Console appender
        p.put("log4j2.appender.console.type", "Console");
        p.put("log4j2.appender.console.name", "Console");
        p.put("log4j2.appender.console.layout.type", "PatternLayout");
        p.put("log4j2.appender.console.layout.pattern", pattern);

        // Rolling file appender (size + daily rotation, gzip'd, keep last 10) — standard, bounded on disk
        p.put("log4j2.appender.rolling.type", "RollingFile");
        p.put("log4j2.appender.rolling.name", "RollingFile");
        p.put("log4j2.appender.rolling.fileName", logFile);
        p.put("log4j2.appender.rolling.filePattern", logFilePattern);
        p.put("log4j2.appender.rolling.append", "true");
        p.put("log4j2.appender.rolling.immediateFlush", "true");
        p.put("log4j2.appender.rolling.layout.type", "PatternLayout");
        p.put("log4j2.appender.rolling.layout.pattern", pattern);
        p.put("log4j2.appender.rolling.policies.type", "Policies");
        p.put("log4j2.appender.rolling.policies.size.type", "SizeBasedTriggeringPolicy");
        p.put("log4j2.appender.rolling.policies.size.size", "50MB");
        p.put("log4j2.appender.rolling.policies.time.type", "TimeBasedTriggeringPolicy");
        p.put("log4j2.appender.rolling.policies.time.interval", "1");
        p.put("log4j2.appender.rolling.strategy.type", "DefaultRolloverStrategy");
        p.put("log4j2.appender.rolling.strategy.max", "10");

        // Quiet noisy third-party loggers
        addLogger(p, "felix", "org.apache.felix", "ERROR");
        addLogger(p, "paxlogging", "org.ops4j.pax.logging", "ERROR");
        addLogger(p, "netty", "io.netty", "ERROR");
        addLogger(p, "hibernate", "org.hibernate", "ERROR");
        addLogger(p, "activemq", "org.apache.activemq", "ERROR");
        addLogger(p, "spring", "org.springframework", "ERROR");
        addLogger(p, "xbean", "org.apache.xbean", "ERROR");
        addLogger(p, "camel", "org.apache.camel", "ERROR");
        addLogger(p, "jetty", "org.eclipse.jetty", "ERROR");
        addLogger(p, "aries", "org.apache.aries", "ERROR");
        addLogger(p, "oshi", "oshi", "ERROR");
        addLogger(p, "cxf", "org.apache.cxf", "ERROR");
        addLogger(p, "osgi", "org.osgi", "OFF");

        ConfigurationAdmin configAdmin = getConfigurationAdmin(bundleContext);
        Configuration loggerConfig = configAdmin.getConfiguration("org.ops4j.pax.logging", null);
        loggerConfig.update(p);
    }

    private void addLogger(Hashtable<String, Object> p, String id, String name, String level) {
        p.put("log4j2.logger." + id + ".name", name);
        p.put("log4j2.logger." + id + ".level", level);
    }

    private ConfigurationAdmin getConfigurationAdmin(final BundleContext bundleContext) {
        final ServiceReference ref = bundleContext.getServiceReference(ConfigurationAdmin.class.getName());
        if (ref == null) {
            throw new IllegalStateException("Cannot find a configuration admin service");
        }
        return (ConfigurationAdmin) bundleContext.getService(ref);
    }

}
