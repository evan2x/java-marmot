package com.creditease.marmot.support.spring;

import com.alibaba.fastjson.support.spring.FastJsonJsonView;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 让 SpringMVC 支持 marmot provider 视图的功能
 */

public class ProviderViewInterceptor extends HandlerInterceptorAdapter {

  private static final String MARMOT_REQUEST = "MarmotHttpRequest";

  @Override
  public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView)
          throws Exception {
    String requestType = request.getHeader("X-Requested-With");

    if (requestType != null && requestType.equals(MARMOT_REQUEST) && modelAndView != null) {
      FastJsonJsonView jsonView = new FastJsonJsonView();
      modelAndView.setView(jsonView);
    }
  }

}
