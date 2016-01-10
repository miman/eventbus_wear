package eu.miman.util.eventbus.wear;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.gson.Gson;

import java.util.Date;

/**
 * This gateway should be inherited to send events to a wear device, it creates an invisible bridge
 * between eventbusses on the phone and on the wear device.
 *
 * It is used by extending the service with a class listening on eventbus events and sending them
 * with the sendToWearDeviceAlways or sendToWearDeviceOnlyIfChanged function.
 *
 * The event will then be transmitted to/from the wear device (as a JSON string) and is then
 * automatically parsed on the other device (phone/wear device) and posted on that devices local
 * eventbus.
 *
 * This class should always be used together with EventbusDataLayerProxyService on the other device.
 *
 * Created by Mikael Thorman on 2015-12-28.
 */
public class EventbusDataLayerGateway implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {
    private static final String TAG = "EventbusDataGateway";

    private GoogleApiClient mGoogleApiClient = null;

    protected Context parentContext;

    public EventbusDataLayerGateway(Context parentContext) {
        this.parentContext = parentContext;
        initiateConnection();
    }

    public void initiateConnection() {
        Log.i(TAG, "initiateConnection");

        if(null == mGoogleApiClient) {
            mGoogleApiClient = new GoogleApiClient.Builder(parentContext)
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
            Log.i(TAG, "GoogleApiClient created");
        }
    }

    public void onResume() {
        if(!mGoogleApiClient.isConnected()){
            mGoogleApiClient.connect();
            Log.i(TAG, "Connecting to GoogleApiClient..");
        }
    }

    public void onPause() {
        if(null != mGoogleApiClient){
            if(mGoogleApiClient.isConnected()){
                mGoogleApiClient.disconnect();
                Log.v(TAG, "GoogleApiClient disconnected");
            }
        }
    }

    @Override
    public void onConnectionSuspended(int cause) {
        Log.i(TAG, "onConnectionSuspended called");
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.i(TAG, "onConnectionFailed called");
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        Log.i(TAG, "onConnected called");
    }

    /**
     * Sends the given object as a JSON string to the connected wear device, it will be sent to the
     * wear device even if the payload isn't changed (this is acheived by adding a timestamp
     * parameter to the payload so the payload is changed from last time).
     *
     * The data is sent on a path with the objects full classname (package + name) but with dots replaced by / and preceeded with a /.
     *
     * The actual data is located in an element with the name as the objects full classname (package + name) but with dots replaced by /
     *
     * @param o The object to send
     */
    protected void sendToDeviceAlways(Object o) {
        sendToDevice(o, true, true);
    }

    /**
     * Sends the given object as a JSON string to the connected wear device, if the payload hasn't
     * been changed from the last event it will NOT be sent to the wear device.
     *
     * The data is sent on a path with the objects full classname (package + name) but with dots
     * replaced by / and preceeded with a /.
     *
     * The actual data is located in an element with the name as the objects full classname
     * (package + name) but with dots replaced by /
     *
     * @param o The object to send
     */
    protected void sendToDeviceOnlyIfChanged(Object o) {
        sendToDevice(o, false, true);
    }

    /**
     * Sends the given object as a JSON string to the connected wear device, it will be sent to the
     * wear device even if the payload isn't changed (this is acheived by adding a timestamp
     * parameter to the payload so the payload is changed from last time).
     *
     * The object will be stored as a singleton, that is a new object of the same type that is
     * synced with this function will overwrite the previous one.
     *
     * The data is sent on a path with the objects full classname (package + name) but with dots replaced by / and preceeded with a /.
     *
     * The actual data is located in an element with the name as the objects full classname (package + name) but with dots replaced by /
     *
     * @param o The object to send
     */
    protected void syncWithDeviceAlways(Object o) {
        sendToDevice(o, true, false);
    }

    /**
     * Sends the given object as a JSON string to the connected wear device, if the payload hasn't
     * been changed from the last event it will NOT be sent to the wear device.
     *
     * The object will be stored as a singleton, that is a new object of the same type that is
     * synced with this function will overwrite the previous one.
     *
     * The data is sent on a path with the objects full classname (package + name) but with dots
     * replaced by / and preceeded with a /.
     *
     * The actual data is located in an element with the name as the objects full classname
     * (package + name) but with dots replaced by /
     *
     * @param o The object to send
     */
    protected void syncWithDeviceOnlyIfChanged(Object o) {
        sendToDevice(o, false, false);
    }

    /**
     * Sends the given object as a JSON string to the connected wear device.
     *
     * The data is sent on a path with the objects full classname (package + name) but with dots
     * replaced by / and preceeded with a /.
     *
     * The actual data is located in an element with the name as the objects full classname
     * (package + name) but with dots replaced by /
     *
     * @param o The object to send
     * @param alwaysSend    If this is true a timestamp is added to the payload so that the message
     *                      always is sent (while the timestamp is changed each time).
     * @param sendAsUniqueInstance If this is true a unique id will be added at the end of the path,
     *                             so each post will be sent. If it is false each new send of the
     *                             same class will overwrite the previous one.
     */
    private void sendToDevice(Object o, boolean alwaysSend, boolean sendAsUniqueInstance) {
        String className = o.getClass().getName();
        String classnameWithFileSeparators = className.replace(".", "/");
        String path = "/" + classnameWithFileSeparators;
        if (sendAsUniqueInstance) {
            path = path + "/" + System.currentTimeMillis();
        }
        PutDataMapRequest putDataMapReq = PutDataMapRequest.create(path);

        // Add vehicle list
        Gson gson = new Gson();
        String json = gson.toJson(o);
        Log.d(TAG, "Generated JSON: " + json);
        putDataMapReq.getDataMap().putString(classnameWithFileSeparators, json);

        if (alwaysSend) {
            putDataMapReq.getDataMap().putLong("time", new Date().getTime());
        }

        sendData(putDataMapReq, new WearCommunicationListener() {
            @Override
            public void wearCommSucceeded(DataApi.DataItemResult dataItemResult) {
                Log.d(TAG, "Data sent Ok to Wear device");
            }

            @Override
            public void wearCommFailed(DataApi.DataItemResult dataItemResult) {
                Log.w(TAG, "Failed to send data to Wear device");
            }
        });
    }

    // Create a data map and put data in it
    public void sendData(final PutDataMapRequest putDataMapReq, final WearCommunicationListener listener) {
        if (mGoogleApiClient != null) {
            PutDataRequest putDataReq = putDataMapReq.asPutDataRequest();
            PendingResult<DataApi.DataItemResult> pendingResult =
                    Wearable.DataApi.putDataItem(mGoogleApiClient, putDataReq);
            pendingResult.setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                @Override
                public void onResult(DataApi.DataItemResult dataItemResult) {
                    if (dataItemResult.getStatus().isSuccess()) {
                        String text = "Data was sent Ok to: " + putDataMapReq.getUri();
                        Log.i(TAG, text);
                        if (listener != null) {
                            listener.wearCommSucceeded(dataItemResult);
                        }
                    } else {
                        String text;
                        if (dataItemResult.getDataItem() != null && dataItemResult.getDataItem().getData() != null) {
                            text = "Failed to send data to '" + putDataMapReq.getUri() + "', msg: " + dataItemResult.getStatus().getStatusMessage();
                        } else {
                            text = "Failed to send data to '" + putDataMapReq.getUri() + "', msg: " + dataItemResult.getStatus().getStatusMessage();
                        }

                        Log.w(TAG, text);
                        if (listener != null) {
                            listener.wearCommFailed(dataItemResult);
                        }
                    }
                }
            });
        }
    }
}
