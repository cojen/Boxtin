/*
 *  Copyright 2025 Cojen.org
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.cojen.boxtin;

import java.util.ArrayList;
import java.util.List;

import java.util.logging.Handler;
import java.util.logging.Logger;
import java.util.logging.LogRecord;

import static org.junit.Assert.assertFalse;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public class SimpleLogHandler extends Handler {
    @FunctionalInterface
    public static interface Runner {
        void run() throws Exception;
    }

    /**
     * Runs the test with logging captured.
     */
    public static List<LogRecord> forSecurityAgent(Runner runner) throws Exception {
        Logger logger = Logger.getLogger(SecurityAgent.class.getName());
        logger.setUseParentHandlers(false);
        var handler = new SimpleLogHandler();
        logger.addHandler(handler);

        try {
            runner.run();
        } finally {
            logger.setUseParentHandlers(true);
            logger.removeHandler(handler);
        }

        return handler.records;
    }

    public final List<LogRecord> records;

    public SimpleLogHandler() {
        records = new ArrayList<>();
    }

    @Override
    public void publish(LogRecord record) {
        records.add(record);
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
    }
}
