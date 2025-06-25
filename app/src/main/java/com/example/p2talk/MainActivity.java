package com.example.p2talk;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Switch;
import android.widget.CompoundButton;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.os.VibratorManager;
import android.os.VibrationEffect;
import android.app.AlertDialog;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.lang.ref.WeakReference;

public class MainActivity extends AppCompatActivity {

    TextView hint, msgs, group, port, msg0, nick, peerIp, peerPort;
    MulticastSocket ms1 = null;
    DatagramSocket unicastSocket = null;
    boolean isMulticastMode = true; // 默认使用多播模式
    Switch modeSwitch;
    Button disconnectBtn;
    ImageView waveformView;
    Animation waveAnimation;
    MediaRecorder rec = null;
    int isRecording = 0;
    long recordStartTime = 0; // 记录开始录制的时间
    byte[] buff1; // local sent
    byte[] buff2; // remote recved
    int sinmax = 64 * 1024;
    int blen1 = 0;
    int blen2 = 0;
    MediaPlayer mplayer = null;
    ByteBuffer soundBuffer; // 用于内存中传输音频
    private String logs = ""; // 用于存储日志信息

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化UI组件
        hint = findViewById(R.id.id_hw);
        hint.setText("实时语音通话应用");
        msgs = findViewById(R.id.id_msgs);
        msgs.setText("\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n");
        group = findViewById(R.id.id_groupip);
        port = findViewById(R.id.id_port);
        msg0 = findViewById(R.id.id_msg1);
        nick = findViewById(R.id.id_nick);

        // 初始化单播模式的组件
        peerIp = findViewById(R.id.id_peer_ip);
        peerPort = findViewById(R.id.id_peer_port);

        // 初始化动画组件
        waveformView = findViewById(R.id.waveform_view);
        waveformView.setBackgroundResource(R.drawable.waveform_background);
        waveAnimation = AnimationUtils.loadAnimation(this, R.anim.wave_animation);
        // 初始化模式切换开关
        modeSwitch = findViewById(R.id.mode_switch);
        disconnectBtn = findViewById(R.id.id_disconnect);

        modeSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                isMulticastMode = isChecked;
                findViewById(R.id.multicast_layout).setVisibility(isChecked ? View.VISIBLE : View.GONE);
                findViewById(R.id.unicast_layout).setVisibility(isChecked ? View.GONE : View.VISIBLE);

