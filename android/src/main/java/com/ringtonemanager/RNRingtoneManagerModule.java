package com.ringtonemanager;

import android.content.ContentValues;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import android.media.MediaMetadataRetriever;
import android.util.Log;

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
        public static final String START = "start";
        public static final String END = "end";
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
            String[] columns = { MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.IS_MUSIC };
            String selection = MediaStore.Audio.Media.IS_MUSIC + " != ?";
            String[] selectionArgs = new String[] { "0" };
            String sortOrder = MediaStore.Audio.Media.DISPLAY_NAME + " ASC";

            Uri externalUri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                externalUri = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
            } else {
                externalUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            }

            try (Cursor musicCursor = this.reactContext.getContentResolver().query(externalUri, columns, selection, selectionArgs, sortOrder)) {
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

            successCallback.invoke(result);
        }catch(Exception e){
            successCallback.invoke(null, e.getMessage());
        }
    }

    @ReactMethod
    public void setRingtone(ReadableMap settings, Callback successCallback) {
        try{
            /* FILE */
            File ringtoneFile = new File(settings.getString(SettingsKeys.URI));
            if (ringtoneFile.exists()) {

                /* META DATA */
                MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
                mediaMetadataRetriever.setDataSource(ringtoneFile.getAbsolutePath());

                String ringtoneTitle = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
                String ringtoneArtist = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
                String ringtoneDuration = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                String ringtoneMime_type = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE);
                boolean ringtoneIsRingtone = settings.getBoolean(SettingsKeys.IS_RINGTONE);
                boolean ringtoneIsNotification = settings.getBoolean(SettingsKeys.IS_NOTIFICATION);
                boolean ringtoneIsAlarm = settings.getBoolean(SettingsKeys.IS_ALARM);

                /* CONTENT VALUES */
                ContentValues values = new ContentValues();
                values.put(MediaStore.Audio.Media.TITLE, ringtoneTitle);
                values.put(MediaStore.Audio.Media.ARTIST, ringtoneArtist);
                values.put(MediaStore.Audio.Media.ALBUM, "Troca Toque");
                values.put(MediaStore.MediaColumns.MIME_TYPE, ringtoneMime_type);
                values.put(MediaStore.Audio.Media.IS_MUSIC, false);

                /* BUFFER */
                BufferedInputStream buffer = new BufferedInputStream(new FileInputStream(ringtoneFile));

                /* FILE INFO */
                int bytes_size = (int) ringtoneFile.length();

                /* TRIM */
                int time_start = settings.getInt(SettingsKeys.START);
                int time_end = settings.getInt(SettingsKeys.END);
                if(time_start>0 && time_end>0){
                    int bitrate = (int) ringtoneFile.length() / Integer.parseInt(ringtoneDuration);
                    bytes_size = (time_end - time_start) * bitrate;
                    ringtoneDuration = ""+ (time_end - time_start);
                    int bytes_garbage_size = time_start * bitrate;
                    byte[] bytes_garbage = new byte[bytes_garbage_size];

                    /* BUFFER */
                    buffer.read(bytes_garbage, 0, bytes_garbage_size);
                }

                byte[] bytes = new byte[bytes_size];

                /* BUFFER */
                buffer.read(bytes, 0, bytes_size);
                buffer.close();

                /* CONTENT VALUES */
                values.put(MediaStore.Audio.Media.DURATION, ringtoneDuration);

                /* IS RINGTONE */
                if(ringtoneIsRingtone){
                    String newRingtoneFilePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RINGTONES).toString() + File.separator + "TrocaToque - Toque.mp3";

                    /* CREATE NEW FILE */
                    OutputStream stream = new FileOutputStream(newRingtoneFilePath);
                    stream.write(bytes);
                    stream.close();
                    stream.flush();

                    File newRingtoneFile = new File(newRingtoneFilePath);

                    /* CONTENT VALUES */
                    values.put(MediaStore.MediaColumns.DISPLAY_NAME, "TrocaToque - Toque.mp3");
                    values.put(MediaStore.MediaColumns.DATA, newRingtoneFile.getAbsolutePath());
                    values.put(MediaStore.MediaColumns.SIZE, newRingtoneFile.length());
                    values.put(MediaStore.Audio.Media.IS_RINGTONE, true);
                    values.put(MediaStore.Audio.Media.IS_NOTIFICATION, false);
                    values.put(MediaStore.Audio.Media.IS_ALARM, false);

                    Uri uri = MediaStore.Audio.Media.getContentUriForPath(newRingtoneFile.getAbsolutePath());

                    try{
                        this.reactContext.getContentResolver().delete(uri, MediaStore.MediaColumns.DATA + "=?", new String[]{ newRingtoneFile.getAbsolutePath() });
                    }catch(Exception ignored){

                    }

                    Uri newUri = this.reactContext.getContentResolver().insert(uri, values);

                    /* CREATE NEW FILE */
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        stream = this.reactContext.getContentResolver().openOutputStream(newUri);
                        stream.write(bytes);
                        stream.close();
                        stream.flush();
                    }

                    // /* SET RINGTONE */
                    RingtoneManager.setActualDefaultRingtoneUri(this.reactContext, RingtoneManager.TYPE_RINGTONE, newUri);

                }

                /* IS NOTIFICATION */
                if(ringtoneIsNotification){
                    try {
                        String newRingtoneFilePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RINGTONES).toString() + File.separator + "TrocaToque - Notificação.mp3";

                        /* CREATE NEW FILE */
                        OutputStream stream = new FileOutputStream(newRingtoneFilePath);
                        stream.write(bytes);
                        stream.close();
                        stream.flush();

                        File newRingtoneFile = new File(newRingtoneFilePath);

                        /* CONTENT VALUES */
                        values.put(MediaStore.MediaColumns.DISPLAY_NAME, "TrocaToque - Notificação.mp3");
                        values.put(MediaStore.MediaColumns.DATA, newRingtoneFile.getAbsolutePath());
                        values.put(MediaStore.MediaColumns.SIZE, newRingtoneFile.length());
                        values.put(MediaStore.Audio.Media.IS_RINGTONE, false);
                        values.put(MediaStore.Audio.Media.IS_NOTIFICATION, true);
                        values.put(MediaStore.Audio.Media.IS_ALARM, false);

                        Uri uri = MediaStore.Audio.Media.getContentUriForPath(newRingtoneFile.getAbsolutePath());

                        try{
                            this.reactContext.getContentResolver().delete(uri, MediaStore.MediaColumns.DATA + "=?", new String[]{ newRingtoneFile.getAbsolutePath() });
                        }catch(Exception ignored){

                        }

                        Uri newUri = this.reactContext.getContentResolver().insert(uri, values);

                        /* CREATE NEW FILE */
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            stream = this.reactContext.getContentResolver().openOutputStream(newUri);
                            stream.write(bytes);
                            stream.close();
                            stream.flush();
                        }

                        /* SET RINGTONE */
                        RingtoneManager.setActualDefaultRingtoneUri(this.reactContext, RingtoneManager.TYPE_NOTIFICATION, newUri);

                    } catch (Exception e) {
                        successCallback.invoke(false, e.getMessage());
                    }
                }

                /* IS ALARM */
                if(ringtoneIsAlarm){
                    try {
                        String newRingtoneFilePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RINGTONES).toString() + File.separator + "TrocaToque - Alarme.mp3";

                        /* CREATE NEW FILE */
                        OutputStream stream = new FileOutputStream(newRingtoneFilePath);
                        stream.write(bytes);
                        stream.close();
                        stream.flush();

                        File newRingtoneFile = new File(newRingtoneFilePath);

                        /* CONTENT VALUES */
                        values.put(MediaStore.MediaColumns.DISPLAY_NAME, "TrocaToque - Alarme.mp3");
                        values.put(MediaStore.MediaColumns.DATA, newRingtoneFile.getAbsolutePath());
                        values.put(MediaStore.MediaColumns.SIZE, newRingtoneFile.length());
                        values.put(MediaStore.Audio.Media.IS_RINGTONE, false);
                        values.put(MediaStore.Audio.Media.IS_NOTIFICATION, false);
                        values.put(MediaStore.Audio.Media.IS_ALARM, true);

                        Uri uri = MediaStore.Audio.Media.getContentUriForPath(newRingtoneFile.getAbsolutePath());

                        try{
                            this.reactContext.getContentResolver().delete(uri, MediaStore.MediaColumns.DATA + "=?", new String[]{ newRingtoneFile.getAbsolutePath() });
                        }catch(Exception ignored){

                        }

                        Uri newUri = this.reactContext.getContentResolver().insert(uri, values);

                        /* CREATE NEW FILE */
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            stream = this.reactContext.getContentResolver().openOutputStream(newUri);
                            stream.write(bytes);
                            stream.close();
                            stream.flush();
                        }

                        /* SET RINGTONE */
                        RingtoneManager.setActualDefaultRingtoneUri(this.reactContext, RingtoneManager.TYPE_ALARM, newUri);

                    } catch (Exception e) {
                        successCallback.invoke(false, e.getMessage());
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