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
package org.apache.ki.config;

import java.io.Reader;
import java.io.StringReader;
import java.util.Scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.ki.KiException;
import org.apache.ki.io.ResourceException;
import org.apache.ki.mgt.SecurityManager;
import org.apache.ki.util.Initializable;


/**
 * //TODO - complete JavaDoc
 *
 * @author Les Hazlewood
 * @since 0.9
 */
public abstract class TextConfiguration extends ResourceConfiguration implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(TextConfiguration.class);

    private String config;

    public TextConfiguration() {
    }

    public String getConfig() {
        return config;
    }

    public void setConfig(String config) {
        this.config = config;
    }

    protected abstract void load(Reader r) throws ConfigurationException;

    protected abstract void load(Scanner s) throws ConfigurationException;

    /**
     * Loads the configuration specified by the 'config' argument by creating a StringReader
     * and using it to load the config.
     *
     * @param config the config text to be loaded.
     */
    protected void loadTextConfig(String config) {
        StringReader sr = new StringReader(config);
        try {
            load(sr);
        } catch (Exception e2) {
            String msg = "Unable to load from text configuration.";
            throw new ResourceException(msg, e2);
        }
    }

    public void init() throws KiException {
        SecurityManager securityManager = getSecurityManager();
        if (securityManager == null) {
            String config = getConfig();
            if (config != null) {
                log.info("Attempting to load Configuration based on 'config' property.");
                loadTextConfig(config);
            }
        }
    }
}
