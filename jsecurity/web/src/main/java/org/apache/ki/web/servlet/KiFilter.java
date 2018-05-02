/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.ki.web.servlet;

import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.net.InetAddress;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.beanutils.PropertyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.ki.config.Configuration;
import org.apache.ki.config.ConfigurationException;
import org.apache.ki.mgt.SecurityManager;
import org.apache.ki.util.ClassUtils;
import org.apache.ki.util.LifecycleUtils;
import static org.apache.ki.util.StringUtils.clean;
import org.apache.ki.util.ThreadContext;
import org.apache.ki.web.DefaultWebSecurityManager;
import org.apache.ki.web.WebUtils;
import org.apache.ki.web.config.IniWebConfiguration;
import org.apache.ki.web.config.WebConfiguration;


/**
 * Main ServletFilter that configures and enables all Ki functions within a web application.
 * <p/>
 * The following is a fully commented example that documents how to configure it:
 * <p/>
 * <pre>&lt;filter&gt;
 * &lt;filter-name&gt;KiFilter&lt;/filter-name&gt;
 * &lt;filter-class&gt;org.apache.ki.web.servlet.KiFilter&lt;/filter-class&gt;
 * &lt;init-param&gt;&lt;param-name&gt;config&lt;/param-name&gt;&lt;param-value&gt;
 * #
 * #NOTE:  This config looks pretty long - but its not - its only a few lines of actual config.
 * #       Everything else is just heavily commented to explain things in-depth. Feel free to delete any
 * #       comments that you don't want to read from your own configuration ;)
 * #
 * # Any commented values below that _don't_ start with 'example.pkg' are Ki's defaults.  If you want to change any
 * # values on those lines, you only need to uncomment the lines you want to change.
 * #
 * [main]
 * # The 'main' section defines Ki-wide configuration.
 * #
 * # The configuration is essentially an object graph definition in a .properties style format.  The beans defined
 * # would be those that are used to construct the application's SecurityManager.  It is essentially 'poor man's'
 * # dependency injection via a .properties format.
 * #
 * # --- Defining Realms ---
 * #
 * # Any Realm defined here will automatically be injected into Ki's default SecurityManager created at startup.  For
 * # example:
 * #
 * # myRealm = example.pkg.security.MyRealm
 * #
 * # This would instantiate the some.pkg.security.MyRealm class with a default no-arg constructor and inject it into
 * # the SecurityManager.  More than one realm can be defined if needed.  You can create graphs and reference
 * # other beans ('$' bean reference notation) while defining Realms and other objects:
 * #
 * # <b>connectionFactory</b> = example.pkg.ConnectionFactory
 * # connectionFactory.driverClassName = a.jdbc.Driver
 * # connectionFactory.username = aUsername
 * # connectionFactory.password = aPassword
 * # connectionFactory.minConnections = 3
 * # connectionFactory.maxConnections = 10
 * # ... etc...
 * #
 * # myJdbcRealm = example.pkg.jdbc.MyJdbcRealm
 * # myJdbcRealm.connectionFactory = <b>$connectionFactory</b>
 * # ... etc ...
 * #
 * # --- Realm Factories ---
 * #
 * # If the .properties style isn't robust enough for your needs, you also have the option of implementing the
 * # {@link org.apache.ki.realm.RealmFactory org.apache.ki.realm.RealmFactory} interface with more complex construction
 * # logic.  Then you can declare the implementation here instead.  The realms it returns will be injected in to the
 * # SecurityManager just as the individual Realms are.  For example:
 * #
 * # aRealmFactory = some.pkg.ClassThatImplementsRealmFactory
 * #
 * # --- SessionManager properties ---
 * #
 * # Except for Realms and RealmFactories, all other objects should be defined and set on the SecurityManager directly.
 * # The default 'securityManager' bean is an instance of {@link org.apache.ki.web.DefaultWebSecurityManager}, so you
 * # can set any of its corresponding properties as necessary:
 * #
 * # someObject = some.fully.qualified.ClassName
 * # someObject.propertyN = foo
 * # ...
 * # securityManager.someObject = $someObject
 * #
 * # For example, if you wanted to change Ki's default session mechanism, you can change the 'sessionMode' property.
 * # By default, Ki's Session infrastructure in a web environment will use the
 * # Servlet container's HttpSession.  However, if you need to share session state across client types
 * # (e.g. Web MVC plus Java Web Start or Flash), or are doing distributed/shared Sessions for
 * # Single Sign On, HttpSessions aren't good enough.  You'll need to use Ki's more powerful
 * # (and client-agnostic) session management.  You can enable this by uncommenting the following line
 * # and changing 'http' to 'ki'
 * #
 * #securityManager.{@link org.apache.ki.web.DefaultWebSecurityManager#setSessionMode(String) sessionMode} = http
 * #
 * [filters]
 * # This section defines the 'pool' of all Filters available to the url path definitions in the [urls] section below.
 * #
 * # The following commented values are already provided by Ki by default and are immediately usable
 * # in the [urls] definitions below.  If you like, you may override any values by uncommenting only the lines
 * # you need to change.
 * #
 * # Each Filter is configured based on its functionality and/or protocol.  You should read each
 * # Filter's JavaDoc to fully understand what each does and how it works as well as how it would
 * # affect the user experience.
 * #
 * # Form-based Authentication filter:
 * #<a name="authc"></a>authc = {@link org.apache.ki.web.filter.authc.FormAuthenticationFilter}
 * #authc.{@link org.apache.ki.web.filter.authc.FormAuthenticationFilter#setLoginUrl(String) loginUrl} = /login.jsp
 * #authc.{@link org.apache.ki.web.filter.authc.FormAuthenticationFilter#setUsernameParam(String) usernameParam} = username
 * #authc.{@link org.apache.ki.web.filter.authc.FormAuthenticationFilter#setPasswordParam(String) passwordParam} = password
 * #authc.{@link org.apache.ki.web.filter.authc.FormAuthenticationFilter#setRememberMeParam(String) rememberMeParam} = rememberMe
 * #authc.{@link org.apache.ki.web.filter.authc.FormAuthenticationFilter#setSuccessUrl(String) successUrl}  = /login.jsp
 * #authc.{@link org.apache.ki.web.filter.authc.FormAuthenticationFilter#setFailureKeyAttribute(String) failureKeyAttribute} = {@link org.apache.ki.web.filter.authc.FormAuthenticationFilter#DEFAULT_ERROR_KEY_ATTRIBUTE_NAME}
 * #
 * # Http BASIC Authentication filter:
 * #<a name="authcBasic"></a>authcBasic = {@link org.apache.ki.web.filter.authc.BasicHttpAuthenticationFilter}
 * #authcBasic.{@link org.apache.ki.web.filter.authc.BasicHttpAuthenticationFilter#setApplicationName(String) applicationName} = application
 * #
 * # Roles filter: requires the requesting user to have one or more roles for the request to continue.
 * # If they do not have the specified roles, they are redirected to the specified URL.
 * #<a name="roles"></a>roles = {@link org.apache.ki.web.filter.authz.RolesAuthorizationFilter}
 * #roles.{@link org.apache.ki.web.filter.authz.RolesAuthorizationFilter#setUnauthorizedUrl(String) unauthorizedUrl} =
 * # (note the above url is null by default, which will cause an HTTP 403 (Access Denied) response instead
 * # of redirecting to a page.  If you want to show a 'nice page' instead, you should specify that url.
 * #
 * # Permissions filter: requires the requesting user to have one or more permissions for the request to
 * # continue, and if they do not, redirects them to the specified URL.
 * #<a name="perms"></a>perms = {@link org.apache.ki.web.filter.authz.PermissionsAuthorizationFilter}
 * #perms.{@link org.apache.ki.web.filter.authz.PermissionsAuthorizationFilter#setUnauthorizedUrl(String) unauthorizedUrl} =
 * # (note the above url is null by default, which will cause an HTTP 403 (Access Denied) response instead
 * # of redirecting to a page.  If you want to show a 'nice page' instead, you should specify that url.  Many
 * # applications like to use the same url specified in roles.unauthorizedUrl above.
 * #
 * #
 * # Define your own filters here as you would any other object as described in the '[main]' section above (properties,
 * # $references, etc).  To properly handle url path matching (see the [urls] section below), your
 * # filter should extend the {@link org.apache.ki.web.filter.PathMatchingFilter PathMatchingFilter} abstract class.
 * #
 * [urls]
 * # This section defines url path mappings.  Each mapping entry must be on a single line and conform to the
 * # following representation:
 * #
 * # ant_path_expression = path_specific_filter_chain_definition
 * #
 * # For any request that matches a specified path, the corresponding value defines a comma-delimited chain of
 * # filters to execute for that request.
 * #
 * # This is incredibly powerful in that you can define arbitrary filter chains for any given request pattern
 * # to greatly customize the security experience.
 * #
 * # The path_specific_filter_chain_definition must match the following format:
 * #
 * # filter1[optional_config1], filter2[optional_config2], ..., filterN[optional_configN]
 * #
 * # where 'filterN' is the name of an filter defined above in the [filters] section and
 * # '[optional_configN]' is an optional bracketed string that has meaning for that particular filter for
 * # _that particular path_.  If the filter does not need specific config for that url path, you may
 * # discard the brackets - that is, filterN[] just becomes filterN.
 * #
 * # And because filter tokens define chains, order matters!  Define the tokens for each path pattern
 * # in the order you want them to filter (comma-delimited).
 * #
 * # Finally, each filter is free to handle the response however it wants if its necessary
 * # conditions are not met (redirect, HTTP error code, direct rendering, etc).  Otherwise, it is expected to allow
 * # the request to continue through the chain on to the final destination view.
 * #
 * # Examples:
 * #
 * # To illustrate chain configuration, look at the /account/** mapping below.  This says
 * # &quot;apply the above 'authcBasic' filter to any request matching the '/account/**' pattern&quot;.  Since the
 * # 'authcBasic' filter does not need any path-specific config, it doesn't have any config brackets [].
 * #
 * # The /remoting/** definition on the other hand uses the 'roles' and 'perms' filters which do use
 * # bracket notation.  That definition says:
 * #
 * # &quot;To access /remoting/** urls, ensure that the user is first authenticated ('authcBasic'), then ensure that user
 * # has the 'b2bClient' role, and then finally ensure that they have the 'remote:invoke:lan,wan' permission.&quot;
 * #
 * # (Note that because elements within brackets [ ] are comma-delimited themselves, we needed to escape the permission
 * # actions of 'lan,wan' with quotes.  If we didn't do that, the permission filter would interpret
 * # the text between the brackets as two permissions: 'remote:invoke:lan' and 'wan' instead of the
 * # single desired 'remote:invoke:lan,wan' token.  So, you can use quotes wherever you need to escape internal
 * # commas.)
 * #
 * /account/** = <a href="#authcBasic">authcBasic</a>
 * /remoting/** = <a href="#authcBasic">authcBasic</a>, <a href="#roles">roles</a>[b2bClient], <a href="#perms">perms</a>[remote:invoke:"lan,wan"]
 * #
 * &lt;/param-value&gt;&lt;/init-param&gt;
 * &lt;/filter&gt;
 * #
 * #
 * &lt;filter-mapping&gt;
 * &lt;filter-name&gt;KiFilter&lt;/filter-name&gt;
 * &lt;url-pattern&gt;/*&lt;/url-pattern&gt;
 * &lt;/filter-mapping&gt;</pre>
 *
 * @author Les Hazlewood
 * @author Jeremy Haile
 * @since 0.1
 */
