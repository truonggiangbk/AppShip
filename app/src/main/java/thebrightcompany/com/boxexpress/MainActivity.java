package thebrightcompany.com.boxexpress;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.mobile.auth.core.IdentityHandler;
import com.amazonaws.mobile.auth.core.IdentityManager;
import com.amazonaws.mobile.client.AWSMobileClient;
import com.amazonaws.mobile.client.AWSStartupHandler;
import com.amazonaws.mobile.client.AWSStartupResult;
import com.amazonaws.mobile.config.AWSConfiguration;
import com.amazonaws.mobileconnectors.iot.AWSIotKeystoreHelper;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttClientStatusCallback;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttLastWillAndTestament;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttManager;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttNewMessageCallback;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttQos;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.iot.AWSIotClient;
import com.amazonaws.services.iot.model.AttachPrincipalPolicyRequest;
import com.amazonaws.services.iot.model.CreateKeysAndCertificateRequest;
import com.amazonaws.services.iot.model.CreateKeysAndCertificateResult;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import thebrightcompany.com.boxexpress.model.BoxExpress;

import static thebrightcompany.com.boxexpress.MainActivity.EditText_Idx.EDIT_TEXT_QR1;
import static thebrightcompany.com.boxexpress.MainActivity.EditText_Idx.EDIT_TEXT_QR2;
import static thebrightcompany.com.boxexpress.MainActivity.EditText_Idx.EDIT_TEXT_RESULT;

public class MainActivity extends AppCompatActivity {

    public static final String TAG = MainActivity.class.getSimpleName();

    private static final int REQUEST_CODE_LOC = 22;

    // --- Constants to modify per your configuration ---

    // IoT endpoint
    // AWS Iot CLI describe-endpoint call returns: XXXXXXXXXX.iot.<region>.amazonaws.com
    private static final String CUSTOMER_SPECIFIC_ENDPOINT = "ad78brdsa8nsn.iot.us-east-2.amazonaws.com";
    // Cognito pool ID. For this app, pool needs to be unauthenticated pool with
    // AWS IoT permissions.
    private static final String COGNITO_POOL_ID = "us-east-2:459a7626-d206-4d32-9a74-e1f6b31cce61";
    // Name of the AWS IoT policy to attach to a newly created certificate
    private static final String AWS_IOT_POLICY_NAME = "IoTBroker";

    // Region of AWS IoT
    private static final Regions MY_REGION = Regions.US_EAST_2;
    // Filename of KeyStore file on the filesystem
    private static final String KEYSTORE_NAME = "iot_keystore";
    // Password for the private key in the KeyStore
    private static final String KEYSTORE_PASSWORD = "password";
    // Certificate and key aliases in the KeyStore
    private static final String CERTIFICATE_ID = "default";

    private static final String PUB_TOPIC = "Ship_Server_Pub";
    private static final String SUB_TOPIC = "Ship_Server_Sub";

    private static final String TRANSACTION_ID = "TransactionID";
    private static final String SDT = "Sdt";
    private static final String BOXINFO = "BoxInfo";
    private static final String ADDRESS = "Address";
    private static final String BOXID = "BoxID";

    private AWSIotClient mIotAndroidClient;
    private AWSIotMqttManager mqttManager;
    private String clientId;
    private String keystorePath;
    private String keystoreName;
    private String keystorePassword;

    private KeyStore clientKeyStore = null;
    private String certificateId;

    protected CognitoCachingCredentialsProvider credentialsProvider;

    //QR code scanner object
    private IntentIntegrator qrScan;

    private TextView txt_qrCode;
    FloatingActionButton fab;
    Button btn_scan_qr_code;
    Button btn_clear;
    private String msg = null;
    private TextView txt_QR1;
    private TextView txt_QR2;
    private TextView txt_result;
    public enum EditText_Idx{
        EDIT_TEXT_QR1,
        EDIT_TEXT_QR2,
        EDIT_TEXT_RESULT
    }

    private String Qr1_info = null;
    private String Qr2_info = null;

    private  EditText_Idx edittext_idx = EDIT_TEXT_QR1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        btn_scan_qr_code = (Button) findViewById(R.id.btn_QR);
        btn_clear = (Button) findViewById(R.id.btn_clear);
        txt_QR1 = (TextView) findViewById(R.id.txt_QR1);
        txt_QR2 = (TextView) findViewById(R.id.txt_QR2);
        txt_result = (TextView) findViewById(R.id.txt_result);

