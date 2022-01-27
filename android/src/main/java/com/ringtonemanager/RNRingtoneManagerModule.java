package com.ringtonemanager;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.media.RingtoneManager;
import android.net.Uri;
import android.provider.MediaStore;
import android.database.Cursor;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.Arguments;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import android.annotation.SuppressLint;
import android.content.ContentUris;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.media.MediaMetadataRetriever;

public class RNRingtoneManagerModule extends ReactContextBaseJavaModule {

    private final ReactApplicationContext reactContext;
    private static final String TYPE_ALARM_KEY = "TYPE_ALARM";
    private static final String TYPE_ALL_KEY = "TYPE_ALL";
    private static final String TYPE_NOTIFICATION_KEY = "TYPE_NOTIFICATION";
    private static final String TYPE_RINGTONE_KEY = "TYPE_RINGTONE";

    final static class SettingsKeys {
        public static final String URI = "uri";
        public static final String TITLE = "title";
        public static final String ARTIST = "artist";
        public static final String IS_RINGTONE = "IsRingtone";
        public static final String IS_NOTIFICATION = "IsNotification";
        public static final String IS_ALARM = "IsAlarm";
    }

    public RNRingtoneManagerModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
    }

    @Override
    public String getName() {
        return "RingtoneManager";
    }

    @ReactMethod
    public void getRingtone(int ringtoneType, Callback successCallback) {
        try{
            Uri ringtoneUri = RingtoneManager.getActualDefaultRingtoneUri(this.reactContext, ringtoneType);

            Cursor musicCursor = this.reactContext.getContentResolver().query(ringtoneUri, new String[] { MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM }, null, null, null);
            if (musicCursor != null && musicCursor.moveToFirst()) {
                String title = musicCursor.getString(musicCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE));
                String artist = musicCursor.getString(musicCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST));
                String album = musicCursor.getString(musicCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM));

                WritableMap result = Arguments.createMap();
                result.putString("title", title);
                result.putString("artist", artist);
                result.putString("album", album);

                successCallback.invoke(result);
            }else{
                successCallback.invoke(ringtoneUri.getPath());
            }
        }catch(Exception e){
            successCallback.invoke(e.getMessage());
        }
    }

    @ReactMethod
    public void getSongs(Callback successCallback) {
        try{
            WritableArray result = Arguments.createArray();

            String[] STAR = { "*" };
            Uri musicUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            try (Cursor musicCursor = this.reactContext.getContentResolver().query(musicUri, STAR, null, null, null)) {

                if (musicCursor != null && musicCursor.moveToFirst()) {
                    while (musicCursor.moveToNext()) {

                        try{
                            String uri = musicCursor.getString(musicCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA));

                            MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
                            mediaMetadataRetriever.setDataSource(uri);

                            String title = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
                            String artist = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);

                            WritableMap data = Arguments.createMap();
                            data.putString("title", title);
                            data.putString("artist", artist);
                            data.putString("uri", uri);

                            result.pushMap(data);
                        }catch(Exception ignored){}
                    }
                }
            }

            successCallback.invoke(result);
        }catch(Exception e){
            successCallback.invoke(null, e.getMessage());
        }
    }

    @ReactMethod
    public void setRingtone(ReadableMap settings, Callback successCallback) {
        try{
            File ringtoneFile = new File(settings.getString(SettingsKeys.URI));

            MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
            mediaMetadataRetriever.setDataSource(ringtoneFile.getAbsolutePath());

            if (ringtoneFile.exists() && getCurrentActivity() != null) {

                String ringtoneTitle = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
                String ringtoneArtist = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
                String ringtoneDuration = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                String ringtoneMime_type = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE);
                boolean ringtoneIsRingtone = settings.getBoolean(SettingsKeys.IS_RINGTONE);
                boolean ringtoneIsNotification = settings.getBoolean(SettingsKeys.IS_NOTIFICATION);
                boolean ringtoneIsAlarm = settings.getBoolean(SettingsKeys.IS_ALARM);

                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DATA, ringtoneFile.getAbsolutePath());
                values.put(MediaStore.MediaColumns.SIZE, ringtoneFile.length());
                values.put(MediaStore.MediaColumns.TITLE, ringtoneTitle);
                values.put(MediaStore.Audio.Media.ARTIST, ringtoneArtist);
                values.put(MediaStore.Audio.Media.ALBUM, "Troca Toque");
                values.put(MediaStore.Audio.Media.DURATION, ringtoneDuration);
                values.put(MediaStore.MediaColumns.MIME_TYPE, ringtoneMime_type);
                values.put(MediaStore.Audio.Media.IS_RINGTONE, ringtoneIsRingtone);
                values.put(MediaStore.Audio.Media.IS_NOTIFICATION, ringtoneIsNotification);
                values.put(MediaStore.Audio.Media.IS_ALARM, ringtoneIsAlarm);
                values.put(MediaStore.Audio.Media.IS_MUSIC, false);

                Uri uri = MediaStore.Audio.Media.getContentUriForPath(ringtoneFile.getAbsolutePath());
                Uri newUri = null;

                if(ringtoneIsRingtone){
                    try{
                        this.reactContext.getContentResolver().delete(uri,MediaStore.MediaColumns.DATA + "=?", new String[]{ringtoneFile.getAbsolutePath()});
                    }catch(Exception ignored){}

                    newUri = this.reactContext.getContentResolver().insert(uri, values);

                    if(newUri!=null) {
                        RingtoneManager.setActualDefaultRingtoneUri(this.reactContext, RingtoneManager.TYPE_RINGTONE, newUri);
                    }
                }

                if(ringtoneIsNotification){
                    if(!ringtoneIsRingtone){
                        try{
                            this.reactContext.getContentResolver().delete(uri,MediaStore.MediaColumns.DATA + "=?", new String[]{ringtoneFile.getAbsolutePath()});
                        }catch(Exception ignored){}

                        newUri = this.reactContext.getContentResolver().insert(uri, values);
                    }

                    if(newUri!=null) {
                        RingtoneManager.setActualDefaultRingtoneUri(this.reactContext, RingtoneManager.TYPE_NOTIFICATION, newUri);
                    }
                }

                if(ringtoneIsAlarm){
                    if(!ringtoneIsRingtone && !ringtoneIsNotification){
                        try{
                            this.reactContext.getContentResolver().delete(uri,MediaStore.MediaColumns.DATA + "=?", new String[]{ringtoneFile.getAbsolutePath()});
                        }catch(Exception ignored){}

                        newUri = this.reactContext.getContentResolver().insert(uri, values);
                    }

                    if(newUri!=null) {
                        RingtoneManager.setActualDefaultRingtoneUri(this.reactContext, RingtoneManager.TYPE_ALARM, newUri);
                    }
                }

                successCallback.invoke(true);
            }else{
                successCallback.invoke(false, "Invalid Uri");
            }
        }catch(Exception e){
            successCallback.invoke(false, e.getMessage());
        }
    }

    @Override
    public Map<String, Object> getConstants() {
        final Map<String, Object> constants = new HashMap<>();
        constants.put(TYPE_ALARM_KEY, RingtoneManager.TYPE_ALARM);
        constants.put(TYPE_NOTIFICATION_KEY, RingtoneManager.TYPE_NOTIFICATION);
        constants.put(TYPE_RINGTONE_KEY, RingtoneManager.TYPE_RINGTONE);
        constants.put(TYPE_ALL_KEY, RingtoneManager.TYPE_ALL);
        return constants;
    }

    /**
     * Returns true when the given ringtone type matches the ringtone to compare.
     * Will default to true if the given ringtone type is RingtoneManager.TYPE_ALL.
     *
     * @param ringtoneType          ringtone type given
     * @param ringtoneTypeToCompare ringtone type to compare to
     * @return true if the type matches or is TYPE_ALL
     */
    private boolean isRingtoneType(int ringtoneType, int ringtoneTypeToCompare) {
        return ringtoneTypeToCompare == ringtoneType || RingtoneManager.TYPE_ALL == ringtoneType;
    }
}