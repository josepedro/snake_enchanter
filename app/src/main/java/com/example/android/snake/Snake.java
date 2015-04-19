/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.snake;

import android.app.Activity;
import android.media.AudioRecord;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.TextView;
import android.media.AudioFormat;
import android.os.AsyncTask;
import android.media.MediaRecorder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Snake: a simple game that everyone can enjoy.
 * 
 * This is an implementation of the classic Game "Snake", in which you control a serpent roaming
 * around the garden looking for apples. Be careful, though, because when you catch one, not only
 * will you become longer, but you'll move faster. Running into yourself or the walls will end the
 * game.
 * 
 */
public class Snake extends Activity {

    /**
     * Constants for desired direction of moving the snake
     */
    public static int MOVE_LEFT = 0;
    public static int MOVE_UP = 1;
    public static int MOVE_DOWN = 2;
    public static int MOVE_RIGHT = 3;

    private static String ICICLE_KEY = "snake-view";

    private SnakeView mSnakeView;

    /**
     * Constants for chords recongnized
     */
    private static final int RECORDER_SAMPLERATE = 44100;
    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private MyFFT myfft;
    private TextView chordTV;
    private RecordingAndSetChord asyncTaskChords = null;
    private AudioRecord recorder = null;
    private int bufferSize = 0;

    private static String[] nomeAcordes = { "F", "F m", "F aum", "F dim", "F#",
            "F# m", "F# aum", "F# dim", "G", "G m", "G aum", "G dim", "G#",
            "G# m", "G# aum", "G# dim", "A", "A m", "A aum", "A dim", "Bb",
            "Bb m", "Bb aum", "Bb dim", "B", "B m", "B aum", "B dim", "C",
            "C m", "C aum", "C dim", "C#", "C# m", "C# aum", "C# dim", "D",
            "D m", "D aum", "D dim", "Eb", "Eb m", "Eb aum", "Eb dim", "E",
            "E m", "E aum", "E dim" };

    /**
     * Called when Activity is first created. Turns off the title bar, sets up the content views,
     * and fires up the SnakeView.
     * 
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.snake_layout);

        myfft = new MyFFT();
        chordTV = (TextView) findViewById(R.id.chord_text);

        asyncTaskChords = new RecordingAndSetChord();
        asyncTaskChords.execute();

        mSnakeView = (SnakeView) findViewById(R.id.snake);
        mSnakeView.setDependentViews((TextView) findViewById(R.id.text),
                findViewById(R.id.arrowContainer), findViewById(R.id.background));

        if (savedInstanceState == null) {
            // We were just launched -- set up a new game
            mSnakeView.setMode(SnakeView.READY);
        } else {
            // We are being restored
            Bundle map = savedInstanceState.getBundle(ICICLE_KEY);
            if (map != null) {
                mSnakeView.restoreState(map);
            } else {
                mSnakeView.setMode(SnakeView.PAUSE);
            }
        }
        mSnakeView.setOnTouchListener(new OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (mSnakeView.getGameState() == SnakeView.RUNNING) {
                    // Normalize x,y between 0 and 1
                    float x = event.getX() / v.getWidth();
                    float y = event.getY() / v.getHeight();

                    // Direction will be [0,1,2,3] depending on quadrant
                    int direction = 0;
                    direction = (x > y) ? 1 : 0;
                    direction |= (x > 1 - y) ? 2 : 0;

                    // Direction is same as the quadrant which was clicked
                    mSnakeView.moveSnake(direction);

                } else {
                    // If the game is not running then on touching any part of the screen
                    // we start the game by sending MOVE_UP signal to SnakeView
                    mSnakeView.moveSnake(MOVE_UP);
                }
                return false;
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Pause the game along with the activity
        mSnakeView.setMode(SnakeView.PAUSE);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        // Store the game state
        outState.putBundle(ICICLE_KEY, mSnakeView.saveState());
    }

    /**
     * Handles key events in the game. Update the direction our snake is traveling based on the
     * DPAD.
     *
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent msg) {

        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                mSnakeView.moveSnake(MOVE_UP);
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                mSnakeView.moveSnake(MOVE_RIGHT);
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                mSnakeView.moveSnake(MOVE_DOWN);
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                mSnakeView.moveSnake(MOVE_LEFT);
                break;
        }

        return super.onKeyDown(keyCode, msg);
    }


    private class RecordingAndSetChord extends AsyncTask<Void, byte[], Void> {

        @Override
        protected Void doInBackground(Void... params) {

            System.out.println("Do in background!!!");

            while (true) {

                // Initializing variables for record
                bufferSize = AudioRecord.getMinBufferSize(RECORDER_SAMPLERATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT);
                // Instanciando array de bytes
                byte data[] = new byte[bufferSize];
                // Inicializando AudioRecord
                recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                        RECORDER_SAMPLERATE, RECORDER_CHANNELS,
                        RECORDER_AUDIO_ENCODING, bufferSize);

                // Inicializando the ByteArrayOutPutStream
                ByteArrayOutputStream baos = null;
                baos = new ByteArrayOutputStream();
                // Verificando Status do objeto recorder
                int i = recorder.getState();
                if (i == 1) {
                    recorder.startRecording();
                }

                long tempoInicio = System.currentTimeMillis();
                long tempoDecorrido = 0;
                while (tempoDecorrido <= 800) {
                    int result = recorder.read(data, 0, data.length);
                    if (result == AudioRecord.ERROR_INVALID_OPERATION) {
                        throw new RuntimeException();
                    } else if (result == AudioRecord.ERROR_BAD_VALUE) {
                        throw new RuntimeException();
                    } else {

                        baos.write(data, 0, result);
                    }
                    tempoDecorrido = System.currentTimeMillis() - tempoInicio;
                }

                recorder.stop();
                recorder.release();
                recorder = null;

                publishProgress(baos.toByteArray());

                try {

                    baos.close();
                    baos = null;

                } catch (IOException e) {
                    e.printStackTrace();
                }

            }

        }
        @Override
        protected void onProgressUpdate(byte[]... values) {

            myfft.setByteArraySong(values[0]);
            float[] S1 = myfft.getS1();

            float energy = myfft.getEnergy();

            // Reordenando as notas nas barras de energia
            float[] valores = new float[12];
            for (int l = 0; l < S1.length; l++) {
                valores[l] = S1[(l + 7) % S1.length];
            }

            // Setando valores na tela
            int number_chord = myfft.getAcorde();
            chordTV.setText(""+number_chord);
            //Mi maior = 44, DM = 36
            //mSnakeView.moveSnake(direction);
            if (number_chord == 36){
                mSnakeView.moveSnake(MOVE_LEFT);
            }
            else if (number_chord == 44){
                mSnakeView.moveSnake(MOVE_UP);
            }
            else if (number_chord == 37){
                mSnakeView.moveSnake(MOVE_RIGHT);
            }
            else if (number_chord == 45){
                mSnakeView.moveSnake(MOVE_DOWN);
            }
            else {
                // Do nothing
            }


        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
        }

    }


}