        //Intializing txt_description
        txt_QR1.setText("Quét mã QR thứ nhất");
        txt_QR2.setText("Quét mã QR thứ hai");
        txt_result.setText("Kết quả !!!");

        //Intializing scan object
        qrScan = new IntentIntegrator(this);
        btn_scan_qr_code.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                qrScan.initiateScan();
            }
        });

        btn_clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                txt_QR1.setText("Quét mã QR thứ nhất");
                txt_QR2.setText("Quét mã QR thứ hai");
                txt_result.setText("Kết quả !!!");
                edittext_idx = EDIT_TEXT_QR1;
            }
        });
//        showMessage("init aws");
        initMQTTAWS();

    }


    private void initMQTTAWS() {

        clientId = UUID.randomUUID().toString();
        // Initialize the AWS Cognito credentials provider
        credentialsProvider = new CognitoCachingCredentialsProvider(
                getApplicationContext(), // context
                COGNITO_POOL_ID, // Identity Pool ID
                MY_REGION // Region
        );

        Region region = Region.getRegion(MY_REGION);

        // MQTT Client
        mqttManager = new AWSIotMqttManager(clientId, CUSTOMER_SPECIFIC_ENDPOINT);

        // Set keepalive to 10 seconds.  Will recognize disconnects more quickly but will also send
        // MQTT pings every 10 seconds.
        mqttManager.setKeepAlive(10);

        // Set Last Will and Testament for MQTT.  On an unclean disconnect (loss of connection)
        // AWS IoT will publish this message to alert other clients.
        AWSIotMqttLastWillAndTestament lwt = new AWSIotMqttLastWillAndTestament(PUB_TOPIC,
                "Android client lost connection", AWSIotMqttQos.QOS0);
        mqttManager.setMqttLastWillAndTestament(lwt);

        // IoT Client (for creation of certificate if needed)
        mIotAndroidClient = new AWSIotClient(credentialsProvider);
        mIotAndroidClient.setRegion(region);

        keystorePath = getFilesDir().getPath();
        keystoreName = KEYSTORE_NAME;
        keystorePassword = KEYSTORE_PASSWORD;
        certificateId = CERTIFICATE_ID;

        // To load cert/key from keystore on filesystem
        try {
            if (AWSIotKeystoreHelper.isKeystorePresent(keystorePath, keystoreName)) {
                if (AWSIotKeystoreHelper.keystoreContainsAlias(certificateId, keystorePath,
                        keystoreName, keystorePassword)) {
                    Log.i(TAG, "Certificate " + certificateId
                            + " found in keystore - using for MQTT.");
                    // load keystore from file into memory to pass on connection
                    clientKeyStore = AWSIotKeystoreHelper.getIotKeystore(certificateId,
                            keystorePath, keystoreName, keystorePassword);
                    //btnConnect.setEnabled(true);
                    //connectMQTTAWS();
                } else {
                    Log.i(TAG, "Key/cert " + certificateId + " not found in keystore.");
                }
            } else {
                Log.i(TAG, "Keystore " + keystorePath + "/" + keystoreName + " not found.");
            }
        } catch (Exception e) {
            Log.e(TAG, "An error occurred retrieving cert/key from keystore.", e);
        }

        if (clientKeyStore == null) {
            Log.i(TAG, "Cert/key was not found in keystore - creating new key and certificate.");

            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        // Create a new private key and certificate. This call
                        // creates both on the server and returns them to the
                        // device.
                        CreateKeysAndCertificateRequest createKeysAndCertificateRequest =
                                new CreateKeysAndCertificateRequest();
                        createKeysAndCertificateRequest.setSetAsActive(true);
                        final CreateKeysAndCertificateResult createKeysAndCertificateResult;
                        createKeysAndCertificateResult =
                                mIotAndroidClient.createKeysAndCertificate(createKeysAndCertificateRequest);
                        Log.i(TAG,
                                "Cert ID: " +
                                        createKeysAndCertificateResult.getCertificateId() +
                                        " created.");

                        // store in keystore for use in MQTT client
                        // saved as alias "default" so a new certificate isn't
                        // generated each run of this application
                        AWSIotKeystoreHelper.saveCertificateAndPrivateKey(certificateId,
                                createKeysAndCertificateResult.getCertificatePem(),
                                createKeysAndCertificateResult.getKeyPair().getPrivateKey(),
                                keystorePath, keystoreName, keystorePassword);

                        // load keystore from file into memory to pass on
                        // connection
                        clientKeyStore = AWSIotKeystoreHelper.getIotKeystore(certificateId,
                                keystorePath, keystoreName, keystorePassword);

                        // Attach a policy to the newly created certificate.
                        // This flow assumes the policy was already created in
                        // AWS IoT and we are now just attaching it to the
                        // certificate.
                        AttachPrincipalPolicyRequest policyAttachRequest =
                                new AttachPrincipalPolicyRequest();
                        policyAttachRequest.setPolicyName(AWS_IOT_POLICY_NAME);
                        policyAttachRequest.setPrincipal(createKeysAndCertificateResult
                                .getCertificateArn());
                        mIotAndroidClient.attachPrincipalPolicy(policyAttachRequest);

//                        runOnUiThread(new Runnable() {
//                            @Override
//                            public void run() {
//                                //btnConnect.setEnabled(true);
//                            }
//                        });
                    } catch (Exception e) {
                        Log.e(TAG,
                                "Exception occurred when generating new private key and certificate.",
                                e);
                    }
                }
            }).start();

        }
        connectMQTTAWS();
        //subScribeMQTTAWS();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_LOC:
                if (grantResults.length > 0) {
                    for (int gr : grantResults) {
                        // Check if request is granted or not
                        if (gr != PackageManager.PERMISSION_GRANTED) {
                            return;
                        }
                    }

                    //TODO - Add your code here to start Discovery

                }
                break;
            default:
                return;
        }
    }


    //Getting the scan results
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null) {
            //If QR_code has nothing in it
            if (result.getContents() == null) {
                Toast.makeText(this, "Result Not Found", Toast.LENGTH_LONG).show();
            } else {
                //if qr contains data
//                BoxExpress boxExpress = new BoxExpress("123", "Nha Xuan Phuong", "0969298682");
//                Gson gson = new Gson();
//                String json = gson.toJson(boxExpress).toString();

                String json = "";
                String transactionid = null;
                String sdt = null;
                String boxinfo = null;
                String address = null;
                String boxid = null;

                try {
                    JSONObject obj = new JSONObject(result.getContents());
                    transactionid = obj.getString(TRANSACTION_ID);showMessage(transactionid);
                    sdt = obj.getString(SDT);showMessage(sdt);
                    boxinfo = obj.getString(BOXINFO);showMessage(boxinfo);
                }
                catch (JSONException e){
                    Log.d(TAG, e.toString());
                }

                try {
                    JSONObject obj = new JSONObject(result.getContents());
                    address = obj.getString(ADDRESS);showMessage(address);
                    boxid = obj.getString(BOXID);showMessage(boxid);
                }
                catch (JSONException e){
                    Log.d(TAG, e.toString());
                }

                if (transactionid != null) {
                    json = "TransactionID: " + transactionid + "\n" + "Sdt :" + sdt + "\n" + "Địa chỉ box :" + boxinfo;
                    Qr1_info = transactionid;
                    showMessage(json);
                }

                if (boxid != null) {
                    json = "Address: " + address + "\n" + "BoxID: " + boxid;
                    Qr2_info = boxid;
                    showMessage(json);
                }

                //publishClick(json);
                switch (edittext_idx){
                    case EDIT_TEXT_QR1:
                        txt_QR1.setText("Quét mã QR thứ nhất");
                        txt_QR2.setText("Quét mã QR thứ hai");
                        txt_result.setText("Kết quả !!!");
                        txt_QR1.setText(json);
                        edittext_idx = EDIT_TEXT_QR2;
                        break;
                    case EDIT_TEXT_QR2:
                        txt_QR2.setText(json);
                        txt_result.setText("Đợi kết quả...");
                        edittext_idx = EDIT_TEXT_QR1;
                        break;
                }

                if(Qr1_info != null && Qr2_info != null){
                    msg = "{\"message_id\": 301,\"transaction_id\": \"" + Qr1_info + "\"," + "\"box_id\": \"" + Qr2_info + "\"}";
                    Log.d(TAG, "json: " + json);
                }

//                try {
//                    //Convert the data to json
//                    JSONObject jsonObject = new JSONObject(json);
//                    BoxExpress box = gson.fromJson(String.valueOf(jsonObject), BoxExpress.class);
//                    txt_QR1.setText("TransactionID: " + box.getTransactionID() +"\n" +
//                    "BoxInfo: " + box.getBoxInfo() + "\n" + "Sdt: " + box.getSdt());
//
//                }catch (JSONException e){
//                    Log.d(TAG, e.toString());
//                }
                //publishClick("Giang ngao");
                //publishClick(json);
                /*try {
                    //Converting the data to json
                    JSONObject obj = new JSONObject(result.getContents());
                    Toast.makeText(this, "Result: " + result.getContents(), Toast.LENGTH_LONG).show();
                } catch (JSONException e) {
                    e.printStackTrace();
                    //if control comes here
                    //that means the encoded format not matches
                    //in this case you can display whatever data is available on the qrcode
                    //to a toast
                    Toast.makeText(this, "Error: " + result.getContents(), Toast.LENGTH_LONG).show();
                }*/

                //publishClick("Anh yeu em");
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }


    /**
     * The method use to connect to mqtt aws
     */
    private void connectMQTTAWS(){
        Log.d(TAG, "clientId = " + clientId);

        try {
            mqttManager.connect(clientKeyStore, new AWSIotMqttClientStatusCallback() {
                @Override
                public void onStatusChanged(final AWSIotMqttClientStatus status,
                                            final Throwable throwable) {
                    Log.d(TAG, "Status = " + String.valueOf(status));

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (status == AWSIotMqttClientStatus.Connecting) {
                                //tvStatus.setText("Connecting...");
                                showMessage("Connecting..");

                            } else if (status == AWSIotMqttClientStatus.Connected) {
                                //tvStatus.setText("Connected");
                                showMessage("Connected");
                                subScribeMQTTAWS();
                               if (!TextUtils.isEmpty(msg)){
                                   publishClick(msg);
                                   msg = null;
                                   Qr1_info = null;
                                   Qr2_info = null;
                               }

                            } else if (status == AWSIotMqttClientStatus.Reconnecting) {
                                if (throwable != null) {
                                    Log.e(TAG, "Connection error.", throwable);
                                    showMessage("Reconnecting..");
                                }
                                //tvStatus.setText("Reconnecting");
                            } else if (status == AWSIotMqttClientStatus.ConnectionLost) {
                                if (throwable != null) {
                                    Log.e(TAG, "Connection error.", throwable);
                                    showMessage("Connection error");
                                }
                                //tvStatus.setText("Disconnected");
                            } else {
                                //tvStatus.setText("Disconnected");
                                showMessage("Disconnected");
                            }
                        }
                    });
                }
            });
        } catch (final Exception e) {
            Log.e(TAG, "Connection error.", e);
            //tvStatus.setText("Error! " + e.getMessage());
        }
    }

    /**
     * The method use to subscribe
     */
    private void subScribeMQTTAWS(){

        Log.d(TAG, "topic = " + SUB_TOPIC);

        try {
            mqttManager.subscribeToTopic(SUB_TOPIC, AWSIotMqttQos.QOS0,
                    new AWSIotMqttNewMessageCallback() {
                        @Override
                        public void onMessageArrived(final String topic, final byte[] data) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        String message = new String(data, "UTF-8");
                                        Log.d(TAG, "Message arrived:");
                                        Log.d(TAG, "   Topic: " + topic);
                                        Log.d(TAG, " Message: " + message);
                                        showMessage(message);
                                        txt_result.setText(message);
                                        //tvLastMessage.setText(message);

                                    } catch (UnsupportedEncodingException e) {
                                        showMessage("Message encoding error");
                                        Log.e(TAG, "Message encoding error.", e);
                                    }
                                }
                            });
                        }
                    });
        } catch (Exception e) {
            showMessage("Subscription error");
            Log.e(TAG, "Subscription error.", e);
        }
    }

    /**
     * The method use to publish msg
     * @param msg
     */
    private void publishClick(String msg){
        try {
            //showMessage(msg);
            mqttManager.publishString(msg, PUB_TOPIC, AWSIotMqttQos.QOS0);
        } catch (Exception e) {
            showMessage("Publish error");
            Log.e(TAG, "Publish error.", e);
        }
    }

    /**
     * The method use to disconnect to mqtt aws
     */
    private void disconnectClick(){
        try {
            mqttManager.disconnect();
        } catch (Exception e) {
            Log.e(TAG, "Disconnect error.", e);
        }
    }

    @Override
    protected void onResume() {
        //subScribeMQTTAWS();
        initMQTTAWS();
        super.onResume();

    }

    @Override
    protected void onPause() {
        super.onPause();
        disconnectClick();
    }

    private void showMessage(String msg){
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
