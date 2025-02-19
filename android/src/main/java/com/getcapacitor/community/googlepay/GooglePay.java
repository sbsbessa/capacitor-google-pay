package com.getcapacitor.community.googlepay;

import static com.google.android.gms.tapandpay.TapAndPayStatusCodes.TAP_AND_PAY_NO_ACTIVE_WALLET;
import static com.google.android.gms.tapandpay.TapAndPayStatusCodes.TAP_AND_PAY_TOKEN_NOT_FOUND;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.nfc.NfcAdapter;
import android.nfc.NfcManager;
import android.nfc.cardemulation.CardEmulation;
import android.util.Log;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.getcapacitor.Bridge;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PluginCall;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tapandpay.TapAndPay;
import com.google.android.gms.tapandpay.TapAndPayClient;
import com.google.android.gms.tapandpay.issuer.IsTokenizedRequest;
import com.google.android.gms.tapandpay.issuer.PushTokenizeRequest;
import com.google.android.gms.tapandpay.issuer.TokenInfo;
import com.google.android.gms.tapandpay.issuer.UserAddress;
import java.util.Objects;
import org.json.JSONObject;

public class GooglePay {

    /**
     * Interface for callbacks when network status changes.
     */
    interface DateChangeListener {
        void onDateChanged(String event, JSObject result, Boolean state);
    }

    private final TapAndPayClient tapAndPay;
    private static final String TAG = "GooglePayPlugin";
    private final Bridge bridge;
    public String callBackId;
    public String dataChangeCallBackId;
    protected static final int REQUEST_CODE_PUSH_TOKENIZE = 3;
    protected static final int REQUEST_CODE_CREATE_WALLET = 4;
    protected static final int REQUEST_CODE_ACTION_TOKEN = 5;
    protected static final int RESULT_CANCELED = 0;
    protected static final int RESULT_OK = -1;
    protected static final int RESULT_INVALID_TOKEN = 15003;

    public enum ErrorCodeReference {
        PUSH_PROVISION_ERROR(-1),
        PUSH_PROVISION_CANCEL(-2),
        MISSING_DATA_ERROR(-3),
        CREATE_WALLET_CANCEL(-4),
        IS_TOKENIZED_ERROR(-5),
        ACTION_TOKEN_ERROR(-6),
        INVALID_TOKEN(-7),
        SET_DEFAULT_PAYMENTS_ERROR(-9);

        private final Integer code;

        ErrorCodeReference(Integer code) {
            this.code = code;
        }

        public String getError() {
            return code.toString();
        }
    }

    public enum TokenStatusReference {
        TOKEN_STATE_UNTOKENIZED(1),
        TOKEN_STATE_PENDING(2),
        TOKEN_STATE_NEEDS_IDENTITY_VERIFICATION(3),
        TOKEN_STATE_SUSPENDED(4),
        TOKEN_STATE_ACTIVE(5),
        TOKEN_STATE_FELICA_PENDING_PROVISIONING(6),
        TOKEN_STATE_NOT_FOUND(-1);

        public final int referenceId;

        public static TokenStatusReference getName(int referenceId) {
            for (TokenStatusReference reference : values()) {
                if (reference.referenceId == referenceId) {
                    return reference;
                }
            }
            return null;
        }

        TokenStatusReference(int referenceId) {
            this.referenceId = referenceId;
        }
    }

    @Nullable
    private DateChangeListener dataChangeListener;

    public void setDataChangeListener(@Nullable DateChangeListener listener) {
        this.dataChangeListener = listener;
    }

