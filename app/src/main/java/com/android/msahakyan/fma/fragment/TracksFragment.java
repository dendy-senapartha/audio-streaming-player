package com.android.msahakyan.fma.fragment;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.android.msahakyan.fma.R;
import com.android.msahakyan.fma.adapter.ItemClickListener;
import com.android.msahakyan.fma.adapter.ItemListAdapter;
import com.android.msahakyan.fma.application.FmaApplication;
import com.android.msahakyan.fma.model.Genre;
import com.android.msahakyan.fma.model.Page;
import com.android.msahakyan.fma.network.FmaApiService;
import com.android.msahakyan.fma.network.NetworkRequestListener;
import com.android.msahakyan.fma.util.AppUtils;
import com.android.msahakyan.fma.util.InfiniteScrollListener;
import com.android.msahakyan.fma.util.Item;
import com.android.msahakyan.fma.util.ItemDecorator;
import com.android.msahakyan.fma.view.MoreDataLoaderView;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import butterknife.Bind;
import timber.log.Timber;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link TracksFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class TracksFragment extends BaseNetworkRequestFragment<Page<Item>> implements
    MoreDataLoaderView.LoadMoreDataCallback, ItemClickListener<Item> {

    public static final String QUALIFIER = "TRACK_WITH_ICON";

    private static final int DEFAULT_THRESHOLD = 1;
    private static final String KEY_SELECTED_GENRE = "KEY_SELECTED_GENRE";

    @Inject
    FmaApiService fmaApiService;

    @Bind(R.id.list_view)
    RecyclerView mListView;
    @Bind(R.id.loading_footer)
    MoreDataLoaderView mLoadingFooter;

    private ItemListAdapter adapter;
    private InfiniteScrollListener mInfiniteScrollListener;
    private Genre mSelectedGenre;
    private int mPage = 1;

    public TracksFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment TracksFragment.
     */
    public static TracksFragment newInstance(Genre genre) {
        TracksFragment fragment = new TracksFragment();
        Bundle args = new Bundle();
        args.putParcelable(KEY_SELECTED_GENRE, genre);
        fragment.setArguments(args);

        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mSelectedGenre = getArguments().getParcelable(KEY_SELECTED_GENRE);
        createAdapter();
        mInfiniteScrollListener = new InfiniteScrollListener(DEFAULT_THRESHOLD) {
            @Override
            protected void onLoadMore() {
                mLoadingFooter.setLoadingShown(isResumed() && adapter.getItemCount() > 0);
                loadMoreData();
            }
        };
        setHasOptionsMenu(true);
    }

    private void setLayoutManager() {
        GridLayoutManager layoutManager = new GridLayoutManager(activity, 2);
        mListView.setLayoutManager(layoutManager);
        mInfiniteScrollListener.setLayoutManager(layoutManager);

        mListView.addOnScrollListener(mInfiniteScrollListener);
        mListView.setAdapter(adapter);
        mListView.addItemDecoration(new ItemDecorator(3, 3));
    }

    private void createAdapter() {
        mPage = 1;
        adapter = new ItemListAdapter(activity, new ArrayList<>(), this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_tracks, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setContentView(mListView);
        setLayoutManager();
        if (getView() != null && adapter != null) {
            if (adapter.getItems().isEmpty()) {
                showProgressView();
                refresh();
            } else {
                hideProgressView();
                Timber.d("Items already loaded -- skip");
            }
        }
        mLoadingFooter.setLoadDataCallback(this);
    }

    @Override
    protected void onSuccess(Page<Item> response, int statusCode) {
        super.onSuccess(response, statusCode);
        hideProgressView();

        if (statusCode == HttpURLConnection.HTTP_OK && response != null) {
            showTracks(response.getItems());
        } else {
            Timber.e("Something went wrong! StatusCode: " + statusCode + ", response: " + response);
            super.onError(statusCode, "Something went wrong!");
        }
    }

    private void showTracks(List<Item> items) {
        AppUtils.setCollectionQualifier(items, QUALIFIER);
        adapter.addAll(items);
    }

    @Override
    protected void onError(int statusCode, String errorMessage) {
        super.onError(statusCode, errorMessage);
        hideProgressView();
        Toast.makeText(activity, "Status code: " + statusCode + " error: " + errorMessage, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void refresh() {
        super.refresh();
        setNetworkRequest(fmaApiService.getTracksByGenreId(getNetworkListener(), mSelectedGenre.getId(), mPage));
    }

    @Override
    public void loadMoreData() {
        fmaApiService.getTracksByGenreId(new NetworkRequestListener<Page<Item>>() {
            @Override
            public void onSuccess(@Nullable Page<Item> response, int statusCode) {
                if (statusCode == HttpURLConnection.HTTP_OK && response != null) {
                    List<Item> items = response.getItems();
                    AppUtils.setCollectionQualifier(items, QUALIFIER);
                    TracksFragment.this.onLoadMoreDataSuccess(response.getItems());
                } else {
                    Timber.e("Something went wrong! StatusCode: " + statusCode + ", response: " + response);
                    onError(statusCode, "Something went wrong!");
                }
            }

            @Override
            public void onError(int statusCode, String errorMessage) {
                TracksFragment.this.onError(statusCode, errorMessage);
            }
        }, mSelectedGenre.getId(), ++mPage);
    }

    private void onLoadMoreDataSuccess(List<Item> result) {
        mInfiniteScrollListener.setVisibleThreshold(Math.max(DEFAULT_THRESHOLD, result.size() / 2));
        if (mLoadingFooter != null) {
            mLoadingFooter.setLoadingShown(false);
        }
        adapter.addAll(result);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        if (activity != null) {
            activity.showSearchIcon(true);
        }
    }

    @Override
    public void onItemClicked(Item item, RecyclerView.ViewHolder holder) {
        navigationManager.showTrackPlayFragment(adapter.getItems(), holder.getAdapterPosition());
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        FmaApplication.get(activity).getApplicationComponent().inject(this);
    }
}
