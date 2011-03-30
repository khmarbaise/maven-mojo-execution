package com.oneandone.devel.modules.pws.pwsp;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.maven.plugin.logging.Log;

/**
 * This class is to delegate all things for getLog() to this
 * class and handle it with Log4J instead.
 *
 * @author Karl Heinz Marbaise
 *
 */
public class LogDelegator implements Log {
    private static Logger LOGGER = Logger.getLogger(LogDelegator.class);

    @Override
    public boolean isDebugEnabled() {
        return LOGGER.isDebugEnabled();
    }

    @Override
    public void debug(CharSequence content) {
        LOGGER.debug(content);
    }

    @Override
    public void debug(CharSequence content, Throwable error) {
        LOGGER.debug(content, error);
    }

    @Override
    public void debug(Throwable error) {
        LOGGER.debug("Throwable", error);
    }

    @Override
    public boolean isInfoEnabled() {
        return LOGGER.isInfoEnabled();
    }

    @Override
    public void info(CharSequence content) {
        LOGGER.info(content);
    }

    @Override
    public void info(CharSequence content, Throwable error) {
        LOGGER.info(content, error);
    }

    @Override
    public void info(Throwable error) {
        LOGGER.info("Throwable", error);
    }

    @Override
    public boolean isWarnEnabled() {
        if (LOGGER.getLevel().equals(Level.WARN_INT)) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void warn(CharSequence content) {
        LOGGER.warn(content);
    }

    @Override
    public void warn(CharSequence content, Throwable error) {
        LOGGER.warn(content, error);
    }

    @Override
    public void warn(Throwable error) {
        LOGGER.warn("Throwable", error);
    }

    @Override
    public boolean isErrorEnabled() {
        if (LOGGER.getLevel().equals(Level.ERROR_INT)) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void error(CharSequence content) {
        LOGGER.error(content);
    }

    @Override
    public void error(CharSequence content, Throwable error) {
        LOGGER.error(content, error);
    }

    @Override
    public void error(Throwable error) {
        LOGGER.error("Throwable", error);
    }

}
