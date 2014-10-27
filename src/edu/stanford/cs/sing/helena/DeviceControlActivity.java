/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.stanford.cs.sing.helena;

import java.util.List;

import android.app.Activity;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Point;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;
import edu.stanford.cs.sing.common.helper.ByteWork;
import edu.stanford.cs.sing.helena.ble.BluetoothLeService;
import edu.stanford.cs.sing.helena.ble.HelenaGattAttributes;
import edu.stanford.cs.sing.helena.nodes.FireAdapter;
import edu.stanford.cs.sing.helena.nodes.FireArray;
import edu.stanford.cs.sing.helena.nodes.Firestorm;
import edu.stanford.cs.sing.helena.nodes.ObservAdapter;

/**
 * For a given BLE device, this Activity provides the user interface to connect,
 * display data, and display GATT services and characteristics supported by the
 * device. The Activity communicates with {@code BluetoothLeService}, which in
 * turn interacts with the Bluetooth LE API.
 */
public class DeviceControlActivity extends Activity {
	private final static String TAG = DeviceControlActivity.class
			.getSimpleName();

	public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
	public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

	private TextView mConnectionState;

	private String mDeviceName;
	private String mDeviceAddress;
	private BluetoothLeService mBluetoothLeService;
	private boolean mConnected = false;
	private BluetoothGattCharacteristic mNotifyCharacteristic;
	private BluetoothGattService mHelenaService;
	private BluetoothGattCharacteristic mObservationCharacteristic;
	private OnItemClickListener mFireListOnClickListner;
	private PopupWindow popWindow;
	public FireArray mFirestormArray;
	private FireAdapter mFireAdapter;
	private ObservAdapter mObserverAdapter;
	private boolean mFireLitDisplay;

