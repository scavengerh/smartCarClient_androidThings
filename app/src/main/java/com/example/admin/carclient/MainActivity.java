package com.example.admin.carclient;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.icu.util.Calendar;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EdgeEffect;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.util.Enumeration;

import static android.graphics.BitmapFactory.decodeFile;
import static android.graphics.BitmapFactory.decodeStream;

public class MainActivity extends AppCompatActivity implements View.OnTouchListener{
    private String TAG = this.getClass().getSimpleName();
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE" };

    private final static Double PI = 3.14159;
    private final static Double toAnglle = 57.297;
    private final static int BUFFER_SIZE = 1024 * 1024;
    private final static String defaultIp = "192.168.43.188";

    private ImageView imageView;
    private EditText editText;
    private TextView textView;
    private Button inputButton;
    private Button snapButton;

    private Button carUp;
    private Button carDown;
    private Button carLeft;
    private Button carRight;

    private FrameLayout frameLayout;
    private int imageWidth;
    private int imageStartX;
    private int imageHeight;
    private int imageStartY;

    private boolean isInput = false;
    private boolean isSocketLink = false;
    private Socket socket = null;
    private byte[] imageDispBuffer;
    private String obtainCheckStr;
    private InputStream ins = null;
    private OutputStream outs = null;
    private int screenWidth;
    private int screenHeight;


    private boolean isTouchWheel = false;
    boolean isTouchImage = false;


    private boolean motorCmdSendFlags = false;
    private boolean isSnapFlags = false;

    private static final String SAVE_PIC_PATH= Environment.getExternalStorageState().equalsIgnoreCase(Environment.MEDIA_MOUNTED) ? Environment.getExternalStorageDirectory().getAbsolutePath() :"/mnt/sdcard"; //保存到SD卡
    private static final String SAVE_REAL_PATH = SAVE_PIC_PATH + "/carClient/";//保存的确切位置

    /*
     *  cmdData[0] : cmd   0x10: Motor control, 0x20: Servo motor contrl, 0x30: Stop all control
     *  cmdData[1] : data
     * */
    private byte[] cmdData = null;

    public static int getScreenHeight(Context context) {
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager manager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        manager.getDefaultDisplay().getMetrics(metrics);
        return metrics.heightPixels;
    }

    /**
     * 获取手机屏幕宽度，以px为单位
     *
     * @param context
     * @return
     */
    public static int getScreenWidth(Context context) {
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager manager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        manager.getDefaultDisplay().getMetrics(metrics);
        return metrics.widthPixels;
    }

