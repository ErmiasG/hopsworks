/*
 * Copyright (C) 2021, Logical Clocks AB. All rights reserved
 */

package io.hops.hopsworks.kube.serving.utils;

import com.logicalclocks.servicediscoverclient.exceptions.ServiceDiscoveryException;
import io.fabric8.kubernetes.api.model.ConfigMapVolumeSourceBuilder;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.EnvVarSourceBuilder;
import io.fabric8.kubernetes.api.model.KeyToPathBuilder;
import io.fabric8.kubernetes.api.model.SecretVolumeSourceBuilder;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.hops.hopsworks.common.dataset.DatasetController;
import io.hops.hopsworks.common.dataset.util.DatasetHelper;
import io.hops.hopsworks.common.dataset.util.DatasetPath;
import io.hops.hopsworks.common.hdfs.DistributedFsService;
import io.hops.hopsworks.common.hdfs.HdfsUsersController;
import io.hops.hopsworks.common.hdfs.Utils;
import io.hops.hopsworks.common.hdfs.inode.InodeController;
import io.hops.hopsworks.common.hosts.ServiceDiscoveryController;
import io.hops.hopsworks.common.jupyter.JupyterController;
import io.hops.hopsworks.common.util.ProjectUtils;
import io.hops.hopsworks.common.util.Settings;
import io.hops.hopsworks.exceptions.DatasetException;
import io.hops.hopsworks.exceptions.ServiceException;
import io.hops.hopsworks.kube.common.KubeClientService;
import io.hops.hopsworks.kube.project.KubeProjectConfigMaps;
import io.hops.hopsworks.persistence.entity.dataset.DatasetType;
import io.hops.hopsworks.persistence.entity.hdfs.inode.Inode;
import io.hops.hopsworks.persistence.entity.project.Project;
import io.hops.hopsworks.persistence.entity.serving.Serving;
import io.hops.hopsworks.persistence.entity.user.Users;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

@Stateless
@TransactionAttribute(TransactionAttributeType.NEVER)
public class KubeTransformerUtils {
  
  private final static String TRANSFORMER_NAME_PREFIX = "transformer-";
  private final static String IPYNB_EXTENSION = ".ipynb";
  private final static String PY_EXTENSION = ".py";
  
  @EJB
  private Settings settings;
  @EJB
  private KubeClientService kubeClientService;
  @EJB
  private KubeProjectConfigMaps kubeProjectConfigMaps;
  @EJB
  private ProjectUtils projectUtils;
  @EJB
  private DatasetController datasetController;
  @EJB
  private DatasetHelper datasetHelper;
  @EJB
  private ServiceDiscoveryController serviceDiscoveryController;
  @EJB
  private DistributedFsService dfs;
  @EJB
  private HdfsUsersController hdfsUsersController;
  @EJB
  private InodeController inodeController;
  @EJB
  private JupyterController jupyterController;
  @EJB
  private KubeArtifactUtils kubeArtifactUtils;
  @EJB
  private KubeServingUtils kubeServingUtils;
  
  public void copyTransformerToArtifactDir(Project project, Users user, Serving serving)
    throws DatasetException, ServiceException {
    
    String sourceFilePath = serving.getTransformer();
    DatasetPath sourceFileDatasetPath = datasetHelper.getDatasetPath(project, sourceFilePath, DatasetType.DATASET);
    String destFilePath = getTransformerFilePath(serving);
    if (serving.getTransformer().endsWith(IPYNB_EXTENSION)) {
      // If transformer file is ipynb, convert it to python script
      String username = hdfsUsersController.getHdfsUserName(project, user);
      JupyterController.NotebookConversion conversionType = jupyterController
        .getNotebookConversionType(sourceFilePath, user, project);
      jupyterController.convertIPythonNotebook(username, sourceFilePath, project, destFilePath, conversionType);
    } else {
      // Otherwise, copy transformer file into model artifact
      DatasetPath destFileDatasetPath = datasetHelper.getDatasetPath(project, destFilePath, DatasetType.DATASET);
      datasetController.copy(project, user, sourceFileDatasetPath.getFullPath(), destFileDatasetPath.getFullPath(),
        sourceFileDatasetPath.getDataset(), destFileDatasetPath.getDataset());
    }
  }
  
  public String getTransformerFileName(Serving serving, Boolean prefix) {
    String transformerPath = serving.getTransformer();
    if (transformerPath == null) {
      return null;
    }
    String name = transformerPath;
    if (transformerPath.contains("/")) {
      String[] splits = transformerPath.split("/");
      name = splits[splits.length - 1];
    }
    if (serving.getTransformer().endsWith(IPYNB_EXTENSION)) {
      name = name.replace(IPYNB_EXTENSION, PY_EXTENSION);
    }
    return prefix ? TRANSFORMER_NAME_PREFIX + name : name;
  }
  
  public String getTransformerFilePath(Serving serving) {
    String artifactDirPath = kubeArtifactUtils.getArtifactDirPath(serving);
    String transformerFileName = getTransformerFileName(serving, true);
    return artifactDirPath + "/" + transformerFileName;
  }
  
  public String findTransformerFile(Serving serving) {
    String artifactDirPath = kubeArtifactUtils.getArtifactDirPath(serving);
    for (Inode inode : dfs.getChildInodes(artifactDirPath)) {
      String name = inode.getInodePK().getName();
      if (name.startsWith(TRANSFORMER_NAME_PREFIX) && !inode.isDir()) {
        return name;
      }
    }
    return null;
  }
  
