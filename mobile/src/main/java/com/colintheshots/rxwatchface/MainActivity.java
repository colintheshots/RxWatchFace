package com.colintheshots.rxwatchface;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import com.colintheshots.rxwatchface.models.Gist;
import com.colintheshots.rxwatchface.models.GistDetail;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;
import com.google.common.collect.Iterables;

import java.util.List;
import java.util.concurrent.TimeUnit;

import retrofit.RequestInterceptor;
import retrofit.RestAdapter;
import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Schedulers;
import rx.subscriptions.CompositeSubscription;
import timber.log.Timber;

/**
 * Created by colin.lee on 11/2/14.
 */
public class MainActivity extends Activity
    implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    public static final String TAG = "MainActivity";
    public static final String MESSAGE_PATH = "/message";
    public static final String GITHUB_BASE_URL = "https://api.github.com";

    /** Set this variable to your GitHub personal access token */
    public final static String GITHUB_PERSONAL_ACCESS_TOKEN = "xxx";
    private static final long INITIAL_DELAY = 2000; // in milliseconds
    private static final long POLLING_INTERVAL = 5000; // in milliseconds

    private GithubClient mGitHubClient;
    private CompositeSubscription mSubscriptions;
    private GoogleApiClient mGoogleApiClient;
    private Subscription mWorker;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSubscriptions = new CompositeSubscription();

        if (mGitHubClient == null) {
            mGitHubClient = new RestAdapter.Builder()
                    .setRequestInterceptor(new RequestInterceptor() {
                        @Override
                        public void intercept(RequestFacade request) {
                            request.addHeader("Authorization", "token " + GITHUB_PERSONAL_ACCESS_TOKEN);
                        }
                    })
                    .setEndpoint(GITHUB_BASE_URL)
                    .setLogLevel(RestAdapter.LogLevel.HEADERS).build()
                    .create(GithubClient.class);
        }

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();

        mGoogleApiClient.connect();
    }

    @Override
    protected void onStop() {
        if (mSubscriptions!=null) {
            mSubscriptions.unsubscribe();
        }

        if (mWorker!=null) {
            mWorker.unsubscribe();
        }
        super.onStop();
    }

    /**
     * Calls the GitHub REST API to access the contents of the most recent gist, grabbing the first file it sees.
     * It then polls the API for updated text on a regular interval.
     */
    public void getGist() {

        mSubscriptions.add(Observable.create(new Observable.OnSubscribe<String>() {
            @Override
            public void call(final Subscriber<? super String> subscriber) {
                mWorker = Schedulers.newThread().createWorker()
                        .schedulePeriodically(new Action0() {
                            @Override
                            public void call() {
                                pollServer(subscriber);
                            }
                        }, INITIAL_DELAY, POLLING_INTERVAL, TimeUnit.MILLISECONDS);
            }
        }).observeOn(Schedulers.newThread())
            .subscribe(new Action1<String>() {
                @Override
                public void call(String content) {
                    // Send data to the watch
                    Log.d(TAG, "Sending content to watch.");
                    NodeApi.GetConnectedNodesResult nodes = Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).await();
                    for (Node node : nodes.getNodes()) {
                        Wearable.MessageApi.sendMessage(mGoogleApiClient, node.getId(), MESSAGE_PATH, content.getBytes()).await();
                    }
                }
            }));
    }

    /**
     * This function chains methods to ask the server for the latest gist file contents since we started subscribing.
     * @param subscriber
     */
    private void pollServer(final Subscriber<? super String> subscriber) {
        mSubscriptions.add(
                mGitHubClient.gists().flatMap(new Func1<List<Gist>, Observable<GistDetail>>() {
                    @Override
                    public Observable<GistDetail> call(List<Gist> gists) {
                        Gist gist = gists.get(0);
                        Timber.d("GitHubNetworkService", gist.getId());
                        return mGitHubClient.gist(gist.getId());
                    }
                })
                        .cache() // We cache the GistDetail here in case a new gist is created.
                        .map(new Func1<GistDetail, String>() {
                            @Override
                            public String call(GistDetail gistDetail) {
                                String fileName = Iterables.get(gistDetail.getFiles().keySet(), 0);
                                Timber.d("GitHubNetworkService",gistDetail.getFiles().get(fileName).getContent());
                                return gistDetail.getFiles().get(fileName).getContent();
                            }
                        })
                        .timeout(POLLING_INTERVAL / 2, TimeUnit.MILLISECONDS) // timeout if it takes too long
                        .retry(1) // retry each request once or try retrywhen() for exponential backoff
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(new Action1<String>() {
                            @Override
                            public void call(String content) {
                                subscriber.onNext(content);
                            }
                        }, new Action1<Throwable>() {
                            @Override
                            public void call(Throwable throwable) {
                                throwable.printStackTrace();
                            }
                        })
        );
    }

    @Override
    public void onConnected(Bundle bundle) {
        Timber.d(TAG, "Connected to Google Services API");
        getGist();
    }

    @Override
    public void onConnectionSuspended(int i) {
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
    }
}
