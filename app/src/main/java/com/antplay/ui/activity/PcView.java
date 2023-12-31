package com.antplay.ui.activity;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

import com.antplay.AppView;
import com.antplay.Game;
import com.antplay.LimeLog;
import com.antplay.R;
import com.antplay.api.APIClient;
import com.antplay.api.RetrofitAPI;
import com.antplay.binding.PlatformBinding;
import com.antplay.binding.crypto.AndroidCryptoProvider;
import com.antplay.computers.ComputerManagerService;
import com.antplay.grid.PcGridAdapter;
import com.antplay.grid.assets.DiskAssetLoader;
import com.antplay.models.LoginRequestModal;
import com.antplay.models.MessageResponse;
import com.antplay.models.RefreshRequestModel;
import com.antplay.models.VMTimerReq;
import com.antplay.nvstream.http.ComputerDetails;
import com.antplay.nvstream.http.NvApp;
import com.antplay.nvstream.http.NvHTTP;
import com.antplay.nvstream.http.PairingManager;
import com.antplay.nvstream.http.PairingManager.PairState;
import com.antplay.nvstream.jni.MoonBridge;
import com.antplay.nvstream.wol.WakeOnLanSender;
import com.antplay.preferences.AddComputerManually;
import com.antplay.preferences.GlPreferences;
import com.antplay.preferences.PreferenceConfiguration;
import com.antplay.preferences.StreamSettings;
import com.antplay.ui.fragments.AdapterFragment;
import com.antplay.ui.intrface.AdapterFragmentCallbacks;
import com.antplay.ui.intrface.ClearService;
import com.antplay.utils.AppUtils;
import com.antplay.utils.Const;
import com.antplay.utils.MyDialog;
import com.antplay.utils.HelpLauncher;
import com.antplay.utils.RestClient;
import com.antplay.utils.ServerHelper;
import com.antplay.utils.SharedPreferenceUtils;
import com.antplay.utils.ShortcutHelper;
import com.antplay.utils.SpinnerDialog;
import com.antplay.utils.UiHelper;


import android.app.ActivityManager;
import android.app.Dialog;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Window;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.AdapterContextMenuInfo;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParserException;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import tech.gusavila92.websocketclient.WebSocketClient;

public class PcView extends AppCompatActivity implements AdapterFragmentCallbacks {
    private RelativeLayout noPcFoundLayout;
    Dialog shutDownVMDialog, paymentSuccessDialog,mDialog;
    boolean shutdownVMStatus, startVMStatus, isVMConnected, isVmDisConnected, firstTimeVMTimer,
            paymentStatus = false, startVmTimerStatus = false, firstTimeStartVmApi = false,
            isFirstTime = true, btnStatus = false, btnShutDownStatus = false, firstTimeDialog = false,
            doubleBackToExitPressedOnce = false;
    Timer timerVmShutDown;
    SpinnerDialog dialog;
    ComputerDetails myComputerDetails, saveComputerDeatails;
    TextView text_PcName, timerText, searchPC, tvTimer;
    Button btnStartVM, btnShutDownVM;
    ProgressBar progressBar, loadingBar;
    String startVmValue = "", connectbtnVisible, time_remaining, startVmCallCount, accessToken, strVMId, status, vmip;
    private PcGridAdapter pcGridAdapter;
    private ShortcutHelper shortcutHelper;
    private Thread addThread;
    RetrofitAPI retrofitAPI;
    ImageView ivRefresh;

