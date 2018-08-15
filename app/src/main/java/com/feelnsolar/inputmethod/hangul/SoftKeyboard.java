/*
 * Copyright (C) 2008-2009 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.feelnsolar.inputmethod.hangul;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.media.AudioManager;
import android.os.Build;
import android.os.Debug;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.text.AutoText;
import android.text.ClipboardManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.PrintWriterPrinter;
import android.util.Printer;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

import com.android.inputmethod.latin.UserDictionary;

/**
 * Input method implementation for Qwerty'ish keyboard.
 */

@SuppressLint("NewApi")
public class SoftKeyboard extends InputMethodService
        implements KeyboardView.OnKeyboardActionListener {
    public static final boolean DEBUG = true;
    static final boolean TRACE = false;
    public static final String PRJ_NAME = "HangulKeyboard";
    public static final String DEF_CHARSET = "UTF-8";
    
    private static final String PREF_VIBRATE_ON = "vibrate_on";
    private static final String PREF_SOUND_ON = "sound_on";
    private static final String PREF_AUTO_CAP = "auto_cap";
    private static final String PREF_QUICK_FIXES = "quick_fixes";
    private static final String PREF_SHOW_SUGGESTIONS = "show_suggestions";
    private static final String PREF_AUTO_COMPLETE = "auto_complete";
    private static final String PREF_SELECT_SKIN = "select_skin";

    private static final int MSG_UPDATE_SUGGESTIONS = 0;
    private static final int MSG_START_TUTORIAL = 1;
    
    // How many continuous deletes at which to start deleting at a higher speed.
    private static final int DELETE_ACCELERATE_AT = 20;
    // Key events coming any faster than this are long-presses.
    private static final int QUICK_PRESS = 200; 
    
    private static final int KEYCODE_ENTER = 10;
    private static final int KEYCODE_SPACE = ' ';

    // Contextual menu positions
    private static final int POS_SETTINGS = 0;
    private static final int POS_METHOD = 1;
    
    private LatinKeyboardView mInputView;
    private List<LatinKeyboardView> mInputViewList;
    private CandidateViewContainer mCandidateViewContainer;
    private CandidateView mCandidateView;
    private CompletionInfo[] mCompletions;
    
    private AlertDialog mOptionsDialog;
    KeyboardSwitcher mKeyboardSwitcher;
    private UserDictionary mUserDictionary;
    private String mLocale;

    private StringBuilder mComposing = new StringBuilder();
    //private HangulBuilder mComposing = new HangulBuilder();
    
    private WordComposer mWord = new WordComposer();
    private int mCommittedLength;
    private boolean mPredicting;
    private CharSequence mBestWord;
    private String mSkin;
    private boolean mPredictionOn;
    private boolean mCompletionOn;
    private boolean mAutoSpace;
    private boolean mAutoCorrectOn;
    private boolean mCapsLock;
    private boolean mVibrateOn;
    private boolean mSoundOn;
    private boolean mAutoCap;
    private boolean mQuickFixes;
    private boolean mShowSuggestions;
    private boolean mAutoComplete;
    private int     mCorrectionMode;
    // Indicates whether the suggestion strip is to be on in landscape
    private boolean mJustAccepted;
    private CharSequence mJustRevertedSeparator;
    private int mDeleteCount;
    private long mLastKeyTime;
    
    private Tutorial mTutorial;

    private Vibrator mVibrator;
    private long mVibrateDuration;

    private AudioManager mAudioManager;
    private final float FX_VOLUME = 1.0f;
    private boolean mSilentMode;

    private String mWordSeparators;
    private String mSentenceSeparators;
    
    HangulAutomata mHangulAutomata = new HangulAutomata();
    
    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) { 
            switch (msg.what) {
                case MSG_UPDATE_SUGGESTIONS:
                    updateSuggestions();
                    break;
                case MSG_START_TUTORIAL:
                    if (mTutorial == null) {
                        if (mInputView.isShown()) {
                            mTutorial = new Tutorial(SoftKeyboard.this, mInputView);
                            mTutorial.start();
                        } else {
                            // Try again soon if the view is not yet showing
                            sendMessageDelayed(obtainMessage(MSG_START_TUTORIAL), 100);
                        }
                    }
                    break;
            }
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        //setStatusIcon(R.drawable.ime_qwerty);
        mKeyboardSwitcher = new KeyboardSwitcher(this);
        initSuggest(getResources().getConfiguration().locale.toString());
        
        mVibrateDuration = getResources().getInteger(R.integer.vibrate_duration_ms);
        
        // register to receive ringer mode changes for silent mode
        IntentFilter filter = new IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION);
        registerReceiver(mReceiver, filter);
        
        mInputViewList = new ArrayList<LatinKeyboardView>(2);
/*
        int i;
        Log.v(PRJ_NAME, "??초성");
        for(i = 0x1113; i < 0x1159; i++)
        	printCode(i);
        Log.v(PRJ_NAME, "??중성");
        for(i = 0x1176; i < 0x11a2; i++)
        	printCode(i);
        Log.v(PRJ_NAME, "중성 글??채�?");
        printCode(0x1160);
        printCode(0x318d);
        Log.v(PRJ_NAME, "??종성");
        for(i = 0x11c3; i < 0x11f9; i++)
        	printCode(i);
*/
    }
