/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.core.jar.runtime;

/**
 *
 * @author jdenise
 */
interface Constants {

    static final String DEPLOYMENT_ARG = "--deployment";
    static final String INSTALL_DIR_ARG = "--install-dir";

    static final String CONFIGURATION = "configuration";
    static final String DATA = "data";
    static final String DEPLOYMENT = "deployment";
    static final String DEPLOYMENTS = "deployments";
    static final String STANDALONE_CONFIG = "standalone.xml";
    static final String STANDALONE = "standalone";

    static final String SERVER_STATE = "server-state";
    static final String STOPPED = "stopped";

    static final String SHA1 = "sha1";
}
