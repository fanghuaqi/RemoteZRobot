package org.hustcse.wifirobot;

public class ctrlcmds {
	/*小车加减速，转向，启动控制*/
	public static final short WRITE_CMD_BASE = 0x000;
	public static final short SPEED_UP = (WRITE_CMD_BASE + 0);
	public static final short SPEED_DOWN = (WRITE_CMD_BASE + 1);
	public static final short TURN_LEFT = (WRITE_CMD_BASE + 2);
	public static final short TURN_RIGHT = (WRITE_CMD_BASE + 3);
	public static final short TURN_DIRECT = (WRITE_CMD_BASE + 4);
	public static final short START = (WRITE_CMD_BASE + 5);
	public static final short STOP = (WRITE_CMD_BASE + 6);
	public static final short OPERATE_CAR = (WRITE_CMD_BASE + 7);
	public static final short ENTER_FOLLOW_ROAD_MODE = (WRITE_CMD_BASE + 8);
	public static final short EXIT_FOLLOW_ROAD_MODE = (WRITE_CMD_BASE + 9);
	public static final short ENTER_REAL_CONTROL_MODE = (WRITE_CMD_BASE + 10);
	public static final short ENTER_AUTO_NAV_MODE = (WRITE_CMD_BASE + 11);
	public static final short ADJUST_ARM_ANGLE = (WRITE_CMD_BASE + 12);
	public static final short OPERATE_ARM = (WRITE_CMD_BASE + 13);
	public static final short ADJUST_VIDEO_MODE = (WRITE_CMD_BASE + 14);
	public static final short LASER_CTRL = (WRITE_CMD_BASE + 15);
	
	/*获取小车摄像头图像和视频*/
	public static final short UDP_DATA_ACQ_BASE = 0x100;
	public static final short ACQUIRE_CAMERA_IMAGE = (UDP_DATA_ACQ_BASE + 0);
	public static final short ACQUIRE_CAMERA_VIDEO_START = (UDP_DATA_ACQ_BASE + 1);
	public static final short ACQUIRE_CAMERA_VIDEO_STOP = (UDP_DATA_ACQ_BASE + 2);


	/*获取小车当前的状态信息*/
	public static final short TCP_DATA_ACQ_BASE = 0x200;
	public static final short ACQUIRE_ROBOT_INFO = (TCP_DATA_ACQ_BASE + 0);
	
	/*对ARM进行一些远程设置*/
	public static final short SET_ARM_BASE = 0x300;
	public static final short SET_LCD_BASE = (SET_ARM_BASE + 0);
	public static final short SET_LCD_WITH_PIC = (SET_LCD_BASE + 0);
	public static final short SET_LCD_CLR_SCREEN = (SET_LCD_BASE + 1);
	public static final short SET_LCD_SHOW_STRING = (SET_LCD_BASE + 2);
	public static final short SET_LCD_SHOW_CAMREA_START = (SET_LCD_BASE + 3);
	public static final short SET_LCD_SHOW_CAMREA_STOP = (SET_LCD_BASE + 4);

}
