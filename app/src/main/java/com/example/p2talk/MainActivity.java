package com.example.p2talk;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

public class MainActivity extends AppCompatActivity {

    TextView hint, msgs, group, port, msg0, nick;
    String logs="";
    MulticastSocket ms1=null;

    MediaRecorder rec = null;
    int isrecording = 0;
    FileInputStream insd;  // read in sound
    byte[] buff1;  // local sent
    byte[] buff2;  // remote recved
    int sinmax= 64*1024;
    int blen1=0;
    int blen2=0;
    MediaPlayer mplayer = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        hint =findViewById(R.id.id_hw);
            hint.setText("push to talk using multicast over wifi");
        msgs =findViewById(R.id.id_msgs);
            msgs.setText("\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n");
        group=findViewById(R.id.id_groupip);
        port =findViewById(R.id.id_port);
        msg0 =findViewById(R.id.id_msg1);
        nick =findViewById(R.id.id_nick);

        ((Button)findViewById(R.id.id_sendit)).setEnabled(false);
        ((Button)findViewById(R.id.id_talk)).setEnabled(false);
    }

    // https://segmentfault.com/a/1190000005926314
    Handler flushlog = new Handler() {
        public void handleMessage(Message msg) {
            if (msg.what==1)
                ;
            else if (msg.what==2)
                logs=msg.arg1+ " bytes voice sent\n" + logs;
            else if (msg.what==3)
                logs=msg.arg1+ " bytes received\n" + logs;
            else if (msg.what==4)
                logs=msg.arg1+ " seconds voice played\n" + logs;

            ((TextView)findViewById(R.id.id_msgs)).setText(logs);
        }
    };

    public void onClick_recv(View view)
    {
        // andr6+ https://blog.csdn.net/weixin_44720673/article/details/116205305
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.RECORD_AUDIO}, 1);
        }
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
        }

        group.setEnabled(false);
        port.setEnabled(false);
        findViewById(R.id.id_recv).setEnabled(false);
        new Thread()
        {
            @Override
            public void run()  {
                try{
                    ms1 = new MulticastSocket(Integer.parseInt(port.getText().toString()));  // port
                    //groupip = ;
                    //portstr = port.getText().toString();
                    ms1.joinGroup(InetAddress.getByName(group.getText().toString()));  // join group ip
                    ms1.setTimeToLive(2);
                    //ms0.setSoTimeout(100);
                    System.out.println("bind "+group.getText().toString()+":"+port.getText().toString());
                    while (true){
                        byte[] msg0 = new byte[65*1024];  // max 64k maybe
                        DatagramPacket dp1=new DatagramPacket(msg0, msg0.length);
                        ms1.receive(dp1);
                        if (dp1.getLength() < 500){  // text
                            //logs=new String(msg0, "utf-8")+"\n"+logs;  // bug? fixed by 2869036143 鸣谢
                            logs=new String(dp1.getData(),0 ,dp1.getLength())+"\n"+logs;
                            Message m1 = new Message();
                            m1.what=1;
                            flushlog.sendMessage(m1);
                        }
                        else // play as voice
                        {
                            buff2 = dp1.getData();
                            blen2 = dp1.getLength();
                            //if buff1==buff0
                                // do not play
                            // buff1 --> file, then play file
                            //FileOutputStream fo = getResources().openRawResource(R.raw.atalk000);
                            String fn2 = new String(getApplicationContext().getExternalFilesDir("") + "/atalk002.amr");
                            File f2 = new File(fn2);
                            f2.delete();
                            FileOutputStream fo = new FileOutputStream(fn2);
                            fo.write(buff2,0, blen2);
                            fo.close();

                            Message m3 = new Message();
                            m3.what =3;
                            m3.arg1 =blen2; // recv len: in seconds or in bytes
                            flushlog.sendMessage(m3);

                            onClick_playvoice(view);
                            // buff --> asset/pipe, then play asset/pipe, good
                            // better
                        }
                    }
                }catch(Exception e){
                    System.out.println(e);
                }
            }
        }.start();
        ((Button)findViewById(R.id.id_sendit)).setEnabled(true);
        ((Button)findViewById(R.id.id_talk)).setEnabled(true);
    }

    // play voice
    public void onClick_playvoice(View view) {
        try {
            mplayer = new MediaPlayer();
            mplayer.setDataSource(getApplicationContext().getExternalFilesDir("") + "/atalk002.amr");
            mplayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mplayer.prepare();
            mplayer.start();

            Message m4 = new Message();
            m4.what =4;
            m4.arg1 =(int)(mplayer.getDuration()/1000);
            flushlog.sendMessage(m4);
        }catch(Exception e){
            System.out.println(e);
        }
    }

    public void onClick_send(View view) {
        new Thread()
        {
            @Override
            public void run()  {
                try {
                    String cs1 = nick.getText().toString()+": "+msg0.getText().toString();
                    byte[] bs1 = cs1.getBytes("utf-8");
                    DatagramPacket dp1 = new DatagramPacket(bs1, bs1.length,
                            InetAddress.getByName(group.getText().toString()), // gip
                            Integer.parseInt(port.getText().toString()));     // port
                    ms1.send(dp1);
                } catch (Exception e) {
                    e.printStackTrace();
                };
            }
        }.start();
    }

    public void onClick_talk2send(View view) {
        new Thread()
        {
            @Override
            public void run()  {
                try {
                    DatagramPacket dp1 = new DatagramPacket(buff1, blen1,
                            InetAddress.getByName(group.getText().toString()), // gip
                            Integer.parseInt(port.getText().toString()));     // port
                    ms1.send(dp1);
                } catch (Exception e) {
                    e.printStackTrace();
                };
            }
        }.start();
    }

    public void onClick_talk(View view) {
        // https://blog.csdn.net/qq_24349189/article/details/78573477

        if (isrecording==0) {
            try {
                rec = new MediaRecorder();
                rec.setOnErrorListener(new MediaRecorder.OnErrorListener() {
                    @Override
                    public void onError(MediaRecorder r, int what, int extra) {
                        Log.e("ptalk ", what + " " + extra);
                    }});
                rec.setAudioSource(MediaRecorder.AudioSource.MIC);
                rec.setAudioChannels(1);
                rec.setAudioSamplingRate(8000);
                rec.setOutputFormat(MediaRecorder.OutputFormat.AMR_NB);
                //rec.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT); // 0
                rec.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB); // 1
                //rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);     // 3
                rec.setMaxDuration(6*1000);
                rec.setMaxFileSize(60*1024);
                if (true) {
                    //rec.setOutputFile("/sdcard/atalk001.rec"); // Environment.getExternalStorageDirectory()
                    String fn1 = new String(getApplicationContext().getExternalFilesDir("") + "/atalk001.amr");
                    File f1 = new File(fn1);
                    f1.delete();
                    rec.setOutputFile(fn1);
                }
                else // or pipe  // https://stackoverflow.com/questions/14448166/using-mediarecorder-to-write-to-a-buffer-or-fifo
                {   // use pipe is perfered, but not ok yet
                    ParcelFileDescriptor[] pp = ParcelFileDescriptor.createPipe();
                    rec.setOutputFile(pp[1].getFileDescriptor());  // write to
                    insd = new FileInputStream(pp[0].getFileDescriptor()); // then can read from [0]
                }
                rec.prepare();
                rec.start();
                isrecording=1;
                ((Button)findViewById(R.id.id_talk)).setText("rec...");
            }
            catch(Exception e) {
                e.printStackTrace();
            }
        }
        else {
            rec.stop();

            try {
                buff1 = new byte[sinmax];
                if (true) {
                    //blen = (new FileInputStream("/sdcard/atalk001.rec")).read(buff1);
                    blen1 = (new FileInputStream(getApplicationContext().getExternalFilesDir("")+"/atalk001.amr")).read(buff1);
                } else  // pipe not ok yet
                {
                    blen1 = insd.read(buff1);
                }
            }
            catch (IOException e) {
                e.printStackTrace();
            }
            Log.e("ptalk ", "rec len "+blen1);

            ((Button)findViewById(R.id.id_talk)).setText("push 2 talk"); // restore button text
            isrecording = 0;

            onClick_talk2send(view);  // send buff over multicast

            Message m2 = new Message();
            m2.what=2;
            m2.arg1 = blen1; // how many sent in seconds or bytes
            //m3.arg2=rec.getMetrics()..;
            flushlog.sendMessage(m2);

        }

    } // end of click talk


}