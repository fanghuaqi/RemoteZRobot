package org.hustcse.wifirobot;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.sql.Date;
import java.text.SimpleDateFormat;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Surface;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.MobileAnarchy.Android.Widgets.Joystick.JoystickMovedListener;
import com.MobileAnarchy.Android.Widgets.Joystick.JoystickView;

public class WifiRobotActivity extends Activity {
	/* 设置LOG的TAG */
	private static String TAG = "ZRobot";
	/* 设置是否需要LOG */
	final static boolean D = true;	
	
	/* 默认的三个视频源的地址 */
	private static String CAR_VIDEO_ADDR = "http://192.168.1.100:8080/?action=snapshot";
	private static String ARM_VIDEO_ADDR = "http://192.168.1.100:8090/?action=snapshot";
	private static String OPENCV_VIDEO_ADDR = "http://192.168.1.100/detection.jpg";

	/* 默认的目标TCP服务器的IP地址 */
	private static String DIST_TCPIPADDR = "192.168.1.100";
	/* 默认的目标TCP服务器的IP地址 */
	private static int DIST_TCPPORT = 1234;

	/* 用于设置界面的preference */
	private SharedPreferences preferences;
	
	/* 定义一个显示视频的类 */
	DrawVideo m_DrawVideo;

	/* TCP服务器的IP和PORT */
	private String dist_tcp_addr;
	private int dist_tcp_port;

	/* 视频当前的状态:
	 * UPDATE : 视频新帧更新
	 * ERROR  : 视频数据错误
	 * END    : 视频结束 */
	/* MSG_ 开头的东西都表示Handler发送的消息 */
	final static int MSG_VIDEO_UPDATE = 1;
	final static int MSG_VIDEO_ERROR = 2;
	final static int MSG_VIDEO_END = 3;

	/* 和tcp_ctrl类相关联
	 * 用于handler传递消息到Activity类上
	 * 用于更新UI之类的信息 否则就会报错(上下文错误) */
	
	/* 显示TOAST消息 */
	final static int MSG_DISPLAY_TOAST = 100;
	/* 修复preference里面的错误输入时的消息的基准值 */
	final static int MSG_FIX_PREFERENCE = 1000;
	/* 修复preference时的IP的偏移量 */
	final static int FIX_IP_PREFERENCE = 0;

	/* 进度等待框
	 * TCP连接等待
	 * 图像获取等待
	 * 视频获取等待 */
	ProgressDialog mDialog_Connect, mDialog_ImageCap, mDialog_VideoCap;
	/* 对话框对应的ID号 */
	private static final int CONNECT_DIALOG_KEY = 0;
	private static final int IMGCAP_DIALOG_KEY = 1;
	private static final int VIDEOCAP_DIALOG_KEY = 2;

	/* 各种BUTTON等对象的定义 
	 * 请参见res/layout/main.xml */
	Button btn_image;
	Button btn_video_srcsel;
	Button btn_video;
	Button btn_follow_road_mode_ctrl;
	Button btn_set_camera2LCD;
	Button btn_control_mode;
	Button btn_connect;
	Button btn_laser_ctrl;

	ImageView img_camera;
	
	JoystickView joystick;
	JoystickView joystickArm;

	TextView txtAngle, txtSpeed, txtTCPState;

	/* 当前的控制模式 自动或者手动 */
	private boolean auto_control_mode = false;	
	
	/* 激光控制命令相关 */
	private final static int LASER_OFF = 0;
	private final static int LASER_ON = 1;
	private int laser_ctrl = LASER_OFF;


	/* video source select : 
	 * CAR 
	 * ARM
	 * OPENCV
	 * */	
	final static int MAX_VIDEO_SRC_CNT = 3;
	
	private int video_source_sel = 0;
	
	final static int CAR_VIDEO_SRC = 0;
	final static int ARM_VIDEO_SRC = 1;
	final static int OPENCV_VIDEO_SRC = 2;

	/* video source address array */
	private String[] video_addr = new String[MAX_VIDEO_SRC_CNT];
	/* current video address */
	private String cur_video_addr; 

	/* 图像或者视频的准备情况 */
	private boolean image_ready_flag = false;
	private boolean video_ready_flag = false;

	/* 自定义的tcp消息的传递协议相关
	 * ctrl_code 控制字
	 * data_length 数据长度
	 * ctrl_data 控制数据 */
	short tcp_ctrl_code;
	short tcp_data_length;
	byte[] tcp_ctrl_data = new byte[1024];

	/* 自定义的TCP控制类 */
	tcp_ctrl tcp_ctrl_obj;

	Bitmap img_camera_bmp;
	
	/* 用于指示当前视频数据的状态
	 * false : 无视频数据正在显示
	 * true  : 视频数据正在显示 */
	boolean video_flag = false;

	/* 控制小车的转向角度以及前进速度 */
	int operate_angle_last = 0;
	int operate_speed_last = 0;
	int operate_angle = 0;
	int operate_speed = 0;

	/* 最大的速度可调单位 */
	private final static int MAX_SPEED_UNIT = 10;
	/* 一个速度单位对应的值 */
	private final static int SPEED_SCALE = 5;
	
	/* 机械臂的X,Y偏移 */
	int arm_x_offset = 0;
	int arm_y_offset = 0;
	int arm_x_offset_last = 0;
	int arm_y_offset_last = 0;
	private final static int MAX_ARM_UNIT = 10;
	private final static int ARM_X_SCALE = 9;
	private final static int ARM_Y_SCALE = 9;

	/* 当前的上下文 */
	private Context mContext;

	private static final int REQ_SYSTEM_SETTINGS = 0x0;

	/* 用于获取手机屏幕的大小分辨率 方向*/
	Display display;
	private int screen_Width = 0;
	private int screen_Height = 0;
	private int screen_Orientation = 0;
	
	/* JoyStick类的配置参数类 */
	LayoutParams joyviewParams;
	LayoutParams joyviewParamsArm;

	/* 用于自动适配时的摇杆 按钮 TextView等
	 * 的按屏幕缩放比例*/
	private float joystick_scale = 3;
	
	private float btn_scale = 42;
	private float txtview_scale = 42;

	/* 用于记录APP启动时间 方便调试使用 */
	private long start_time = 0;
	private long end_time = 0;

