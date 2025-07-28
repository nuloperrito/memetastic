package net.gsantner.memetastic.activity;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.view.WindowManager;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.viewpager.widget.ViewPager;

import net.gsantner.memetastic.App;
import net.gsantner.memetastic.data.MemeData;
import net.gsantner.memetastic.service.AssetUpdater;
import net.gsantner.memetastic.util.AppSettings;
import net.gsantner.memetastic.util.PermissionChecker;

import java.io.File;
import java.util.List;

import io.github.gsantner.memetastic.R;
import io.github.gsantner.memetastic.databinding.ImageviewActivityBinding;

public class ImageViewActivity extends AppCompatActivity {
    //########################
    //## View Binding
    //########################
    private ImageviewActivityBinding binding;
    private ViewPager viewPager;
    private Toolbar toolbar;

    //#####################
    //## Members
    //#####################
    private File imageFile;
    private Bitmap bitmap = null;
    List<MemeData.Image> imageList = null;

    //#####################
    //## Methods
    //#####################
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ImageviewActivityBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewPager = binding.imageviewActivityViewPager;
        toolbar = binding.toolbar;

        if (PermissionChecker.hasExtStoragePerm(this)) {
            File folder = AssetUpdater.getMemesDir(AppSettings.get());
            folder.mkdirs();
            imageList = MemeData.getCreatedMemes();
        }

        if (AppSettings.get().isOverviewStatusBarHidden()) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }

        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            ActionBar ab = getSupportActionBar();
            ab.setDisplayShowTitleEnabled(false);
            ab.setDisplayHomeAsUpEnabled(true);
        }

        viewPager.setAdapter(new ImagePagerAdapter(getSupportFragmentManager()));
        viewPager.setCurrentItem(getIntent().getIntExtra(MainActivity.IMAGE_POS, 0));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.BLACK);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.imageview__menu, menu);
        // menu.findItem(R.id.action_delete).setVisible(imageFile != null);
        return true;
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null; // Clean ViewBinding reference
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        ImageViewFragment page = null;

        if (item.getItemId() == R.id.action_share || item.getItemId() == R.id.action_delete) {
            page = ((ImageViewFragment) viewPager.getAdapter().instantiateItem(viewPager, viewPager.getCurrentItem()));
        }

        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;

            case R.id.action_share:
                if (page != null) {
                    bitmap = page.getBitmap();
                    ((App) getApplication()).shareBitmapToOtherApp(bitmap, this);
                }
                return true;

            case R.id.action_delete:
                if (page != null) {
                    imageFile = page.getImageFile();
                }

                if (imageFile != null) {
                    deleteFile(imageFile);
                    deleteFile(new File(getCacheDir(), imageFile.getAbsolutePath().substring(1)));
                    MemeData.Image memeData = MemeData.findImage(imageFile);
                    if (memeData != null) {
                        MemeData.getCreatedMemes().remove(memeData);
                    }
                }
                viewPager.getAdapter().notifyDataSetChanged();
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private boolean deleteFile(File file) {
        return file.exists() && file.delete();
    }

    class ImagePagerAdapter extends FragmentStatePagerAdapter {
        public ImagePagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int i) {
            return ImageViewFragment.newInstance(i, imageList.get(i).fullPath.getAbsolutePath());
        }

        @Override
        public int getCount() {
            return imageList.size();
        }
    }
}