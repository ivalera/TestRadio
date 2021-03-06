package ru.valera.testradio;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.app.NotificationCompat;
import android.widget.RemoteViews;
import android.widget.Toast;

import org.greenrobot.eventbus.EventBus;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import ru.valera.testradio.events.CurrentButtonPlayStopState;

/**
 * Created by Valera on 26.09.2016.
 */
public class RadioStreamService extends Service {

    public static final int STATE_PLAYING = 1;
    public static final int STATE_BUFFERING = 2;
    public static final int STATE_DUCKING = 3;
    public static final int STATE_STOPPED = 4;
    private static final int NOTIFICATION_ID = 1;
    private static final String STREAM_URL = "http://radio.alania.net:8000/radiocity";
    ;

    private final IBinder binder = new MyLocalBinder();
    private Context context;
    private static MediaPlayer mediaPlayer;
    private static boolean isLoading = false;
    private static boolean isPaused = false;
    private AudioManager.OnAudioFocusChangeListener mOnAudioFocusChangeListener;
    private Handler.Callback playCallback = null;

    public RadioStreamService() {
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mOnAudioFocusChangeListener = new AudioManager.OnAudioFocusChangeListener() {
            @Override
            public void onAudioFocusChange(int focusChange) {
                final Message message = new Message();

                switch (focusChange) {
                    case AudioManager.AUDIOFOCUS_GAIN:
                        play(playCallback);
                        mediaPlayer.setVolume(1.0f, 1.0f);
                        break;

                    case AudioManager.AUDIOFOCUS_LOSS:
                        message.arg1 = STATE_STOPPED;
                        playCallback.handleMessage(message);
                        stop();
                        break;

                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                        // Lost focus for a short time, but we have to stop
                        // playback. We don't release the media player because playback
                        // is likely to resume
                        if (mediaPlayer.isPlaying()) {
                            mediaPlayer.pause();
                            isPaused = true;
                            message.arg1 = STATE_STOPPED;
                            playCallback.handleMessage(message);
                        }
                        break;

                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                        // Lost focus for a short time, but it's ok to keep playing
                        // at an attenuated level
                        if (mediaPlayer.isPlaying()) mediaPlayer.setVolume(0.1f, 0.1f);
                        break;
                }

            }
        };

        return START_STICKY;
    }


    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        stop();
        super.onDestroy();
    }

    public void setContext(Context c) {
        context = c;
    }

    private MediaPlayer getPlayer() {
        if (mediaPlayer != null) {
            return mediaPlayer;
        }

        MediaPlayer player = new MediaPlayer();
        player.setAudioStreamType(AudioManager.STREAM_MUSIC);

        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "URN Android App");

        try {
            player.setDataSource(this.context, Uri.parse(getStreamUrl()), headers);
        } catch (IOException e) {
            e.printStackTrace();
        }

        mediaPlayer = player;
        return player;
    }

    private String getStreamUrl() {
        return STREAM_URL;
    }

    public boolean isPlaying() {
        return mediaPlayer != null && getPlayer().isPlaying();
    }

    public boolean isLoading() {
        return isLoading;
    }

    public boolean play(final Handler.Callback callback) {
        if (!isNetworkConnected()) {
            return false;
        }

        playCallback = callback;

        MediaPlayer player = getPlayer();

        if (player.isPlaying()) {
            return true;
        }

        final Message message = new Message();

        player.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer player) {
                AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                int result = audioManager.requestAudioFocus(mOnAudioFocusChangeListener, AudioManager.STREAM_MUSIC,
                        AudioManager.AUDIOFOCUS_GAIN);

                if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    message.arg1 = STATE_STOPPED;
                } else {
                    showNotification();
                    message.arg1 = STATE_PLAYING;
                    player.start();
                }

                callback.handleMessage(message);
                isLoading = false;
            }
        });

        player.setOnInfoListener(new MediaPlayer.OnInfoListener() {
            @Override
            public boolean onInfo(MediaPlayer mp, int what, int extra) {
                switch (what) {
                    case MediaPlayer.MEDIA_INFO_BUFFERING_START:
                        isLoading = true;
                        message.arg1 = STATE_BUFFERING;
                        callback.handleMessage(message);
                        break;
                    case MediaPlayer.MEDIA_INFO_BUFFERING_END:
                        isLoading = false;
                        message.arg1 = STATE_PLAYING;
                        callback.handleMessage(message);
                        break;
                }
                return false;
            }
        });

        player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                stop();
                play(callback);
                message.arg1 = STATE_BUFFERING;
                callback.handleMessage(message);
            }
        });

        player.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                stop();
                message.arg1 = STATE_STOPPED;
                callback.handleMessage(message);
                return false;
            }
        });

        if (isPaused) {
            player.start();
            isLoading = true;
            isPaused = false;
        } else {
            try {
                isLoading = true;
                player.prepareAsync();
            } catch (IllegalStateException e) {
                isLoading = false;
                message.arg1 = STATE_STOPPED;
                callback.handleMessage(message);
                stop();
            }
        }

        return true;
    }

    public void stop() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        isLoading = false;
        isPaused = false;
        stopSelf();
        stopForeground(true);
        cancelNotification(context);
    }


    private boolean isNetworkConnected() {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        return cm.getActiveNetworkInfo() != null;
    }

    /**
     * Binder that allows local classes to communicate with the service.
     *
     * @author johannes
     */

    public class MyLocalBinder extends Binder {
        RadioStreamService getService() {
            return RadioStreamService.this;
        }
    }

    public void showNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
        Intent i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent intent = PendingIntent.getActivity(this, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(intent);
        builder.setTicker(getResources().getString(R.string.custom_notification));

        builder.setSmallIcon(R.drawable.ic_mic_black_36dp);

        //builder.setAutoCancel(true);

        Notification notification = builder.build();

        RemoteViews contentView = new RemoteViews(context.getPackageName(), R.layout.notification);

        final String text = getResources().getString(R.string.radio_text);
        contentView.setTextViewText(R.id.textView, text);

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingNotificationIntent = PendingIntent.getActivity(this, 0,
                notificationIntent, 0);

        notification.contentView = contentView;
        notification.contentIntent = pendingNotificationIntent;
        notification.flags |= Notification.FLAG_NO_CLEAR;

        Intent playIntent = new Intent(context, MediaListener.class);
        playIntent.setAction("play");
        PendingIntent pendingPlayIntent = PendingIntent.getBroadcast(context, 0, playIntent, 0);
        contentView.setOnClickPendingIntent(R.id.notif_play, pendingPlayIntent);

        Intent pauseIntent = new Intent(context, MediaListener.class);
        pauseIntent.setAction("pause");
        PendingIntent pendingPauseIntent = PendingIntent.getBroadcast(context, 0, pauseIntent, 0);
        contentView.setOnClickPendingIntent(R.id.notif_pause, pendingPauseIntent);

        Intent closeIntent = new Intent(context, MediaListener.class);
        closeIntent.setAction("close");
        PendingIntent pendingCloseIntent = PendingIntent.getBroadcast(context, 0, closeIntent, 0);
        contentView.setOnClickPendingIntent(R.id.notif_close, pendingCloseIntent);

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(NOTIFICATION_ID, notification);


    }


    public static void cancelNotification(Context context) {
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(NOTIFICATION_ID);
    }

    public static class MediaListener extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {

            RadioStreamService streamService = new RadioStreamService();

            String action = intent.getAction();
            String actionPlay = "play";
            String actionPause = "pause";
            String actionClose = "close";

            MediaPlayer player = streamService.getPlayer();

            if (actionPlay.equals(action)) {
                Toast.makeText(context, "play", Toast.LENGTH_SHORT).show();

                player.start();

            } else if (actionPause.equals(action)) {
                Toast.makeText(context, "pause", Toast.LENGTH_SHORT).show();

                player.pause();
            } else if (actionClose.equals(action)) {
                RadioStreamService.cancelNotification(context);
                EventBus.getDefault().post(new CurrentButtonPlayStopState());
            }
        }
    }
}