    public GooglePay(@NonNull Bridge bridge) {
        this.tapAndPay = TapAndPay.getClient(bridge.getActivity());
        this.bridge = bridge;
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.i(TAG, "onActivityResult --- " + resultCode + " --- " + requestCode);
        Log.i(TAG, "onActivityResultData --- " + data);
        Log.i(TAG, "CallBackID --- " + callBackId);

        // Get the previously saved call
        PluginCall call = this.bridge.getSavedCall(callBackId);

        if (call == null) {
            return;
        }

        JSObject result = new JSObject();

        if (requestCode == REQUEST_CODE_CREATE_WALLET) {
            if (resultCode == RESULT_CANCELED) {
                // The user canceled the request.
                call.reject("Google wallet create cancelled", ErrorCodeReference.CREATE_WALLET_CANCEL.getError());
            } else if (resultCode == RESULT_OK) {
                Log.i(TAG, "Google wallet created --- ");
                result.put("isCreated", true);
                call.resolve(result);
            }
        } else if (requestCode == REQUEST_CODE_PUSH_TOKENIZE) {
            if (resultCode == RESULT_CANCELED) {
                call.reject("PUSH_PROVISION_CANCEL", ErrorCodeReference.PUSH_PROVISION_CANCEL.getError());
            } else if (resultCode == RESULT_OK) {
                // The action succeeded.
                String tokenId = data.getStringExtra(TapAndPay.EXTRA_ISSUER_TOKEN_ID);
                result.put("tokenId", tokenId);
                call.resolve(result);
            }
        } else if (requestCode == REQUEST_CODE_ACTION_TOKEN) {
            Log.i(TAG, "ACTION_TOKEN --- ");

            if (resultCode == RESULT_CANCELED) {
                // The user canceled the request.
                Log.i(TAG, "ACTION_TOKEN CANCEL --- ");
                result.put("isSuccess", false);
                call.resolve(result);
            } else if (resultCode == RESULT_OK) {
                Log.i(TAG, "ACTION_TOKEN SUCCESS --- ");
                result.put("isSuccess", true);
                call.resolve(result);
            } else if (resultCode == RESULT_INVALID_TOKEN) {
                Log.i(TAG, "ACTION_TOKEN WRONG TOKEN --- ");
                call.reject("Invalid TokenReferenceID", ErrorCodeReference.INVALID_TOKEN.getError());
            } else {
                Log.i(TAG, "ACTION_TOKEN ERROR --- ");
                Log.i(TAG, call.toString());
                call.reject("ACTION_TOKEN ERROR", ErrorCodeReference.ACTION_TOKEN_ERROR.getError());
            }
        } else {
            call.resolve();
        }
        this.bridge.releaseCall(callBackId);
        call.setKeepAlive(false);
    }