/*
    private void printCode(int c)
    {
    	String str = new String();
    	str += (char)c;
    	Log.v(PRJ_NAME, encodingStr(str));
    }
*/
    private void initSuggest(String locale) {
        mLocale = locale;


        mUserDictionary = new UserDictionary(this);

        mWordSeparators = getResources().getString(R.string.word_separators);
        mSentenceSeparators = getResources().getString(R.string.sentence_separators);
    }
    
    @Override public void onDestroy() {
        mUserDictionary.close();
        unregisterReceiver(mReceiver);
        mInputViewList.clear();
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration conf) {
        if (!TextUtils.equals(conf.locale.toString(), mLocale)) {
            initSuggest(conf.locale.toString());
        }
        super.onConfigurationChanged(conf);
    }
    
    @Override
    public View onCreateCandidatesView() {
        mKeyboardSwitcher.makeKeyboards();
        mCandidateViewContainer = (CandidateViewContainer) getLayoutInflater().inflate(
                R.layout.candidates, null);
        mCandidateViewContainer.initViews();
        mCandidateView = (CandidateView) mCandidateViewContainer.findViewById(R.id.candidates);
        mCandidateView.setService(this);
        setCandidatesViewShown(true);
        return mCandidateViewContainer;
    }

    private void loadSettings() {
        // Get the settings preferences
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        mVibrateOn = sp.getBoolean(PREF_VIBRATE_ON, false);
        mSoundOn = sp.getBoolean(PREF_SOUND_ON, false);
        mAutoCap = sp.getBoolean(PREF_AUTO_CAP, true);
        mQuickFixes = sp.getBoolean(PREF_QUICK_FIXES, true);
        
        String[] strdef = getResources().getStringArray(R.array.pref_skin_values);
        //String strval = sp.getString(PREF_SELECT_SKIN, strdef[0]);
        //mUseSkin = (0 != strval.compareTo(strdef[0]));
        mSkin = sp.getString(PREF_SELECT_SKIN, strdef[0]);
        
        // If there is no auto text data, then quickfix is forced to "on", so that the other options
        // will continue to work
        if (AutoText.getSize(mInputView) < 1) mQuickFixes = true;
        mShowSuggestions = sp.getBoolean(PREF_SHOW_SUGGESTIONS, true) & mQuickFixes;
        mAutoComplete = sp.getBoolean(PREF_AUTO_COMPLETE, true) & mShowSuggestions;
        mCorrectionMode = mAutoComplete ? 2 : (mQuickFixes ? 1 : 0);
    }

    @Override
    public View onCreateInputView() {
    	LatinKeyboardView view;
    	String[] strValues = getResources().getStringArray(R.array.pref_skin_values);
    	int[] resId = { R.layout.input, R.layout.custom_input };
    	for(int i = 0; i < strValues.length; i++)
    	{
    		view = (LatinKeyboardView) getLayoutInflater().inflate(resId[i], null);
    		view.setSkin(strValues[i]);
    		view.setOnKeyboardActionListener(this);
    		mInputViewList.add(view);
    	}
    	
    	mInputView = mInputViewList.get(0);
        mKeyboardSwitcher.setInputView(mInputView);
        mKeyboardSwitcher.makeKeyboards();
        mKeyboardSwitcher.setKeyboardMode(KeyboardSwitcher.MODE_TEXT_HANGUL, 0);
        return mInputView;
    }

    @Override 
    public void onStartInputView(EditorInfo attribute, boolean restarting) {
        // In landscape mode, this method gets called without the input view being created.
    	if (mInputView == null)
    		return;
        mInputView.closing();
        
    	loadSettings();
    	if(0 != mSkin.compareTo(mInputView.getSkin()))
    	{
    		LatinKeyboardView view;
    		for(int i = 0; i < mInputViewList.size(); i++)
    		{
    			view = mInputViewList.get(i);
    			if(0 == mSkin.compareTo(view.getSkin()))
    				mInputView = view;
    		}
    		mKeyboardSwitcher.setInputView(mInputView);
            setInputView(mInputView);
        }
        mKeyboardSwitcher.makeKeyboards();
        TextEntryState.newSession(this);
        
        mPredictionOn = false;
        mCompletionOn = false;
        mCompletions = null;
        mCapsLock = false;
 
        switch (attribute.inputType&EditorInfo.TYPE_MASK_CLASS) {
            case EditorInfo.TYPE_CLASS_NUMBER:
            case EditorInfo.TYPE_CLASS_DATETIME:
                mKeyboardSwitcher.setKeyboardMode(KeyboardSwitcher.MODE_TEXT_ENG,
                        attribute.imeOptions);
                mKeyboardSwitcher.toggleSymbols();
                break;
            case EditorInfo.TYPE_CLASS_PHONE:
                mKeyboardSwitcher.setKeyboardMode(KeyboardSwitcher.MODE_PHONE,
                        attribute.imeOptions);
                break;
            case EditorInfo.TYPE_CLASS_TEXT:
                mKeyboardSwitcher.setKeyboardMode(KeyboardSwitcher.MODE_TEXT_HANGUL,
                        attribute.imeOptions);
                //startPrediction();
                mPredictionOn = true;
                // Make sure that passwords are not displayed in candidate view
                int variation = attribute.inputType &  EditorInfo.TYPE_MASK_VARIATION;
                if (variation == EditorInfo.TYPE_TEXT_VARIATION_PASSWORD ||
                        variation == EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ) {
                    mPredictionOn = false;
                }
                if (variation == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                        || variation == EditorInfo.TYPE_TEXT_VARIATION_PERSON_NAME) {
                    mAutoSpace = false;
                } else {
                    mAutoSpace = true;
                }
                if (variation == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS) {
                    mPredictionOn = false;
                    mKeyboardSwitcher.setKeyboardMode(KeyboardSwitcher.MODE_EMAIL,
                            attribute.imeOptions);
                } else if (variation == EditorInfo.TYPE_TEXT_VARIATION_URI) {
                    mPredictionOn = false;
                    mKeyboardSwitcher.setKeyboardMode(KeyboardSwitcher.MODE_URL,
                            attribute.imeOptions);
                } else if (variation == EditorInfo.TYPE_TEXT_VARIATION_SHORT_MESSAGE) {
                    mKeyboardSwitcher.setKeyboardMode(KeyboardSwitcher.MODE_IM,
                            attribute.imeOptions);
                } else if (variation == EditorInfo.TYPE_TEXT_VARIATION_FILTER) {
                    mPredictionOn = false;
                }
                if ((attribute.inputType&EditorInfo.TYPE_TEXT_FLAG_AUTO_COMPLETE) != 0) {
                    mPredictionOn = false;
                    mCompletionOn = true && isFullscreenMode();
                }
                updateShiftKeyState(attribute);
                break;
            default:
                mKeyboardSwitcher.setKeyboardMode(KeyboardSwitcher.MODE_TEXT_HANGUL,
                        attribute.imeOptions);
                updateShiftKeyState(attribute);
        }
       
        mComposing.setLength(0);
        mPredicting = false;
        mDeleteCount = 0;
        setCandidatesViewShown(false);
        if (mCandidateView != null) mCandidateView.setSuggestions(null, false, false, false);
        mInputView.setProximityCorrectionEnabled(true);

        mPredictionOn = mPredictionOn && mCorrectionMode > 0;
        checkTutorial(attribute.privateImeOptions);
        if (TRACE) Debug.startMethodTracing("latinime");
        
    }

    @Override
    public void onFinishInput() {
        super.onFinishInput();

        if (mInputView != null) {
            mInputView.closing();
        }
    }

    @Override
    public void hideWindow() {
        if (TRACE) Debug.stopMethodTracing();
        if (mOptionsDialog != null && mOptionsDialog.isShowing()) {
            mOptionsDialog.dismiss();
            mOptionsDialog = null;
        }
        if (mTutorial != null) {
            mTutorial.close();
            mTutorial = null;
        }

        super.hideWindow();
        
        mHangulAutomata.reset();
        TextEntryState.endSession();
    }

    @Override
    public void onDisplayCompletions(CompletionInfo[] completions) {
        if (false) {
            Log.i("foo", "Received completions:");
            for (int i=0; i<(completions != null ? completions.length : 0); i++) {
                Log.i("foo", "  #" + i + ": " + completions[i]);
            }
        }
        if (mCompletionOn) {
            mCompletions = completions;
            if (completions == null) {
                mCandidateView.setSuggestions(null, false, false, false);
                return;
            }
            
            List<CharSequence> stringList = new ArrayList<CharSequence>();
            for (int i=0; i<(completions != null ? completions.length : 0); i++) {
                CompletionInfo ci = completions[i];
                if (ci != null) stringList.add(ci.getText());
            }
            //CharSequence typedWord = mWord.getTypedWord();
            mCandidateView.setSuggestions(stringList, true, true, true);
            mBestWord = null;
            setCandidatesViewShown(isCandidateStripVisible() || mCompletionOn);
        }
    }

    @Override
    public void setCandidatesViewShown(boolean shown) {
        // TODO: Remove this if we support candidates with hard keyboard
        if (onEvaluateInputViewShown()) {
            super.setCandidatesViewShown(shown);
        }
    }
    
    @Override
    public void onComputeInsets(InputMethodService.Insets outInsets) {
        super.onComputeInsets(outInsets);
        if (!isFullscreenMode()) {
            outInsets.contentTopInsets = outInsets.visibleTopInsets;
        }
    }
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                if (event.getRepeatCount() == 0 && mInputView != null) {
                    if (mInputView.handleBack()) {
                        return true;
                    } else if (mTutorial != null) {
                        mTutorial.close();
                        mTutorial = null;
                    }
                }
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                // If tutorial is visible, don't allow dpad to work
                if (mTutorial != null) {
                    return true;
                }
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                // If tutorial is visible, don't allow dpad to work
                if (mTutorial != null) {
                    return true;
                }
                // Enable shift key and DPAD to do selections
                if (mInputView != null && mInputView.isShown() && mInputView.isShifted()) {
                    event = new KeyEvent(event.getDownTime(), event.getEventTime(), 
                            event.getAction(), event.getKeyCode(), event.getRepeatCount(),
                            event.getDeviceId(), event.getScanCode(),
                            KeyEvent.META_SHIFT_LEFT_ON | KeyEvent.META_SHIFT_ON);
                    InputConnection ic = getCurrentInputConnection();
                    if (ic != null) ic.sendKeyEvent(event);
                    return true;
                }
                break;
        }
        return super.onKeyUp(keyCode, event);
    }

    private void commitTyped(InputConnection inputConnection) {
        if (mPredicting) {
            mPredicting = false;
            if (mComposing.length() > 0) {
                if (inputConnection != null) {
                    inputConnection.commitText(mComposing, 1);
                }
                mCommittedLength = mComposing.length();
                TextEntryState.acceptedTyped(mComposing);
            }
            updateSuggestions();
        }
    }

    private boolean isHangulMode()
    {
    	if(mKeyboardSwitcher == null)
    		return false;
    	return mKeyboardSwitcher.isHangulMode();
    }
    
    public void updateHangulShiftKeyState(EditorInfo attr) {
    	InputConnection ic = getCurrentInputConnection();
        if (attr != null && mInputView != null && isHangulMode()&& ic != null) {
            int caps = 0;
            EditorInfo ei = getCurrentInputEditorInfo();
            if (mAutoCap && ei != null && ei.inputType != EditorInfo.TYPE_NULL) {
                caps = ic.getCursorCapsMode(attr.inputType);
            }
            mKeyboardSwitcher.setHangul(mCapsLock || caps != 0);
        }
    }

    public void updateShiftKeyState(EditorInfo attr) {
    	InputConnection ic = getCurrentInputConnection();
        if (attr != null && mInputView != null && mKeyboardSwitcher.isAlphabetMode()
                && ic != null) {
            int caps = 0;
            EditorInfo ei = getCurrentInputEditorInfo();
            if (isHangulMode()) {
            	mInputView.setShifted(mCapsLock);
            } else {
	            if (mAutoCap && ei != null && ei.inputType != EditorInfo.TYPE_NULL) {
	                caps = ic.getCursorCapsMode(attr.inputType);
	            }
	            mInputView.setShifted(mCapsLock || caps != 0);
            }
        }
    }
    
    private void swapPunctuationAndSpace() {
        final InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        CharSequence lastTwo = ic.getTextBeforeCursor(2, 0);
        if (lastTwo != null && lastTwo.length() == 2
                && lastTwo.charAt(0) == KEYCODE_SPACE && isSentenceSeparator(lastTwo.charAt(1))) {
            ic.beginBatchEdit();
            ic.deleteSurroundingText(2, 0);
            ic.commitText(lastTwo.charAt(1) + " ", 1);
            ic.endBatchEdit();
            updateShiftKeyState(getCurrentInputEditorInfo());
        }
    }
    
    private void doubleSpace() {
        //if (!mAutoPunctuate) return;
        if (mCorrectionMode == Suggest.CORRECTION_NONE) return;
        final InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        CharSequence lastThree = ic.getTextBeforeCursor(3, 0);
        if (lastThree != null && lastThree.length() == 3
                && Character.isLetterOrDigit(lastThree.charAt(0))
                && lastThree.charAt(1) == KEYCODE_SPACE && lastThree.charAt(2) == KEYCODE_SPACE) {
            ic.beginBatchEdit();
            ic.deleteSurroundingText(2, 0);
            ic.commitText(". ", 1);
            ic.endBatchEdit();
            updateShiftKeyState(getCurrentInputEditorInfo());
        }
    }
    
    public boolean addWordToDictionary(String word) {
        mUserDictionary.addWord(word, 128);
        return true;
    }

    private boolean isAlphabet(int code) {
        if (Character.isLetter(code)) {
            return true;
        } else {
            return false;
        }
    }
    
    // Implementation of KeyboardViewListener

    public void onKey(int primaryCode, int[] keyCodes) {
        long when = SystemClock.uptimeMillis();
        if (primaryCode != Keyboard.KEYCODE_DELETE || 
                when > mLastKeyTime + QUICK_PRESS) {
            mDeleteCount = 0;
        }
        mLastKeyTime = when;
        switch (primaryCode) {
            case Keyboard.KEYCODE_DELETE:
        		handleBackspace();
        		mDeleteCount++;
                break;
            case Keyboard.KEYCODE_SHIFT:
                handleShift();
                break;
            case Keyboard.KEYCODE_CANCEL:
                if (mOptionsDialog == null || !mOptionsDialog.isShowing()) {
                    handleClose();
                }
                break;
            case LatinKeyboardView.KEYCODE_OPTIONS:
                showOptionsMenu();
                break;
            case LatinKeyboardView.KEYCODE_SHIFT_LONGPRESS:
                if (mCapsLock) {
                    handleShift();
                } else {
                    toggleCapsLock();
                }
                break;
            case Keyboard.KEYCODE_MODE_CHANGE:
                changeKeyboardMode();
                break;
            default:
                if (isWordSeparator(primaryCode)) {
                    handleSeparator(primaryCode);
                } else {
                	handleCharacter(primaryCode, keyCodes);
                }
                // Cancel the just reverted state
                mJustRevertedSeparator = null;
        }
    }
    
    public void onText(CharSequence text) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.beginBatchEdit();
        if (mPredicting) {
            commitTyped(ic);
        }
        ic.commitText(text, 1);
        ic.endBatchEdit();
        updateShiftKeyState(getCurrentInputEditorInfo());
        mJustRevertedSeparator = null;
    }

    private void handleSeparator(int primaryCode) {
        boolean pickedDefault = false;
        // Handle separator
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.beginBatchEdit();
        }

        if (mPredicting) {
            // In certain languages where single quote is a separator, it's better
            // not to auto correct, but accept the typed word. For instance, 
            // in Italian dov' should not be expanded to dove' because the elision
            // requires the last vowel to be removed.
            if (mAutoCorrectOn && primaryCode != '\'' && 
                    (mJustRevertedSeparator == null 
                            || mJustRevertedSeparator.length() == 0 
                            || mJustRevertedSeparator.charAt(0) != primaryCode)) {
                pickDefaultSuggestion();
                pickedDefault = true;
            } else {
                commitTyped(ic);
            }
        }
        else if(isHangulMode() && mComposing.length() > 0)
    	{
    		if (ic != null)
    			ic.commitText(mComposing, 1);

    		mComposing.setLength(0);
    		mHangulAutomata.reset();
    	}

        sendKeyChar((char)primaryCode);
        TextEntryState.typedCharacter((char) primaryCode, true);
        if (TextEntryState.getState() == TextEntryState.STATE_PUNCTUATION_AFTER_ACCEPTED 
                && primaryCode != KEYCODE_ENTER) {
            swapPunctuationAndSpace();
        } else if (isPredictionOn() && primaryCode == ' ') { 
        //else if (TextEntryState.STATE_SPACE_AFTER_ACCEPTED) {
            doubleSpace();
        }
        if (pickedDefault && mBestWord != null) {
        	String w = HangulAutomata.encode(mWord.getTypedWord().toString());
        	TextEntryState.acceptedDefault(w, mBestWord);
        }
        
        updateShiftKeyState(getCurrentInputEditorInfo());
        if (ic != null) {
            ic.endBatchEdit();
        }
    }
    
    private void handleClose() {
        commitTyped(getCurrentInputConnection());
        requestHideSelf(0);
        mInputView.closing();
        TextEntryState.endSession();
    }

    private void handleShift() {
    	if (mKeyboardSwitcher.isAlphabetMode()) {
            // Alphabet keyboard
            checkToggleCapsLock();
            if(isHangulMode())
            	mKeyboardSwitcher.setHangul(mCapsLock || !mInputView.isShifted());
            mInputView.setShifted(mCapsLock || !mInputView.isShifted());
        } else {
            mKeyboardSwitcher.toggleShift();
        }
    }
    
    private void checkToggleCapsLock() {
        if (mInputView.getKeyboard().isShifted()) {
            toggleCapsLock();
        }
    }

    private void toggleCapsLock() {
    	mCapsLock = !mCapsLock;
        if (mKeyboardSwitcher.isAlphabetMode()) {
        	if(isHangulMode())
            	mKeyboardSwitcher.setHangul(mCapsLock);
            ((LatinKeyboard) mInputView.getKeyboard()).setShiftLocked(mCapsLock);
        }
    }

    private void postUpdateSuggestions() {
        mHandler.removeMessages(MSG_UPDATE_SUGGESTIONS);
        mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_UPDATE_SUGGESTIONS), 100);
    }
    
    private boolean isPredictionOn() {
        boolean predictionOn = mPredictionOn;
        //if (isFullscreenMode()) predictionOn &= mPredictionLandscape;
        return predictionOn;
    }
    
    private boolean isCandidateStripVisible() {
        return isPredictionOn() && mShowSuggestions;
    }

    private void updateSuggestions() {
        if (!mPredicting) {
            mCandidateView.setSuggestions(null, false, false, false);
            return;
        }




        //|| mCorrectionMode == mSuggest.CORRECTION_FULL;
        CharSequence typedWord = mWord.getTypedWord();
        // If we're in basic correct


        setCandidatesViewShown(isCandidateStripVisible() || mCompletionOn);
    }

    private void pickDefaultSuggestion() {
        // Complete any pending candidate query first
        if (mHandler.hasMessages(MSG_UPDATE_SUGGESTIONS)) {
            mHandler.removeMessages(MSG_UPDATE_SUGGESTIONS);
            updateSuggestions();
        }
        if (mBestWord != null) {
        	String w = HangulAutomata.encode(mWord.getTypedWord().toString());
        	TextEntryState.acceptedDefault(w, mBestWord);
        	
            mJustAccepted = true;
            pickSuggestion(mBestWord);
        }
    }

    public void pickSuggestionManually(int index, CharSequence suggestion) {
        if (mCompletionOn && mCompletions != null && index >= 0
                && index < mCompletions.length) {
            CompletionInfo ci = mCompletions[index];
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                ic.commitCompletion(ci);
            }
            mCommittedLength = suggestion.length();
            if (mCandidateView != null) {
                mCandidateView.clear();
            }
            updateShiftKeyState(getCurrentInputEditorInfo());
            return;
        }
        pickSuggestion(suggestion);
        TextEntryState.acceptedSuggestion(mComposing.toString(), suggestion);
        
        // Follow it with a space
        if (mAutoSpace) {
            sendSpace();
        }
        // Fool the state watcher so that a subsequent backspace will not do a revert
        TextEntryState.typedCharacter((char) KEYCODE_SPACE, true);
    }
    
    private void pickSuggestion(CharSequence suggestion) {
    	if (mCapsLock) {
            suggestion = suggestion.toString().toUpperCase();
        } else if (preferCapitalization() 
                || (mKeyboardSwitcher.isAlphabetMode() && mInputView.isShifted())) {
            suggestion = Character.toUpperCase(suggestion.charAt(0)) 
                    + suggestion.subSequence(1, suggestion.length()).toString();
        }
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
        	ic.commitText(suggestion, 1);
        }
        
        mPredicting = false;

        mComposing.setLength(0);
        mHangulAutomata.reset();

        mCommittedLength = suggestion.length();
        if (mCandidateView != null) {
            mCandidateView.setSuggestions(null, false, false, false);
        }
        updateShiftKeyState(getCurrentInputEditorInfo());
    }

    private boolean isCursorTouchingWord() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return false;
        CharSequence toLeft = ic.getTextBeforeCursor(1, 0);
        CharSequence toRight = ic.getTextAfterCursor(1, 0);
        if (!TextUtils.isEmpty(toLeft)
                && !isWordSeparator(toLeft.charAt(0))) {
            return true;
        }
        if (!TextUtils.isEmpty(toRight) 
                && !isWordSeparator(toRight.charAt(0))) {
            return true;
        }
        return false;
    }
    
    public void revertLastWord(boolean deleteChar) {
        final int length = mComposing.length();
        if (!mPredicting && length > 0) {
            final InputConnection ic = getCurrentInputConnection();
            mPredicting = true;
            ic.beginBatchEdit();
            mJustRevertedSeparator = ic.getTextBeforeCursor(1, 0);
            if (deleteChar) ic.deleteSurroundingText(1, 0);
            int toDelete = mCommittedLength;
            CharSequence toTheLeft = ic.getTextBeforeCursor(mCommittedLength, 0);
            if (toTheLeft != null && toTheLeft.length() > 0 
                    && isWordSeparator(toTheLeft.charAt(0))) {
                toDelete--;
            }
            ic.deleteSurroundingText(toDelete, 0);
            ic.setComposingText(mComposing, 1);
            TextEntryState.backspace();
            ic.endBatchEdit();
            postUpdateSuggestions();
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
            mJustRevertedSeparator = null;
        }
    }

    protected String getWordSeparators() {
        return mWordSeparators;
    }
    
    public boolean isWordSeparator(int code) {
        String separators = getWordSeparators();
        return separators.contains(String.valueOf((char)code));
    }

    public boolean isSentenceSeparator(int code) {
        return mSentenceSeparators.contains(String.valueOf((char)code));
    }

    private void sendSpace() {
        sendKeyChar((char)KEYCODE_SPACE);
        updateShiftKeyState(getCurrentInputEditorInfo());
        //onKey(KEY_SPACE[0], KEY_SPACE);
    }

    public boolean preferCapitalization() {
        return mWord.isCapitalized();
    }

    public void swipeRight() {
        if (LatinKeyboardView.DEBUG_AUTO_PLAY) {
            ClipboardManager cm = ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE));
            CharSequence text = cm.getText();
            if (!TextUtils.isEmpty(text)) {
                mInputView.startPlaying(text.toString());
            }
        }
    }
    
    public void swipeLeft() {
        //handleBackspace();
    }

    public void swipeDown() {
        //handleClose();
    }

    public void swipeUp() {
        //launchSettings();
    }

    public void onPress(int primaryCode) {
        vibrate();
        playKeyClick(primaryCode);
    }

    public void onRelease(int primaryCode) {
        //vibrate();
    }

    // receive ringer mode changes to detect silent mode
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateRingerMode();
        }
    };

    // update flags for silent mode
    private void updateRingerMode() {
        if (mAudioManager == null) {
            mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        }
        if (mAudioManager != null) {
            mSilentMode = (mAudioManager.getRingerMode() != AudioManager.RINGER_MODE_NORMAL);
        }
    }

    private void playKeyClick(int primaryCode) {
        // if mAudioManager is null, we don't have the ringer state yet
        // mAudioManager will be set by updateRingerMode
        if (mAudioManager == null) {
            if (mInputView != null) {
                updateRingerMode();
            }
        }
        if (mSoundOn && !mSilentMode) {
            // FIXME: Volume and enable should come from UI settings
            // FIXME: These should be triggered after auto-repeat logic
            int sound = AudioManager.FX_KEYPRESS_STANDARD;
            switch (primaryCode) {
                case Keyboard.KEYCODE_DELETE:
                    sound = AudioManager.FX_KEYPRESS_DELETE;
                    break;
                case KEYCODE_ENTER:
                    sound = AudioManager.FX_KEYPRESS_RETURN;
                    break;
                case KEYCODE_SPACE:
                    sound = AudioManager.FX_KEYPRESS_SPACEBAR;
                    break;
            }
            mAudioManager.playSoundEffect(sound, FX_VOLUME);
        }
    }

    private void vibrate() {
        if (!mVibrateOn) {
            return;
        }
        if (mVibrator == null) {
            //mVibrator = new Vibrator();
        	mVibrator = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
        }
        mVibrator.vibrate(mVibrateDuration);
    }

    private void checkTutorial(String privateImeOptions) {
        if (privateImeOptions == null) return;
        if (privateImeOptions.equals("com.android.setupwizard:ShowTutorial")) {
            if (mTutorial == null) startTutorial();
        } else if (privateImeOptions.equals("com.android.setupwizard:HideTutorial")) {
            if (mTutorial != null) {
                if (mTutorial.close()) {
                    mTutorial = null;
                }
            }
        }
    }
    
    private void startTutorial() {
        mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_START_TUTORIAL), 500);
    }

    void tutorialDone() {
        mTutorial = null;
    }
    
    private void launchSettings() {
        handleClose();
        Intent intent = new Intent();
        intent.setClass(SoftKeyboard.this, KeyboardSettings.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void showOptionsMenu() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(true);
        builder.setIcon(R.drawable.ic_dialog_keyboard);
        builder.setNegativeButton(android.R.string.cancel, null);
        CharSequence itemSettings = getString(R.string.english_ime_settings);
        //CharSequence itemInputMethod = getString(android.internal.R.string.inputMethod);
        CharSequence itemInputMethod = getString(R.string.inputMethod);

        builder.setItems(new CharSequence[] {
                itemSettings, itemInputMethod},
                new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface di, int position) {
                di.dismiss();
                switch (position) {
                    case POS_SETTINGS:
                        launchSettings();
                        break;
                    case POS_METHOD:
                    	((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showInputMethodPicker();
                        break;
                }
            }
        });
        builder.setTitle(getResources().getString(R.string.english_ime_name));
        mOptionsDialog = builder.create();
        Window window = mOptionsDialog.getWindow();
        WindowManager.LayoutParams lp = window.getAttributes();
        lp.token = mInputView.getWindowToken();
        lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
        window.setAttributes(lp);
        window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        mOptionsDialog.show();
    }

    private void changeKeyboardMode() {
    	mKeyboardSwitcher.toggleSymbols();
    	if(isHangulMode())
    		mKeyboardSwitcher.setHangul(mCapsLock);
    	if (mCapsLock && mKeyboardSwitcher.isAlphabetMode()) {
    		((LatinKeyboard) mInputView.getKeyboard()).setShiftLocked(mCapsLock);
    	}

    	updateShiftKeyState(getCurrentInputEditorInfo());
    }
    
    @Override protected void dump(FileDescriptor fd, PrintWriter fout, String[] args) {
        super.dump(fd, fout, args);
        
        final Printer p = new PrintWriterPrinter(fout);
        p.println("LatinIME state :");
        p.println("  Keyboard mode = " + mKeyboardSwitcher.getKeyboardMode());
        p.println("  mCapsLock=" + mCapsLock);
        p.println("  mComposing=" + mComposing.toString());
        p.println("  mPredictionOn=" + mPredictionOn);
        p.println("  mCorrectionMode=" + mCorrectionMode);
        p.println("  mPredicting=" + mPredicting);
        p.println("  mAutoCorrectOn=" + mAutoCorrectOn);
        p.println("  mAutoSpace=" + mAutoSpace);
        p.println("  mCompletionOn=" + mCompletionOn);
        p.println("  TextEntryState.state=" + TextEntryState.getState());
        p.println("  mSoundOn=" + mSoundOn);
        p.println("  mVibrateOn=" + mVibrateOn);
    }

    // Characters per second measurement
    
    private static final boolean PERF_DEBUG = false;
    private long mLastCpsTime;
    private static final int CPS_BUFFER_SIZE = 16;
    private long[] mCpsIntervals = new long[CPS_BUFFER_SIZE];
    private int mCpsIndex;
    
    private void measureCps() {
        if (!SoftKeyboard.PERF_DEBUG) return;
        long now = System.currentTimeMillis();
        if (mLastCpsTime == 0) mLastCpsTime = now - 100; // Initial
        mCpsIntervals[mCpsIndex] = now - mLastCpsTime;
        mLastCpsTime = now;
        mCpsIndex = (mCpsIndex + 1) % CPS_BUFFER_SIZE;
        long total = 0;
        for (int i = 0; i < CPS_BUFFER_SIZE; i++) total += mCpsIntervals[i];
        System.out.println("CPS = " + ((CPS_BUFFER_SIZE * 1000f) / total));
    }

    @Override
    public void onUpdateSelection(int oldSelStart, int oldSelEnd,
            int newSelStart, int newSelEnd,
            int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd,
                candidatesStart, candidatesEnd);
        // If the current selection in the text view changes, we should
        // clear whatever candidate text we have.
        final int length = mComposing.length();
        //Log.v(PRJ_NAME, "onUpdateSelection mPredicting=" + mPredicting + ", length=" + length);
        //Log.v(PRJ_NAME, "newSelStart=" + newSelStart + ", newSelEnd=" + newSelEnd + ", candidatesEnd=" + candidatesEnd);
        if (length > 0 && (mPredicting || isHangulMode()) && 
        		(newSelStart != candidatesEnd || newSelEnd != candidatesEnd)) {
        	if(!(isHangulMode() && -1 == candidatesEnd))
        	{
        		mPredicting = false;
        		mComposing.setLength(0);
	        	
				updateSuggestions();
				TextEntryState.reset();
			
				mHangulAutomata.reset();
				InputConnection ic = getCurrentInputConnection();
				if (ic != null)
				    ic.finishComposingText();
        	}
        } else if (!mPredicting && !mJustAccepted
                && TextEntryState.getState() == TextEntryState.STATE_ACCEPTED_DEFAULT) {
            TextEntryState.reset();
        }

        if(!mPredicting)
        {
			if(mShowSuggestions)
				setCandidatesViewShown(false);
        }

        mJustAccepted = false;

    }

    private void printComposing()
    {
	    Log.v(PRJ_NAME, "mComposing= \'" + encodingStr(mComposing.toString()) + "\'");
    }

    private String encodingStr(String str)
    {
    	String ret = null;
    	try
    	{
    		ret = new String(str.getBytes(), DEF_CHARSET);
    	}
    	catch(UnsupportedEncodingException e)
    	{
    		Log.v(PRJ_NAME, "UnsupportedEncodingException");
    	}
    	
    	return ret;
    }

    private void printCodes(String text)
    {
    	for(int i = 0; i < mWord.size(); i++)
    	{
    		int[] codes = mWord.getCodesAt(i);
    		int j;
    		for(j = 0; j < codes.length; j++)
    		{
    			if(-1 == codes[j])
    				break;
    		}
    		String str = new String(codes, 0, j);
    		Log.v(PRJ_NAME, text + " codes[" + i + "]= \"" + encodingStr(str) + "\"");
    		
    	}
    }

    private void handleBackspace() {
        boolean deleteChar = false;
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        final int length = mComposing.length();
        
        
    	if(mPredicting)
    	{
    		if(-1 != mHangulAutomata.getBuffer())
        		mWord.deleteLast();
        	else
        	{
        		char lastChar = mComposing.charAt(length - 1);
        		int count = HangulAutomata.countCharacter(lastChar);
				
				for(int i = 0; i < count; i++)
					mWord.deleteLast();
        	}
    		
    		if(0 < length)
    		{
    			mComposing.delete(length - 1, length);
    			int result = mHangulAutomata.deleteCharacter();
        		
    			if(-1 != result)
    				mComposing.append((char)result);
    			ic.setComposingText(mComposing, 1);
    			if(0 == mComposing.length())
    				mPredicting = false;
    		}
    		else
    			ic.deleteSurroundingText(1, 0);
    	}
    	else if(0 < length/* && -1 != mHangulAutomata.getBuffer()*/)
    	{
			mComposing.delete(length - 1, length);
			int result = mHangulAutomata.deleteCharacter();
    		
			if(-1 != result)
				mComposing.append((char)result);
			ic.setComposingText(mComposing, 1);
    	}
    	else
    		deleteChar = true;
   
		if(DEBUG)
		{
			printComposing();
			printCodes("delete");
		}
 
		updateShiftKeyState(getCurrentInputEditorInfo());
        TextEntryState.backspace();
        if (TextEntryState.getState() == TextEntryState.STATE_UNDO_COMMIT) {
            revertLastWord(deleteChar);
            return;
        } else if (deleteChar) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
            if (mDeleteCount > DELETE_ACCELERATE_AT) {
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
            }
        }
        mJustRevertedSeparator = null;
    }

    private void handleCharacter(int primaryCode, int[] keyCodes) 
    {
        if (isAlphabet(primaryCode) && isPredictionOn() && !isCursorTouchingWord()) 
        {
            if (!mPredicting) 
            {
                mPredicting = true;
                mComposing.setLength(0);
                mWord.reset();
                mHangulAutomata.reset();
            }
        }

        if (mInputView.isShifted())
            primaryCode = Character.toUpperCase(primaryCode);
        
        InputConnection ic = getCurrentInputConnection();
        if(null == ic)
        	return;
        
        int length = mComposing.length();
		if (mPredicting) 
		{
		    if (mInputView.isShifted() && mComposing.length() == 0)
		        mWord.setCapitalized(true);

		    if(-1 != mHangulAutomata.getBuffer() && 0 < length)
		    	mComposing.delete(length - 1, length);
		    
		    mWord.add(primaryCode, keyCodes);
		    int ret[] = mHangulAutomata.appendCharacter(primaryCode);
		    for(int i = 0; i < ret.length; i++)
		    {
		    	if(-1 != ret[i])
		    		mComposing.append((char)ret[i]);
		    }
	        ic.setComposingText(mComposing, 1);
		    postUpdateSuggestions();
		    
		    if(DEBUG)
		    {
			    printComposing();
			    printCodes("insert");
		    }
		} 
		else
		{
			ic.beginBatchEdit();
			 if(-1 != mHangulAutomata.getBuffer() && 0 < length)
			    	mComposing.delete(length - 1, length);
			 
		    int ret[] = mHangulAutomata.appendCharacter(primaryCode);
		    for(int i = 0; i < ret.length - 1; i++)
		    {
		    	if(-1 != ret[i])
		    		mComposing.append((char)ret[i]);
		    }
		    ic.commitText(mComposing, 1);
		    mComposing.setLength(0);
		    if(-1 != ret[2])
		    {
		    	mComposing.append((char)ret[2]);
		    	ic.setComposingText(mComposing, 1);
		    }
		    ic.endBatchEdit();
		}

		updateHangulShiftKeyState(getCurrentInputEditorInfo());
        updateShiftKeyState(getCurrentInputEditorInfo());
        measureCps();
        TextEntryState.typedCharacter((char) primaryCode, isWordSeparator(primaryCode));
    }

}
