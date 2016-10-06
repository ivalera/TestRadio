package ru.valera.testradio;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity{

    private RadioStreamService streamService;
    private boolean streamServiceIsBound = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Intent intent = new Intent(this, RadioStreamService.class);
        startService(intent);
        bindService(intent, streamServiceConnection, BIND_AUTO_CREATE);

    }
    private void setPlayButtonListeners() {
        final Context context = getApplicationContext();
        final PlayButtonAnimator pba = new PlayButtonAnimator(MainActivity.this, this);
        final ImageButton playButton = (ImageButton) findViewById(R.id.play_button);

        if (playButton == null) {
            throw new IllegalStateException();
        }

        streamService.setContext(context);

        try {
            if (streamService.isLoading()) {
                pba.changeState(PlayButtonAnimator.STATE_LOADING, false);
            }
            if (streamService.isPlaying()) {
                pba.changeState(PlayButtonAnimator.STATE_PLAYING, false);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        playButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (streamService.isPlaying()) {
                    try {
                        pba.changeState(PlayButtonAnimator.STATE_STOPPED, true);

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    streamService.stop();
                }
                else if (streamService.isLoading()) {
                    streamService.stop();
                    try {
                        pba.changeState(PlayButtonAnimator.STATE_STOPPED, true);

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                else {
                    try {
                        pba.changeState(PlayButtonAnimator.STATE_LOADING, true);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    boolean success = streamService.play(new Handler.Callback() {
                        public boolean handleMessage(Message msg) {
                            try {
                                switch (msg.arg1) {
                                    case RadioStreamService.STATE_PLAYING:
                                      //  streamService.showNotification();
                                        pba.changeState(PlayButtonAnimator.STATE_PLAYING, true);
                                        break;
                                    case RadioStreamService.STATE_BUFFERING:
                                        pba.changeState(PlayButtonAnimator.STATE_LOADING, true);
                                        break;
                                    case RadioStreamService.STATE_STOPPED:
                                        pba.changeState(PlayButtonAnimator.STATE_STOPPED, true);
                                        break;
                                    case RadioStreamService.STATE_DUCKING:
                                        // volume has been lowered
                                        break;
                                    default:
                                        break;
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }

                            return false;
                        }
                    });

                    if (!success) {
                        try {
                            pba.changeState(PlayButtonAnimator.STATE_STOPPED, false);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                        playButton.setEnabled(true);

                        String errorMessage = getResources().getString(R.string.stream_error);

                        Toast.makeText(
                                MainActivity.this,
                                errorMessage,
                                Toast.LENGTH_LONG
                        ).show();
                    }
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (streamServiceIsBound) {
            unbindService(streamServiceConnection);
        }
        super.onDestroy();
    }

    private ServiceConnection streamServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            RadioStreamService.MyLocalBinder binder = (RadioStreamService.MyLocalBinder) service;
            streamService = binder.getService();
            streamServiceIsBound = true;
            setPlayButtonListeners();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            streamServiceIsBound = false;
        }
    };

}
