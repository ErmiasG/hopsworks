/*
 * This file is part of Hopsworks
 * Copyright (C) 2019, Logical Clocks AB. All rights reserved
 *
 * Hopsworks is free software: you can redistribute it and/or modify it under the terms of
 * the GNU Affero General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * Hopsworks is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 * PURPOSE.  See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program.
 * If not, see <https://www.gnu.org/licenses/>.
 */
package io.hops.hopsworks.api.modelregistry.models;

import io.hops.hopsworks.api.modelregistry.dto.ModelRegistryDTO;
import io.hops.hopsworks.api.modelregistry.models.dto.ModelDTO;
import io.hops.hopsworks.common.api.ResourceRequest;
import io.hops.hopsworks.common.dao.AbstractFacade;
import io.hops.hopsworks.common.dao.hdfsUser.HdfsUsersFacade;
import io.hops.hopsworks.common.dao.project.ProjectFacade;
import io.hops.hopsworks.common.dao.project.team.ProjectTeamFacade;
import io.hops.hopsworks.common.dao.user.UserFacade;
import io.hops.hopsworks.common.featurestore.FeaturestoreFacade;
import io.hops.hopsworks.common.featurestore.trainingdatasets.TrainingDatasetDTO;
import io.hops.hopsworks.common.hdfs.Utils;
import io.hops.hopsworks.common.provenance.core.ProvParser;
import io.hops.hopsworks.common.provenance.ops.ProvLinks;
import io.hops.hopsworks.common.provenance.ops.ProvLinksParamBuilder;
import io.hops.hopsworks.common.provenance.ops.ProvOpsControllerIface;
import io.hops.hopsworks.common.provenance.ops.dto.ProvLinksDTO;
import io.hops.hopsworks.common.provenance.ops.dto.ProvOpsDTO;
import io.hops.hopsworks.common.hdfs.HdfsUsersController;
import io.hops.hopsworks.common.provenance.core.Provenance;
import io.hops.hopsworks.common.provenance.state.ProvStateParamBuilder;
import io.hops.hopsworks.common.provenance.state.ProvStateParser;
import io.hops.hopsworks.common.provenance.state.ProvStateController;
import io.hops.hopsworks.common.provenance.state.dto.ProvStateDTO;
import io.hops.hopsworks.common.provenance.util.ProvHelper;
import io.hops.hopsworks.common.util.Settings;
import io.hops.hopsworks.exceptions.DatasetException;
import io.hops.hopsworks.exceptions.GenericException;
import io.hops.hopsworks.exceptions.ModelRegistryException;
import io.hops.hopsworks.exceptions.ProvenanceException;
import io.hops.hopsworks.persistence.entity.featurestore.Featurestore;
import io.hops.hopsworks.persistence.entity.hdfs.user.HdfsUsers;
import io.hops.hopsworks.persistence.entity.project.Project;
import io.hops.hopsworks.persistence.entity.project.team.ProjectTeam;
import io.hops.hopsworks.persistence.entity.user.Users;
import io.hops.hopsworks.restutils.RESTCodes;
import org.elasticsearch.search.sort.SortOrder;
import org.javatuples.Pair;
import org.json.JSONObject;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;
import javax.ws.rs.core.UriInfo;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

@Stateless
@TransactionAttribute(TransactionAttributeType.NEVER)
public class ModelsBuilder {

  public final static String MODEL_SUMMARY_XATTR_NAME = "model_summary";

  private static final Logger LOGGER = Logger.getLogger(ModelsBuilder.class.getName());
  @EJB
  private ProvStateController provenanceController;
  @EJB
  private ModelConverter modelConverter;
  @EJB
  private Settings settings;
  @EJB
  private UserFacade userFacade;
  @EJB
  private HdfsUsersFacade hdfsUsersFacade;
  @EJB
  private HdfsUsersController hdfsUsersController;
  @EJB
  private ProjectFacade projectFacade;
  @EJB
  private ProjectTeamFacade projectTeamFacade;
  @EJB
  private FeaturestoreFacade featurestoreFacade;
  @EJB
  private ModelsController modelsController;
  @Inject
  private ProvOpsControllerIface provOpsController;
  
  public ModelDTO uri(ModelDTO dto, UriInfo uriInfo, Project userProject, Project modelRegistryProject) {
    dto.setHref(uriInfo.getBaseUriBuilder()
      .path(ResourceRequest.Name.PROJECT.toString().toLowerCase())
      .path(Integer.toString(userProject.getId()))
      .path(ResourceRequest.Name.MODELREGISTRIES.toString().toLowerCase())
      .path(Integer.toString(modelRegistryProject.getId()))
      .path(ResourceRequest.Name.MODELS.toString().toLowerCase())
      .build());
    return dto;
  }
  
