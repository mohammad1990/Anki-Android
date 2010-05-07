package com.ichi2.anki;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import com.ichi2.async.Connection;
import com.ichi2.async.Connection.Payload;
import com.tomgibara.android.veecheck.util.PrefSettings;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;


public class StudyOptions extends Activity
{
	/**
	 * Default database
	 */
	public static final String OPT_DB = "com.ichi2.anki.deckFilename";
	
	/**
	 * Tag for logging messages
	 */
	private static final String TAG = "Ankidroid";
	
	/**
	 * Filename of the sample deck to load
	 */
	private static final String SAMPLE_DECK_NAME = "country-capitals.anki";
	
	/**
	 * Menus
	 */
	private static final int MENU_OPEN = 1;
	
	private static final int MENU_DOWNLOAD_PERSONAL_DECK = 2;
	
	private static final int MENU_DOWNLOAD_SHARED_DECK = 3;

	private static final int MENU_PREFERENCES = 4;
	
	private static final int MENU_DECK_PROPERTIES = 5;
	
	private static final int MENU_SYNC = 6;

	private static final int MENU_ABOUT = 7;
	
	/**
	 * Available options returning from another activity
	 */
	private static final int PICK_DECK_REQUEST = 0;

	private static final int PREFERENCES_UPDATE = 1;
	
	private static final int REQUEST_REVIEW = 2;
	
	private static final int DOWNLOAD_PERSONAL_DECK = 3;
	
	private static final int DOWNLOAD_SHARED_DECK = 4;
	
	/** 
	 * Constants for selecting which content view to display 
	 */
	private static final int CONTENT_NO_DECK = 0;
	
	private static final int CONTENT_STUDY_OPTIONS = 1;
	
	private static final int CONTENT_CONGRATS = 2;
	
	private static final int CONTENT_DECK_NOT_LOADED = 3;
	
	private static final int CONTENT_SESSION_COMPLETE = 4;
	
	/**
	 * Preferences
	 */
	private String prefDeckPath;
	
	private boolean prefStudyOptions;
	
	//private boolean deckSelected;
	
	private boolean inDeckPicker;
	
	private String deckFilename;
	
	/* package */ ProgressDialog mProgressDialog;
	
	private int mCurrentContentView;
	
	/**
	 * Alerts to inform the user about different situations
	 */
	private ProgressDialog progressDialog;

	private AlertDialog noConnectionAlert;
	
	private AlertDialog connectionFailedAlert;
	
	/** 
	 * UI elements for "Study Options" view 
	 */
	private View mStudyOptionsView;
	
	private Button mButtonStart;
	
	private TextView mTextTitle;
	
	private TextView mTextReviewsDue;
	
	private TextView mTextNewToday;
	
	private TextView mTextNewTotal;
	
	private EditText mEditNewPerDay;
	
	private EditText mEditSessionTime;
	
	private EditText mEditSessionQuestions;

	/** 
	 * UI elements for "More Options" dialog
	 */
	private AlertDialog mDialogMoreOptions;
	
	private Spinner mSpinnerNewCardOrder;
	
	private Spinner mSpinnerNewCardSchedule;
	
	private Spinner mSpinnerRevCardOrder;
	
	private Spinner mSpinnerFailCardOption;
	
	/** 
	 * UI elements for "No Deck" view
	 */
	private View mNoDeckView;
	
	private TextView mTextNoDeckTitle;
	
	private TextView mTextNoDeckMessage;
	
	/** 
	 * UI elements for "Congrats" view
	 */
	private View mCongratsView;
	
	private TextView mTextCongratsMessage;
	
	private Button mButtonCongratsLearnMore;
	
	private Button mButtonCongratsReviewEarly;
	
	private Button mButtonCongratsFinish;
	