	private long benchmark_start = 0;
	private long benchmark_end = 0;

	/* 用于记录APP启动阶段人为输出LOG信息 */
	private static int MAX_PASS = 40;
	private long[] time_pass = new long[MAX_PASS + 1];
	private String[] pass_log = new String[MAX_PASS + 1];

	/* 用于记录系统信息相关 */
	private static int MAX_INFO_CNT = 40;
	private String[] SystemInfo = new String[MAX_INFO_CNT];
	private int SystemInfoCnt = 0;

	/* 记录当前PASS的状态 */
	private int pass_cnt = 0;
	private String MYLOG_PATH_SD = "hrrobotlog";


	/** Called when the activity is first created. */
	/** 所谓的主函数 **/
	@Override
	public void onCreate(Bundle savedInstanceState) {
		Log.i(TAG, "program startup");
		init_log_time();

		start_time = System.currentTimeMillis();
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		log_pass_time("Set ContentView OK");
		/* get the setting preference for update the setting information */
		preferences = PreferenceManager.getDefaultSharedPreferences(this);

		/* 获取屏幕的宽度长度 */
		display = getWindowManager().getDefaultDisplay();
		screen_Orientation = display.getOrientation();
		if ((screen_Orientation == Surface.ROTATION_0)
				|| (screen_Orientation == Surface.ROTATION_180)) {
			screen_Width = display.getHeight();
			screen_Height = display.getWidth();
		} else {
			screen_Width = display.getWidth();
			screen_Height = display.getHeight();
		}

		Log.i(TAG, "Screen Resolution:" + screen_Height + " X " + screen_Width);
		log_pass_time("screen info ok");

		/* 各种按钮等对象的定义初始化 */
		btn_video_srcsel = (Button) findViewById(R.id.button_video_src);
		btn_video = (Button) findViewById(R.id.button_video);
		btn_control_mode = (Button) findViewById(R.id.button_control);
		btn_connect = (Button) findViewById(R.id.button_connect);
		btn_laser_ctrl = (Button) findViewById(R.id.button_laser_ctrl);

		img_camera = (ImageView) findViewById(R.id.imageView_camera);

		/* car direction control joystick initialize */
		joystick = (JoystickView) findViewById(R.id.joystickView); 
		/* machine arm control joystick initialize */
		joystickArm = (JoystickView) findViewById(R.id.joystickARM); 

		txtAngle = (TextView) findViewById(R.id.TextViewX);
		txtSpeed = (TextView) findViewById(R.id.TextViewY);
		txtTCPState = (TextView) findViewById(R.id.TextViewTCPState);

		/* 设置按钮对象的半透明效果 */
		btn_video_srcsel.getBackground().setAlpha(100); 
		btn_video.getBackground().setAlpha(100); 
		btn_control_mode.getBackground().setAlpha(100);
		btn_connect.getBackground().setAlpha(100);
		btn_laser_ctrl.getBackground().setAlpha(100);

		/* 根据屏幕的大小和分辨率自动适配按钮,文本框等对象的大小 */
		
		/* 按钮适配 */
		btn_video_srcsel.setTextSize(screen_Width / btn_scale);
		btn_video.setTextSize(screen_Width / btn_scale);
		btn_control_mode.setTextSize(screen_Width / btn_scale);
		btn_connect.setTextSize(screen_Width / btn_scale);
		btn_laser_ctrl.setTextSize(screen_Width / btn_scale);

		/* TextView适配 */
		((TextView) findViewById(R.id.TextViewAngle)).setTextSize(screen_Width
				/ txtview_scale);
		((TextView) findViewById(R.id.TextViewSpeed)).setTextSize(screen_Width
				/ txtview_scale);
		((TextView) findViewById(R.id.TextViewTCPStateTxt))
				.setTextSize(screen_Width / txtview_scale);
		txtAngle.setTextSize(screen_Width / txtview_scale);
		txtSpeed.setTextSize(screen_Width / txtview_scale);
		txtTCPState.setTextSize(screen_Width / txtview_scale);

		/* 设置按钮等对象的按下后响应的Listener */
		btn_video_srcsel.setOnClickListener(video_src_acquire_listener);
		btn_video.setOnClickListener(video_acquire_listener);

		btn_control_mode.setOnClickListener(ctrl_btn_listener);
		btn_connect.setOnClickListener(connect_listener);
		btn_laser_ctrl.setOnClickListener(laser_ctrl_listener);

		log_pass_time("all objects init ok");

		update_preference();
		
		/* TCP连接初始化, 不先建立连接,
		 * 这样子可以加快APP的启动速度 
		 * 建立TCP连接(未成功)非常耗时 */
		tcp_ctrl_obj = new tcp_ctrl(getApplicationContext(),
				mHandler_UpdateUI, dist_tcp_addr, dist_tcp_port);
		log_pass_time("tcp ok");

		mContext = getApplicationContext();
		/* 根据当前TCP是否连接到TCP服务器
		 * 决定当前TCP状态并且显示到屏幕上 */
		if (tcp_ctrl_obj.mTcp_ctrl_client.isSocketOK()) {
			txtTCPState.setText(R.string.tcpstate_online);
		} else {
			txtTCPState.setText(R.string.tcpstate_offline);
		}
		
		/* 根据屏幕大小自动适配遥控小车的界面的摇杆大小 */
		joyviewParams = joystick.getLayoutParams();
		joyviewParams.width = (int) (screen_Width / joystick_scale);
		joyviewParams.height = (int) (screen_Width / joystick_scale);
		joystick.setLayoutParams(joyviewParams);
		/* 设置小车操作摇杆运动时的Listener */
		joystick.setOnJostickMovedListener(joystickctrl_listener);

		/* 根据屏幕大小自动视频操作机械臂的界面的摇杆大小 */
		joyviewParams = joystickArm.getLayoutParams();
		joyviewParams.width = (int) (screen_Width / joystick_scale);
		joyviewParams.height = (int) (screen_Width / joystick_scale);
		joystickArm.setLayoutParams(joyviewParams);
		/* 设置机械臂控制摇杆运动时的Listener */
		joystickArm.setOnJostickMovedListener(joystickarm_listener);

		log_pass_time("joystick ok");

		end_time = System.currentTimeMillis();

		Log.i(TAG, "app startup use " + (end_time - start_time) + " ms");
		log_pass_time("program started");
		end_log_time();
		log_system_info();
		write_log2file("hrrobotup", false);
	}