    private WebSocketClient webSocketClient;
    private ComputerManagerService.ComputerManagerBinder managerBinder;
    private final LinkedBlockingQueue<String> computersToAdd = new LinkedBlockingQueue<>();
    private boolean freezeUpdates, runningPolling, inForeground, completeOnCreateCalled;
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder binder) {
            final ComputerManagerService.ComputerManagerBinder localBinder = ((ComputerManagerService.ComputerManagerBinder) binder);

            // Wait in a separate thread to avoid stalling the UI
            new Thread() {
                @Override
                public void run() {
                    // Wait for the binder to be ready
                    localBinder.waitForReady();
                    managerBinder = localBinder;
                    startComputerUpdates();
                    new AndroidCryptoProvider(PcView.this).getClientCertificate();
                }
            }.start();
        }
        public void onServiceDisconnected(ComponentName className) {
            managerBinder = null;
        }
    };
    private final ServiceConnection serviceConnection2 = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, final IBinder binder) {
            managerBinder = ((ComputerManagerService.ComputerManagerBinder) binder);
            startAddThread();
        }

        public void onServiceDisconnected(ComponentName className) {
            joinAddThread();
            managerBinder = null;
        }
    };

    private boolean isWrongSubnetSiteLocalAddress(String address) {
        try {
            InetAddress targetAddress = InetAddress.getByName(address);
            if (!(targetAddress instanceof Inet4Address) || !targetAddress.isSiteLocalAddress()) {
                return false;
            }

            // We have a site-local address. Look for a matching local interface.
            for (NetworkInterface iface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (InterfaceAddress addr : iface.getInterfaceAddresses()) {
                    if (!(addr.getAddress() instanceof Inet4Address) || !addr.getAddress().isSiteLocalAddress()) {
                        // Skip non-site-local or non-IPv4 addresses
                        continue;
                    }

                    byte[] targetAddrBytes = targetAddress.getAddress();
                    byte[] ifaceAddrBytes = addr.getAddress().getAddress();

                    // Compare prefix to ensure it's the same
                    boolean addressMatches = true;
                    for (int i = 0; i < addr.getNetworkPrefixLength(); i++) {
                        if ((ifaceAddrBytes[i / 8] & (1 << (i % 8))) != (targetAddrBytes[i / 8] & (1 << (i % 8)))) {
                            addressMatches = false;
                            break;
                        }
                    }

                    if (addressMatches) {
                        return false;
                    }
                }
            }

            // Couldn't find a matching interface
            return true;
        } catch (Exception e) {
            // Catch all exceptions because some broken Android devices
            // will throw an NPE from inside getNetworkInterfaces().
            e.printStackTrace();
            return false;
        }
    }

    private URI parseRawUserInputToUri(String rawUserInput) {
        try {
            // Try adding a scheme and parsing the remaining input.
            // This handles input like 127.0.0.1:47989, [::1], [::1]:47989, and 127.0.0.1.
            URI uri = new URI("moonlight://" + rawUserInput);
            if (uri.getHost() != null && !uri.getHost().isEmpty()) {
                return uri;
            }
        } catch (URISyntaxException ignored) {
        }

        try {
            // Attempt to escape the input as an IPv6 literal.
            // This handles input like ::1.
            URI uri = new URI("moonlight://[" + rawUserInput + "]");
            if (uri.getHost() != null && !uri.getHost().isEmpty()) {
                return uri;
            }
        } catch (URISyntaxException ignored) {
        }
        return null;
    }

    private void doAddPc(String rawUserInput) throws InterruptedException {
        boolean wrongSiteLocal = false;
        boolean invalidInput = false;
        boolean success;
        int portTestResult;

        if (startVmValue.equalsIgnoreCase("")) {
//            dialog = SpinnerDialog.displayDialog(this, getResources().getString(R.string.title_add_pc),
//                    getResources().getString(R.string.msg_add_pc), false);
        }

        try {
            ComputerDetails details = new ComputerDetails();

            // Check if we parsed a host address successfully
            URI uri = parseRawUserInputToUri(rawUserInput);
            if (uri != null && uri.getHost() != null && !uri.getHost().isEmpty()) {
                String host = uri.getHost();
                int port = uri.getPort();

                // If a port was not specified, use the default
                if (port == -1) {
                    port = NvHTTP.DEFAULT_HTTP_PORT;
                }

                details.manualAddress = new ComputerDetails.AddressTuple(host, port);
                success = managerBinder.addComputerBlocking(details);
                if (!success) {
                    wrongSiteLocal = isWrongSubnetSiteLocalAddress(host);
                }
            } else {
                // Invalid user input
                success = false;
                invalidInput = true;
            }
        } catch (InterruptedException e) {
            if (startVmValue.equalsIgnoreCase("")) {
//                dialog.dismiss();
            }
            throw e;
        } catch (IllegalArgumentException e) {
            // This can be thrown from OkHttp if the host fails to canonicalize to a valid name.
            // https://github.com/square/okhttp/blob/okhttp_27/okhttp/src/main/java/com/squareup/okhttp/HttpUrl.java#L705
            e.printStackTrace();
            success = false;
            invalidInput = true;
        }

        // Keep the SpinnerDialog open while testing connectivity
        if (!success && !wrongSiteLocal && !invalidInput) {
            // Run the test before dismissing the spinner because it can take a few seconds.
            portTestResult = MoonBridge.testClientConnectivity(ServerHelper.CONNECTION_TEST_SERVER, 443,
                    MoonBridge.ML_PORT_FLAG_TCP_47984 | MoonBridge.ML_PORT_FLAG_TCP_47989);
        } else {
            // Don't bother with the test if we succeeded or the IP address was bogus
            portTestResult = MoonBridge.ML_TEST_RESULT_INCONCLUSIVE;
        }

        if (invalidInput) {
            MyDialog.displayDialog(this, getResources().getString(R.string.conn_error_title), getResources().getString(R.string.addpc_unknown_host), false);
        } else if (wrongSiteLocal) {
            MyDialog.displayDialog(this, getResources().getString(R.string.conn_error_title), getResources().getString(R.string.addpc_wrong_sitelocal), false);
        } else if (!success) {
            String dialogText;
            if (portTestResult != MoonBridge.ML_TEST_RESULT_INCONCLUSIVE && portTestResult != 0) {
                dialogText = getResources().getString(R.string.nettest_text_blocked);
            } else {
                // dialogText = getResources().getString(R.string.addpc_fail);
            }
            // MyDialog.displayDialog(this, getResources().getString(R.string.conn_error_title), dialogText, false);
        } else {
            PcView.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    //   Toast.makeText(PcView.this, getResources().getString(R.string.addpc_success), Toast.LENGTH_LONG).show();

                    completeOnCreate();


                    // if (!isFinishing()) {

                    // Close the activity
                    //AddComputerManually.this.finish();
                    //  }
                }
            });
        }

    }

    private void startAddThread() {
        addThread = new Thread() {
            @Override
            public void run() {
                while (!isInterrupted()) {
                    try {
                        String computer = computersToAdd.take();
                        doAddPc(computer);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }
        };
        addThread.setName("UI - AddComputerManually");
        addThread.start();
    }

    private void joinAddThread() {
        if (addThread != null) {
            addThread.interrupt();

            try {
                addThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();

                // InterruptedException clears the thread's interrupt status. Since we can't
                // handle that here, we will re-interrupt the thread to set the interrupt
                // status back to true.
                Thread.currentThread().interrupt();
            }

            addThread = null;
        }
    }


    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // Only reinitialize views if completeOnCreate() was called
        // before this callback. If it was not, completeOnCreate() will
        // handle initializing views with the config change accounted for.
        // This is not prone to races because both callbacks are invoked
        // in the main thread.
        if (completeOnCreateCalled) {
            // Reinitialize views just in case orientation changed
            initializeViews();
        }
    }

    private final static int PAIR_ID = 2;
    private final static int UNPAIR_ID = 3;
    private final static int WOL_ID = 4;
    private final static int DELETE_ID = 5;
    private final static int RESUME_ID = 6;
    private final static int QUIT_ID = 7;
    private final static int VIEW_DETAILS_ID = 8;
    private final static int FULL_APP_LIST_ID = 9;
    private final static int TEST_NETWORK_ID = 10;
    private final static int GAMESTREAM_EOL_ID = 11;

    private void initializeViews() {
        setContentView(R.layout.activity_pc_view);
        UiHelper.notifyNewRootView(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setShouldDockBigOverlays(false);
        }
//        Intent intent = new Intent(getBaseContext(), ClearService.class);
////        intent.putExtra("MyService.data", "myValue");
//        startService(intent);

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        pcGridAdapter.updateLayoutWithPreferences(this, PreferenceConfiguration.readPreferences(this));
        retrofitAPI = APIClient.getRetrofitInstance().create(RetrofitAPI.class);
        accessToken = SharedPreferenceUtils.getString(PcView.this, Const.ACCESS_TOKEN);
        btnStatus = SharedPreferenceUtils.getBoolean(PcView.this, Const.STARTBtnStatus);
        btnStatus = SharedPreferenceUtils.getBoolean(PcView.this, Const.STARTBtnStatus);
        isVMConnected = SharedPreferenceUtils.getBoolean(PcView.this, Const.IS_VM_CONNECTED);
        isVmDisConnected = SharedPreferenceUtils.getBoolean(PcView.this, Const.IS_VM_DISCONNECTED);
        connectbtnVisible = SharedPreferenceUtils.getString(PcView.this, Const.connectbtnVisible);
        firstTimeVMTimer = SharedPreferenceUtils.getBoolean(PcView.this, Const.FIRSTTIMEVMTIMER);


        ivRefresh = findViewById(R.id.ivRefresh);
        searchPC = findViewById(R.id.searchPC);
        tvTimer = findViewById(R.id.tvTimer);
        text_PcName = findViewById(R.id.text_PcName);
        progressBar = findViewById(R.id.progressBar);
        loadingBar = findViewById(R.id.loadingBar);
        btnStartVM = findViewById(R.id.btnStartVM);
        btnShutDownVM = findViewById(R.id.btnShutDownVM);
        progressBar = findViewById(R.id.progressBar);
        ImageButton profileButton = findViewById(R.id.profileButton);
        ImageView settingsButton = findViewById(R.id.settingsButton);
        ImageButton addComputerButton = findViewById(R.id.manuallyAddPc);
        ImageButton helpButton = findViewById(R.id.helpButton);
        SwipeRefreshLayout swipeLayout = findViewById(R.id.refreshLayout);
        swipeLayout.setOnRefreshListener(() -> {
            new Handler().postDelayed(() -> {
                swipeLayout.setRefreshing(false);
               // getVMInitiallyCall();
               getVM("");
            }, 1000);
        });
        swipeLayout.setColorSchemeColors(getResources().getColor(R.color.teal_700));
        startVMStatus = SharedPreferenceUtils.getBoolean(PcView.this, Const.IS_STARTVM);
        shutdownVMStatus = SharedPreferenceUtils.getBoolean(PcView.this, Const.IS_SHUT_DOWN);
        firstTimeStartVmApi = SharedPreferenceUtils.getBoolean(PcView.this, Const.FIRSTTIMESTARTVMAPI);


        btnStartVM.setOnClickListener(view -> {
            String btnText = btnStartVM.getText().toString();
            if (btnText.equalsIgnoreCase("start")) {
                boolean firstTimeDialog = SharedPreferenceUtils.getBoolean(PcView.this, Const.FIRSTTIMEDIALOG);
                if (firstTimeDialog) {
                    startVm(strVMId);
                } else {
                    btnStatus = true;
                    btnShutDownStatus = false;
                    SharedPreferenceUtils.saveBoolean(PcView.this, Const.STARTBtnStatus, true);
                    startVm(strVMId);
                }
            }
            else if (btnText.equalsIgnoreCase("connect")) {
                btnStartVM.setClickable(false);
                doPair(myComputerDetails);
            }
        });

        btnShutDownVM.setOnClickListener(view -> {
            if (status.equalsIgnoreCase("running")) {
                openShutDownVMDialog("shutdown", 0L);
                btnShutDownStatus = true;
                if (vmip != "")
                    shutDownVM("", strVMId);
                else
                    Toast.makeText(PcView.this, "ip is" + vmip, Toast.LENGTH_LONG).show();
                // stopVM("" ,strVMId);
            }
        });
        ivRefresh.setOnClickListener(view -> getVM(""));
        profileButton.setOnClickListener(v -> startActivity(new Intent(PcView.this, ProfileActivity.class)));

        settingsButton.setOnClickListener(v -> startActivity(new Intent(PcView.this, StreamSettings.class)));
        addComputerButton.setOnClickListener(v -> {
            Intent i = new Intent(PcView.this, AddComputerManually.class);
            startActivity(i);
        });
        helpButton.setOnClickListener(v -> HelpLauncher.launchSetupGuide(PcView.this));

        // Amazon review didn't like the help button because the wiki was not entirely
        // navigable via the Fire TV remote (though the relevant parts were). Let's hide
        // it on Fire TV.
        if (getPackageManager().hasSystemFeature("amazon.hardware.fire_tv")) {
            helpButton.setVisibility(View.GONE);
        }

        getFragmentManager().beginTransaction().replace(R.id.pcFragmentContainer, new AdapterFragment()).commitAllowingStateLoss();

        noPcFoundLayout = findViewById(R.id.no_pc_found_layout);
        if (pcGridAdapter.getCount() == 0) {
            noPcFoundLayout.setVisibility(View.VISIBLE);
        } else {
            noPcFoundLayout.setVisibility(View.INVISIBLE);
        }
        // pcGridAdapter.notifyDataSetChanged();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        retrofitAPI = APIClient.getRetrofitInstance().create(RetrofitAPI.class);
        accessToken = SharedPreferenceUtils.getString(PcView.this, Const.ACCESS_TOKEN);
        firstTimeStartVmApi = SharedPreferenceUtils.getBoolean(PcView.this, Const.FIRSTTIMESTARTVMAPI);
        String email = SharedPreferenceUtils.getString(PcView.this, Const.EMAIL_ID);
        btnStatus = SharedPreferenceUtils.getBoolean(PcView.this, Const.STARTBtnStatus);
        String loginEmail = SharedPreferenceUtils.getString(PcView.this, Const.LOGIN_EMAIL);
        if (email == null || !email.equalsIgnoreCase(loginEmail)) {
            SharedPreferenceUtils.saveString(PcView.this, Const.EMAIL_ID, loginEmail);
            SharedPreferenceUtils.saveBoolean(PcView.this, Const.FIRSTTIMEVMTIMER, false);
        }
        inForeground = true;
        firstTimeVMTimer = SharedPreferenceUtils.getBoolean(PcView.this, Const.FIRSTTIMEVMTIMER);
        Log.i("testtt_firsttime" , ""+firstTimeVMTimer);
        if (!firstTimeVMTimer) {
            SharedPreferenceUtils.saveString(PcView.this, Const.EMAIL_ID, loginEmail);
            if (AppUtils.isOnline(PcView.this))
                getVM("");
            else
                AppUtils.showInternetDialog(PcView.this);
        }
        else {
            if(btnStatus) {
                getVMInitiallyCall();
            }
            else {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    Long time = timeDifference();
                    if (time == null || time > 1200) {
                        SharedPreferenceUtils.saveBoolean(PcView.this, Const.FIRSTTIMEVMTIMER, false);
                        SharedPreferenceUtils.saveBoolean(PcView.this, Const.FIRSTTIMEDIALOG, false);
                        firstTimeDialog = false;
                      getVMInitiallyCall();
                        getVM("");
                    } else{
                        openDialog(true,getResources().getString(R.string.startVMMsg),"startSocket");
                        getVMInitiallyCall();

                    }

                    //openShutDownVMDialog("vmtimer", 1200 - time);
                }
            }
        }

        tvTimer = findViewById(R.id.tvTimer);
        isVmDisConnected = SharedPreferenceUtils.getBoolean(PcView.this, Const.IS_VM_DISCONNECTED);
        if (isVmDisConnected) {
            timerVmShutDown = new Timer();
            timerVmShutDown.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    Log.i("test_background", "testttt");
                    if (!isFirstTime) {
                        if (AppUtils.isOnline(PcView.this))
                            getVMForShutDown();
                        else
                            AppUtils.showInternetDialog(PcView.this);
                    } else {
                        isFirstTime = false;
                    }
                }
            }, 0, 400 * 1000);
        }



        // Create a GLSurfaceView to fetch GLRenderer unless we have
        // a cached result already.
        final GlPreferences glPrefs = GlPreferences.readPreferences(this);
        if (!glPrefs.savedFingerprint.equals(Build.FINGERPRINT) || glPrefs.glRenderer.isEmpty()) {
            GLSurfaceView surfaceView = new GLSurfaceView(this);
            surfaceView.setRenderer(new GLSurfaceView.Renderer() {
                @Override
                public void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig) {
                    // Save the GLRenderer string so we don't need to do this next time
                    glPrefs.glRenderer = gl10.glGetString(GL10.GL_RENDERER);
                    glPrefs.savedFingerprint = Build.FINGERPRINT;
                    glPrefs.writePreferences();

                    LimeLog.info("Fetched GL Renderer: " + glPrefs.glRenderer);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            completeOnCreate();
                        }
                    });
                }

                @Override
                public void onSurfaceChanged(GL10 gl10, int i, int i1) {
                }

                @Override
                public void onDrawFrame(GL10 gl10) {
                }
            });
            setContentView(surfaceView);

        } else {
            LimeLog.info("Cached GL Renderer: " + glPrefs.glRenderer);
            completeOnCreate();
        }
    }

    private void completeOnCreate() {
        completeOnCreateCalled = true;
        shortcutHelper = new ShortcutHelper(this);
        UiHelper.setLocale(this);

        // Bind to the computer manager service
        bindService(new Intent(PcView.this, ComputerManagerService.class), serviceConnection, Service.BIND_AUTO_CREATE);
        pcGridAdapter = new PcGridAdapter(this, PreferenceConfiguration.readPreferences(this));
        initializeViews();
    }

    private void startComputerUpdates() {
        try {
            ComputerDetails computerDetails = SharedPreferenceUtils.getObject(PcView.this, Const.SAVE_DETAILS);
            if (computerDetails != null)
                removeComputer(computerDetails);
        } catch (Exception e) {
        }

        if (managerBinder != null && !runningPolling && inForeground) {
            freezeUpdates = false;
            managerBinder.startPolling(details -> {
                if (!freezeUpdates) {
                    PcView.this.runOnUiThread(() -> {

                        if (details.manualAddress != null) {
                            Log.i("test_id", details.manualAddress + " " + details.state);
                            saveComputerDeatails = details;
                            SharedPreferenceUtils.saveObject(PcView.this, Const.SAVE_DETAILS, saveComputerDeatails);
                            progressBar.setVisibility(View.GONE);
                            updateComputer(details, time_remaining);
                        }
                    });
                }
            });
            runningPolling = true;
        }
    }

    private void stopComputerUpdates(boolean wait) {
        if (managerBinder != null) {
            if (!runningPolling) {
                return;
            }

            freezeUpdates = true;
            managerBinder.stopPolling();
            if (wait) {
                managerBinder.waitForPollingStopped();
            }

            runningPolling = false;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            if (managerBinder != null) {
                unbindService(serviceConnection);
                joinAddThread();
                unbindService(serviceConnection2);

            }
        } catch (Exception e) {
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i("test_onResumee", "testOnResume");
//        boolean firstTimePayment = SharedPreferenceUtils.getBoolean(PcView.this , Const.FIRST_TIME_PAYMENT);
//        if(firstTimePayment){
//            getVM("");
//       }


        UiHelper.showDecoderCrashDialog(this);
        inForeground = true;
        startComputerUpdates();

    }

    @Override
    protected void onPause() {
        super.onPause();

        inForeground = false;
        stopComputerUpdates(false);
    }

    @Override
    protected void onStop() {
        super.onStop();
        MyDialog.closeDialogs();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        stopComputerUpdates(false);

        // Call superclass
        super.onCreateContextMenu(menu, v, menuInfo);

        AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
        ComputerObject computer = (ComputerObject) pcGridAdapter.getItem(info.position);

        // Add a header with PC status details
        menu.clearHeader();
        String headerTitle = computer.details.name + " - ";
        switch (computer.details.state) {
            case ONLINE:
                headerTitle += getResources().getString(R.string.pcview_menu_header_online);
                break;
            case OFFLINE:
                menu.setHeaderIcon(R.drawable.ic_pc_offline);
                headerTitle += getResources().getString(R.string.pcview_menu_header_offline);
                break;
            case UNKNOWN:
                headerTitle += getResources().getString(R.string.pcview_menu_header_unknown);
                break;
        }

        menu.setHeaderTitle(headerTitle);

        // Inflate the context menu
        if (computer.details.state == ComputerDetails.State.OFFLINE || computer.details.state == ComputerDetails.State.UNKNOWN) {
            menu.add(Menu.NONE, WOL_ID, 1, getResources().getString(R.string.pcview_menu_send_wol));
            menu.add(Menu.NONE, GAMESTREAM_EOL_ID, 2, getResources().getString(R.string.pcview_menu_eol));
        } else if (computer.details.pairState != PairState.PAIRED) {
            menu.add(Menu.NONE, PAIR_ID, 1, getResources().getString(R.string.pcview_menu_pair_pc));
            if (computer.details.nvidiaServer) {
                menu.add(Menu.NONE, GAMESTREAM_EOL_ID, 2, getResources().getString(R.string.pcview_menu_eol));
            }
        } else {
            if (computer.details.runningGameId != 0) {
                menu.add(Menu.NONE, RESUME_ID, 1, getResources().getString(R.string.applist_menu_resume));
                menu.add(Menu.NONE, QUIT_ID, 2, getResources().getString(R.string.applist_menu_quit));
            }

            if (computer.details.nvidiaServer) {
                menu.add(Menu.NONE, GAMESTREAM_EOL_ID, 3, getResources().getString(R.string.pcview_menu_eol));
            }

            menu.add(Menu.NONE, FULL_APP_LIST_ID, 4, getResources().getString(R.string.pcview_menu_app_list));
        }

        menu.add(Menu.NONE, TEST_NETWORK_ID, 5, getResources().getString(R.string.pcview_menu_test_network));
        menu.add(Menu.NONE, DELETE_ID, 6, getResources().getString(R.string.pcview_menu_delete_pc));
        menu.add(Menu.NONE, VIEW_DETAILS_ID, 7, getResources().getString(R.string.pcview_menu_details));
    }

    @Override
    public void onContextMenuClosed(Menu menu) {
        // For some reason, this gets called again _after_ onPause() is called on this activity.
        // startComputerUpdates() manages this and won't actual start polling until the activity
        // returns to the foreground.
        startComputerUpdates();
    }

    private void doPair(final ComputerDetails computer) {
        if (computer.state == ComputerDetails.State.OFFLINE || computer.activeAddress == null) {
            Toast.makeText(PcView.this, getResources().getString(R.string.pair_pc_offline), Toast.LENGTH_SHORT).show();
            return;
        }
        if (managerBinder == null) {
            Toast.makeText(PcView.this, getResources().getString(R.string.error_manager_not_running), Toast.LENGTH_LONG).show();
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                NvHTTP httpConn;
                String message;
                boolean success = false;
                try {
                    // Stop updates and wait while pairing
                    stopComputerUpdates(true);
                    httpConn = new NvHTTP(ServerHelper.getCurrentAddressFromComputer(computer), computer.httpsPort, managerBinder.getUniqueId(), computer.serverCert, PlatformBinding.getCryptoProvider(PcView.this));
                    if (httpConn.getPairState() == PairState.PAIRED) {
                        // Don't display any toast, but open the app list
                        message = null;
                        success = true;
                    } else {
                        final String pinStr = PairingManager.generatePinString();
                        sendAndVerifySecurityPinManually(pinStr);
                        PairingManager pm = httpConn.getPairingManager();

                        PairState pairState = pm.pair(httpConn.getServerInfo(true), pinStr);
                        if (pairState == PairState.PIN_WRONG) {
                            message = getResources().getString(R.string.pair_incorrect_pin);
                        } else if (pairState == PairState.FAILED) {
                            if (computer.runningGameId != 0) {
                                message = getResources().getString(R.string.pair_pc_ingame);
                            } else {
                                message = getResources().getString(R.string.pair_fail);
                            }
                        } else if (pairState == PairState.ALREADY_IN_PROGRESS) {
                            message = getResources().getString(R.string.pair_already_in_progress);
                        } else if (pairState == PairState.PAIRED) {
                            // Just navigate to the app view without displaying a toast
                            message = null;
                            success = true;
                            try {
                                managerBinder.getComputer(computer.uuid).serverCert = pm.getPairedCert();
                                managerBinder.invalidateStateForComputer(computer.uuid);
                            } catch (Exception e) {
                                startActivity(getIntent());
                            }
                        } else {
                            message = null;
                        }
                    }
                } catch (UnknownHostException e) {
                    message = getResources().getString(R.string.error_unknown_host);
                } catch (FileNotFoundException e) {
                    message = getResources().getString(R.string.error_404);
                } catch (XmlPullParserException | IOException e) {
                    e.printStackTrace();
                    message = e.getMessage();
                }
                final String toastMessage = message;
                final boolean toastSuccess = success;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {

                        if (toastMessage != null) {
                            btnStartVM.setClickable(true);
                            Toast.makeText(PcView.this, toastMessage, Toast.LENGTH_LONG).show();
                        }
                        if (toastSuccess) {
                            doAppList(computer, true, false);
                        } else {
                            btnStartVM.setClickable(true);
                            startActivity(getIntent());
//                            startComputerUpdates();
                        }
                    }
                });
            }
        }).start();
    }

    String TAG = "ANT_PLAY";

    private void sendAndVerifySecurityPinManually(String pinStr) {
        HashMap<String, String> pinMap = new HashMap<>();
        pinMap.put("pin", pinStr);
        new RestClient(PcView.this).postRequestWithHeader("update_pin", "vmauth", pinMap, accessToken, "", new RestClient.ResponseListener() {
            @Override
            public void onResponse(String tag, String response) {
                if (response != null) {
                    try {
                        JSONObject jsonObject = new JSONObject(response);
                        String status = jsonObject.getJSONObject("data").getString("status");
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        }, (tag, errorMsg, statusCode) -> Log.d("ANT_PLAY", "Message : " + errorMsg));
    }

    private void doWakeOnLan(final ComputerDetails computer) {
        if (computer.state == ComputerDetails.State.ONLINE) {
            Toast.makeText(PcView.this, getResources().getString(R.string.wol_pc_online), Toast.LENGTH_SHORT).show();
            return;
        }

        if (computer.macAddress == null) {
            Toast.makeText(PcView.this, getResources().getString(R.string.wol_no_mac), Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                String message;
                try {
                    WakeOnLanSender.sendWolPacket(computer);
                    message = getResources().getString(R.string.wol_waking_msg);
                } catch (IOException e) {
                    message = getResources().getString(R.string.wol_fail);
                }

                final String toastMessage = message;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(PcView.this, toastMessage, Toast.LENGTH_LONG).show();
                    }
                });
            }
        }).start();
    }

    private void doUnpair(final ComputerDetails computer) {
        if (computer.state == ComputerDetails.State.OFFLINE || computer.activeAddress == null) {
            Toast.makeText(PcView.this, getResources().getString(R.string.error_pc_offline), Toast.LENGTH_SHORT).show();
            return;
        }
        if (managerBinder == null) {
            Toast.makeText(PcView.this, getResources().getString(R.string.error_manager_not_running), Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(PcView.this, getResources().getString(R.string.unpairing), Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                NvHTTP httpConn;
                String message;
                try {
                    httpConn = new NvHTTP(ServerHelper.getCurrentAddressFromComputer(computer), computer.httpsPort, managerBinder.getUniqueId(), computer.serverCert, PlatformBinding.getCryptoProvider(PcView.this));
                    if (httpConn.getPairState() == PairingManager.PairState.PAIRED) {
                        httpConn.unpair();
                        if (httpConn.getPairState() == PairingManager.PairState.NOT_PAIRED) {
                            message = getResources().getString(R.string.unpair_success);
                        } else {
                            message = getResources().getString(R.string.unpair_fail);
                        }
                    } else {
                        message = getResources().getString(R.string.unpair_error);
                    }
                } catch (UnknownHostException e) {
                    message = getResources().getString(R.string.error_unknown_host);
                } catch (FileNotFoundException e) {
                    message = getResources().getString(R.string.error_404);
                } catch (XmlPullParserException | IOException e) {
                    message = e.getMessage();
                    e.printStackTrace();
                }

                final String toastMessage = message;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(PcView.this, toastMessage, Toast.LENGTH_LONG).show();
                    }
                });
            }
        }).start();
    }

    private void doAppList(ComputerDetails computer, boolean newlyPaired, boolean showHiddenGames) {
        if (computer.state == ComputerDetails.State.OFFLINE) {
            Toast.makeText(PcView.this, getResources().getString(R.string.error_pc_offline), Toast.LENGTH_SHORT).show();
            return;
        }
        if (managerBinder == null) {
            Toast.makeText(PcView.this, getResources().getString(R.string.error_manager_not_running), Toast.LENGTH_LONG).show();
            return;
        }

        Intent i = new Intent(this, AppView.class);
        i.putExtra(AppView.NAME_EXTRA, computer.name);
        i.putExtra(AppView.UUID_EXTRA, computer.uuid);
        i.putExtra(AppView.NEW_PAIR_EXTRA, newlyPaired);
        i.putExtra(AppView.SHOW_HIDDEN_APPS_EXTRA, showHiddenGames);
        startActivity(i);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
        final ComputerObject computer = (ComputerObject) pcGridAdapter.getItem(info.position);
        switch (item.getItemId()) {
            case PAIR_ID:
                doPair(computer.details);
                return true;

            case UNPAIR_ID:
                doUnpair(computer.details);
                return true;

            case WOL_ID:
                doWakeOnLan(computer.details);
                return true;

            case DELETE_ID:
                if (ActivityManager.isUserAMonkey()) {
                    LimeLog.info("Ignoring delete PC request from monkey");
                    return true;
                }
                UiHelper.displayDeletePcConfirmationDialog(this, computer.details, new Runnable() {
                    @Override
                    public void run() {
                        if (managerBinder == null) {
                            Toast.makeText(PcView.this, getResources().getString(R.string.error_manager_not_running), Toast.LENGTH_LONG).show();
                            return;
                        }
                        removeComputer(computer.details);
                    }
                }, null);
                return true;

            case FULL_APP_LIST_ID:
                doAppList(computer.details, false, true);
                return true;

            case RESUME_ID:
                if (managerBinder == null) {
                    Toast.makeText(PcView.this, getResources().getString(R.string.error_manager_not_running), Toast.LENGTH_LONG).show();
                    return true;
                }

                ServerHelper.doStart(this, new NvApp("app", computer.details.runningGameId, false), computer.details, managerBinder);
                return true;

            case QUIT_ID:
                if (managerBinder == null) {
                    Toast.makeText(PcView.this, getResources().getString(R.string.error_manager_not_running), Toast.LENGTH_LONG).show();
                    return true;
                }

                // Display a confirmation dialog first
                UiHelper.displayQuitConfirmationDialog(this, new Runnable() {
                    @Override
                    public void run() {
                        ServerHelper.doQuit(PcView.this, computer.details, new NvApp("app", 0, false), managerBinder, null);
                    }
                }, null);
                return true;

            case VIEW_DETAILS_ID:
                MyDialog.displayDialog(PcView.this, getResources().getString(R.string.title_details), computer.details.toString(), false);
                return true;

            case TEST_NETWORK_ID:
                ServerHelper.doNetworkTest(PcView.this);
                return true;

            case GAMESTREAM_EOL_ID:
                HelpLauncher.launchGameStreamEolFaq(PcView.this);
                return true;

            default:
                return super.onContextItemSelected(item);
        }
    }

    private void removeComputer(ComputerDetails details) {
        managerBinder.removeComputer(details);
        new DiskAssetLoader(this).deleteAssetsForComputer(details.uuid);

        // Delete hidden games preference value
        getSharedPreferences(AppView.HIDDEN_APPS_PREF_FILENAME, MODE_PRIVATE).edit().remove(details.uuid).apply();

        for (int i = 0; i < pcGridAdapter.getCount(); i++) {
            ComputerObject computer = (ComputerObject) pcGridAdapter.getItem(i);
            if (details.equals(computer.details)) {
                // Disable or delete shortcuts referencing this PC
                shortcutHelper.disableComputerShortcut(details, getResources().getString(R.string.scut_deleted_pc));
                pcGridAdapter.removeComputer(computer);
                pcGridAdapter.notifyDataSetChanged();
                if (pcGridAdapter.getCount() == 0) {
                    noPcFoundLayout.setVisibility(View.VISIBLE);
                }
                break;
            }
        }
    }

    private void updateComputer(ComputerDetails details, String value) {
        showTimer(value);
        myComputerDetails = details;
        text_PcName.setText("" + details.name);
        if (status != null) {
            if (status.equalsIgnoreCase("running")) {
                try {
                    if (mDialog.isShowing())
                        mDialog.dismiss();
                }
                catch (Exception e){

                }

                if (!btnShutDownStatus)
                    btnStartVM.setVisibility(View.VISIBLE);
                else
                    btnStartVM.setVisibility(View.INVISIBLE);
                btnShutDownVM.setVisibility(View.VISIBLE);
                if (details.state == ComputerDetails.State.OFFLINE || details.activeAddress == null) {
                    btnStartVM.setBackground(getResources().getDrawable(R.drawable.btngradient_grey));
                    btnStartVM.setClickable(false);
                } else {
                    btnStartVM.setBackground(getResources().getDrawable(R.drawable.btngradient_white));
                    btnStartVM.setClickable(true);
                }
                text_PcName.setVisibility(View.VISIBLE);
                btnStartVM.setText("Connect");
                btnShutDownVM.setText("Shutdown");
                progressBar.setVisibility(View.GONE);
                ivRefresh.setVisibility(View.INVISIBLE);
            } else if (status.equalsIgnoreCase("stopped")) {
                progressBar.setVisibility(View.GONE);
                btnStartVM.setText("Start");
                btnShutDownVM.setVisibility(View.INVISIBLE);

            }
            ComputerObject existingEntry = null;
            for (int i = 0; i < pcGridAdapter.getCount(); i++) {
                ComputerObject computer = (ComputerObject) pcGridAdapter.getItem(i);
                if (details.uuid.equals(computer.details.uuid)) {
                    existingEntry = computer;
                    break;
                }
            }
            if (details.pairState == PairState.PAIRED) {
                shortcutHelper.createAppViewShortcutForOnlineHost(details);
            }
            if (existingEntry != null) {
            } else {
                pcGridAdapter.addComputer(new ComputerObject(details));
                noPcFoundLayout.setVisibility(View.INVISIBLE);
                try {
                    if (startVmValue.equalsIgnoreCase("")) {
                       // dialog.dismiss();
                    }
                } catch (Exception e) {
                }
            }
            pcGridAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public int getAdapterFragmentLayoutId() {
        return R.layout.pc_grid_view;
    }

    @Override
    public void receiveAbsListView(AbsListView listView) {
        listView.setAdapter(pcGridAdapter);
        listView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int pos, long id) {
                ComputerObject computer = (ComputerObject) pcGridAdapter.getItem(pos);
                if (computer.details.state == ComputerDetails.State.UNKNOWN || computer.details.state == ComputerDetails.State.OFFLINE) {
                    // Open the context menu if a PC is offline or refreshing
                    openContextMenu(arg1);
                } else if (computer.details.pairState != PairState.PAIRED) {
                    // Pair an unpaired machine by default
                    doPair(computer.details);
                } else {
                    doAppList(computer.details, false, false);
                }
            }
        });
        UiHelper.applyStatusBarPadding(listView);
        registerForContextMenu(listView);
    }

    public static class ComputerObject {
        public ComputerDetails details;

        public ComputerObject(ComputerDetails details) {
            if (details == null) {
                throw new IllegalArgumentException("details must not be null");
            }
            this.details = details;
        }

        @Override
        public String toString() {
            return details.name;
        }
    }

    private boolean handleDoneEvent(String vmIp, String startVM) {
        if (vmIp.length() == 0) {
            Toast.makeText(PcView.this, getResources().getString(R.string.addpc_enter_ip), Toast.LENGTH_LONG).show();
            return true;
        }
        startVmValue = startVM;
        computersToAdd.add(vmIp);
        return false;
    }

    private void startVm(String strVMId) {
        loadingBar.setVisibility(View.VISIBLE);
        VMTimerReq vmTimerReq = new VMTimerReq(strVMId);
        Call<MessageResponse> call = retrofitAPI.startVm("Bearer " + accessToken, vmTimerReq);
        call.enqueue(new Callback<>() {
            @Override
            public void onResponse(Call<MessageResponse> call, Response<MessageResponse> response) {
                loadingBar.setVisibility(View.GONE);
                if (response.code() == Const.SUCCESS_CODE_200) {
                    btnStartVM.setVisibility(View.INVISIBLE);
                    if (response.body().getSuccess().equalsIgnoreCase("true")) {
                        boolean firstTimeDialog = SharedPreferenceUtils.getBoolean(PcView.this, Const.FIRSTTIMEDIALOG);

                        if (startVmCallCount.equalsIgnoreCase("0") || firstTimeDialog) {
                            btnStartVM.setVisibility(View.GONE);
                            SharedPreferenceUtils.saveBoolean(PcView.this, Const.FIRST_TIME_PAYMENT, false);
                            firstTimeVMTimer = true;
                            SharedPreferenceUtils.saveBoolean(PcView.this, Const.FIRSTTIMEVMTIMER, true);
                            saveTime();
                            openDialog(true,getResources().getString(R.string.startVMMsg),"startSocket");

                            getVMInitiallyCall();
//                            openShutDownVMDialog("vmtimer", 1200L);
                        } else if (btnStatus) {
                            openDialog(true,getResources().getString(R.string.startVMMsg),"startSocket");
                            firstTimeVMTimer = true;
                            SharedPreferenceUtils.saveBoolean(PcView.this, Const.FIRSTTIMEVMTIMER, true);
                            getVMInitiallyCall();
//                            connectWebSocket();
                           // openShutDownVMDialog("start", 0L);
                        }
                    }
                } else if (response.code() == 403) {
                    try {
                        JSONObject jObj = new JSONObject(response.errorBody().string());
                        String value = jObj.getString("message");
                        if (value.contains("Servers are full")) {
                            openDialog(true, value ,"");
                        }
                    } catch (Exception e) {

                    }

                } else if (response.code() == 401 || response.code() == Const.ERROR_CODE_404 ||
                        response.code() == Const.ERROR_CODE_400 || response.code() == Const.ERROR_CODE_500) {
                    try {
                        if (shutDownVMDialog.isShowing()) {
                            shutDownVMDialog.dismiss();
                        }
                        JSONObject jObj = new JSONObject(response.errorBody().string());
                        String value = jObj.getString("message");
                        Toast.makeText(PcView.this, "" + value, Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onFailure(Call<MessageResponse> call, Throwable t) {
                loadingBar.setVisibility(View.GONE);
                try {
                    if (shutDownVMDialog.isShowing()) {
                        shutDownVMDialog.dismiss();
                    }
                } catch (Exception e) {

                }
                //    AppUtils.showToast(Const.something_went_wrong, PcView.this);
            }
        });
    }

    private void getVM(String startVm) {
        try {
            Call<ResponseBody> call = retrofitAPI.getVMFromServer("Bearer " + accessToken);
            call.enqueue(new Callback<>() {
                @Override
                public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                    if (response.code() == Const.SUCCESS_CODE_200) {
                        try {
                            JSONObject jsonObject = new JSONObject(response.body().string());
                            JSONArray jsonArray = jsonObject.getJSONArray("data");
                            strVMId = jsonArray.getJSONObject(0).getString("vmid");
                            startVmCallCount = jsonArray.getJSONObject(0).getString("start_vm_call_count");
                            status = jsonArray.getJSONObject(0).getString("status");
                            vmip = jsonArray.getJSONObject(0).getString("vmip");
                            time_remaining = jsonArray.getJSONObject(0).getString("time_remaining");
                            SharedPreferenceUtils.saveString(PcView.this, Const.VMID, strVMId);

                            if (startVmCallCount.equalsIgnoreCase("0")) {
                                firstTimeDialog = true;
                                SharedPreferenceUtils.saveBoolean(PcView.this, Const.FIRSTTIMEDIALOG, true);
                                paymentStatus = SharedPreferenceUtils.getBoolean(PcView.this, Const.PAYMENT_STATUS);
                                if (!paymentStatus) {
                                    openPaymentSuccessDialog();
                                    paymentStatus = true;
                                    SharedPreferenceUtils.saveBoolean(PcView.this, Const.PAYMENT_STATUS, true);
                                } else {
                                    ivRefresh.setVisibility(View.GONE);
                                    btnStartVM.setVisibility(View.VISIBLE);
                                    btnShutDownVM.setVisibility(View.GONE);
                                    progressBar.setVisibility(View.GONE);
                                    btnStartVM.setText("Start");
                                    showTimer(time_remaining);
                                }
                            } else if (status.equalsIgnoreCase("running"))
                                getVMIP(time_remaining, startVm);
                            else {
                                ivRefresh.setVisibility(View.GONE);
                                btnStartVM.setVisibility(View.VISIBLE);
                                btnShutDownVM.setVisibility(View.GONE);
                                progressBar.setVisibility(View.GONE);
                                btnStartVM.setText("Start");
                                showTimer(time_remaining);
                            }
                        } catch (Exception e) {
                        }
                    } else if (response.code() == 401) {
                        try {
                            JSONObject jObj = new JSONObject(response.errorBody().string());
                            String code = jObj.getString("code");
                            if (code.equalsIgnoreCase("token_not_valid")) {
                                callRefreshAPi();
                            }

                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }


                    } else if (response.code() == 404 || response.code() == 500 || response.code() == 400) {
                        try {
                            JSONObject jObj = new JSONObject(response.errorBody().string());
                            String value = jObj.getString("message");
                            openDialog(false, value , "");
                        } catch (Exception e) {
                        }
                        try {

                            text_PcName.setText("");
                            tvTimer.setText("00:00:00 hrs.");
                            searchPC = findViewById(R.id.searchPC);
//                           searchPC.setText();
                            progressBar.setVisibility(View.GONE);

                        } catch (Exception e) {

                        }

                    }
                }

                @Override
                public void onFailure(Call<ResponseBody> call, Throwable t) {
                    //  AppUtils.showToast(Const.something_went_wrong, PcView.this);
                }
            });
        } catch (Exception e) {
        }
    }

    private void getVMIP(String timeRemaing, String startVm) {
        loadingBar.setVisibility(View.VISIBLE);
        Call<ResponseBody> call = retrofitAPI.getVMIP("Bearer " + accessToken);
        call.enqueue(new Callback<>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                loadingBar.setVisibility(View.GONE);
                if (response.code() == 200) {
                    try {
                        JSONObject jsonObject = new JSONObject(response.body().string());
                        String messageValue = jsonObject.getString("message");
                        if (messageValue.equalsIgnoreCase("success")) {
                            startVMStatus = false;
                            shutdownVMStatus = false;
                            SharedPreferenceUtils.saveBoolean(PcView.this, Const.IS_STARTVM, false);
                            SharedPreferenceUtils.saveBoolean(PcView.this, Const.IS_SHUT_DOWN, false);
                            JSONArray jsonArray = jsonObject.getJSONArray("data");
                            String vmIp = jsonArray.getJSONObject(0).getString("vmip");
                            startVmTimerStatus = false;
                            progressBar.setVisibility(View.GONE);
                            showTimer(time_remaining);
                            handleDoneEvent(vmIp, startVm);
                            bindService(new Intent(PcView.this, ComputerManagerService.class), serviceConnection2, Service.BIND_AUTO_CREATE);
                            pcGridAdapter = new PcGridAdapter(PcView.this, PreferenceConfiguration.readPreferences(PcView.this));
                            initializeViews();
                        }
                    } catch (Exception e) {
                    }
                } else if (response.code() == 400 || response.code() == 404 || response.code() == 500 || response.code() == 401) {
                    progressBar.setVisibility(View.GONE);
                    showTimer(timeRemaing);
                    if (status.equalsIgnoreCase("running")) {
                        if (!startVmTimerStatus) {
                            openDialog(true, getResources().getString(R.string.startVMMsg) ,"");
                            searchPC.setText(getResources().getString(R.string.startVMMsg));
                        } else
                            callTimer(timerText, 60, "start");
                    }
                }
            }


            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                // progressBar.setVisibility(View.GONE);
                AppUtils.showToast(Const.something_went_wrong, PcView.this);
            }
        });
    }

    private void shutDownVM(String status, String strVMId) {
        VMTimerReq vmTimerReq = new VMTimerReq(strVMId);
        Call<MessageResponse> call = retrofitAPI.shutDownVm("Bearer " + accessToken, vmTimerReq);
        call.enqueue(new Callback<>() {
            @Override
            public void onResponse(Call<MessageResponse> call, Response<MessageResponse> response) {
                if (response.code() == 200) {
                    btnStartVM.setVisibility(View.INVISIBLE);

                    if (status.equalsIgnoreCase("background")) {
                        if (timerVmShutDown != null) {
                            timerVmShutDown.cancel();
                            timerVmShutDown.purge();
                            timerVmShutDown = null;
                        }
                        shutdownVMStatus = true;
                        isVmDisConnected = false;
                        connectbtnVisible = "stopped";
                        btnStatus = false;
                        btnStartVM.setVisibility(View.VISIBLE);
                        btnShutDownVM.setVisibility(View.GONE);
                        btnStartVM.setText("Start");
                        SharedPreferenceUtils.saveBoolean(PcView.this, Const.IS_SHUT_DOWN, true);
                        SharedPreferenceUtils.saveBoolean(PcView.this, Const.IS_VM_DISCONNECTED, false);
                        SharedPreferenceUtils.saveString(PcView.this, Const.connectbtnVisible, "stopped");
                        SharedPreferenceUtils.saveBoolean(PcView.this, Const.STARTBtnStatus, false);
                        startActivity(getIntent());
                    }

                } else if (response.code() == 400 || response.code() == 404 ||
                        response.code() == 500 || response.code() == 401) {
                    try {
                        shutDownVMDialog.dismiss();
                    } catch (Exception e) {

                    }
                }
            }

            @Override
            public void onFailure(Call<MessageResponse> call, Throwable t) {
                AppUtils.showToast(Const.something_went_wrong, PcView.this);
            }
        });
    }

    private void stopVM(String status, String strVMId) {
        VMTimerReq vmTimerReq = new VMTimerReq(strVMId);
        Call<MessageResponse> call = retrofitAPI.stopVM("Bearer " + accessToken, vmTimerReq);
        call.enqueue(new Callback<>() {
            @Override
            public void onResponse(Call<MessageResponse> call, Response<MessageResponse> response) {
                if (response.code() == 200) {
                    btnStartVM.setVisibility(View.INVISIBLE);
                    if (status.equalsIgnoreCase("background")) {
                        if (timerVmShutDown != null) {
                            timerVmShutDown.cancel();
                            timerVmShutDown.purge();
                            timerVmShutDown = null;
                        }
                        shutdownVMStatus = true;
                        isVmDisConnected = false;
                        connectbtnVisible = "stopped";
                        btnStatus = false;

                        SharedPreferenceUtils.saveBoolean(PcView.this, Const.IS_SHUT_DOWN, true);
                        SharedPreferenceUtils.saveBoolean(PcView.this, Const.IS_VM_DISCONNECTED, false);
                        SharedPreferenceUtils.saveString(PcView.this, Const.connectbtnVisible, "stopped");
                        SharedPreferenceUtils.saveBoolean(PcView.this, Const.STARTBtnStatus, false);

                        btnStartVM.setVisibility(View.VISIBLE);
                        btnShutDownVM.setVisibility(View.GONE);
                        btnStartVM.setText("Start");
                        // getVM("");
                    }

                } else if (response.code() == 400 || response.code() == 404 ||
                        response.code() == 500 || response.code() == 401) {
                    try {
                        shutDownVMDialog.dismiss();
                    } catch (Exception e) {
                    }
                }
            }

            @Override
            public void onFailure(Call<MessageResponse> call, Throwable t) {
                AppUtils.showToast(Const.something_went_wrong, PcView.this);
            }
        });
    }

    public void getVMForShutDown() {
        try {
            Call<ResponseBody> call = retrofitAPI.getVMFromServer("Bearer " + accessToken);
            call.enqueue(new Callback<>() {
                @Override
                public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                    if (response.code() == 200) {
                        try {
                            JSONObject jsonObject = new JSONObject(response.body().string());
                            JSONArray jsonArray = jsonObject.getJSONArray("data");
                            String status = jsonArray.getJSONObject(0).getString("status");
                            String vmip = jsonArray.getJSONObject(0).getString("vmip");
                            String strVMId = jsonArray.getJSONObject(0).getString("vmid");
                            String startVmCallCount = jsonArray.getJSONObject(0).getString("start_vm_call_count");

                            isVmDisConnected = SharedPreferenceUtils.getBoolean(PcView.this, Const.IS_VM_DISCONNECTED);
                            if (isVmDisConnected) {
                                boolean firstTimeVMTimer = SharedPreferenceUtils.getBoolean(PcView.this, Const.FIRSTTIMEVMTIMER);
                                if (!firstTimeVMTimer) {
                                    if (status.equalsIgnoreCase("running")) {
                                        if (vmip != "")
                                            shutDownVM("background", strVMId);
                                        else
                                            Toast.makeText(PcView.this, "ip is" + vmip, Toast.LENGTH_LONG).show();
                                        //  stopVM("background" ,strVMId);
                                    }
                                }
                            }
                        } catch (Exception e) {
                        }
                    }

                }

                @Override
                public void onFailure(Call<ResponseBody> call, Throwable t) {
                    AppUtils.showToast(Const.something_went_wrong, PcView.this);
                }
            });
        } catch (Exception e) {

        }
    }

    private void openShutDownVMDialog(String status, Long time) {
        try {
            if (paymentSuccessDialog.isShowing()) {
                paymentSuccessDialog.dismiss();
            }
        } catch (Exception e) {
        }
        shutDownVMDialog = new Dialog(PcView.this);
        shutDownVMDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        shutDownVMDialog.setContentView(R.layout.shutingdownvm_dialog_layout);
        shutDownVMDialog.setCancelable(false);
        shutDownVMDialog.setCanceledOnTouchOutside(false);
        shutDownVMDialog.getWindow().setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
        shutDownVMDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        TextView titleText = shutDownVMDialog.findViewById(R.id.titleText);
        timerText = shutDownVMDialog.findViewById(R.id.timerText);
        TextView msgText = shutDownVMDialog.findViewById(R.id.msgText);
        titleText.setText(getResources().getString(R.string.no_vm_title));
        if (status.equalsIgnoreCase("start")) {
            callTimer(timerText, 60, status);
            msgText.setText(getResources().getString(R.string.startVMMsg));
        } else if (status.equalsIgnoreCase("vmtimer")) {
            callTimer(timerText, time, status);
            msgText.setText(getResources().getString(R.string.searching_pc_second));
        } else {
            callTimer(timerText, 25, status);
            msgText.setText(getResources().getString(R.string.shutdownVMMsg));
        }
        shutDownVMDialog.show();

    }

    private void callTimer(TextView timerText, long sec, String status) {
        new CountDownTimer(sec * 1000, 1000) {
            public void onTick(long millisUntilFinished) {
                long value = millisUntilFinished / 1000;
                long minutes = value / 60;
                long sec_ = value % 60;
                String timeString = String.format("%02d:%02d", minutes, sec_);
                timerText.setText(timeString);

                if (status.equalsIgnoreCase("start")) {
                    if (sec_ == 6) {
                        startVmTimerStatus = true;
                        getVM("startVm");
                    }
                }
            }

            public void onFinish() {
                shutDownVMDialog.dismiss();
                if (status.equalsIgnoreCase("shutdown")) {
                    if (timerVmShutDown != null) {
                        timerVmShutDown.cancel();
                        timerVmShutDown.purge();
                        timerVmShutDown = null;
                    }
                    shutdownVMStatus = true;
                    isVmDisConnected = false;
                    connectbtnVisible = "stopped";
                    btnStatus = false;
                    SharedPreferenceUtils.saveBoolean(PcView.this, Const.IS_SHUT_DOWN, true);
                    SharedPreferenceUtils.saveBoolean(PcView.this, Const.IS_VM_DISCONNECTED, false);
                    SharedPreferenceUtils.saveString(PcView.this, Const.connectbtnVisible, "stopped");
                    SharedPreferenceUtils.saveBoolean(PcView.this, Const.STARTBtnStatus, false);
                    getVM("");
                } else if (status.equalsIgnoreCase("vmtimer")) {
                    firstTimeVMTimer = false;
                    SharedPreferenceUtils.saveBoolean(PcView.this, Const.FIRSTTIMEVMTIMER, false);
                    firstTimeStartVmApi = true;
                    SharedPreferenceUtils.saveBoolean(PcView.this, Const.FIRSTTIMESTARTVMAPI, true);
                    SharedPreferenceUtils.saveBoolean(PcView.this, Const.FIRSTTIMEDIALOG, false);
                    firstTimeDialog = false;
                    startActivity(getIntent());
                }
            }
        }.start();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public Long timeDifference() {
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("H:mm:ss 'h'");
        String startTimeString = SharedPreferenceUtils.getString(PcView.this, Const.startTime);
        SimpleDateFormat sdf = new SimpleDateFormat("H:mm:ss 'h'");
        String stopTimeString = sdf.format(new Date());
        LocalTime startTime = LocalTime.parse(startTimeString, timeFormatter);
        LocalTime stopTime = LocalTime.parse(stopTimeString, timeFormatter);

        if (stopTime.isBefore(startTime)) {
            System.out.println("Stop time must not be before start time");
        } else {
            Duration difference = Duration.between(startTime, stopTime);

            long hours = difference.toHours();
            difference = difference.minusHours(hours);
            long minutes = difference.toMinutes();
            difference = difference.minusMinutes(minutes);
            long seconds = difference.getSeconds();

            long newTime = hours * 3600 + minutes * 60 + seconds;
            return newTime;
        }
        return null;
    }

    public void saveTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("H:mm:ss 'h'");
        String s = sdf.format(new Date());
        SharedPreferenceUtils.saveString(PcView.this, Const.startTime, s);
    }

    private void showTimer(String timeRemaining) {
        try {
            long value = Long.parseLong(timeRemaining);
            long hours = value / 3600;
            long minutes = value % 3600 / 60;
            long sec = value % 60;
            String timeString = String.format("%02d:%02d:%02d", hours, minutes, sec);
            tvTimer.setText(timeString + " hrs.");
        } catch (Exception e) {
        }
    }

    @Override
    public void onBackPressed() {
        if (doubleBackToExitPressedOnce) {
            super.onBackPressed();
            return;
        }
        this.doubleBackToExitPressedOnce = true;
        Toast.makeText(this, "Please click back again to exit", Toast.LENGTH_SHORT).show();
        new Handler().postDelayed(() -> doubleBackToExitPressedOnce = false, 2000);
    }

    private void openDialog(boolean purchaseVmFLag, String msg,String strStartSocket) {
        mDialog = new Dialog(PcView.this);
        mDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        mDialog.setContentView(R.layout.dialog_logout);
        mDialog.setCancelable(false);
        mDialog.setCanceledOnTouchOutside(false);
        mDialog.getWindow().setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
        mDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        TextView titleText = mDialog.findViewById(R.id.titleText);
        TextView msgText = mDialog.findViewById(R.id.msgText);
        Button txtNo = mDialog.findViewById(R.id.txtNo);
        Button txtYes = mDialog.findViewById(R.id.txtYes);
        titleText.setText(getResources().getString(R.string.no_vm_title));
        txtYes.setText("purchase");

        msgText.setText(msg);
        if (!purchaseVmFLag) {
            txtYes.setVisibility(View.VISIBLE);
            txtNo.setText(getResources().getString(R.string.applist_menu_cancel));
        } else {
            txtYes.setVisibility(View.GONE);
            txtNo.setText("Ok");
        }


        if(strStartSocket.equalsIgnoreCase("startSocket"))
            txtNo.setVisibility(View.GONE);
        else
            txtNo.setVisibility(View.VISIBLE);


        txtYes.setOnClickListener(view -> {
            mDialog.dismiss();
            SharedPreferenceUtils.saveBoolean(PcView.this, Const.FIRST_TIME_PAYMENT, true);
            AppUtils.navigateScreenWithoutFinish(PcView.this, SubscriptionPlanActivity.class);
        });
        txtNo.setOnClickListener(view -> {
            mDialog.dismiss();
        });
        mDialog.show();
    }

    private void openPaymentSuccessDialog() {
        paymentSuccessDialog = new Dialog(PcView.this);
        paymentSuccessDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        paymentSuccessDialog.setContentView(R.layout.payment_success_dialog);
        paymentSuccessDialog.setCancelable(true);
        paymentSuccessDialog.setCanceledOnTouchOutside(true);
        paymentSuccessDialog.getWindow().setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
        paymentSuccessDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        TextView msgText = paymentSuccessDialog.findViewById(R.id.msgText);
        Button txtYes = paymentSuccessDialog.findViewById(R.id.txtYes);
        txtYes.setText("OK");
        msgText.setText(getResources().getString(R.string.success_msg));

        txtYes.setOnClickListener(view -> {
            firstTimeDialog = true;
            SharedPreferenceUtils.saveBoolean(PcView.this, Const.FIRSTTIMEDIALOG, true);
            paymentSuccessDialog.dismiss();
            ivRefresh.setVisibility(View.GONE);
            btnStartVM.setVisibility(View.VISIBLE);
            btnShutDownVM.setVisibility(View.GONE);
            progressBar.setVisibility(View.GONE);
            btnStartVM.setText("Start");
        });
        paymentSuccessDialog.show();
    }

    private void callRefreshAPi() {
        String refreshToken = SharedPreferenceUtils.getString(PcView.this, Const.REFRESH_TOKEN);
        RefreshRequestModel refreshRequestModel = new RefreshRequestModel(refreshToken);
        Call<ResponseBody> call = retrofitAPI.userRefresh(refreshRequestModel);
        call.enqueue(new Callback<>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.code() == 200) {
                    try {
                        String responseValue = response.body().string();
                        JSONObject jObj = new JSONObject(responseValue);
                        String accessToken = jObj.getString("access");
                        SharedPreferenceUtils.saveString(PcView.this, Const.ACCESS_TOKEN, accessToken);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else if (response.code() == Const.ERROR_CODE_400 ||
                        response.code() == Const.ERROR_CODE_500 ||
                        response.code() == Const.ERROR_CODE_404 ||
                        response.code() == 401) {
                    try {
                        JSONObject jObj = new JSONObject(response.errorBody().string());
                        Toast.makeText(PcView.this, jObj.getString("detail"), Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    Toast.makeText(PcView.this, "Something went wrong, please try again later.", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
            }
        });
    }

    public void connectWebSocket(){
        Log.i("testt_statrr" , "6");
        URI uri;
        try {
            uri = new URI(Const.WEBSOCKET_URL);
        }
        catch (URISyntaxException e) {
            e.printStackTrace();
            return;
        }
        webSocketClient = new WebSocketClient(uri) {
            @Override
            public void onOpen() {
                Log.i("WebSocket", "Session is starting");
            //    webSocketClient.send("Hello World!");
            }
            @Override
            public void onTextReceived(String s) {
                Log.i("WebSocket", "Message received");
                final String message = s;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try{
                            Log.i("testt_websocket_msg", message);
                            if(message.contains("Ready")) {
                                Log.i("testt_websocket_msg2", "3e3f");
                                firstTimeVMTimer = false;
                                SharedPreferenceUtils.saveBoolean(PcView.this, Const.FIRSTTIMEVMTIMER, false);
                                btnStatus = false;
                                SharedPreferenceUtils.saveBoolean(PcView.this, Const.STARTBtnStatus, false);
                                getVM("");

//                                firstTimeStartVmApi = true;
//                                SharedPreferenceUtils.saveBoolean(PcView.this, Const.FIRSTTIMESTARTVMAPI, true);
//                                SharedPreferenceUtils.saveBoolean(PcView.this, Const.FIRSTTIMEDIALOG, false);
//                                firstTimeDialog = false;
                                }
                            else{
                                // getVM("");

                            }
                        } catch (Exception e){
                            Log.i("websocket_msg_expp", "exception " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                });
            }
            @Override
            public void onBinaryReceived(byte[] data) {
            }
            @Override
            public void onPingReceived(byte[] data) {
            }
            @Override
            public void onPongReceived(byte[] data) {
            }
            @Override
            public void onException(Exception e) {
                Log.i("WebSocket", "OnException");
                System.out.println(e.getMessage());
            }
            @Override
            public void onCloseReceived() {
                Log.i("WebSocket", "Closed ");
                System.out.println("onCloseReceived");
            }
        };
        webSocketClient.addHeader("vmid", strVMId);
        webSocketClient.addHeader("CurrUser", "ClientApp");
        webSocketClient.addHeader("Authorization", accessToken);
        webSocketClient.setConnectTimeout(10000);
        webSocketClient.setReadTimeout(60000);
        webSocketClient.enableAutomaticReconnection(5000);
        webSocketClient.connect();
    }

    private void getVMInitiallyCall() {
        Log.i("testt_statrr" , "3");
        Call<ResponseBody> call = retrofitAPI.getVMFromServer("Bearer " + accessToken);
        call.enqueue(new Callback<>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    try{
                        JSONObject jsonObject = new JSONObject(response.body().string());
                        JSONArray jsonArray = jsonObject.getJSONArray("data");
                        strVMId = jsonArray.getJSONObject(0).getString("vmid");
                        time_remaining = jsonArray.getJSONObject(0).getString("time_remaining");
                        status = jsonArray.getJSONObject(0).getString("status");
                        vmip = jsonArray.getJSONObject(0).getString("vmip");
                        String userType = jsonArray.getJSONObject(0).getString("platform_type");
                        if(userType.equalsIgnoreCase("android")) {
                            Log.i("testt_statrr", "4");
                            connectWebSocket();
                        }
                        else
                            openDialogForOtherVM(getResources().getString(R.string.other_vmMsg));
                    }
                    catch (Exception e){
                    }
                }
            }
            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                // progressBar.setVisibility(View.GONE);
                AppUtils.showToast(Const.something_went_wrong, PcView.this);
            }
        });
    }
    private void openDialogForOtherVM(String msg) {
        Dialog dialog = new Dialog(PcView.this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_logout);
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        dialog.getWindow().setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        TextView titleText = dialog.findViewById(R.id.titleText);
        TextView msgText = dialog.findViewById(R.id.msgText);
        Button txtNo = dialog.findViewById(R.id.txtNo);
        Button txtYes = dialog.findViewById(R.id.txtYes);
        titleText.setText("Oops!");
        msgText.setText(msg);
        txtYes.setVisibility(View.GONE);
        txtNo.setText("Ok");


        txtNo.setOnClickListener(view -> {
            dialog.dismiss();
        });
        dialog.show();
    }
}