public class KiFilter extends OncePerRequestFilter {

    //TODO - complete JavaDoc

    public static final String SECURITY_MANAGER_CONTEXT_KEY = SecurityManager.class.getName() + "_SERVLET_CONTEXT_KEY";

    public static final String CONFIG_CLASS_NAME_INIT_PARAM_NAME = "configClassName";
    public static final String CONFIG_INIT_PARAM_NAME = "config";
    public static final String CONFIG_URL_INIT_PARAM_NAME = "configUrl";

    private static final Logger log = LoggerFactory.getLogger(KiFilter.class);

    protected String config;
    protected String configUrl;
    protected String configClassName;
    protected WebConfiguration configuration;

    // Reference to the security manager used by this filter
    protected SecurityManager securityManager;

    public KiFilter() {
        this.configClassName = IniWebConfiguration.class.getName();
    }

    public WebConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(WebConfiguration configuration) {
        this.configuration = configuration;
    }

    public SecurityManager getSecurityManager() {
        return securityManager;
    }

    protected void setSecurityManager(org.apache.ki.mgt.SecurityManager sm) {
        this.securityManager = sm;
    }

    protected void onFilterConfigSet() throws Exception {
        applyInitParams();
        WebConfiguration config = configure();
        setConfiguration(config);

        // Retrieve and store a reference to the security manager
        SecurityManager sm = ensureSecurityManager(config);
        setSecurityManager(sm);
    }

