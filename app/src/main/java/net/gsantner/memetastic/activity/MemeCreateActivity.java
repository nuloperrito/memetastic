package net.gsantner.memetastic.activity;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.jaredrummler.android.colorpicker.ColorPanelView;
import com.jaredrummler.android.colorpicker.ColorPickerDialog;
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener;

import net.gsantner.memetastic.App;
import net.gsantner.memetastic.data.MemeConfig;
import net.gsantner.memetastic.data.MemeData;
import net.gsantner.memetastic.data.MemeEditorElements;
import net.gsantner.memetastic.data.MemeLibConfig;
import net.gsantner.memetastic.service.AssetUpdater;
import net.gsantner.memetastic.ui.FontItemAdapter;
import net.gsantner.memetastic.util.ActivityUtils;
import net.gsantner.memetastic.util.AppCast;
import net.gsantner.memetastic.util.AppSettings;
import net.gsantner.memetastic.util.ContextUtils;
import net.gsantner.memetastic.util.PermissionChecker;
import net.gsantner.opoc.ui.TouchImageView;
import net.gsantner.opoc.util.ShareUtil;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.Locale;

import io.github.gsantner.memetastic.R;
import io.github.gsantner.memetastic.databinding.MemecreateActivityBinding;
import io.github.gsantner.memetastic.databinding.UiMemecreateTextSettingsBinding;
import other.so.AndroidBug5497Workaround;

/**
 * Activity for creating memes
 */
@SuppressWarnings("ResultOfMethodCallIgnored")
public class MemeCreateActivity extends AppCompatActivity implements ColorPickerDialogListener {
    //########################
    //## Static
    //########################
    public final static int RESULT_MEME_EDITING_FINISHED = 150;
    public final static String EXTRA_IMAGE_PATH = "MemeCreateActivity_EXTRA_IMAGE_PATH";
    public final static String EXTRA_MEMETASTIC_DATA = "MemeCreateActivity_EXTRA_MEMETASTIC_DATA";
    private static final String TAG = MemeCreateActivity.class.getSimpleName();

    // View Binding
    private MemecreateActivityBinding binding;
    private FloatingActionButton fab;
    private Toolbar toolbar;
    private ImageView imageEditView;
    private LinearLayout editBar;
    private EditText createCaption;
    private ColorPanelView paddingColor;
    private TouchImageView fullscreenImageView;

    //#####################
    //## Members
    //#####################
    private static boolean doubleBackToExitPressedOnce = false;
    private Bitmap lastBitmap = null;
    private long memeSavetime = -1;
    private File predefinedTargetFile = null;
    private App app;
    private MemeEditorElements memeEditorElements;
    private Bundle savedInstanceStateBundle = null;
    boolean bottomContainerVisible = false;
    private boolean isBottom;
    private View dialogView;
    private boolean savedAsMemeTemplate = false;

