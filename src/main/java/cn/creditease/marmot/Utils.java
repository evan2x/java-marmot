/**
 * Copyright 2015 creditease Inc. All rights reserved.
 * @desc 工具类
 * @author aiweizhang(aiweizhang@creditease.cn)
 * @date 2015/05/05
 */

package cn.creditease.marmot;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.zip.DeflaterInputStream;
import java.util.zip.GZIPInputStream;

import cn.creditease.marmot.bean.ResponseBean;

public class Utils {

    /**
     * 使用指定provider发起请求
     * @param url 代理地址
     * @param request 原request对象
     * @param response 原response对象
     * @return 返回处理结果
     * @throws IOException
     */
    public static ResponseBean providerResource(String url, HttpServletRequest request, HttpServletResponse response) throws IOException {
        String queryString = request.getQueryString();
        String uri = request.getRequestURI();

        if(queryString == null){
            queryString = "";
        } else {
            queryString = "?" + queryString;
        }

        if(url.matches("^http(?:s)?://[\\s\\S]*$")){
            url = url + uri + queryString;
        } else {
            String scheme = request.getScheme() + "://";
            url = url.replaceAll("^(?:[a-z]+:/+)|(?:/*)", "");
            url = scheme + url + uri + queryString;
        }

        return requestProviderResource(url, request, response);
    }

    /**
     * 向provider发起资源请求
     * @param url provider地址
     * @param request 原request对象
     * @param response 原response对象
     * @return 返回处理结果
     * @throws IOException
     */
    private static ResponseBean requestProviderResource(String url, HttpServletRequest request, HttpServletResponse response) throws IOException {
        URL action = new URL(url);
        HttpURLConnection connection = (HttpURLConnection) action.openConnection();
        setRequestHeader(request, connection);

        int statusCode = connection.getResponseCode();
        if(statusCode == HttpURLConnection.HTTP_MOVED_PERM || statusCode == HttpURLConnection.HTTP_MOVED_TEMP){
            String location = connection.getHeaderField("location");
            if(location != null){
                return requestProviderResource(location, request, response);
            }
        }

        InputStream inputStream;
        String contentEncoding = connection.getContentEncoding();
        try {
            if(contentEncoding != null){
                switch (contentEncoding.toLowerCase()) {
                    case "gzip":
                        inputStream = new GZIPInputStream(connection.getInputStream());
                        break;
                    case "deflate":
                        inputStream = new DeflaterInputStream(connection.getInputStream());
                        break;
                    default:
                        inputStream = connection.getInputStream();
                }
            } else {
                inputStream = connection.getInputStream();
            }
        } catch (IOException e) {
            throw new IOException("Server returned HTTP response code: " + statusCode + " for URL: " + url);
        }

        ResponseBean result = new ResponseBean();
        result.setStatus(statusCode);
        result.setUrl(url);
        result.setData(stream2string(inputStream));

        response.setContentType(connection.getContentType());
        response.setContentLength(connection.getContentLength());
        response.setHeader("X-TARGET-URL", url);
        inputStream.close();
        return result;

    }

    private static void setRequestHeader(HttpServletRequest request, HttpURLConnection connection) throws IOException {
        // 提取原请求中的Headers
        Enumeration<String> names = request.getHeaderNames();
        HashSet<String> breakHeaders = new HashSet<String>(){{
            add("host");
        }};

        // 与原请求类型保持一致
        connection.setRequestMethod(request.getMethod());
        while(names.hasMoreElements()){
            String name = names.nextElement();
            if(name == null || breakHeaders.contains(name.toLowerCase())){
                continue;
            }
            String value = request.getHeader(name);
            // 与原请求的header字段保持一致
            connection.setRequestProperty(name, value);
        }

        // 处理请求的body
        BufferedReader reader = request.getReader();
        StringBuilder builder = new StringBuilder();
        String line;
        while((line = reader.readLine()) != null){
            builder.append(line);
        }

        if(!builder.toString().equals("")){
            byte[] bytes = builder.toString().getBytes();
            connection.setDoOutput(true);
            connection.getOutputStream().write(bytes);
            reader.close();
        }
    }

    /**
     * 将输入流转为字符串
     * @param stream 输入流
     * @return 解析后的的JSON Object
     * @throws IOException
     */
    public static String stream2string(InputStream stream) throws IOException {
        if(stream == null){
            return null;
        }
        InputStreamReader inputReader = new InputStreamReader(stream, "UTF-8");
        BufferedReader buffer = new BufferedReader(inputReader);
        StringBuilder builder = new StringBuilder();
        String line;
        while((line = buffer.readLine()) != null){
            builder.append(line);
        }

        buffer.close();
        inputReader.close();
        stream.close();

        return builder.toString();
    }

    /**
     * 标准化路径, 将多个"/"替换为一个
     * @param path 未经过处理的路径
     * @return 标准化路径
     */
    public static String pathNormalize(String path){
        return path.replaceAll("(/)+\\1", "$1");
    }

    /**
     * 去除路径中首尾的"/"
     * @param path 原路径
     * @return 去除了首尾"/"的路径
     */
    public static String slashTrim(String path){
        if(path.startsWith("/")){
            path = path.substring(1, path.length());
        }

        if(path.endsWith("/")){
            path = path.substring(0, path.length() - 1);
        }

        return path;
    }
}