  public ModelDTO uri(ModelDTO dto, UriInfo uriInfo, Project userProject, Project modelRegistryProject,
                      ProvStateDTO fileProvenanceHit) {
    dto.setHref(uriInfo.getBaseUriBuilder()
      .path(ResourceRequest.Name.PROJECT.toString().toLowerCase())
      .path(Integer.toString(userProject.getId()))
      .path(ResourceRequest.Name.MODELREGISTRIES.toString().toLowerCase())
      .path(Integer.toString(modelRegistryProject.getId()))
      .path(ResourceRequest.Name.MODELS.toString().toLowerCase())
      .path(fileProvenanceHit.getMlId())
      .build());
    return dto;
  }

  public ModelDTO expand(ModelDTO dto, ResourceRequest resourceRequest) {
    if (resourceRequest != null && resourceRequest.contains(ResourceRequest.Name.MODELS)) {
      dto.setExpand(true);
    }
    return dto;
  }

  //Build collection
  public ModelDTO build(UriInfo uriInfo,
                        ResourceRequest resourceRequest,
                        Project userProject,
                        Project modelRegistryProject)
    throws ModelRegistryException, GenericException {
    ModelDTO dto = new ModelDTO();
    uri(dto, uriInfo, userProject, modelRegistryProject);
    expand(dto, resourceRequest);
    dto.setCount(0l);
    if(dto.isExpand()) {
      validatePagination(resourceRequest);
      ProvStateDTO fileState;
      try {
        Pair<ProvStateParamBuilder, ModelRegistryDTO> provFilesParamBuilder
          = buildModelProvenanceParams(userProject, modelRegistryProject, resourceRequest);
        if(provFilesParamBuilder.getValue1() == null) {
          //no endpoint - no results
          return dto;
        }

        fileState = provenanceController.provFileStateList(
                provFilesParamBuilder.getValue1().getParentProject(),
                provFilesParamBuilder.getValue0());

        List<ProvStateDTO> models = new LinkedList<>(fileState.getItems());

        dto.setCount(fileState.getCount());
        for(ProvStateDTO fileProvStateHit: models) {
          ModelDTO modelDTO
            = build(uriInfo, resourceRequest, userProject, modelRegistryProject, fileProvStateHit);
          if(modelDTO != null) {
            dto.addItem(modelDTO);
          }
        }
      } catch (ProvenanceException e) {
        if (ProvHelper.missingMappingForField( e)) {
          LOGGER.log(Level.WARNING, "Could not find elastic mapping for experiments query", e);
          return dto;
        } else {
          throw new ModelRegistryException(RESTCodes.ModelRegistryErrorCode.MODEL_LIST_FAILED, Level.FINE,
            "Unable to list models for project " + modelRegistryProject.getName(), e.getMessage(), e);
        }
      } catch(DatasetException e) {
        throw new ModelRegistryException(RESTCodes.ModelRegistryErrorCode.MODEL_LIST_FAILED, Level.FINE,
          "Unable to list models for project " + modelRegistryProject.getName(), e.getMessage(), e);
      }
    }
    return dto;
  }

