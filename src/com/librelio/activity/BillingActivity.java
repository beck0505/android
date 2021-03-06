/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 * 
 *    http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package com.librelio.activity;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;

import com.android.vending.billing.IInAppBillingService;
import com.librelio.LibrelioApplication;
import com.librelio.base.BaseActivity;
import com.librelio.view.InputTextDialog;
import com.librelio.view.InputTextDialog.OnEnterValueListener;
import com.niveales.wind.R;

/**
 * The class for purchases via Google Play
 * 
 * @author Nikolay Moskvin <moskvin@netcook.org>
 * 
 */
public class BillingActivity extends BaseActivity {
	private static final String TAG = "BillingActivity";

	// Only for test. Must always be FALSE!
	private static final boolean TEST_MODE = false;
	/*
	 * productId can be the following values:
	 *	android.test.purchased
	 *	android.test.canceled
	 *	android.test.refunded
	 *	android.test.item_unavailable
	 */
	public static final String SUBSCRIPTION_PREF = "SubscriptionPreferences";
	public static final String PARAM_SUBSCRIPTION_CODE = "PARAM_SUBSCRIPTION_CODE";
	
	private static final String TEST_PRODUCT_ID = "android.test.purchased";
	
	private static final String PARAM_PRODUCT_ID = "@product_id";
	private static final String PARAM_DATA = "@data";
	private static final String PARAM_SIGNATURE = "@signature";
	private static final String PARAM_URLSTRING = "@urlstring";
	private static final String PARAM_CODE = "@code";
	private static final String PARAM_CLIENT = "@client";
	private static final String PARAM_APP = "@app";
	
	private static final String UNAUTHORIZED_STRING = "Unauthorized";
	
	private static final int CALLBACK_CODE = 101;
	private static final int UNAUTHORIZED_CODE = 401;
	
	private static final int BILLING_RESPONSE_RESULT_OK = 0;
	private static final int BILLING_RESPONSE_RESULT_USER_CANCELED = 1;
	private static final int BILLING_RESPONSE_RESULT_BILLING_UNAVAILABLE = 3;
	private static final int BILLING_RESPONSE_RESULT_ITEM_UNAVAILABLE = 5;
	private static final int BILLING_RESPONSE_RESULT_ITEM_ALREADY_OWNED = 7;
	
	private String fileName;
	private String title;
	private String subtitle;
	private String productId;
	private String productPrice;
	private String productTitle;
	private String yearlySubPrice;
	private String yearlySubTitle;
	private String monthlySubPrice;
	private String monthlySubTitle;

	private Button buy;
	private Button cancel;
	private Button subsYear;
	private Button subsMonthly;
	private Button subsCode;

	private String ownedItemSignature = "";
	private String ownedItemPurshaseData = "";

	private IInAppBillingService billingService;

