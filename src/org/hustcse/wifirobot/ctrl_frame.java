package org.hustcse.wifirobot;

import android.util.Log;

public class ctrl_frame {
	String TAG = "Ctrl Frame";
	boolean D = true;
	int max_datasize = 1024;			/* 最大的数据量 */
	short ctrl_prefix_ack;						/* 是否需要ACK应答 */
	short ctrl_prefix_operate;		/* 控制命令前缀： 命令操作类型： 读/写 */
	short ctrl_prefix_request;		/* 控制命令前缀： 命令操作请求数据(如果有)的量的大小: 大量数据请求/少量数据请求	*/
	short ctrl_prefix;				/* 控制命令的前缀，主要是控制命令的类型说明 */
	short ctrl_cmd;					/* 控制命令： 这个是用于表示当前的命令的编码 */
	short ctrlcode;					/* 控制字：包含控制前缀和控制字 */
	short datalength;				/* 数据长度 */
	byte[] data = new byte[max_datasize];	/* 包含的数据 */
	boolean show_data = false;

	private boolean available = false;				/* 控制帧是否可用 */

		
	/* 构造函数 默认初始化全部为0 */
	public ctrl_frame (){
		this.construct_frame((short)0xff,  (short)0xffffff,  null);
		this.display_ctrl_frame();
	}
	
	/* 构造函数 传入参数为控制前缀 控制命令 数据(需要发送的数据) */
	public ctrl_frame (short ctrl_prefix, short ctrl_cmd, byte[] msg){
		this.construct_frame(ctrl_prefix,  ctrl_cmd,  msg);
		this.display_ctrl_frame();
		this.available = true;
	}
	
	/* 构造函数 重载 这次是可以更改 log时显示的TAG */
	public ctrl_frame (short ctrl_prefix, short ctrl_cmd, byte[] msg, String DEBUG_TAG){
		this.TAG = DEBUG_TAG;
		this.construct_frame(ctrl_prefix,  ctrl_cmd,  msg);
		this.display_ctrl_frame();
		this.available = true;
	}
	
	/* 在LOG中显示帧的详细信息 */
	public boolean display_ctrl_frame (){
		if(D && this.available) {
			Log.i(TAG, "------The Ctrl Frame Display Start-----");	
			Log.i(TAG, "Ctrl Prefix: " +  String.format("%1$#4x", ctrl_prefix));
			Log.i(TAG, "Ctrl Command: " +  String.format("%1$#4x", ctrl_cmd));
			Log.i(TAG, "Data Length: " +  String.format("%1$d", datalength));
			if (datalength > 0){
				if ((show_data == true) || (datalength < 10)){ /*十个数据以内就进行显示*/
					String hexstring = "";
					for (short i = 0; i < datalength; i++){
						hexstring = hexstring.concat(String.format(" %1$#4x", data[i])) ;
					}
					Log.i(TAG, "Data Packets: " +  hexstring);
				}else{
					Log.i(TAG, "Data Packets is too big, or no need to show it!");
				}
			}else{
				Log.i(TAG, "No Data Packets!");
			}
			Log.i(TAG, "------The Ctrl Frame Display End-----");	
		}
		return this.available;
	}
	
	/* 察看当前帧是否可用 */
	public boolean available(){
		return this.available;
	}
	
	public  boolean encode_frametobytes(byte[] msg){
		int tmp2 = 0;
		
		if (msg == null){
			return false;
		}
		if (this.available){
			tmp2 = (ctrlcode << 16) + datalength;
			msg[0] = (byte) ((tmp2 >> 24) & 0xff);
			msg[1] = (byte) ((tmp2 >> 16) & 0xff);
			msg[2] = (byte) ((tmp2 >> 8) & 0xff);
			msg[3] = (byte) ((tmp2 >> 0) & 0xff);
		
			if (datalength > 0){
				System.arraycopy(data, 0, msg, 4, datalength);
			}
		}

		return this.available;
	}
	
	/* 将传入的字节数组转化为一帧数据，成功的话返回true，失败返回false */
	public boolean decode_framefrombytes(byte[] msg){
		boolean ercd;
		if (msg == null){
			ercd = false;
		}else{
			if (msg.length >= 4){
				this.ctrl_prefix = (short) ( (msg[0] >> 4) );
				this.ctrl_cmd = (short) ( ( (msg[0] & 0xf) << 8) + msg[1]);
				decode_ctrlprefix();
				encode_ctrlcode();
				if (this.datalength > 0){
					System.arraycopy(data, 0, msg, 4, this.datalength);
				}
				Log.i(TAG, "Construct A new Frame From bytes Input Success!");	
				ercd = true;
				available = true;
			}else{
				Log.e(TAG, "Construct A new Frame From bytes Input Failed!Input Bytes is not enough,only " + msg.length);	
				ercd = false;
			}
		}
		return ercd;
	}
	
	/* 根据传入参数构造一帧 */
	private void construct_frame(short ctrl_prefix, short ctrl_cmd, byte[] msg){
		this.ctrl_prefix = (short) (ctrl_prefix & ctrl_prefixs.ctrl_prefix_mask);
		this.ctrl_cmd = (short) (ctrl_cmd & ctrl_prefixs.ctrl_mask);
		decode_ctrlprefix();
		encode_ctrlcode();
		if (msg == null){
			this.datalength =  (short) 0;
		}else{
			//this.datalength =  (short) Math.min((short) msg.length, this.max_datasize);
			this.datalength =  (short) msg.length;
			if (this.datalength > this.max_datasize){
				this.datalength = (short) this.max_datasize;
			}
		}
		if (this.datalength > 0) { /* 进行数据拷贝*/
			System.arraycopy(msg, 0, this.data, 0, this.datalength);
		}
	}
	
	/* 将控制前缀解析为更小的单位 */
	private void decode_ctrlprefix(){
		this.ctrl_prefix_operate = (short) ( ( (this.ctrl_prefix & (ctrl_prefixs.operate_mask)) 
				>> (ctrl_prefixs.operate_offset)) );
		this.ctrl_prefix_request = (short) ( (this.ctrl_prefix & (ctrl_prefixs.data_request_mask)) 
				>> (ctrl_prefixs.data_request_offset) );
		this.ctrl_prefix_ack = (short) ( (this.ctrl_prefix & (ctrl_prefixs.ack_mask) ) 
				>> (ctrl_prefixs.ack_offset));
	}
	/* 将控制前缀和控制命令整合为控制字 */
	private void encode_ctrlcode(){
		this.ctrlcode = (short) ( (this.ctrl_prefix << ctrl_prefixs.ctrl_prefix_offset) | this.ctrl_cmd );
	}
	
}
