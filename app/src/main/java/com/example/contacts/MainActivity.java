package com.example.contacts;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.accounts.AccountManager;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Random;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    Button addBtn;
    Button delAllBtn;
    EditText addCountEt;
    TextView totalAddedTv;
    TextView deletedCountTv;
    TextView cancelAdd;
    TextView getDelDescription;

    private boolean readWriteContactsPermitted = false;
    DelTestContactsAsync delTestContactsAsync;
    AddTestContactsAsync addTestContactsAsync;
    GetTestContactsAsync getTestContactsAsync;

    private final static String TEST_CONTACT_NAME = "TestContact_";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d("myLog", "onCreate");
        addBtn = findViewById(R.id.addBtn);
        addBtn.setOnClickListener(this);
        delAllBtn = findViewById(R.id.delAllBtn);
        delAllBtn.setOnClickListener(this);
        addCountEt = findViewById(R.id.countEt);
        totalAddedTv = findViewById(R.id.totalCountTv);
        deletedCountTv = findViewById(R.id.deletedCountTv);
        cancelAdd = findViewById(R.id.cancelAdd);
        cancelAdd.setOnClickListener(this);
        getDelDescription = findViewById(R.id.getDelDesc);

        requestContactPermission();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d("myLog", "onDestroy");
        if (addTestContactsAsync != null) {
            addTestContactsAsync.cancel(true);
            addTestContactsAsync = null;
        }
        if (delTestContactsAsync != null) {
            delTestContactsAsync.cancel(true);
            delTestContactsAsync = null;
        }
        if (getTestContactsAsync != null) {
            getTestContactsAsync.cancel(true);
            getTestContactsAsync = null;
        }
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.addBtn) {
            totalAddedTv.setText("0");
            deletedCountTv.setText("0");
            addTestContacts();

        } else if (v.getId() == R.id.delAllBtn) {
            if (!readWriteContactsPermitted) {
                requestContactPermission();
                return;
            }
            totalAddedTv.setText("0");
            deletedCountTv.setText("0");
            if (getTestContactsAsync == null) getTestContactsAsync = new GetTestContactsAsync();
            if (!getTestContactsAsync.getStatus().equals(AsyncTask.Status.RUNNING) &&
                    (addTestContactsAsync == null || !addTestContactsAsync.getStatus().equals(AsyncTask.Status.RUNNING))
                    && (delTestContactsAsync == null || !delTestContactsAsync.getStatus().equals(AsyncTask.Status.RUNNING))
            ) {
                getTestContactsAsync.execute();
            } else Toast.makeText(this, "Wait, I`m working...", Toast.LENGTH_LONG).show();

        } else if (v.getId() == R.id.cancelAdd) {
            if (addTestContactsAsync != null) addTestContactsAsync.cancel(true);
            addTestContactsAsync = null;
        }
    }

    private void addTestContacts() {
        if (!readWriteContactsPermitted) {
            requestContactPermission();
            return;
        }
        String countStr = addCountEt.getText().toString();
        try {
            int countInt = Integer.parseInt(countStr);
            if (countInt == 0) return;

            if (addTestContactsAsync == null) addTestContactsAsync = new AddTestContactsAsync();
            if (!addTestContactsAsync.getStatus().equals(AsyncTask.Status.RUNNING) &&
                    (delTestContactsAsync == null || !delTestContactsAsync.getStatus().equals(AsyncTask.Status.RUNNING)) &&
                    (getTestContactsAsync == null || !getTestContactsAsync.getStatus().equals(AsyncTask.Status.RUNNING))
            ) {
                addTestContactsAsync.execute(countInt);
            } else Toast.makeText(this, "Wait, I`m working...", Toast.LENGTH_LONG).show();

        } catch (NumberFormatException e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    private void addContact(String name, String phone) {
        ArrayList<ContentProviderOperation> operationList = new ArrayList <> ();

        operationList.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                .build());

        // first and last names
        operationList.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Contacts.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Contacts.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, name)
                .build());

        operationList.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE,ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_HOME)
                .build());
        // Asking the Contact provider to create a new contact
        try {
            getContentResolver().applyBatch(ContactsContract.AUTHORITY, operationList);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Exception: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private ArrayList<TestContact> getTestContacts() {
        ArrayList<TestContact> testContacts = new ArrayList<>();
        ContentResolver cr = getContentResolver();
        Cursor cur = cr.query(ContactsContract.Contacts.CONTENT_URI,
                null, null, null, null);

        if ((cur != null ? cur.getCount() : 0) > 0) {
            while (cur != null && cur.moveToNext()) {
                String id = cur.getString(
                        cur.getColumnIndex(ContactsContract.Contacts._ID));
                String name = cur.getString(cur.getColumnIndex(
                        ContactsContract.Contacts.DISPLAY_NAME));

                String phoneNo = "non";
                if (cur.getInt(cur.getColumnIndex(
                        ContactsContract.Contacts.HAS_PHONE_NUMBER)) > 0) {
                    Cursor pCur = cr.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            null,
                            ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                            new String[]{id},
                            null
                    );
                    assert pCur != null;
                    while (pCur.moveToNext()) {
                        phoneNo = pCur.getString(pCur.getColumnIndex(
                                ContactsContract.CommonDataKinds.Phone.NUMBER));
                        Log.i("myLog", "ID: " + id);
                        Log.i("myLog", "Name: " + name);
                        Log.i("myLog", "Phone Number: " + phoneNo);
                        Log.i("myLog", "_______________________________ ");
                    }
                    pCur.close();
                }
                if (name.contains(TEST_CONTACT_NAME)) testContacts.add(new TestContact(id, name, phoneNo));
            }
        } else Log.i("myLog", "No Contacts");
        if (cur != null) {
            cur.close();
        }
        return testContacts;
    }

    public void requestContactPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.READ_CONTACTS)
                        || ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_CONTACTS)) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Read Contacts permission");
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.setMessage("Please enable access to contacts.");
                    builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @TargetApi(Build.VERSION_CODES.M)
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                            requestPermissions(
                                    new String[]{android.Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS}
                                    , 1);
                        }
                    });
                    builder.show();
                } else {
                    ActivityCompat.requestPermissions(this,
                            new String[]{android.Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS},
                            1);
                }
            } else {
                readWriteContactsPermitted = true;
            }
        } else {
            readWriteContactsPermitted = true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case 1: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    readWriteContactsPermitted = true;
                } else {
                    Toast.makeText(this, "You have disabled a contacts permission", Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    public static String generateRandomPhoneNum() {
        Random random = new Random();
        StringBuilder sb = new StringBuilder();

        // first not 0 digit
        sb.append(random.nextInt(9));

        // rest of 7 digits
        for (int i = 0; i < 7; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }

    private class AddTestContactsAsync extends AsyncTask<Integer, Integer, Integer> {

        int addedCount = 0;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            addCountEt.getText().clear();
        }

        @Override
        protected Integer doInBackground(Integer... integers) {
            for (int i = 1; i <= integers[0]; i++) {
                if (isCancelled()) break;
                String generated = generateRandomPhoneNum();
                String phone = "+9929" + generated;
                String name = TEST_CONTACT_NAME + generated;

                addContact(name, phone);
                publishProgress(++addedCount);
            }
            return addedCount;
        }

        @Override
        protected void onPostExecute(Integer integer) {
            super.onPostExecute(integer);
            totalAddedTv.setText("" + integer);
            addTestContactsAsync = null;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            totalAddedTv.setText("" + values[0]);
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            Log.d("myLog", "AddTestContactsAsync -> Cancelled");
        }
    }

    private class DelTestContactsAsync extends AsyncTask<ArrayList<TestContact>, Integer, Integer> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            getDelDescription.setText("Deleted contacts: ");
        }

        @Override
        protected void onPostExecute(Integer integer) {
            super.onPostExecute(integer);
            delTestContactsAsync = null;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            deletedCountTv.setText("" + values[0]);
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            Log.d("myLog", "DelTestContactsAsync->Cancelled");
        }

        @Override
        protected Integer doInBackground(ArrayList<TestContact>... lists) {
            ArrayList<TestContact> testContacts = lists[0];
            int count = 0;
            if (testContacts.size() < 1) return 0;

            for (TestContact contact :testContacts) {
                Uri contactUri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(contact.phone));
                Cursor cur = MainActivity.this.getContentResolver().query(contactUri, null, null, null, null);
                try {
                    assert cur != null;
                    if (cur.moveToFirst()) {
                        do {
                            String lookupKey = cur.getString(cur.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY));
                            Uri uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey);
                            MainActivity.this.getContentResolver().delete(uri, null, null);
                            publishProgress(++count);
                        } while (cur.moveToNext());
                    }

                } catch (Exception e) {
                    System.out.println(e.getStackTrace());
                }
                cur.close();
            }
            return count;
        }
    }

    private class GetTestContactsAsync extends AsyncTask<Void, Integer, ArrayList<TestContact>> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            getDelDescription.setText("Getting contacts: ");
        }

        @Override
        protected void onPostExecute(ArrayList<TestContact> testContacts) {
            super.onPostExecute(testContacts);
            getTestContactsAsync = null;
            delTestContactsAsync = new DelTestContactsAsync();
           delTestContactsAsync.execute(testContacts);
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            deletedCountTv.setText("" + values[0]);
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
        }

        @Override
        protected ArrayList<TestContact> doInBackground(Void... voids) {
            ArrayList<TestContact> testContacts = new ArrayList<>();
            ContentResolver cr = getContentResolver();
            Cursor cur = cr.query(ContactsContract.Contacts.CONTENT_URI,
                    null, null, null, null);

            if ((cur != null ? cur.getCount() : 0) > 0) {
                while (cur != null && cur.moveToNext()) {
                    if (isCancelled()) break;
                    String id = cur.getString(
                            cur.getColumnIndex(ContactsContract.Contacts._ID));
                    String name = cur.getString(cur.getColumnIndex(
                            ContactsContract.Contacts.DISPLAY_NAME));

                    String phoneNo = "non";
                    if (cur.getInt(cur.getColumnIndex(
                            ContactsContract.Contacts.HAS_PHONE_NUMBER)) > 0) {
                        Cursor pCur = cr.query(
                                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                null,
                                ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                                new String[]{id},
                                null
                        );
                        assert pCur != null;
                        while (pCur.moveToNext()) {
                            phoneNo = pCur.getString(pCur.getColumnIndex(
                                    ContactsContract.CommonDataKinds.Phone.NUMBER));
                        }
                        pCur.close();
                    }
                    if (name.contains(TEST_CONTACT_NAME)) testContacts.add(new TestContact(id, name, phoneNo));
                    publishProgress(testContacts.size());
                }
            } else Log.i("myLog", "No Contacts");
            if (cur != null) {
                cur.close();
            }
            return testContacts;
        }
    }

    class TestContact {
        String id;
        String name;
        String phone;

        public TestContact(String id, String name, String phone) {
            this.id = id;
            this.name = name;
            this.phone = phone;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }
    }
}
