package net.gsantner.memetastic.activity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import net.gsantner.memetastic.service.AssetUpdater;
import net.gsantner.memetastic.service.ThumbnailCleanupTask;
import net.gsantner.memetastic.util.AppSettings;
import net.gsantner.memetastic.util.PermissionChecker;
import net.gsantner.opoc.preference.GsPreferenceFragmentCompat;
import net.gsantner.opoc.preference.SharedPreferencesPropertyBackend;

import java.util.Date;

import io.github.gsantner.memetastic.R;
import io.github.gsantner.memetastic.databinding.SettingsActivityBinding;

public class SettingsActivity extends AppCompatActivity {
    static final int ACTIVITY_ID = 10;

    static class RESULT {
        static final int NOCHANGE = -1;
        static final int CHANGE = 1;
        static final int CHANGE_RESTART = 2;
    }

    private SettingsActivityBinding binding;
    private AppSettings appSettings;
    public static int activityRetVal = RESULT.NOCHANGE;

    public void onCreate(Bundle b) {
        super.onCreate(b);

        binding = SettingsActivityBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        appSettings = AppSettings.get();
        activityRetVal = RESULT.NOCHANGE;

        binding.settingsToolbar.setTitle(R.string.settings);
        setSupportActionBar(binding.settingsToolbar);

        // Set back pressing button
        binding.settingsToolbar.setNavigationIcon(getResources().getDrawable(R.drawable.ic_arrow_back_white_48px));
        binding.settingsToolbar.setNavigationOnClickListener(v -> onBackPressed());

        showFragment(SettingsFragmentMaster.TAG, false);
    }

    @Override
    public void onBackPressed() {
        GsPreferenceFragmentCompat prefFrag = (GsPreferenceFragmentCompat) getSupportFragmentManager().findFragmentByTag(SettingsFragmentMaster.TAG);
        if (prefFrag != null && prefFrag.canGoBack()) {
            prefFrag.goBack();
            return;
        }
        super.onBackPressed();
    }

    protected void showFragment(String tag, boolean addToBackStack) {
        GsPreferenceFragmentCompat fragment = (GsPreferenceFragmentCompat) getSupportFragmentManager().findFragmentByTag(tag);
        if (fragment == null) {
            switch (tag) {
                case SettingsFragmentMaster.TAG:
                default:
                    fragment = new SettingsFragmentMaster();
                    binding.settingsToolbar.setTitle(R.string.settings);
                    break;
            }
        }
        FragmentTransaction t = getSupportFragmentManager().beginTransaction();
        if (addToBackStack) {
            t.addToBackStack(tag);
        }
        t.replace(R.id.settings__fragment_container, fragment, tag).commit();
    }

    @Override
    protected void onStop() {
        setResult(activityRetVal);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }

    public static class SettingsFragmentMaster extends GsPreferenceFragmentCompat {
        public static final String TAG = "SettingsFragmentMaster";

        @Override
        protected void onPreferenceChanged(SharedPreferences prefs, String key) {
            if (activityRetVal == RESULT.NOCHANGE) {
                activityRetVal = RESULT.CHANGE;
            }
        }

        @Override
        public int getPreferenceResourceForInflation() {
            return R.xml.preferences_master;
        }

        @Override
        public String getFragmentTag() {
            return TAG;
        }

        @Override
        protected SharedPreferencesPropertyBackend getAppSettings(Context context) {
            return new AppSettings(context);
        }

        @SuppressLint("ApplySharedPref")
        @Override
        public Boolean onPreferenceClicked(Preference preference, String key, int keyResId) {
            if (isAdded() && preference.hasKey()) {
                Context context = getActivity();
                AppSettings settings = AppSettings.get();

                if (eq(key, R.string.pref_key__memelist_view_type)) {
                    activityRetVal = RESULT.CHANGE_RESTART;
                }
                if (eq(key, R.string.pref_key__cleanup_thumbnails)) {
                    new ThumbnailCleanupTask(context).start();
                    return true;
                }
                if (eq(key, R.string.pref_key__is_overview_statusbar_hidden)) {
                    activityRetVal = RESULT.CHANGE_RESTART;
                }
                if (eq(key, R.string.pref_key__language)) {
                    activityRetVal = RESULT.CHANGE_RESTART;
                }
                if (eq(key, R.string.pref_key__download_assets_try)) {
                    if (PermissionChecker.doIfPermissionGranted(getActivity())) {
                        Date zero = new Date(0);
                        settings.setLastArchiveCheckDate(zero);
                        settings.setLastArchiveDate(zero);
                        settings.getDefaultPreferences().edit().commit();
                        new AssetUpdater.UpdateThread(context, true).start();
                        getActivity().finish();
                    }
                }
            }
            return null;
        }

        @Override
        protected void onPreferenceScreenChanged(PreferenceFragmentCompat preferenceFragmentCompat, PreferenceScreen preferenceScreen) {
            super.onPreferenceScreenChanged(preferenceFragmentCompat, preferenceScreen);
            if (!TextUtils.isEmpty(preferenceScreen.getTitle())) {
                SettingsActivity activity = (SettingsActivity) getActivity();
                if (activity != null) {
                    activity.binding.settingsToolbar.setTitle(preferenceScreen.getTitle());
                }
            }
        }

        @Override
        public synchronized void doUpdatePreferences() {
            super.doUpdatePreferences();
            setPreferenceVisible(R.string.pref_key__download_assets_try, false);
        }
    }
}