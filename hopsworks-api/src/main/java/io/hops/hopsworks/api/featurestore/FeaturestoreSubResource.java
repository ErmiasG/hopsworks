/*
 * This file is part of Hopsworks
 * Copyright (C) 2024, Hopsworks AB. All rights reserved
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
package io.hops.hopsworks.api.featurestore;

import io.hops.hopsworks.api.project.ProjectSubResource;
import io.hops.hopsworks.common.featurestore.FeaturestoreController;
import io.hops.hopsworks.exceptions.FeaturestoreException;
import io.hops.hopsworks.exceptions.ProjectException;
import io.hops.hopsworks.persistence.entity.featurestore.Featurestore;
import io.hops.hopsworks.persistence.entity.project.Project;

public abstract class FeaturestoreSubResource extends ProjectSubResource {

  private Integer featurestoreId;

  public Integer getFeaturestoreId() {
    return featurestoreId;
  }

  public void setFeaturestoreId(Integer featurestoreId) {
    this.featurestoreId = featurestoreId;
  }

  public Featurestore getFeaturestore(Project project) throws ProjectException, FeaturestoreException {
    return getFeaturestoreController().getFeaturestoreForProjectWithId(project, featurestoreId);
  }

  public Featurestore getFeaturestore() throws ProjectException, FeaturestoreException {
    Project project = getProject();
    return getFeaturestoreController().getFeaturestoreForProjectWithId(project, featurestoreId);
  }

  protected abstract FeaturestoreController getFeaturestoreController();
}
