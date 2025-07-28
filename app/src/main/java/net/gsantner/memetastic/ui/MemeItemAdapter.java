package net.gsantner.memetastic.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.RecyclerView;

import net.gsantner.memetastic.App;
import net.gsantner.memetastic.activity.MainActivity;
import net.gsantner.memetastic.activity.MemeCreateActivity;
import net.gsantner.memetastic.data.MemeData;
import net.gsantner.memetastic.service.ImageLoaderTask;
import net.gsantner.memetastic.util.ContextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.github.gsantner.memetastic.R;
import io.github.gsantner.memetastic.databinding.ItemRowWithTitleBinding;
import io.github.gsantner.memetastic.databinding.ItemSquareImageBinding;

/**
 * Adapter to show images in given view mode
 */
public class MemeItemAdapter extends RecyclerView.Adapter<MemeItemAdapter.ViewHolder> implements ImageLoaderTask.OnImageLoadedListener<MemeItemAdapter.ViewHolder> {
    public static final int VIEW_TYPE__PICTURE_GRID = 0;
    public static final int VIEW_TYPE__ROWS_WITH_TITLE = 1;

    private int itemViewType = -1;
    private List<MemeData.Image> originalImageDataList; // original data
    private List<MemeData.Image> imageDataList; // filtered data (use this)
    private int shortAnimationDuration;
    private Activity activity;
    private App app;

    public MemeItemAdapter(List<MemeData.Image> imageDataList, Activity activity, int itemViewType) {
        this.originalImageDataList = imageDataList;
        this.imageDataList = new ArrayList<>(imageDataList);
        this.shortAnimationDuration = -1;
        this.activity = activity;
        this.app = (App) (activity.getApplication());
        this.itemViewType = itemViewType;
    }

    public void setOriginalImageDataList(List<MemeData.Image> originalImageDataList) {
        this.originalImageDataList = originalImageDataList;
    }

    @Override
    public int getItemViewType(int position) {
        return itemViewType;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());

