
package com.creditease.marmot.bean;

public class RouteBean {
  private String pathName;
  private String pathRule;
  private String location;
  private String provider;
  private String redirect;
  private String contentType;

  public String getPathName() {
    return pathName;
  }

  public void setPathName(String pathName) {
    this.pathName = pathName;
  }

  public String getPathRule() {
    return pathRule;
  }

  public void setPathRule(String pathRule) {
    this.pathRule = pathRule;
  }

  public String getLocation() {
    return location;
  }

  public void setLocation(String location) {
    this.location = location;
  }

  public String getProvider() {
    return provider;
  }

  public void setProvider(String provider) {
    this.provider = provider;
  }

  public String getContentType() {
    return contentType;
  }

  public void setContentType(String contentType) {
    this.contentType = contentType;
  }

  public String getRedirect() {
    return redirect;
  }

  public void setRedirect(String redirect) {
    this.redirect = redirect;
  }
}