  //Build specific
  public ModelDTO build(UriInfo uriInfo,
                        ResourceRequest resourceRequest,
                        Project userProject,
                        Project modelRegistryProject,
                        ProvStateDTO fileProvenanceHit)
    throws ModelRegistryException, GenericException, ProvenanceException {
    
    ModelDTO modelDTO = new ModelDTO();
    uri(modelDTO, uriInfo, userProject, modelRegistryProject, fileProvenanceHit);
    if (expand(modelDTO, resourceRequest).isExpand()) {
      if (fileProvenanceHit.getXattrs() != null
        && fileProvenanceHit.getXattrs().containsKey(MODEL_SUMMARY_XATTR_NAME)) {
        JSONObject summary = new JSONObject(fileProvenanceHit.getXattrs().get(MODEL_SUMMARY_XATTR_NAME));
        ModelDTO modelSummary = modelConverter.unmarshalDescription(summary.toString());
        modelDTO.setId(fileProvenanceHit.getMlId());
        modelDTO.setName(modelSummary.getName());
        modelDTO.setVersion(modelSummary.getVersion());
        modelDTO.setUserFullName(modelSummary.getUserFullName());
        modelDTO.setCreated(fileProvenanceHit.getCreateTime());
        modelDTO.setMetrics(modelSummary.getMetrics());
        modelDTO.setDescription(modelSummary.getDescription());
        modelDTO.setProgram(modelSummary.getProgram());
        modelDTO.setInputExample(modelSummary.getInputExample());
        modelDTO.setFramework(modelSummary.getFramework());
        modelDTO.setSignature(modelSummary.getSignature());
        modelDTO.setEnvironment(modelSummary.getEnvironment());
        modelDTO.setExperimentId(modelSummary.getExperimentId());
        modelDTO.setExperimentProjectName(modelSummary.getExperimentProjectName());
        modelDTO.setProjectName(modelSummary.getProjectName());
        modelDTO.setModelRegistryId(modelRegistryProject.getId());
        /**
         * If request is to expand training dataset, query the provenance API to find the first Training Dataset linked
         * with the model.
         * Currently there is no training datatsets builder that can be used to set the href for the training dataset
         */
        if(resourceRequest.contains(ResourceRequest.Name.TRAININGDATASETS)) {
          if(modelSummary.getTrainingDataset() != null) {
            modelDTO.setTrainingDataset(modelSummary.getTrainingDataset());
          }
          Set<String> filterByFields = new HashSet<>();
          filterByFields.add(ProvLinks.FieldsPF.OUT_ARTIFACT.name() + ":" + modelSummary.getId());
          filterByFields.add(ProvLinks.FieldsPF.IN_TYPE.name() + ":" + ProvParser.DocSubType.TRAINING_DATASET.name());
          filterByFields.add(ProvLinks.FieldsPF.OUT_TYPE.name() + ":" + ProvParser.DocSubType.MODEL.name());

          ProvLinksParamBuilder provLinksParamBuilder = new ProvLinksParamBuilder()
            .linkType(true)
            .onlyApps(true)
            .filterByFields(filterByFields);
          ProvLinksDTO dto = provOpsController.provLinks(userProject, provLinksParamBuilder, true);
          if(dto != null) {
            List<ProvLinksDTO> provLinksDTOList = dto.getItems();
            if (provLinksDTOList != null && !provLinksDTOList.isEmpty()) {
              Map<String, ProvOpsDTO> inLinks = dto.getItems().get(0).getIn();
              if (inLinks != null && !inLinks.isEmpty()) {
                Set<Map.Entry<String, ProvOpsDTO>> inLinksSets = inLinks.entrySet();

                Optional<Map.Entry<String, ProvOpsDTO>> modelTrainingDataset =
                  inLinksSets.stream().findFirst().filter(x ->
                    x.getValue().getDocSubType().equals(ProvParser.DocSubType.TRAINING_DATASET));

                if (modelTrainingDataset.isPresent()) {
                  ProvOpsDTO modelProvenanceDTO = modelTrainingDataset.get().getValue();
                  String mlId = modelProvenanceDTO.getMlId();

                  int splitIndex = mlId.lastIndexOf("_");
                  String[] nameVersionSplit = {mlId.substring(0, splitIndex), mlId.substring(splitIndex + 1)};

                  TrainingDatasetDTO tdDTO = new TrainingDatasetDTO();

                  tdDTO.setName(nameVersionSplit[0]);
                  tdDTO.setVersion(Integer.valueOf(nameVersionSplit[1]));

                  Project tdProject = projectFacade.findByName(modelProvenanceDTO.getProjectName());
                  if (tdProject != null) {
                    List<Featurestore> featurestores = featurestoreFacade.findByProject(tdProject);
                    if (!featurestores.isEmpty()) {
                      tdDTO.setFeaturestoreId(featurestores.get(0).getId());
                      tdDTO.setFeaturestoreName(Utils.getFeaturestoreName(tdProject));
                    }
                    modelDTO.setTrainingDataset(tdDTO);
                  }
                }
              }
            }
          }
        }
      }
    }
    return modelDTO;
  }

