package net.gsantner.memetastic.util;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import io.github.gsantner.memetastic.R;

public class PermissionChecker {

    // Constants for Request Codes
    private static final int RC_STORAGE_PERM = 123;
    private static final int RC_MANAGE_STORAGE = 124;

    // Check whether you have storage perms
    public static boolean hasExtStoragePerm(Context context) {
        // Android 11+ needs special perms
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        // Otherwise traditional perms are enough
        return ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    // Request storage perms
    public static boolean doIfPermissionGranted(final Activity activity) {
        if (hasExtStoragePerm(activity)) {
            return true;
        }

        // Android 11+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + activity.getPackageName()));
                activity.startActivityForResult(intent, RC_MANAGE_STORAGE);
            } catch (Exception e) {
                // Alternative method
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                activity.startActivityForResult(intent, RC_MANAGE_STORAGE);
            }
        }
        // Android 10 or minor
        else {
            ActivityCompat.requestPermissions(
                    activity,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    RC_STORAGE_PERM
            );
        }
        return false;
    }

    // Handle perm request results
    public static boolean checkPermissionResult(final Activity activity, int requestCode,
                                                @NonNull String[] permissions, @NonNull int[] grantResults) {
        // Handling traditional storage perm requests
        if (requestCode == RC_STORAGE_PERM) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                return true;
            }
        }
        // Handling storage management requests on Android 11+
        else if (requestCode == RC_MANAGE_STORAGE) {
            if (hasExtStoragePerm(activity)) {
                return true;
            }
        }

        // Error case
        ActivityUtils.get(activity).showSnackBar(R.string.error_storage_permission__appspecific, true);
        return false;
    }
}