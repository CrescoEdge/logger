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
 * Bundle Activator.<br/>
 * Looks up the Configuration Admin service and on activation will configure Pax Logging.
 * On deactivation will unconfigure Pax Logging.
 *
 * @author Alin Dreghiciu (adreghiciu@gmail.com)
 * @since 0.2.2, November 26, 2008
 */
public final class Activator
        implements BundleActivator
{
    private List<String> levelList;
    private Bundle loggerService;
    private Bundle loggerAPI;
    private Bundle osgiService;

    /**
     * {@inheritDoc}
     * Configures Pax Logging via Configuration Admin.
     */
    public void start( final BundleContext bundleContext )
            throws Exception {

        levelList = new ArrayList<>();
        levelList.add("OFF");
        levelList.add("FATAL");
        levelList.add("ERROR");
        levelList.add("WARN");
        levelList.add("INFO");
        levelList.add("DEBUG");
        levelList.add("TRACE");
        levelList.add("ALL");

        updateConfiguration( bundleContext, "%d{dd MMM yyyy HH:mm:ss,SSS} %5p [%t] - %m%n" );

        osgiService = installInternalBundleJars(bundleContext,"org.osgi.service.cm-1.6.0.jar");
        osgiService.start();

        //loggerService = installInternalBundleJars(bundleContext,"pax-logging-service-1.11.17.jar");
        //loggerAPI = installInternalBundleJars(bundleContext,"pax-logging-api-1.11.17.jar");

        loggerService = installInternalBundleJars(bundleContext,"pax-logging-service-1.10.4.jar");
        loggerAPI = installInternalBundleJars(bundleContext,"pax-logging-api-1.10.4.jar");

        //loggerService = installInternalBundleJars(bundleContext,"pax-logging-log4j2-2.2.7.jar");
        //loggerAPI = installInternalBundleJars(bundleContext,"pax-logging-api-2.2.7.jar");

        loggerService.start();
        loggerAPI.start();

    }

    /**
     * {@inheritDoc}
     * UnConfigures Pax Logging via Configuration Admin.
     */
    public void stop( final BundleContext bundleContext )
            throws Exception
    {
        loggerAPI.stop();
        loggerService.stop();
        osgiService.stop();
        updateConfiguration( bundleContext, "%-4r [%t] %-5p %c %x - %m%n" );
    }

    private Bundle installInternalBundleJars(BundleContext context, String bundleName) {

        Bundle installedBundle = null;
        try {
            URL bundleURL = getClass().getClassLoader().getResource(bundleName);
            if(bundleURL != null) {

                String bundlePath = bundleURL.getPath();
                installedBundle = context.installBundle(bundlePath,
                        getClass().getClassLoader().getResourceAsStream(bundleName));


            } else {
                System.out.println("core installInternalBundleJars() Bundle = null for " + bundleName);
            }
        } catch(Exception ex) {
            ex.printStackTrace();
        }

        if(installedBundle == null) {
            System.out.println("core installInternalBundleJars () Failed to load bundle " + bundleName + " exiting!");
            System.exit(0);
        }

        return installedBundle;
    }

    /**
     * Updates Pax Logging configuration to a specifid conversion pattern.
     *
     * @param bundleContext bundle context
     * @param pattern       layout conversion pattern
     *
     * @throws IOException - Re-thrown
     */
    private void updateConfiguration2( BundleContext bundleContext, final String pattern ) throws IOException {

        String rootLogLevel = System.getProperty("root_log_level","INFO");
        rootLogLevel = rootLogLevel.toUpperCase();
        if(!levelList.contains(rootLogLevel)) {
            rootLogLevel = "INFO";
        }

        List<String> configList = new ArrayList<>();
        configList.add("org.osgi.service.log.LogService");
        configList.add("org.osgi.service.log.LoggerFactory");
        configList.add("org.ops4j.pax.logging.PaxLoggingService");
        configList.add("org.ops4j.pax.logging");

        for(String configName : configList) {

            ConfigurationAdmin configAdmin = getConfigurationAdmin( bundleContext );
            Configuration loggerConfig = configAdmin.getConfiguration( configName, null );

            Hashtable<String, Object> log4jProps = new Hashtable<>();
            //log4jProps.put( "log4j.rootLogger", rootLogLevel + ", CONSOLE, FILE, APP" );
            log4jProps.put( "log4j.rootLogger", rootLogLevel + ", CONSOLE, FILE" );

            //log4jProps.put( "log4j.appender.APP","org.ops4j.pax.logging.extender.ZipRollingFileAppender");
            log4jProps.put( "log4j.appender.CONSOLE", "org.apache.log4j.ConsoleAppender" );
            log4jProps.put( "log4j.appender.CONSOLE.layout", "org.apache.log4j.PatternLayout" );
            log4jProps.put( "log4j.appender.CONSOLE.layout.ConversionPattern", pattern );

            log4jProps.put( "log4j.appender.FILE","org.apache.log4j.FileAppender");

            String cresco_data_location = System.getProperty("cresco_data_location");
            if(cresco_data_location != null) {
                Path path = Paths.get(cresco_data_location, "cresco-logs","main.log");
                log4jProps.put( "log4j.appender.FILE.File",path.toAbsolutePath().normalize().toString());
            } else {
                log4jProps.put( "log4j.appender.FILE.File","cresco-data/cresco-logs/main.log");
            }

            log4jProps.put( "log4j.appender.FILE.ImmediateFlush","true");
            //log4jProps.put( "log4j.appender.FILE.Threshold","ALL");
            log4jProps.put( "log4j.appender.FILE.Append","true");
            log4jProps.put( "log4j.appender.FILE.layout","org.apache.log4j.PatternLayout");
            log4jProps.put( "log4j.appender.FILE.layout.conversionPattern", pattern);

            log4jProps.put( "log4j.category.org.apache.felix.configadmin","ERROR");
            log4jProps.put( "log4j.category.org.apache.felix","ERROR");
            log4jProps.put( "log4j.category.org.ops4j.pax","ERROR");
            //log4jProps.put( "log4j.category.com.orientechnologies","ERROR");
            log4jProps.put( "log4j.category.io.netty","ERROR");
            log4jProps.put( "log4j.category.org.hibernate","ERROR");
            log4jProps.put( "log4j.category.org.apache.activemq","ERROR");
            //log4jProps.put( "log4j.category.org.apache.aries","ERROR");
            //log4jProps.put( "log4j.category.org.apache.cxf","ERROR");

            log4jProps.put( "log4j.logger.org.apache.activemq.spring","ERROR");
            log4jProps.put( "log4j.logger.org.apache.activemq.web.handler","ERROR");
            log4jProps.put( "log4j.logger.org.springframework","ERROR");
            log4jProps.put( "log4j.logger.org.apache.xbean","ERROR");
            log4jProps.put( "log4j.logger.org.apache.camel","ERROR");
            log4jProps.put( "log4j.logger.org.eclipse.jetty","ERROR");
            log4jProps.put( "log4j.logger.org.apache.activemq.broker","ERROR");
            log4jProps.put( "log4j.logger.org.apache.activemq","ERROR");
            log4jProps.put( "log4j.logger.org.apache.aries","ERROR");
            //log4jProps.put( "log4j.logger.org.apache.aries.jax.rs.whiteboard","ERROR");
            log4jProps.put( "log4j.logger.oshi.*","ERROR");
            log4jProps.put( "log4j.logger.org.apache.cxf","ERROR");
            log4jProps.put( "log4j.logger.org.osgi","OFF");
            log4jProps.put( "log4j.logger.osgi","OFF");
            log4jProps.put( "log4j.logger.org.ops4j.pax.logging","OFF");

            loggerConfig.update( log4jProps );
        }

    }

    private void updateConfiguration( BundleContext bundleContext, final String pattern ) throws IOException {

        String rootLogLevel = System.getProperty("root_log_level","INFO");
        rootLogLevel = rootLogLevel.toUpperCase();
        if(!levelList.contains(rootLogLevel)) {
            rootLogLevel = "INFO";
        }

        List<String> configList = new ArrayList<>();
        configList.add("org.ops4j.pax.logging");
        /*
        configList.add("org.osgi.service.log.LogService");
        configList.add("org.osgi.service.log.LoggerFactory");
        configList.add("org.ops4j.pax.logging.PaxLoggingService");
        configList.add("org.ops4j.pax.logging.pax-logging-log4j2");
        configList.add("org.ops4j.pax.logging.pax-logging-service");
        configList.add("org.osgi.service.log.LogService");
        configList.add("org.knopflerfish.service.log.LogService");
        configList.add("org.ops4j.pax.logging.PaxLoggingService");
        configList.add("org.osgi.service.cm.ManagedService");
        configList.add("org.osgi.service.log.LogReaderService");

         */


        //configList.clear();

        for(String configName : configList) {


            ConfigurationAdmin configAdmin = getConfigurationAdmin( bundleContext );
            Configuration loggerConfig = configAdmin.getConfiguration( configName, null );

            Hashtable<String, Object> log4jProps = new Hashtable<>();

            /*
            log4jProps.put( "rootLogger.level", rootLogLevel);
            log4jProps.put( "property.filename", "cody.log");


            log4jProps.put( "appenders", "R, console");
            log4jProps.put( "appender.console.type", "STDOUT");
            log4jProps.put( "appender.console.layout.type", "PatternLayout");
            log4jProps.put( "appender.console.layout.pattern", "%d %5p [%t] (%F:%L) - %m%n");

            log4jProps.put( "appender.R.type","RollingFile");
            log4jProps.put( "appender.R.name","File");
            log4jProps.put( "appender.R.fileName","cody.log");
            log4jProps.put( "appender.R.filePattern","aA");
            log4jProps.put( "appender.R.layout.type", "PatternLayout");
            log4jProps.put( "appender.R.layout.pattern", "%d{yyyy-MM-dd HH:mm:ss} %c{1} [%p] %m%n");
            log4jProps.put( "appender.R.policies.type", "Policies");
            log4jProps.put( "appender.R.policies.time.type", "TimeBasedTriggeringPolicy");
            log4jProps.put( "appender.R.policies.time.interval", "1");

            log4jProps.put( "rootLogger.appenderRefs", "R, console");
            log4jProps.put( "rootLogger.appenderRef.console.ref", "STDOUT");
            log4jProps.put( "rootLogger.appenderRef.R.ref", "File");


             */
            log4jProps.put( "log4j.rootLogger", rootLogLevel + ", CONSOLE, FILE" );
            log4jProps.put( "log4j.appender.CONSOLE", "org.apache.log4j.ConsoleAppender" );
            log4jProps.put( "log4j.appender.CONSOLE.layout", "org.apache.log4j.PatternLayout" );
            log4jProps.put( "log4j.appender.CONSOLE.layout.ConversionPattern", pattern );

            log4jProps.put( "log4j.appender.FILE","org.apache.log4j.FileAppender");

            String cresco_data_location = System.getProperty("cresco_data_location");
            if(cresco_data_location != null) {
                Path path = Paths.get(cresco_data_location, "cresco-logs","main.log");
                log4jProps.put( "log4j.appender.FILE.File",path.toAbsolutePath().normalize().toString());
            } else {
                log4jProps.put( "log4j.appender.FILE.File","cresco-data/cresco-logs/main.log");
            }

            log4jProps.put( "log4j.appender.FILE.ImmediateFlush","true");
            //log4jProps.put( "log4j.appender.FILE.Threshold","ALL");
            log4jProps.put( "log4j.appender.FILE.Append","true");
            log4jProps.put( "log4j.appender.FILE.layout","org.apache.log4j.PatternLayout");
            log4jProps.put( "log4j.appender.FILE.layout.conversionPattern", pattern);

            log4jProps.put( "log4j.category.org.apache.felix","ERROR");
            log4jProps.put( "log4j.category.org.ops4j.pax","ERROR");
            //log4jProps.put( "log4j.category.com.orientechnologies","ERROR");
            log4jProps.put( "log4j.category.io.netty","ERROR");
            log4jProps.put( "log4j.category.org.hibernate","ERROR");
            log4jProps.put( "log4j.category.org.apache.activemq","ERROR");
            //log4jProps.put( "log4j.category.org.apache.aries","ERROR");
            //log4jProps.put( "log4j.category.org.apache.cxf","ERROR");

            log4jProps.put( "log4j.logger.org.apache.activemq.spring","ERROR");
            log4jProps.put( "log4j.logger.org.apache.activemq.web.handler","ERROR");
            log4jProps.put( "log4j.logger.org.springframework","ERROR");
            log4jProps.put( "log4j.logger.org.apache.xbean","ERROR");
            log4jProps.put( "log4j.logger.org.apache.camel","ERROR");
            log4jProps.put( "log4j.logger.org.eclipse.jetty","ERROR");
            log4jProps.put( "log4j.logger.org.apache.activemq.broker","ERROR");
            log4jProps.put( "log4j.logger.org.apache.activemq","ERROR");
            log4jProps.put( "log4j.logger.org.apache.aries","ERROR");
            //log4jProps.put( "log4j.logger.org.apache.aries.jax.rs.whiteboard","ERROR");
            log4jProps.put( "log4j.logger.oshi.*","ERROR");
            log4jProps.put( "log4j.logger.org.apache.cxf","ERROR");
            log4jProps.put( "log4j.logger.org.osgi","OFF");
            log4jProps.put( "log4j.logger.osgi","OFF");
            log4jProps.put( "log4j.logger.org.ops4j.pax.logging","OFF");
            log4jProps.put( "org.ops4j.pax.logging.pax-logging-service","OFF");

            loggerConfig.update( log4jProps );
        }

    }


    /**
     * Gets Configuration Admin service from service registry.
     *
     * @param bundleContext bundle context
     *
     * @return configuration admin service
     *
     * @throws IllegalStateException - If no Configuration Admin service is available
     */
    private ConfigurationAdmin getConfigurationAdmin( final BundleContext bundleContext )
    {
        final ServiceReference ref = bundleContext.getServiceReference( ConfigurationAdmin.class.getName() );
        if( ref == null )
        {
            throw new IllegalStateException( "Cannot find a configuration admin service" );
        }
        return (ConfigurationAdmin) bundleContext.getService( ref );
    }


}
