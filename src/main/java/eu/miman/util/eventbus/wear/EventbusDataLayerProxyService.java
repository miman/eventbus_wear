package eu.miman.util.eventbus.wear;

import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;
import com.google.gson.Gson;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import de.greenrobot.event.EventBus;

/**
 * This service listens to incoming events and creates an invisible bridge between eventbusses on
 * the phone and on the wear device.
 *
 * It is used by extending the service and call handleMessageClass with the class of all events that should be listened to.
 *
 * The events will then be automatically parsed and posted on the local eventbus.
 *
 * This class should always be used with the EventbusDataLayerGateway on the other device.
 *
 * Created by Mikael Thorman on 2015-12-28.
 */
public class EventbusDataLayerProxyService extends WearableListenerService implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {
    private static final String TAG = "EventbusDataService";

    /**
     * This map contains path/object key pairs.
     * This is automatically populated, do NOT change this !
     */
    Map<String, ManagedMessageObject> handledPathMap;

    private GoogleApiClient mGoogleApiClient = null;

    /**
     * The first time we start the application we reload everything from local storage.
     */
    boolean initialLoadDone = false;

    /**
     * This function is called once for each class that this listener should handle if received
     * from the phone.
     *
     * If an event of this class is received it will be converted into an object and posted on
     * the local eventbus.
     *
     * @param cl The class for the event to handle
     */
    protected void handleMessageClass(Class cl, boolean postAsSticky, boolean storeLocalCopy,
                                      boolean deleteWhenRead) {
        String className = cl.getName();
        String classnameWithFileSeparators = className.replace(".", "/");
        String path = "/" + classnameWithFileSeparators;
        ManagedMessageObject obj = new ManagedMessageObject(cl, deleteWhenRead,
                classnameWithFileSeparators, path, postAsSticky, storeLocalCopy);
        handledPathMap.put(path, obj);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Created");

        handledPathMap = new HashMap<String, ManagedMessageObject>();

        if(null == mGoogleApiClient) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
            Log.i(TAG, "GoogleApiClient created");
        }

