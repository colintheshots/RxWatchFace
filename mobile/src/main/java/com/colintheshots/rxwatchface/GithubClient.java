package com.colintheshots.rxwatchface;

import com.colintheshots.rxwatchface.models.Gist;
import com.colintheshots.rxwatchface.models.GistDetail;

import java.util.List;

import retrofit.http.GET;
import retrofit.http.Path;
import rx.Observable;

/**
 * Created by colin.lee on 11/2/14.
 */
public interface GithubClient {

    @GET("/gists")
    Observable<List<Gist>> gists();

    @GET("/gists/{id}")
    Observable<GistDetail> gist(@Path("id") String id);
}
