package com.antplay.models;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class GetVmReq {
    @SerializedName("user_type")
    @Expose
    private String user_type;

    public GetVmReq(String userType) {
        this.user_type=  userType;
    }

    public String getUser_type() {
        return user_type;
    }

    public void setUser_type(String user_type) {
        this.user_type = user_type;
    }
}
