package com.project.nightkoders.ftp;
//Android internal packages
import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.design.widget.TextInputLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
//Directory chooser
import net.rdrei.android.dirchooser.DirectoryChooserActivity;
import net.rdrei.android.dirchooser.DirectoryChooserConfig;
//Apache's FTP Server packages
import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.ftplet.Authority;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.FtpReply;
import org.apache.ftpserver.ftplet.FtpRequest;
import org.apache.ftpserver.ftplet.FtpSession;
import org.apache.ftpserver.ftplet.Ftplet;
import org.apache.ftpserver.ftplet.FtpletContext;
import org.apache.ftpserver.ftplet.FtpletResult;
import org.apache.ftpserver.ftplet.UserManager;
import org.apache.ftpserver.listener.ListenerFactory;
import org.apache.ftpserver.usermanager.PropertiesUserManagerFactory;
import org.apache.ftpserver.usermanager.SaltedPasswordEncryptor;
import org.apache.ftpserver.usermanager.impl.BaseUser;
import org.apache.ftpserver.usermanager.impl.WritePermission;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    TextView mAddrReg, mAddriOS, mPrompt, mPasswdDisp, mUserDisp, mDirAddress; // Here we are declaring the text view items sych as address, password, username and directory address
    EditText mUser, mPasswd; // Kunalandroid : This is and editable username and password field
    Switch mTogglePass; // This is a toggle to show or hide password
    LinearLayout mAddr1, mAddr2; // These are local address and remote address
    TextInputLayout mUserParent, mPasswdParent; // These are the labels for the username and password
    Button mDirChooser; // This is the directory chooser button
    static String pass; // This is used to match the successful authentication

    final int MY_PERMISSIONS_REQUEST = 2203; // Nehanshj : Android App permission from https://developer.android.com/training/permissions/requesting
    final int REQUEST_DIRECTORY = 2108; // Nehanshj : Android App permission from https://developer.android.com/training/permissions/requesting

    FtpServerFactory serverFactory = new FtpServerFactory(); // This is apache ftpserverfactory used to develop this application
    ListenerFactory factory = new ListenerFactory(); //FtpServer listner
    PropertiesUserManagerFactory userManagerFactory = new PropertiesUserManagerFactory(); // FTP Users manager
    FtpServer finalServer;
    Toolbar toolbar;

    boolean justStarted = true; // a boolean to check if the server has started or not

    @SuppressLint("AuthLeak")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage(R.string.dialog_message).setTitle(R.string.dialog_title);
                builder.setPositiveButton("OK", (dialog, id) -> {
                    dialog.dismiss();
                    justStarted = false;
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, MY_PERMISSIONS_REQUEST);
                });
                builder.show();
            } else {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, MY_PERMISSIONS_REQUEST);
            }
        }

        // Checking and storing the credentials of the server
        mTogglePass = findViewById(R.id.togglePass);
        mTogglePass.setEnabled(false);
        mTogglePass.setChecked(false);
        mUserParent = findViewById(R.id.userParent);
        mUser = findViewById(R.id.user);
        mUserDisp = findViewById(R.id.userDisp);
        mPasswd = findViewById(R.id.passwd);
        mPasswdDisp = findViewById(R.id.passwdDisp);
        mPasswdParent = findViewById(R.id.passwdParent);
        mPrompt = findViewById(R.id.prompt);

        mAddrReg = findViewById(R.id.addrReg);
        mAddr2 = findViewById(R.id.addr2);
        mAddrReg.setText(String.format("ftp://%s:2121", wifiIpAddress(this))); // Setting a listner on the FTP port

        mAddriOS = findViewById(R.id.addriOS);
        mAddr1 = findViewById(R.id.addr1);
        mAddriOS.setText(String.format("ftp://ftp:ftp@%s:2121", wifiIpAddress(this))); // Setting the listner in the iOS format

        mDirAddress = findViewById(R.id.dirAddress);
        mDirChooser= findViewById(R.id.dirChooser);

        mDirChooser.setOnClickListener(view -> {
            final Intent chooserIntent = new Intent(this, DirectoryChooserActivity.class); // Directory Choser Button Function

            // Kunalandroid : It chooses the directory path in the android using https://github.com/passy/Android-DirectoryChooser
            final DirectoryChooserConfig config = DirectoryChooserConfig.builder() 
                    .newDirectoryName("New Folder")
                    .allowNewDirectoryNameModification(true)
                    .build();

            chooserIntent.putExtra(DirectoryChooserActivity.EXTRA_CONFIG, config);

            startActivityForResult(chooserIntent, REQUEST_DIRECTORY);
        });

        finalServer = serverFactory.createServer(); // Apache FTP server API

        toolbar.setOnClickListener(view -> {
            try {
                if (checkWifiOnAndConnected(this) || wifiHotspotEnabled(this)) { // Checking if Wifi is ON and Mobile Hotspot is enabled
                    MainActivity.this.serverControl();
                } else {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setMessage(R.string.dialog_wifi_message).setTitle(R.string.dialog_wifi_title);
                    builder.setPositiveButton("OK", (dialog, id) -> dialog.dismiss());
                    builder.show();
                }
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        });

        mTogglePass.setOnCheckedChangeListener((compoundButton, b) -> {
            if (b) {
                mPasswdDisp.setText(String.format("Password: %s", pass));
            } else {
                StringBuilder strB = new StringBuilder("Password: ");
                for (int i=0; i < pass.length(); i++) {
                    strB.append('*');
                }
                mPasswdDisp.setText(strB.toString());
            }
        });

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    // Nehanshj : Checking and browsing the remote device directory by granting the permission to the android app
    // https://developer.android.com/training/permissions/requesting

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST: {
                if (grantResults.length <= 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setMessage(R.string.dialog_message_exit).setTitle(R.string.dialog_title);
                    builder.setPositiveButton("OK", (dialog, id) -> {
                        dialog.dismiss();
                        finish();
                    });
                    builder.show();
                }
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_DIRECTORY) {
            if (resultCode == DirectoryChooserActivity.RESULT_CODE_DIR_SELECTED) {
                mDirAddress.setText(data.getStringExtra(DirectoryChooserActivity.RESULT_SELECTED_DIR));
            }
        }
    }

    // Nehanshj : Stopping or Aborting the server if power button is pressed

    @Override
    protected void onDestroy() {
        try {
            finalServer.stop();
        } catch (Exception e) {
            Log.e("Server Close Error", e.getCause().toString());
        }
        super.onDestroy();
    }
    // Kunalandroid : Adding Device Listener to connect the two devices for data transfer

    private void setupStart(String username, String password, String subLoc) throws FileNotFoundException {
        factory.setPort(2121);
        serverFactory.addListener("default", factory.createListener());

        File files = new File(Environment.getExternalStorageDirectory().getPath() + "/users.properties");
        if (!files.exists()) {
            try {
                files.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        userManagerFactory.setFile(files);
        userManagerFactory.setPasswordEncryptor(new SaltedPasswordEncryptor());
        UserManager um = userManagerFactory.createUserManager();
        BaseUser user = new BaseUser();
        user.setName(username);
        user.setPassword(password);
        String home = Environment.getExternalStorageDirectory().getPath() + "/" + subLoc;
        user.setHomeDirectory(home);

        List<Authority> auths = new ArrayList<>();
        Authority auth = new WritePermission();
        auths.add(auth);
        user.setAuthorities(auths);

        try {
            um.save(user);
        } catch (FtpException e1) {
            e1.printStackTrace();
        }
        // Kunalandroid : Apache Factory FTP API
        serverFactory.setUserManager(um);
        Map<String, Ftplet> m = new HashMap<>();
        m.put("miaFtplet", new Ftplet()
        {

            @Override
            public void init(FtpletContext ftpletContext) throws FtpException {

            }

            @Override
            public void destroy() {

            }

            @Override
            public FtpletResult beforeCommand(FtpSession session, FtpRequest request) throws FtpException, IOException
            {
                return FtpletResult.DEFAULT;
            }

            @Override
            public FtpletResult afterCommand(FtpSession session, FtpRequest request, FtpReply reply) throws FtpException, IOException
            {
                return FtpletResult.DEFAULT;
            }

            @Override
            public FtpletResult onConnect(FtpSession session) throws FtpException, IOException
            {
                return FtpletResult.DEFAULT;
            }

            @Override
            public FtpletResult onDisconnect(FtpSession session) throws FtpException, IOException
            {
                return FtpletResult.DEFAULT;
            }
        });
        serverFactory.setFtplets(m);
    }
    // Kunalandroid : Setting Local Static Wifi Address i.e 192.168.43.1
    private String wifiIpAddress(Context context) {
        try {
            if (wifiHotspotEnabled(context)) {
                return "192.168.43.1";
            }
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return Utils.getIPAddress(true);
    }
    // Checking if wifi hotspot is enabled, if not the turn the hotspot on automatically
    private boolean wifiHotspotEnabled(Context context) throws InvocationTargetException, IllegalAccessException {
        WifiManager manager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        Method method = null;
        try {
            method = manager.getClass().getDeclaredMethod("isWifiApEnabled");
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
        method.setAccessible(true); //in the case of visibility change in future APIs
        return (Boolean) method.invoke(manager);
    }

    private boolean checkWifiOnAndConnected(Context context) {
        WifiManager wifiMgr = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        assert wifiMgr != null;
        if (wifiMgr.isWifiEnabled()) { // Wi-Fi adapter is ON

            WifiInfo wifiInfo = wifiMgr.getConnectionInfo();

            return wifiInfo.getNetworkId() != -1;
        }
        else {
            return false; // Wi-Fi adapter is OFF
        }
    }


    // Nehanshj : If back button is pressed in android device then the FTP server will be stopped/killed
    @Override
    public void onBackPressed() {
        finalServer.stop();
        findViewById(R.id.toolbar).setEnabled(false);
        toolbar.setBackgroundColor(ResourcesCompat.getColor(getResources(), R.color.colorRed, null));
        super.onBackPressed();
    }
    // Final server control board. If the server is stopped then all the credentials are reset to empty
    void serverControl() {

        if (finalServer.isStopped()) {

            mUser.setEnabled(false);
            mPasswd.setEnabled(false);
            mDirChooser.setEnabled(false);

            String user = mUser.getText().toString();
            String passwd = mPasswd.getText().toString();
            if (user.isEmpty()) {
                user = "ftp";
            }
            if (passwd.isEmpty()) {
                passwd = "ftp";
            }
            String subLoc = mDirAddress.getText().toString().substring(20);

            pass = passwd;

            StringBuilder strB = new StringBuilder("Password: ");
            for (int i=0; i < pass.length(); i++) {
                strB.append('*');
            }
            mPasswdDisp.setText(strB.toString());

            mUserDisp.setText(String.format("Username: %s", user));

            mUserDisp.setVisibility(View.VISIBLE);
            mUserParent.setVisibility(View.INVISIBLE);

            mPasswdParent.setVisibility(View.INVISIBLE);
            mPasswdDisp.setVisibility(View.VISIBLE);
            // Startup the server once again after the user has pressed on the app icon in the app drawer
            try {
                setupStart(user, passwd, subLoc);
            } catch (FileNotFoundException fnfe) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setMessage(R.string.dialog_message_error).setTitle(R.string.dialog_title);
                    builder.setPositiveButton("OK", (dialog, id) -> {
                        dialog.dismiss();
                        justStarted = false;
                        ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, MY_PERMISSIONS_REQUEST);
                    });
                    builder.show();
                } else {
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, MY_PERMISSIONS_REQUEST);
                }
            }

            try {

                finalServer.start();
                mAddrReg.setText(String.format("ftp://%s:2121", wifiIpAddress(this)));
                mAddriOS.setText(String.format("ftp://%s:%s@%s:2121", user, passwd, wifiIpAddress(this)));

            } catch (FtpException e) {
                e.printStackTrace();
            }
            toolbar.setBackgroundColor(ResourcesCompat.getColor(getResources(), R.color.colorGreen, null));
            // Here we are setting colors when the server is running and when the server is off.
            mPrompt.setVisibility(View.VISIBLE);
            mAddr1.setVisibility(View.VISIBLE);
            mAddr2.setVisibility(View.VISIBLE);

            mTogglePass.setEnabled(true);

        } else if (finalServer.isSuspended()) {

            finalServer.resume();
            toolbar.setBackgroundColor(ResourcesCompat.getColor(getResources(), R.color.colorGreen, null));

            mPrompt.setVisibility(View.VISIBLE);
            mAddr1.setVisibility(View.VISIBLE);
            mAddr2.setVisibility(View.VISIBLE);

        } else {
            // Here the color is changed to red after the server has been stopped
            finalServer.suspend();
            toolbar.setBackgroundColor(ResourcesCompat.getColor(getResources(), R.color.colorRed, null));

            mPrompt.setVisibility(View.INVISIBLE);

            mAddr1.setVisibility(View.INVISIBLE);
            mAddr2.setVisibility(View.INVISIBLE);

        }

    }

}
