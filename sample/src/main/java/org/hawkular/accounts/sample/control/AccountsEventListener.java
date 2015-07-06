/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hawkular.accounts.sample.control;

import org.hawkular.accounts.events.listener.AccountsEvent;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;

/**
 * @author Juraci Paixão Kröhling
 */
@ApplicationScoped
public class AccountsEventListener {
    private final MsgLogger logger = MsgLogger.LOGGER;

    public void accountsEvent(@Observes AccountsEvent event) {
        logger.cdiEventReceived(event.getAction(), event.getEventId(), event.getPersona().getId());
    }

}