  public JSONObject buildInferenceServiceTransformer(Project project, Users user, Serving serving)
    throws ServiceDiscoveryException {
    
    List<Container> containers = new ArrayList<>();
    containers.add(buildTransformerContainer(project, user, serving));
    
    List<Volume> volumes = buildVolumes(project, user);
    
    return new JSONObject() {
      {
        put("minReplicas", serving.getTransformerInstances());
        put("containers", new JSONArray(containers));
        put("volumes", new JSONArray(volumes));
      }
    };
  }
  
  public Boolean checkTransformerExists(Serving serving)
    throws UnsupportedEncodingException {
    String hdfsPath = Utils.prepPath(getTransformerFilePath(serving));
    return inodeController.existsPath(hdfsPath);
  }
  
  private Container buildTransformerContainer(Project project, Users user, Serving serving)
    throws ServiceDiscoveryException {
    
    List<EnvVar> envVars = buildEnvironmentVariables(project, user, serving);
    List<VolumeMount> volumeMounts = buildVolumeMounts();
    
    return new ContainerBuilder()
      .withName("transformer")
      .withImage(projectUtils.getFullDockerImageName(project, true))
      .withImagePullPolicy(settings.getKubeImagePullPolicy())
      .withCommand("transformer-launcher.sh")
      .withEnv(envVars)
      .withVolumeMounts(volumeMounts)
      .build();
  }
  
  private List<EnvVar> buildEnvironmentVariables(Project project, Users user, Serving serving)
    throws ServiceDiscoveryException {
    List<EnvVar> envVars = new ArrayList<>();
    
    // Transformer launcher
    envVars.add(new EnvVarBuilder()
      .withName("SCRIPT_PATH").withValue(getTransformerFileName(serving, true)).build());
    envVars.add(new EnvVarBuilder().withName("IS_KUBE").withValue("true").build());
    envVars.add(new EnvVarBuilder().withName("IS_TRANSFORMER").withValue("true").build());
    envVars.add(new EnvVarBuilder()
      .withName("PYTHONPATH").withValue(settings.getAnacondaProjectDir() + "/bin/python").build());
    
    // HSFS and HOPS
    envVars.add(new EnvVarBuilder()
      .withName("HADOOP_USER_NAME").withValue(hdfsUsersController.getHdfsUserName(project, user)).build());
    envVars.add(new EnvVarBuilder().withName("REST_ENDPOINT").withValue("https://" + serviceDiscoveryController
      .constructServiceFQDNWithPort(ServiceDiscoveryController.HopsworksService.HOPSWORKS_APP)).build());
    envVars.add(new EnvVarBuilder()
      .withName("REQUESTS_VERIFY").withValue(String.valueOf(settings.getRequestsVerify())).build());
    envVars.add(new EnvVarBuilder().withName("MATERIAL_DIRECTORY").withValue("/certs").build());
    
    // HSFS
    envVars.add(new EnvVarBuilder()
      .withName("PROJECT_ID").withValue(String.valueOf(serving.getProject().getId())).build());
    envVars.add(new EnvVarBuilder().withName("PROJECT_NAME").withValue(serving.getProject().getName()).build());
    envVars.add(new EnvVarBuilder().withName("SECRETS_DIR").withValue("/keys").build());
    
    // HOPS
    envVars.add(new EnvVarBuilder()
      .withName("HOPSWORKS_PROJECT_ID").withValue(String.valueOf(serving.getProject().getId())).build());
    envVars.add(new EnvVarBuilder()
      .withName("HOPSWORKS_PROJECT_NAME").withValue(serving.getProject().getName()).build());
    envVars.add(new EnvVarBuilder().withName("API_KEY").withValueFrom(
      new EnvVarSourceBuilder().withNewSecretKeyRef(
        kubeServingUtils.getApiKeySecretKey(),
        kubeServingUtils.getApiKeySecretName(kubeClientService.getKubeProjectName(project)),
        false)
        .build())
      .build());
    
    return envVars;
  }
  
  private List<Volume> buildVolumes(Project project, Users user) {
    List<Volume> volumes = new ArrayList<>();
    volumes.add(new VolumeBuilder().withName("certs").withSecret(
      new SecretVolumeSourceBuilder().withSecretName(kubeClientService.getKubeDeploymentName(project, user)).build())
      .build());
    volumes.add(new VolumeBuilder().withName("hadoopconf").withConfigMap(
      new ConfigMapVolumeSourceBuilder().withName(kubeProjectConfigMaps.getHadoopConfigMapName(project)).build())
      .build());
    volumes.add(new VolumeBuilder().withName("keys").withSecret(
      new SecretVolumeSourceBuilder()
        .withSecretName(kubeServingUtils.getApiKeySecretName(kubeClientService.getKubeProjectName(project)))
        .withItems(new KeyToPathBuilder()
          .withKey(kubeServingUtils.getApiKeySecretKey())
          .withPath("api.key").build())
        .build())
      .build());
    return volumes;
  }
  
  private List<VolumeMount> buildVolumeMounts() {
    List<VolumeMount> volumeMounts = new ArrayList<>();
    volumeMounts.add(new VolumeMountBuilder().withName("certs").withReadOnly(true).withMountPath("/certs").build());
    volumeMounts.add(new VolumeMountBuilder().withName("hadoopconf").withReadOnly(true)
      .withMountPath(settings.getHadoopSymbolicLinkDir() + "/etc/hadoop").build());
    volumeMounts.add(new VolumeMountBuilder().withName("keys").withReadOnly(true).withMountPath("/keys").build());
    return volumeMounts;
  }
}