    public void getEnvironment(PluginCall call) {
        try {
            this.tapAndPay.getEnvironment()
                .addOnCompleteListener(task -> {
                    Log.i(TAG, "onComplete (getEnvironment) - " + task.isSuccessful());
                    if (task.isSuccessful()) {
                        Log.d(TAG, "getEnvironment: " + task.getResult());
                        JSObject result = new JSObject();
                        result.put("value", task.getResult());
                        call.resolve(result);
                    } else {
                        call.reject("Environment not found", "ENV_ERROR");
                    }
                });
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    public void getStableHardwareId(PluginCall call) {
        try {
            this.tapAndPay.getStableHardwareId()
                .addOnCompleteListener(task -> {
                    Log.i(TAG, "onComplete (getStableHardwareId) - " + task.isSuccessful());
                    if (task.isSuccessful()) {
                        Log.d(TAG, "getStableHardwareId: " + task.getResult());
                        JSObject result = new JSObject();
                        result.put("hardwareId", task.getResult());
                        call.resolve(result);
                    } else {
                        Exception exception = task.getException();

                        if (exception instanceof ApiException apiException) {
                            call.reject(apiException.getMessage());
                        } else {
                            call.reject("Hardware ID not found", "NO_HARDWARE_ID");
                        }
                    }
                });
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    public void getActiveWalletID(PluginCall call) {
        try {
            this.tapAndPay.getActiveWalletId()
                .addOnCompleteListener(task -> {
                    Log.i(TAG, "onComplete (getActiveWalletID) - " + task.isSuccessful());
                    if (task.isSuccessful()) {
                        // Next: look up token ids for the active wallet
                        // This typically involves network calls to a server with knowledge
                        // of wallets and tokens.
                        Log.d(TAG, "getActiveWalletID: " + task.getResult());
                        JSObject result = new JSObject();
                        result.put("walletId", task.getResult());
                        call.resolve(result);
                    } else {
                        Exception exception = task.getException();

                        if (exception instanceof ApiException apiException) {
                            if (apiException.getStatusCode() == TAP_AND_PAY_NO_ACTIVE_WALLET) {
                                call.reject("Active wallet not found", "NO_ACTIVE_WALLET");
                            } else {
                                call.reject(apiException.getMessage());
                            }
                        } else {
                            call.reject("Active wallet not found", "NO_ACTIVE_WALLET");
                        }
                    }
                });
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    public void createWallet(PluginCall call) {
        try {
            this.bridge.saveCall(call);
            this.callBackId = call.getCallbackId();
            call.setKeepAlive(true);
            tapAndPay.createWallet(bridge.getActivity(), REQUEST_CODE_CREATE_WALLET);
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    public void getTokenStatus(PluginCall call) {
        final String tokenReferenceId = call.getString("tokenReferenceId");
        if (tokenReferenceId == null) {
            call.reject("No tokenReferenceId found");
            return;
        }

        final String tsp = call.getString("tsp");
        if (tsp == null) {
            call.reject("No tsp found", ErrorCodeReference.MISSING_DATA_ERROR.getError());
            return;
        }

        try {
            this.tapAndPay.getTokenStatus(getTSP(tsp), tokenReferenceId)
                .addOnCompleteListener(task -> {
                    Log.i(TAG, "onComplete (getTokenStatus) - " + task.isSuccessful());
                    if (task.isSuccessful()) {
                        @TapAndPay.TokenState
                        int tokenStateInt = task.getResult().getTokenState();
                        //                                    boolean isSelected = task.getResult().isSelected();
                        // Next: update payment card UI to reflect token state and selection
                        JSObject result = new JSObject();
                        result.put("state", tokenStateInt);
                        result.put("code", GooglePay.TokenStatusReference.getName(tokenStateInt));
                        call.resolve(result);
                    } else {
                        Exception exception = task.getException();

                        if (exception instanceof ApiException apiException) {
                            if (apiException.getStatusCode() == TAP_AND_PAY_TOKEN_NOT_FOUND) {
                                // Could not get token status
                                call.reject(apiException.getMessage(), "TAP_AND_PAY_TOKEN_NOT_FOUND");
                            } else {
                                call.reject(apiException.getMessage());
                            }
                        } else {
                            call.reject("TOKEN_NOT_FOUND", "TAP_AND_PAY_TOKEN_NOT_FOUND");
                        }
                    }
                });
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    public void listTokens(PluginCall call) {
        try {
            this.tapAndPay.listTokens()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        JSObject result = new JSObject();
                        JSArray tokens = new JSArray();
                        Log.i(TAG, "listTokens: " + task.getResult());
                        for (TokenInfo token : task.getResult()) {
                            Log.d(TAG, "Found token with ID: " + token.getIssuerTokenId());
                            tokens.put(token.getIssuerTokenId());
                        }
                        result.put("tokens", tokens);
                        call.resolve(result);
                    } else {
                        Exception exception = task.getException();
                        Log.i(TAG, "listTokens" + exception);
                        if (exception instanceof ApiException apiException) {
                            call.reject(apiException.getMessage());
                        } else {
                            call.reject("LIST_TOKEN_ERROR", "LIST_TOKEN_ERROR");
                        }
                    }
                });
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    public void isTokenized(PluginCall call) {
        String tsp = call.getString("tsp");
        String lastDigits = call.getString("lastDigits");
        if (tsp == null) {
            call.reject("No tsp found", ErrorCodeReference.MISSING_DATA_ERROR.getError());
            return;
        }
        if (lastDigits == null) {
            call.reject("No lastDigits found", ErrorCodeReference.MISSING_DATA_ERROR.getError());
            return;
        }
        try {
            IsTokenizedRequest request = new IsTokenizedRequest.Builder()
                .setIdentifier(lastDigits)
                .setNetwork(getCardNetwork(tsp))
                .setTokenServiceProvider(getTSP(tsp))
                .build();

            this.tapAndPay.isTokenized(request)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Boolean isTokenized = task.getResult();
                        JSObject result = new JSObject();
                        result.put("isTokenized", isTokenized.booleanValue());
                        call.resolve(result);
                    } else {
                        Exception exception = task.getException();
                        if (exception instanceof ApiException apiException) {
                            call.reject(apiException.getMessage(), ErrorCodeReference.IS_TOKENIZED_ERROR.getError());
                        } else {
                            if (exception != null) {
                                call.reject(exception.getMessage(), ErrorCodeReference.IS_TOKENIZED_ERROR.getError());
                            } else {
                                call.reject("IS_TOKENIZED_ERROR", ErrorCodeReference.IS_TOKENIZED_ERROR.getError());
                            }
                        }

                        Log.i(TAG, "isTokenized" + exception);
                    }
                });
        } catch (Exception e) {
            call.reject(e.getMessage(), ErrorCodeReference.IS_TOKENIZED_ERROR.getError());
        }
    }

    public void pushProvision(PluginCall call) {
        Log.i(TAG, "PUSHPROVISION --- 1");
        String opcData = call.getString("opc");
        if (opcData == null) {
            call.reject("No OPC found");
            return;
        }
        byte[] opc = opcData.getBytes();
        String tsp = call.getString("tsp");
        String clientName = call.getString("clientName");
        String lastDigits = call.getString("lastDigits");
        JSONObject address = call.getObject("address");
        if (tsp == null) {
            call.reject("No tsp found", ErrorCodeReference.MISSING_DATA_ERROR.getError());
            return;
        }
        if (lastDigits == null) {
            call.reject("No lastDigits found", ErrorCodeReference.MISSING_DATA_ERROR.getError());
            return;
        }
        if (clientName == null) {
            call.reject("No clientName found", ErrorCodeReference.MISSING_DATA_ERROR.getError());
            return;
        }
        if (Objects.isNull(address)) {
            call.reject("No address found", ErrorCodeReference.MISSING_DATA_ERROR.getError());
            return;
        }
        try {
            UserAddress userAddress = UserAddress
                .newBuilder()
                .setName(Objects.requireNonNullElse(address.getString("name"), ""))
                .setAddress1(Objects.requireNonNullElse(address.getString("address1"), ""))
                .setAddress2(Objects.requireNonNullElse(address.getString("address2"), ""))
                .setLocality(Objects.requireNonNullElse(address.getString("locality"), ""))
                .setAdministrativeArea(Objects.requireNonNullElse(address.getString("administrativeArea"), ""))
                .setCountryCode(Objects.requireNonNullElse(address.getString("countryCode"), ""))
                .setPostalCode(Objects.requireNonNullElse(address.getString("postalCode"), ""))
                .setPhoneNumber(Objects.requireNonNullElse(address.getString("phoneNumber"), ""))
                .build();

            PushTokenizeRequest pushTokenizeRequest = new PushTokenizeRequest.Builder()
                .setOpaquePaymentCard(opc)
                .setNetwork(getCardNetwork(tsp))
                .setTokenServiceProvider(getTSP(tsp))
                .setDisplayName(clientName)
                .setLastDigits(lastDigits)
                .setUserAddress(userAddress)
                .build();
            Log.i(TAG, "PUSHPROVISION --- 2");
            this.bridge.saveCall(call);
            this.callBackId = call.getCallbackId();
            call.setKeepAlive(true);
            // Start the Activity for result using the name of the callback method
            Log.i(TAG, "PUSHPROVISION --- 3");

            // a request code value you define as in Android's startActivityForResult
            tapAndPay.pushTokenize(bridge.getActivity(), pushTokenizeRequest, REQUEST_CODE_PUSH_TOKENIZE);
        } catch (Exception e) {
            call.reject(e.getMessage(), ErrorCodeReference.PUSH_PROVISION_ERROR.getError());
        }
    }

    public void resumeTokenization(PluginCall call) {
        String tokenReferenceId = call.getString("tokenReferenceId");
        String tsp = call.getString("tsp");

        if (tokenReferenceId == null || tokenReferenceId.isEmpty()) {
            call.reject("No tokenReferenceId found");
            return;
        }

        if (tsp == null || tsp.isEmpty()) {
            call.reject("No tsp found");
            return;
        }

        try {
            this.bridge.saveCall(call);
            this.callBackId = call.getCallbackId();
            call.setKeepAlive(true);

            tapAndPay.tokenize(
                bridge.getActivity(),
                tokenReferenceId,
                getTSP(tsp),
                "Cocos Card",
                getCardNetwork(tsp),
                REQUEST_CODE_ACTION_TOKEN
            );
        } catch (Exception e) {
            call.reject("Error resuming tokenization: " + e.getMessage(), ErrorCodeReference.ACTION_TOKEN_ERROR.getError());
        }
    }

    public void requestSelectToken(PluginCall call) {
        Log.i(TAG, "selectToken --- 1");
        String tokenReferenceId = call.getString("tokenReferenceId");
        if (tokenReferenceId == null) {
            call.reject("No tokenReferenceId found");
            return;
        }
        String tsp = call.getString("tsp");
        if (tsp == null) {
            call.reject("No tsp found");
            return;
        }
        try {
            this.bridge.saveCall(call);
            this.callBackId = call.getCallbackId();
            call.setKeepAlive(true);
            Log.i(TAG, "selectToken --- 2");
            this.tapAndPay.requestSelectToken(bridge.getActivity(), tokenReferenceId, getTSP(tsp), REQUEST_CODE_ACTION_TOKEN);
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    public void requestDeleteToken(PluginCall call) {
        Log.i(TAG, "removeToken --- 1");
        String tokenReferenceId = call.getString("tokenReferenceId");
        if (tokenReferenceId == null) {
            call.reject("No tokenReferenceId found");
            return;
        }
        String tsp = call.getString("tsp");
        if (tsp == null) {
            call.reject("No tsp found");
            return;
        }
        try {
            this.bridge.saveCall(call);
            this.callBackId = call.getCallbackId();
            call.setKeepAlive(true);
            Log.i(TAG, "removeToken --- 2");
            this.tapAndPay.requestDeleteToken(bridge.getActivity(), tokenReferenceId, getTSP(tsp), REQUEST_CODE_ACTION_TOKEN);
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    public void isGPayDefaultNFCApp(PluginCall call) {
        try {
            NfcManager nfcManager = (NfcManager) this.bridge.getContext().getSystemService(Context.NFC_SERVICE);
            NfcAdapter adapter = nfcManager.getDefaultAdapter();
            if (adapter != null) {
                JSObject result = new JSObject();
                if (adapter.isEnabled()) {
                    CardEmulation emulation = CardEmulation.getInstance(adapter);
                    boolean isDefault = emulation.isDefaultServiceForCategory(
                        new ComponentName(
                            GoogleApiAvailability.GOOGLE_PLAY_SERVICES_PACKAGE,
                            "com.google.android.gms.tapandpay.hce.service.TpHceService"
                        ),
                        CardEmulation.CATEGORY_PAYMENT
                    );
                    result.put("isDefault", isDefault);
                    result.put("isNFCOn", true);
                    call.resolve(result);
                } else {
                    result.put("isDefault", false);
                    result.put("isNFCOn", false);
                    call.resolve(result);
                }
            } else {
                call.reject("NFC is not supported", "NFC_SERVICE_NOT_SUPPORTED");
            }
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    public void setGPayAsDefaultNFCApp(PluginCall call) {
        try {
            Intent intent = new Intent(CardEmulation.ACTION_CHANGE_DEFAULT);
            intent.putExtra(CardEmulation.EXTRA_CATEGORY, CardEmulation.CATEGORY_PAYMENT);
            intent.putExtra(
                CardEmulation.EXTRA_SERVICE_COMPONENT,
                new ComponentName("com.google.android.gms", "com.google.android.gms.tapandpay.hce.service.TpHceService")
            );

            this.bridge.saveCall(call);
            this.callBackId = call.getCallbackId();
            call.setKeepAlive(true);
            ActivityResultLauncher<Intent> launcher =
                this.bridge.registerForActivityResult(
                        new ActivityResultContracts.StartActivityForResult(),
                        result -> {
                            // Handle the result here
                            if (result.getResultCode() == Activity.RESULT_OK) {
                                // Success, do something
                                JSObject ret = new JSObject();
                                ret.put("isDefault", true);
                                call.resolve(ret);
                            } else {
                                // Failure, handle error
                                call.reject("Default payment set cancelled", ErrorCodeReference.SET_DEFAULT_PAYMENTS_ERROR.getError());
                            }
                        }
                    );

            launcher.launch(intent);
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    public void registerDataChangedListener(PluginCall call) {
        try {
            this.tapAndPay.registerDataChangedListener(() -> {
                    Log.i(TAG, call.toString());
                    JSObject result = new JSObject();
                    result.put("value", "OK");
                    assert this.dataChangeListener != null;
                    this.dataChangeCallBackId = call.getCallbackId();
                    call.setKeepAlive(true);
                    this.dataChangeListener.onDateChanged("registerDataChangedListener", result, true);
                });
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    private int getCardNetwork(String tsp) {
        return switch (tsp) {
            case "VISA" -> TapAndPay.CARD_NETWORK_VISA;
            case "MASTERCARD" -> TapAndPay.CARD_NETWORK_MASTERCARD;
            case "MIR" -> TapAndPay.CARD_NETWORK_MIR;
            default -> 0;
        };
    }

    private int getTSP(String tsp) {
        return switch (tsp) {
            case "VISA" -> TapAndPay.TOKEN_PROVIDER_VISA;
            case "MASTERCARD" -> TapAndPay.TOKEN_PROVIDER_MASTERCARD;
            case "MIR" -> TapAndPay.TOKEN_PROVIDER_MIR;
            default -> 0;
        };
    }
}
