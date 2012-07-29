package ly.count.android.api;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.OpenUDID.OpenUDID_manager;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.telephony.TelephonyManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

public class Countly
{
	private static Countly sharedInstance_;
	private Timer timer_;
	private ConnectionQueue queue_;
	private boolean isVisible_;
	private double unsentSessionLength_;
	private double lastTime_;

	static public Countly sharedInstance()
	{
		if (sharedInstance_ == null)
			sharedInstance_ = new Countly();
		
		return sharedInstance_;
	}
	
	private Countly()
	{
		queue_ = new ConnectionQueue();
		timer_ = new Timer();
		timer_.schedule(new TimerTask()
		{
			@Override
			public void run()
			{
				onTimer();
			}
		}, 30 * 1000,  30 * 1000);

		isVisible_ = false;
		unsentSessionLength_ = 0;
	}
	
	public void init(Context context, String serverURL, String appKey)
	{
		OpenUDID_manager.sync(context);
		queue_.setContext(context);
		queue_.setServerURL(serverURL);
		queue_.setAppKey(appKey);
	}

	public void onStart()
	{
		lastTime_ = System.currentTimeMillis() / 1000.0;

		queue_.beginSession();

		isVisible_ = true;
	}
	
	public void onStop()
	{
		isVisible_ = false;

		double currTime = System.currentTimeMillis() / 1000.0;
		unsentSessionLength_ += currTime - lastTime_;

		int duration = (int)unsentSessionLength_;
		queue_.endSession(duration);
		unsentSessionLength_ -= duration;
	}
	
	private void onTimer()
	{
		if (isVisible_ == false)
			return;
		
		double currTime = System.currentTimeMillis() / 1000.0;
		unsentSessionLength_ += currTime - lastTime_;
		lastTime_ = currTime;
		
		int duration = (int)unsentSessionLength_;
		queue_.updateSession(duration);
		unsentSessionLength_ -= duration;
	}
}

class ConnectionQueue
{
	private ConcurrentLinkedQueue<String> queue_ = new ConcurrentLinkedQueue<String>();
	private Thread thread_ = null;
	private String appKey_;
	private Context context_;
	private String serverURL_;
	
	public void setAppKey(String appKey)
	{
		appKey_ = appKey;
	}

	public void setContext(Context context)
	{
		context_ = context;
	}
	
	public void setServerURL(String serverURL)
	{
		serverURL_ = serverURL;
	}
	
	public void beginSession()
	{
		String data;
		data  =       "app_key=" + appKey_;
		data += "&" + "device_id=" + DeviceInfo.getUDID();
		data += "&" + "sdk_version=" + "1.0";
		data += "&" + "begin_session=" + "1";
		data += "&" + "metrics=" + DeviceInfo.getMetrics(context_);
		
		queue_.offer(data);		
	
		tick();
	}

	public void updateSession(int duration)
	{
		String data;
		data  =       "app_key=" + appKey_;
		data += "&" + "device_id=" + DeviceInfo.getUDID();
		data += "&" + "session_duration=" + duration;

		queue_.offer(data);		

		tick();
	}
	
	public void endSession(int duration)
	{
		String data;
		data  =       "app_key=" + appKey_;
		data += "&" + "device_id=" + DeviceInfo.getUDID();
		data += "&" + "end_session=" + "1";
		data += "&" + "session_duration=" + duration;

		queue_.offer(data);		
		
		tick();
	}
	
	private void tick()
	{
		if (thread_ != null && thread_.isAlive())
			return;
		
		if (queue_.isEmpty())
			return;
				
		thread_ = new Thread() 
		{
			@Override
			public void run()
			{
				while (true)
				{
					String data = queue_.peek();

					if (data == null)
						break;
					
					int index = data.indexOf("REPLACE_UDID");
					if (index != -1)
					{
						if (OpenUDID_manager.isInitialized() == false)
							break;						
						data.replaceFirst("REPLACE_UDID", OpenUDID_manager.getOpenUDID());						
					}
					
					try
					{
						DefaultHttpClient httpClient = new DefaultHttpClient();
						HttpGet method = new HttpGet(new URI(serverURL_ + "/i?" + data));			
						HttpResponse response = httpClient.execute(method);
						InputStream input = response.getEntity().getContent();
						while (input.read() != -1)
							;
						httpClient.getConnectionManager().shutdown();
												
						Log.d("Countly", "ok ->" + data);

						queue_.poll();
					}
					catch (Exception e)
					{
						Log.d("Countly", e.toString());
						Log.d("Countly", "error ->" + data);
						break;
					}
				}
			}
		};

		thread_.start();
	}
}


class DeviceInfo
{
	public static String getUDID()
	{
		return OpenUDID_manager.isInitialized() == false ? "REPLACE_UDID" : OpenUDID_manager.getOpenUDID();
	}
	
	public static String getOS()
	{
		return "Android";
	}
	
	public static String getOSVersion()
	{
		return android.os.Build.VERSION.RELEASE;
	}

	public static String getDevice()
	{
		return android.os.Build.MODEL;
	}
	
	public static String getResolution(Context context)
	{
		WindowManager wm = (WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
		
		Display display = wm.getDefaultDisplay();

		DisplayMetrics metrics = new DisplayMetrics();
		display.getMetrics(metrics);

		return metrics.heightPixels + "x" + metrics.widthPixels;
	}
	
	public static String getCarrier(Context context)
	{
		TelephonyManager manager = (TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE);
		return manager.getNetworkOperatorName();
	}

	public static String getLocale()
	{
		Locale locale = Locale.getDefault();
		return locale.getLanguage() + "_" + locale.getCountry();
	}
	
	public static String appVersion(Context context)
	{
		String result = "1.0";
		try {
			result = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
		} catch (NameNotFoundException e) {
		}		

		return result;
	}

	public static String getMetrics(Context context)
	{
		String result = "{";
		
		result +=       "\"" + "_device"      + "\"" + ":" + "\"" + getDevice()            + "\"";
		
		result += "," + "\"" + "_os"          + "\"" + ":" + "\"" + getOS()                + "\"";
		
		result += "," + "\"" + "_os_version"  + "\"" + ":" + "\"" + getOSVersion()         + "\"";
		
		result += "," + "\"" + "_carrier"     + "\"" + ":" + "\"" + getCarrier(context)    + "\"";
		
		result += "," + "\"" + "_resolution"  + "\"" + ":" + "\"" + getResolution(context) + "\"";
		
		result += "," + "\"" + "_locale"      + "\"" + ":" + "\"" + getLocale()            + "\"";

		result += "," + "\"" + "_app_version" + "\"" + ":" + "\"" + appVersion(context)            + "\"";

		result += "}";
		
		try
		{
			result = java.net.URLEncoder.encode(result, "UTF-8");
		} catch (UnsupportedEncodingException e)
		{
			
		}

		return result;
	}
}
