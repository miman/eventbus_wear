# README #

# What is this repository for? #

An Android library used to hide the complexity for communication  between a phone and an Android wear watch.

Using this you can work with the Greenrobot eventbus on both sides without having to worry about the phone <-> wear communication.

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
3. Add a service block for the proxy service in the AndroidManifest.xml file

#### Gateway class example ####

#### Example of service block in AndroidManifest.xml ####


```
#!xml

        <service android:name=".service.HeartbeatDataService" >
            <intent-filter>
                <action android:name="com.google.android.gms.wearable.BIND_LISTENER" />
            </intent-filter>
        </service>

```


### Do the following for each new event ###

1. Create Event class to be used
2. 