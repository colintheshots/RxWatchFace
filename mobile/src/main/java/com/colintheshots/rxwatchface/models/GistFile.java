package com.colintheshots.rxwatchface.models;

import com.google.gson.annotations.Expose;

/**
 * Created by colin.lee on 11/2/14.
 */
public class GistFile {

    @Expose
    private String content;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}