    @SuppressWarnings({"unchecked", "ConstantConditions"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = MemecreateActivityBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        fab = binding.fab;
        toolbar = binding.toolbar;
        imageEditView = binding.memecreateActivityImage;
        editBar = binding.editBar;
        createCaption = binding.createCaption;
        paddingColor = binding.memecreateMoarControlsColorPickerForPadding;
        fullscreenImageView = binding.memecreateActivityFullscreenImage;

        if (AppSettings.get().isEditorStatusBarHidden()) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }

        if (AppSettings.get().isEditorStatusBarHidden()) {
            AndroidBug5497Workaround.assistActivity(this);
        }

        // Quit activity if no conf was given
        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();
        if (!(Intent.ACTION_SEND.equals(action) && type.startsWith("image/")) &&
                (!getIntent().hasExtra(EXTRA_IMAGE_PATH)) && !(Intent.ACTION_EDIT.equals(action) && type.startsWith("image/"))) {
            finish();
            return;
        }

        // Stop if data is not loaded yet (Try load in onResume, recreate activity in broadcast)
        if (MemeData.isReady()) {
            app = (App) getApplication();

            // Set toolbar
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }
            if (!initMemeSettings(savedInstanceState)) {
                return;
            }
            initMoarControlsContainer();
            initCaptionButtons();

            binding.settingsCaption.setOnClickListener(v -> openSettingsDialog());
            binding.doneCaption.setOnClickListener(v -> settingsDone());
            binding.memecreateMoarControlsLayout.setOnClickListener(v -> onBottomContainerClicked());
            binding.memecreateActivityFullscreenImage.setOnClickListener(v -> onFullScreenImageClicked());
            binding.memecreateActivityFullscreenImage.setOnLongClickListener(v -> onFullScreenImageLongClicked());
            createCaption.addTextChangedListener(new TextWatcherAdapter() {
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    onCaptionChanged(s);
                }
            });
            imageEditView.setOnTouchListener((v, event) -> onImageTouched(v, event));
        }
        if (savedInstanceState != null
                && savedInstanceState.containsKey("captionPosition")
                && savedInstanceState.containsKey("captionEditBar")
                && savedInstanceState.containsKey("captionText")) {
            isBottom = savedInstanceState.getBoolean("captionPosition");
            editBar.setVisibility(savedInstanceState.getBoolean("captionEditBar") ?
                    View.VISIBLE : View.GONE);
            createCaption.setText(savedInstanceState.getString("captionText"));
        }
        try {
            if (!ActivityUtils.get(this).isInSplitScreenMode()) {
                imageEditView.postDelayed(this::touchTopElement, 40);
            }
        } catch (Exception ignored) {
        }
    }

    private void initCaptionButtons() {
        ImageButton buttonTextSettings = binding.settingsCaption;
        ImageButton buttonOk = binding.doneCaption;
        buttonTextSettings.setColorFilter(ContextCompat.getColor(this, R.color.black));
        buttonOk.setColorFilter(ContextCompat.getColor(this, R.color.black));
    }

    public boolean initMemeSettings(Bundle savedInstanceState) {
        MemeData.Font lastUsedFont = getFont(app.settings.getLastUsedFont());
        Bitmap bitmap = extractBitmapFromIntent(getIntent());
        if (bitmap == null) {
            finish();
            return false;
        }
        if (savedInstanceState != null && savedInstanceState.containsKey("memeObj")) {
            memeEditorElements = (MemeEditorElements) savedInstanceState.getSerializable("memeObj");
            if (memeEditorElements == null) {
                memeEditorElements = new MemeEditorElements(lastUsedFont, bitmap);
            }
            memeEditorElements.getImageMain().setImage(bitmap);
            memeEditorElements.setFontToAll(lastUsedFont);
        } else {
            memeEditorElements = new MemeEditorElements(lastUsedFont, bitmap);
        }
        memeEditorElements.getImageMain().setDisplayImage(memeEditorElements.getImageMain().getImage().copy(Bitmap.Config.RGB_565, false));
        onMemeEditorObjectChanged();
        return true;
    }

    public MemeData.Font getFont(String filepath) {
        MemeData.Font font = MemeData.findFont(new File(filepath));
        if (font == null) {
            font = MemeData.getFonts().get(0);
        }
        return font;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        prepareForSaving();
        outState.putSerializable("memeObj", memeEditorElements);
        outState.putBoolean("captionPosition", isBottom);
        outState.putBoolean("captionEditBar", editBar != null && editBar.getVisibility() == View.VISIBLE);
        outState.putString("captionText", createCaption != null ? createCaption.getText().toString() : "");
        this.savedInstanceStateBundle = outState;
    }

    private void prepareForSaving() {
        if (memeEditorElements == null) {
            return;
        }
        imageEditView.setImageBitmap(null);
        if (lastBitmap != null && !lastBitmap.isRecycled())
            lastBitmap.recycle();
        MemeEditorElements.EditorImage imageMain = memeEditorElements.getImageMain();
        if (imageMain.getImage() != null && !imageMain.getImage().isRecycled())
            imageMain.getImage().recycle();
        if (imageMain.getDisplayImage() != null && !imageMain.getDisplayImage().isRecycled())
            imageMain.getDisplayImage().recycle();
        lastBitmap = null;
        imageMain.setDisplayImage(null);
        imageMain.setImage(null);
        memeEditorElements.setFontToAll(null);
    }

    @Override
    protected void onDestroy() {
        prepareForSaving();
        super.onDestroy();
        binding = null;
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            // Checking if registered may not work always, therefore try to force it
            LocalBroadcastManager.getInstance(this).unregisterReceiver(localBroadcastReceiver);
        } catch (IllegalArgumentException ignored) {
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!MemeData.isReady()) {
            LocalBroadcastManager.getInstance(this).registerReceiver(localBroadcastReceiver, AppCast.getLocalBroadcastFilter());
            new AssetUpdater.LoadAssetsThread(this).start();
            return;
        }

        if (savedInstanceStateBundle != null) {
            overridePendingTransition(R.anim.fadein, R.anim.fadeout);
            if (!initMemeSettings(savedInstanceStateBundle)) {
                return;
            }
        }
    }

    private final BroadcastReceiver localBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (AppCast.ASSETS_LOADED.ACTION.equals(action)) {
                recreate();
            }
        }
    };

    private Bitmap extractBitmapFromIntent(final Intent intent) {
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        Bitmap bitmap = null;
        if (intent.getAction() != null && intent.getAction().equals(Intent.ACTION_SEND) && intent.getType().startsWith("image/")) {
            Uri imageURI = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (imageURI != null) {
                try {
                    bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageURI);
                } catch (IOException e) {
                    bitmap = null;
                    e.printStackTrace();
                }
            }
        } else if (intent.getAction() != null && intent.getAction().equals(Intent.ACTION_EDIT) && intent.getType().startsWith("image/")) {
            ShareUtil shu = new ShareUtil(this);
            predefinedTargetFile = shu.extractFileFromIntent(intent);
            if (predefinedTargetFile == null) {
                Toast.makeText(this, R.string.the_file_could_not_be_loaded, Toast.LENGTH_SHORT).show();
                finish();
            }
            bitmap = ContextUtils.get().loadImageFromFilesystem(predefinedTargetFile, app.settings.getRenderQualityReal());
        } else {
            String imagePath = getIntent().getStringExtra(EXTRA_IMAGE_PATH);
            bitmap = ContextUtils.get().loadImageFromFilesystem(new File(imagePath), app.settings.getRenderQualityReal());
        }
        return bitmap;
    }

    // Text settings dialog
    public void openSettingsDialog() {
        ActivityUtils.get(this).hideSoftKeyboard();
        UiMemecreateTextSettingsBinding dialogBinding = UiMemecreateTextSettingsBinding.inflate(getLayoutInflater());
        dialogView = dialogBinding.getRoot();

        initTextSettingsPopupDialog(dialogBinding);

        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(R.string.settings)
                .setView(dialogView)
                .setPositiveButton(android.R.string.ok, (dialogInterface, i) -> {
                    // Return focus to createCaption
                    createCaption.requestFocus();
                })
                .setOnDismissListener((di) -> {
                    toolbar.setVisibility(View.VISIBLE);
                    imageEditView.setPadding(0, 0, 0, 0);
                })
                .create();

        // Get some more space
        try {
            toolbar.setVisibility(View.GONE);
            WindowManager.LayoutParams wmlp = dialog.getWindow().getAttributes();
            wmlp.gravity = Gravity.TOP;
            android.graphics.Point p = new android.graphics.Point();
            getWindowManager().getDefaultDisplay().getSize(p);
            imageEditView.setPadding(0, p.y / 2, 0, 0);
        } catch (Exception ignored) {
        }
        dialog.show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.creatememe__menu, menu);
        ContextUtils cu = new ContextUtils(getApplicationContext());
        cu.tintMenuItems(menu, true, Color.WHITE);
        cu.setSubMenuIconsVisiblity(menu, true);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_share: {
                recreateImage(true);
                app.shareBitmapToOtherApp(lastBitmap, this);
                return true;
            }
            case R.id.action_save: {
                recreateImage(true);
                saveMemeToFilesystem(true);
                return true;
            }
            case R.id.action_save_as_template: {
                if (!savedAsMemeTemplate) {
                    File folder = AssetUpdater.getCustomAssetsDir(AppSettings.get());
                    String filename = String.format(Locale.getDefault(), "%s_%s.jpg", getString(R.string.app_name), AssetUpdater.FORMAT_MINUTE_FILE.format(new Date(memeSavetime)));
                    File fullpath = new File(folder, filename);
                    folder.mkdirs();
                    savedAsMemeTemplate = ContextUtils.get().writeImageToFile(fullpath, memeEditorElements.getImageMain().getDisplayImage());
                }
                return true;
            }
            case R.id.action_appearance: {
                toggleMoarControls(false, false);
                ActivityUtils.get(this).hideSoftKeyboard();
                View focusedView = this.getCurrentFocus();
                if (focusedView != null) {
                    ActivityUtils.get(this).hideSoftKeyboard();
                }
                return true;
            }
            case R.id.action_show_original_image: {
                fullscreenImageView.setImageBitmap(memeEditorElements.getImageMain().getDisplayImage());
                fullscreenImageView.setVisibility(View.VISIBLE);
                toggleMoarControls(true, true);
                return true;
            }
            case R.id.action_show_edited_image: {
                recreateImage(true);
                fullscreenImageView.setImageBitmap(lastBitmap);
                fullscreenImageView.setVisibility(View.VISIBLE);
                toggleMoarControls(true, true);
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    private boolean saveMemeToFilesystem(boolean showDialog) {
        if (!PermissionChecker.doIfPermissionGranted(this)) {
            return false;
        }

        File folder = AssetUpdater.getMemesDir(AppSettings.get());
        if (memeSavetime < 0) {
            memeSavetime = System.currentTimeMillis();
        }

        String filename = String.format(Locale.getDefault(), "%s_%s.jpg", getString(R.string.app_name), AssetUpdater.FORMAT_MINUTE_FILE.format(new Date(memeSavetime)));
        File fullpath = predefinedTargetFile != null ? predefinedTargetFile : new File(folder, filename);
        boolean wasSaved = ContextUtils.get().writeImageToFile(fullpath, lastBitmap.copy(lastBitmap.getConfig(), false));
        if (wasSaved && showDialog) {

            AlertDialog.Builder dialog = new AlertDialog.Builder(this);
            dialog.setTitle(R.string.successfully_saved)
                    .setMessage(R.string.saved_meme_successfully__appspecific)
                    .setNegativeButton(R.string.keep_editing, null)
                    .setNeutralButton(R.string.share_meme__appspecific, (dialogInterface, i) ->
                            app.shareBitmapToOtherApp(lastBitmap, MemeCreateActivity.this)
                    )
                    .setPositiveButton(R.string.close, (dialog1, which) -> finish());
            dialog.show();
        }
        if (wasSaved) {
            MemeConfig.Image confImage = AssetUpdater.generateImageEntry(folder, filename, new String[0]);
            MemeData.Image dataImage = new MemeData.Image();
            dataImage.conf = confImage;
            dataImage.fullPath = fullpath;
            dataImage.isTemplate = false;
            MemeData.getCreatedMemes().add(dataImage);
        }
        return wasSaved;
    }

    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        PermissionChecker.checkPermissionResult(this, requestCode, permissions, grantResults);
    }

    public void toggleMoarControls(boolean forceVisibile, boolean visible) {
        bottomContainerVisible = !bottomContainerVisible;
        if (forceVisibile) {
            bottomContainerVisible = visible;
        }

        createCaption.setVisibility(bottomContainerVisible ? View.GONE : View.VISIBLE);
        toolbar.setVisibility(bottomContainerVisible ? View.GONE : View.VISIBLE);

        // higher weightRatio means the conf is more wide, so below dialogView can be higher
        // 100 is the max weight, 55 means the below dialogView is a little more weighted
        Bitmap curImg = memeEditorElements.getImageMain().getDisplayImage();
        int weight = (int) (55f * (1 + ((curImg.getWidth() / (float) curImg.getHeight()) / 10f)));
        weight = weight > 100 ? 100 : weight;

        // Set weights. If bottomContainerVisible == false -> Hide them = 0 weight
        View container = binding.memecreateActivityImageContainer;
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) container.getLayoutParams();
        lp.height = 0;
        lp.weight = bottomContainerVisible ? 100 - weight : 100;
        container.setLayoutParams(lp);
        container = binding.memecreateActivityMoarControlsContainer;
        container.setVisibility(bottomContainerVisible ? View.VISIBLE : View.GONE);
        lp = (LinearLayout.LayoutParams) container.getLayoutParams();
        lp.height = 0;
        lp.weight = bottomContainerVisible ? weight : 0;
        container.setLayoutParams(lp);
    }

    private void initTextSettingsPopupDialog(UiMemecreateTextSettingsBinding binding) {
        SeekBar textSize = binding.memeDialogSeekFontSize;
        View textBackGroundColor = binding.memeDialogColorPickerForText;
        View textBorderColor = binding.memeDialogColorPickerForBorder;
        Switch allCapsSwitch = binding.memeDialogToggleAllCaps;
        Spinner fontDropDown = binding.memeDialogDropdownFont;

        FontItemAdapter fontAdapter = new FontItemAdapter(this,
                android.R.layout.simple_list_item_1, MemeData.getFonts(),
                false, getString(R.string.font));
        fontDropDown.setAdapter(fontAdapter);
        fontAdapter.setSelectedFont(fontDropDown, memeEditorElements.getCaptionTop().getFont());

        textBackGroundColor.setBackgroundColor(memeEditorElements.getCaptionTop().getTextColor());
        textBorderColor.setBackgroundColor(memeEditorElements.getCaptionTop().getBorderColor());

        allCapsSwitch.setChecked(memeEditorElements.getCaptionTop().isAllCaps());
        textSize.setProgress(memeEditorElements.getCaptionTop().getFontSize() - MemeLibConfig.FONT_SIZES.MIN);

        // listeners
        View.OnClickListener colorListeners = view1 -> {
            switch (view1.getId()) {
                case R.id.meme_dialog__color_picker_for_text:
                    if (isBottom) {
                        showColorDialog(view1.getId(), memeEditorElements.getCaptionBottom().getTextColor());
                    } else {
                        showColorDialog(view1.getId(), memeEditorElements.getCaptionTop().getTextColor());
                    }
                    break;
                case R.id.meme_dialog__color_picker_for_border:
                    if (isBottom) {
                        showColorDialog(view1.getId(), memeEditorElements.getCaptionBottom().getBorderColor());
                    } else {
                        showColorDialog(view1.getId(), memeEditorElements.getCaptionTop().getBorderColor());
                    }
            }
        };

        textBackGroundColor.setOnClickListener(colorListeners);
        textBorderColor.setOnClickListener(colorListeners);

        // drop down
        fontDropDown.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (isBottom) {
                    memeEditorElements.getCaptionBottom().setFont((MemeData.Font)
                            parent.getSelectedItem());
                }
                if (!isBottom || memeEditorElements.getImageMain().isTextSettingsGlobal()) {
                    memeEditorElements.getCaptionTop().setFont((MemeData.Font)
                            parent.getSelectedItem());
                }
                app.settings.setLastUsedFont(((MemeData.Font) parent.getSelectedItem()).fullPath.getAbsolutePath());
                onMemeEditorObjectChanged();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        // seekBar
        textSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (isBottom) {
                    memeEditorElements.getCaptionBottom().setFontSize(progress + MemeLibConfig.FONT_SIZES.MIN);
                }

                if (!isBottom || memeEditorElements.getImageMain().isTextSettingsGlobal()) {
                    memeEditorElements.getCaptionTop().setFontSize(progress + MemeLibConfig.FONT_SIZES.MIN);
                }
                onMemeEditorObjectChanged();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        // switch
        allCapsSwitch.setOnCheckedChangeListener((compoundButton, isChecked) -> {
            if (isBottom) {
                memeEditorElements.getCaptionBottom().setAllCaps(isChecked);
            }
            if (!isBottom || memeEditorElements.getImageMain().isTextSettingsGlobal()) {
                memeEditorElements.getCaptionTop().setAllCaps(isChecked);
            }
            onMemeEditorObjectChanged();
        });
    }

    private void initMoarControlsContainer() {
        final Button rotateButton = binding.memecreateMoarControlsRotatePlus90deg;
        final SeekBar seekPaddingSize = binding.memecreateMoarControlsSeekPaddingSize;
        final ColorPanelView colorPickerPadding = binding.memecreateMoarControlsColorPickerForPadding;
        final CheckBox globalTextSettingsCheckbox = binding.memecreateMoarControlsGlobalTextSettings;

        // Apply existing settings
        colorPickerPadding.setColor(memeEditorElements.getImageMain().getPaddingColor());
        seekPaddingSize.setProgress(memeEditorElements.getImageMain().getPadding());
        globalTextSettingsCheckbox.setChecked(memeEditorElements.getImageMain().isTextSettingsGlobal());

        // Add bottom sheet listeners
        View.OnClickListener colorListener = v -> {
            showColorDialog(R.id.memecreate__moar_controls__color_picker_for_padding, memeEditorElements.getImageMain().getPaddingColor());
            onMemeEditorObjectChanged();
        };
        globalTextSettingsCheckbox.setOnCheckedChangeListener((buttonView, isChecked) ->
                memeEditorElements.getImageMain().setTextSettingsGlobal(isChecked)
        );
        colorPickerPadding.setOnClickListener(colorListener);

        seekPaddingSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                memeEditorElements.getImageMain().setPadding(progress);
                onMemeEditorObjectChanged();
            }
        });
        rotateButton.setOnClickListener(v -> {
            memeEditorElements.getImageMain().setRotationDeg((memeEditorElements.getImageMain().getRotationDeg() + 90) % 360);
            onMemeEditorObjectChanged();
        });
    }

    private void showColorDialog(int id, @ColorInt int color) {
        ColorPickerDialog.newBuilder()
                .setDialogId(id)
                .setColor(color)
                .setPresets(MemeLibConfig.MEME_COLORS.ALL)
                .setCustomButtonText(R.string.palette)
                .setPresetsButtonText(R.string.presets)
                .setDialogTitle(R.string.select_color)
                .setSelectedButtonText(android.R.string.ok)
                .show(this);
    }

    public void onFullScreenImageClicked() {
        fullscreenImageView.setVisibility(View.INVISIBLE);
        recreateImage(false);
        toggleMoarControls(true, false);
    }

    public boolean onFullScreenImageLongClicked() {
        fullscreenImageView.setRotation((fullscreenImageView.getRotation() + 90) % 360);
        return true;
    }

    @Override
    public void onColorSelected(int id, @ColorInt int colorInt) {
        switch (id) {
            case R.id.meme_dialog__color_picker_for_border: // border color
                if (isBottom) {
                    memeEditorElements.getCaptionBottom().setBorderColor(colorInt);
                }
                if (!isBottom || memeEditorElements.getImageMain().isTextSettingsGlobal()) {
                    memeEditorElements.getCaptionTop().setBorderColor(colorInt);
                }
                dialogView.findViewById(R.id.meme_dialog__color_picker_for_border).setBackgroundColor(colorInt);
                break;
            case R.id.meme_dialog__color_picker_for_text: // text background color
                if (isBottom) {
                    memeEditorElements.getCaptionBottom().setTextColor(colorInt);
                }
                if (!isBottom || memeEditorElements.getImageMain().isTextSettingsGlobal()) {
                    memeEditorElements.getCaptionTop().setTextColor(colorInt);
                }
                dialogView.findViewById(R.id.meme_dialog__color_picker_for_text).setBackgroundColor(colorInt);
                break;
            case R.id.memecreate__moar_controls__color_picker_for_padding: // padding color
                memeEditorElements.getImageMain().setPaddingColor(colorInt);
                memeEditorElements.getImageMain().setPaddingColor(colorInt);
                paddingColor.setColor(colorInt);
                break;
            default:
                Log.i(TAG, "Wrong selection");
                break;
        }
        onMemeEditorObjectChanged();
    }

    @Override
    public void onDialogDismissed(int id) {
    }

    public Bitmap renderMemeImageFromElements(Context c, MemeEditorElements memeEditorElements) {
        // prepare canvas
        Bitmap bitmap = memeEditorElements.getImageMain().getDisplayImage();

        if (memeEditorElements.getImageMain().getRotationDeg() != 0) {
            Matrix matrix = new Matrix();
            matrix.postRotate(memeEditorElements.getImageMain().getRotationDeg());
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        }

        double pad = 1 + memeEditorElements.getImageMain().getPadding() / 100.0;
        if (pad > 1.01) {
            Bitmap workBmp = Bitmap.createBitmap((int) (bitmap.getWidth() * pad), (int) (bitmap.getHeight() * pad), Bitmap.Config.ARGB_8888);
            Canvas can = new Canvas(workBmp);
            can.drawColor(memeEditorElements.getImageMain().getPaddingColor());
            can.drawBitmap(bitmap, (int) ((workBmp.getWidth() - bitmap.getWidth()) / 2.0), (int) ((workBmp.getHeight() - bitmap.getHeight()) / 2.0), null);
            bitmap = workBmp;
        }

        float scale = ContextUtils.get().getScalingFactorInPixelsForWritingOnPicture(bitmap.getWidth(), bitmap.getHeight());
        float borderScale = scale * memeEditorElements.getCaptionTop().getFontSize() / MemeLibConfig.FONT_SIZES.DEFAULT;
        Bitmap.Config bitmapConfig = bitmap.getConfig();
        // set default bitmap config if none
        if (bitmapConfig == null) {
            bitmapConfig = Bitmap.Config.RGB_565;
        }
        // resource bitmaps are immutable,
        // so we need to convert it to mutable one
        bitmap = bitmap.copy(bitmapConfig, true);
        Canvas canvas = new Canvas(bitmap);

        // new antialiased Paint
        TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

        paint.setStrokeWidth(borderScale);

        for (MemeEditorElements.EditorCaption caption : memeEditorElements.getCaptions()) {
            String textString = caption.isAllCaps() ? caption.getText().toUpperCase() : caption.getText();

            if (TextUtils.isEmpty(textString)) {
                textString = getString(R.string.tap_here_to_add_caption);
                paint.setTextSize((int) (scale * caption.getFontSize() * 5 / 8));
                paint.setTypeface(caption.getFont().typeFace);
                paint.setColor(caption.getBorderColor());
                paint.setStyle(Paint.Style.FILL_AND_STROKE);
            } else {
                paint.setTextSize((int) (scale * caption.getFontSize()));
                paint.setTypeface(caption.getFont().typeFace);
                paint.setColor(caption.getBorderColor());
                paint.setStyle(Paint.Style.FILL_AND_STROKE);
            }

            // set text width to canvas width minus 16dp padding
            int textWidth = canvas.getWidth() - (int) (16 * scale);

            // init StaticLayout for text
            StaticLayout textLayout = new StaticLayout(
                    textString, paint, textWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);

            // get height of multiline text
            int textHeight = textLayout.getHeight();

            // get position of text in the canvas, this will depend in its internal location mode
            MemeConfig.Point where = caption.getPositionInCanvas(
                    bitmap.getWidth(), bitmap.getHeight(), textWidth, textHeight);

            // draw text to the Canvas center
            canvas.save();
            canvas.translate(where.x, where.y);
            textLayout.draw(canvas);

            // new antialiased Paint
            paint.setColor(caption.getTextColor());
            paint.setStyle(Paint.Style.FILL);

            // init StaticLayout for text
            textLayout = new StaticLayout(
                    textString, paint, textWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);

            // get height of multiline text
            textHeight = textLayout.getHeight();

            // draw text to the Canvas center
            textLayout.draw(canvas);
            canvas.restore();
        }

        return bitmap;
    }

    public void onCaptionChanged(CharSequence text) {
        if (isBottom) {
            memeEditorElements.getCaptionBottom().setText(text.toString());
        } else {
            memeEditorElements.getCaptionTop().setText(text.toString());
        }
        onMemeEditorObjectChanged();
    }

    public void onMemeEditorObjectChanged() {
        imageEditView.setImageBitmap(null);
        if (lastBitmap != null) {
            lastBitmap.recycle();
        }
        Bitmap bmp = renderMemeImageFromElements(this, memeEditorElements);
        imageEditView.setImageBitmap(bmp);
        lastBitmap = bmp;
    }

    // createForSaving == true will make template text elements empty
    public void recreateImage(boolean createForSaving) {
        if (createForSaving) {
            for (MemeEditorElements.EditorCaption caption : memeEditorElements.getCaptions()) {
                if (TextUtils.isEmpty(caption.getText())) {
                    caption.setText(" ");
                }
            }
        }
        onMemeEditorObjectChanged();
    }

    //////////////////////////////////////////////////////////////
    ////
    ///  Visibility etc
    //
    public void settingsDone() {
        editBar.setVisibility(View.GONE);
        ActivityUtils.get(this).hideSoftKeyboard();
        onMemeEditorObjectChanged();
    }

    void onBottomContainerClicked() {
        toggleMoarControls(true, false);
    }

    @Override
    public void onBackPressed() {
        boolean hasTextInput = !createCaption.getText().toString().isEmpty() ||
                !memeEditorElements.getCaptionBottom().getText().isEmpty() ||
                !memeEditorElements.getCaptionTop().getText().isEmpty();

        if (fullscreenImageView.getVisibility() == View.VISIBLE) {
            fullscreenImageView.setVisibility(View.INVISIBLE);
            toggleMoarControls(true, false);
            return;
        }

        // Close views above
        if (bottomContainerVisible) {
            toggleMoarControls(true, false);
            return;
        }

        if (editBar.getVisibility() != View.GONE) {
            settingsDone();
            return;
        }

        // Auto save if option checked
        if (hasTextInput && app.settings.isAutoSaveMeme()) {
            if (saveMemeToFilesystem(false)) {
                finish();
                return;
            }
        }

        // Close if no input
        if (!hasTextInput) {
            finish();
            return;
        }

        // Else wait for double back-press
        if (doubleBackToExitPressedOnce) {
            super.onBackPressed();
            return;
        }
        doubleBackToExitPressedOnce = true;
        Snackbar.make(binding.getRoot(), R.string.press_back_again_to_stop_editing__appspecific, Snackbar.LENGTH_SHORT).show();
        new Handler().postDelayed(() -> doubleBackToExitPressedOnce = false, 2000);
    }

    public void touchTopElement() {
        onImageTouched(imageEditView, MotionEvent.obtain(1, 1, MotionEvent.ACTION_DOWN, 0, 0, 0));
    }

    public boolean onImageTouched(View view, MotionEvent event) {
        if (editBar.getVisibility() == View.VISIBLE && !createCaption.getText().toString().isEmpty()) {
            onMemeEditorObjectChanged();
        }
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            float heightOfPic = view.getMeasuredHeight();
            float heightOfEvent = event.getY();

            int position = (int) (heightOfEvent / heightOfPic * 100);

            isBottom = position >= 50;

            editBar.setVisibility(View.VISIBLE);

            String areaCaption = isBottom ?
                    memeEditorElements.getCaptionBottom().getText() :
                    memeEditorElements.getCaptionTop().getText();

            createCaption.setText(areaCaption);
            createCaption.requestFocus();

            ActivityUtils.get(this).showSoftKeyboard();

            if (bottomContainerVisible) {
                toggleMoarControls(true, false);
            }
            return true;
        }
        return super.onTouchEvent(event);
    }

    // Text Observer Adapter
    private abstract static class TextWatcherAdapter implements android.text.TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {}
        @Override
        public void afterTextChanged(android.text.Editable s) {}
    }
}