    public void verifyStoragePermissions(Activity activity) {

        try {
            //检测是否有写的权限
            int permission = ActivityCompat.checkSelfPermission(activity,
                    "android.permission.WRITE_EXTERNAL_STORAGE");
            if (permission != PackageManager.PERMISSION_GRANTED) {
                // 没有写的权限，去申请写的权限，会弹出对话框
                ActivityCompat.requestPermissions(activity, PERMISSIONS_STORAGE,REQUEST_EXTERNAL_STORAGE);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 保存图片到手机
     * */
    public  void saveFile(String fileName, int length) throws IOException {
        String subForder = SAVE_REAL_PATH ;
        File foder = new File(subForder);
        if (!foder.exists()) {
            foder.mkdirs();
        }
        Log.i(TAG, "Save path is "+ subForder);
        File myCaptureFile = new File(subForder, fileName);
        if (!myCaptureFile.exists()) {
            myCaptureFile.createNewFile();
        }
        Log.i(TAG, "Save file is "+ fileName);
        FileOutputStream fos = new FileOutputStream(myCaptureFile);
        fos.write(imageDispBuffer, 0, length);
        fos.flush();
        fos.close();
    }

    /**
     * Handle 等待<捕捉>按钮，触发视频捕捉并保存。
     * */
    Handler handleUI = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            int what = msg.what;
            InputStream imageStream = null;
            int length = msg.arg1;

            //     Log.i(TAG, "ImageView draw frame, length: " + length);
            //保存图片数据
            if(isSnapFlags){
                isSnapFlags = false;
                Calendar calendar=Calendar.getInstance();  //获取当前时间，作为图标的名字
                String year=calendar.get(Calendar.YEAR)+"";
                String month=calendar.get(Calendar.MONTH)+1+"";
                String day=calendar.get(Calendar.DAY_OF_MONTH)+"";
                String hour=calendar.get(Calendar.HOUR_OF_DAY)+"";
                String minute=calendar.get(Calendar.MINUTE)+"";
                String second=calendar.get(Calendar.SECOND)+"";
                String time=year+month+day+hour+minute+second+".jpg";
                try {
                    saveFile(time, length);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }else {
                imageStream = new ByteArrayInputStream(imageDispBuffer);
                Drawable d = Drawable.createFromStream(imageStream, null);
                imageView.setImageDrawable(d);

                textView.setText(obtainCheckStr);
            }

            super.handleMessage(msg);
        }
    };

    @Override
    protected void onCreate(Bundle temp) {
        super.onCreate(temp);

        requestWindowFeature(Window.FEATURE_NO_TITLE);//隐藏标题
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);//设置全屏
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);// 横屏

        setContentView(R.layout.activity_main);

        verifyStoragePermissions(this);

        frameLayout = (FrameLayout) findViewById(R.id.framel);
        imageView = (ImageView) findViewById(R.id.cameraview);
        imageView.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        imageWidth = imageView.getWidth();
                        imageHeight = imageView.getHeight();
                        imageStartX = imageView.getLeft();
                        imageStartY = imageView.getTop();
                        Log.i(TAG, "ImageView: start: " + imageStartX + ", " + imageStartY + ", size: " + imageWidth + ", " + imageHeight);
                        imageView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    }
                }
        );
        editText = (EditText) findViewById(R.id.inputIp);
        textView = (TextView)findViewById(R.id.statusText);
        inputButton = (Button) findViewById(R.id.inputButton);
        snapButton = (Button) findViewById(R.id.snapImageButton);

        carUp = (Button)findViewById(R.id.carUp);
        carDown = (Button)findViewById(R.id.carDown);
        carLeft = (Button)findViewById(R.id.carLeft);
        carRight = (Button)findViewById(R.id.carRight);