        if (viewType == VIEW_TYPE__ROWS_WITH_TITLE) {
            ItemRowWithTitleBinding binding = ItemRowWithTitleBinding.inflate(inflater, parent, false);
            return new ViewHolder(binding);
        } else {
            ItemSquareImageBinding binding = ItemSquareImageBinding.inflate(inflater, parent, false);
            return new ViewHolder(binding);
        }
    }

    // sets up the view of the item
    @Override
    public void onBindViewHolder(final ViewHolder holder, int pos) {
        final MemeData.Image imageData = imageDataList.get(holder.getAdapterPosition());
        if (imageData == null || imageData.fullPath == null || !imageData.fullPath.exists()) {
            holder.setImageResource(R.drawable.ic_mood_bad_black_256dp);
            holder.setFavButtonVisibility(View.INVISIBLE);
            holder.setTitleText("Meme");
            return;
        }

        holder.setTitleText(imageData.conf.getTitle());
        holder.setFavButtonVisibility(View.INVISIBLE);
        holder.setImageViewVisibility(View.INVISIBLE);

        ImageLoaderTask<ViewHolder> taskLoadImage = new ImageLoaderTask<>(this, activity, true, holder);
        taskLoadImage.execute(imageData.fullPath);

        holder.setTag(imageData);
        tintFavouriteImage(holder.getFavButton(), app.settings.isFavorite(imageData.fullPath.toString()));
        preparePopupMenu(holder);

        holder.getFavButton().setOnClickListener(v -> {
            MemeData.Image image = holder.getTag();
            if (image != null && image.isTemplate) {
                toggleFavorite(holder);
            }
        });

        holder.getImageView().setOnClickListener(v -> {
            MemeData.Image image = holder.getTag();
            if (image == null) return;

            if (image.isTemplate) {
                Intent intent = new Intent(activity, MemeCreateActivity.class);
                intent.putExtra(MemeCreateActivity.EXTRA_IMAGE_PATH, image.fullPath.getAbsolutePath());
                intent.putExtra(MemeCreateActivity.EXTRA_MEMETASTIC_DATA, image);
                activity.startActivityForResult(intent, MemeCreateActivity.RESULT_MEME_EDITING_FINISHED);
            } else {
                if (activity instanceof MainActivity) {
                    ((MainActivity) activity).openImageViewActivityWithImage(holder.getAdapterPosition(), image.fullPath.getAbsolutePath());
                }
            }
        });
    }

    // gets and returns the count of available items in the grid
    @Override
    public int getItemCount() {
        return imageDataList.size();
    }

    @Override
    public void onImageLoaded(Bitmap bitmap, final ViewHolder holder) {
        MemeData.Image dataImage = holder.getTag();
        if (dataImage == null) return;

        Animation animation = AnimationUtils.loadAnimation(activity, R.anim.fadeinfast);
        holder.getImageView().startAnimation(animation);
        if (dataImage.isTemplate) {
            holder.getFavButton().startAnimation(animation);
            holder.setFavButtonVisibility(View.VISIBLE);
        }

        if (app.settings.isHidden(dataImage.fullPath.getAbsolutePath())) {
            holder.setFavButtonVisibility(View.INVISIBLE);
            holder.getImageView().setOnClickListener(null);
            preparePopupMenu(holder);
        }

        holder.setImageBitmap(bitmap);
        holder.setImageViewVisibility(View.VISIBLE);
    }

    private void toggleFavorite(ViewHolder holder) {
        MemeData.Image dataImage = holder.getTag();
        if (dataImage == null || !dataImage.isTemplate) {
            return;
        }

        if (app.settings.toggleFavorite(dataImage.fullPath.getAbsolutePath())) {
            tintFavouriteImage(holder.getFavButton(), true);
        } else {
            tintFavouriteImage(holder.getFavButton(), false);
        }

        int index = imageDataList.indexOf(dataImage);
        if (index >= 0) {
            notifyItemChanged(index);
        }
    }

    private void preparePopupMenu(final ViewHolder holder) {
        final MemeData.Image imageData = holder.getTag();
        if (imageData == null) return;

        final PopupMenu menu = new PopupMenu(activity, holder.getImageView());
        menu.inflate(R.menu.memeitemadapter__popup_menu);
        ContextUtils.popupMenuEnableIcons(menu);

        menu.setOnMenuItemClickListener(menuItem -> {
            switch (menuItem.getItemId()) {
                case R.id.memeitemadapter__popup_menu__action_fav:
                    toggleFavorite(holder);
                    return true;
                case R.id.memeitemadapter__popup_menu__action_hide:
                    int position = holder.getAdapterPosition();
                    toggleHidden(holder, position);
                    if (activity instanceof MainActivity) {
                        ((MainActivity) activity).updateHiddenNavOption();
                    }
                    return true;
                case R.id.memeitemadapter__popup_menu__action_title:
                    Toast.makeText(holder.getImageView().getContext(), imageData.conf.getTitle(), Toast.LENGTH_SHORT).show();
                    return true;
            }
            return false;
        });

        View longClickView = holder.getLongClickView();

        longClickView.setOnLongClickListener(v -> {
            Menu itemMenu = menu.getMenu();
            boolean isHidden = app.settings.isHidden(imageData.fullPath.toString());
            boolean isFav = app.settings.isFavorite(imageData.fullPath.toString());
            boolean isTemplate = imageData.isTemplate;

            itemMenu.findItem(R.id.memeitemadapter__popup_menu__action_hide)
                    .setVisible(isTemplate)
                    .setTitle(isHidden ? R.string.unhide : R.string.hide);

            itemMenu.findItem(R.id.memeitemadapter__popup_menu__action_fav)
                    .setVisible(isTemplate)
                    .setTitle(isFav ? R.string.remove_favourite : R.string.favourite);

            menu.show();
            return true;
        });
    }

    private void toggleHidden(ViewHolder holder, int position) {
        MemeData.Image image = holder.getTag();
        if (image == null) return;

        String filePath = image.fullPath.getAbsolutePath();

        if (app.settings.toggleHiddenMeme(filePath)) {
            imageDataList.remove(image);
            notifyItemRemoved(position);
        } else {
            imageDataList.remove(image);
            notifyItemRemoved(position);
            if (activity instanceof MainActivity) {
                ((MainActivity) activity).recreateFragmentsAfterUnhiding();
            }
        }

        if (imageDataList.isEmpty() && activity instanceof MainActivity) {
            ((MainActivity) activity).selectCreateMainMode();
        }
    }

    private void tintFavouriteImage(ImageView iv, boolean isFav) {
        ContextUtils.setDrawableWithColorToImageView(iv,
                isFav ? R.drawable.ic_star_black_32dp : R.drawable.ic_star_border_black_32dp,
                isFav ? R.color.comic_yellow : R.color.comic_blue);
    }

    public void setFilter(String filter) {
        imageDataList.clear();
        String[] filterTokens = filter.toLowerCase().split("[\\W_]");
        ArrayList<String> contentTokens = new ArrayList<>();

        for (MemeData.Image image : originalImageDataList) {
            contentTokens.clear();

            // Tokenize filename
            contentTokens.addAll(Arrays.asList(image.fullPath.getName().toLowerCase().split("[\\W_]")));

            // Tokenize the image title (split by everything that's not a word)
            if (image.conf != null && image.conf.getTitle() != null && !image.conf.getTitle().isEmpty()) {
                contentTokens.addAll(Arrays.asList(image.conf.getTitle().toLowerCase().split("[\\W_]")));
            }

            // Tokenize tags
            if (image.conf != null && image.conf.getTags() != null) {
                contentTokens.addAll(image.conf.getTags());
            }

            boolean allTokensFound = true;
            for (String filterToken : filterTokens) {
                boolean foundTokenInTitle = false;
                for (String titleToken : contentTokens) {
                    if (titleToken.contains(filterToken)) {
                        foundTokenInTitle = true;
                    }
                }
                if (!foundTokenInTitle) {
                    allTokensFound = false;
                    break;
                }
            }

            if (allTokensFound) {
                imageDataList.add(image);
            }
        }
        notifyDataSetChanged();
    }

    // ViewHolder class using ViewBinding
    public class ViewHolder extends RecyclerView.ViewHolder {
        private ItemSquareImageBinding squareImageBinding;
        private ItemRowWithTitleBinding rowWithTitleBinding;
        private MemeData.Image tag;

        public ViewHolder(ItemSquareImageBinding binding) {
            super(binding.getRoot());
            this.squareImageBinding = binding;
            this.rowWithTitleBinding = null;

            if (shortAnimationDuration < 0) {
                shortAnimationDuration = binding.getRoot().getContext().getResources().getInteger(
                        android.R.integer.config_shortAnimTime);
            }
        }

        public ViewHolder(ItemRowWithTitleBinding binding) {
            super(binding.getRoot());
            this.rowWithTitleBinding = binding;
            this.squareImageBinding = null;

            if (shortAnimationDuration < 0) {
                shortAnimationDuration = binding.getRoot().getContext().getResources().getInteger(
                        android.R.integer.config_shortAnimTime);
            }
        }

        // Helper methods to access views
        public ImageView getImageView() {
            if (squareImageBinding != null) {
                return squareImageBinding.itemSquareImageImage;
            } else {
                return rowWithTitleBinding.itemSquareImageImage;
            }
        }

        public ImageView getFavButton() {
            if (squareImageBinding != null) {
                return squareImageBinding.itemSquareImageImageBottomEnd;
            } else {
                return rowWithTitleBinding.itemSquareImageImageBottomEnd;
            }
        }

        public TextView getTitleView() {
            if (squareImageBinding != null) {
                return squareImageBinding.itemSquareImageTitle;
            } else {
                return rowWithTitleBinding.itemSquareImageTitle;
            }
        }

        public View getLongClickView() {
            if (itemViewType == VIEW_TYPE__ROWS_WITH_TITLE) {
                return itemView;
            } else {
                return getImageView();
            }
        }

        public void setTag(MemeData.Image image) {
            this.tag = image;
            getImageView().setTag(image);
            getFavButton().setTag(image);
        }

        public MemeData.Image getTag() {
            return tag;
        }

        public void setImageResource(int resId) {
            getImageView().setImageResource(resId);
        }

        public void setImageBitmap(Bitmap bitmap) {
            getImageView().setImageBitmap(bitmap);
        }

        public void setImageViewVisibility(int visibility) {
            getImageView().setVisibility(visibility);
        }

        public void setFavButtonVisibility(int visibility) {
            getFavButton().setVisibility(visibility);
        }

        public void setTitleText(String text) {
            getTitleView().setText(text);
        }
    }
}