	private SharedPreferences subscrPref;
	private Editor subscrPrefEd;


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.wait_bar);
		if(!isNetworkConnected()){
			showAlertDialog(CONNECTION_ALERT);
		} else {
			getSubscriptionPreferences();
			bindService(
					new Intent(
							"com.android.vending.billing.InAppBillingService.BIND"), 
							mServiceConn, 
							Context.BIND_AUTO_CREATE);
			fileName = getIntent().getExtras().getString(DownloadActivity.FILE_NAME_KEY);
			title = getIntent().getExtras().getString(DownloadActivity.TITLE_KEY);
			subtitle = getIntent().getExtras().getString(DownloadActivity.SUBTITLE_KEY);
			int finId = fileName.indexOf("/");
			productId = fileName.substring(0, finId);
		}
	}
	
	private void getSubscriptionPreferences(){
		subscrPref = getSharedPreferences(SUBSCRIPTION_PREF, MODE_PRIVATE);
		subscrPrefEd = subscrPref.edit();
	}
	
	private void initViews() {
		
		String prefSubscrCode = 
				subscrPref.getString(PARAM_SUBSCRIPTION_CODE, null);

		if (prefSubscrCode != null){
			new DownloadSubsrcFromTempURLTask().execute(
					buildPswdQuery(prefSubscrCode), prefSubscrCode);
			return;
		}
 		
		setContentView(R.layout.billing_activity);
		buy = (Button)findViewById(R.id.billing_buy_button);
		subsMonthly = (Button)findViewById(R.id.billing_subs_monthly);
		subsYear = (Button)findViewById(R.id.billing_subs_year);
		subsCode = (Button)findViewById(R.id.billing_subs_code_button);
		cancel = (Button)findViewById(R.id.billing_cancel_button);
		//
		cancel.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {

				finish();
			}
		});
		//
		if(productPrice == null){
			buy.setVisibility(View.GONE);
		} else {
			buy.setText(productTitle + ": "+ productPrice);
			buy.setOnClickListener(new OnClickListener(){
				@Override
				public void onClick(View v) {
					purchaseItem();
				}

			});
		}
		//
		if(yearlySubPrice == null){
			subsYear.setVisibility(View.GONE);;
		} else {
			subsYear.setText(yearlySubTitle + ": "+ yearlySubPrice);
			subsYear.setOnClickListener(new OnClickListener(){
				@Override
				public void onClick(View v) {
					purchaseYearlySub();
				}

			});
		}
		
		if(monthlySubPrice == null){
			subsMonthly.setVisibility(View.GONE);;
		} else {
			subsMonthly.setText(monthlySubTitle + ": "+ monthlySubPrice);
			subsMonthly.setOnClickListener(new OnClickListener(){
				@Override
				public void onClick(View v) {
					purchaseMonthlySub();
				}

			});
		}


	
		if (LibrelioApplication.isEnableCodeSubs(getContext())){
			subsCode.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					InputTextDialog dialog = new InputTextDialog(getContext(),
							getString(R.string.please_enter_your_code));
					dialog.setOnEnterValueListener(onEnterValueListener);
					dialog.show();
				}
			});
		}else{
			subsCode.setVisibility(View.GONE);
		}
	}

	private ServiceConnection mServiceConn = new ServiceConnection() {
		@Override
		public void onServiceDisconnected(ComponentName name) {
			billingService = null;
		}

		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			
			billingService = IInAppBillingService.Stub.asInterface(service);
			new AsyncTask<String, String, Bundle>() {
				private Bundle ownedItems = null;
				private Bundle ownedSubs = null;


				@Override
				protected Bundle doInBackground(String... params) {
					Bundle skuDetails = null;
					try {
						ArrayList<String> skuList = new ArrayList<String>();
						skuList.add(productId);

						//Add  subscription codes
						skuList.add(LibrelioApplication.getYearlySubsCode(getContext()));
						skuList.add(LibrelioApplication.getMonthlySubsCode(getContext()));


						
						Bundle querySkus = new Bundle();
						querySkus.putStringArrayList("ITEM_ID_LIST", skuList);
						
						//Retrieve relevant in app items
						skuDetails = billingService.getSkuDetails(3, getPackageName(), "inapp", querySkus);
						ArrayList<String> details = skuDetails.getStringArrayList("DETAILS_LIST");
	
						//Retrieve relevant subscriptions
						skuDetails = billingService.getSkuDetails(3, getPackageName(), "subs", querySkus);
						ArrayList<String> subsDetails = skuDetails.getStringArrayList("DETAILS_LIST");
						
						//Combine in app and subscriptions
						details.addAll(subsDetails);
						skuDetails.putStringArrayList("DETAILS_LIST",details);
						
						//Retrieve owned in app items
						ownedItems = billingService.getPurchases(3, getPackageName(), "inapp", null);
						//Retrieve owned AND current subscriptions 
						ownedSubs = billingService.getPurchases(3, getPackageName(), "subs", null);
						
						
						
					} catch (RemoteException e) {
						Log.d(TAG, "InAppBillingService failed", e);
						return null;
					}
					return skuDetails;
				}

				@Override
				protected void onPostExecute(Bundle skuDetails) {
					//If item was purchase then download begin without open billing activity 
					int getPurchaseResponse = ownedItems.getInt("RESPONSE_CODE");
					if (TEST_MODE) {
						getPurchaseResponse = -1;
					}
					if(getPurchaseResponse == 0){
						ArrayList<String> ownedSkus = ownedItems.getStringArrayList("INAPP_PURCHASE_ITEM_LIST");
						for(String s : ownedSkus){
							Log.d(TAG, productId + " already purchased? " + s);
						}
						if(ownedSkus.contains(productId)){
							prepareDownloadWithOwnedItem(ownedItems,productId);
							return;
						}
						ownedSkus = ownedSubs.getStringArrayList("INAPP_PURCHASE_ITEM_LIST");
						for(String s : ownedSkus){
							Log.d(TAG, productId + " already purchased? " + s);
						}

						
						if(ownedSkus.contains(LibrelioApplication.getYearlySubsCode(getContext()))){
							prepareDownloadWithOwnedItem(ownedSubs,LibrelioApplication.getYearlySubsCode(getContext()));
							return;
						}
						if(ownedSkus.contains(LibrelioApplication.getMonthlySubsCode(getContext()))){
							prepareDownloadWithOwnedItem(ownedSubs,LibrelioApplication.getMonthlySubsCode(getContext()));
							return;
						}


						
					}
					//
					int response = skuDetails.getInt("RESPONSE_CODE");
					if (response == 0) {
						Log.d(TAG, "response code was success");
						ArrayList<String> details = skuDetails.getStringArrayList("DETAILS_LIST");
						for (String detail : details) {
							Log.d(TAG, "response = " + detail);
							JSONObject object = null;
							String sku = "";
							String price = "";
							String title = "";
							try {
								object = new JSONObject(detail);
								sku = object.getString("productId");
								price = object.getString("price");
								title = object.getString("title");
							} catch (JSONException e) {
								Log.e(TAG, "getSKU details failed", e);
							}
							if (sku.equals(productId)) {
								productPrice = price;
								productTitle = title;
							}
							else if (sku.equals(LibrelioApplication.getYearlySubsCode(getContext()))){
								yearlySubPrice = price;
								yearlySubTitle = title;
								
							}
							else if (sku.equals(LibrelioApplication.getMonthlySubsCode(getContext()))){
								monthlySubPrice = price;
								monthlySubTitle = title;
								
							}

						}
					}
					initViews();
					super.onPostExecute(skuDetails);
				}
				protected void prepareDownloadWithOwnedItem(Bundle ownedBundle, String subsoritemID) {
					ArrayList<String> ownedSkus = ownedBundle.getStringArrayList("INAPP_PURCHASE_ITEM_LIST");
					int idx = ownedSkus.indexOf(subsoritemID);
					ArrayList<String> purchaseDataList = 
							ownedBundle.getStringArrayList("INAPP_PURCHASE_DATA_LIST");
					ArrayList<String> signatureList = 
							ownedBundle.getStringArrayList("INAPP_DATA_SIGNATURE_LIST");
					Log.d(TAG,"[getPurchases] purchaseDataList: "+purchaseDataList);
					Log.d(TAG,"[getPurchases] signatureList: "+signatureList);
					if(purchaseDataList!=null){
						ownedItemPurshaseData = purchaseDataList.get(idx);
					}
					if(signatureList!=null){
						ownedItemSignature = signatureList.get(idx);
					}
					onDownloadAction(ownedItemPurshaseData,ownedItemSignature);
					return;

				}
 
				
			}.execute();
		}
	};

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		Log.d(TAG, requestCode + " " + resultCode);
		Log.d(TAG, "data = " + data.getExtras().getString("INAPP_PURCHASE_DATA"));
		Log.d(TAG, "signature = " + data.getExtras().getString("INAPP_DATA_SIGNATURE"));

		if (requestCode == CALLBACK_CODE) {
			String purchaseData = data.getStringExtra("INAPP_PURCHASE_DATA");

			if (resultCode == RESULT_OK && purchaseData != null) {
				try {
					JSONObject jo = new JSONObject(purchaseData);
					String sku = jo.getString("productId");
					String dataResponse = data.getExtras().getString("INAPP_PURCHASE_DATA");
					String signatureResponse = data.getExtras().getString("INAPP_DATA_SIGNATURE");
					Log.d(TAG, "You have bought the " + sku + ". Excellent choice, adventurer!");
					onDownloadAction(dataResponse, signatureResponse);
				} catch (JSONException e) {
					Log.e(TAG, "Failed to parse purchase data.", e);
				}
			} else {
				finish();
			}
		} else {
			finish();
		}
	}

	@Override
	protected void onDestroy() {
		if (isNetworkConnected()) {
			unbindService(mServiceConn);
		}
		super.onDestroy();
	}

	protected void onDownloadAction(String dataResponse, String signatureResponse) {
		new DownloadFromTempURLTask().execute(buildVerifyQuery(dataResponse, signatureResponse));
	}
	
	private void purchaseItem(){
				new PurchaseTask().execute(productId);
	}
	private void purchaseMonthlySub(){
		new PurchaseTask().execute(LibrelioApplication.getMonthlySubsCode(getContext()));
	}
	private void purchaseYearlySub(){
		new PurchaseTask().execute(LibrelioApplication.getYearlySubsCode(getContext()));
	}


	private boolean isNetworkConnected() {
		return LibrelioApplication.thereIsConnection(this);
	}

	private Context getContext(){
		return this;
	}

	private String buildVerifyQuery(String dataResponse, String signatureResponse) {
		
		StringBuilder query = new StringBuilder(
				LibrelioApplication.getServerUrl(getContext()));
		
		String comand = getString(R.string.command_android_verify)
				.replace(";", "&")
				.replace(PARAM_PRODUCT_ID, Uri.encode(productId))
				.replace(PARAM_DATA, Uri.encode(dataResponse))
				.replace(PARAM_SIGNATURE, Uri.encode(signatureResponse))
				.replace(PARAM_URLSTRING, Uri.encode(
						LibrelioApplication.getUrlString(getContext(), fileName)));
		
		return query.append(comand).toString();
	}
	
	private String buildPswdQuery(String code) {
		
		StringBuilder query = new StringBuilder(
				LibrelioApplication.getServerUrl(getContext()));
		
		String comand = getString(R.string.command_pswd)
				.replace(";", "&")
				.replace(PARAM_CODE, Uri.encode(code))
				.replace(PARAM_URLSTRING, Uri.encode(
						LibrelioApplication.getUrlString(fileName)))
				.replace(PARAM_CLIENT, Uri.encode(LibrelioApplication.getClientName(getContext())))
				.replace(PARAM_APP, Uri.encode(LibrelioApplication.getMagazineName(getContext())));
		
		return query.append(comand).toString();
	}

	private class DownloadFromTempURLTask extends AsyncTask<String, Void, HttpResponse>{
		@Override
		protected HttpResponse doInBackground(String... params) {
			
			HttpClient httpclient = new DefaultHttpClient();
			String verifyQuery = params[0];
			Log.d(TAG, "Verify query = " + verifyQuery);
			try {
				HttpGet httpget = new HttpGet(verifyQuery);
				HttpClientParams.setRedirecting(httpclient.getParams(), false);
				return httpclient.execute(httpget);
			} catch (IllegalArgumentException e) {
				Log.e(TAG, "URI is malformed", e);
			} catch (ClientProtocolException e) {
				Log.e(TAG, "Download failed", e);
			} catch (IOException e) {
				Log.e(TAG, "Download failed", e);
			}
			return null;
		}

		protected void onPostExecute(HttpResponse response) {
			String tempURL = null;
			if (null == response) {
				showAlertDialog(DOWNLOAD_ALERT);
				Log.w(TAG, "download response was null");
				return;
			}

			Log.d(TAG, "status line: " + response.getStatusLine().toString());
			HttpEntity entity = response.getEntity();

			DataInputStream bodyStream = null;
			if (entity != null) {
				try {
					bodyStream = new DataInputStream(entity.getContent());
					StringBuilder content = new StringBuilder();
					if (null != bodyStream) {
						String line = null;
						while((line = bodyStream.readLine()) != null) {
							content.append(line).append("\n");
						}
					}
					Log.d(TAG, "body: " + content.toString());
				} catch (Exception e) {
					Log.e(TAG, "get content failed", e); 
				} finally {
					try { bodyStream.close(); } catch (Exception e) {}
				}
			}
			if (null != response.getAllHeaders()) {
				for(Header h : response.getAllHeaders()){
					if(h.getName().equalsIgnoreCase("location")){
						tempURL = h.getValue();
					}
					Log.d(TAG, "header: " + h.getName() + " => " + h.getValue());
				}
			}
			if(tempURL == null){
				//Toast.makeText(getContext(), "Download failed", Toast.LENGTH_SHORT).show();
				showAlertDialog(DOWNLOAD_ALERT);
				
				return;
			}
			Intent intent = new Intent(getContext(), DownloadActivity.class);
			intent.putExtra(DownloadActivity.FILE_NAME_KEY, fileName);
			intent.putExtra(DownloadActivity.SUBTITLE_KEY, subtitle);
			intent.putExtra(DownloadActivity.TITLE_KEY, title);
			intent.putExtra(DownloadActivity.IS_TEMP_KEY, true);
			intent.putExtra(DownloadActivity.IS_SAMPLE_KEY, false);
			intent.putExtra(DownloadActivity.TEMP_URL_KEY, tempURL);
			startActivity(intent);
			
			finish();
		};
	}
	
	private class DownloadSubsrcFromTempURLTask extends DownloadFromTempURLTask{
		
		private String subscrCode;
		
		@Override
		protected HttpResponse doInBackground(String... params) {
			subscrCode = params[1];
			return super.doInBackground(params);		
		}
		
		@Override
		protected void onPostExecute(HttpResponse response) {
			if (null != response) {
				String responseStatus = response.getStatusLine().toString();
				int responseCode = response.getStatusLine().getStatusCode();
				
				if (responseStatus.contains(UNAUTHORIZED_STRING) && 
					responseCode == UNAUTHORIZED_CODE){
					subscrPrefEd.remove(PARAM_SUBSCRIPTION_CODE).commit(); 
				}else{
					String prefSubscrCode = 
							subscrPref.getString(PARAM_SUBSCRIPTION_CODE, null);
					if (prefSubscrCode == null){
						subscrPrefEd.putString(
								PARAM_SUBSCRIPTION_CODE, subscrCode).commit();
					}
				}
			}
			
			super.onPostExecute(response);
		}
	}


	private class PurchaseTask extends AsyncTask<String, String, Bundle>{
		private Bundle ownedItems;
		
		@Override
		protected Bundle doInBackground(String... params) {
			String itemToBuyId = params[0];
			Log.d(TAG, "id: " + itemToBuyId);
			try {
				ownedItems = billingService.getPurchases(3, getPackageName(), "inapp", null);
				if (TEST_MODE) {
					productId = TEST_PRODUCT_ID;
				}
				if (itemToBuyId == productId){
					Log.d(TAG, "let us buy a product " + itemToBuyId);
					return billingService.getBuyIntent(3, getPackageName(), productId, "inapp", null);
				}
				else{
					Log.d(TAG, "let us buy a subscription " + itemToBuyId);
					return billingService.getBuyIntent(3, getPackageName(), itemToBuyId, "subs", null);
					
				}
				
			} catch (RemoteException e) {
				Log.e(TAG, "Problem with getBuyIntent", e);
			}
			return null;
		}

		protected void onPostExecute(Bundle result) {
			super.onPostExecute(result);

			if (null == result) {
				return;
			}
			int response = result.getInt("RESPONSE_CODE");
			Log.d(TAG, "Purchase response = " + response);
			switch (response) {
			case BILLING_RESPONSE_RESULT_USER_CANCELED:
			case BILLING_RESPONSE_RESULT_BILLING_UNAVAILABLE:
			case BILLING_RESPONSE_RESULT_ITEM_UNAVAILABLE:
				Toast.makeText(getContext(), "Error!", Toast.LENGTH_LONG).show();
				return;
			}
			if(response == BILLING_RESPONSE_RESULT_ITEM_ALREADY_OWNED){
				Log.d(TAG, productId + " ITEM_ALREADY_OWNED ");
				if(ownedItemPurshaseData!=null&ownedItemSignature!=null){
					if((!ownedItemPurshaseData.equals(""))&(!ownedItemSignature.equals(""))){
						onDownloadAction(ownedItemPurshaseData,ownedItemSignature);
					}
				}
				return;
			} else if(response == BILLING_RESPONSE_RESULT_OK){
				PendingIntent pendingIntent = result.getParcelable("BUY_INTENT");
				Log.d(TAG, "pendingIntent = " + pendingIntent);
				if (pendingIntent == null) {
					Toast.makeText(getContext(), "Error!", Toast.LENGTH_LONG).show();
					return;
				}
				try {
					startIntentSenderForResult(pendingIntent.getIntentSender(), CALLBACK_CODE, new Intent(), 0, 0, 0);
				} catch (SendIntentException e) {
					Log.e(TAG, "Problem with startIntentSenderForResult", e);
				}
			}
		}
	}
	
	private OnEnterValueListener onEnterValueListener = new OnEnterValueListener() {
		@Override
		public void onEnterValue(String code) {
			setContentView(R.layout.wait_bar);
			new DownloadSubsrcFromTempURLTask().execute(buildPswdQuery(code), code);
		}
	};
	
}