//        textView.setText("    操作说明： 输入智能小车IP，点击<启动连接>连接到智能小车，点击<拍照>捕捉当前视频，" +
//                "在圆形区域拖动方向控制智能小车行走，在视频监控区域上下滑动控制监控相机姿态。");

        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        cmdData = new byte[3];
        editText.setText(defaultIp);

        imageDispBuffer = new byte[BUFFER_SIZE];

        //在视频显示区域默认显示一张视频。
        InputStream is = null;
        try {
            is = getAssets().open("test.jpg");
        } catch (IOException e) {
            e.printStackTrace();
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;

        Drawable d = Drawable.createFromStream(is, null);
        imageView.setImageDrawable(d);
        screenWidth = getScreenWidth(this);
        screenHeight = getScreenHeight(this);
        Log.i(TAG, "Screen width: " + screenWidth + ", height " + screenHeight);

        //监听Layout触摸行为，并判断是否为智能小车操作。
        frameLayout.setOnTouchListener(new View.OnTouchListener() {
            float DownX, DownY;
            float updateX, updateY;
            int oldMoveX;
            int oldMoveY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        if (event.getX() < imageWidth && event.getY() < imageHeight) {
                            DownX = event.getX();
                            DownY = event.getY();
                            isTouchImage = true;
                        }
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (event.getX() < imageWidth && event.getY() < imageHeight) {
                            //在视频显示区域，说明是摄像头姿态调整事件。
                            if (isTouchImage) {
                                float moveX = DownX - event.getX();
                                float moveY = DownY - event.getY();
                                motorCmdSendFlags = true;
                                cmdData[0] = 0x20;
                                cmdData[1] = (byte) (moveX * 90 / imageWidth + oldMoveX);
                                cmdData[2] = (byte) (moveY * 90 / imageHeight);
                                Log.i(TAG, "imageTouch: " + cmdData[1] + ", " + cmdData[2]);
                            }
                        }
                        break;

                    case MotionEvent.ACTION_UP:
                        if (isTouchImage) {
//                            //保存上次调整的角度，在下次调整时做叠加
//                            oldMoveX = cmdData[1];
//                            if (oldMoveX > 90)
//                                oldMoveX = 90;
//                            if (oldMoveX < 0)
//                                oldMoveX = 0;
//
//                            oldMoveY = cmdData[2];
//                            if (oldMoveY > 90)
//                                oldMoveY = 90;
//                            if (oldMoveY < 0)
//                                oldMoveY = 0;
                            isTouchImage = false;
                        }

                        //结束触摸，发送电机停止事件到智能小车。
                        motorCmdSendFlags = true;
                        cmdData[0] = 0x30;
                        cmdData[1] = 0x1;
                        cmdData[2] = 0x2;
                        break;
                    default:
                        break;
                }
                return true;
            }
        });

        new Thread(runnable).start();
    }


    @Override
    public boolean onTouch(View view, MotionEvent event) {
        switch (view.getId()) {
            case R.id.inputButton:
                // Log.i(TAG, "Connect server button press down");
                isInput = true;
                break;
            case R.id.snapImageButton:
                isSnapFlags = true;
                break;
            case R.id.carUp:
                motorCmdSendFlags = true;
                cmdData[0] = 0x10;
                if (event.getAction() == MotionEvent.ACTION_BUTTON_PRESS)
                    cmdData[1] = 0x10;
                else if (event.getAction() == MotionEvent.ACTION_BUTTON_RELEASE)
                    cmdData[1] = 0x50;
                break;
            case R.id.carDown:
                motorCmdSendFlags = true;
                cmdData[0] = 0x10;
                if (event.getAction() == MotionEvent.ACTION_BUTTON_PRESS)
                    cmdData[1] = 0x20;
                else if (event.getAction() == MotionEvent.ACTION_BUTTON_RELEASE)
                    cmdData[1] = 0x50;
                break;
            case R.id.carLeft:
                motorCmdSendFlags = true;
                cmdData[0] = 0x10;
                if (event.getAction() == MotionEvent.ACTION_BUTTON_PRESS)
                    cmdData[1] = 0x30;
                else if (event.getAction() == MotionEvent.ACTION_BUTTON_RELEASE)
                    cmdData[1] = 0x50;
                break;
            case R.id.carRight:
                motorCmdSendFlags = true;
                cmdData[0] = 0x10;
                if (event.getAction() == MotionEvent.ACTION_BUTTON_PRESS)
                    cmdData[1] = 0x40;
                else if (event.getAction() == MotionEvent.ACTION_BUTTON_RELEASE)
                    cmdData[1] = 0x50;
                break;
            default:
                break;
        }

        return  false;
    }


    @Override
    protected void onDestroy() {
        if (isSocketLink) {
            try {
                Log.i(TAG, "onDestroy: Client socket close");
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        super.onDestroy();
    }

    private Result CheckPackEnd(byte[] data, int length) {
        Result result = new Result();
        for (int i = 0; i < length - 3; i++) {
            if (data[i] == 0x11) {
                if (data[i + 1] == 0x22) {
                    if (data[i + 2] == 0x33) {
                        if (data[i + 3] == 0x55){
                            result.frameType = 1;
                            result.index = i;
                            return result;
                        }
                    }else if(data[i + 2] == 0x44) {
                        if (data[i + 3] == 0x66){
                            result.frameType = 2;
                            result.index = i;
                            return result;
                        }
                    }
                }
            }
        }

        result.frameType = 0;
        result.index = -1;
        return result;
    }

    //Main Loop线程，处理server连接，数据接收，解析任务。
    private Runnable runnable = new Runnable() {
        int lengthCache = 0;
        int length;
        byte[] imageBuffer = new byte[1024 * 1024];
        byte[] frameBufferCache = new byte[1024 * 1024];

        @Override
        public void run() {
            while (true) {
                try {
                    if (isInput) {
                        isInput = false;
                        //获取IP地址，并进行TCP连接Server端
                        String ip = editText.getText().toString();
                        Log.i(TAG, "Service IP: " + ip);
                        if (isSocketLink == false) {
                            socket = new Socket(ip, 8080);
                            isSocketLink = true;
                            Log.i(TAG, "socket connect ...");
                        }
                    }
                    if (isSocketLink && socket.isConnected()) {
                        Log.i(TAG, "isSocketLink && socket.isConnected()");
                        if (ins == null || outs == null) {
                            inputButton.setText("连接到智能小车");
                            ins = socket.getInputStream();
                            outs = socket.getOutputStream();
                        }

                        //发送控制电机信号
                        if (motorCmdSendFlags) {
                            Log.i(TAG, "Send server cmd:" + cmdData[0]);
                            motorCmdSendFlags = false;
                            outs.write(cmdData);
                            outs.flush();
                        }

                        //接收Socket数据
                        length = ins.read(imageBuffer);
                        if (length > 0) {
                            Result result = CheckPackEnd(imageBuffer, length);
                            //   Log.i(TAG, "Socket receive length: " + length + ", check ret: " + ret);
                            if (result.index < 0) {
                                //不是完整帧，缓存数据
                                System.arraycopy(imageBuffer, 0, frameBufferCache, lengthCache, length);
                                lengthCache += length;
                            } else {
                                //是完整帧，提取一帧数据到 frameBufferCache，更新帧长度
                                System.arraycopy(imageBuffer, 0, frameBufferCache, lengthCache, result.index);
                                lengthCache += result.index;
                                if(result.frameType == 1) {
                                    //检测是视频帧，处理视频帧数据
                                    Message message = new Message();
                                    message.arg1 = lengthCache;
                                    System.arraycopy(frameBufferCache, 0, imageDispBuffer, 0, lengthCache);
                                    handleUI.sendMessage(message);
                                }else if(result.frameType == 2){
                                    //检测字符串帧，处理字符串数据
                                    byte[] bStr = new byte[lengthCache];
                                    System.arraycopy(frameBufferCache, 0, bStr, 0, lengthCache);
                                    String recvStr = new String(bStr, "UTF-8");
                                    obtainCheckStr = recvStr;
                                }
                                //拷贝剩下的数据到缓冲区
                                System.arraycopy(imageBuffer, result.index + 4, frameBufferCache, 0, length - result.index - 4);
                                lengthCache = length - result.index - 4;
                            }
                        }
                    }
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    isSocketLink = false;
                    e.printStackTrace();
                }
            }
        }
    };

    /**
     * 获取ip地址
     *
     * @return
     */
    public static String getHostIP() {

        String hostIp = null;
        try {
            Enumeration nis = NetworkInterface.getNetworkInterfaces();
            InetAddress ia = null;
            while (nis.hasMoreElements()) {
                NetworkInterface ni = (NetworkInterface) nis.nextElement();
                Enumeration<InetAddress> ias = ni.getInetAddresses();
                while (ias.hasMoreElements()) {
                    ia = ias.nextElement();
                    if (ia instanceof Inet6Address) {
                        continue;// skip ipv6
                    }
                    String ip = ia.getHostAddress();
                    if (!"127.0.0.1".equals(ip)) {
                        hostIp = ia.getHostAddress();
                        break;
                    }
                }
            }
        } catch (SocketException e) {
            Log.i("yao", "SocketException");
            e.printStackTrace();
        }
        return hostIp;

    }
}


class Result {
    int frameType;
    int index;

    // 构造函数
    public Result() {
        super();
    }
}