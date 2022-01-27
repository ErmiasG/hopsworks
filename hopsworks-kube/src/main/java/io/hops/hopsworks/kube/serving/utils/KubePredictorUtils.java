/*
 * Copyright (C) 2022, Logical Clocks AB. All rights reserved
 */

package io.hops.hopsworks.kube.serving.utils;

import io.hops.hopsworks.common.dataset.DatasetController;
import io.hops.hopsworks.common.dataset.util.DatasetHelper;
import io.hops.hopsworks.common.dataset.util.DatasetPath;
import io.hops.hopsworks.common.hdfs.HdfsUsersController;
import io.hops.hopsworks.common.hdfs.Utils;
import io.hops.hopsworks.common.hdfs.inode.InodeController;
import io.hops.hopsworks.common.jupyter.JupyterController;
import io.hops.hopsworks.exceptions.DatasetException;
import io.hops.hopsworks.exceptions.ServiceException;
import io.hops.hopsworks.persistence.entity.dataset.DatasetType;
import io.hops.hopsworks.persistence.entity.project.Project;
import io.hops.hopsworks.persistence.entity.serving.Serving;
import io.hops.hopsworks.persistence.entity.user.Users;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.ws.rs.NotSupportedException;
import java.io.UnsupportedEncodingException;

@Stateless
@TransactionAttribute(TransactionAttributeType.NEVER)
public class KubePredictorUtils {
  
  protected final static String PREDICTOR_NAME_PREFIX = "predictor-";
  private final static String IPYNB_EXTENSION = ".ipynb";
  private final static String PY_EXTENSION = ".py";
  
  @EJB
  private DatasetController datasetController;
  @EJB
  private DatasetHelper datasetHelper;
  @EJB
  private HdfsUsersController hdfsUsersController;
  @EJB
  private InodeController inodeController;
  @EJB
  private JupyterController jupyterController;
  @EJB
  private KubeArtifactUtils kubeArtifactUtils;
  @EJB
  private KubePredictorTensorflowUtils kubePredictorTensorflowUtils;
  @EJB
  private KubePredictorPythonCustomUtils kubePredictorPythonCustomUtils;
  @EJB
  private KubePredictorPythonSklearnUtils kubePredictorPythonSklearnUtils;
  
  public void copyPredictorToArtifactDir(Project project, Users user, Serving serving)
    throws DatasetException, ServiceException {
    
    String sourceFilePath = serving.getPredictor();
    DatasetPath sourceFileDatasetPath = datasetHelper.getDatasetPath(project, sourceFilePath, DatasetType.DATASET);
    String destFilePath = getPredictorFilePath(serving);
    if (serving.getPredictor().endsWith(IPYNB_EXTENSION)) {
      // If predictor file is ipynb, convert it to python script
      String username = hdfsUsersController.getHdfsUserName(project, user);
      JupyterController.NotebookConversion conversionType = jupyterController
        .getNotebookConversionType(sourceFilePath, user, project);
      jupyterController.convertIPythonNotebook(username, sourceFilePath, project, destFilePath, conversionType);
    } else {
      // Otherwise, copy predictor file into model artifact
      DatasetPath destFileDatasetPath = datasetHelper.getDatasetPath(project, destFilePath, DatasetType.DATASET);
      datasetController.copy(project, user, sourceFileDatasetPath.getFullPath(), destFileDatasetPath.getFullPath(),
        sourceFileDatasetPath.getDataset(), destFileDatasetPath.getDataset());
    }
  }
  
  public String getPredictorFileName(Serving serving, Boolean prefix) {
    String predictorPath = serving.getPredictor();
    if (predictorPath == null) {
      return null;
    }
    String name = predictorPath;
    if (predictorPath.contains("/")) {
      String[] splits = predictorPath.split("/");
      name = splits[splits.length - 1];
    }
    if (predictorPath.endsWith(IPYNB_EXTENSION)) {
      name = name.replace(IPYNB_EXTENSION, PY_EXTENSION);
    }
    return prefix ? PREDICTOR_NAME_PREFIX + name : name;
  }
  
  public String getPredictorFilePath(Serving serving) {
    String artifactDirPath = kubeArtifactUtils.getArtifactDirPath(serving);
    String predictorFileName = getPredictorFileName(serving, true);
    return artifactDirPath + "/" + predictorFileName;
  }
  
  public Boolean checkPredictorExists(Serving serving)
    throws UnsupportedEncodingException {
    String hdfsPath = Utils.prepPath(getPredictorFilePath(serving));
    return inodeController.existsPath(hdfsPath);
  }
  
  public KubePredictorServerUtils getPredictorServerUtils(Serving serving) {
    switch (serving.getModelServer()) {
      case TENSORFLOW_SERVING:
        return kubePredictorTensorflowUtils;
      case PYTHON:
        return serving.getPredictor() == null ? kubePredictorPythonSklearnUtils : kubePredictorPythonCustomUtils;
      default:
        throw new NotSupportedException("Model server not supported for KFServing inference services");
    }
  }
}