	// Code to manage Service lifecycle.
	private final ServiceConnection mServiceConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName componentName,
				IBinder service) {
			mBluetoothLeService = ((BluetoothLeService.LocalBinder) service)
					.getService();
			if (!mBluetoothLeService.initialize()) {
				Log.e(TAG, "Unable to initialize Bluetooth");
				finish();
			}
			// Automatically connects to the device upon successful start-up
			// initialization.
			mBluetoothLeService.connect(mDeviceAddress);
		}

		@Override
		public void onServiceDisconnected(ComponentName componentName) {
			mBluetoothLeService = null;
		}
	};

	// Handles various events fired by the Service.
	// ACTION_GATT_CONNECTED: connected to a GATT server.
	// ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
	// ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
	// ACTION_DATA_AVAILABLE: received data from the device. This can be a
	// result of read
	// or notification operations.
	private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			final String action = intent.getAction();
			Log.d(TAG, "BroadcastonReceive " + action);
			if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
				mConnected = true;
				updateConnectionState(R.string.connected);
				invalidateOptionsMenu();

			} else if (BluetoothLeService.ACTION_GATT_DISCONNECTED
					.equals(action)) {
				mConnected = false;
				updateConnectionState(R.string.disconnected);
				invalidateOptionsMenu();
				// clearUI();
			} else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED
					.equals(action)) {

				checkServices(mBluetoothLeService.getSupportedGattServices());
			} else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
				Log.d(TAG, "BroadcastReceiver: " + action);
				// dealWithData(intent
				// .getByteArrayExtra(BluetoothLeService.BYTE_DATA));

			} else if (BluetoothLeService.ACTION_NOTIFY_DATA_AVAILABLE
					.equals(action)) {
				Log.d(TAG, "BroadcastReceiver: " + action);

			} else if (BluetoothLeService.ACTION_READ_DATA_AVAILABLE
					.equals(action)) {
				Log.d(TAG, "BroadcastReceiver: " + action);

			} else {
				Log.e(TAG, "Got action, no reaction: " + action);
			}
		}
	};

	private void dealWithData(byte[] data) {
		// int rx = ByteWork.convertTwoBytesToInt(data);
		// byte[] device = ByteWork.getBytes(data, 0, 5);
		// byte[] observed = ByteWork.getBytes(data, 6, 15);
		StringBuilder str = new StringBuilder(data.length);
		for (byte byteChar : data)
			str.append(String.format("%02X ", byteChar));
		//
		// mFirestormArray.addDeviceData(str.toString(), observed);
		Log.d(TAG, "Deal with data: " + str.toString());
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mFireLitDisplay = false;
		mFirestormArray = new FireArray();
		mFireListOnClickListner = new FireListOnClickListner();
		setContentView(R.layout.device_control_activity);

		//
		final Intent intent = getIntent();
		mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
		mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);
		Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
		bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
		getActionBar().setTitle(mDeviceName);
		getActionBar().setDisplayHomeAsUpEnabled(true);
		((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);

		// mGattServicesList.setOnChildClickListener(servicesListClickListner);
		mConnectionState = (TextView) findViewById(R.id.connection_state);
		// Create the adapter to convert the array to views
		mFireAdapter = new FireAdapter(DeviceControlActivity.this,
				mFirestormArray);

		addFireList();

		Log.d(TAG, "onCreate bindService");
	}

	@Override
	public void onBackPressed() {
		Log.d(TAG, "OnBackPress");
		if (mFireLitDisplay) {
			super.onBackPressed();
		} else {
			addFireList();
		}
	}

	public void onShowPopup(View view, final int position) {

		LayoutInflater layoutInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);

		// inflate the custom popup layout
		final View inflatedView = layoutInflater.inflate(
				R.layout.observer_view, null, false);

		// find the ListView in the popup layout
		mFireLitDisplay = false;
		Firestorm mFire = mFirestormArray.get(position);

		Log.d(TAG, "FIRE ID " + mFire.id);

		// get device size
		Display display = getWindowManager().getDefaultDisplay();
		final Point size = new Point();
		display.getSize(size);

		// set height depends on the device size
		popWindow = new PopupWindow(inflatedView, size.x - 50, size.y - 400,
				true);
		// set a background drawable with rounders corners
		popWindow.setBackgroundDrawable(getResources().getDrawable(
				R.drawable.dialog_background));
		// make it focusable to show the keyboard to enter in `EditText`
		popWindow.setFocusable(true);
		// make it outside touchable to dismiss the popup window
		popWindow.setOutsideTouchable(true);

		// show the popup at bottom of the screen and set some margin at bottom
		// ie,
		// popWindow.showAtLocation(getCurrentFocus(), Gravity.TOP, 0, 100);
		// //showAtLocation(v, Gravity.BOTTOM, 0,100);
		findViewById(R.id.fire_list).post(new Runnable() {
			@Override
			public void run() {
				popWindow.showAtLocation(findViewById(R.id.fire_list),
						Gravity.CENTER, 0, 0);
				addDetailList(inflatedView, position);
			}
		});
	}

	private void addDetailList(View inflatedView, int position) {
		mFireLitDisplay = false;
		Firestorm mFire = mFirestormArray.get(position);
		ListView listView = (ListView) inflatedView
				.findViewById(R.id.list_observed);
		Log.d(TAG, "FIRE ID " + mFire.id);
		((TextView) inflatedView.findViewById(R.id.popup_header)).setText(""
				+ mFire.toString());
		((TextView) inflatedView.findViewById(R.id.popup_header_columt_1))
				.setText("MAC");
		((TextView) inflatedView.findViewById(R.id.popup_header_columt_2))
				.setText("Time");
		mObserverAdapter = new ObservAdapter(DeviceControlActivity.this,
				mFire.getObservationList());

		listView.setAdapter(mObserverAdapter);

	}

	class FireListOnClickListner implements OnItemClickListener {

		@Override
		public void onItemClick(AdapterView<?> parent, View view, int position,
				long id) {
			onShowPopup(view, position);
			Log.d(TAG, "onClick " + position);
		}

	}

	private void addFireList() {
		if (!mFireLitDisplay) {
			Log.d(TAG, "Connected to " + mDeviceAddress);
			mFireLitDisplay = true;
			((TextView) findViewById(R.id.header_columt_1))
					.setText(R.string.addr);
			((TextView) findViewById(R.id.header_columt_2))
					.setText(R.string.number);
			((TextView) findViewById(R.id.header_columt_3))
					.setText(R.string.label_last_seen);
			// Attach the adapter to a ListView
			ListView listView = (ListView) findViewById(R.id.fire_list);
			listView.setAdapter(mFireAdapter);
			listView.setOnItemClickListener(mFireListOnClickListner);

		}
	}

	@Override
	protected void onResume() {
		super.onResume();

		registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
		if (mBluetoothLeService != null) {
			final boolean result = mBluetoothLeService.connect(mDeviceAddress);
			Log.d(TAG, "Connect request result=" + result);
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		unregisterReceiver(mGattUpdateReceiver);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		unbindService(mServiceConnection);
		mBluetoothLeService = null;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.gatt_services, menu);
		if (mConnected) {
			menu.findItem(R.id.menu_connect).setVisible(false);
			menu.findItem(R.id.menu_disconnect).setVisible(true);
		} else {
			menu.findItem(R.id.menu_connect).setVisible(true);
			menu.findItem(R.id.menu_disconnect).setVisible(false);
		}
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (mDeviceAddress != null) {
			switch (item.getItemId()) {
			case R.id.menu_connect:
				mBluetoothLeService.connect(mDeviceAddress);
				return true;
			case R.id.menu_disconnect:
				mBluetoothLeService.disconnect();
				return true;
			case android.R.id.home:
				onBackPressed();
				return true;
			}
		} else {
			Log.d(TAG, "onOptionsItemSelected mDeviceAddress == null ");
		}
		return super.onOptionsItemSelected(item);
	}

	private void updateConnectionState(final int resourceId) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				mConnectionState.setText(resourceId);
			}
		});
	}

	@Override
	public void onSaveInstanceState(Bundle savedInstanceState) {
		// good practice to save
		// TODO save the instance
		Log.d(TAG, "onSaveInstanceState");

		super.onSaveInstanceState(savedInstanceState);
	}

	@Override
	public void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
		Log.d(TAG, "onRestoreInstanceState");
		// Restore UI state from the savedInstanceState.
		// TODO: implement restorations of the instance
	}

	private void checkServices(List<BluetoothGattService> gattServices) {
		if (gattServices == null)
			return;
		String uuid;
		for (BluetoothGattService gattService : gattServices) {
			uuid = gattService.getUuid().toString();
			if (uuid.equals(HelenaGattAttributes.HELENA_2_SERVICE)) {
				mHelenaService = gattService;
				List<BluetoothGattCharacteristic> gattCharacteristics = gattService
						.getCharacteristics();
				for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
					if (gattCharacteristic
							.getUuid()
							.toString()
							.equals(HelenaGattAttributes.PIPE_READDEVICE_DATAAVAILABLE_TX)) {

						final int charaProp = gattCharacteristic
								.getProperties();
						if ((charaProp == BluetoothGattCharacteristic.PROPERTY_NOTIFY)) {
							// Initiate notifications
							mNotifyCharacteristic = gattCharacteristic;
							Log.d("checkServices", "mNotifyCharacteristic");
							mBluetoothLeService.setCharacteristicNotification(
									mNotifyCharacteristic, true);
						}
					} else if (gattCharacteristic
							.getUuid()
							.toString()
							.equals(HelenaGattAttributes.PIPE_READDEVICE_OBSERVATION_SET)) {
						final int charaProp = gattCharacteristic
								.getProperties();
						if ((charaProp == BluetoothGattCharacteristic.PROPERTY_READ)) {
							mObservationCharacteristic = gattCharacteristic;
							
							Log.d("checkServices", "mObservationCharacteristic");
						} else {
							Log.d("checkServices",
									"mObservationCharacteristic not a read property: "
											+ String.valueOf(charaProp));
						}
					} else {
						Log.e("checkServices",
								"Should had these characteristics");
					}
				}

			} else {
				Log.d("checkServices", "Skipping other services");
			}
		}
	}

	private static IntentFilter makeGattUpdateIntentFilter() {
		final IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
		intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
		intentFilter
				.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
		intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
		intentFilter.addAction(BluetoothLeService.ACTION_NOTIFY_DATA_AVAILABLE);
		intentFilter.addAction(BluetoothLeService.ACTION_READ_DATA_AVAILABLE);
		return intentFilter;
	}
}
