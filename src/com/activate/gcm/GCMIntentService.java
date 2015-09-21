package com.activate.gcm;

import java.io.IOException;
import java.io.File;
import java.util.HashMap;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiProperties;
import org.appcelerator.kroll.common.Log;

import com.google.android.gcm.GCMBaseIntentService;
import com.google.android.gcm.GCMBroadcastReceiver;
import com.activate.gcm.C2dmModule;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.support.v4.app.NotificationCompat;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap;

import org.json.JSONObject;

public class GCMIntentService extends GCMBaseIntentService {

	private static final String LCAT = "C2DMReceiver";

	private static final String REGISTER_EVENT = "registerC2dm";
	private static final String UNREGISTER_EVENT = "unregister";
	private static final String MESSAGE_EVENT = "message";
	private static final String ERROR_EVENT = "error";

	private static int notificationId = 1;

	public GCMIntentService(){
		super(TiApplication.getInstance().getAppProperties().getString("com.activate.gcm.sender_id", ""));
	}

	@Override
	public void onRegistered(Context context, String registrationId){
		Log.d(LCAT, "Registered: " + registrationId);

		C2dmModule.getInstance().sendSuccess(registrationId);
	}

	@Override
	public void onUnregistered(Context context, String registrationId) {
		Log.d(LCAT, "Unregistered");

		C2dmModule.getInstance().fireEvent(UNREGISTER_EVENT, new HashMap());
	}

	@Override
	protected void onMessage(Context context, Intent intent) {
		Log.d(LCAT, "Message received");

		notificationId++;

		TiProperties systProp = TiApplication.getInstance().getAppProperties();

		HashMap data = new HashMap();
		for (String key : intent.getExtras().keySet()) {
			Log.d(LCAT, "Message key: " + key + " value: " + intent.getExtras().getString(key));

			String eventKey = key.startsWith("data.") ? key.substring(5) : key;
			data.put(eventKey, intent.getExtras().getString(key));
		}

		int icon = systProp.getInt("com.activate.gcm.icon", 0);
		//another way to get icon :
		//http://developer.appcelerator.com/question/116650/native-android-java-module-for-titanium-doesnt-generate-rjava
		CharSequence tickerText = (CharSequence) data.get("ticker");
		long when = System.currentTimeMillis();

		CharSequence contentTitle = (CharSequence) data.get("title");
		CharSequence contentText = (CharSequence) data.get("message");
		CharSequence contentUri = (CharSequence) data.get("uri");
        
        
		Intent notificationIntent = new Intent(this, GCMIntentService.class);

		Intent launcherintent = new Intent("android.intent.action.MAIN");
		launcherintent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED | Intent.FLAG_FROM_BACKGROUND);
		//I'm sure there is a better way ...
		launcherintent.setComponent(ComponentName.unflattenFromString(systProp.getString("com.activate.gcm.component", "")));
		//
		launcherintent.addCategory("android.intent.category.LAUNCHER");
		if( contentUri != null ) {
			launcherintent.putExtra( "uri", contentUri);
		}

		PendingIntent contentIntent = PendingIntent.getActivity(this, notificationId, launcherintent, 0);

		// the next two lines initialize the Notification, using the
		// configurations above

	// Send to the app if the instance exists, otherwise handle it ourselves as per config.
	if(C2dmModule.getInstance() != null) {
		JSONObject json = new JSONObject(data);
		systProp.setString("com.activate.gcm.last_data", json.toString());
	        Log.d(LCAT, "sending message to C2dmModule");
	    	C2dmModule.getInstance().sendMessage(data);
	} else if( contentText != null ) {
            Log.d(LCAT, "creating notification ...");

            Bitmap largeIcon = BitmapFactory.decodeResource(getResources(), icon);
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context)
										            .setLargeIcon(largeIcon)
										            .setContentTitle(contentTitle)
										            .setContentText(contentText)
										            .setContentIntent(contentIntent)
										            .setTicker(tickerText);
        	int smallIcon = systProp.getInt("com.activate.gcm.smallIcon", 0);
        	if (smallIcon != 0) {
        		builder.setSmallIcon(smallIcon);
        	}
        	Notification notification = builder.build();

			// Custom
			CharSequence vibrate = (CharSequence) data.get("vibrate");
			CharSequence sound = (CharSequence) data.get("sound");
            
			if("default".equals(sound)) {
				Log.e(LCAT, "Notification: DEFAULT_SOUND");
                notification.defaults |= Notification.DEFAULT_SOUND;
			}
			else if(sound != null) {
                
				Log.e(LCAT, "Notification: sound "+sound);
                
				String[] packagename = systProp.getString("com.activate.gcm.component", "").split("/");
                
				String baseDir = android.os.Environment.getExternalStorageDirectory().getAbsolutePath();
				String path = baseDir + "/"+ packagename[0] +"/sound/"+sound;
                
				Log.e(LCAT, path);
                
				File file = new File(path);
				
				Log.i(TAG,"Sound exists : " + file.exists());
                
				if (file.exists()) {
					Uri soundUri = Uri.fromFile(file);
                    notification.sound = soundUri;
				}
				else {
                    notification.defaults |= Notification.DEFAULT_SOUND;
				}
			}
			
			if(vibrate != null) {
				notification.defaults |= Notification.DEFAULT_VIBRATE;
			}
			
			notification.defaults |= Notification.DEFAULT_LIGHTS;
            
			Log.d(LCAT, "pushing to notification manager" );
			notification.flags = Notification.FLAG_AUTO_CANCEL;
			String ns = Context.NOTIFICATION_SERVICE;
			NotificationManager mNotificationManager = (NotificationManager) getSystemService(ns);
			mNotificationManager.notify(notificationId, notification);
        }
	
    }

	@Override
	public void onError(Context context, String errorId) {
		Log.e(LCAT, "Error: " + errorId);

		C2dmModule.getInstance().sendError(errorId);
	}

	@Override
	public boolean onRecoverableError(Context context, String errorId) {
		Log.e(LCAT, "RecoverableError: " + errorId);

		C2dmModule.getInstance().sendError(errorId);

		return true;
	}

}
