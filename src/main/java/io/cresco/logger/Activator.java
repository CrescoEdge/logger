package io.cresco.logger;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;

import java.io.IOException;
import java.net.URL;
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

        updateConfiguration( bundleContext, "%5p [%t] - %m%n" );

        installInternalBundleJars(bundleContext,"org.osgi.service.cm-1.6.0.jar").start();
        Bundle loggerService = installInternalBundleJars(bundleContext,"pax-logging-service-1.10.1.jar");
        Bundle loggerAPI = installInternalBundleJars(bundleContext,"pax-logging-api-1.10.1.jar");
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
    private void updateConfiguration( BundleContext bundleContext, final String pattern ) throws IOException {

        String rootLogLevel = System.getProperty("root_log_level","INFO");
        rootLogLevel = rootLogLevel.toUpperCase();
        if(!levelList.contains(rootLogLevel)) {
            rootLogLevel = "INFO";
        }

        ConfigurationAdmin configAdmin = getConfigurationAdmin( bundleContext );
        Configuration loggerConfig = configAdmin.getConfiguration( "org.ops4j.pax.logging", null );

        Hashtable<String, Object> log4jProps = new Hashtable<>();
        log4jProps.put( "log4j.rootLogger", rootLogLevel + ", CONSOLE, FILE" );

        log4jProps.put( "log4j.appender.CONSOLE", "org.apache.log4j.ConsoleAppender" );
        log4jProps.put( "log4j.appender.CONSOLE.layout", "org.apache.log4j.PatternLayout" );
        log4jProps.put( "log4j.appender.CONSOLE.layout.ConversionPattern", pattern );

        log4jProps.put( "log4j.appender.FILE","org.apache.log4j.FileAppender");
        log4jProps.put( "log4j.appender.FILE.File","log/log.out");
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

        loggerConfig.update( log4jProps );

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
