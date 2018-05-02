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
package org.apache.ki.web.filter.authz;

import org.apache.ki.config.ConfigurationException;
import org.apache.ki.util.StringUtils;
import org.apache.ki.web.WebUtils;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * A Filter that requires the request to be on a specific port, and if not, redirects to the same URL on that port.
 *
 * @author Les Hazlewood
 * @since 1.0
 */
public class PortFilter extends AuthorizationFilter {

    protected int toPort(Object mappedValue) {
        String[] ports = (String[]) mappedValue;
        if (ports == null || ports.length == 0) {
            throw new ConfigurationException("PortFilter defined, but no port was specified!");
        }
        if (ports.length > 1) {
            throw new ConfigurationException("PortFilter can only be configured with a single port.  You have " +
                "configured " + ports.length + ": " + StringUtils.toString(ports));
        }
        return Integer.parseInt(ports[0]);
    }

    protected boolean isAccessAllowed(ServletRequest request, ServletResponse response, Object mappedValue) throws Exception {
        int requiredPort = toPort(mappedValue);
        int requestPort = request.getServerPort();
        return requiredPort == requestPort;
    }

    /**
     * Redirects the request to the same exact incoming URL, but with the port listed in the filter's configuration.
     *
     * @param request     the incoming <code>ServletRequest</code>
     * @param response    the outgoing <code>ServletResponse</code>
     * @param mappedValue the config specified for the filter in the matching request's filter chain.
     * @return {@code false} always to force a redirect.
     */
    @Override
    protected boolean onAccessDenied(ServletRequest request, ServletResponse response, Object mappedValue) throws IOException {

        //just redirect to the specified port:
        int port = toPort(mappedValue);

        StringBuffer sb = new StringBuffer();

        String scheme = request.getScheme();
        if (port == 80) {
            scheme = "http";
        } else if (port == 443) {
            scheme = "https";
        }
        sb.append(scheme).append("://");
        sb.append(request.getServerName());
        if (port != 80 && port != 443) {
            sb.append(":");
            sb.append(request.getServerPort());
        }
        if (request instanceof HttpServletRequest) {
            sb.append(WebUtils.toHttp(request).getRequestURI());
            String query = WebUtils.toHttp(request).getQueryString();
            if ( query != null ) {
                sb.append("?").append(query);
            }
        }

        WebUtils.issueRedirect(request, response, sb.toString());

        return false;
    }
}