                // 更新按钮状态
                if (ms1 != null || unicastSocket != null) {
                    findViewById(R.id.id_sendit).setEnabled(true);
                    findViewById(R.id.id_talk).setEnabled(true);
                }
            }
        });

        // 分配内存缓冲区用于音频数据
        soundBuffer = ByteBuffer.allocate(sinmax);

        findViewById(R.id.id_sendit).setEnabled(false);
        findViewById(R.id.id_talk).setEnabled(false);

        // 按住说话功能
        Button talkBtn = findViewById(R.id.id_talk);
        talkBtn.setOnTouchListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                startRecord();
            } else if (event.getAction() == android.view.MotionEvent.ACTION_UP
                    || event.getAction() == android.view.MotionEvent.ACTION_CANCEL) {
                stopRecord();
            }
            v.performClick(); // 保证无障碍
            return false;
        });
    }

    // Toast显示普通信息
    private static int activeToastCount = 0;
    private void showInfoToast(final String message) {
        runOnUiThread(() -> {
            int baseDuration = 600; // 单条toast 600ms
            int duration = baseDuration + activeToastCount * 600 + 200; // 每多一条多400ms
            Toast toast = Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT);
            toast.show();
            activeToastCount++;
            new Handler().postDelayed(() -> {
                toast.cancel();
                activeToastCount = Math.max(0, activeToastCount - 1);
            }, duration);
        });
    }

    // https://segmentfault.com/a/1190000005926314
    // Handler改为静态内部类，防止内存泄漏，普通信息用Toast
    private static class FlushLogHandler extends Handler {
        private final WeakReference<MainActivity> activityRef;
        public FlushLogHandler(MainActivity activity) {
            activityRef = new WeakReference<>(activity);
        }
        @Override
        public void handleMessage(Message msg) {
            MainActivity activity = activityRef.get();
            if (activity == null) return;
            String info = null;
            if (msg.what == 1)
                ;
            else if (msg.what == 2)
                info = msg.arg1 + " bytes voice sent";
            else if (msg.what == 3)
                info = msg.arg1 + " bytes received";
            else if (msg.what == 4)
                info = msg.arg1 + " seconds voice played";
            ((TextView) activity.findViewById(R.id.id_msgs)).setText(activity.logs);
            if (info != null) activity.showInfoToast(info);
        }
    }
    private final Handler flushlog = new FlushLogHandler(this);

    public void onClick_recv(View view) {
        // 请求必要的权限
        if (ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[] { android.Manifest.permission.RECORD_AUDIO }, 1);
        } // 禁用设置相关的UI
        group.setEnabled(false);
        port.setEnabled(false);
        peerIp.setEnabled(false);
        peerPort.setEnabled(false);
        findViewById(R.id.id_recv).setEnabled(false);
        modeSwitch.setEnabled(false);
        disconnectBtn.setEnabled(true);

        // 根据当前模式启动相应的接收线程
        if (isMulticastMode) {
            startMulticastReceiver();
        } else {
            startUnicastReceiver();
        }

        // 启用发送和通话按钮
        findViewById(R.id.id_sendit).setEnabled(true);
        findViewById(R.id.id_talk).setEnabled(true);
    }

    // 启动多播接收线程
    private void startMulticastReceiver() {
        new Thread() {
            @Override
            public void run() {
                try {
                    ms1 = new MulticastSocket(Integer.parseInt(port.getText().toString())); // port
                    ms1.joinGroup(InetAddress.getByName(group.getText().toString())); // join group ip
                    ms1.setTimeToLive(2);
                    showInfoToast(
                        "已连接到多播组: " + group.getText().toString() + ":" + port.getText().toString()
                    );
                    Message m1 = new Message();
                    m1.what = 1;
                    flushlog.sendMessage(m1);

                    while (true) {
                        byte[] msg0 = new byte[65 * 1024]; // max 64k maybe
                        DatagramPacket dp1 = new DatagramPacket(msg0, msg0.length);
                        ms1.receive(dp1);
                        processReceivedPacket(dp1);
                    }
                } catch (Exception e) {
                    String msg = e.getMessage() != null ? e.getMessage() : "";
                    if (msg.contains("closed") || msg.contains("SocketException")) {
                        // socket closed，主动断开连接并toast
                        runOnUiThread(() -> {
                            showInfoToast("多播连接已断开");
                            onClick_disconnect(null);
                        });
                    } else {
                        runOnUiThread(() -> showInfoToast("多播接收异常: " + msg));
                    }
                }
            }
        }.start();
    }

    // 启动单播接收线程
    private void startUnicastReceiver() {
        new Thread() {
            @Override
            public void run() {
                try {
                    unicastSocket = new DatagramSocket(Integer.parseInt(port.getText().toString()));
                    showInfoToast(
                        "已开启单播接收端口: " + port.getText().toString() 
                    );
                    Message m1 = new Message();
                    m1.what = 1;
                    flushlog.sendMessage(m1);

                    while (true) {
                        byte[] msg0 = new byte[65 * 1024];
                        DatagramPacket dp1 = new DatagramPacket(msg0, msg0.length);
                        unicastSocket.receive(dp1);
                        processReceivedPacket(dp1);
                    }
                } catch (Exception e) {
                    String msg = e.getMessage() != null ? e.getMessage() : "";
                    if (msg.contains("closed") || msg.contains("SocketException")) {
                        // socket closed，主动断开连接并toast
                        runOnUiThread(() -> {
                            showInfoToast("单播连接已断开");
                            onClick_disconnect(null);
                        });
                    } else {
                        runOnUiThread(() -> showInfoToast("单播接收异常: " + msg));
                    }
                }
            }
        }.start();
    }

    // 处理接收到的数据包
    private void processReceivedPacket(DatagramPacket dp1) {
        try {
            if (dp1.getLength() < 500) { // 文本消息
                String message = new String(dp1.getData(), 0, dp1.getLength());
                logs = message + "\n" + logs;
                Message m1 = new Message();
                m1.what = 1;
                flushlog.sendMessage(m1);
            } else // 语音消息
            {
                // 将接收到的语音数据保存到内存缓冲区
                buff2 = dp1.getData();
                blen2 = dp1.getLength();

                // 将内存中的数据复制到文件
                String fn2 = getApplicationContext().getExternalFilesDir("") + "/atalk002.amr";
                File f2 = new File(fn2);
                if (!f2.delete() && f2.exists()) {
                    Log.w("MainActivity", "文件删除失败: " + fn2);
                }
                FileOutputStream fo = new FileOutputStream(fn2);
                fo.write(buff2, 0, blen2);
                fo.close();

                // 获取语音时长和发送者昵称
                String sender = "语音";
                try {
                    // 尝试从上一条文本消息中提取昵称
                    String[] lines = logs.split("\\n");
                    if (lines.length > 0 && lines[0].contains(": ")) {
                        sender = lines[0].split(": ", 2)[0];
                    }
                } catch (Exception ignore) {}
                int durationSec = 0;
                try {
                    MediaPlayer tmpPlayer = new MediaPlayer();
                    tmpPlayer.setDataSource(fn2);
                    tmpPlayer.prepare();
                    durationSec = tmpPlayer.getDuration() / 1000;
                    tmpPlayer.release();
                } catch (Exception ignore) {}
                double sizeKB = blen2 / 1024.0;
                String sizeStr = String.format("%.2f", sizeKB);
                String infoMsg = sender + ": (sound, " + durationSec + "s, " + sizeStr + "KB)";
                logs = infoMsg + "\n" + logs;

                // 发送接收消息
                Message m3 = new Message();
                m3.what = 3;
                m3.arg1 = blen2;
                flushlog.sendMessage(m3);

                // 自动播放接收到的语音，使用Handler在UI线程中执行
                runOnUiThread(() -> onClick_playvoice(null));
            }
        } catch (Exception e) {
            showErrorDialog("处理接收包异常:\n" + e.getMessage());
        }
    }

    // 播放语音
    public void onClick_playvoice(View view) {
        try {
            // 显示波形动画
            waveformView.setVisibility(View.VISIBLE);
            waveformView.startAnimation(waveAnimation);

            mplayer = new MediaPlayer();
            mplayer.setDataSource(getApplicationContext().getExternalFilesDir("") + "/atalk002.amr");
            mplayer.setAudioAttributes(
                new android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            );
            mplayer.setOnCompletionListener(mp -> {
                // 播放完成后停止动画
                waveformView.clearAnimation();
                waveformView.setVisibility(View.INVISIBLE);
            });
            mplayer.prepare();
            mplayer.start();

            Message m4 = new Message();
            m4.what = 4;
            m4.arg1 = (int) (mplayer.getDuration() / 1000);
            flushlog.sendMessage(m4);
        } catch (Exception e) {
            Message m1 = new Message();
            m1.what = 1;
            flushlog.sendMessage(m1);
            showErrorDialog("播放语音失败:\n" + e.getMessage());
            // 出错时也要清除动画
            waveformView.clearAnimation();
            waveformView.setVisibility(View.INVISIBLE);
        }
    }

    public void onClick_send(View view) {
        new Thread() {
            @Override
            public void run() {
                try {
                    String cs1 = nick.getText().toString() + ": " + msg0.getText().toString();
                    byte[] bs1 = cs1.getBytes(StandardCharsets.UTF_8);

                    if (isMulticastMode && ms1 != null) {
                        // 多播模式发送
                        DatagramPacket dp1 = new DatagramPacket(bs1, bs1.length,
                                InetAddress.getByName(group.getText().toString()),
                                Integer.parseInt(port.getText().toString()));
                        ms1.send(dp1);
                        showInfoToast("多播文本消息已发送");
                    } else if (!isMulticastMode && unicastSocket != null) {
                        // 单播模式发送
                        DatagramPacket dp1 = new DatagramPacket(bs1, bs1.length,
                                InetAddress.getByName(peerIp.getText().toString()),
                                Integer.parseInt(peerPort.getText().toString()));
                        unicastSocket.send(dp1);
                        showInfoToast("单播文本消息已发送");
                    }

                    Message m1 = new Message();
                    m1.what = 1;
                    flushlog.sendMessage(m1);
                } catch (Exception e) {
                    showErrorDialog("发送文本消息失败:\n" + e.getMessage());
                }
                ;
            }
        }.start();
    }

    public void onClick_talk2send(View view) {
        new Thread() {
            @Override
            public void run() {
                try {
                    if (isMulticastMode && ms1 != null) {
                        // 多播模式发送语音
                        DatagramPacket dp1 = new DatagramPacket(buff1, blen1,
                                InetAddress.getByName(group.getText().toString()),
                                Integer.parseInt(port.getText().toString()));
                        ms1.send(dp1);
//                        showInfoToast("多播语音消息已发送");
                    } else if (!isMulticastMode && unicastSocket != null) {
                        // 单播模式发送语音
                        DatagramPacket dp1 = new DatagramPacket(buff1, blen1,
                                InetAddress.getByName(peerIp.getText().toString()),
                                Integer.parseInt(peerPort.getText().toString()));
                        unicastSocket.send(dp1);
//                        showInfoToast("单播语音消息已发送到 " + peerIp.getText().toString());
                    }

                    Message m1 = new Message();
                    m1.what = 1;
                    flushlog.sendMessage(m1);
                } catch (Exception e) {
                    Message m1 = new Message();
                    m1.what = 1;
                    flushlog.sendMessage(m1);
                    showErrorDialog("发送语音消息失败:\n" + e.getMessage());
                }
                ;
            }
        }.start();
    }

    private void startRecord() {
        if (isRecording == 0) {
            try {
                waveformView.setVisibility(View.VISIBLE);
                waveformView.startAnimation(waveAnimation);
                // MediaRecorder(Context) 仅API 31+，为兼容性保留无参构造
                rec = new MediaRecorder();
                rec.setOnErrorListener((r, what, extra) -> Log.e("ptalk ", what + " " + extra));
                rec.setAudioSource(MediaRecorder.AudioSource.MIC);
                rec.setAudioChannels(1);
                rec.setAudioSamplingRate(8000);
                rec.setOutputFormat(MediaRecorder.OutputFormat.AMR_NB);
                rec.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
                rec.setMaxDuration(10 * 1000);
                rec.setMaxFileSize(60 * 1024);
                String fn1 = getApplicationContext().getExternalFilesDir("") + "/atalk001.amr";
                File f1 = new File(fn1);
                if (!f1.delete() && f1.exists()) {
                    Log.w("MainActivity", "文件删除失败: " + fn1);
                }
                rec.setOutputFile(fn1);
                rec.prepare();
                rec.start();
                recordStartTime = System.currentTimeMillis(); // 记录开始时间
                isRecording = 1;
                ((Button) findViewById(R.id.id_talk)).setText("录音中...");
                VibratorManager vm = (VibratorManager) getSystemService(VIBRATOR_MANAGER_SERVICE);
                if (vm != null) {
                    vm.getDefaultVibrator()
                            .vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
                }
            } catch (Exception e) {
                Message m1 = new Message();
                m1.what = 1;
                flushlog.sendMessage(m1);
                showErrorDialog("开始录音失败:\n" + e.getMessage());

                // 重置录音状态
                isRecording = 0;
                recordStartTime = 0;
                if (rec != null) {
                    try {
                        rec.release();
                    } catch (Exception ex) {
                        // 忽略释放时的异常
                    }
                    rec = null;
                }
                ((Button) findViewById(R.id.id_talk)).setText("按住说话");
                waveformView.clearAnimation();
                waveformView.setVisibility(View.INVISIBLE);
            }
        }
    }

    private void stopRecord() {
        if (isRecording == 1) {
            try {
                // 检查录制时间是否足够长（至少500毫秒）
                long recordDuration = System.currentTimeMillis() - recordStartTime;
                final int MIN_RECORD_DURATION = 500; // 最小录制时间500毫秒

                if (recordDuration < MIN_RECORD_DURATION) {
                    // 录制时间太短，直接取消录制
                    try {
                        if (rec != null) {
                            rec.stop();
                            rec.release();
                        }
                    } catch (IllegalStateException e) {
                        // 如果MediaRecorder还没有开始录制，stop()会抛出IllegalStateException
                        // 这种情况下直接释放资源即可
                        if (rec != null) {
                            rec.release();
                        }
                    } catch (Exception e) {
                        // 处理其他可能的异常
                        if (rec != null) {
                            rec.release();
                        }
                    }

                    rec = null;
                    isRecording = 0;
                    recordStartTime = 0;

                    waveformView.clearAnimation();
                    waveformView.setVisibility(View.INVISIBLE);
                    ((Button) findViewById(R.id.id_talk)).setText("按住说话");

                    // 显示录制时间太短的提示
                    showInfoToast(
                        "录制时间太短，请按住按钮至少0.5秒\n"
                    );
                    Message m1 = new Message();
                    m1.what = 1;
                    flushlog.sendMessage(m1);

                    // 轻微震动提示
                    VibratorManager vm = (VibratorManager) getSystemService(VIBRATOR_MANAGER_SERVICE);
                    if (vm != null) {
                        vm.getDefaultVibrator()
                                .vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE));
                    }
                    return;
                }

                // 正常停止录制
                rec.stop();
                rec.release();
                rec = null;
                waveformView.clearAnimation();
                waveformView.setVisibility(View.INVISIBLE);
                VibratorManager vm2 = (VibratorManager) getSystemService(VIBRATOR_MANAGER_SERVICE);
                if (vm2 != null) {
                    long[] pattern = { 0, 50, 50, 50 };
                    vm2.getDefaultVibrator().vibrate(VibrationEffect.createWaveform(pattern, -1));
                }
                String fn1 = getApplicationContext().getExternalFilesDir("") + "/atalk001.amr";
                File audioFile = new File(fn1);

                // 检查音频文件是否存在且有效
                if (!audioFile.exists() || audioFile.length() < 100) {
                    showErrorDialog(
                        "录制的音频文件无效，请重试"
                    );
                    Message m1 = new Message();
                    m1.what = 1;
                    flushlog.sendMessage(m1);
                    ((Button) findViewById(R.id.id_talk)).setText("按住说话");
                    isRecording = 0;
                    recordStartTime = 0;
                    return;
                }

                buff1 = new byte[sinmax];
                FileInputStream fis = new FileInputStream(audioFile);
                blen1 = fis.read(buff1);
                fis.close();
                soundBuffer.clear();
                soundBuffer.put(buff1, 0, blen1);
                ((Button) findViewById(R.id.id_talk)).setText("按住说话");
                isRecording = 0;
                recordStartTime = 0;
                onClick_talk2send(null);
                Message m2 = new Message();
                m2.what = 2;
                m2.arg1 = blen1;
                flushlog.sendMessage(m2);
            } catch (IllegalStateException e) {
                Message m1 = new Message();
                m1.what = 1;
                flushlog.sendMessage(m1);
                showErrorDialog("录制失败：录制时间太短或录制器状态异常\n" + e.getMessage());

                // 清理资源
                if (rec != null) {
                    try {
                        rec.release();
                    } catch (Exception ex) {
                        // 忽略释放时的异常
                    }
                    rec = null;
                }
                isRecording = 0;
                recordStartTime = 0;
                ((Button) findViewById(R.id.id_talk)).setText("按住说话");
                waveformView.clearAnimation();
                waveformView.setVisibility(View.INVISIBLE);
            } catch (Exception e) {
                Message m1 = new Message();
                m1.what = 1;
                flushlog.sendMessage(m1);
                showErrorDialog("停止录音失败:\n" + e.getMessage());

                // 清理资源
                if (rec != null) {
                    try {
                        rec.release();
                    } catch (Exception ex) {
                        // 忽略释放时的异常
                    }
                    rec = null;
                }
                isRecording = 0;
                recordStartTime = 0;
                ((Button) findViewById(R.id.id_talk)).setText("按住说话");
                waveformView.clearAnimation();
                waveformView.setVisibility(View.INVISIBLE);
            }
        }
    }

    public void onClick_talk(View view) {
    }

    // 断开连接
    public void onClick_disconnect(View view) {
        try { // 停止录音
            if (isRecording == 1 && rec != null) {
                try {
                    rec.stop();
                } catch (IllegalStateException e) {
                    // 录制器状态异常，直接释放即可
                } catch (Exception e) {
                    // 其他异常也直接释放
                }
                try {
                    rec.release();
                } catch (Exception e) {
                    // 忽略释放时的异常
                }
                rec = null;
                isRecording = 0;
                recordStartTime = 0;
                ((Button) findViewById(R.id.id_talk)).setText("按住说话");
            }

            // 停止播放
            if (mplayer != null && mplayer.isPlaying()) {
                mplayer.stop();
                mplayer.release();
                mplayer = null;
            }

            // 清除动画
            waveformView.clearAnimation();
            waveformView.setVisibility(View.INVISIBLE);

            // 关闭Socket连接
            if (ms1 != null) {
                ms1.close();
                ms1 = null;
                showInfoToast("多播连接已断开");
            }
            if (unicastSocket != null) {
                unicastSocket.close();
                unicastSocket = null;
                showInfoToast("单播连接已断开");
            }

            // 重新启用设置相关的UI
            group.setEnabled(true);
            port.setEnabled(true);
            peerIp.setEnabled(true);
            peerPort.setEnabled(true);
            findViewById(R.id.id_recv).setEnabled(true);
            modeSwitch.setEnabled(true);
            disconnectBtn.setEnabled(false);

            // 禁用发送和通话按钮
            findViewById(R.id.id_sendit).setEnabled(false);
            findViewById(R.id.id_talk).setEnabled(false);

            Message m1 = new Message();
            m1.what = 1;
            flushlog.sendMessage(m1);
        } catch (Exception e) {
            showErrorDialog("断开连接时发生错误:\n" + e.getMessage());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 释放资源
        try {
            if (ms1 != null) {
                ms1.close();
                ms1 = null;
            }
            if (unicastSocket != null) {
                unicastSocket.close();
                unicastSocket = null;
            }
            if (mplayer != null) {
                mplayer.release();
                mplayer = null;
            }
            if (rec != null) {
                rec.release();
                rec = null;
            }
        } catch (Exception e) {
            showErrorDialog("onDestroy异常:\n" + e.getMessage());
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 暂停时停止所有正在进行的操作
        try {
            if (isRecording == 1 && rec != null) {
                try {
                    rec.stop();
                } catch (IllegalStateException e) {
                    // 录制器状态异常，直接释放即可
                } catch (Exception e) {
                    // 其他异常也直接释放
                }
                try {
                    rec.release();
                } catch (Exception e) {
                    // 忽略释放时的异常
                }
                rec = null;
                isRecording = 0;
                recordStartTime = 0;
            }
            if (mplayer != null && mplayer.isPlaying()) {
                mplayer.stop();
            }
            waveformView.clearAnimation();
            waveformView.setVisibility(View.INVISIBLE);
        } catch (Exception e) {
            showErrorDialog("onPause异常:\n" + e.getMessage());
        }
    }

    // 弹窗显示错误信息
    private void showErrorDialog(final String message) {
        runOnUiThread(() -> {
            new AlertDialog.Builder(MainActivity.this)
                .setTitle("错误")
                .setMessage(message)
                .setPositiveButton("确定", null)
                .show();
        });
    }

    // 处理权限请求结果
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean allGranted = true;

        if (requestCode == 1) {
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (!allGranted) {
                showInfoToast(
                    "警告：未授予所有必需权限，应用可能无法正常工作"
                );
                Message m1 = new Message();
                m1.what = 1;
                flushlog.sendMessage(m1);
            }
        }
    }
}