package ru.valera.testradio;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.support.graphics.drawable.AnimatedVectorDrawableCompat;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

import com.bumptech.glide.Glide;

public class PlayButtonAnimator {
    public final static int STATE_STOPPED = 0;
    public final static int STATE_PLAYING = 1;
    public final static int STATE_LOADING = 2;

    private static int currentState = STATE_STOPPED;
    private Context context;
    private Activity activity;

    private ImageButton playButton;
    private ProgressBar spinner;

    private LinearLayout llGif;

    private AnimatedVectorDrawableCompat avdPlayToStop;
    private AnimatedVectorDrawableCompat avdStopToPlay;

    public PlayButtonAnimator(Context context, Activity activity) {
        this.context=context;
        this.activity=activity;

        playButton = (ImageButton) activity.findViewById(R.id.play_button);
        spinner = (ProgressBar) activity.findViewById(R.id.play_button_load_spinner);

        ImageView imageView = (ImageView) activity.findViewById(R.id.view_gif);
        Glide.with(context).load(R.drawable.equalizer).asGif().into(imageView);

        llGif = (LinearLayout) activity.findViewById(R.id.ll_gif);

        if (Build.VERSION.SDK_INT >= 23) {
            avdPlayToStop = AnimatedVectorDrawableCompat.create(context, R.drawable.avd_play_stop);
            avdStopToPlay = AnimatedVectorDrawableCompat.create(context, R.drawable.avd_stop_play);

            if (playButton == null || avdPlayToStop == null) {
                throw new IllegalStateException();
            }

            playButton.setImageDrawable(avdPlayToStop);
        }
    }

    public void changeState(int newState, boolean animate) throws Exception {
        int oldState = currentState;

        switch (newState) {
            case STATE_STOPPED:
                spinner.setVisibility(View.GONE);

                llGif.setVisibility(View.INVISIBLE);

                if (oldState == STATE_PLAYING && animate && Build.VERSION.SDK_INT >= 23) {
                    playButton.setImageDrawable(avdStopToPlay);
                    avdStopToPlay.start();
                }
                else {
                    playButton.setImageResource(R.drawable.play);
                }

                currentState = STATE_STOPPED;
                break;

            case STATE_LOADING:
                spinner.setVisibility(View.VISIBLE);

                llGif.setVisibility(View.INVISIBLE);
                currentState = STATE_LOADING;
                break;

            case STATE_PLAYING:
                spinner.setVisibility(View.GONE);

                if (oldState == STATE_STOPPED && animate && Build.VERSION.SDK_INT >= 23) {
                    playButton.setImageDrawable(avdPlayToStop);
                    avdPlayToStop.start();
                }
                else {
                    playButton.setImageResource(R.drawable.stop);
                }

                llGif.setVisibility(View.VISIBLE);
                currentState = STATE_PLAYING;
                break;

            default:
                throw new Exception("Invalid state");
        }
    }
}
