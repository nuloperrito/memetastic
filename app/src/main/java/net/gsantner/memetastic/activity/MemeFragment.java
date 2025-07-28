package net.gsantner.memetastic.activity;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.gsantner.memetastic.App;
import net.gsantner.memetastic.data.MemeData;
import net.gsantner.memetastic.ui.GridDecoration;
import net.gsantner.memetastic.ui.MemeItemAdapter;
import net.gsantner.memetastic.util.AppCast;
import net.gsantner.memetastic.util.AppSettings;
import net.gsantner.memetastic.util.ContextUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.gsantner.memetastic.R;
import io.github.gsantner.memetastic.databinding.FragmentMemeBinding;

public class MemeFragment extends Fragment {
    private FragmentMemeBinding binding;
    private RecyclerView recyclerMemeList;
    private LinearLayout emptylistLayout;
    private TextView emptylistText;

    private App app;
    private int tabPos;
    private String[] tagKeys, tagValues;
    private List<MemeData.Image> imageList;
    private MemeItemAdapter recyclerMemeAdapter;

    public RecyclerView getRecyclerMemeList() {
        return recyclerMemeList;
    }

    public void setRecyclerMemeList(RecyclerView recyclerMemeList) {
        this.recyclerMemeList = recyclerMemeList;
    }

    // newInstance constructor for creating fragment with arguments
    public static MemeFragment newInstance(int pagePos) {
        MemeFragment fragmentFirst = new MemeFragment();
        Bundle args = new Bundle();
        args.putInt("pos", pagePos);
        fragmentFirst.setArguments(args);
        return fragmentFirst;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        app = (App) requireActivity().getApplication();
        tabPos = getArguments().getInt("pos");

        imageList = new ArrayList<>();
    }

    private void reloadAdapter() {
        tagKeys = getResources().getStringArray(R.array.meme_tags__keys);
        tagValues = getResources().getStringArray(R.array.meme_tags__titles);
        if (tabPos >= 0 && tabPos < tagKeys.length) {
            imageList = MemeData.getImagesWithTag(tagKeys[tabPos]);
        }

        if (app.settings.isShuffleTagLists()) {
            Collections.shuffle(imageList);
        }

        List<MemeData.Image> hiddenImages = new ArrayList<>();
        for (MemeData.Image image : imageList) {
            if (app.settings.isHidden(image.fullPath.getAbsolutePath())) {
                hiddenImages.add(image);
            }
        }
        imageList.removeAll(hiddenImages);

        if (recyclerMemeAdapter != null) {
            recyclerMemeAdapter.setOriginalImageDataList(imageList);
            recyclerMemeAdapter.notifyDataSetChanged();
            setRecyclerMemeListAdapter(recyclerMemeAdapter);
        }
    }

    @SuppressLint("StringFormatInvalid")
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentMemeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        recyclerMemeList = binding.memeFragmentRecyclerView;
        emptylistLayout = binding.memeFragmentListEmptyLayout;
        emptylistText = binding.memeFragmentListEmptyText;

        recyclerMemeList.setHasFixedSize(true);
        recyclerMemeList.setItemViewCacheSize(app.settings.getGridColumnCountPortrait() * app.settings.getGridColumnCountLandscape() * 2);
        recyclerMemeList.setDrawingCacheEnabled(true);
        recyclerMemeList.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_LOW);
        recyclerMemeList.addItemDecoration(new GridDecoration(1.7f));

        int viewType = AppSettings.get().getMemeListViewType();
        if (viewType == MemeItemAdapter.VIEW_TYPE__ROWS_WITH_TITLE) {
            RecyclerView.LayoutManager recyclerLinearLayout = new LinearLayoutManager(requireActivity(), LinearLayoutManager.VERTICAL, false);
            recyclerMemeList.setLayoutManager(recyclerLinearLayout);
        } else {
            int gridColumns = ContextUtils.get().isInPortraitMode()
                    ? app.settings.getGridColumnCountPortrait()
                    : app.settings.getGridColumnCountLandscape();
            RecyclerView.LayoutManager recyclerGridLayout = new GridLayoutManager(requireActivity(), gridColumns);

            recyclerMemeList.setLayoutManager(recyclerGridLayout);
        }

        emptylistText.setText(getString(R.string.no_custom_templates_description__appspecific, getString(R.string.custom_templates_visual)));
        recyclerMemeAdapter = new MemeItemAdapter(imageList, requireActivity(), AppSettings.get().getMemeListViewType());
        setRecyclerMemeListAdapter(recyclerMemeAdapter);

        return root;
    }

    private void setRecyclerMemeListAdapter(MemeItemAdapter adapter) {
        adapter.setFilter("");
        recyclerMemeList.setAdapter(adapter);
        boolean isEmpty = adapter.getItemCount() == 0;
        emptylistLayout.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        recyclerMemeList.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    private final BroadcastReceiver localBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (AppCast.ASSETS_LOADED.ACTION.equals(action)) {
                reloadAdapter();
            }
        }
    };

    @Override
    public void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(requireActivity()).registerReceiver(localBroadcastReceiver, AppCast.getLocalBroadcastFilter());
        reloadAdapter();
    }

    @Override
    public void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(requireActivity()).unregisterReceiver(localBroadcastReceiver);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}