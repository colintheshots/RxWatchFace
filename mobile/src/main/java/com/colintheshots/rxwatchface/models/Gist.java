package com.colintheshots.rxwatchface.models;

import com.google.gson.annotations.Expose;

/**
 * Created by colin.lee on 11/2/14.
 */
public class Gist {
    @Expose
    private String id;

    @Expose
    private String description;

    @Expose
    private String html_url;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getHtml_url() {
        return html_url;
    }

    public void setHtml_url(String html_url) {
        this.html_url = html_url;
    }
}
