package com.partron.sensorlab.partrontemperexample;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.media.AudioManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.partron.temperandroid.partrontemperlib.PartronTemperService;

public class MainActivity extends AppCompatActivity implements ServiceConnection{

    private static final int SELF_TIMER_MSG = 0x1;
    private static final int SELF_TIMER_MSG_INTERVAL = 3000;
    private ServiceConnection mConnection = this;
    private Messenger mServiceMessenger = null;
    private boolean mIsBound;
    private Messenger mMessenger = new Messenger(new IncomingMessageHandler());
    private String TAG = "MainActivity";
    private Button button;
    private TextView textView;
    private static float human;
    private DongleIntentReceiver myReceiver;
    private IntentFilter filter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textView = (TextView) findViewById(R.id.textView);
        button = (Button) findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mRefreshHandler.sendEmptyMessageDelayed(SELF_TIMER_MSG, SELF_TIMER_MSG_INTERVAL);
                button.setEnabled(false);
            }
        });

        myReceiver = new DongleIntentReceiver();
        filter = new IntentFilter(Intent.ACTION_HEADSET_PLUG);
    }
    private void doBindService() {
        bindService(new Intent(this, PartronTemperService.class), mConnection, Context.BIND_AUTO_CREATE);
        mIsBound = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(myReceiver, filter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (myReceiver != null)
            unregisterReceiver(myReceiver);
    }

    private void doUnbindService() {
        if (mIsBound) {
            // If we have received the service, and hence registered with it, then now is the time to unregister.
            if (mServiceMessenger != null) {
                try {
                    Message msg = Message.obtain(null, PartronTemperService.MSG_UNREGISTER_CLIENT);
                    msg.replyTo = mMessenger;
                    mServiceMessenger.send(msg);
                } catch (RemoteException e) {
                    // There is nothing special we need to do if the service has crashed.
                }
            }
            // Detach our existing connection.
            unbindService(mConnection);
            mIsBound = false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            doUnbindService();
            stopService(new Intent(MainActivity.this, PartronTemperService.class));
        } catch (Throwable t) {
            Log.e(TAG, "Failed to unbind from the service", t);
        }
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        mServiceMessenger = new Messenger(iBinder);
        try {
            Message msg = Message.obtain(null, PartronTemperService.MSG_REGISTER_CLIENT);
            msg.replyTo = mMessenger;
            mServiceMessenger.send(msg);
        }
        catch (RemoteException e) {
            // In this case the service has crashed before we could even do anything with it
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        mServiceMessenger = null;
    }
    private class DongleIntentReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String intentAction = intent.getAction();
            if (AudioManager.ACTION_HEADSET_PLUG.equals(intentAction)) {
                int state = intent.getIntExtra("state", -1);
                switch (state) {
                    case 0:
                        doUnbindService();
                        stopService(new Intent(MainActivity.this, PartronTemperService.class));
                        break;
                    case 1:
                        if (!PartronTemperService.isRunning()) {
                            doBindService();
                        }
                        break;
                }
            }
        }
    }

    private class IncomingMessageHandler extends Handler {

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case PartronTemperService.MSG_SET_INT_VALUE:
                    Log.d(TAG, "MSG_SET_INT_VALUE Message id : " + msg.arg1 + " Value : " + msg.arg2);
                    if(msg.arg1 == 1)
                        human = msg.arg2;
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }

    Handler mRefreshHandler = new Handler(new Handler.Callback() {
        public boolean handleMessage(Message msg){
            switch(msg.what){
                case SELF_TIMER_MSG:
                    textView.setText("body temperature : " + human / 10 + " ℃");
                    button.setEnabled(true);
                    break;
            }

            return false;
        }
    });
}
