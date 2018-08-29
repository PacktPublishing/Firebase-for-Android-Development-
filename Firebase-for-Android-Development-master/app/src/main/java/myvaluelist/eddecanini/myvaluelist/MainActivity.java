package myvaluelist.eddecanini.myvaluelist;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;

import com.beardedhen.androidbootstrap.BootstrapButton;
import com.crashlytics.android.Crashlytics;
import com.firebase.ui.auth.AuthUI;
import com.github.aakira.expandablelayout.ExpandableLinearLayout;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity
        implements TodoAdapter.onImageClickedListener {

    String LOG_TAG = MainActivity.class.getSimpleName();

    private static int RC_SIGN_IN = 1;
    private static int RC_PICK_IMAGE = 2;

    FirebaseFirestore db;
    FirebaseStorage storage;
    String uid;

    List<AuthUI.IdpConfig> providers = Arrays.asList(
            new AuthUI.IdpConfig.Builder(AuthUI.EMAIL_PROVIDER).build()
    );

    View touchInterceptor;
    FloatingActionButton fab;
    ExpandableLinearLayout lytAddItem;
    ListView listTodo;
    EditText etAddItem;
    BootstrapButton btnAddItem;

    TodoAdapter todoAdapter;
    ArrayList<String> todoItems = new ArrayList<>();

    int requestingPosition;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        referenceViews();
        initFirebase();
    }

    private void referenceViews() {
        touchInterceptor = findViewById(R.id.touch_interceptor);
        lytAddItem = findViewById(R.id.lyt_add_item);
        listTodo = findViewById(R.id.list_todo);
        etAddItem = findViewById(R.id.et_add_item);
        btnAddItem = findViewById(R.id.btn_add_item);
        fab = findViewById(R.id.fab);

        fab.setOnClickListener(v -> {
            lytAddItem.expand();
            fab.setVisibility(View.GONE);
            touchInterceptor.setVisibility(View.VISIBLE);
        });

        touchInterceptor.setOnClickListener(v -> {
            lytAddItem.collapse();
            fab.setVisibility(View.VISIBLE);
            touchInterceptor.setVisibility(View.GONE);
        });

        initList();

        // This button inserts the list item
        btnAddItem.setOnClickListener(v -> insertItem(etAddItem.getText().toString()));
    }

    private void insertItem(String newItem) {
        etAddItem.setText("");
        todoItems.add(newItem);

        Map<String, Object> userMap = new HashMap<>();
        userMap.put("todoList", todoItems);
        db.collection("users").document(uid).set(userMap);
    }

    private void initList() {
        todoAdapter = new TodoAdapter(this, R.layout.list_item_todo, todoItems);
        listTodo.setAdapter(todoAdapter);
    }

    private void addListListener() {
        // Downloads and updates to-do list
        db.collection("users").document(uid).addSnapshotListener((documentSnapshot, e) -> {
            if (!documentSnapshot.exists())
                return;
            ArrayList<String> newTodoItems = (ArrayList<String>) documentSnapshot.get("todoList");
            if (newTodoItems == null)
                return;
            todoItems.clear();
            todoItems.addAll(newTodoItems);
            todoAdapter.notifyDataSetChanged();
        });
    }

    private void initFirebase() {
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();

        uid = FirebaseAuth.getInstance().getUid();
        Log.v(LOG_TAG, "Uid: " + uid);

        // Open Authentication Screen if needed
        if (uid == null) {
            startActivityForResult(
                    AuthUI.getInstance()
                            .createSignInIntentBuilder()
                            .setAvailableProviders(providers)
                            .build(), RC_SIGN_IN);
            return;
        }


        addListListener();
        prepareCrashReport();
    }

    private void prepareCrashReport() {
        Crashlytics.log("Error Report");
        Crashlytics.setInt("listSize", todoItems.size());
        Crashlytics.setUserIdentifier(uid);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Respond to Authentication Callback
        if (requestCode == RC_SIGN_IN && resultCode == RESULT_OK) {
            uid = FirebaseAuth.getInstance().getUid();
            initFirebase();

            String token = FirebaseInstanceId.getInstance().getToken();
            DocumentReference userRef = db.collection("users").document(uid);
            userRef.addSnapshotListener((documentSnapshot, e) -> {
                if (documentSnapshot.exists()) {
                    userRef.update("messagingToken", token);
                } else {
                    Map<String, Object> userMap = new HashMap<>();
                    userMap.put("messagingToken", token);
                    userRef.set(userMap);
                }
            });

        }

        if (requestCode == RC_PICK_IMAGE && resultCode == RESULT_OK) {
            try {
                String itemName = todoItems.get(requestingPosition);
                InputStream inputStream = getContentResolver().openInputStream(data.getData());
                Uri fileUri = saveFile(inputStream, itemName);

                StorageReference imageReference = storage.getReference()
                        .child(uid).child("images").child(itemName);
                imageReference.putFile(fileUri).addOnCompleteListener(new OnCompleteListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<UploadTask.TaskSnapshot> task) {
                        if (task.isSuccessful()) {
                            Log.d(LOG_TAG, "Upload success: " + task.getResult().getDownloadUrl());
                        } else {
                            Log.w(LOG_TAG, "Upload failed: " + task.getException());
                        }
                    }
                });
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.ic_logout) {
            FirebaseAuth.getInstance().signOut();
            initFirebase();
            return true;
        }

        return false;
    }

    private Uri saveFile(InputStream input, String itemName) {
        File file = new File(getCacheDir(), itemName);
        Uri returnUri = null;
        try {
            OutputStream output = new FileOutputStream(file);
            try {
                byte[] buffer = new byte[4 * 1024]; // or other buffer size
                int read;

                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }

                output.flush();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    output.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } finally {
            try {
                input.close();
                returnUri = Uri.fromFile(file);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return returnUri;
    }

    @Override
    public void OnImageClicked(int position) {
        requestingPosition = position;

        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), RC_PICK_IMAGE);
    }
}
