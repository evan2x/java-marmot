package com.evan2x.marmot.object;

import javax.servlet.http.Cookie;
import java.util.List;

public class Router {

  private Route route = new Route();
  private List<Cookie> cookies;
  private boolean matched = false;

  public Route getRoute() {
    return route;
  }

  public void setRoute(Route route) {
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
