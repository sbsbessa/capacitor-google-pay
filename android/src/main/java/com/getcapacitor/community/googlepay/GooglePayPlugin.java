package com.getcapacitor.community.googlepay;

import android.content.Intent;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(
    name = "GooglePay",
    requestCodes = {
        GooglePayPlugin.REQUEST_CODE_PUSH_TOKENIZE, GooglePayPlugin.REQUEST_CREATE_WALLET, GooglePayPlugin.REQUEST_CODE_DELETE_TOKEN
    }
)
public class GooglePayPlugin extends Plugin {

    private GooglePay implementation;
    protected static final int REQUEST_CODE_PUSH_TOKENIZE = 3;
    protected static final int REQUEST_CREATE_WALLET = 4;
    protected static final int REQUEST_CODE_DELETE_TOKEN = 5;

    @Override
    public void load() {
        implementation = new GooglePay(this.bridge);
        implementation.setDataChangeListener(this::onDataChangeEvent);
    }

    @Override
    public void handleOnActivityResult(int requestCode, int resultCode, Intent data) {
        super.handleOnActivityResult(requestCode, resultCode, data);
        implementation.onActivityResult(requestCode, resultCode, data);
    }

    @PluginMethod
    public void getEnvironment(PluginCall call) {
        implementation.getEnvironment(call);
    }

    @PluginMethod
    public void getStableHardwareId(PluginCall call) {
        implementation.getStableHardwareId(call);
    }

    @PluginMethod
    public void getActiveWalletID(PluginCall call) {
        implementation.getActiveWalletID(call);
    }

    @PluginMethod
    public void createWallet(PluginCall call) {
        implementation.createWallet(call);
    }

    @PluginMethod
    public void getTokenStatus(PluginCall call) {
        implementation.getTokenStatus(call);
    }

    @PluginMethod
    public void listTokens(PluginCall call) {
        implementation.listTokens(call);
    }

    @PluginMethod
    public void isTokenized(PluginCall call) {
        implementation.isTokenized(call);
    }

    @PluginMethod
    public void pushProvision(PluginCall call) {
        implementation.pushProvision(call);
    }

    @PluginMethod
    public void resumeTokenization(PluginCall call) {
        implementation.resumeTokenization(call);
    }

    @PluginMethod
    public void requestSelectToken(PluginCall call) {
        implementation.requestSelectToken(call);
    }

    @PluginMethod
    public void requestDeleteToken(PluginCall call) {
        implementation.requestDeleteToken(call);
    }

    @PluginMethod
    public void isGPayDefaultNFCApp(PluginCall call) {
        implementation.isGPayDefaultNFCApp(call);
    }

    @PluginMethod
    public void setGPayAsDefaultNFCApp(PluginCall call) {
        implementation.setGPayAsDefaultNFCApp(call);
    }

    @PluginMethod
    public void registerDataChangedListener(PluginCall call) {
        implementation.registerDataChangedListener(call);
    }

    private void onDataChangeEvent(String event, JSObject result, Boolean bool) {
        notifyListeners(event, result, bool);
    }
}
