package com.beoui.geocell;

import java.util.logging.Logger;

public final class GeocellLogger {

    private GeocellLogger() {
        // no instantiation allowed
    }

    public static Logger get() {
        return Logger.getLogger("com.beoui.geocell");
    }

}