        if(!mGoogleApiClient.isConnected()){
            mGoogleApiClient.connect();
            Log.i(TAG, "Connecting to GoogleApiClient..");
        }
    }

    @Override
    public void onDestroy() {
        Log.v(TAG, "Destroyed");

        if(null != mGoogleApiClient){
            if(mGoogleApiClient.isConnected()){
                mGoogleApiClient.disconnect();
                Log.v(TAG, "GoogleApiClient disconnected");
            }
        }

        super.onDestroy();
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
        // We re-initate the locally stored data if there is any.
        inititateData();
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        super.onDataChanged(dataEvents);
        Log.i(TAG, "DataEvent received from Mobile device.");
        for (DataEvent event : dataEvents) {
            DataItem item = event.getDataItem();
            String path = item.getUri().getPath();
            if (event.getType() == DataEvent.TYPE_CHANGED) {
                // DataItem changed
                ManagedMessageObject handler = getHandlerForPath(path);
                if (handler != null) {
                    // This is a path that is handled by this application
                    Log.i(TAG, "DataEvent handled by this app received from Mobile device, path: " + path);
                    handleReceivedMessage(item, handler);
                    if (handler.isDeleteWhenRead()) {
                        // This event should be deleted from the Data Layer
                        deleteDataPath(item.getUri());
                    }
                } else {
                    // This is a path that is NOT handled by this application
                    Log.w(TAG, "DataEvent NOT handled by this app received from Mobile device, path: " + path);
                }
            } else if (event.getType() == DataEvent.TYPE_DELETED) {
                // DataItem deleted
                Log.i(TAG, "DataEvent deleted event received from Mobile device, path: " + path);
            }
        }
    }

    /**
     * Retrieves a handler that can handle the given path.
     */
    private ManagedMessageObject getHandlerForPath(String path) {
        // Find a handler for the full path
        ManagedMessageObject handler = handledPathMap.get(path);
        if (handler == null) {
            // There was no handler for the full path, see if there is a handler for a unique
            // instance path (ending with a unique timestamp)
            int index = path.lastIndexOf("/");
            String pathWithoutUniqueId = path.substring(0, index);
            handler = handledPathMap.get(pathWithoutUniqueId);
        }
        return handler;
    }

    private void deleteDataPath(Uri uri) {
        final String path = uri.getPath();
        PendingResult<DataApi.DeleteDataItemsResult> pendingResult =
                Wearable.DataApi.deleteDataItems(mGoogleApiClient, uri);
        pendingResult.setResultCallback(new ResultCallback<DataApi.DeleteDataItemsResult>() {
            @Override
            public void onResult(DataApi.DeleteDataItemsResult deleteDataItemsResult) {
                if (deleteDataItemsResult.getStatus().isSuccess()) {
                    Log.d(TAG, "Data item path was deleted ok, " + path);
                } else {
                    Log.w(TAG, "Failed to delete Data item path, " + path);
                }
            }
        });
    }

    /**
     * Handle the received message from the mobile device
     * @param item
     * @param handler
     */
    private void handleReceivedMessage(DataItem item, ManagedMessageObject handler) {
        DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
        Gson gson = new Gson();

        String json = dataMap.getString(handler.getObjectKey());
        if (json != null) {
            Object message = gson.fromJson(json, handler.getClassInMessage());
            if (message != null) {
                if (handler.isPostAsSticky()) {
                    EventBus.getDefault().postSticky(message);
                } else {
                    EventBus.getDefault().post(message);
                }
                if (handler.isStoreLocalCopy()) {
                    storeDataLocally(handler, json);
                }
            }
        }
    }

    /**
     * Handle the message read from the local Wear cache.
     * We handle this as a normal message, but we do not re-store it.
     * @param item
     */
    private void handleReceivedLocallyCachedMessage(DataItem item, ManagedMessageObject handler) {
        DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
        Gson gson = new Gson();

        String json = dataMap.getString(handler.getObjectKey());
        Object message = gson.fromJson(json, handler.getClassInMessage());
        if (handler.isPostAsSticky()) {
            EventBus.getDefault().postSticky(message);
        } else {
            EventBus.getDefault().post(message);
        }
    }

    /**
     * Stores the given JSON at a wear local path
     * @param handler
     * @param json
     */
    private void storeDataLocally(ManagedMessageObject handler, String json) {
        PutDataMapRequest putDataMapReq = PutDataMapRequest.create(handler.getLocalPath());

        putDataMapReq.getDataMap().putString(handler.getObjectKey(), json);

        PutDataRequest putDataReq = putDataMapReq.asPutDataRequest();
        PendingResult<DataApi.DataItemResult> pendingResult =
                Wearable.DataApi.putDataItem(mGoogleApiClient, putDataReq);
        pendingResult.setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
            @Override
            public void onResult(DataApi.DataItemResult dataItemResult) {
                String text;
                if (dataItemResult.getStatus().isSuccess()) {
                    text = "Data was stored locally Ok :-)";
                    Log.i(TAG, text);
                } else {
                    text = "Failed to store data locally";
                    Log.w(TAG, text);
                }
            }
        });
    }


    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        super.onMessageReceived(messageEvent);
    }

    @Override
    public void onPeerConnected(Node peer) {
        super.onPeerConnected(peer);
        Log.i(TAG, "Peer Connected " + peer.getDisplayName());
    }

    @Override
    public void onPeerDisconnected(Node peer) {
        super.onPeerDisconnected(peer);
        Log.i(TAG, "Peer Disconnected " + peer.getDisplayName());
    }

    String nodeId = null;
    /**
     * This function will read data from the DataItem if local waer data isn't set.
     */
    public void inititateData() {
        if (nodeId == null) {
            retrieveLocalNodeId();
        } else {
            if (!initialLoadDone) {
                retrieveLocalDataItems();
                initialLoadDone = true;
            }
        }
    }

    private void retrieveLocalNodeId() {
        PendingResult<NodeApi.GetLocalNodeResult> localNodePending = Wearable.NodeApi.getLocalNode(mGoogleApiClient);
        localNodePending.setResultCallback(new ResultCallback<NodeApi.GetLocalNodeResult>() {
            @Override
            public void onResult(NodeApi.GetLocalNodeResult getLocalNodeResult) {
                nodeId = getLocalNodeResult.getNode().getId();
                inititateData();
            }
        });
    }

    private Uri getLocalUri(String localPath) {
        return new Uri.Builder().scheme(PutDataRequest.WEAR_URI_SCHEME).authority(nodeId).path(localPath).build();
    }

    /**
     * Used to connect to the DataItem repository and retrieve the settings from local variables.
     */
    private void retrieveLocalDataItems() {
        Collection<ManagedMessageObject> managedhandlerList = handledPathMap.values();
        for (final ManagedMessageObject mmo : managedhandlerList) {
            PendingResult<DataApi.DataItemResult> dataItems = Wearable.DataApi.getDataItem(mGoogleApiClient, getLocalUri(mmo.getLocalPath()));
            dataItems.setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                @Override
                public void onResult(DataApi.DataItemResult dataItemResult) {
                    if (dataItemResult.getStatus().isSuccess()) {
                        if (dataItemResult != null && dataItemResult.getDataItem() != null) {
                            DataItem di = dataItemResult.getDataItem();
                            handleReceivedLocallyCachedMessage(di, mmo);
                        } else {
                            Log.w(TAG, "### There is nothing in the DataItem repository");
                        }
                    } else {
                        Log.w(TAG, "### Failed to get the data in the DataItem repository");
                    }
                }
            });
        }
    }

    /**
     * This class identifies a path/message that will be handled by this applicaiton.
     */
    private class ManagedMessageObject {
        /**
         * The data handled must be received on this path
         */
        String path;
        /**
         * The object will reside in the element with this key
         */
        String objectKey;
        /**
         * The JSON string in the objectKey element is of this type.
         */
        Class classInMessage;
        /**
         * If the received message will be posted as sticky or not on the internal eventbus after it has been received.
         */
        boolean postAsSticky;
        /**
         * If the received message will be stored in a wear local path or not after it has been received.
         * If this is true a copy of the message will be posted on the orginal path prefixed by /local
         */
        boolean storeLocalCopy;
        /**
         * If this is true the message will be deleted as soon as it has been read.
         */
        boolean deleteWhenRead;

        public ManagedMessageObject(Class classInMessage, boolean deleteWhenRead, String objectKey,
                                    String path, boolean postAsSticky, boolean storeLocalCopy) {
            this.classInMessage = classInMessage;
            this.deleteWhenRead = deleteWhenRead;
            this.objectKey = objectKey;
            this.path = path;
            this.postAsSticky = postAsSticky;
            this.storeLocalCopy = storeLocalCopy;
        }

        public Class getClassInMessage() {
            return classInMessage;
        }

        public String getObjectKey() {
            return objectKey;
        }

        public String getPath() {
            return path;
        }

        public String getLocalPath() {
            return "/local" + path;
        }

        public boolean isPostAsSticky() {
            return postAsSticky;
        }

        public boolean isStoreLocalCopy() {
            return storeLocalCopy;
        }

        public boolean isDeleteWhenRead() {
            return deleteWhenRead;
        }
    }
}
