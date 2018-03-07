package com.evan2x.marmot.object;

import java.io.InputStream;

public class RemoteData {
  private String url;
  private byte[] data;

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public byte[] getData() {
    return data;
  }

  public void setData(byte[] data) {
    this.data = data;
  }
}
