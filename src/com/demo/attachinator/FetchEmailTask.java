package com.demo.attachinator;

import java.io.IOException;
import java.util.List;

import org.json.JSONException;

import android.os.AsyncTask;
import android.util.Log;

import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.batch.BatchRequest;
import com.google.api.client.googleapis.batch.json.JsonBatchCallback;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;

public class FetchEmailTask extends AsyncTask<Void, Void, Void> {

	private String mScope;
	private AttachmentListActivity mActivity;
	private String mEmail;

	private static final String TAG = "TokenInfoTask";
	private static final String APP_NAME = "Attachinator";

	public FetchEmailTask(AttachmentListActivity activity, String email, String scope) {
		mActivity = activity;
		mEmail = email;
		mScope = scope;
	}

	@Override
	protected Void doInBackground(Void... params) {
		try {
			fetchEmailFromGoogle();
		} catch (IOException ex) {
			onError("Following Error occured, please try again. "
					+ ex.getMessage(), ex);
		} catch (JSONException e) {
			onError("Bad response: " + e.getMessage(), e);
		}
		return null;
	}

	/**
	 * Get a authentication token if one is not available. If the error is not
	 * recoverable then it displays the error message on parent activity right
	 * away.
	 */
	protected String fetchToken() throws IOException {
		try {
			return GoogleAuthUtil.getToken(mActivity, mEmail, mScope);
		} catch (UserRecoverableAuthException userRecoverableException) {
			// GooglePlayServices.apk is either old, disabled, or not present,
			// which is
			// recoverable, so we need to show the user some UI through the
			// activity.
			mActivity.handleException(userRecoverableException);
		} catch (GoogleAuthException fatalException) {
			onError("Unrecoverable error " + fatalException.getMessage(),
					fatalException);
		}
		return null;
	}

	protected void onError(String msg, Exception e) {
		if (e != null) {
			Log.e(TAG, "Exception: ", e);
		}
		mActivity.show(msg); // will be run in UI thread
	}

	private void fetchEmailFromGoogle() throws IOException, JSONException {
		String token = fetchToken();
		if (token == null) {
			// error has already been handled in fetchToken()
			return;
		}

		HttpTransport httpTransport = new NetHttpTransport();
		JsonFactory jsonFactory = new JacksonFactory();
		GoogleCredential credential = new GoogleCredential()
				.setAccessToken(token);

		// Create a new authorized Gmail API client

		Gmail service = new Gmail.Builder(httpTransport, jsonFactory,
				credential).setApplicationName(APP_NAME).build();

		BatchRequest b = service.batch();
		// callback function. (Can also define different callbacks for each
		// request, as required)
		JsonBatchCallback<Message> bc = new JsonBatchCallback<Message>() {

			@Override
			public void onSuccess(Message t, HttpHeaders responseHeaders)
					throws IOException {

				List<MessagePart> parts = t.getPayload().getParts();
				for (MessagePart part : parts) {
					if (part.getFilename() != null
							&& part.getFilename().length() > 0) {
						String filename = part.getFilename();
						String attId = part.getBody().getAttachmentId();
						String mimeType = part.getMimeType();
						System.out.println(mimeType + ":" + attId + ":"
								+ filename);
					}
				}

			}

			@Override
			public void onFailure(GoogleJsonError e, HttpHeaders responseHeaders)
					throws IOException {

			}
		};

		// Retrieve a page of Threads; max of 100 by default.

		ListMessagesResponse messagesResponse = service.users().messages()
				.list(mEmail).setQ("has:attachment").execute();
		List<Message> emailsWithAttachment = messagesResponse.getMessages();

		// queuing requests on the batch requests
		for (Message email : emailsWithAttachment) {
			service.users().messages().get(mEmail, email.getId()).queue(b, bc);
		}

		b.execute();

	}

}