	@Override
	protected void onResume() {
		/**
		 * 程序从后台恢复后
		 * 强制设置为横屏
		 */
		if (getRequestedOrientation() != ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		}
		super.onResume();
	}

	/* 创建一个按下Menu键弹出的Menu选择框 
	 * menu 位于res/menu/menu.xml */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.menu, menu);
		return true;
	}

	/* 更新preference中的设置数据 */
	public void update_preference() {
		int temp;
		try {
			video_addr[0] = preferences.getString(
					getResources().getString(R.string.videoaddr1),
					CAR_VIDEO_ADDR);
			video_addr[1] = preferences.getString(
					getResources().getString(R.string.videoaddr2),
					ARM_VIDEO_ADDR);
			video_addr[2] = preferences.getString(
					getResources().getString(R.string.videoaddr3),
					OPENCV_VIDEO_ADDR);
			dist_tcp_addr = preferences.getString(
					getResources().getString(R.string.distipaddr),
					DIST_TCPIPADDR);
			/* NOTICE 这里需要注意下
			 * 需要尝试读取TCP端口号
			 * 由于暂时我也不清楚如何创建一个只能输入数字的文本框
			 * 所以只能在这里判断一下用户输入的PORT号是否为数字
			 * 不是数字的话就强制将文本框内容改为默认的PORT */
			try {
				temp = Integer.parseInt((preferences.getString(getResources()
						.getString(R.string.disttcpport), String
						.valueOf(DIST_TCPPORT))));
				dist_tcp_port = temp;
			} catch (Exception e) {
				/* 强制将preference中的错误输入恢复为默认 */
				SharedPreferences.Editor editor = preferences.edit();
				editor.putString(
						getResources().getString(R.string.disttcpport),
						String.valueOf(dist_tcp_port));
				editor.commit();
			}
		} catch (Exception e) {
			Log.d(TAG, e.toString());
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int ItemId; 
		
		/* 获取选中的Menu菜单的ID号 */
		ItemId = item.getItemId() ;
		
		switch (ItemId){
		case R.id.Settings:
			/* 跳转到对应的preference类 */
			startActivityForResult(new Intent(this, Preferences.class),
					REQ_SYSTEM_SETTINGS);
			break;
		default:
			break;
		}
		return true;
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
			/* 按下的如果是BACK，同时没有重复 
			 * 程序强制退出
			 * TODO 缺乏资源释放? */
			Log.d(TAG, "Program Exit!");
			System.exit(0);
		}
		return super.onKeyDown(keyCode, event);
	}

	/* 建立TCP连接按钮对应的Listener  */
	private OnClickListener connect_listener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			/* 更新对应的preference中的内容
			 * 主要是各种视频地址
			 * TCP服务器的IP以及PORT */
			update_preference();
			showDialog(CONNECT_DIALOG_KEY);
		}
	};
	
	/* 视频源切换按钮的listener */
	private OnClickListener video_src_acquire_listener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			process_video_src_select(v.getId());
		}
	};

	/* 获取视频按钮的listener */
	private OnClickListener video_acquire_listener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			post_ctrl_btnclk_msg(v.getId());
		}
	};

	/* 其他控制按钮的Listener */
	private OnClickListener ctrl_btn_listener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			/* 根据小车控制按钮的id来处理事件 */
			post_ctrl_btnclk_msg(v.getId());
		}
	};

	/* 激光控制按钮的Listener */
	private OnClickListener laser_ctrl_listener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			/* 发送激光控制命令 */
			postLaserCtrlMsg(v.getId());
		}
	};
	
	/* 方向以及速度控制摇杆的Listener定义实现 */
	private JoystickMovedListener joystickctrl_listener = new JoystickMovedListener() {
		@Override
		public void OnMoved(int pan, int tilt) {
			int operate_x = 0;
			int operate_y = 0;

			operate_x = pan;
			operate_y = -tilt;
			calc_speed_and_angle(operate_x, operate_y);
			txtAngle.setText(Integer.toString(operate_angle));
			txtSpeed.setText(Integer.toString(operate_speed));
			checkSendOperateCarMsg();
			/* 进程主动让出控制权，
			 * 这样的话,在操作摇杆时还是可以显示动态图像的
			 * 虽然效果不好 */
			Thread.yield(); 
		}

		@Override
		public void OnReleased() {
			txtAngle.setText("released");
			txtSpeed.setText("released");
			Thread.yield();
		}

		@Override
		public void OnReturnedToCenter() {
			txtAngle.setText("stopped");
			txtSpeed.setText("stopped");
			operate_angle = 0;
			operate_speed = 0;
			checkSendOperateCarMsg();
			Thread.yield();
		};
	};

	/* 机械臂控制的摇杆的listener实现 */
	private JoystickMovedListener joystickarm_listener = new JoystickMovedListener() {
		@Override
		public void OnMoved(int pan, int tilt) {
			int operate_x = 0;
			int operate_y = 0;

			operate_x = pan;
			operate_y = -tilt;
			calc_arm_xy(operate_x, operate_y);

			checkSendOperateArmMsg();
			/* 进程主动让出控制权，
			 * 这样的话,在操作摇杆时还是可以显示动态图像的
			 * 虽然效果不好 */
			Thread.yield(); 
		}

		@Override
		public void OnReleased() {
			Thread.yield();
		}

		@Override
		public void OnReturnedToCenter() {
			arm_x_offset = 0;
			arm_y_offset = 0;
			checkSendOperateArmMsg();
			Thread.yield();
		};
	};

	/*** OPERATE CAR ***/
	/* 发送获取角度控制和速度控制的命令 */
	private void postOperateCarMessage(int angle, int speed) {
		short ctrl_cmd;
		short ctrl_prefix;
		byte[] msg = new byte[4];

		/* 准备待发送的TCP消息数据 */
		ctrl_prefix = ctrl_prefixs.encode_ctrlprefix(
				ctrl_prefixs.write_request, ctrl_prefixs.less_data_request,
				ctrl_prefixs.withoutack);
		ctrl_cmd = ctrlcmds.OPERATE_CAR;
		msg[0] = (byte) (angle & 0xff);
		msg[1] = (byte) ((angle >> 8) & 0xff);
		msg[2] = (byte) (speed & 0xff);
		msg[3] = (byte) ((speed >> 8) & 0xff);

		post_tcp_msg(ctrl_prefix, ctrl_cmd, msg);
	}
	
	/* 测试是否角度和速度没有改变 并发送控制小车命令 */
	private void checkSendOperateCarMsg() {
		/* 检查当前的角度或者速度是否改变 */
		if (!((operate_angle == operate_angle_last) && (operate_speed == operate_speed_last))) {
			/* 当前socket可用才进行数据发送 */
			if ((tcp_ctrl_obj.mTcp_ctrl_client.isSocketOK())) { 
				postOperateCarMessage(operate_angle, operate_speed);
			}
			operate_angle_last = operate_angle;
			operate_speed_last = operate_speed;
		}
	}

	/* 通过当前坐标计算角度和速度信息 */
	private void calc_speed_and_angle(int operate_x, int operate_y) {
		operate_speed = (int) Math.sqrt((operate_x * operate_x)
				+ (operate_y * operate_y));

		if (operate_y < 0) {
			operate_speed = -operate_speed;
		}

		if (operate_x == 0) {
			if (operate_y == 0) {
				operate_angle = 0;
			} else if (operate_y > 0) {
				operate_angle = 90;
			} else {
				operate_angle = -90;
			}
		} else if (operate_y == 0) {
			if (operate_x == 0) {
				operate_angle = 0;
			} else if (operate_x > 0) {
				operate_angle = 0;
			} else {
				operate_angle = 180;
			}
		} else {
			operate_angle = (int) ((Math.atan2(operate_y, operate_x) / Math.PI) * 180);
		}

		if (operate_speed == 0) {
			operate_angle = 0;
		} else if (operate_speed > 0) {
			operate_angle = 90 - operate_angle;
		} else {
			operate_angle = operate_angle + 90;
		}

		if (operate_speed > MAX_SPEED_UNIT) {
			operate_speed = MAX_SPEED_UNIT;
		} else if (operate_speed < -MAX_SPEED_UNIT) {
			operate_speed = -MAX_SPEED_UNIT;
		}
		operate_speed = operate_speed * SPEED_SCALE;

	}



	/*** OPERATE_ARM ***/
	/* 发送获取机械臂XY控制的命令 */
	private void postOperateArmMessage(int x, int y) {
		short ctrl_cmd;
		short ctrl_prefix;
		byte[] msg = new byte[4];

		ctrl_prefix = ctrl_prefixs.encode_ctrlprefix(
				ctrl_prefixs.write_request, ctrl_prefixs.less_data_request,
				ctrl_prefixs.withoutack);
		ctrl_cmd = ctrlcmds.OPERATE_ARM;
		msg[0] = (byte) (x & 0xff);
		msg[1] = (byte) ((x >> 8) & 0xff);
		msg[2] = (byte) (y & 0xff);
		msg[3] = (byte) ((y >> 8) & 0xff);

		post_tcp_msg(ctrl_prefix, ctrl_cmd, msg);
	}
	
	/* 测试机械臂的XY是否没有改变 并发送控制机械臂命令 */
	private void checkSendOperateArmMsg() {
		if (!((arm_x_offset == arm_x_offset_last) && (arm_y_offset == arm_y_offset_last))) {
			if ((tcp_ctrl_obj.mTcp_ctrl_client.isSocketOK())) {
				/*
				 * 当前socket可用才进行数据发送
				 */
				postOperateArmMessage(arm_x_offset, arm_y_offset);
			}
			arm_x_offset_last = arm_x_offset;
			arm_y_offset_last = arm_y_offset;
		}
	}
	
	/* 计算机械臂的X,Y偏移值 */
	private void calc_arm_xy(int operate_x, int operate_y) {
		if (operate_x > MAX_ARM_UNIT) {
			operate_x = MAX_ARM_UNIT;
		} else if (operate_x < -MAX_ARM_UNIT) {
			operate_x = -MAX_ARM_UNIT;
		}

		if (operate_y > MAX_ARM_UNIT) {
			operate_y = MAX_ARM_UNIT;
		} else if (operate_y < -MAX_ARM_UNIT) {
			operate_y = -MAX_ARM_UNIT;
		}

		arm_x_offset = operate_x * ARM_X_SCALE;
		arm_y_offset = operate_y * ARM_Y_SCALE;
	}



	/* 用于tcp_ctrl类的handler 
	 * 主要是有些需要更新UI之类的操作 */
	private final Handler mHandler_UpdateUI = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MSG_DISPLAY_TOAST:
				disp_toast((String) msg.obj);
				break;
			case (MSG_FIX_PREFERENCE + FIX_IP_PREFERENCE):
				String ip = (String) msg.obj;
				SharedPreferences.Editor editor = preferences.edit();
				editor.putString(getResources().getString(R.string.distipaddr),
						ip);
				editor.commit();
				break;
			default:
				break;
			}

		}
	};



	/* 根据不同的进度框ID创建进度框 */
	@Override
	protected Dialog onCreateDialog(int id) {
		switch (id) {
		case CONNECT_DIALOG_KEY:
			/* 在当前的Activity下创建一个进度框 */
			mDialog_Connect = new ProgressDialog(WifiRobotActivity.this);
			/* 设置进度框上显示的消息 */
			mDialog_Connect.setMessage("Trying to connect to TCP server...");
			/* 设置进度框为不可取消的
			 * 这样设置的目的由于对话框运行时后台有进程在运行
			 * 如果取消的话,后台的进程没有结束,会导致一些无法预料的问题 */
			mDialog_Connect.setCancelable(false);
			return mDialog_Connect;
		case IMGCAP_DIALOG_KEY:
			mDialog_ImageCap = new ProgressDialog(WifiRobotActivity.this);
			mDialog_ImageCap.setMessage("Trying to obtain image ...");
			mDialog_ImageCap.setCancelable(false);
			return mDialog_ImageCap;
		case VIDEOCAP_DIALOG_KEY:
			mDialog_VideoCap = new ProgressDialog(WifiRobotActivity.this);
			mDialog_VideoCap.setMessage("Trying to obtain video ...");
			mDialog_VideoCap.setCancelable(false);
			return mDialog_VideoCap;
		default:
			return null;
		}
	}



	/***
	 * 尝试连接进度框
	 * 尝试获取远程图片进度框
	 * 尝试获取远程视频进度框
	 * ***/
	@Override
	protected void onPrepareDialog(int id, Dialog dialog) {
		switch (id) {
		case CONNECT_DIALOG_KEY:
			ConnectProgressThread mConnectProgressThread = new ConnectProgressThread(
					progress_handler);
			mConnectProgressThread.start();
			break;
		case IMGCAP_DIALOG_KEY:
			ImageCapProgressThread mCapProgressThread = new ImageCapProgressThread(
					progress_handler, IMGCAP_DIALOG_KEY);
			mCapProgressThread.start();
			break;
		case VIDEOCAP_DIALOG_KEY:
			ImageCapProgressThread mCapProgressThread2 = new ImageCapProgressThread(
					progress_handler, VIDEOCAP_DIALOG_KEY);
			mCapProgressThread2.start();
			break;
		default:
			break;
		}

	}

	/* 处理各种进度框的消息 */
	final Handler progress_handler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			if (msg.what <= VIDEOCAP_DIALOG_KEY) {
				dismissDialog(msg.what);
			}

			switch (msg.what) {
			case CONNECT_DIALOG_KEY:
				if (msg.obj != null) {
					disp_toast((String) msg.obj);
				}
				if (tcp_ctrl_obj.mTcp_ctrl_client.isSocketOK()) {
					txtTCPState.setText(R.string.tcpstate_online);
				} else {
					txtTCPState.setText(R.string.tcpstate_offline);
				}
				break;
			case IMGCAP_DIALOG_KEY:
				image_ready_flag = (Boolean) (msg.obj);
				if (image_ready_flag == true) {
					img_camera.setImageBitmap(img_camera_bmp);
				} else {
					disp_toast("Get remote image failed,please check the video address!");
				}
				break;
			case VIDEOCAP_DIALOG_KEY:
				video_ready_flag = (Boolean) (msg.obj);
				if (video_ready_flag == true) {
					img_camera.setImageBitmap(img_camera_bmp);
					/* 检查到视频数据正常 
					 * 就可以创建一个用于显示视频数据的类*/
					m_DrawVideo = new DrawVideo(cur_video_addr,
							mHandler_video_process);
					m_DrawVideo.start();
					btn_video.setText(R.string.button_video_stop);
					video_flag = true;
				} else {
					disp_toast("Get remote video failed,please check the video address!");
				}
				break;
			case MSG_DISPLAY_TOAST:
				break;

			default:
				break;
			}
		}
	};

	/* 检查TCP连接是否已经连接或者
	 * 连接的IP和Port已经更新 就需要重新连接  */
	private class ConnectProgressThread extends Thread {
		Handler mHandler;
		String msg = null;

		ConnectProgressThread(Handler h) {
			mHandler = h;
		}

		@Override
		public void run() {
			if (tcp_ctrl_obj.mTcp_ctrl_client.updateIPandPort(dist_tcp_addr,
					dist_tcp_port)
					|| (tcp_ctrl_obj.mTcp_ctrl_client.isSocketOK() == false)) {
				/*
				 * 检查是否有如下情况:
				 * 1. TCP连接的IP或者端口有更改
				 * 2. TCP连接没有建立
				 */
				/* 强制建立TCP连接 */
				tcp_ctrl_obj.mTcp_ctrl_client.tcp_connect(true);
			} else {
				msg = new String("Already Connected to TCP Server @"
						+ dist_tcp_addr + ":" + dist_tcp_port);
			}
			mHandler.obtainMessage(CONNECT_DIALOG_KEY, msg).sendToTarget();
		}
	}

	/* 检查是否图像或者视频数据准备好了 */
	private class ImageCapProgressThread extends Thread {
		Handler mHandler;
		boolean image_ok = false;
		int dialog_key;

		ImageCapProgressThread(Handler h, int id) {
			mHandler = h;
			dialog_key = id;
		}

		@Override
		public void run() {
			image_ok = get_remote_image(cur_video_addr);
			mHandler.obtainMessage(dialog_key, image_ok).sendToTarget();
		}
	}


	/*** LASER_CTRL ***/
	/* 发送激光控制的TCP消息 */
	private void postLaserCtrlMsg(int btn_id) {
		short ctrl_cmd;
		short ctrl_prefix;
		byte[] msg = new byte[1];
		Button btn;

		ctrl_prefix = ctrl_prefixs.encode_ctrlprefix(
				ctrl_prefixs.write_request, ctrl_prefixs.less_data_request,
				ctrl_prefixs.withoutack);
		ctrl_cmd = ctrlcmds.LASER_CTRL;

		btn = (Button) findViewById(btn_id);

		if (laser_ctrl == LASER_OFF) {
			btn.setText(R.string.button_laser_off);
			laser_ctrl = LASER_ON;
		} else {
			btn.setText(R.string.button_laser_on);
			laser_ctrl = LASER_OFF;
		}

		msg[0] = (byte) (laser_ctrl & 0xff);

		Log.d(TAG, "Switch Laser Ctrl " + " to  " + laser_ctrl);
		post_tcp_msg(ctrl_prefix, ctrl_cmd, msg);
	}


	/*** ADJUST_VIDEO_MODE ***/
	/* 发送切换视频模式的指令 */
	private void postSwitchVideoModeMsg(int videomode) {
		short ctrl_cmd;
		short ctrl_prefix;
		byte[] msg = new byte[1];

		ctrl_prefix = ctrl_prefixs.encode_ctrlprefix(
				ctrl_prefixs.write_request, ctrl_prefixs.less_data_request,
				ctrl_prefixs.withoutack);
		ctrl_cmd = ctrlcmds.ADJUST_VIDEO_MODE;
		msg[0] = (byte) (videomode & 0xff);

		Log.d(TAG, "Switch Video Mode " + " to  " + videomode);
		post_tcp_msg(ctrl_prefix, ctrl_cmd, msg);
	}

	/* 检查是否可以发送切换视频模式 */
	private void checkSendSwitchVideoModeMsg(int videomode) {
		if (tcp_ctrl_obj.mTcp_ctrl_client.isSocketOK()) { 
			/* 当前socket可用才进行数据发送 */
			postSwitchVideoModeMsg(videomode);
		}
	}

	/* 处理选择视频源的消息 */
	private void process_video_src_select(int btn_id) {
		Button btn;
		String toast_str;

		switch (btn_id) {
		case R.id.button_video_src:
			update_preference();
			btn = (Button) findViewById(R.id.button_video_src);
			video_source_sel += 1;
			if (video_source_sel >= MAX_VIDEO_SRC_CNT) {
				video_source_sel = 0;
			}
			/* 选择正确视频源 */
			cur_video_addr = video_addr[video_source_sel]; 
			toast_str = new String(" Address : " + cur_video_addr);
			switch (video_source_sel) {
			case CAR_VIDEO_SRC:
				btn.setText(R.string.button_video_src_car);
				toast_str = "Switch to car video ," + toast_str;
				break;
			case ARM_VIDEO_SRC:
				btn.setText(R.string.button_video_src_arm);
				toast_str = "Switch to arm video ," + toast_str;
				break;
			case OPENCV_VIDEO_SRC:
				btn.setText(R.string.button_video_src_opencv);
				toast_str = "Switch to openCV video ," + toast_str;
				break;
			}
			/* 测试并发送切换的视频模式 */
			checkSendSwitchVideoModeMsg(video_source_sel);

			// disp_toast(toast_str);

			/*
			 * 如果当前的正在采集视频数据 就需要进行切换, 并且先要将之前的视频掐掉
			 */
			if (video_flag == true) {
				/* 先退出之前的视频源 */
				if (m_DrawVideo != null) {
					m_DrawVideo.exit_thread();
				}
				btn_video.setText(R.string.button_video_start);
				img_camera.setImageResource(R.drawable.zynq_logo);
				video_flag = false;
				/* 切换为新的视频源 */
				showDialog(VIDEOCAP_DIALOG_KEY);
			}

			break;
		default:
			return;
		}

	}

	/*** 
	 * ENTER_REAL_CONTROL_MODE
	 * ENTER_AUTO_NAV_MODE 
	 * IMAGE AND VIDEO 相关
	 * ***/
	/* 处理对小车控制按钮的消息
	 * 图像 视频 自动/手动切换 */
	private void post_ctrl_btnclk_msg(int btn_id) {
		short ctrl_cmd = 0;
		short ctrl_prefix = 0;
		byte[] msg = null;
		Button btn;
		
		ctrl_prefix = ctrl_prefixs.encode_ctrlprefix(
				ctrl_prefixs.write_request, ctrl_prefixs.less_data_request,
				ctrl_prefixs.withoutack);
		switch (btn_id) {
		case R.id.button_image:
			update_preference();
			showDialog(IMGCAP_DIALOG_KEY);
			return; /* no break, direct return */

		case R.id.button_video:
			btn = (Button) findViewById(R.id.button_video);
			update_preference();
			if (video_flag == false) {
				/* 选择正确的视频源地址 */
				cur_video_addr = video_addr[video_source_sel]; 
				showDialog(VIDEOCAP_DIALOG_KEY);
			} else {
				if (m_DrawVideo != null) {
					m_DrawVideo.exit_thread();
					// m_DrawVideo.stop();
				}
				btn.setText(R.string.button_video_start);
				img_camera.setImageResource(R.drawable.zynq_logo);
				video_flag = false;
			}
			return; /* no break, direct return */
			
			/*** 
			 * ENTER_REAL_CONTROL_MODE
			 * ENTER_AUTO_NAV_MODE 
			 * ***/
		case R.id.button_control:
			if (auto_control_mode == false) {
				ctrl_cmd = (ctrlcmds.ENTER_AUTO_NAV_MODE);
				btn_control_mode.setText(R.string.button_realcontrol);
				auto_control_mode = true;
			} else {
				ctrl_cmd = (ctrlcmds.ENTER_REAL_CONTROL_MODE);
				btn_control_mode.setText(R.string.button_autocontrol);
				auto_control_mode = false;
			}
			break;

		default:
			return;
		}

		post_tcp_msg(ctrl_prefix, ctrl_cmd, msg);
	}

	/* 尝试获取远程图片 */
	public boolean get_remote_image(String url_addr) {
		boolean flag = false;

		String m_video_addr = CAR_VIDEO_ADDR;
		HttpURLConnection m_video_conn = null;
		InputStream m_InputStream = null;
		HttpGet httpRequest;
		HttpClient httpclient = null;
		HttpResponse httpResponse;

		try {
			m_video_addr = url_addr;
			Log.d(TAG, "start get url");
			httpRequest = new HttpGet(m_video_addr);

			Log.d(TAG, "open connection");
			httpclient = new DefaultHttpClient();

			Log.d(TAG, "begin connect");
			httpResponse = httpclient.execute(httpRequest);
			Log.d(TAG, "get InputStream");
			if (httpResponse.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
				Log.d(TAG, "decodeStream");
				m_InputStream = httpResponse.getEntity().getContent();
				/* 从获取的流中构建出BMP图像 */
				img_camera_bmp = BitmapFactory.decodeStream(m_InputStream);
			}
			Log.d(TAG, "decodeStream end");

			flag = true;
		} catch (Exception e) {
			Log.e(TAG, "Error In Get Image Msg:" + e.getMessage());
			flag = false;
		} finally {
			if (m_video_conn != null) {
				m_video_conn.disconnect();
			}
			if ((httpclient != null)
					&& (httpclient.getConnectionManager() != null)) {
				/* 及时关闭httpclient释放资源 */
				httpclient.getConnectionManager().shutdown(); 
			}
		}

		return flag;
	}

	/* 很重要的函数
	 * 调用tcp_ctrl类中的函数来发送tcp消息 */
	private void post_tcp_msg(short ctrl_prefix, short ctrl_cmd, byte[] msg) {
		ctrl_frame mCtrl_frame = new ctrl_frame(ctrl_prefix, ctrl_cmd, msg);
		byte[] tcp_msg = new byte[4 + mCtrl_frame.datalength];
		mCtrl_frame.encode_frametobytes(tcp_msg);
		tcp_ctrl_obj.mTcp_ctrl_client.post_msg(tcp_msg);
		if (D) {
			Log.d(TAG, "The Sent TCP Message is As Follows:");
			mCtrl_frame.display_ctrl_frame();
		}
	}
	
	/* 显示Toast 消息 */
	private void disp_toast(String msg) {
		Toast.makeText(mContext, msg, Toast.LENGTH_SHORT).show();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
		case REQ_SYSTEM_SETTINGS:
			systemsettingchange(resultCode, data);
			break;

		default:
			break;
		}
		super.onActivityResult(requestCode, resultCode, data);
	}

	private boolean systemsettingchange(int resultCode, Intent data) {
		boolean ifSucess = true;

		if (resultCode == RESULT_OK) {

		} else {
			Log.i(TAG, "None settings change");
		}
		return ifSucess;
	}

		/* NO USE */
	private OnSharedPreferenceChangeListener sys_set_chg_listener = new OnSharedPreferenceChangeListener() {

		@Override
		public void onSharedPreferenceChanged(
				SharedPreferences sharedPreferences, String key) {
			if (key == getResources().getString(R.string.videoaddr1)) {
				video_addr[0] = preferences.getString(key, CAR_VIDEO_ADDR);
			} else if (key == getResources().getString(R.string.videoaddr2)) {
				video_addr[1] = preferences.getString(key, ARM_VIDEO_ADDR);
			} else if (key == getResources().getString(R.string.videoaddr3)) {
				video_addr[2] = preferences.getString(key, OPENCV_VIDEO_ADDR);
			} else if ((key == getResources().getString(R.string.distipaddr))
					|| (key == getResources().getString(R.string.disttcpport))) {
				dist_tcp_addr = preferences.getString(
						getResources().getString(R.string.distipaddr),
						DIST_TCPIPADDR);
				dist_tcp_port = Integer.parseInt((preferences.getString(
						getResources().getString(R.string.disttcpport),
						String.valueOf(DIST_TCPPORT))));
				tcp_ctrl_obj.mTcp_ctrl_client.tcpreconnect(dist_tcp_addr,
						dist_tcp_port);
			}
		}
	};



	/**************USER LOG FUNCTIONS DEFINITION START**************/
	
	/* 记录程序每一个阶段的时间以及用户自定义的LOG消息
	 * Tag 表示用户自定义的LOG消息 */
	public void log_pass_time(String Tag) {
		long pass_time;

		/* 记录这个PASS结束时间 */
		benchmark_end = System.currentTimeMillis();
		if (true) {
			/* 生成LOG消息 
			 * 并存放在预定义好的LOG信息数组中 */
			if (pass_cnt > MAX_PASS) {
				return;
			} else {
				pass_time = benchmark_end - benchmark_start;
				time_pass[pass_cnt] = pass_time;
				pass_log[pass_cnt] = "PASS " + pass_cnt + " : " + Tag + ":"
						+ "costs " + pass_time + "ms";
				pass_cnt++;
			}
		}
		/* 设定这个PASS的开始时间 */
		benchmark_start = System.currentTimeMillis();
	}

	/* 初始化用于记录LOG的各项时间 */
	public void init_log_time() {
		benchmark_start = System.currentTimeMillis();
		benchmark_end = System.currentTimeMillis();
		start_time = System.currentTimeMillis();
		end_time = System.currentTimeMillis();
	}

	/* 结束PASS时做最后的时间和消息记录 */
	public void end_log_time() {
		end_time = System.currentTimeMillis();

		if (true) {
			if (pass_cnt > (MAX_PASS + 1)) {
				return;
			} else {
				long pass_time = end_time - start_time;
				time_pass[pass_cnt] = pass_time;
				pass_log[pass_cnt] = "Program Startup Costs " + pass_time
						+ "ms";
				pass_cnt++;
			}
		}
	}

	/* 记录当前手机的所有的重要的信息 */
	public void log_system_info() {
		String ScreenInfo = "Screen Resolution:" + screen_Height + " X "
				+ screen_Width;
		String CpuInfo = "";
		String VersionInfo = "";

		CpuInfo = readfile2str("/proc/cpuinfo");
		VersionInfo = readfile2str("proc/version");

		SystemInfo[0] = CpuInfo;
		SystemInfo[1] = VersionInfo;
		SystemInfo[2] = ScreenInfo;
		SystemInfo[3] = getphoneinfo();

		SystemInfoCnt = 4;
	}

	/* 获取手机的内部的信息 
	 * 详见程序中的说明 */
	public String getphoneinfo() {
		String phoneInfo = "Product: " + android.os.Build.PRODUCT;
		phoneInfo += "\n CPU_ABI: " + android.os.Build.CPU_ABI;
		phoneInfo += "\n TAGS: " + android.os.Build.TAGS;
		phoneInfo += "\n VERSION_CODES.BASE: "
				+ android.os.Build.VERSION_CODES.BASE;
		phoneInfo += "\n MODEL: " + android.os.Build.MODEL;
		phoneInfo += "\n SDK: " + android.os.Build.VERSION.SDK;
		phoneInfo += "\n VERSION.RELEASE: " + android.os.Build.VERSION.RELEASE;
		phoneInfo += "\n DEVICE: " + android.os.Build.DEVICE;
		phoneInfo += "\n DISPLAY: " + android.os.Build.DISPLAY;
		phoneInfo += "\n BRAND: " + android.os.Build.BRAND;
		phoneInfo += "\n BOARD: " + android.os.Build.BOARD;
		phoneInfo += "\n FINGERPRINT: " + android.os.Build.FINGERPRINT;
		phoneInfo += "\n ID: " + android.os.Build.ID;
		phoneInfo += "\n MANUFACTURER: " + android.os.Build.MANUFACTURER;
		phoneInfo += "\n USER: " + android.os.Build.USER;

		return phoneInfo;
	}

	/* 将文件内容保存到字符串
	 * TODO 后期为了防止用户打开一个很大的文件
	 * 这里可以加上一个读取多少行数据或者字符后自动退出 */
	public String readfile2str(String file_path) {
		String res = "";

		File file = new File(file_path);
		if (file.exists()) {
			try {
				String temp;
				FileReader fileReader = new FileReader(file);
				BufferedReader bufferedReader = new BufferedReader(fileReader);
				while (((temp = bufferedReader.readLine()) != null)) {
					res = res + "\n" + temp;
				}
				fileReader.close();
				bufferedReader.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		return res;
	}

	/* 将用户自定义的LOG信息写入到对于的LOG文件
	 * LOG信息包括 : 各个PASS的信息 手机信息
	 * log_file_name : log的文件名(后期会自动增加一些其他的附属信息如时间戳) */
	public void write_log2file(String log_file_name, boolean need_sd_log) {
		update_preference();
		if (need_sd_log) {
			SimpleDateFormat formatter = new SimpleDateFormat(
					"yyyy-MM-dd HH_mm_ss");
			Date curDate = new Date(System.currentTimeMillis());// 获取当前时间
			String log_file_date = formatter.format(curDate);
			String full_log_filename = log_file_name + "_" + log_file_date
					+ ".txt";
			String log_file_path = "";

			String sd_status = Environment.getExternalStorageState();
			if (!(sd_status.equals(Environment.MEDIA_MOUNTED))) {
				log_file_path = "/mnt/flash" + File.separator + MYLOG_PATH_SD;
				disp_toast("SD卡没有挂载, 日志文件将写入到" + log_file_path + "目录下!");
			} else {
				log_file_path = Environment.getExternalStorageDirectory()
						+ File.separator + MYLOG_PATH_SD;
			}
			File log_file_Dir = new File(log_file_path);
			File log_file = new File(log_file_path, full_log_filename);
			try {
				if (!log_file_Dir.exists()) {/* 文件或者不存在就创建目录和文件 */
					if (!log_file_Dir.mkdir()) {
						disp_toast("创建启动日志文件目录失败!");
						return;
					} else {
						if (!log_file.exists()) {
							log_file.createNewFile(); /* 创建文件 */
						}
					}
				}
				// 后面这个参数代表是不是要接上文件中原来的数据，不进行覆盖
				FileWriter filerWriter = new FileWriter(log_file, true);
				BufferedWriter bufWriter = new BufferedWriter(filerWriter);
				for (int cnt = 0; cnt < pass_cnt; cnt++) {
					bufWriter.write(pass_log[cnt]);
					bufWriter.newLine();
				}
				bufWriter.newLine();

				for (int cnt = 0; cnt < SystemInfoCnt; cnt++) {
					bufWriter.write(SystemInfo[cnt]);
					bufWriter.newLine();
				}
				bufWriter.close();
				filerWriter.close();
				disp_toast("启动日志文件已生成位于" + log_file.getAbsolutePath());
			} catch (Exception e) {
				Log.e(TAG, "Write Log File Failed! " + e.getMessage());
			}
		}
	}
	
	/**************USER LOG FUNCTIONS DEFINITION END**************/

	/* 处理DrawVideo类的Handler
	 * UI的更新 */
	@SuppressLint("HandlerLeak")
	private final Handler mHandler_video_process = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MSG_VIDEO_UPDATE:
				img_camera.setImageBitmap(img_camera_bmp);
				break;
			case MSG_VIDEO_ERROR:
				((Button) findViewById(R.id.button_video))
						.setText(R.string.button_video_start);
				disp_toast("Getting remote video failed,please check the video address!");
				img_camera.setImageResource(R.drawable.zynq_logo);
				break;
			case MSG_VIDEO_END:
				((Button) findViewById(R.id.button_video))
						.setText(R.string.button_video_start);
				img_camera.setImageResource(R.drawable.zynq_logo);
				break;
			default:
				break;
			}
		}
	};
	
	/* 处理视频显示的子类
	 * 主要实现原理是:
	 * 1s内获取至少24帧图像
	 * 并即时显示出来达到连续的效果 
	 * TODO: 最好是能够实现MJPEG的解码 
	 * 这样子连续性最好了*/
	class DrawVideo extends Thread {
		private String m_video_addr = CAR_VIDEO_ADDR;
		private HttpURLConnection m_video_conn;
		private InputStream m_InputStream;
		private Handler video_Handler;
		HttpGet httpRequest;
		HttpClient httpclient = null;
		HttpResponse httpResponse;
		Bitmap bmp = null;
		private boolean exit_flag = false;

		public DrawVideo(String url_addr, Handler handler) {
			m_video_addr = url_addr;
			video_Handler = handler;
		}

		public void exit_thread() {
			exit_flag = true;
		}

		/* 检查视频数据是否准备好了 */
		public boolean testconnection() {
			boolean flag = false;
			try {
				httpRequest = new HttpGet(m_video_addr);
				httpclient = new DefaultHttpClient();
				httpResponse = httpclient.execute(httpRequest);
				if (httpResponse.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
					m_InputStream = httpResponse.getEntity().getContent();
					/* 从获取的流中构建出BMP图像 */
					bmp = BitmapFactory.decodeStream(m_InputStream);
				}
				if (bmp == null) {
					flag = false;
				} else {
					flag = true;
				}
			} catch (Exception e) {
				flag = false;
				Log.e(TAG, "Error In Get Video Msg:" + e.getMessage());
			}
			if (m_video_conn != null) {
				m_video_conn.disconnect();
			}
			if ((httpclient != null)
					&& (httpclient.getConnectionManager() != null)) {
				/* 及时关闭httpclient释放资源 */
				httpclient.getConnectionManager().shutdown(); 
			}
			return flag;
		}

		@Override
		public void run() {
			try {
				httpRequest = new HttpGet(m_video_addr);
				httpclient = new DefaultHttpClient();

				while (!exit_flag) {
					httpResponse = httpclient.execute(httpRequest);

					if (httpResponse.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
						m_InputStream = httpResponse.getEntity().getContent();
						bmp = BitmapFactory.decodeStream(m_InputStream);
					}

					if (bmp != null) {
						/* 获取到数据后及时通过handler的方式显示出来 */
						img_camera_bmp = bmp;
						video_Handler.obtainMessage(MSG_VIDEO_UPDATE)
								.sendToTarget();
					}
					/* 延时一段时间 */
					sleep(30);
				}
				exit_flag = false;
			} catch (Exception e) {
				video_flag = false;
				Log.e(TAG, "Error In Get Video Msg:" + e.getMessage());
				video_Handler.obtainMessage(MSG_VIDEO_ERROR).sendToTarget();
			} finally {
				if (m_video_conn != null) {
					m_video_conn.disconnect();
				}
				if ((httpclient != null)
						&& (httpclient.getConnectionManager() != null)) {
					httpclient.getConnectionManager().shutdown(); 
				}
				video_Handler.obtainMessage(MSG_VIDEO_END).sendToTarget();
			}
		}
	}
}
