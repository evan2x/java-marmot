/**
 * Copyright 2015 creditease Inc. All rights reserved.
 * @desc router bean
 * @author aiweizhang(aiweizhang@creditease.cn)
 * @date 2015/05/05
 */

package cn.creditease.marmot.bean;

import javax.servlet.http.Cookie;
import java.util.HashSet;

public class RouterBean {
    private RouteBean route = new RouteBean();
    private HashSet<Cookie> cookies;
    private boolean matched = false;

    public RouteBean getRoute() {
        return route;
    }

    public void setRoute(RouteBean route) {
        this.route = route;
    }

    public HashSet<Cookie> getCookies() {
        return cookies;
    }

    public void setCookies(HashSet<Cookie> cookies) {
        this.cookies = cookies;
    }

    public boolean isMatched() {
        return matched;
    }

    public void setMatched(boolean matched) {
        this.matched = matched;
    }
}
