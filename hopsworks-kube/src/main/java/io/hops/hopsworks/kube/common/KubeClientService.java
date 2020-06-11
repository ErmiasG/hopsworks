/*
 * Copyright (C) 2018, Logical Clocks AB. All rights reserved
 */

package io.hops.hopsworks.kube.common;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.QuantityBuilder;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentStatus;
import io.fabric8.kubernetes.api.model.batch.Job;
import io.fabric8.kubernetes.api.model.batch.JobCondition;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.github.resilience4j.retry.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.hops.hopsworks.common.util.Settings;
import io.hops.hopsworks.persistence.entity.jobs.configuration.DockerJobConfiguration;
import io.hops.hopsworks.persistence.entity.jobs.history.Execution;
import io.hops.hopsworks.persistence.entity.project.Project;
import io.hops.hopsworks.persistence.entity.user.Users;
import io.hops.hopsworks.exceptions.ServiceException;
import io.hops.hopsworks.restutils.RESTCodes;

import javax.annotation.PostConstruct;
import javax.ejb.Asynchronous;
import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.hops.hopsworks.common.util.Settings.CERT_PASS_SUFFIX;
import static io.hops.hopsworks.common.util.Settings.HOPS_USERNAME_SEPARATOR;
import static io.hops.hopsworks.common.util.Settings.KEYSTORE_SUFFIX;
import static io.hops.hopsworks.common.util.Settings.TRUSTSTORE_SUFFIX;


