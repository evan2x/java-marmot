package com.evan2x.marmot.object;

public class Route {
  private String pathName;
  private String pathRule;
  private String location;
  private String proxy;
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

  public String getProxy() {
    return proxy;
  }

  public void setProxy(String proxy) {
    this.proxy = proxy;
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
