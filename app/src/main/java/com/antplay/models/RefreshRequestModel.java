package com.antplay.models;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class RefreshRequestModel {
    @SerializedName("access")
    @Expose
    private String access;

    public RefreshRequestModel(String access) {
        this.access = access;
    }

    public String getAccess() {
        return access;
    }
    public void setAccess(String access) {
        this.access = access;
    }
}
