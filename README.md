# README #

# Who should use this library? #

You should use this library if you (like me) really like the [Greenrobot eventbus](https://github.com/greenrobot/EventBus) concept and are using it throughout you android application and now want to extend your mobile application with an Android wear application.

# What is this repository for? #

This Android library is used to hide the complexity of communication between a phone and an Android wear watch.

Using this you can work with the [Greenrobot eventbus](https://github.com/greenrobot/EventBus) on both sides without having to worry about the phone <-> wear communication.

You post an event on the EventBus on the sender side (mobile or watch) and get the event in the onEvent function on your other device, just as you would within your application.

# How do I get set up? #

## Summary of set up ##

To use this library you need to copy the code to your Android project and add the project to your ***settings.gradle*** file.

Ex: 
```
#!java

include ':mobile', ':wear', ':eventbus_wear'
```



## Dependencies ##

This library works together with the Greenrobot eventbus concept, so your mobile & wear projects should import the latest version of this library.

It also uses the gson library for the data transfer.

```
#!gradle

    compile 'com.google.code.gson:gson:2.3.1'
    compile 'de.greenrobot:eventbus:2.4.0'

```

## Steps to do for a new event to be transferred between phone & wear device ##

### Once per mobile/wear application ###

The following steps needs to be done once for the mobile and once for the wear projects.

1. Create a gateway class which extends EventbusDataLayerGateway
2. Create a proxy service that extends EventbusDataLayerProxyService
3. Add a service block for the proxy service in the ***AndroidManifest.xml*** file
4. Add the eventbus_wear to the ***build.gradle*** file for both the mobile & wear projects

```
#!gradle

compile project(':eventbus_wear')
```



#### Proxy service ####

The proxy service listens to incoming messages and will parse & resend these on the local eventbus on the phone or wear device.

#### Gateway ####

The gateway listens to the events that should be routed to the other device. It will then use the send/sync functions to send these to the other device.

### Do the following for each new event ###

1. Create Event class to be used

#### In the device that sends the event ####
2. Add an onEvent in the gateway class and call one of the 4 send/sync functions in the parent class.

#### In the device that receives the event ####
2. Add a class to handle in the proxy service class's onCreate function using the handleMessageClass-function

## Examples ##

In the example files here below the producer sends an SettingsChangesEvent and the consumer receives this event.

#### Gateway class example ####

```
#!java


package eu.miman.eventbuswear.example.service;

import android.content.Context;
import android.util.Log;

import eu.miman.util.eventbus.wear.EventbusDataLayerGateway;

import de.greenrobot.event.EventBus;

/**
 * This Gateway is used to send data to the phone/wear device.
 */
public class MyGateway extends EventbusDataLayerGateway {
    private static final String TAG = "MyGateway";

    public MyGateway(Context parentContext) {
        super(parentContext);
        EventBus.getDefault().register(this);
    }

    public void onDestroy() {
        Log.v(TAG, "Destroyed");
    }

    /**
     * Example eventbus event handler
     * @param event
     */
    public void onEvent(SettingsChangedEvent event) {
        Log.d(TAG, "SettingsChangedEvent received");
        sendToDeviceAlways(event);
    }
}

```

#### Service example ####


```
#!java

package eu.miman.eventbuswear.example.service;

import android.util.Log;
import eu.miman.util.eventbus.wear.EventbusDataLayerProxyService;
import de.greenrobot.event.EventBus;

/**
 * This service listens to data from the mobile device.
 */
public class MyProxyService extends EventbusDataLayerProxyService {
    private static final String TAG = "MyProxyService";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Created");

        handleMessageClass(SettingsChangedEvent.class, true, false, false);
        EventBus.getDefault().register(this);
    }
}

```

#### Example of service block in AndroidManifest.xml ####


```
#!xml
    <application>
...
        <service android:name=".service.MyProxyService" >
            <intent-filter>
                <action android:name="com.google.android.gms.wearable.BIND_LISTENER" />
            </intent-filter>
        </service>
    </application>
</manifest>
```