/*
 * Copyright (C) 2020, Logical Clocks AB. All rights reserved
 */

package io.hops.hopsworks.jobs;

import com.google.common.base.Strings;
import io.fabric8.kubernetes.api.model.ContainerState;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.hops.hopsworks.common.dao.jobhistory.ExecutionFacade;
import io.hops.hopsworks.common.hdfs.DistributedFileSystemOps;
import io.hops.hopsworks.common.hdfs.DistributedFsService;
import io.hops.hopsworks.common.jobs.ExecutionJWT;
import io.hops.hopsworks.common.jobs.JobsMonitor;
import io.hops.hopsworks.common.jobs.yarn.YarnLogUtil;
import io.hops.hopsworks.common.util.Settings;
import io.hops.hopsworks.kube.common.KubeClientService;
import io.hops.hopsworks.persistence.entity.jobs.configuration.JobType;
import io.hops.hopsworks.persistence.entity.jobs.configuration.history.JobState;
import io.hops.hopsworks.persistence.entity.jobs.history.Execution;

import javax.ejb.EJB;
import javax.ejb.Schedule;
import javax.ejb.Singleton;
import javax.ejb.Timer;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@Singleton
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class KubeJobsMonitor implements JobsMonitor {
  
  private static final Logger LOGGER = Logger.getLogger(KubeJobsMonitor.class.getName());
  
  @EJB
  KubeClientService kubeClientService;
  @EJB
  private JobsJWTManager jobsJWTManager;
  @EJB
  private ExecutionFacade executionFacade;
  @EJB
  private DistributedFsService dfs;
  
  @Schedule(persistent = false,
    second = "*/5",
    minute = "*",
    hour = "*")
  public synchronized void monitor(Timer timer) {
    LOGGER.log(Level.FINE, "Running KubeJobsMonitor timer");
    try {
      // Get all non-finished executions of type Python, if they don't exist in kubernetes, set them to failed
      List<Execution> pendingExecutions = executionFacade.findByTypeAndStates(JobType.PYTHON,
        JobState.getKubeRunningStates());
      
      Map<String, String> label = new HashMap<>();
      label.put("deployment-type", "job");
      List<Pod> pods = kubeClientService.getPodList(label);
      // Go through all fetched jobs and update state accordingly
      for (Pod pod : pods) {
        int executionId = Integer.parseInt(pod.getMetadata().getLabels().get("execution"));
        if (executionFacade.findById(executionId).isPresent()) {
          Execution execution = executionFacade.findById(executionId).get();
          pendingExecutions.remove(execution);
          LOGGER.log(Level.FINEST, "Execution: " + execution + ", with state:" + execution.getState());
          if (execution.getState() == JobState.FINISHED
            || execution.getState() == JobState.FAILED
            || execution.getState() == JobState.KILLED) {
            cleanUpExecution(execution, pod);
          } else {
            //Get the app container
            for (ContainerStatus containerStatus : pod.getStatus().getContainerStatuses()) {
              if (containerStatus.getName().equals(JobType.PYTHON.getName().toLowerCase())) {
                ContainerState containerState = containerStatus.getState();
                if (containerState.getTerminated() != null) {
                  LOGGER.log(Level.FINEST, "reason: " + containerState.getTerminated().getReason() + ", pod: " + pod);
                  execution.setState(KubeJobType.getAsJobState(containerState.getTerminated().getReason()));
                  cleanUpExecution(execution, pod);
                } else if (containerState.getWaiting() != null) {
                  String reason = containerState.getWaiting().getReason();
                  String message = containerState.getWaiting().getMessage();
                  // The timeout cannot be set individually per job, it has to be done in kubelet. This is a more
                  // flexible way to fail a waiting app and get the logs
                  if (!Strings.isNullOrEmpty(reason) &&
                    !reason.equals("ContainerCreating") &&
                    execution.getExecutionDuration() > Settings.PYTHON_JOB_KUBE_WAITING_TIMEOUT_MS) {
                    LOGGER.log(Level.FINEST, "reason: " + containerState.getTerminated().getReason() + ", pod: " + pod);
                    execution.setState(KubeJobType.getAsJobState(reason));
                    cleanUpExecution(execution, pod);
                    // Write log in Logs dataset
                    DistributedFileSystemOps udfso = null;
                    try {
                      udfso = dfs.getDfsOps(execution.getHdfsUser());
                      YarnLogUtil.writeLog(udfso, execution.getStderrPath(),
                        "Job failed with: " + reason + " - " + message);
                    } finally {
                      if (udfso != null) {
                        dfs.closeDfsClient(udfso);
                      }
                    }
                  }
                } else {
                  updateState(JobState.RUNNING, execution);
                }
              }
            }
          }
          LOGGER.log(Level.FINEST, "Execution: " + execution + ", state:" + execution.getState());
        } else {
          LOGGER.log(Level.WARNING, "Execution with id: " + executionId + " not found");
          kubeClientService.deleteJob(pod.getMetadata().getNamespace(), pod.getMetadata().getLabels()
            .get("job-name"));
        }
      }
      if (!pendingExecutions.isEmpty()) {
        for (Execution execution : pendingExecutions) {
          // If execution in pending but job was not submitted yet to kubernetes as it is asynchronous, the job would
          // be marked as failed. Therefore we give some margin to kubernetes to actually start the job.
          if (execution.getExecutionDuration() > 20000) {
            updateState(JobState.FAILED, execution);
            execution.setExecutionStop(System.currentTimeMillis());
            execution.setProgress(1);
          }
        }
      }
    } catch (Exception ex) {
      LOGGER.log(Level.WARNING, "KubeJobsMonitor exception", ex);
    }
  }
  
  private void cleanUpExecution(Execution execution, Pod pod) {
    LOGGER.log(Level.FINEST, "Execution: " + execution + ", with state:" + execution.getState() + ", pod: " + pod);
    if (execution.getExecutionStop() < 1) {
      execution.setExecutionStop(System.currentTimeMillis());
      execution.setProgress(1);
      execution = executionFacade.update(execution);
    }
    jobsJWTManager.cleanJWT(new ExecutionJWT(execution));
    kubeClientService.deleteJob(pod.getMetadata().getNamespace(), pod.getMetadata().getLabels()
      .get("job-name"));
  }
  
  @Override
  public Execution updateProgress(float progress, Execution execution) {
    return executionFacade.updateProgress(execution, progress);
  }
  
  @Override
  public Execution updateState(JobState newState, Execution execution) {
    return executionFacade.updateState(execution, newState);
  }
  
  private enum KubeJobType {
    FAILED("Failed"), // taken from kubernetes fabric8 API
    COMPLETED("Completed"); //taken from kubernetes fabric8 API
    
    private final String name;
    
    KubeJobType(String name) {
      this.name = name;
    }
    
    @Override
    public String toString() {
      return name;
    }
    
    public static JobState getAsJobState(String kubeJobType) {
      if (!Strings.isNullOrEmpty(kubeJobType) && kubeJobType.equals(KubeJobType.COMPLETED.name)) {
        return JobState.FINISHED;
      }
      return JobState.FAILED;
    }
  }
  
}