  private Pair<ProvStateParamBuilder, ModelRegistryDTO> buildFilter(Project project,
    Project modelRegistryProject, Set<? extends AbstractFacade.FilterBy> filters)
          throws GenericException, ProvenanceException, DatasetException {
    ProvStateParamBuilder provFilesParamBuilder = new ProvStateParamBuilder();
    if(filters != null) {
      Users filterUser = null;
      Project filterUserProject = project;
      for (AbstractFacade.FilterBy filterBy : filters) {
        if(filterBy.getParam().compareToIgnoreCase(Filters.NAME_EQ.name()) == 0) {
          provFilesParamBuilder.filterByXAttr(MODEL_SUMMARY_XATTR_NAME + ".name", filterBy.getValue());
        } else if(filterBy.getParam().compareToIgnoreCase(Filters.NAME_LIKE.name()) == 0) {
          provFilesParamBuilder.filterLikeXAttr(MODEL_SUMMARY_XATTR_NAME + ".name", filterBy.getValue());
        }  else if(filterBy.getParam().compareToIgnoreCase(Filters.VERSION.name()) == 0) {
          provFilesParamBuilder.filterByXAttr(MODEL_SUMMARY_XATTR_NAME + ".version", filterBy.getValue());
        } else if(filterBy.getParam().compareToIgnoreCase(Filters.ID_EQ.name()) == 0) {
          provFilesParamBuilder.filterByXAttr(MODEL_SUMMARY_XATTR_NAME + ".id", filterBy.getValue());
        } else if (filterBy.getParam().compareToIgnoreCase(Filters.USER.name()) == 0) {
          try {
            filterUser = userFacade.find(Integer.parseInt(filterBy.getValue()));
          } catch(NumberFormatException e) {
            throw new GenericException(RESTCodes.GenericErrorCode.ILLEGAL_ARGUMENT, Level.INFO,
              "expected int user id, found: " + filterBy.getValue());
          }
        } else if (filterBy.getParam().compareToIgnoreCase(Filters.USER_PROJECT.name()) == 0) {
          try {
            filterUserProject = projectFacade.find(Integer.parseInt(filterBy.getValue()));
          } catch(NumberFormatException e) {
            throw new GenericException(RESTCodes.GenericErrorCode.ILLEGAL_ARGUMENT, Level.INFO,
              "expected int user project id, found: " + filterBy.getValue());
          }
        } else {
          throw new GenericException(RESTCodes.GenericErrorCode.ILLEGAL_ARGUMENT, Level.INFO,
            "Filter by - found: " + filterBy.getParam() + " expected:" + EnumSet.allOf(Filters.class));
        }
      }
      if(filterUser != null) {
        ProjectTeam member = projectTeamFacade.findByPrimaryKey(filterUserProject, filterUser);
        if(member == null) {
          throw new GenericException(RESTCodes.GenericErrorCode.ILLEGAL_ARGUMENT, Level.INFO,
            "Selected user: " + filterUser.getUid() + " is not part of project:" + filterUserProject.getId());
        }
        String hdfsUserStr = hdfsUsersController.getHdfsUserName(filterUserProject, filterUser);
        HdfsUsers hdfsUsers = hdfsUsersFacade.findByName(hdfsUserStr);
        provFilesParamBuilder.filterByField(ProvStateParser.FieldsP.USER_ID, hdfsUsers.getId().toString());
      }
    }
    ModelRegistryDTO modelRegistryDTO = modelsController.getModelRegistry(modelRegistryProject);
    provFilesParamBuilder
            .filterByField(ProvStateParser.FieldsP.PROJECT_I_ID, modelRegistryDTO.getParentProject().getInode().getId())
            .filterByField(ProvStateParser.FieldsP.DATASET_I_ID, modelRegistryDTO.getDatasetInodeId());
    return Pair.with(provFilesParamBuilder, modelRegistryDTO);
  }

  private void buildSortOrder(ProvStateParamBuilder provFilesParamBuilder, Set<? extends AbstractFacade.SortBy> sort) {
    if(sort != null) {
      for(AbstractFacade.SortBy sortBy: sort) {
        if(sortBy.getValue().compareToIgnoreCase(SortBy.NAME.name()) == 0) {
          provFilesParamBuilder.sortByXAttr(MODEL_SUMMARY_XATTR_NAME + ".name",
            SortOrder.valueOf(sortBy.getParam().getValue()));
        } else {
          String sortKeyName = sortBy.getValue();
          String sortKeyOrder = sortBy.getParam().getValue();
          provFilesParamBuilder.sortByXAttr(MODEL_SUMMARY_XATTR_NAME + ".metrics." + sortKeyName,
            SortOrder.valueOf(sortKeyOrder));
        }
      }
    }
  }

  private void validatePagination(ResourceRequest resourceRequest) {
    if(resourceRequest.getLimit() == null || resourceRequest.getLimit() <= 0) {
      resourceRequest.setLimit(settings.getElasticDefaultScrollPageSize());
    }

    if(resourceRequest.getOffset() == null || resourceRequest.getOffset() <= 0) {
      resourceRequest.setOffset(0);
    }
  }

  protected enum SortBy {
    NAME
  }

  protected enum Filters {
    NAME_EQ,
    NAME_LIKE,
    VERSION,
    ID_EQ,
    USER,
    USER_PROJECT
  }
  
  private Pair<ProvStateParamBuilder, ModelRegistryDTO> buildModelProvenanceParams(Project project,
                                                                                   Project modelRegistryProject,
                                                                                   ResourceRequest resourceRequest)
          throws ProvenanceException, GenericException, DatasetException {
    Pair<ProvStateParamBuilder, ModelRegistryDTO> builder
      = buildFilter(project, modelRegistryProject, resourceRequest.getFilter());
    builder.getValue0()
      .filterByField(ProvStateParser.FieldsP.ML_TYPE, Provenance.MLType.MODEL.name())
      .hasXAttr(MODEL_SUMMARY_XATTR_NAME)
      .paginate(resourceRequest.getOffset(), resourceRequest.getLimit());
    buildSortOrder(builder.getValue0(), resourceRequest.getSort());
    return builder;
  }
}