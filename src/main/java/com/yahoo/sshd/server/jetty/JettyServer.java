/*
 * Copyright 2014 Yahoo! Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the License); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an AS IS BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.yahoo.sshd.server.jetty;

import java.io.File;
import java.lang.management.ManagementFactory;

import org.eclipse.jetty.jmx.MBeanContainer;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ScheduledExecutorScheduler;
import org.eclipse.jetty.webapp.WebAppContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JettyServer {
    private static final Logger LOG = LoggerFactory.getLogger(JettyServer.class);


    private Server server;
    private final int jettyPort;
    private final String jettyWebAppDir;
    private final String jettyFilesDir;


    public JettyServer(int jettyPort, String jettyWebAppDir, String jettyFilesDir) {
        if (jettyPort == 0 || jettyWebAppDir == null) {
            throw new IllegalArgumentException("Jetty port and resource dir may not be empty");
        }
        this.jettyPort = jettyPort;
        this.jettyWebAppDir = jettyWebAppDir;
        this.jettyFilesDir = jettyFilesDir;
    }


    // http://www.eclipse.org/jetty/documentation/current/embedding-jetty.html#d0e19050
    // FIXME: need to pick between loading artifactory and serving resources
    public void setup() {
        // setup Server
        server = new Server(setupThreadPool());

        // Scheduler
        server.addBean(setupScheduledExecutor());

        server.setHandler(setupHandlers());

        // Extra options
        setExtraOptions();

        // === jetty-jmx.xml ===
        // http://www.eclipse.org/jetty/documentation/current/embedding-jetty.html#d0e19050
        // Setup JMX
        server.addBean(setupMbeanContainer());

        server.setHandler(setupWebapps());
        setupSSL();

        // server.setHanlder(setupResources());

        // === jetty-http.xml ===
        // HTTP Configuration
        HttpConfiguration http_config = setupHttpConfig();
        setupHttpConnector(http_config);
    }

    public void start() throws Exception {
        // start server
        server.start();

        LOG.info("Started jetty server on port: {}, resource dir: {} ", Integer.valueOf(jettyPort), jettyWebAppDir);
    }


    protected QueuedThreadPool setupThreadPool() {
        QueuedThreadPool threadPool = new QueuedThreadPool();
        threadPool.setMaxThreads(500);
        return threadPool;
    }

    protected ScheduledExecutorScheduler setupScheduledExecutor() {
        return new ScheduledExecutorScheduler();
    }

    protected HandlerCollection setupHandlers() {
        // Handler Structure
        HandlerCollection handlers = new HandlerCollection();
        ContextHandlerCollection contexts = new ContextHandlerCollection();
        handlers.setHandlers(new Handler[] {contexts, new DefaultHandler()});
        return handlers;
    }

    protected void setExtraOptions() {
        server.setDumpAfterStart(false);
        server.setDumpBeforeStop(false);
        server.setStopAtShutdown(true);
    }

    protected MBeanContainer setupMbeanContainer() {
        return new MBeanContainer(ManagementFactory.getPlatformMBeanServer());
    }

    protected Handler setupWebapps() {
        // The WebAppContext is the entity that controls the environment in
        // which a web application lives and breathes. In this example the
        // context path is being set to "/" so it is suitable for serving root
        // context requests and then we see it setting the location of the war.
        // A whole host of other configurations are available, ranging from
        // configuring to support annotation scanning in the webapp (through
        // PlusConfiguration) to choosing where the webapp will unpack itself.
        WebAppContext webapp = new WebAppContext();
        File warFile = new File(jettyWebAppDir + File.separator + "artifactory.war");
        webapp.setContextPath("/artifactory");
        webapp.setWar(warFile.getAbsolutePath());
        // TBD how to do this: setupFilters(webapp);

        // A WebAppContext is a ContextHandler as well so it needs to be set to
        // the server so it is aware of where to send the appropriate requests.

        return webapp;
    }

    // protected Handler setupWebapps() {
    // // === jetty-deploy.xml ===
    // DeploymentManager deployer = new DeploymentManager();
    // deployer.setContexts(contexts);
    // deployer.setContextAttribute(
    // "org.eclipse.jetty.server.webapp.ContainerIncludeJarPattern",
    // ".*/servlet-api-[^/]*\\.jar$");
    //
    // WebAppProvider webapp_provider = new WebAppProvider();
    // webapp_provider.setMonitoredDirName(jetty_base + "/webapps");
    // webapp_provider.setDefaultsDescriptor(jetty_home + "/etc/webdefault.xml");
    // webapp_provider.setScanInterval(1);
    // webapp_provider.setExtractWars(true);
    // webapp_provider.setConfigurationManager(new PropertiesConfigurationManager());
    //
    // deployer.addAppProvider(webapp_provider);
    // server.addBean(deployer);
    // }

    protected HttpConfiguration setupHttpConfig() {
        HttpConfiguration http_config = new HttpConfiguration();
        http_config.setSecureScheme("https");
        http_config.setSecurePort(8443);
        http_config.setOutputBufferSize(32768);
        http_config.setRequestHeaderSize(8192);
        http_config.setResponseHeaderSize(8192);
        http_config.setSendServerVersion(true);
        http_config.setSendDateHeader(false);
        // httpConfig.addCustomizer(new ForwardedRequestCustomizer());
        return http_config;
    }



    protected Handler setupResources() {
        // TBD: how do we have multiple handlers?
        // ResourceHandler resourceHandler = new ResourceHandler();
        // resourceHandler.setDirectoriesListed(false);
        // resourceHandler.setResourceBase(jettyWebAppDir);
        // server.setHandler(resourceHandler);

        // TBD how to do this correctly.
        // setupFilters(resourceHandler);
        return null;
    }


    // TBD.
    protected void setupSSL() {
        //
        // // === jetty-https.xml ===
        // // SSL Context Factory
        // SslContextFactory sslContextFactory = new SslContextFactory();
        // sslContextFactory.setKeyStorePath(jetty_home + "/etc/keystore");
        // sslContextFactory.setKeyStorePassword("OBF:1vny1zlo1x8e1vnw1vn61x8g1zlu1vn4");
        // sslContextFactory.setKeyManagerPassword("OBF:1u2u1wml1z7s1z7a1wnl1u2g");
        // sslContextFactory.setTrustStorePath(jetty_home + "/etc/keystore");
        // sslContextFactory.setTrustStorePassword("OBF:1vny1zlo1x8e1vnw1vn61x8g1zlu1vn4");
        // sslContextFactory.setExcludeCipherSuites("SSL_RSA_WITH_DES_CBC_SHA", "SSL_DHE_RSA_WITH_DES_CBC_SHA",
        // "SSL_DHE_DSS_WITH_DES_CBC_SHA", "SSL_RSA_EXPORT_WITH_RC4_40_MD5",
        // "SSL_RSA_EXPORT_WITH_DES40_CBC_SHA", "SSL_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA",
        // "SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA");
        //
        // // SSL HTTP Configuration
        // HttpConfiguration https_config = new HttpConfiguration(http_config);
        // https_config.addCustomizer(new SecureRequestCustomizer());
        //
        // // SSL Connector
        // ServerConnector sslConnector =
        // new ServerConnector(server, new SslConnectionFactory(sslContextFactory,
        // HttpVersion.HTTP_1_1.asString()), new HttpConnectionFactory(https_config));
        // sslConnector.setPort(8443);
        // server.addConnector(sslConnector);
    }

    @SuppressWarnings("resource")
    protected void setupHttpConnector(HttpConfiguration http_config) {
        ServerConnector http = new ServerConnector(server, new HttpConnectionFactory(http_config));
        http.setPort(jettyPort);
        http.setIdleTimeout(30000);
        server.addConnector(http);
    }

    protected void setupFilters(ResourceHandler resourceHandler) {
        // this is for if you need to hook specific servlet filters in front of the app.
        // A typical example is some sort of ip filtering, or some sort of SSO mechanism.
    }


    public void stop() throws Exception {
        server.stop();
    }
}
