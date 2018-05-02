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
package org.apache.ki.session.mgt;

import java.io.Serializable;
import java.net.InetAddress;
import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.ki.authz.HostUnauthorizedException;
import org.apache.ki.session.ExpiredSessionException;
import org.apache.ki.session.InvalidSessionException;
import org.apache.ki.session.Session;
import org.apache.ki.session.ReplacedSessionException;
import org.apache.ki.util.Destroyable;
import org.apache.ki.util.LifecycleUtils;
import org.apache.ki.util.ThreadContext;


/**
 * Default business-tier implementation of the {@link ValidatingSessionManager} interface.
 *
 * @author Les Hazlewood
 * @author Jeremy Haile
 * @since 0.1
 */
public abstract class AbstractValidatingSessionManager extends AbstractSessionManager
    implements ValidatingSessionManager, Destroyable {

    //TODO - complete JavaDoc

    private static final Logger log = LoggerFactory.getLogger(AbstractValidatingSessionManager.class);

    /**
     * The default interval at which sessions will be validated (1 hour);
     * This can be overridden by calling {@link #setSessionValidationInterval(long)}
     */
    public static final long DEFAULT_SESSION_VALIDATION_INTERVAL = MILLIS_PER_HOUR;

    protected boolean sessionValidationSchedulerEnabled = true; //default
    /** Scheduler used to validate sessions on a regular basis. */
    protected SessionValidationScheduler sessionValidationScheduler = null;

    protected long sessionValidationInterval = DEFAULT_SESSION_VALIDATION_INTERVAL;

    /**
     * Whether or not to automatically create a new session transparently when a referenced session is invalid or
     * did not exist.  {@code true} by default, for developer convenience.
     */
    private boolean autoCreateWhenInvalid = true;

    public AbstractValidatingSessionManager() {
    }

    public boolean isSessionValidationSchedulerEnabled() {
        return sessionValidationSchedulerEnabled;
    }

    public void setSessionValidationSchedulerEnabled(boolean sessionValidationSchedulerEnabled) {
        this.sessionValidationSchedulerEnabled = sessionValidationSchedulerEnabled;
    }

    public void setSessionValidationScheduler(SessionValidationScheduler sessionValidationScheduler) {
        this.sessionValidationScheduler = sessionValidationScheduler;
    }

    public SessionValidationScheduler getSessionValidationScheduler() {
        return sessionValidationScheduler;
    }

    private void enableSessionValidationIfNecessary() {
        SessionValidationScheduler scheduler = getSessionValidationScheduler();
        if (isSessionValidationSchedulerEnabled() && (scheduler == null || !scheduler.isEnabled())) {
            enableSessionValidation();
        }
    }

    /**
     * If using the underlying default <tt>SessionValidationScheduler</tt> (that is, the
     * {@link #setSessionValidationScheduler(SessionValidationScheduler) setSessionValidationScheduler} method is
     * never called) , this method allows one to specify how
     * frequently session should be validated (to check for orphans).  The default value is
     * {@link #DEFAULT_SESSION_VALIDATION_INTERVAL}.
     * <p/>
     * <p>If you override the default scheduler, it is assumed that overriding instance 'knows' how often to
     * validate sessions, and this attribute will be ignored.
     * <p/>
     * <p>Unless this method is called, the default value is {@link #DEFAULT_SESSION_VALIDATION_INTERVAL}.
     *
     * @param sessionValidationInterval the time in milliseconds between checking for valid sessions to reap orphans.
     */
    public void setSessionValidationInterval(long sessionValidationInterval) {
        this.sessionValidationInterval = sessionValidationInterval;
    }

    public long getSessionValidationInterval() {
        return sessionValidationInterval;
    }

    /**
     * Returns <code>true</code> if this session manager should automatically create a new session when an invalid or
     * nonexistent session is referenced, <code>false</code> otherwise.  Unless overridden by the
     * {@link #setAutoCreateWhenInvalid(boolean)} method, the default value is <code>true</code> for developer
     * convenience and to match what most people are accustomed based on years of servlet container behavior.
     * <p/>
     * When true (the default), this {@code SessionManager} implementation throws an
     * {@link org.apache.ki.session.ReplacedSessionException ReplacedSessionException} to the caller whenever a new session is created so
     * the caller can receive the new session ID and react accordingly for future {@code SessionManager SessionManager}
     * method invocations.
     *
     * @return <code>true</code> if this session manager should automatically create a new session when an invalid
     *         session is referenced, <code>false</code> otherwise.
     */
    public boolean isAutoCreateWhenInvalid() {
        return autoCreateWhenInvalid;
    }

    /**
     * Sets if this session manager should automatically create a new session when an invalid
     * session is referenced.  The default value unless overridden by this method is <code>true</code> for developer
     * convenience and to match what most people are accustomed based on years of servlet container behavior.
     * <p/>
     * When true (the default), this {@code SessionManager} implementation throws an
     * {@link org.apache.ki.session.ReplacedSessionException ReplacedSessionException} to the caller whenever a new session is created so
     * the caller can receive the new session ID and react accordingly for future {@code SessionManager SessionManager}
     * method invocations.
     *
     * @param autoCreateWhenInvalid if this session manager should automatically create a new session when an
     *                                    invalid session is referenced
     */
    public void setAutoCreateWhenInvalid(boolean autoCreateWhenInvalid) {
        this.autoCreateWhenInvalid = autoCreateWhenInvalid;
    }

    protected final Session doGetSession(Serializable sessionId) throws InvalidSessionException {
        enableSessionValidationIfNecessary();

        if (log.isTraceEnabled()) {
            log.trace("Attempting to retrieve session with id [" + sessionId + "]");
        }
        InetAddress hostAddress = null;
        try {
            Session s = retrieveSession(sessionId);
            //save the host address in case the session will be invalidated.  We want to retain it for the
            //replacement session:
            hostAddress = s.getHostAddress();
            validate(s);
            return s;
        } catch (InvalidSessionException ise) {
            if (isAutoCreateWhenInvalid()) {
                if (hostAddress == null) {
                    //try the threadContext as a last resort:
                    hostAddress = ThreadContext.getInetAddress();
                }
                Serializable newId = start(hostAddress);
                String msg = "Session with id [" + sessionId + "] is invalid.  The SessionManager " +
                    "has been configured to automatically re-create sessions upon invalidation.  Returnining " +
                    "new session id [" + newId + "] with exception so the caller may react accordingly.";
                throw new ReplacedSessionException(msg, ise, sessionId, newId);
            } else {
                //propagate original exception:
                throw ise;
            }
        }
    }



    /**
     * Looks up a session from the underlying data store based on the specified {@code sessionId}.
     *
     * @param sessionId
     * @return
     * @throws InvalidSessionException
     */
    protected abstract Session retrieveSession(Serializable sessionId) throws InvalidSessionException;

    protected final Session createSession(InetAddress originatingHost) throws HostUnauthorizedException, IllegalArgumentException {
        enableSessionValidationIfNecessary();
        return doCreateSession(originatingHost);
    }

    protected abstract Session doCreateSession(InetAddress originatingHost) throws HostUnauthorizedException, IllegalArgumentException;

    protected void validate(Session session) throws InvalidSessionException {
        try {
            doValidate(session);
        } catch (ExpiredSessionException ese) {
            onExpiration(session);            
            notifyExpiration(session);
            //propagate to caller:
            throw ese;
        }
    }

    protected void doValidate(Session session) throws InvalidSessionException {
        if (session instanceof ValidatingSession) {
            ((ValidatingSession) session).validate();
        } else {
            String msg = "The " + getClass().getName() + " implementation only supports validating " +
                "Session implementations of the " + ValidatingSession.class.getName() + " interface.  " +
                "Please either implement this interface in your session implementation or override the " +
                AbstractValidatingSessionManager.class.getName() + ".doValidate(Session) method to perform validation.";
            throw new IllegalStateException(msg);
        }
    }

    /**
     * Subclass template hook in case per-session timeout is not based on
     * {@link org.apache.ki.session.Session#getTimeout()}.
     * <p/>
     * <p>This implementation merely returns {@link org.apache.ki.session.Session#getTimeout()}</p>
     *
     * @param session the session for which to determine session timeout.
     * @return the time in milliseconds the specified session may remain idle before expiring.
     */
    protected long getTimeout(Session session) {
        return session.getTimeout();
    }

    protected SessionValidationScheduler createSessionValidationScheduler() {
        ExecutorServiceSessionValidationScheduler scheduler;

        if (log.isDebugEnabled()) {
            log.debug("No sessionValidationScheduler set.  Attempting to create default instance.");
        }
        scheduler = new ExecutorServiceSessionValidationScheduler(this);
        scheduler.setInterval(getSessionValidationInterval());
        if (log.isTraceEnabled()) {
            log.trace("Created default SessionValidationScheduler instance of type [" + scheduler.getClass().getName() + "].");
        }
        return scheduler;
    }

    protected void enableSessionValidation() {
        SessionValidationScheduler scheduler = getSessionValidationScheduler();
        if (scheduler == null) {
            scheduler = createSessionValidationScheduler();
            setSessionValidationScheduler(scheduler);
        }
        if (log.isInfoEnabled()) {
            log.info("Enabling session validation scheduler...");
        }
        scheduler.enableSessionValidation();
        afterSessionValidationEnabled();
    }

    protected void afterSessionValidationEnabled() {
    }

    protected void disableSessionValidation() {
        beforeSessionValidationDisabled();
        SessionValidationScheduler scheduler = getSessionValidationScheduler();
        if (scheduler != null) {
            try {
                scheduler.disableSessionValidation();
                if (log.isInfoEnabled()) {
                    log.info("Disabled session validation scheduler.");
                }
            } catch (Exception e) {
                if (log.isDebugEnabled()) {
                    String msg = "Unable to disable SessionValidationScheduler.  Ignoring (shutting down)...";
                    log.debug(msg, e);
                }
            }
            LifecycleUtils.destroy(scheduler);
            setSessionValidationScheduler(null);
        }
    }

    protected void beforeSessionValidationDisabled() {
    }

    public void destroy() {
        disableSessionValidation();
    }

    /** @see ValidatingSessionManager#validateSessions() */
    public void validateSessions() {
        if (log.isInfoEnabled()) {
            log.info("Validating all active sessions...");
        }

        int invalidCount = 0;

        Collection<Session> activeSessions = getActiveSessions();

        if (activeSessions != null && !activeSessions.isEmpty()) {
            for (Session s : activeSessions) {
                try {
                    validate(s);
                } catch (InvalidSessionException e) {
                    if (log.isDebugEnabled()) {
                        boolean expired = (e instanceof ExpiredSessionException);
                        String msg = "Invalidated session with id [" + s.getId() + "]" +
                            (expired ? " (expired)" : " (stopped)");
                        log.debug(msg);
                    }
                    invalidCount++;
                }
            }
        }

        if (log.isInfoEnabled()) {
            String msg = "Finished session validation.";
            if (invalidCount > 0) {
                msg += "  [" + invalidCount + "] sessions were stopped.";
            } else {
                msg += "  No sessions were stopped.";
            }
            log.info(msg);
        }
    }

    protected abstract Collection<Session> getActiveSessions();

    public void validateSession(Serializable sessionId) {
        //standard getSession call will validate, so just call the method:
        getSession(sessionId);
    }

}