    /**
     * Retrieves the security manager for the given configuration.
     *
     * @param config the configuration for this filter.
     * @return the security manager that this filter should use.
     */
    protected SecurityManager ensureSecurityManager(Configuration config) {
        SecurityManager sm = config.getSecurityManager();

        // If the config doesn't return a security manager, build one by default.
        if (sm == null) {
            if (log.isInfoEnabled()) {
                log.info("Configuration instance [" + config + "] did not provide a SecurityManager.  No config " +
                    "specified?  Defaulting to a " + DefaultWebSecurityManager.class.getName() + " instance...");
            }
            sm = new DefaultWebSecurityManager();
        }

        return sm;
    }

    protected void applyInitParams() {
        FilterConfig config = getFilterConfig();

        String configCN = clean(config.getInitParameter(CONFIG_CLASS_NAME_INIT_PARAM_NAME));
        if (configCN != null) {
            if (ClassUtils.isAvailable(configCN)) {
                this.configClassName = configCN;
            } else {
                String msg = "configClassName fully qualified class name value [" + configCN + "] is not " +
                    "available in the classpath.  Please ensure you have typed it correctly and the " +
                    "corresponding class or jar is in the classpath.";
                throw new ConfigurationException(msg);
            }
        }

        this.config = clean(config.getInitParameter(CONFIG_INIT_PARAM_NAME));
        this.configUrl = clean(config.getInitParameter(CONFIG_URL_INIT_PARAM_NAME));
    }

