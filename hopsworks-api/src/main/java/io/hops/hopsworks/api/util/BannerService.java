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

package io.hops.hopsworks.api.util;

import io.hops.hopsworks.api.filter.Audience;
import io.hops.hopsworks.api.filter.JWTNotRequired;
import io.hops.hopsworks.api.filter.NoCacheResponse;
import io.hops.hopsworks.api.jwt.JWTHelper;
import io.hops.hopsworks.audit.logger.annotation.Logged;
import io.hops.hopsworks.common.dao.user.security.audit.AccountAuditFacade;
import io.hops.hopsworks.common.maintenance.Maintenance;
import io.hops.hopsworks.common.maintenance.MaintenanceController;
import io.hops.hopsworks.common.util.Settings;
import io.hops.hopsworks.jwt.annotation.JWTRequired;
import io.hops.hopsworks.persistence.entity.user.Users;
import io.hops.hopsworks.persistence.entity.user.security.audit.AccountAudit;
import io.hops.hopsworks.persistence.entity.user.security.ua.UserAccountStatus;
import io.hops.hopsworks.persistence.entity.util.VariablesVisibility;
import io.swagger.annotations.Api;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

@Logged
@Path("/banner")
@Stateless
@Api(value = "Banner Service",
    description = "Banner Service")
@TransactionAttribute(TransactionAttributeType.NEVER)
public class BannerService {

  public BannerService() {
  }

  @EJB
  private MaintenanceController maintenanceController;
  @EJB
  private Settings settings;
  @EJB
  private NoCacheResponse noCacheResponse;
  @EJB
  private JWTHelper jWTHelper;
  @EJB
  private AccountAuditFacade accountAuditFacade;

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @JWTNotRequired
  public Response findBanner(@Context SecurityContext sc) {
    Maintenance maintenance = maintenanceController.getMaintenance();
    maintenance.setOtp(settings.getTwoFactorAuth());
    return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).entity(maintenance).build();
  }

  @GET
  @Path("user")
  @Produces(MediaType.APPLICATION_JSON)
  @JWTRequired(acceptedTokens={Audience.API}, allowedUserRoles={"HOPS_ADMIN", "HOPS_USER"})
  public Response findUserBanner(@Context SecurityContext sc) {
    Users user = jWTHelper.getUserPrincipal(sc);
    RESTApiJsonResponse json = new RESTApiJsonResponse();
    json.setSuccessMessage("");
    if (user != null && (user.getSalt() == null || user.getSalt().isEmpty())) {
      json.setSuccessMessage("For security purposes, we highly recommend you change your password.");
    } else if (user != null && UserAccountStatus.TEMP_PASSWORD.equals(user.getStatus())) {
      AccountAudit accountAudit = accountAuditFacade.findByTargetLatestPwdReset(user);
      String fullName = "";
      if (accountAudit != null) {
        fullName = " (" +accountAudit.getInitiator().getFname() + " " + accountAudit.getInitiator().getLname() + ")";
      }
      json.setSuccessMessage("The password you used is autogenerated by an administrator" + fullName + ", it is " +
        "highly recommend that you change it asap.");
    }
    return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).entity(json).build();
  }

  @GET
  @Path("firsttime")
  @Produces(MediaType.TEXT_PLAIN)
  @JWTNotRequired
  public Response isFirstTimeLogin(@Context HttpServletRequest req){
    if (maintenanceController.isFirstTimeLogin()) {
      return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).build();
    }
    return noCacheResponse.getNoCacheResponseBuilder(Response.Status.NOT_FOUND).build();
  }
  
  @GET
  @Path("firstlogin")
  @Produces(MediaType.TEXT_PLAIN)
  @JWTNotRequired
  public Response firstLogin(@Context HttpServletRequest req) {
    settings.updateVariable("first_time_login", "0", VariablesVisibility.NOTAUTHENTICATED);
    return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).build();
  }  

  @GET
  @Path("admin_pwd_changed")  
  @Produces(MediaType.TEXT_PLAIN)
  @JWTNotRequired
  public Response adminPwdChanged(@Context HttpServletRequest req) {
    if (settings.isDefaultAdminPasswordChanged()) {
      return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).build();
    }
    return noCacheResponse.getNoCacheResponseBuilder(Response.Status.NOT_FOUND).build();
  }
}
