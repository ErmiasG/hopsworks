/*
 * Changes to this file committed after and not including commit-id: ccc0d2c5f9a5ac661e60e6eaf138de7889928b8b
 * are released under the following license:
 *
 * This file is part of Hopsworks
 * Copyright (C) 2018, Logical Clocks AB. All rights reserved
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
 *
 * Changes to this file committed before and including commit-id: ccc0d2c5f9a5ac661e60e6eaf138de7889928b8b
 * are released under the following license:
 *
 * Copyright (C) 2013 - 2018, Logical Clocks AB and RISE SICS AB. All rights reserved
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the "Software"), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software, and to permit
 * persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS  OR IMPLIED, INCLUDING
 * BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL  THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR  OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

angular.module('hopsWorksApp')
    .controller('ShareDatasetCtrl', ['$scope','$uibModalInstance', 'DataSetService', '$routeParams', 'growl', 'ProjectService', 'datasetPath', 'dsType', 'permissions', 'ModalService',
        function ($scope, $uibModalInstance, DataSetService, $routeParams, growl, ProjectService, datasetPath, dsType, permissions, ModalService) {

            var self = this;
            self.datasets = [];
            self.projects = [];
            self.targetProject = null;
            self.pId = $routeParams.projectID;
            self.loading = true;
            var dataSetService = DataSetService(self.pId);
            var defaultPermissions = 'OWNER_ONLY';

            self.ownerOnlyMsg = "Sets default permissions setting of a Data Set. Only Data Owners will be able to upload/remove files either via the Data Sets browser and only the owner of the Data Set via Jobs and Jupyter notebooks.";
            self.groupWritableAndStickyBitSet = "Data Owners are allowed to upload/remove files and Data Scientists are allowed to upload files, but only remove files/dirs they own. These operations can be done via the Data Set Browser, Jobs and Jupyter notebooks. <br> Are you sure you want to proceed?";
            self.groupWritable = "This is the least strict setting. It allows both Data Owners and Data Scientists to upload/remove files either via the Data Set Browser, Jobs and Jupyter Notebooks. <br> Are you sure you want to proceed?";

            self.hiveOwnerOnlyMsg = "Sets default permissions setting of a Data Set. Only the owner will be able to upload/remove files from the Data Set via Jobs and Jupyter notebooks.";
            self.hiveGroupWritableAndStickyBitSet = "Data Owners are allowed to upload/remove files and Data Scientists are allowed to upload files, but only remove files/dirs they own. These operations can be done via Jobs and Jupyter notebooks. <br> Are you sure you want to proceed?";
            self.hiveGroupWritable = "This is the least strict setting. It allows both Data Owners and Data Scientists to upload/remove files either via Jobs and Jupyter Notebooks. <br> Are you sure you want to proceed?";

          ProjectService.getAll().$promise.then(
                function (success) {
                    self.projects = success;
                    self.loading = false;
                }, function (error) {
                    if (typeof error.data.usrMsg !== 'undefined') {
                        growl.error(error.data.usrMsg, {title: error.data.errorMsg, ttl: 8000});
                    } else {
                        growl.error("", {title: error.data.errorMsg, ttl: 8000});
                    }
                    self.loading = false;
                }
            );


            self.close = function () {
                $uibModalInstance.dismiss('cancel');
            };

            self.shareDataset = function () {
                if ($scope.dataSetForm.$valid) {
                    dataSetService.share(datasetPath, self.targetProject, dsType)
                        .then(function (success) {
                            $uibModalInstance.close(success);
                        },
                        function (error) {
                            if (typeof error.data.usrMsg !== 'undefined') {
                                growl.error(error.data.usrMsg, {title: error.data.errorMsg, ttl: 8000});
                            } else {
                                growl.error("", {title: error.data.errorMsg, ttl: 8000});
                            }
                        });
                } else {
                    self.dataSet.permissions = defaultPermissions;
                }

            };
            
            self.setPermissions = function () {
                if ($scope.dataSetForm.$valid) {
                    dataSetService.permissions(datasetPath, permissions)
                        .then(function (success) {
                            $uibModalInstance.close(success);
                        },
                        function (error) {
                            growl.error(error.data.errorMsg, {title: 'Error', ttl: 15000});
                        });
                } else {
                    self.dataSet.permissions = defaultPermissions;
                }

            };
            
            self.getPermissionsText = function() {
              if(dsType === "HIVEDB" || dsType === "FEATURESTORE") {
                if(permissions === 'GROUP_WRITABLE_SB'){
                  return self.hiveGroupWritableAndStickyBitSet;
                } else if(permissions === 'GROUP_WRITABLE'){
                  return self.hiveGroupWritable;
                } else if(permissions === 'OWNER_ONLY'){
                  return self.hiveOwnerOnlyMsg;
                }
              } else {
                if (typeof permissions !== 'undefined') {
                  if (permissions === 'GROUP_WRITABLE_SB') {
                    return self.groupWritableAndStickyBitSet;
                  } else if (permissions === 'GROUP_WRITABLE') {
                    return self.groupWritable;
                  } else if (permissions === 'OWNER_ONLY') {
                    return self.ownerOnlyMsg;
                  }
                }
              }
            };
            
              /**
             * Opens a modal dialog to make dataset editable
             * @param {type} permissions
             * @returns {undefined}
             */
            self.setPermissionsModal = function (permissions) {
              ModalService.permissions('md', dsName, permissions).then(
                      function (success) {
                        growl.success(success.data.successMessage, {title: 'Success', ttl: 5000});
                      }, function (error) {
              });
            };
            
            $scope.omitCurrentProject = function (project) {
              var id = parseInt(self.pId);
              return project.id !== id;
            };
        }]);