	/** 
	 * Callbacks for UI events
	 */
	private View.OnClickListener mButtonClickListener = new View.OnClickListener()
	{
		@Override
		public void onClick(View v)
		{
			switch (v.getId())
			{
			case R.id.studyoptions_start:
				//finish();
				Intent reviewer = new Intent(StudyOptions.this, Reviewer.class);
				reviewer.putExtra("deckFilename", deckFilename);
				startActivityForResult(
						reviewer,
						REQUEST_REVIEW
						);
				return;
			case R.id.studyoptions_more:
				showMoreOptionsDialog();
				return;
			case R.id.studyoptions_load_sample_deck:
				loadSampleDeck();
				return;
			case R.id.studyoptions_load_other_deck:
				openDeckPicker();
				return;
			default:
				return;
			}
		}
	};

	
	private View.OnFocusChangeListener mEditFocusListener = new View.OnFocusChangeListener()
	{
		@Override
		public void onFocusChange(View v, boolean hasFocus)
		{
			Deck deck = AnkiDroidApp.deck();
			if (!hasFocus)
				switch (v.getId())
				{
				case R.id.studyoptions_new_cards_per_day:
					deck.setNewCardsPerDay(Integer.parseInt(((EditText)v).getText().toString()));
					updateValuesFromDeck();
					return;
				case R.id.studyoptions_session_minutes:
					deck.setSessionTimeLimit(Long.parseLong(((EditText)v).getText().toString()) * 60);
					return;
				case R.id.studyoptions_session_questions:
					deck.setSessionRepLimit(Long.parseLong(((EditText)v).getText().toString()));
					return;
				default:
					return;
				}
		}
	};
	
	private DialogInterface.OnClickListener mDialogSaveListener = new DialogInterface.OnClickListener()
	{
		@Override
		public void onClick(DialogInterface dialog, int which)
		{
			Deck deck = AnkiDroidApp.deck();
			deck.setNewCardOrder(mSpinnerNewCardOrder.getSelectedItemPosition());
			deck.setNewCardSpacing(mSpinnerNewCardSchedule.getSelectedItemPosition());
			deck.setRevCardOrder(mSpinnerRevCardOrder.getSelectedItemPosition());
			
			dialog.dismiss();
		}
	};
	
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		
		Log.i(TAG, "StudyOptions Activity");
		
		SharedPreferences preferences = restorePreferences();
		//registerExternalStorageListener();
		
		// Remove the status bar and make title bar progress available
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		
		initAllContentViews();
		initAllAlertDialogs();
		
		if (savedInstanceState != null)
		{
			// Use the same deck as last time Ankidroid was used.
			deckFilename = savedInstanceState.getString("deckFilename");
			Log.i(TAG, "onCreate - deckFilename from savedInstanceState: " + deckFilename);
		} else
		{
			Log.i(TAG, "onCreate - " + preferences.getAll().toString());
			deckFilename = preferences.getString("deckFilename", null);
			Log.i(TAG, "onCreate - deckFilename from preferences: " + deckFilename);
		}
		
