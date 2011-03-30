package com.oneandone.devel.modules.pws.pwsp.embedder;

import org.apache.log4j.Logger;
import org.sonatype.aether.transfer.TransferCancelledException;
import org.sonatype.aether.transfer.TransferEvent;
import org.sonatype.aether.transfer.TransferListener;

public class ExecutionListener implements TransferListener {
    private static Logger LOGGER = Logger
            .getLogger(ExecutionListener.class);

    @Override
    public void transferInitiated(TransferEvent event)
            throws TransferCancelledException {
        LOGGER.info("transferInitiated():" + event);
    }

    @Override
    public void transferStarted(TransferEvent event)
            throws TransferCancelledException {
        LOGGER.info("transferStarted():" + event);
    }

    @Override
    public void transferProgressed(TransferEvent event)
            throws TransferCancelledException {
        LOGGER.info("transferProgressed():" + event);
    }

    @Override
    public void transferCorrupted(TransferEvent event)
            throws TransferCancelledException {
        LOGGER.info("transferCorrupted():" + event);
    }

    @Override
    public void transferSucceeded(TransferEvent event) {
        LOGGER.info("transferCorrupted():" + event);
    }

    @Override
    public void transferFailed(TransferEvent event) {
        LOGGER.info("transferFailed():" + event);
    }

}
