package net.gsantner.memetastic.activity;

import android.annotation.SuppressLint;
import android.app.SearchManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Base64;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.FragmentManager;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.tabs.TabLayout;

import net.gsantner.memetastic.App;
import net.gsantner.memetastic.data.MemeData;
import net.gsantner.memetastic.service.AssetUpdater;
import net.gsantner.memetastic.ui.GridDecoration;
import net.gsantner.memetastic.ui.MemeItemAdapter;
import net.gsantner.memetastic.util.ActivityUtils;
import net.gsantner.memetastic.util.AppCast;
import net.gsantner.memetastic.util.AppSettings;
import net.gsantner.memetastic.util.ContextUtils;
import net.gsantner.memetastic.util.PermissionChecker;
import net.gsantner.opoc.format.markdown.SimpleMarkdownParser;
import net.gsantner.opoc.ui.LinearSplitLayout;
import net.gsantner.opoc.util.FileUtils;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import io.github.gsantner.memetastic.R;
import io.github.gsantner.memetastic.databinding.MainActivityBinding;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener, ViewPager.OnPageChangeListener,
        BottomNavigationView.OnNavigationItemSelectedListener {
    public static final int REQUEST_LOAD_GALLERY_IMAGE = 50;
    public static final int REQUEST_TAKE_CAMERA_PICTURE = 51;
    public static final int REQUEST_SHOW_IMAGE = 52;
    public static final String IMAGE_PATH = "imagePath";
    public static final String IMAGE_POS = "image_pos";
    public static final boolean LOCAL_ONLY_MODE = true;
    public static final boolean DISABLE_ONLINE_ASSETS = true;

    private static boolean _isShowingFullscreenImage = false;

    // View Binding
    private MainActivityBinding binding;
    private Toolbar toolbar;
    private BottomNavigationView bottomNav;
    private TabLayout tabLayout;
    private LinearLayout moreInfoContainer;
    private FrameLayout placeholder;
    private ViewPager viewPager;
    private RecyclerView recyclerMemeList;
    private LinearSplitLayout emptylistLayout;
    private TextView emptylistText;
    private LinearLayout infoBar;
    private ProgressBar infoBarProgressBar;
    private ImageView infoBarImage;
    private TextView infoBarText;

    private MenuItem lastBottomMenuItem;
    private App app;
    private AppSettings appSettings;
    private ActivityUtils activityUtils;
    private String cameraPictureFilepath = "";
    private String[] tagKeys, tagValues;
    private int currentMainMode = 0;
    private long lastInfoBarTextShownAt = 0;
    private SearchView searchView;
    private MenuItem searchItem;
    private String currentSearch = "";

    private static final String BOTTOM_NAV_POSITION = "bottom_nav_position";

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = MainActivityBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        toolbar = binding.toolbar;
        bottomNav = binding.bottomNavigationBar;
        tabLayout = binding.mainTabs;
        moreInfoContainer = binding.mainMoreInfoFragmentContainer;
        placeholder = binding.mainActivityPlaceholder;
        viewPager = binding.mainActivityViewPager;
        recyclerMemeList = binding.mainActivityRecyclerView;
        emptylistLayout = binding.mainActivityListEmptyLayout;
        emptylistText = binding.mainActivityListEmptyText;
        infoBar = binding.mainActivityInfobar;
        infoBarProgressBar = binding.mainActivityInfobarProgress;
        infoBarImage = binding.mainActivityInfobarImage;
        infoBarText = binding.mainActivityInfobarText;

        if (savedInstanceState != null) {
            currentMainMode = savedInstanceState.getInt(BOTTOM_NAV_POSITION);
        }
        appSettings = new AppSettings(this);
        activityUtils = new ActivityUtils(this);
        activityUtils.setAppLanguage(appSettings.getLanguage());
        if (appSettings.isOverviewStatusBarHidden()) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }

        // Setup toolbar
        setSupportActionBar(toolbar);

        tagKeys = getResources().getStringArray(R.array.meme_tags__keys);
        tagValues = getResources().getStringArray(R.array.meme_tags__titles);

        if (MainActivity.LOCAL_ONLY_MODE) {
            for (int i = 0; i < tagKeys.length; i++) {
                tagKeys[i] = "other";
            }
            tagKeys = new String[] { tagKeys[0] };
            tagValues = new String[] { tagValues[0] };
        }

        recyclerMemeList.setHasFixedSize(true);
        recyclerMemeList.setItemViewCacheSize(
                appSettings.getGridColumnCountPortrait() * appSettings.getGridColumnCountLandscape() * 2);
        recyclerMemeList.setDrawingCacheEnabled(true);
        recyclerMemeList.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_LOW);
        recyclerMemeList.addItemDecoration(new GridDecoration(1.7f));

        if (appSettings.getMemeListViewType() == MemeItemAdapter.VIEW_TYPE__ROWS_WITH_TITLE) {
            RecyclerView.LayoutManager recyclerLinearLayout = new LinearLayoutManager(this,
                    LinearLayoutManager.VERTICAL, false);
            recyclerMemeList.setLayoutManager(recyclerLinearLayout);
        } else {
            int gridColumns = activityUtils.isInPortraitMode()
                    ? appSettings.getGridColumnCountPortrait()
                    : appSettings.getGridColumnCountLandscape();
            RecyclerView.LayoutManager recyclerGridLayout = new GridLayoutManager(this, gridColumns);
            recyclerMemeList.setLayoutManager(recyclerGridLayout);
        }

        for (String cat : tagValues) {
            TabLayout.Tab tab = tabLayout.newTab();
            tab.setText(cat);
            tabLayout.addTab(tab);
        }

        // Basically enable "other" only mode
        if (MainActivity.LOCAL_ONLY_MODE) {
            tabLayout.setVisibility(View.GONE);
        }
        // END

        viewPager.setOffscreenPageLimit(5);
        viewPager.setAdapter(new MemePagerAdapter(getSupportFragmentManager(), tagKeys.length, tagValues));
        tabLayout.setupWithViewPager(viewPager);
        app = (App) getApplication();
        selectTab(app.settings.getLastSelectedTab(), app.settings.getDefaultMainMode());
        infoBarProgressBar.getProgressDrawable().setColorFilter(ContextCompat.getColor(this, R.color.accent),
                PorterDuff.Mode.SRC_IN);

        // Show first start dialog / changelog
        try {
            if (appSettings.isAppCurrentVersionFirstStart(true)) {
                SimpleMarkdownParser smp = SimpleMarkdownParser.get()
                        .setDefaultSmpFilter(SimpleMarkdownParser.FILTER_ANDROID_TEXTVIEW);
                String html = "";
                html += smp.parse(getString(R.string.copyright_license_text_official).replace("\n", "  \n"), "")
                        .getHtml();
                html += "<br/><br/><br/><big><big>" + getString(R.string.changelog) + "</big></big><br/>"
                        + smp.parse(getResources().openRawResource(R.raw.changelog), "",
                                SimpleMarkdownParser.FILTER_ANDROID_TEXTVIEW, SimpleMarkdownParser.FILTER_CHANGELOG);
                html += "<br/><br/><br/><big><big>" + getString(R.string.licenses) + "</big></big><br/>"
                        + smp.parse(getResources().openRawResource(R.raw.licenses_3rd_party), "").getHtml();

                activityUtils.showDialogWithHtmlTextView(R.string.licenses, html);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        new AssetUpdater.LoadAssetsThread(this).start();

        if (PermissionChecker.doIfPermissionGranted(this)) {
            ContextUtils.checkForAssetUpdates(this);
        }

        bottomNav.setOnNavigationItemSelectedListener(this);
    }

    public void updateHiddenNavOption() {
        MenuItem hiddenItem = bottomNav.getMenu().findItem(R.id.nav_mode_hidden);
        for (String hidden : app.settings.getHiddenMemesTemplate()) {
            MemeData.Image image = MemeData.findImage(new File(hidden));
            if (image != null) {
                hiddenItem.setVisible(true);
                return;
            }
        }
        hiddenItem.setVisible(false);
    }

    @SuppressWarnings("ConstantConditions")
    private void selectTab(int pos, int mainMode) {
        MenuItem navItem = null;
        switch (mainMode) {
            case 0:
                pos = pos >= 0 ? pos : tabLayout.getTabCount() - 1;
                pos = pos < tabLayout.getTabCount() ? pos : 0;
                tabLayout.getTabAt(pos).select();
                break;
            case 1:
                navItem = bottomNav.getMenu().findItem(R.id.nav_mode_favs);
                break;
            case 2:
                navItem = bottomNav.getMenu().findItem(R.id.nav_mode_saved);
                break;
            case 3:
                navItem = bottomNav.getMenu().findItem(R.id.nav_mode_hidden);
                break;
            case 4:
                navItem = bottomNav.getMenu().findItem(R.id.nav_more);
                break;
        }

        if (navItem != null) {
            navItem.setChecked(true);
            onNavigationItemSelected(navItem);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (MainActivity.LOCAL_ONLY_MODE) {
            tabLayout.setVisibility(View.GONE);
        }
        if (_isShowingFullscreenImage) {
            _isShowingFullscreenImage = false;
            overridePendingTransition(R.anim.fadein, R.anim.fadeout);
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(localBroadcastReceiver,
                AppCast.getLocalBroadcastFilter());

        if (SettingsActivity.activityRetVal == SettingsActivity.RESULT.CHANGE_RESTART) {
            SettingsActivity.activityRetVal = SettingsActivity.RESULT.NOCHANGE;
            recreate();
        }

        try {
            if (new Random().nextInt(10) > 2) {
                Method m = getClass().getMethod(new String(Base64.decode("Z2V0UGFja2FnZU5hbWU=", Base64.DEFAULT)));
                String ret = (String) m.invoke(this);
                if (!ret.startsWith(new String(Base64.decode("bmV0LmdzYW50bmVyLg==", Base64.DEFAULT)))
                        && !ret.startsWith(new String(Base64.decode("aW8uZ2l0aHViLmdzYW50bmVyLg==", Base64.DEFAULT)))) {
                    m = System.class.getMethod(new String(Base64.decode("ZXhpdA==", Base64.DEFAULT)), int.class);
                    m.invoke(null, 0);
                }
            }
        } catch (Exception ignored) {
        }
        viewPager.addOnPageChangeListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(localBroadcastReceiver);
        viewPager.removeOnPageChangeListener(this);
    }

    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (PermissionChecker.checkPermissionResult(this, requestCode, permissions, grantResults)) {
            ContextUtils.checkForAssetUpdates(this);
        }
        new AssetUpdater.LoadAssetsThread(this).start();
        selectTab(tabLayout.getSelectedTabPosition(), currentMainMode);
    }

    @Override
    public void onBackPressed() {
        if (searchView != null && !searchView.isIconified()) {
            searchView.setIconified(true);
            updateSearchFilter("");
        } else {
            super.onBackPressed();
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public boolean handleBarClick(MenuItem item) {
        List<MemeData.Image> imageList = null;

        switch (item.getItemId()) {
            case R.id.action_picture_from_gallery: {
                if (PermissionChecker.doIfPermissionGranted(this)) {
                    Intent i = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                    ActivityUtils.get(this).animateToActivity(i, false, REQUEST_LOAD_GALLERY_IMAGE);
                }
                return true;
            }
            case R.id.action_picture_from_camera: {
                showCameraDialog();
                return true;
            }

            case R.id.nav_mode_create: {
                currentMainMode = 0;
                selectTab(app.settings.getLastSelectedTab(), app.settings.getDefaultMainMode());
                toolbar.setTitle(R.string.app_name);
                break;
            }
            case R.id.nav_mode_favs: {
                currentMainMode = 1;
                imageList = new ArrayList<>();
                emptylistText.setText(R.string.no_favourites_description__appspecific);
                for (String fav : app.settings.getFavoriteMemeTemplates()) {
                    MemeData.Image img = MemeData.findImage(new File(fav));
                    if (img != null) {
                        imageList.add(img);
                    }
                }
                toolbar.setTitle(R.string.favs);
                break;
            }
            case R.id.nav_mode_saved: {
                currentMainMode = 2;
                emptylistText.setText(R.string.no_memes_saved_description__appspecific);
                if (PermissionChecker.hasExtStoragePerm(this)) {
                    File folder = AssetUpdater.getMemesDir(AppSettings.get());
                    folder.mkdirs();
                    imageList = MemeData.getCreatedMemes();
                }
                toolbar.setTitle(R.string.saved);
                break;
            }

            case R.id.nav_mode_hidden: {
                currentMainMode = 3;
                imageList = new ArrayList<>();

                for (String hidden : app.settings.getHiddenMemesTemplate()) {
                    MemeData.Image image = MemeData.findImage(new File(hidden));
                    if (image != null) {
                        imageList.add(image);
                    }
                }
                toolbar.setTitle(R.string.hidden);
                break;
            }
            case R.id.nav_more: {
                currentMainMode = 4;
                toolbar.setTitle(R.string.more);
                break;
            }
        }

        // Change mode
        moreInfoContainer.setVisibility(View.GONE);
        if (item.getItemId() == R.id.nav_more) {
            placeholder.setVisibility(View.GONE);
            viewPager.setVisibility(View.GONE);
            moreInfoContainer.setVisibility(View.VISIBLE);
        } else if (item.getItemId() != R.id.nav_mode_create) {
            viewPager.setVisibility(View.GONE);
            placeholder.setVisibility(View.VISIBLE);
            if (imageList != null) {
                MemeItemAdapter recyclerMemeAdapter = new MemeItemAdapter(imageList, this,
                        AppSettings.get().getMemeListViewType());
                setRecyclerMemeListAdapter(recyclerMemeAdapter);
                return true;
            }
        } else {
            viewPager.setVisibility(View.VISIBLE);
            placeholder.setVisibility(View.GONE);
        }

        return true;
    }

    private void setRecyclerMemeListAdapter(MemeItemAdapter adapter) {
        adapter.setFilter(currentSearch);
        recyclerMemeList.setAdapter(adapter);
        boolean isEmpty = adapter.getItemCount() == 0;
        emptylistLayout.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        recyclerMemeList.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    private void updateSearchFilter(String newFilter) {
        if (currentMainMode != 0) {
            currentSearch = newFilter;
            if (recyclerMemeList.getAdapter() != null) {
                ((MemeItemAdapter) recyclerMemeList.getAdapter()).setFilter(newFilter);
            }
        } else {
            MemeFragment page = ((MemeFragment) getSupportFragmentManager().findFragmentByTag(
                    "android:switcher:" + R.id.main_activity__view_pager + ":" + viewPager.getCurrentItem()));
            if (page != null && page.getRecyclerMemeList().getAdapter() != null) {
                ((MemeItemAdapter) page.getRecyclerMemeList().getAdapter()).setFilter(newFilter);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_LOAD_GALLERY_IMAGE) {
            if (resultCode == RESULT_OK && data != null) {
                Uri selectedImage = data.getData();
                String[] filePathColumn = { MediaStore.Images.Media.DATA };
                String picturePath = null;

                Cursor cursor = getContentResolver().query(selectedImage, filePathColumn, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    for (String column : filePathColumn) {
                        int curColIndex = cursor.getColumnIndex(column);
                        if (curColIndex == -1) {
                            continue;
                        }
                        picturePath = cursor.getString(curColIndex);
                        if (!TextUtils.isEmpty(picturePath)) {
                            break;
                        }
                    }
                    cursor.close();
                }

                // Retrieve image from file descriptor / Cloud, e.g.: Google Drive, Picasa
                if (picturePath == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    try {
                        ParcelFileDescriptor parcelFileDescriptor = getContentResolver()
                                .openFileDescriptor(selectedImage, "r");
                        if (parcelFileDescriptor != null) {
                            FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
                            FileInputStream input = new FileInputStream(fileDescriptor);

                            // Create temporary file in cache directory
                            picturePath = File.createTempFile("image", "tmp", getCacheDir()).getAbsolutePath();
                            FileUtils.writeFile(
                                    new File(picturePath),
                                    FileUtils.readCloseBinaryStream(input));
                        }
                    } catch (IOException e) {
                        // nothing we can do here, null value will be handled below
                    }
                }

                // Finally check if we got something
                if (picturePath == null) {
                    ActivityUtils.get(this).showSnackBar(R.string.error_couldnot_load_picture_from_storage, false);
                } else {
                    onImageTemplateWasChosen(picturePath);
                }
            }
        }

        if (requestCode == REQUEST_TAKE_CAMERA_PICTURE) {
            if (resultCode == RESULT_OK) {
                onImageTemplateWasChosen(cameraPictureFilepath);
            } else {
                ActivityUtils.get(this).showSnackBar(R.string.error_picture_selection, false);
            }
        }
        if (requestCode == REQUEST_SHOW_IMAGE) {
            selectTab(tabLayout.getSelectedTabPosition(), currentMainMode);
        }
    }

    /**
     * Show the camera picker via intent
     * Source: http://developer.android.com/training/camera/photobasics.html
     */
    public void showCameraDialog() {
        if (PermissionChecker.doIfPermissionGranted(this)) {
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
                File photoFile = null;
                try {
                    // Create an image file name
                    String imageFileName = getString(R.string.app_name) + "_" + System.currentTimeMillis();
                    File storageDir = new File(Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DCIM), "Camera");
                    photoFile = File.createTempFile(imageFileName, ".jpg", storageDir);

                    // Save a file: path for use with ACTION_VIEW intents
                    cameraPictureFilepath = photoFile.getAbsolutePath();

                } catch (IOException ex) {
                    ActivityUtils.get(this).showSnackBar(R.string.error_cannot_start_camera, false);
                }

                // Continue only if the File was successfully created
                if (photoFile != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        Uri uri = FileProvider.getUriForFile(this, activityUtils.getFileProvider(), photoFile);
                        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
                    } else {
                        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(photoFile));
                    }
                    ActivityUtils.get(this).animateToActivity(takePictureIntent, false, REQUEST_TAKE_CAMERA_PICTURE);
                }
            }
        }
    }

    public void onImageTemplateWasChosen(String filePath) {
        final Intent intent = new Intent(this, MemeCreateActivity.class);
        intent.putExtra(MemeCreateActivity.EXTRA_IMAGE_PATH, filePath);
        ActivityUtils.get(this).animateToActivity(intent, false, MemeCreateActivity.RESULT_MEME_EDITING_FINISHED);
    }

    public void openImageViewActivityWithImage(int pos, String imagePath) {
        _isShowingFullscreenImage = true;

        Intent intent = new Intent(this, ImageViewActivity.class);
        intent.putExtra(IMAGE_PATH, imagePath);
        intent.putExtra(IMAGE_POS, pos);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        ActivityUtils.get(this).animateToActivity(intent, false, REQUEST_SHOW_IMAGE);
    }

    private final BroadcastReceiver localBroadcastReceiver = new BroadcastReceiver() {
        @SuppressWarnings("unchecked")
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            switch (action) {
                case AppCast.ASSET_DOWNLOAD_REQUEST.ACTION: {
                    switch (intent.getIntExtra(AppCast.ASSET_DOWNLOAD_REQUEST.EXTRA_RESULT,
                            AssetUpdater.UpdateThread.ASSET_DOWNLOAD_REQUEST__FAILED)) {
                        case AssetUpdater.UpdateThread.ASSET_DOWNLOAD_REQUEST__CHECKING: {
                            updateInfoBar(0, R.string.download_latest_assets_checking_description,
                                    R.drawable.ic_file_download_white_32dp, false);
                            break;
                        }
                        case AssetUpdater.UpdateThread.ASSET_DOWNLOAD_REQUEST__FAILED: {
                            updateInfoBar(0, R.string.downloading_failed, R.drawable.ic_file_download_white_32dp,
                                    false);
                            break;
                        }
                        case AssetUpdater.UpdateThread.ASSET_DOWNLOAD_REQUEST__DO_DOWNLOAD_ASK: {
                            updateInfoBar(0, R.string.download_latest_assets_checking_description,
                                    R.drawable.ic_file_download_white_32dp, false);
                            showDownloadDialog();
                            break;
                        }
                    }
                    return;
                }
                case AppCast.DOWNLOAD_STATUS.ACTION: {
                    int percent = intent.getIntExtra(AppCast.DOWNLOAD_STATUS.EXTRA_PERCENT, 100);
                    switch (intent.getIntExtra(AppCast.DOWNLOAD_STATUS.EXTRA_STATUS,
                            AssetUpdater.UpdateThread.DOWNLOAD_STATUS__FAILED)) {
                        case AssetUpdater.UpdateThread.DOWNLOAD_STATUS__DOWNLOADING: {
                            updateInfoBar(percent, R.string.downloading, R.drawable.ic_file_download_white_32dp, true);
                            break;
                        }
                        case AssetUpdater.UpdateThread.DOWNLOAD_STATUS__FAILED: {
                            updateInfoBar(percent, R.string.downloading_failed, R.drawable.ic_mood_bad_black_256dp,
                                    false);
                            break;
                        }
                        case AssetUpdater.UpdateThread.DOWNLOAD_STATUS__UNZIPPING: {
                            updateInfoBar(percent, R.string.unzipping, R.drawable.ic_file_download_white_32dp, true);
                            break;
                        }
                        case AssetUpdater.UpdateThread.DOWNLOAD_STATUS__FINISHED: {
                            updateInfoBar(percent, R.string.successfully_downloaded, R.drawable.ic_gavel_white_48px,
                                    false);
                            break;
                        }
                    }
                    return;
                }
                case AppCast.ASSETS_LOADED.ACTION: {
                    selectTab(tabLayout.getSelectedTabPosition(), currentMainMode);
                    updateHiddenNavOption();
                    break;
                }
            }
        }
    };

    private void showDownloadDialog() {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.download_latest_assets)
                .setMessage(R.string.download_latest_assets_message__appspecific)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.yes,
                        (dialogInterface, i) -> new AssetUpdater.UpdateThread(MainActivity.this, true).start());
        dialog.show();
    }

    public void updateInfoBar(Integer percent, @StringRes Integer textResId, @DrawableRes Integer image,
            final boolean showlong) {
        lastInfoBarTextShownAt = System.currentTimeMillis();
        infoBar.setVisibility(View.VISIBLE);
        Handler handler = new Handler();
        handler.postDelayed(() -> {
            if ((System.currentTimeMillis() - lastInfoBarTextShownAt) > (showlong ? 20 : 2) * 1000) {
                infoBar.setVisibility(View.GONE);
            }
        }, (showlong ? 20 : 2) * 1000 + 100);
        if (percent != null) {
            infoBarProgressBar.setProgress(percent);
        }
        if (textResId != null) {
            infoBarText.setText(textResId);
        }
        if (image != null) {
            infoBarImage.setImageResource(image);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.main__menu, menu);
        updateSearchFilter("");
        boolean isCreateTab = bottomNav.getSelectedItemId() == R.id.nav_mode_create;
        menu.findItem(R.id.action_picture_from_camera).setVisible(isCreateTab);
        menu.findItem(R.id.action_picture_from_gallery).setVisible(isCreateTab);
        menu.findItem(R.id.action_search_meme).setVisible(isCreateTab);

        searchItem = menu.findItem(R.id.action_search_meme);
        searchView = (SearchView) searchItem.getActionView();

        SearchManager searchManager = (SearchManager) getSystemService(SEARCH_SERVICE);
        if (searchManager != null) {
            searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        }
        searchView.setQueryHint(getString(R.string.search_meme__appspecific));
        if (searchView != null) {
            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    if (query != null) {
                        updateSearchFilter(query);
                    }
                    return false;
                }

                @Override
                public boolean onQueryTextChange(String newText) {
                    if (newText != null) {
                        updateSearchFilter(newText);
                    }
                    return false;
                }
            });
            searchView.setOnQueryTextFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus) {
                    searchItem.collapseActionView();
                    updateSearchFilter("");
                }
            });
        }
        return true;
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        invalidateOptionsMenu();
        return handleBarClick(item);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return handleBarClick(item);
    }

    @Override
    public void onPageScrolled(int i, float v, int i1) {
    }

    @Override
    public void onPageSelected(int i) {
        app.settings.setLastSelectedTab(i);
    }

    @Override
    public void onPageScrollStateChanged(int i) {
    }

    public void selectCreateMainMode() {
        MenuItem createItem = bottomNav.getMenu().findItem(R.id.nav_mode_create);
        onNavigationItemSelected(createItem);
        createItem.setChecked(true);
    }

    public void recreateFragmentsAfterUnhiding() {
        viewPager.getAdapter().notifyDataSetChanged();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt(BOTTOM_NAV_POSITION, currentMainMode);
        super.onSaveInstanceState(outState);
    }
}