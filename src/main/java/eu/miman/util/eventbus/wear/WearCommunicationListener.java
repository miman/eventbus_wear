package eu.miman.util.eventbus.wear;

import com.google.android.gms.wearable.DataApi;

/**
 * Created by Mikael on 2015-12-28.
 */
public interface WearCommunicationListener {

    public void wearCommSucceeded(DataApi.DataItemResult dataItemResult);
    public void wearCommFailed(DataApi.DataItemResult dataItemResult);
}
