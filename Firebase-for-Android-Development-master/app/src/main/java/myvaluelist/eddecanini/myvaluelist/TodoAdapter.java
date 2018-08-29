package myvaluelist.eddecanini.myvaluelist;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.github.aakira.expandablelayout.ExpandableLinearLayout;
import com.github.aakira.expandablelayout.ExpandableRelativeLayout;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FileDownloadTask;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TodoAdapter extends ArrayAdapter<String> {

    String LOG_TAG = TodoAdapter.class.getSimpleName();

    Context context;
    int resource;
    List<String> objects;

    onImageClickedListener imageClickedListener;

    public TodoAdapter(@NonNull Context context, int resource, @NonNull List<String> objects) {
        super(context, resource, objects);
        this.context = context;
        this.resource = resource;
        this.objects = objects;

        imageClickedListener = (onImageClickedListener) context;
    }

    public interface onImageClickedListener {
        void OnImageClicked(int position);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(resource, parent, false);
        }

        View rootLayout = convertView.findViewById(R.id.root_layout);
        ExpandableLinearLayout expandView = convertView.findViewById(R.id.expand_view);
        ImageView btnImage = convertView.findViewById(R.id.btn_image);
        ImageView imgExpanded = convertView.findViewById(R.id.img_expanded);

        rootLayout.setOnClickListener(v -> {
            expandView.toggle();
        });

        btnImage.setOnClickListener(v -> imageClickedListener.OnImageClicked(position));

        // Apply the data to the to-do list
        setItemTextFromFirestore(convertView, position);
        downloadImage(position, imgExpanded);

        return convertView;
    }

    private void downloadImage(int position, ImageView imgExpanded) {
        String itemName = objects.get(position);
        File imageFile = new File(context.getCacheDir(), itemName);

        FirebaseStorage storage = FirebaseStorage.getInstance();
        String uid = FirebaseAuth.getInstance().getUid();
        StorageReference imageRef = storage.getReference()
                .child(uid).child("images").child(itemName);

        imageRef.getFile(Uri.fromFile(imageFile))
                .addOnSuccessListener(taskSnapshot -> {
                    Log.d(LOG_TAG, "Image download successful: " + imageFile.getPath());
                    imgExpanded.setImageURI(Uri.fromFile(imageFile));
                })
                .addOnFailureListener(e -> {
                    Log.d(LOG_TAG, "Image download failed: " + e.getMessage());
                });
    }

    private void setItemTextFromFirestore(View convertView, int position) {
        ((TextView) convertView.findViewById(R.id.tv_item)).setText(this.objects.get(position));
        convertView.findViewById(R.id.btn_delete).setOnClickListener(v -> {
            // Delete List Item
            this.objects.remove(position);
            notifyDataSetChanged();

            FirebaseFirestore db = FirebaseFirestore.getInstance();
            String uid = FirebaseAuth.getInstance().getUid();

            Map<String, Object> userMap = new HashMap<>();
            userMap.put("todoList", this.objects);
            db.collection("users").document(uid).set(userMap);
            addValuePoints();
        });
    }

    private void addValuePoints() {
        // Add points
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        int valuePoints = preferences.getInt("value_points", 0);
        valuePoints += 10;

        preferences.edit()
                .putInt("value_points", valuePoints)
                .apply();

        // Check if user property should be set
        FirebaseAnalytics analytics = FirebaseAnalytics.getInstance(getContext());
        if (valuePoints >= 100 && valuePoints < 200)
            analytics.setUserProperty("reward_level", "1");
        else if (valuePoints >= 200)
            analytics.setUserProperty("reward_level", "2");
    }

}
