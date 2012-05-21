/*
 * Copyright (C) 2010 Haowen Ning
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * 
 * See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 */
package cz.destil.fixbrokenpb;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Process;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.Toast;

import com.markupartist.android.widget.ActionBar;

public class SettingsActivity extends Activity implements
        OnCheckedChangeListener {
	private PendingIntent mAlarmSender;
	private AlarmManager am;
	private Handler mHandler;
	private SharedPreferences settings;
	/* Magic number */
	private final static int REQ_CODE = 0x575f72a;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		ActionBar actionBar = (ActionBar) findViewById(R.id.actionbar);
		actionBar.setTitle(R.string.app_name);
		actionBar.setHomeLogo(R.drawable.ic_launcher);

		Button button = (Button) findViewById(R.id.action_button);
		settings = PreferenceManager.getDefaultSharedPreferences(this);
		if (settings.getBoolean("enabled", false)) {
			if (Build.VERSION.SDK_INT == 8) {
				button.setText(R.string.disable_22);
			} else {
				button.setText(R.string.disable_23);
			}
		} else {
			if (Build.VERSION.SDK_INT == 8) {
				button.setText(R.string.enable_22);
			} else {
				button.setText(R.string.enable_23);
			}
		}

		CheckBox checkbox = (CheckBox) findViewById(R.id.enable_unlock_screen);
		checkbox.setOnCheckedChangeListener(this);
		checkbox.setChecked(settings.getBoolean("enable_unlock_screen", true));
		checkbox.setPadding(
		        checkbox.getPaddingLeft()
		                + (int) (7.0f * getResources().getDisplayMetrics().density + 0.5f),
		        checkbox.getPaddingTop(), checkbox.getPaddingRight(),
		        checkbox.getPaddingBottom());

		am = (AlarmManager) getSystemService(ALARM_SERVICE);
		mAlarmSender = PendingIntent.getService(this, REQ_CODE, new Intent(
		        this, UnlockService.class), 0);
		mHandler = new Handler();
	}

	public void buttonClicked(View view) {
		if (settings.getBoolean("enabled", false)) {
			// disable
			settings.edit().putBoolean("enabled", false).commit();
			Toast.makeText(this, R.string.custom_unlocking_disabled,
			        Toast.LENGTH_SHORT).show();
			am.cancel(mAlarmSender);
			disableAdmin();
			stopService(new Intent(this, UnlockService.class));
			mHandler.postDelayed(new Runnable() {
				public void run() {
					Process.killProcess(Process.myPid());
				}
			}, 400);
		} else {
			// enable
			AlertDialog.Builder alert = new AlertDialog.Builder(this);
			alert.setTitle(R.string.important);
			alert.setMessage(R.string.need_to_be_disabled);
			alert.setCancelable(true);
			alert.setPositiveButton(R.string.i_understand,
			        new DialogInterface.OnClickListener() {
				        public void onClick(DialogInterface dialog, int which) {
							settings.edit().putBoolean("enabled", true).commit();
							long firstTime = SystemClock.elapsedRealtime();
							am.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, firstTime,
							        600 * 1000, mAlarmSender);
							enableAdmin();
							Toast.makeText(SettingsActivity.this, R.string.custom_unlocking_enabled,
							        Toast.LENGTH_SHORT).show();
							Button button = (Button) findViewById(R.id.action_button);
							if (Build.VERSION.SDK_INT == 8) {
								button.setText(R.string.disable_22);
							} else {
								button.setText(R.string.disable_23);
							}
				        }
			        });
			alert.show();
		}
	}

	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main_menu, menu);
		return true;
	}

	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.about:
			startActivity(new Intent(this, AboutActivity.class));
			return true;

		case R.id.donate:
			Intent intent = new Intent(
			        Intent.ACTION_VIEW,
			        Uri.parse("https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=PSHU456C862DQ"));
			startActivity(intent);
			return true;
		}
		return false;
	}

	@Override
	public void onResume() {
		TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		if (tm.getCallState() != 0) {
			finish();
		}
		super.onResume();
	}

	@Override
	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
		settings.edit().putBoolean("enable_unlock_screen", isChecked).commit();
	}

	private void enableAdmin() {
		Intent myIntent = new Intent(this, AdminPermissionSet.class);
		startActivityForResult(myIntent, 18);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == 18) {
			KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
			KeyguardManager.KeyguardLock kl = km
			        .newKeyguardLock(getString(R.string.app_name));
			kl.disableKeyguard();

		}

		super.onActivityResult(requestCode, resultCode, data);
	}

	private void disableAdmin() {
		ComponentName mAdminReceiver = new ComponentName(this,
		        AdminReceiver.class);
		DevicePolicyManager mDPM = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
		mDPM.removeActiveAdmin(mAdminReceiver);
	}

}
