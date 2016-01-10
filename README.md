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