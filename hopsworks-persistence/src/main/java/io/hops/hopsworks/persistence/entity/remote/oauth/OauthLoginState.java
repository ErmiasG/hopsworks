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
package io.hops.hopsworks.persistence.entity.remote.oauth;

import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.Serializable;
import java.util.Date;

@Entity
@Table(name = "oauth_login_state",
    catalog = "hopsworks",
    schema = "")
@XmlRootElement
@NamedQueries({
  @NamedQuery(name = "OauthLoginState.findAll",
      query = "SELECT o FROM OauthLoginState o")
  ,
    @NamedQuery(name = "OauthLoginState.findById",
      query = "SELECT o FROM OauthLoginState o WHERE o.id = :id")
  ,
    @NamedQuery(name = "OauthLoginState.findByState",
      query = "SELECT o FROM OauthLoginState o WHERE o.state = :state")
  ,
    @NamedQuery(name = "OauthLoginState.findByStateAndSession",
      query = "SELECT o FROM OauthLoginState o WHERE o.state = :state AND o.sessionId = :sessionId")
  ,
  @NamedQuery(name = "OauthLoginState.findByLoginTimeBefore",
    query = "SELECT o FROM OauthLoginState o WHERE o.loginTime < :loginTime")
  ,
    @NamedQuery(name = "OauthLoginState.findByLoginTime",
      query
      = "SELECT o FROM OauthLoginState o WHERE o.loginTime = :loginTime")})
public class OauthLoginState implements Serializable {

  private static final long serialVersionUID = 1L;
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Basic(optional = false)
  @Column(name = "id")
  private Integer id;
  @Basic(optional = false)
  @NotNull
  @Size(min = 1,
      max = 128)
  @Column(name = "state")
  private String state;
  @Basic(optional = false)
  @NotNull
  @Size(min = 1,
    max = 128)
  @Column(name = "session_id")
  private String sessionId;
  @Basic(optional = false)
  @NotNull
  @Size(min = 1,
    max = 128)
  @Column(name = "nonce")
  private String nonce;
  @Column(name = "code_challenge")
  private String codeChallenge;
  @Basic(optional = false)
  @NotNull
  @Column(name = "login_time")
  @Temporal(TemporalType.TIMESTAMP)
  private Date loginTime;
  @Size(max = 8000)
  @Column(name = "token")
  private String token;
  @Basic(optional = false)
  @NotNull
  @Size(min = 1,
    max = 1024)
  @Column(name = "redirect_uri")
  private String redirectURI;
  @Size(max = 2048)
  @Column(name = "scopes")
  private String scopes;
  @JoinColumn(name = "client_id",
      referencedColumnName = "client_id")
  @ManyToOne(optional = false)
  private OauthClient clientId;

  public OauthLoginState() {
  }

  public OauthLoginState(Integer id) {
    this.id = id;
  }

  public OauthLoginState(String state, OauthClient clientId, String sessionId, String redirectURI, String scopes) {
    this.state = state;
    this.clientId = clientId;
    this.sessionId = sessionId;
    this.loginTime = new Date();
    this.redirectURI = redirectURI;
    this.scopes = scopes;
  }

  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public String getState() {
    return state;
  }

  public void setState(String state) {
    this.state = state;
  }
  
  public String getSessionId() {
    return sessionId;
  }
  
  public void setSessionId(String sessionId) {
    this.sessionId = sessionId;
  }
  
  public String getNonce() {
    return nonce;
  }
  
  public void setNonce(String nonce) {
    this.nonce = nonce;
  }
  
  public String getCodeChallenge() {
    return codeChallenge;
  }
  
  public void setCodeChallenge(String codeChallenge) {
    this.codeChallenge = codeChallenge;
  }
  
  public Date getLoginTime() {
    return loginTime;
  }

  public void setLoginTime(Date loginTime) {
    this.loginTime = loginTime;
  }
  
  public String getScopes() {
    return scopes;
  }
  
  public void setScopes(String scopes) {
    this.scopes = scopes;
  }
  
  public OauthClient getClientId() {
    return clientId;
  }

  public void setClientId(OauthClient clientId) {
    this.clientId = clientId;
  }
  
  public String getRedirectURI() {
    return redirectURI;
  }
  
  public void setRedirectURI(String redirectURI) {
    this.redirectURI = redirectURI;
  }
  
  @Override
  public int hashCode() {
    int hash = 0;
    hash += (id != null ? id.hashCode() : 0);
    return hash;
  }

  @Override
  public boolean equals(Object object) {
    // TODO: Warning - this method won't work in the case the id fields are not set
    if (!(object instanceof OauthLoginState)) {
      return false;
    }
    OauthLoginState other = (OauthLoginState) object;
    if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "io.hops.hopsworks.persistence.entity.remote.oauth.OauthLoginState[ id=" + id + " ]";
  }

  public String getToken() {
    return token;
  }

  public void setToken(String token) {
    this.token = token;
  }
  
}
