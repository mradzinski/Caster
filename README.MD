# Caster
[![](https://jitpack.io/v/mradzinski/Caster.svg)](https://jitpack.io/#mradzinski/Caster)

Caster is a small Android library that provides a media player for Chromecast. It's fully compliant with [Google Cast v3][Cast_v3].
## Installation
Add the following repository and dependency to `build.gradle` file of your project (repository to the main gradle file, dependency to the app module):
```gradle
repositories {
    // ...
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.mradzinski:Caster:1.2.0'
}
```
## Usage
Caster requires Google Play Services and I assume that the target device has it installed (if not this example won't work).
**If you need a Caster's working example you can check the project included with the library.**

First, you need to initialize a Caster instance in every Activity you want to use it in:
```java
public class MainActivity extends AppCompatActivity {
    private Caster caster;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        caster = Caster.create(this);
    }
}
```
If you want to add a [Mini Controller widget][Mini_Controller_Info]:
```java
caster.addMiniController() // Or
caster.addMiniController(R.layout.custom_mini_controller)
```
Alternatively you can place it in your layout XML, just like in the official [Google Cast example][Mini_Controller_Implementation] (remember to change fill_parent to match_parent!).
You can also have a custom mini controller (more on this later).

To support device discovery, add a menu item in an overriden `onCreateOptionsMenu` method on your Activity:
```java
@Override
public boolean onCreateOptionsMenu(Menu menu) {
    super.onCreateOptionsMenu(menu);

    caster.addMediaRouteMenuItem(menu); // <--

    getMenuInflater().inflate(R.menu.your_menu, menu);
    return true;
}
```
Automatically, [Introduction Overlay][Introduction_Overlay] (with text "Use this button to connect with Chromecast") will be shown at the first device discovery. If you want to change it or add another language override the string with id `caster_introduction_text` in an XML resource.

You can also add a discovery button anywhere else by placing the official MediaRouteButton in an XML layout:
```xml
<android.support.v7.app.MediaRouteButton
    android:id="@+id/media_route_button"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content" />
```
And then set in up in your Activity:
```java
MediaRouteButton mediaRouteButton = (MediaRouteButton) findViewById(R.id.media_route_button);
caster.setUpMediaRouteButton(mediaRouteButton);
```
Althugh not necessary (you can manually setup everything), you can add the above functionality (except `MediaRouteButton`) simply by extending `CasterActivity`. It will add a `caster` field and set up the rest for you:
```java
public class MainActivity extends CasterActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (caster.isConnected()) {
            caster.getPlayer().loadMediaAndPlay(...)
        }
    }
}
```
All media player actions are included in a `CasterPlayer` object, which you can access by calling `caster.getPlayer()`.
When you are connected to the device, you can play media the following way:
```java
MediaData mediaData = new MediaData.Builder(VIMEO_URL)
    .setStreamType(MediaData.STREAM_TYPE_BUFFERED)
    .setContentType("application/x-mpegURL") // Or "videos/mp4"... or any supported content type
    .setMediaType(MediaData.MEDIA_TYPE_MOVIE)
    .setTitle("Two birds, many stones.")
    .setDescription("Isaac searches for Rebekah to retrieve Arachnid's stolen XP.")
    .setThumbnailUrl("...")
    .setPlaybackRate(MediaData.PLAYBACK_RATE_NORMAL)
    .setAutoPlay(true)
    .build();

caster.getPlayer().loadMediaAndPlay(mediaData);
```
Alternativly you can use `loadMediaAndPlay(MediaInfo, autoPlay, position, playbackRate)` similar to [Google Cast example][Cast_load_media], though this is more limited and less flexible.

To react to Chromecast connect and disconnect events, you can simply register a listener:
```java
caster.setOnConnectChangeListener(new Caster.OnConnectChangeListener() {
    @Override
    public void onConnected() {
        Log.d("Caster", "Connected with Chromecast");
    }
    
    @Override
    public void onDisconnected() {
        Log.d("Caster" "Disconnected from Chromecast");
    }
});
```
### Custom usage
In case the library doesn't fit you, there's the possibility to change everything like in Google Cast v3.
You can set a receiver ID or even the whole `CastOptions` in your `Application` class:
```java
Caster.configure(receiverId); // Or
Caster.configure(customCastOptions); // Rr
Caster.configure(launchOptions)
```
Get `CastContext` by calling:
```java
CastContext.getSharedInstance(context);
```
Get `CastSession` (so `RemoteMediaClient`) by register OnCastSessionUpdatedListener:
```java
caster.setOnCastSessionUpdatedListener(new Caster.OnCastSessionUpdatedListener() {
    @Override
    public void onCastSessionUpdated(CastSession castSession) {
        if (castSession != null) {
            RemoteMediaClient remoteMediaClient = castSession.getRemoteMediaClient();
            //...
        }
    }
});
```
## Styling
Caster allows for custom styling of the mini controller and the expanded controls activity. For styling the mini controller
you must add your own mini controller XML layout and pass it to your Caster instance.
```xml
<fragment
    android:id="@+id/caster_mini_controller"
    class="com.google.android.gms.cast.framework.media.widget.MiniControllerFragment"
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:visibility="gone"
    app:castProgressBarColor="@color/green"
    app:castBackground="@color/dark_gray"
    app:castButtonColor="@color/white"
    app:castTitleTextAppearance="@style/MiniControllerTextAppearace"
    app:castSubtitleTextAppearance="@style/MiniControllerTextAppearace"/>

    <!-- More styling props are available at https://developers.google.com/cast/docs/android_sender_advanced#customize-theme-mini-controller -->
```
```java
caster.addMiniController(R.layout.custom_mini_controller);
```
For styling the expanded controls activity, you must build an instance of the `ExpandedControlsStyle` class and pass it to
your Caster instance before any connection is available.
```java
ExpandedControlsStyle style = new ExpandedControlsStyle.Builder()
        .setSeekbarLineColor(getResources().getColor(R.color.green))
        .setSeekbarThumbColor(getResources().getColor(R.color.white))
        .setStatusTextColor(getResources().getColor(R.color.green))
        .build();

caster.setExpandedPlayerStyle(style);
```
## Sample
A sample app for Caster is included withing this repository. Please feel free to check it out before filing any issues :)

## License
[MIT][License]

[//]: #
   [Cast_v3]: <https://developers.google.com/cast/docs/developers>
   [Mini_Controller_Info]: <https://developers.google.com/cast/docs/design_checklist/sender#sender-mini-controller>
   [Mini_Controller_Implementation]: <https://developers.google.com/cast/docs/android_sender_integrate#add_mini_controller>
   [Cast_load_media]: <https://developers.google.com/cast/docs/android_sender_integrate#load_media>
   [Introduction_Overlay]: <https://developers.google.com/cast/docs/design_checklist/cast-button#prompting>
   [License]: <https://github.com/mradzinski/Caster/blob/master/LICENSE>
