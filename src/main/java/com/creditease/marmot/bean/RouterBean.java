
package com.creditease.marmot.bean;

import javax.servlet.http.Cookie;
import java.util.List;

public class RouterBean {
  private RouteBean route = new RouteBean();
  private List<Cookie> cookies;
  private boolean matched = false;

  public RouteBean getRoute() {
    return route;
  }

  public void setRoute(RouteBean route) {
    this.route = route;
  }

  public List<Cookie> getCookies() {
    return cookies;
  }

  public void setCookies(List<Cookie> cookies) {
    this.cookies = cookies;
  }

  public boolean isMatched() {
    return matched;
  }

  public void setMatched(boolean matched) {
    this.matched = matched;
  }
}
