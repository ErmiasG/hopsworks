/*
 * Copyright (C) 2021, Logical Clocks AB. All rights reserved
 */
package io.hops.hopsworks.cloud;

import io.hops.hopsworks.common.util.OSProcessExecutor;
import io.hops.hopsworks.common.util.ProcessDescriptor;
import io.hops.hopsworks.common.util.ProcessResult;
import io.hops.hopsworks.common.util.Settings;

import javax.annotation.PostConstruct;
import javax.ejb.ConcurrencyManagement;
import javax.ejb.ConcurrencyManagementType;
import javax.ejb.EJB;
import javax.ejb.Singleton;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

@Singleton
@ConcurrencyManagement(ConcurrencyManagementType.BEAN)
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class ACRClientService {
    private static final Logger LOG = Logger.getLogger(ACRClientService.class.getName());

    @EJB
    Settings settings;
    @EJB
    private OSProcessExecutor osProcessExecutor;
    @PostConstruct
    private void initClient() {
    }

    public List<String> deleteImagesWithTagPrefix(final String repositoryName,
      final String imageTagPrefix) {

        String prog = settings.getSudoersDir() + "/dockerImage.sh";
        ProcessDescriptor processDescriptor = new ProcessDescriptor.Builder()
            .addCommand("/usr/bin/sudo")
            .addCommand(prog)
            .addCommand("delete-acr")
            .addCommand(repositoryName)
            .addCommand(imageTagPrefix)
            .redirectErrorStream(true)
            .setWaitTimeout(1, TimeUnit.MINUTES)
            .build();
  
        try {
            ProcessResult processResult =
                osProcessExecutor.execute(processDescriptor);
            if (processResult.getExitCode() != 0) {
                LOG.info("Could not delete docker images in "+repositoryName+" under prefix "+imageTagPrefix+". Exit code: " +
                    processResult.getExitCode() + " out: " + processResult.getStdout());
                return new ArrayList<>();
            }
            return Arrays.asList(processResult.getStdout().split("\n"));
        } catch (IOException ex) {
            LOG.info("Could not delete docker images in "+repositoryName+" under prefix "+imageTagPrefix+". Exception caught: " +
                ex.getMessage());
            return new ArrayList<>();
        }
    }
}