@Stateless
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class KubeClientService {
  
  private static final Logger LOGGER = Logger.getLogger(KubeClientService.class.getName());
  
  @EJB
  private Settings settings;

  private KubernetesClient client = null;

  @PostConstruct
  private void initClient() {
    Config config = new ConfigBuilder()
        .withUsername(settings.getKubeUser())
        .withMasterUrl(settings.getKubeMasterUrl())
        .withCaCertFile(settings.getKubeCaCertfile())
        .withTrustStoreFile(settings.getKubeTruststorePath())
        .withTrustStorePassphrase(settings.getKubeTruststoreKey())
        .withKeyStoreFile(settings.getKubeKeystorePath())
        .withKeyStorePassphrase(settings.getKubeKeystoreKey())
        .withClientCertFile(settings.getKubeClientCertfile())
        .withClientKeyFile(settings.getKubeClientKeyfile())
        .withClientKeyPassphrase(settings.getKubeClientKeypass())
        .build();

    client = new DefaultKubernetesClient(config);
  }

  public void createProjectNamespace(Project project) throws KubernetesClientException {
    client.namespaces().createNew()
        .withNewMetadata()
        .withName(getKubeProjectName(project))
        .endMetadata()
        .done();
  }

  public void deleteProjectNamespace(Project project) throws KubernetesClientException {
    client.namespaces().withName(getKubeProjectName(project)).delete();
  }
  
  @Asynchronous
  public void createOrUpdateConfigMap(Execution execution, String suffix, Map<String, String> filenameToContent)
    throws KubernetesClientException {
  
    String kubeProjectNS = getKubeProjectName(execution.getJob().getProject());
    String kubeProjectDeployment = getKubeDeploymentName(execution) + suffix;
    createOrUpdateConfigMap(kubeProjectNS, kubeProjectDeployment, filenameToContent);
  }
  
  @Asynchronous
  public void createOrUpdateConfigMap(Project project, Users user, String suffix, Map<String, String> filenameToContent)
    throws KubernetesClientException {
    
    String kubeProjectNS = getKubeProjectName(project);
    String kubeProjectDeployment = getKubeDeploymentName(kubeProjectNS, user) + suffix;
    createOrUpdateConfigMap(kubeProjectNS, kubeProjectDeployment, filenameToContent);
  }
  
  @Asynchronous
  public void createOrUpdateConfigMap(String kubeProjectNS, String kubeProjectDeployment,
    Map<String, String> filenameToContent) throws KubernetesClientException {
    
    ConfigMap configMap = new ConfigMapBuilder()
      .withMetadata(
        new ObjectMetaBuilder()
          .withName(kubeProjectDeployment)
          .build())
      .withData(filenameToContent)
      .build();
    
    client.configMaps().inNamespace(kubeProjectNS).createOrReplace(configMap);
  }
  
  @Asynchronous
  public void deleteConfigMap(String namespace, String secretName) throws KubernetesClientException {
    client.configMaps().inNamespace(namespace).delete(
      new ConfigMapBuilder()
        .withMetadata(
          new ObjectMetaBuilder()
            .withName(secretName)
            .build())
        .build());
  }
  
  @Asynchronous
  public void createOrUpdateSecret(Project project, Users user, String suffix, Map<String, byte[]> filenameToContent)
      throws KubernetesClientException {
    
    String kubeProjectNS = getKubeProjectName(project);
    String kubeProjectSecretName = getKubeDeploymentName(kubeProjectNS, user) + suffix;
    
    createOrUpdateSecret(kubeProjectNS, kubeProjectSecretName, filenameToContent, null);
  }
  
  @Asynchronous
  public void createOrUpdateSecret(Execution execution, String suffix,
    Map<String, byte[]> filenameToContent, Map<String, String> labels)
    throws KubernetesClientException {
    
    String kubeProjectNS = getKubeProjectName(execution.getJob().getProject());
    String kubeProjectSecretName =
      getKubeDeploymentName(execution) + suffix;
    createOrUpdateSecret(kubeProjectNS, kubeProjectSecretName, filenameToContent, labels);
  }
  
  @Asynchronous
  public void createOrUpdateSecret(String projectNamespace, String deploymentName,
    Map<String, byte[]> filenameToContent, Map<String, String> labels) throws KubernetesClientException {
    
    Secret secret = new SecretBuilder()
      .withMetadata(
        new ObjectMetaBuilder()
          .withName(deploymentName)
          .withLabels(labels)
          .build())
      .withData(filenameToContent
        .entrySet()
        .stream()
        .collect(Collectors.toMap(Map.Entry::getKey, e -> Base64.getEncoder().encodeToString(e.getValue()))))
      .build();
    
    client.secrets().inNamespace(projectNamespace).createOrReplace(secret);
  }
  
  @Asynchronous
  public void deleteSecret(Project project, Users user, String suffix) throws KubernetesClientException {
    String kubeProjectNS = getKubeProjectName(project);
    String kubeProjectSecretName = getKubeDeploymentName(kubeProjectNS, user) + suffix;
    deleteSecret(kubeProjectNS, kubeProjectSecretName);
  }
  
  @Asynchronous
  public void deleteSecret(Execution execution, String suffix) throws KubernetesClientException {
    String kubeProjectNS = getKubeProjectName(execution.getJob().getProject());
    String kubeProjectSecretName = getKubeDeploymentName(execution) + suffix;
    deleteSecret(kubeProjectNS, kubeProjectSecretName);
  }

  @Asynchronous
  public void deleteSecret(String namespace, String secretName) throws KubernetesClientException {
    client.secrets().inNamespace(namespace).delete(
      new SecretBuilder()
        .withMetadata(
          new ObjectMetaBuilder()
            .withName(secretName)
            .build())
        .build());
  }

  public void createTLSSecret(Project project, Users user, byte[] keyStore, byte[] trustStore,
                              String keyPassword) throws KubernetesClientException {
    
    String projectUsername = getProjectUsername(project, user);

    Map<String, byte[]> secretData = new HashMap<>();
    secretData.put(projectUsername + CERT_PASS_SUFFIX, keyPassword.getBytes());
    secretData.put(projectUsername + KEYSTORE_SUFFIX, keyStore);
    secretData.put(projectUsername + TRUSTSTORE_SUFFIX, trustStore);

    createOrUpdateSecret(project, user, "", secretData);
  }

  @Asynchronous
  public void deleteTLSSecret(Project project, Users user) throws KubernetesClientException {
    deleteSecret(project, user, "");
  }
  
  /**
   * Returns a list of Kube secrets with potentially matching labels.
   */
  public List<Secret> getSecrets(Map<String, String> labels) {
    return client.secrets().inAnyNamespace().withLabels(labels).list().getItems();
  }
  
  
  @Asynchronous
  public void deleteDeployment(String namespace, String deploymentName)
    throws KubernetesClientException{
    deleteDeployment(
      namespace,
      new ObjectMetaBuilder()
        .withName(deploymentName)
        .build());
  }

  @Asynchronous
  public void deleteDeployment(Project project, ObjectMeta deploymentMetadata)
      throws KubernetesClientException{
    deleteDeployment(getKubeProjectName(project), deploymentMetadata);
  }

  @Asynchronous
  public void deleteDeployment(String namespace, ObjectMeta deploymentMetadata)
    throws KubernetesClientException{
    client.apps().deployments().inNamespace(namespace)
      .delete(new DeploymentBuilder().withMetadata(deploymentMetadata).build());
  }

  @Asynchronous
  public void deleteService(String namespace, String serviceName)
    throws KubernetesClientException{
    deleteService(
      namespace,
      new ObjectMetaBuilder()
        .withName(serviceName)
        .build());
  }

  @Asynchronous
  public void deleteService(Project project, ObjectMeta serviceMetadata)
      throws KubernetesClientException{
    deleteService(getKubeProjectName(project), serviceMetadata);
  }

  @Asynchronous
  public void deleteService(String namespace, ObjectMeta serviceMetadata)
    throws KubernetesClientException{
    client.services().inNamespace(namespace)
      .delete(new ServiceBuilder().withMetadata(serviceMetadata).build());
  }
  
  @Asynchronous
  public void deleteJob(String namespace, String kubeJobName) throws KubernetesClientException {
    try {
      boolean deleted = client.batch().jobs().inNamespace(namespace).withName(kubeJobName).delete();
      LOGGER.info("Deleted job: " + kubeJobName + ", status: " + deleted);
    } catch (KubernetesClientException ex){
      if(ex.getStatus().getCode() == 500) {
        LOGGER.log(Level.WARNING, null, ex);
      }
      throw ex;
    }
  }
  
  @Asynchronous
  public void stopExecution(String prefix, Execution execution) throws KubernetesClientException{
    stopJob(getKubeProjectName(execution.getJob().getProject()), prefix + getKubeDeploymentName(execution));
  }
  
  @Asynchronous
  private void stopJob(String namespace, String kubeJobName) throws KubernetesClientException{
    Job job = client.batch().jobs().inNamespace(namespace).withName(kubeJobName).get();
    if(job.getStatus().getConditions().isEmpty()){
      JobCondition condition = new JobCondition();
      condition.setType("Failed");
      job.getStatus().getConditions().add(condition);
    }
    job.getStatus().getConditions().get(0).setType("Failed");
    client.batch().jobs().inNamespace(namespace).withName(kubeJobName).replace(job);
    LOGGER.info("Stopped job: " + kubeJobName);
  }

  
  public List<Job> getJobs(){
    return client.batch().jobs().inAnyNamespace().list().getItems();
  }
  
  
  @Asynchronous
  public void createOrReplaceDeployment(Project project, Deployment deployment)
      throws KubernetesClientException {
    String kubeProjectNs = getKubeProjectName(project);
    client.apps().deployments().inNamespace(kubeProjectNs).createOrReplace(deployment);
  }

  @Asynchronous
  public void createOrReplaceService(Project project, Service service) {
    String kubeProjectNs = getKubeProjectName(project);
    client.services().inNamespace(kubeProjectNs).createOrReplace(service);
  }
  
  public void createJob(Project project, Job job)
    throws KubernetesClientException {
    String kubeProjectNs = getKubeProjectName(project);
    client.batch().jobs().inNamespace(kubeProjectNs).create(job);
  }
  
  public void waitForDeployment(Project project, String deploymentName, int maxAttempts) throws TimeoutException {
    RetryConfig retryConfig = RetryConfig.<Optional<Integer>>custom()
      .maxAttempts(maxAttempts)
      .intervalFunction(IntervalFunction.ofExponentialBackoff(400L, 1.3D))
      .retryOnResult(o -> o.map(replicas -> replicas < 1).orElse(true))
      .build();
    Retry retry = Retry.of("waitForDeployment: " + deploymentName, retryConfig);
    Retry.decorateSupplier(retry, () -> getDeploymentStatus(project, deploymentName, 1)
        .map(o -> o.getAvailableReplicas()))
      .get()
      .orElseThrow(() -> new TimeoutException("Timed out waiting for Jupyter pod startup"));
  }

  public Optional<DeploymentStatus> getDeploymentStatus(Project project, String deploymentName, int maxAttempts) {
    RetryConfig retryConfig = RetryConfig.<DeploymentStatus>custom()
        .maxAttempts(maxAttempts)
        .intervalFunction(IntervalFunction.ofExponentialBackoff())
        .retryOnResult(x -> x == null)
        .build();
    Retry retry = Retry.of("getDeploymentStatus: " + deploymentName, retryConfig);
    Supplier<DeploymentStatus> supplier = Retry.decorateSupplier(retry, () ->
        this.getDeploymentStatus(project, deploymentName));
    return Optional.ofNullable(supplier.get());
  }

  public DeploymentStatus getDeploymentStatus(Project project, String deploymentName)
      throws KubernetesClientException{
    String kubeProjectNs = getKubeProjectName(project);

    Deployment deployment = client.apps().deployments().inNamespace(kubeProjectNs)
        .withName(deploymentName).get();
    return deployment == null ? null : deployment.getStatus();
  }
  
  public Optional<Service> getServiceInfo(Project project, String serviceName, int maxAttempts) {
    RetryConfig retryConfig = RetryConfig.<Service>custom()
      .maxAttempts(maxAttempts)
      .intervalFunction(IntervalFunction.ofExponentialBackoff())
      .retryOnResult(x -> x == null)
      .build();
    Retry retry = Retry.of("getServiceInfo: " + serviceName, retryConfig);
    Supplier<Service> supplier = Retry.decorateSupplier(retry, () -> this.getServiceInfo(project, serviceName));
    return Optional.ofNullable(supplier.get());
  }

  public Service getServiceInfo(Project project, String serviceName)
      throws KubernetesClientException{
    String kubeProjectNs = getKubeProjectName(project);
    return client.services().inNamespace(kubeProjectNs).withName(serviceName).get();
  }

  public List<Service> getServices(String label) throws KubernetesClientException{
    return client.services().inAnyNamespace().withLabel(label).list().getItems();
  }
  
  public List<Pod> getPodList(Project project, Map<String, String> podLabels)
    throws KubernetesClientException {
    return getPodList(getKubeProjectName(project), podLabels);
  }
  
  public List<Pod> getPodList(String kubeProjectNs, Map<String, String> podLabels)
      throws KubernetesClientException {
    return client.pods().inNamespace(kubeProjectNs)
            .withLabels(podLabels).list().getItems();
  }
  
  public List<Pod> getPodList(Map<String, String> podLabels)
    throws KubernetesClientException {
    return client.pods().inAnyNamespace()
      .withLabels(podLabels).list().getItems();
  }

  /* In Kubernetes, most of the regex to validate names do not allow the _.
   * For this reason we replace _ with - which is allowed.
   * Hopsworks projects cannot contain -.
   * All chars should be lowercase
   */
  public String getKubeProjectName(Project project) {
    return project.getName().toLowerCase().replaceAll("[^a-z0-9-]", "-");
  }
  
  public String getKubeDeploymentName(Project project, Users user) {
    return this.getKubeDeploymentName(this.getKubeProjectName(project), user.getUsername());
  }
  
  public String getKubeDeploymentName(String kubeProjectNS, Users user) {
    return this.getKubeDeploymentName(kubeProjectNS, user.getUsername());
  }
  
  public String getKubeDeploymentName(Execution execution) {
    return this.getKubeDeploymentName(this.getKubeProjectName(execution.getJob().getProject()),
      execution.getUser().getUsername(),
      execution.getJob().getName(), Integer.toString(execution.getId()));
  }
  
  /**
   * From 0.6.0 usernames are alphanumeric only
   * Previous versions contain _ and -, to maintain compatibility we convert the _ to -
   * There might be some usernames that end with -, this is not allowed. In this case we add a 0 at the end
   * @param kubeProjectName
   * @param params username, jobName, executionId
   * @return kubernetes deployment name for Jupyter and Jobs
   */
  private String getKubeDeploymentName(String kubeProjectName, String... params) {
    StringBuilder kubeName = new StringBuilder(kubeProjectName).append("--");
    for (String param : params) {
      kubeName.append(param.toLowerCase().replaceAll("[^a-z0-9-]", "-"));
      if (kubeName.lastIndexOf("-") == kubeName.length() - 1) {
        kubeName.replace(kubeName.length() - 1, kubeName.length(), "0");
      }
      kubeName.append("--");
    }
    
    return kubeName.toString().replaceAll("--$","");
  }

  private String getProjectUsername(Project project, Users user) {
    return project.getName() + HOPS_USERNAME_SEPARATOR + user.getUsername();
  }

  public List<Node> getNodeList() throws KubernetesClientException {
    return client.nodes().list().getItems();

  }

  public List<String> getNodeIpList() throws KubernetesClientException {
    // Extract node IPs
    List<String> nodeIPList = getNodeList().stream()
        .flatMap(node -> node.getStatus().getAddresses().stream())
        .filter(nodeAddress -> nodeAddress.getType().equals("InternalIP"))
        .flatMap(nodeAddress -> Stream.of(nodeAddress.getAddress()))
        .collect(Collectors.toList());

    Collections.shuffle(nodeIPList);

    return nodeIPList;
  }
  
  public ResourceRequirements buildResourceRequirements(DockerJobConfiguration dockerConfig) throws ServiceException {

    if(dockerConfig.getMemory() > settings.getKubeDockerMaxMemoryAllocation()) {
      throw new ServiceException(RESTCodes.ServiceErrorCode.JUPYTER_START_ERROR, Level.FINE, "Exceeded maximum memory "
          + "allocation allowed for Jupyter Notebook server: " + settings.getKubeDockerMaxMemoryAllocation() + "MB");
    } else if(dockerConfig.getCores() > settings.getKubeDockerMaxCoresAllocation()) {
      throw new ServiceException(RESTCodes.ServiceErrorCode.JUPYTER_START_ERROR, Level.FINE, "Exceeded maximum cores "
          + "allocation allowed for Jupyter Notebook server: " + settings.getKubeDockerMaxCoresAllocation() + " cores");
    } else if(dockerConfig.getGpus() > settings.getKubeDockerMaxGpusAllocation()) {
      throw new ServiceException(RESTCodes.ServiceErrorCode.JUPYTER_START_ERROR, Level.FINE, "Exceeded maximum gpus "
        + "allocation allowed for Jupyter Notebook server: " + settings.getKubeDockerMaxGpusAllocation() + " gpus");
    }

    ResourceRequirementsBuilder resources = new ResourceRequirementsBuilder();
    resources
        .addToLimits("memory", new Quantity(dockerConfig.getMemory() + "Mi"))
        .addToLimits("cpu", new QuantityBuilder().withAmount(
            Double.toString(dockerConfig.getCores() * settings.getKubeDockerCoresFraction())).build());

    int requestedGPUs = dockerConfig.getGpus();
    if(requestedGPUs > 0) {
      resources.addToLimits("nvidia.com/gpu", new QuantityBuilder()
          .withAmount(Double.toString(dockerConfig.getGpus())).build());
    }
    return resources.build();
  }
  
  public List<EnvVar> getEnvVars(Map<String, String> envVarsMap) {
    if(envVarsMap == null || envVarsMap.isEmpty()){
      return new ArrayList<>();
    }
    //Convert envVars map to List of envVars
    return envVarsMap.keySet().stream()
      .map(name -> new EnvVarBuilder().withName(name).withValue(envVarsMap.get(name)).build())
      .collect(Collectors.toList());
  }
}