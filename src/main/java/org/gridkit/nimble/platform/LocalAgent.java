package org.gridkit.nimble.platform;

import org.slf4j.Logger;

public interface LocalAgent extends TimeService, RemoteAgent, AttributeContext {
    Logger getLogger(String name);
}