package it.univaq.disim.spencer.injectblackhole;

import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public class LoggerConfig {
    static {
        configureGlobalLogger();
    }

    public static void configureGlobalLogger() {
        Logger rootLogger = Logger.getLogger("");
        Handler[] handlers = rootLogger.getHandlers();

        // Remove default handlers
        for (Handler handler : handlers) {
            rootLogger.removeHandler(handler);
        }

        // Create a new console handler with a custom formatter
        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setFormatter(new Formatter() {
            @Override
            public String format(LogRecord record) {
                return record.getLevel() + ": " + record.getMessage() + "\n";
            }
        });

        rootLogger.addHandler(consoleHandler);
    }
}