		if (deckFilename == null || !new File(deckFilename).exists())
			showContentView(CONTENT_NO_DECK);
		else
		{
			// Load previous deck.
			Intent deckLoadIntent = new Intent();
			deckLoadIntent.putExtra(OPT_DB, deckFilename);
			onActivityResult(PICK_DECK_REQUEST, RESULT_OK, deckLoadIntent);
		}
			
	}
	
	private void initAllContentViews()
	{
		// The main study options view that will be used when there are reviews left.
		mStudyOptionsView = getLayoutInflater().inflate(R.layout.studyoptions, null);
		
		mTextTitle = (TextView) mStudyOptionsView.findViewById(R.id.studyoptions_title);
		
		mButtonStart = (Button) mStudyOptionsView.findViewById(R.id.studyoptions_start);
		mStudyOptionsView.findViewById(R.id.studyoptions_more).setOnClickListener(mButtonClickListener);
		
		mTextReviewsDue = (TextView) mStudyOptionsView.findViewById(R.id.studyoptions_reviews_due);
		mTextNewToday = (TextView) mStudyOptionsView.findViewById(R.id.studyoptions_new_today);
		mTextNewTotal = (TextView) mStudyOptionsView.findViewById(R.id.studyoptions_new_total);
		
		mEditNewPerDay = (EditText) mStudyOptionsView.findViewById(R.id.studyoptions_new_cards_per_day);
		mEditSessionTime = (EditText) mStudyOptionsView.findViewById(R.id.studyoptions_session_minutes);
		mEditSessionQuestions = (EditText) mStudyOptionsView.findViewById(R.id.studyoptions_session_questions);
		
		mButtonStart.setOnClickListener(mButtonClickListener);
		mEditNewPerDay.setOnFocusChangeListener(mEditFocusListener);
		mEditSessionTime.setOnFocusChangeListener(mEditFocusListener);
		mEditSessionQuestions.setOnFocusChangeListener(mEditFocusListener);
		
		mDialogMoreOptions = createDialog();
		
		// The view to use when there is no deck loaded yet.
		// TODO: Add and init view here.
		mNoDeckView = getLayoutInflater().inflate(R.layout.studyoptions_nodeck, null);
		
		mTextNoDeckTitle = (TextView) mNoDeckView.findViewById(R.id.studyoptions_nodeck_title);
		mTextNoDeckMessage = (TextView) mNoDeckView.findViewById(R.id.studyoptions_nodeck_message);
		
		mNoDeckView.findViewById(R.id.studyoptions_load_sample_deck).setOnClickListener(mButtonClickListener);
		mNoDeckView.findViewById(R.id.studyoptions_load_other_deck).setOnClickListener(mButtonClickListener);
		
		// The view that shows the congratulations view.
		mCongratsView = getLayoutInflater().inflate(R.layout.studyoptions_congrats, null);
		
		mTextCongratsMessage = (TextView) mCongratsView.findViewById(R.id.studyoptions_congrats_message);
		mButtonCongratsLearnMore = (Button) mCongratsView.findViewById(R.id.studyoptions_congrats_learnmore);
		mButtonCongratsReviewEarly = (Button) mCongratsView.findViewById(R.id.studyoptions_congrats_reviewearly);
		mButtonCongratsFinish = (Button) mCongratsView.findViewById(R.id.studyoptions_congrats_finish);
	}
	
	/**
	 * Create AlertDialogs used on all the activity
	 */
	private void initAllAlertDialogs()
	{
		Resources res = getResources();
		
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		
		builder.setMessage(res.getString(R.string.connection_needed));
		builder.setPositiveButton(res.getString(R.string.ok), null);
		noConnectionAlert = builder.create();
		
		builder.setMessage(res.getString(R.string.connection_unsuccessful));
		connectionFailedAlert = builder.create();
	}
	
	private AlertDialog createDialog()
	{
		// Custom view for the dialog content.
		View contentView = getLayoutInflater().inflate(R.layout.studyoptions_more_dialog_contents, null);
		mSpinnerNewCardOrder = (Spinner) contentView.findViewById(R.id.studyoptions_new_card_order);
		mSpinnerNewCardSchedule = (Spinner) contentView.findViewById(R.id.studyoptions_new_card_schedule);
		mSpinnerRevCardOrder = (Spinner) contentView.findViewById(R.id.studyoptions_rev_card_order);
		mSpinnerFailCardOption = (Spinner) contentView.findViewById(R.id.studyoptions_fail_card_option);
		
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.studyoptions_more_dialog_title);
		builder.setPositiveButton(R.string.studyoptions_more_save, mDialogSaveListener);
		builder.setView(contentView);
		
		return builder.create();
	}
	
	private void showMoreOptionsDialog()
	{
		// Update spinner selections from deck prior to showing the dialog.
		Deck deck = AnkiDroidApp.deck();
		mSpinnerNewCardOrder.setSelection(deck.getNewCardOrder());
		mSpinnerNewCardSchedule.setSelection(deck.getNewCardSpacing());
		mSpinnerRevCardOrder.setSelection(deck.getRevCardOrder());
		mSpinnerFailCardOption.setVisibility(View.GONE); // TODO: Not implemented yet.
		
		mDialogMoreOptions.show();
	}
	
	private void showContentView(int which)
	{
		mCurrentContentView = which;
		showContentView();
	}
	
	private void showContentView()
	{
		switch (mCurrentContentView)
		{
		case CONTENT_NO_DECK:
			setTitle(R.string.app_name);
			mTextNoDeckTitle.setText(R.string.studyoptions_nodeck_title);
			mTextNoDeckMessage.setText(String.format(
					getResources().getString(R.string.studyoptions_nodeck_message), 
					prefDeckPath));
			setContentView(mNoDeckView);
			break;
		case CONTENT_DECK_NOT_LOADED:
			setTitle(R.string.app_name);
			mTextNoDeckTitle.setText(R.string.studyoptions_deck_not_loaded_title);
			mTextNoDeckMessage.setText(R.string.studyoptions_deck_not_loaded_message);
			setContentView(mNoDeckView);
		case CONTENT_STUDY_OPTIONS:
			updateValuesFromDeck();
			mButtonStart.setText(R.string.studyoptions_start);
			mTextTitle.setText(R.string.studyoptions_title);
			setContentView(mStudyOptionsView);
			break;
		case CONTENT_SESSION_COMPLETE:
			updateValuesFromDeck();
			mButtonStart.setText(R.string.studyoptions_continue);
			mTextTitle.setText(R.string.studyoptions_well_done);
			setContentView(mStudyOptionsView);
			break;
		case CONTENT_CONGRATS:
			updateValuesFromDeck();
			setContentView(mCongratsView);
			break;
		}
	}
	
	private void updateValuesFromDeck()
	{
		Deck deck = AnkiDroidApp.deck();
		DeckTask.waitToFinish();
		deck.checkDue();
		int reviewCount = deck.revCount + deck.failedSoonCount;
		String unformattedTitle = getResources().getString(R.string.studyoptions_window_title);
		setTitle(String.format(unformattedTitle, deck.deckName, reviewCount, deck.cardCount));
		
		mTextReviewsDue.setText(String.valueOf(reviewCount));
		mTextNewToday.setText(String.valueOf(deck.newCountToday));
		mTextNewTotal.setText(String.valueOf(deck.newCount));
		
		mEditNewPerDay.setText(String.valueOf(deck.getNewCardsPerDay()));
		mEditSessionTime.setText(String.valueOf(deck.getSessionTimeLimit()/60));
		mEditSessionQuestions.setText(String.valueOf(deck.getSessionRepLimit()));
	}

	@Override
	public void onSaveInstanceState(Bundle outState)
	{
		Log.i(TAG, "onSaveInstanceState: " + deckFilename);
		// Remember current deck's filename.
		if (deckFilename != null)
			outState.putString("deckFilename", deckFilename);
		Log.i(TAG, "onSaveInstanceState - Ending");
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuItem item;
		item = menu.add(Menu.NONE, MENU_OPEN, Menu.NONE, R.string.menu_open_deck);
		item.setIcon(android.R.drawable.ic_menu_manage);
		SubMenu downloadDeckSubMenu = menu.addSubMenu(R.string.download_deck);
		downloadDeckSubMenu.setIcon(R.drawable.ic_menu_download);
		downloadDeckSubMenu.add(Menu.NONE, MENU_DOWNLOAD_PERSONAL_DECK, Menu.NONE, R.string.download_personal_deck);
		downloadDeckSubMenu.add(Menu.NONE, MENU_DOWNLOAD_SHARED_DECK, Menu.NONE, R.string.download_shared_deck);
		item = menu.add(Menu.NONE, MENU_PREFERENCES, Menu.NONE, R.string.menu_preferences);
		item.setIcon(android.R.drawable.ic_menu_preferences);
		item = menu.add(Menu.NONE, MENU_DECK_PROPERTIES, Menu.NONE, R.string.deck_properties);
		item.setIcon(R.drawable.ic_menu_archive);
		item = menu.add(Menu.NONE, MENU_SYNC, Menu.NONE, R.string.menu_sync);
		item.setIcon(R.drawable.ic_menu_refresh);
		item = menu.add(Menu.NONE, MENU_ABOUT, Menu.NONE, R.string.menu_about);
		item.setIcon(android.R.drawable.ic_menu_info_details);
		
		return true;
	}
	
	/** Handles item selections */
	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
		case MENU_OPEN:
			openDeckPicker();
			return true;
		case MENU_DOWNLOAD_PERSONAL_DECK:
			Intent downloadPersonalDeck = new Intent(this, PersonalDeckPicker.class);
			startActivityForResult(downloadPersonalDeck, DOWNLOAD_PERSONAL_DECK); 
			break;
			
		case MENU_DOWNLOAD_SHARED_DECK:
			Connection.getSharedDecks(getSharedDecksListener, new Connection.Payload(new Object[] {}));
			break;
		case MENU_PREFERENCES:
			Intent preferences = new Intent(this, Preferences.class);
			startActivityForResult(preferences, PREFERENCES_UPDATE);
			return true;
		case MENU_DECK_PROPERTIES:
			Intent deckProperties = new Intent(this, DeckProperties.class);
			startActivity(deckProperties);
			break;
		case MENU_SYNC:
			syncDeck();
			return true;
		case MENU_ABOUT:
			Intent about = new Intent(this, About.class);
			startActivity(about);
			return true;
		}
		return false;
	}

	private void openDeckPicker()
	{
    	//Log.i(TAG, "openDeckPicker - deckSelected = " + deckSelected);
    	if(AnkiDroidApp.deck() != null )//&& sdCardAvailable)
    	{
    		AnkiDroidApp.deck().closeDeck();
    		AnkiDroidApp.setDeck(null);
    	}
    	//deckLoaded = false;
		Intent decksPicker = new Intent(this, DeckPicker.class);
		//inDeckPicker = true;
		startActivityForResult(decksPicker, PICK_DECK_REQUEST);
		//Log.i(TAG, "openDeckPicker - Ending");
	}
	
	public void openSharedDeckPicker()
	{
    	if(AnkiDroidApp.deck() != null )//&& sdCardAvailable)
    	{
    		AnkiDroidApp.deck().closeDeck();
    		AnkiDroidApp.setDeck(null);
    	}
    	//deckLoaded = false;
		Intent intent = new Intent(StudyOptions.this, SharedDeckPicker.class);
		startActivityForResult(intent, DOWNLOAD_SHARED_DECK);
	}
	
	private void loadSampleDeck()
	{
		File sampleDeckFile = new File(prefDeckPath, SAMPLE_DECK_NAME);
		
		if (!sampleDeckFile.exists())
		{
			// Create the deck.
			try
			{
				// Copy the sample deck from the assets to the SD card.
				InputStream stream = getResources().getAssets().open(SAMPLE_DECK_NAME);
				boolean written = Utils.writeToFile(stream, sampleDeckFile.getAbsolutePath());
				stream.close();
				if (!written)
				{
					openDeckPicker();
					Log.i(TAG, "onCreate - The copy of country-capitals.anki to the sd card failed.");
					return;
				}
				Log.i(TAG, "onCreate - The copy of country-capitals.anki to the sd card was sucessful.");
			} catch (IOException e)
			{
				e.printStackTrace();
			}
		}
		
		Intent deckLoadIntent = new Intent();
		deckLoadIntent.putExtra(OPT_DB, sampleDeckFile.getAbsolutePath());
		onActivityResult(PICK_DECK_REQUEST, RESULT_OK, deckLoadIntent);
	}
	
	private void syncDeck() {
		SharedPreferences preferences = PrefSettings.getSharedPrefs(getBaseContext());
		
		String username = preferences.getString("username", "");
		String password = preferences.getString("password", "");
		Deck deck = AnkiDroidApp.deck();
		
		Log.i(TAG, "Synchronizing deck " + deckFilename + " with username " + username + " and password " + password);
		Connection.syncDeck(syncListener, new Connection.Payload(new Object[] {username, password, deck, deckFilename}));		
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent)
	{
		super.onActivityResult(requestCode, resultCode, intent);
		if (requestCode == PICK_DECK_REQUEST || requestCode == DOWNLOAD_PERSONAL_DECK || requestCode == DOWNLOAD_SHARED_DECK)
		{
			//Clean the previous card before showing the first of the new loaded deck (so the transition is not so abrupt)
//			updateCard("");
//			hideSdError();
//			hideDeckErrors();
			inDeckPicker = false;
			
			if (resultCode != RESULT_OK)
			{
				Log.e(TAG, "onActivityResult - Deck browser returned with error");
				//Make sure we open the database again in onResume() if user pressed "back"
				//deckSelected = false;
				displayProgressDialogAndLoadDeck();
				return;
			}
			if (intent == null)
			{
				Log.e(TAG, "onActivityResult - Deck browser returned null intent");
				//Make sure we open the database again in onResume()
				//deckSelected = false;
				displayProgressDialogAndLoadDeck();
				return;
			}
			// A deck was picked. Save it in preferences and use it.
			Log.i(TAG, "onActivityResult = OK");
			deckFilename = intent.getExtras().getString(OPT_DB);
			savePreferences();

        	//Log.i(TAG, "onActivityResult - deckSelected = " + deckSelected);
			boolean updateAllCards = (requestCode == DOWNLOAD_SHARED_DECK);
			displayProgressDialogAndLoadDeck(updateAllCards);
			
		} else if (requestCode == PREFERENCES_UPDATE)
		{
			restorePreferences();
			showContentView();
			//If there is no deck loaded the controls have not to be shown
//			if(deckLoaded && cardsToReview)
//			{
//				showOrHideControls();
//				showOrHideAnswerField();
//			}
		} else if (requestCode == REQUEST_REVIEW)
		{
			switch (resultCode)
			{
			case Reviewer.RESULT_SESSION_COMPLETED:
				showContentView(CONTENT_SESSION_COMPLETE);
				break;
			case Reviewer.RESULT_NO_MORE_CARDS:
				showContentView(CONTENT_CONGRATS);
				break;
			default:
				showContentView(CONTENT_STUDY_OPTIONS);
				break;
			}
		}
	}
	
	private void savePreferences()
	{
		SharedPreferences preferences = PrefSettings.getSharedPrefs(getBaseContext());
		Editor editor = preferences.edit();
		editor.putString("deckFilename", deckFilename);
		editor.commit();
	}
	
	private SharedPreferences restorePreferences()
	{
		SharedPreferences preferences = PrefSettings.getSharedPrefs(getBaseContext());
		prefDeckPath = preferences.getString("deckPath", "/sdcard");
		prefStudyOptions = preferences.getBoolean("study_options", true);
		return preferences;
	}
	
	private void displayProgressDialogAndLoadDeck()
	{
		displayProgressDialogAndLoadDeck(false);
	}
	
	private void displayProgressDialogAndLoadDeck(boolean updateAllCards)
	{
		Log.i(TAG, "displayProgressDialogAndLoadDeck - Loading deck " + deckFilename);

		// Don't open database again in onResume() until we know for sure this attempt to load the deck is finished
		//deckSelected = true;

//		if(isSdCardMounted())
//		{
			if (deckFilename != null && new File(deckFilename).exists())
			{
				//showControls(false);
				
				if(updateAllCards)
				{
					DeckTask.launchDeckTask(
							DeckTask.TASK_TYPE_LOAD_DECK_AND_UPDATE_CARDS,
							mLoadDeckHandler,
							new DeckTask.TaskData(deckFilename));
				}
				else
				{
					DeckTask.launchDeckTask(
							DeckTask.TASK_TYPE_LOAD_DECK,
							mLoadDeckHandler,
							new DeckTask.TaskData(deckFilename));
				}
			}
			else
			{
				if(deckFilename == null) Log.i(TAG, "displayProgressDialogAndLoadDeck - SD card unmounted.");
				else if(!new File(deckFilename).exists()) Log.i(TAG, "displayProgressDialogAndLoadDeck - The deck " + deckFilename + "does not exist.");

				//Show message informing that no deck has been loaded
				//displayDeckNotLoaded();
			}
//		} else
//		{
//			Log.i(TAG, "displayProgressDialogAndLoadDeck - SD card unmounted.");
//			deckSelected = false;
//        	Log.i(TAG, "displayProgressDialogAndLoadDeck - deckSelected = " + deckSelected);
//			displaySdError();
//		}

	}
	
	DeckTask.TaskListener mLoadDeckHandler = new DeckTask.TaskListener()
	{

		public void onPreExecute() {
//			if(updateDialog == null || !updateDialog.isShowing())
//			{
				mProgressDialog = ProgressDialog.show(StudyOptions.this, "", "Loading deck. Please wait...", true);
//			}
		}

		public void onPostExecute(DeckTask.TaskData result) {

			// Close the previously opened deck.
//			if (AnkidroidApp.deck() != null)
//			{
//				AnkidroidApp.deck().closeDeck();
//				AnkidroidApp.setDeck(null);
//			}
			
			switch(result.getInt())
			{
				case AnkiDroid.DECK_LOADED:
					// Set the deck in the application instance, so other activities
					// can access the loaded deck.
				    AnkiDroidApp.setDeck( result.getDeck() );
				    if(prefStudyOptions)
				    {
				    	showContentView(CONTENT_STUDY_OPTIONS);
				    }
				    else
				    {
				    	startActivityForResult(
								new Intent(StudyOptions.this, Reviewer.class),
								REQUEST_REVIEW
								);
				    }
				    
					break;

				case AnkiDroid.DECK_NOT_LOADED:
					showContentView(CONTENT_DECK_NOT_LOADED);
					break;

				case AnkiDroid.DECK_EMPTY:
					//displayNoCardsInDeck();
					break;
			}
			
			// This verification would not be necessary if onConfigurationChanged it's executed correctly (which seems that emulator does not do)
			if(mProgressDialog.isShowing())
			{
				try
				{
					mProgressDialog.dismiss();
				} catch(Exception e)
				{
					Log.e(TAG, "onPostExecute - Dialog dismiss Exception = " + e.getMessage());
				}
			}
		}

		public void onProgressUpdate(DeckTask.TaskData... values) {
			// Pass
		}
	};
	
	Connection.TaskListener getSharedDecksListener = new Connection.TaskListener() {

		@Override
		public void onDisconnected() {
			noConnectionAlert.show();
		}

		@Override
		public void onPostExecute(Payload data) {
			progressDialog.dismiss();
			if(data.success)
			{
				openSharedDeckPicker();
			}
			else
			{
				connectionFailedAlert.show();
			}
		}

		@Override
		public void onPreExecute() {
			progressDialog = ProgressDialog.show(StudyOptions.this, "", getResources().getString(R.string.loading_shared_decks));
		}

		@Override
		public void onProgressUpdate(Object... values) {
			//Pass
		}
		
	};
	
	Connection.TaskListener syncListener = new Connection.TaskListener() {

		@Override
		public void onDisconnected() {
			noConnectionAlert.show();			
		}

		@Override
		public void onPostExecute(Payload data) {
			progressDialog.dismiss();
			Log.i(TAG, "onPostExecute");
			//closeDeck();
			if(AnkiDroidApp.deck() != null )//&& sdCardAvailable)
			{
				AnkiDroidApp.deck().closeDeck();
				AnkiDroidApp.setDeck(null);
			}
			
			DeckTask.launchDeckTask(
					DeckTask.TASK_TYPE_LOAD_DECK,
					mLoadDeckHandler,
					new DeckTask.TaskData(deckFilename));
		}

		@Override
		public void onPreExecute() {
			progressDialog = ProgressDialog.show(StudyOptions.this, "", getResources().getString(R.string.loading_shared_decks));
		}

		@Override
		public void onProgressUpdate(Object... values) {
			// TODO Auto-generated method stub
		}
		
	};
	
}