    protected WebConfiguration configure() {
        WebConfiguration conf = (WebConfiguration) ClassUtils.newInstance(this.configClassName);
        applyFilterConfig(conf);
        applyUrlConfig(conf);
        applyEmbeddedConfig(conf);
        LifecycleUtils.init(conf);
        return conf;
    }

    protected void applyFilterConfig(WebConfiguration conf) {
        if (log.isDebugEnabled()) {
            String msg = "Attempting to inject the FilterConfig (using 'setFilterConfig' method) into the " +
                "instantiated WebConfiguration for any wrapped Filter initialization...";
            log.debug(msg);
        }
        try {
            PropertyDescriptor pd = PropertyUtils.getPropertyDescriptor(conf, "filterConfig");
            if (pd != null) {
                PropertyUtils.setProperty(conf, "filterConfig", getFilterConfig());
            }
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.debug("Error setting FilterConfig on WebConfiguration instance.", e);
            }
        }
    }

    protected void applyEmbeddedConfig(WebConfiguration conf) {
        if (this.config != null) {
            try {
                PropertyDescriptor pd = PropertyUtils.getPropertyDescriptor(conf, "config");

                if (pd != null) {
                    PropertyUtils.setProperty(conf, "config", this.config);
                } else {
                    String msg = "The 'config' filter param was specified, but there is no " +
                        "'setConfig(String)' method on the Configuration instance [" + conf + "].  If you do " +
                        "not require the 'config' filter param, please comment it out, or if you do need it, " +
                        "please ensure your Configuration instance has a 'setConfig(String)' method to receive it.";
                    throw new ConfigurationException(msg);
                }
            } catch (Exception e) {
                String msg = "There was an error setting the 'config' property of the Configuration object.";
                throw new ConfigurationException(msg, e);
            }
        }
    }

    protected void applyUrlConfig(WebConfiguration conf) {
        if (this.configUrl != null) {
            try {
                PropertyDescriptor pd = PropertyUtils.getPropertyDescriptor(conf, "configUrl");

                if (pd != null) {
                    PropertyUtils.setProperty(conf, "configUrl", this.configUrl);
                } else {
                    String msg = "The 'configUrl' filter param was specified, but there is no " +
                        "'setConfigUrl(String)' method on the Configuration instance [" + conf + "].  If you do " +
                        "not require the 'configUrl' filter param, please comment it out, or if you do need it, " +
                        "please ensure your Configuration instance has a 'setConfigUrl(String)' method to receive it.";
                    throw new ConfigurationException(msg);
                }
            } catch (Exception e) {
                String msg = "There was an error setting the 'configUrl' property of the Configuration object.";
                throw new ConfigurationException(msg, e);
            }
        }
    }

    protected boolean isHttpSessions() {
        SecurityManager secMgr = getSecurityManager();
        if (secMgr instanceof DefaultWebSecurityManager) {
            return ((DefaultWebSecurityManager) secMgr).isHttpSessionMode();
        } else {
            return true;
        }
    }

    protected InetAddress getInetAddress(ServletRequest request) {
        return WebUtils.getInetAddress(request);
    }

    /**
     * Wraps the original HttpServletRequest in a {@link KiHttpServletRequest}
     * @since 1.0
     */
    protected ServletRequest wrapServletRequest(HttpServletRequest orig) {
        return new KiHttpServletRequest(orig, getServletContext(), isHttpSessions());
    }

    /** @since 1.0 */
    protected ServletRequest prepareServletRequest(ServletRequest request, ServletResponse response,
                                                   FilterChain chain) {
        ServletRequest toUse = request;
        if (request instanceof HttpServletRequest) {
            HttpServletRequest http = (HttpServletRequest) request;
            toUse = wrapServletRequest(http);
        }
        return toUse;
    }

    /** @since 1.0 */
    protected ServletResponse wrapServletResponse(HttpServletResponse orig, KiHttpServletRequest request) {
        return new KiHttpServletResponse(orig, getServletContext(), request);
    }

    /** @since 1.0 */
    protected ServletResponse prepareServletResponse(ServletRequest request, ServletResponse response,
                                                     FilterChain chain) {
        ServletResponse toUse = response;
        if (isHttpSessions() && (request instanceof KiHttpServletRequest) &&
            (response instanceof HttpServletResponse)) {
            //the KiHttpServletResponse exists to support URL rewriting for session ids.  This is only needed if
            //using Ki sessions (i.e. not simple HttpSession based sessions):
            toUse = wrapServletResponse((HttpServletResponse) response, (KiHttpServletRequest) request);
        }
        return toUse;
    }

    /** @since 1.0 */
    protected void bind(ServletRequest request, ServletResponse response) {
        WebUtils.bindInetAddressToThread(request);
        WebUtils.bind(request);
        WebUtils.bind(response);
        ThreadContext.bind(getSecurityManager());
        ThreadContext.bind(getSecurityManager().getSubject());
    }

    /** @since 1.0 */
    protected void unbind(ServletRequest request, ServletResponse response) {
        //arguments ignored, just clear the thread:
        ThreadContext.unbindSubject();
        ThreadContext.unbindSecurityManager();
        WebUtils.unbindServletResponse();
        WebUtils.unbindServletRequest();
        ThreadContext.unbindInetAddress();
    }

    protected void doFilterInternal(ServletRequest servletRequest, ServletResponse servletResponse,
                                    FilterChain origChain) throws ServletException, IOException {

        ServletRequest request = prepareServletRequest(servletRequest, servletResponse, origChain);
        ServletResponse response = prepareServletResponse(request, servletResponse, origChain);

        bind(request, response);

        FilterChain chain = getConfiguration().getChain(request, response, origChain);
        if (chain == null) {
            chain = origChain;
            if (log.isTraceEnabled()) {
                log.trace("No security filter chain configured for the current request.  Using default.");
            }
        } else {
            if (log.isTraceEnabled()) {
                log.trace(" Using configured filter chain for the current request.");
            }
        }

        try {
            chain.doFilter(request, response);
        } finally {
            unbind(request, response);
        }
    }

    public void destroy() {
        LifecycleUtils.destroy(getConfiguration());
    }
}
