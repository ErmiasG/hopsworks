/*
 * This file is part of Hopsworks
 * Copyright (C) 2020, Logical Clocks AB. All rights reserved
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

package io.hops.hopsworks.api.featurestore.statistics;

import io.hops.hopsworks.api.auth.key.ApiKeyRequired;
import io.hops.hopsworks.api.featurestore.FeaturestoreCommonSubResource;
import io.hops.hopsworks.api.filter.AllowedProjectRoles;
import io.hops.hopsworks.api.filter.Audience;
import io.hops.hopsworks.api.jobs.JobDTO;
import io.hops.hopsworks.api.jobs.JobsBuilder;
import io.hops.hopsworks.api.jwt.JWTHelper;
import io.hops.hopsworks.api.util.Pagination;
import io.hops.hopsworks.common.api.ResourceRequest;
import io.hops.hopsworks.common.featurestore.FeaturestoreController;
import io.hops.hopsworks.common.featurestore.app.FsJobManagerController;
import io.hops.hopsworks.common.featurestore.featuregroup.FeaturegroupController;
import io.hops.hopsworks.common.featurestore.featureview.FeatureViewController;
import io.hops.hopsworks.common.featurestore.statistics.SplitStatisticsDTO;
import io.hops.hopsworks.common.featurestore.statistics.StatisticsController;
import io.hops.hopsworks.common.featurestore.statistics.StatisticsDTO;
import io.hops.hopsworks.common.featurestore.statistics.StatisticsFilterBy;
import io.hops.hopsworks.common.featurestore.statistics.StatisticsFilters;
import io.hops.hopsworks.common.featurestore.statistics.StatisticsInputValidation;
import io.hops.hopsworks.common.featurestore.trainingdatasets.TrainingDatasetController;
import io.hops.hopsworks.common.project.ProjectController;
import io.hops.hopsworks.exceptions.DatasetException;
import io.hops.hopsworks.exceptions.FeaturestoreException;
import io.hops.hopsworks.exceptions.GenericException;
import io.hops.hopsworks.exceptions.HopsSecurityException;
import io.hops.hopsworks.exceptions.JobException;
import io.hops.hopsworks.exceptions.ProjectException;
import io.hops.hopsworks.exceptions.ServiceException;
import io.hops.hopsworks.jwt.annotation.JWTRequired;
import io.hops.hopsworks.persistence.entity.featurestore.Featurestore;
import io.hops.hopsworks.persistence.entity.featurestore.featuregroup.Featuregroup;
import io.hops.hopsworks.persistence.entity.featurestore.featureview.FeatureView;
import io.hops.hopsworks.persistence.entity.featurestore.statistics.FeatureDescriptiveStatistics;
import io.hops.hopsworks.persistence.entity.featurestore.statistics.FeatureGroupStatistics;
import io.hops.hopsworks.persistence.entity.featurestore.statistics.FeatureViewStatistics;
import io.hops.hopsworks.persistence.entity.featurestore.statistics.TrainingDatasetStatistics;
import io.hops.hopsworks.persistence.entity.featurestore.trainingdataset.TrainingDataset;
import io.hops.hopsworks.persistence.entity.jobs.description.Jobs;
import io.hops.hopsworks.persistence.entity.project.Project;
import io.hops.hopsworks.persistence.entity.user.Users;
import io.hops.hopsworks.persistence.entity.user.security.apiKey.ApiScope;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

import javax.ejb.EJB;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.enterprise.context.RequestScoped;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Api(value = "Feature store statistics Resource")
@RequestScoped
@TransactionAttribute(TransactionAttributeType.NEVER)
public class StatisticsResource extends FeaturestoreCommonSubResource {
  
  @EJB
  private StatisticsBuilder statisticsBuilder;
  @EJB
  private StatisticsController statisticsController;
  @EJB
  private StatisticsInputValidation statisticsInputValidation;
  @EJB
  private FeatureDescriptiveStatisticsBuilder featureDescriptiveStatisticsBuilder;
  @EJB
  private JWTHelper jWTHelper;
  @EJB
  private FeaturegroupController featuregroupController;
  @EJB
  private FeatureViewController featureViewController;
  @EJB
  private TrainingDatasetController trainingDatasetController;
  @EJB
  private FsJobManagerController fsJobManagerController;
  @EJB
  private JobsBuilder jobsBuilder;

  @EJB
  private FeaturestoreController featurestoreController;
  @EJB
  private ProjectController projectController;

  @Override
  protected FeaturestoreController getFeaturestoreController() {
    return featurestoreController;
  }

  @Override
  protected ProjectController getProjectController() {
    return projectController;
  }

  @Override
  protected FeatureViewController getFeatureViewController() {
    return featureViewController;
  }

  @Override
  protected TrainingDatasetController getTrainingDatasetController() {
    return trainingDatasetController;
  }

  @Override
  protected FeaturegroupController getFeaturegroupController() {
    return featuregroupController;
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Get all available statistics", response = StatisticsDTO.class)
  @AllowedProjectRoles({AllowedProjectRoles.DATA_OWNER, AllowedProjectRoles.DATA_SCIENTIST})
  @JWTRequired(acceptedTokens = {Audience.API, Audience.JOB},
    allowedUserRoles = {"HOPS_ADMIN", "HOPS_USER", "HOPS_SERVICE_USER"})
  @ApiKeyRequired(acceptedScopes = {ApiScope.DATASET_VIEW, ApiScope.FEATURESTORE},
    allowedUserRoles = {"HOPS_ADMIN", "HOPS_USER", "HOPS_SERVICE_USER"})
  public Response get(@BeanParam Pagination pagination,
    @BeanParam StatisticsBeanParam statisticsBeanParam,
    @Context UriInfo uriInfo,
    @Context HttpServletRequest req,
    @Context SecurityContext sc,
    @ApiParam(value = "feature_names") @QueryParam("feature_names") Set<String> featureNames,
    // backwards compatibility
    @ApiParam(value = "for_transformation", example = "false")
    @QueryParam("for_transformation") Boolean forTransformation)
      throws FeaturestoreException, ProjectException {
    Users user = jWTHelper.getUserPrincipal(sc);
    
    // backwards compatibility
    if (forTransformation != null) {
      statisticsBeanParam.getFilterSet().add(new StatisticsFilterBy(
        StatisticsFilters.Filters.BEFORE_TRANSFORMATION_EQ, String.valueOf(forTransformation)));
    }
    
    ResourceRequest resourceRequest = new ResourceRequest(ResourceRequest.Name.STATISTICS);
    resourceRequest.setOffset(pagination.getOffset());
    resourceRequest.setLimit(pagination.getLimit());
    resourceRequest.setSort(statisticsBeanParam.getSortBySet());
    resourceRequest.setFilter(statisticsBeanParam.getFilterSet());
    resourceRequest.setField(statisticsBeanParam.getFieldSet());
    
    StatisticsFilters filters = new StatisticsFilters((Set)statisticsBeanParam.getFilterSet());
    Project project = getProject();
    Featurestore featurestore = getFeaturestore(project);
    Featuregroup featuregroup = getFeaturegroup(featurestore);
    FeatureView featureView = getFeatureView(featurestore);
    TrainingDataset trainingDataset = getTrainingDataset(featurestore);
    StatisticsDTO dto;
    if (featuregroup != null) { // feature group statistics
      statisticsInputValidation.validateStatisticsFiltersForFeatureGroup((Set) statisticsBeanParam.getFilterSet());
      statisticsInputValidation.validateGetForFeatureGroup(featuregroup, filters);
      dto = statisticsBuilder.build(uriInfo, resourceRequest, project, user, featurestore, featuregroup, featureNames);
    } else if (trainingDataset != null) {
      statisticsInputValidation.validateStatisticsFiltersForTrainingDataset((Set)statisticsBeanParam.getFilterSet());
      dto = statisticsBuilder.build(uriInfo, resourceRequest, project, user, featurestore, trainingDataset,
        featureNames);
    } else {
      statisticsInputValidation.validateStatisticsFiltersForFeatureView((Set) statisticsBeanParam.getFilterSet());
      statisticsInputValidation.validateGetForFeatureView(featureView, filters);
      dto = statisticsBuilder.build(uriInfo, resourceRequest, project, user, featurestore, featureView, featureNames);
    }
    return Response.ok().entity(dto).build();
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Register new statistics")
  @AllowedProjectRoles({AllowedProjectRoles.DATA_OWNER, AllowedProjectRoles.DATA_SCIENTIST})
  @JWTRequired(acceptedTokens = {Audience.API, Audience.JOB},
    allowedUserRoles = {"HOPS_ADMIN", "HOPS_USER", "HOPS_SERVICE_USER"})
  @ApiKeyRequired(acceptedScopes = {ApiScope.DATASET_VIEW, ApiScope.FEATURESTORE},
    allowedUserRoles = {"HOPS_ADMIN", "HOPS_USER", "HOPS_SERVICE_USER"})
  public Response register(@Context UriInfo uriInfo,
    @Context HttpServletRequest req,
    @Context SecurityContext sc,
    StatisticsDTO statisticsDTO)
      throws FeaturestoreException, DatasetException, HopsSecurityException, IOException, ProjectException {
    
    Users user = jWTHelper.getUserPrincipal(sc);
    Project project = getProject();
    Featurestore featurestore = getFeaturestore(project);
    Featuregroup featuregroup = getFeaturegroup(featurestore);
    FeatureView featureView = getFeatureView(featurestore);
    TrainingDataset trainingDataset = getTrainingDataset(featurestore, featureView);
    StatisticsDTO dto = null;
    if (featuregroup != null) {
      dto = registerFeatureGroupStatistics(project, featuregroup, user, uriInfo, statisticsDTO);
    } else if (trainingDataset != null) {
      dto = registerTrainingDatasetStatistics(project, trainingDataset, user, uriInfo, statisticsDTO);
    } else if (featureView != null) {
      dto = registerFeatureViewStatistics(project, featureView, user, uriInfo, statisticsDTO);
    }
    return Response.ok().entity(dto).build();
  }

  @Path("compute")
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Setup job and trigger for computing statistics", response = JobDTO.class)
  @AllowedProjectRoles({AllowedProjectRoles.DATA_OWNER, AllowedProjectRoles.DATA_SCIENTIST})
  @JWTRequired(acceptedTokens = {Audience.API, Audience.JOB},
    allowedUserRoles = {"HOPS_ADMIN", "HOPS_USER", "HOPS_SERVICE_USER"})
  @ApiKeyRequired(acceptedScopes = {ApiScope.DATASET_VIEW, ApiScope.FEATURESTORE},
    allowedUserRoles = {"HOPS_ADMIN", "HOPS_USER", "HOPS_SERVICE_USER"})
  public Response compute(@Context UriInfo uriInfo,
    @Context HttpServletRequest req,
    @Context SecurityContext sc)
    throws FeaturestoreException, ServiceException, JobException, ProjectException, GenericException {
    Users user = jWTHelper.getUserPrincipal(sc);
    Project project = getProject();
    Featurestore featurestore = getFeaturestore(project);
    Featuregroup featuregroup = getFeaturegroup(featurestore);
    TrainingDataset trainingDataset = getTrainingDataset(featurestore);
    Jobs job = fsJobManagerController.setupStatisticsJob(project, user, featurestore, featuregroup, trainingDataset);
    JobDTO jobDTO = jobsBuilder.build(uriInfo, new ResourceRequest(ResourceRequest.Name.JOBS), job);
    return Response.created(jobDTO.getHref()).entity(jobDTO).build();
  }
  
  private StatisticsDTO registerFeatureGroupStatistics(Project project, Featuregroup featuregroup, Users user,
      UriInfo uriInfo, StatisticsDTO statisticsDTO) throws FeaturestoreException, IOException, DatasetException,
      HopsSecurityException {
    statisticsInputValidation.validateRegisterForFeatureGroup(featuregroup, statisticsDTO);
    Collection<FeatureDescriptiveStatistics> stats = featureDescriptiveStatisticsBuilder.buildManyFromContentOrDTO(
        statisticsDTO.getFeatureDescriptiveStatistics(), statisticsDTO.getContent());
    FeatureGroupStatistics featureGroupStatistics = statisticsController.registerFeatureGroupStatistics(project, user,
      statisticsDTO.getComputationTime(), statisticsDTO.getWindowStartCommitTime(),
      statisticsDTO.getWindowEndCommitTime(), statisticsDTO.getRowPercentage(), stats, featuregroup);
    ResourceRequest resourceRequest = new ResourceRequest(ResourceRequest.Name.STATISTICS);
    resourceRequest.setField(Collections.singleton("content"));
    return statisticsBuilder.build(uriInfo, resourceRequest, project, user, featuregroup, featureGroupStatistics);
  }
  
  private StatisticsDTO registerFeatureViewStatistics(Project project, FeatureView featureView, Users user,
      UriInfo uriInfo, StatisticsDTO statisticsDTO) throws FeaturestoreException, IOException, DatasetException,
      HopsSecurityException {
    statisticsInputValidation.validateRegisterForFeatureView(featureView, statisticsDTO);
    Collection<FeatureDescriptiveStatistics> stats = featureDescriptiveStatisticsBuilder.buildManyFromContentOrDTO(
      statisticsDTO.getFeatureDescriptiveStatistics(), statisticsDTO.getContent());
    FeatureViewStatistics featureViewStatistics = statisticsController.registerFeatureViewStatistics(project, user,
      statisticsDTO.getComputationTime(), statisticsDTO.getWindowStartCommitTime(),
      statisticsDTO.getWindowEndCommitTime(), statisticsDTO.getRowPercentage(), stats, featureView);
    ResourceRequest resourceRequest = new ResourceRequest(ResourceRequest.Name.STATISTICS);
    resourceRequest.setField(Collections.singleton("content"));
    return statisticsBuilder.build(uriInfo, resourceRequest, project, user, featureView, featureViewStatistics);
  }
  
  private StatisticsDTO registerTrainingDatasetStatistics(Project project, TrainingDataset trainingDataset, Users user,
      UriInfo uriInfo, StatisticsDTO statisticsDTO) throws FeaturestoreException, IOException, DatasetException,
      HopsSecurityException {
    statisticsInputValidation.validateRegisterForTrainingDataset(trainingDataset, statisticsDTO);
    // register training dataset statistics as a file in hopsfs
    TrainingDatasetStatistics statistics;
    if (statisticsDTO.getSplitStatistics() != null && !statisticsDTO.getSplitStatistics().isEmpty() &&
      !statisticsDTO.getBeforeTransformation()) {
      Map<String, Collection<FeatureDescriptiveStatistics>> splitStatistics =
        statisticsDTO.getSplitStatistics().stream().collect(Collectors.toMap(SplitStatisticsDTO::getName,
          sps -> featureDescriptiveStatisticsBuilder.buildManyFromContentOrDTO(sps.getFeatureDescriptiveStatistics(),
            sps.getContent())));
      statistics = statisticsController.registerTrainingDatasetSplitStatistics(project, user,
        statisticsDTO.getComputationTime(), statisticsDTO.getRowPercentage(), splitStatistics, trainingDataset);
    } else {
      Collection<FeatureDescriptiveStatistics> stats = featureDescriptiveStatisticsBuilder.buildManyFromContentOrDTO(
        statisticsDTO.getFeatureDescriptiveStatistics(), statisticsDTO.getContent());
      statistics = statisticsController.registerTrainingDatasetStatistics(project, user,
        statisticsDTO.getComputationTime(), statisticsDTO.getBeforeTransformation(), statisticsDTO.getRowPercentage(),
        stats, trainingDataset);
    }
    ResourceRequest resourceRequest = new ResourceRequest(ResourceRequest.Name.STATISTICS);
    resourceRequest.setField(Collections.singleton("content"));
    return statisticsBuilder.build(uriInfo, resourceRequest, project, user, trainingDataset, statistics);
  }
}