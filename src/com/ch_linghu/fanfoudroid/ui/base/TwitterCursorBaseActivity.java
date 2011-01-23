/*
 * Copyright (C) 2009 Google Inc.
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
package com.ch_linghu.fanfoudroid.ui.base;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.drawable.AnimationDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.ch_linghu.fanfoudroid.R;
import com.ch_linghu.fanfoudroid.data.Tweet;
import com.ch_linghu.fanfoudroid.data.db.TwitterDbAdapter;
import com.ch_linghu.fanfoudroid.helper.Preferences;
import com.ch_linghu.fanfoudroid.helper.Utils;
import com.ch_linghu.fanfoudroid.task.GenericTask;
import com.ch_linghu.fanfoudroid.task.TaskListener;
import com.ch_linghu.fanfoudroid.task.TaskParams;
import com.ch_linghu.fanfoudroid.task.TaskResult;
import com.ch_linghu.fanfoudroid.ui.module.TweetAdapter;
import com.ch_linghu.fanfoudroid.ui.module.TweetCursorAdapter;
import com.ch_linghu.fanfoudroid.weibo.IDs;
import com.ch_linghu.fanfoudroid.weibo.Status;
import com.ch_linghu.fanfoudroid.weibo.WeiboException;

/**
 * TwitterCursorBaseLine用于带有静态数据来源（对应数据库的，与twitter表同构的特定表）的展现
 */
public abstract class TwitterCursorBaseActivity extends TwitterListBaseActivity{
	static final String TAG = "TwitterListBaseActivity";

	// Views.
	protected ListView mTweetList;
	protected TweetCursorAdapter mTweetAdapter;
	
	protected TextView loadMoreBtn;
	protected ProgressBar loadMoreGIF;
	// use setOneShot(true) to stop this animation,
	protected AnimationDrawable loadMoreAnimation; 
	
	protected static int lastPosition = 0;

	// Tasks.
	private GenericTask mRetrieveTask;
	private GenericTask mFollowersRetrieveTask;

	// Refresh data at startup if last refresh was this long ago or greater.
	private static final long REFRESH_THRESHOLD = 5 * 60 * 1000;

	// Refresh followers if last refresh was this long ago or greater.
	private static final long FOLLOWERS_REFRESH_THRESHOLD = 12 * 60 * 60 * 1000;

	abstract protected void markAllRead();

	abstract protected Cursor fetchMessages();

	public abstract String fetchMaxId();

	public abstract void addMessages(ArrayList<Tweet> tweets,
			boolean isUnread);

	public abstract List<Status> getMessageSinceId(String maxId)
			throws WeiboException;

	public static final int CONTEXT_REPLY_ID = Menu.FIRST + 1;
	// public static final int CONTEXT_AT_ID = Menu.FIRST + 2;
	public static final int CONTEXT_RETWEET_ID = Menu.FIRST + 3;
	public static final int CONTEXT_DM_ID = Menu.FIRST + 4;
	public static final int CONTEXT_MORE_ID = Menu.FIRST + 5;
	public static final int CONTEXT_ADD_FAV_ID = Menu.FIRST + 6;
	public static final int CONTEXT_DEL_FAV_ID = Menu.FIRST + 7;

	@Override
	protected void setupState() {
		Cursor cursor;

		cursor = fetchMessages(); // getDb().fetchMentions();
		setTitle(getActivityTitle());

		startManagingCursor(cursor);

		mTweetList = (ListView) findViewById(R.id.tweet_list);
		setupListHeader(true);
	    
		mTweetAdapter = new TweetCursorAdapter(this, cursor);
		mTweetList.setAdapter(mTweetAdapter);
		//registerOnClickListener(mTweetList);
	}
	
	/**
	 * 绑定listView底部 - 载入更多
	 * NOTE: 必须在listView#setAdapter之前调用
	 */
	protected void setupListHeader(boolean addFooter) {
        // Add Header to ListView
        View header = View.inflate(this, R.layout.listview_header, null);
        mTweetList.addHeaderView(header, null, false);
        header.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadMoreBtn.setVisibility(View.GONE);
                loadMoreGIF.setVisibility(View.VISIBLE);
                //frameAnimation.setOneShot(true);
                //frameAnimation.stop();
            }
        });
        
        View footer = View.inflate(this, R.layout.listview_footer, null);
        mTweetList.addFooterView(footer, null, false);
        footer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadMoreBtn.setVisibility(View.GONE);
                loadMoreGIF.setVisibility(View.VISIBLE);
                //frameAnimation.setOneShot(true);
                //frameAnimation.stop();
            }
        });
    }
	
	@Override
	protected int getLayoutId(){
		return R.layout.main;
	}

	@Override
	protected ListView getTweetList(){
		return mTweetList;
	}

	@Override
	protected TweetAdapter getTweetAdapter(){
		return mTweetAdapter;
	}

	@Override
	protected boolean useBasicMenu(){
		return true;
	}

	@Override
	protected Tweet getContextItemTweet(int position){
		Cursor cursor = (Cursor) mTweetAdapter.getItem(position);
		if (cursor == null){
			return null;
		}else{
			Tweet tweet = new Tweet();
			tweet.id = cursor.getString(cursor.getColumnIndex(TwitterDbAdapter.KEY_ID));
			tweet.createdAt = Utils.parseDateTimeFromSqlite(cursor.getString(cursor.getColumnIndex(TwitterDbAdapter.KEY_CREATED_AT)));
			tweet.favorited = cursor.getString(cursor.getColumnIndex(TwitterDbAdapter.KEY_FAVORITED));
			tweet.screenName = cursor.getString(cursor.getColumnIndex(TwitterDbAdapter.KEY_USER));
			tweet.userId = cursor.getString(cursor.getColumnIndex(TwitterDbAdapter.KEY_USER_ID));
			tweet.text = cursor.getString(cursor.getColumnIndex(TwitterDbAdapter.KEY_TEXT));
			tweet.source = cursor.getString(cursor.getColumnIndex(TwitterDbAdapter.KEY_SOURCE));
			tweet.profileImageUrl = cursor.getString(cursor.getColumnIndex(TwitterDbAdapter.KEY_PROFILE_IMAGE_URL));
			tweet.inReplyToScreenName = cursor.getString(cursor.getColumnIndex(TwitterDbAdapter.KEY_IN_REPLY_TO_SCREEN_NAME));
			tweet.inReplyToStatusId = cursor.getString(cursor.getColumnIndex(TwitterDbAdapter.KEY_IN_REPLY_TO_STATUS_ID));
			tweet.inReplyToUserId = cursor.getString(cursor.getColumnIndex(TwitterDbAdapter.KEY_IN_REPLY_TO_USER_ID));
			return tweet;
		}
	}

	@Override
	protected void updateTweet(Tweet tweet){
		//对所有相关表的对应消息都进行刷新（如果存在的话）
		getDb().updateTweet(TwitterDbAdapter.TABLE_FAVORITE, tweet);
		getDb().updateTweet(TwitterDbAdapter.TABLE_MENTION, tweet);
		getDb().updateTweet(TwitterDbAdapter.TABLE_TWEET, tweet);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Log.i(TAG, "onCreate.");
		super.onCreate(savedInstanceState);
		if (!checkIsLogedIn()) return;
		
		goTop(); // skip the header
		
		// Find View
		loadMoreBtn = (TextView)findViewById(R.id.ask_for_more);
		loadMoreGIF = (ProgressBar)findViewById(R.id.rectangleProgressBar);
        loadMoreAnimation = (AnimationDrawable) loadMoreGIF.getIndeterminateDrawable();

		// Mark all as read.
		// getDb().markAllMentionsRead();
		markAllRead();

		boolean shouldRetrieve = false;

		//FIXME： 该子类页面全部使用了这个统一的计时器，导致进入Mention等分页面后经常不会自动刷新
		long lastRefreshTime = mPreferences.getLong(
				Preferences.LAST_TWEET_REFRESH_KEY, 0);
		long nowTime = Utils.getNowTime();

		long diff = nowTime - lastRefreshTime;
		Log.i(TAG, "Last refresh was " + diff + " ms ago.");

		if (diff > REFRESH_THRESHOLD) {
			shouldRetrieve = true;
		} else if (Utils.isTrue(savedInstanceState, SIS_RUNNING_KEY)) {
			// Check to see if it was running a send or retrieve task.
			// It makes no sense to resend the send request (don't want dupes)
			// so we instead retrieve (refresh) to see if the message has
			// posted.
			Log.i(TAG,
					"Was last running a retrieve or send task. Let's refresh.");
			shouldRetrieve = true;
		}

		if (shouldRetrieve) {
			doRetrieve();
		}

		long lastFollowersRefreshTime = mPreferences.getLong(
				Preferences.LAST_FOLLOWERS_REFRESH_KEY, 0);

		diff = nowTime - lastFollowersRefreshTime;
		Log.i(TAG, "Last followers refresh was " + diff + " ms ago.");

		// Should Refresh Followers
		if (diff > FOLLOWERS_REFRESH_THRESHOLD && 
				(mRetrieveTask == null || mRetrieveTask.getStatus() != GenericTask.Status.RUNNING)) {
			Log.i(TAG, "Refresh followers.");
			doRetrieveFollowers();
		}
	}

	@Override
	protected void onResume() {
		Log.i(TAG, "onResume.");
		if (lastPosition != 0) {
		    mTweetList.setSelection(lastPosition);
		}
		super.onResume();
		checkIsLogedIn();
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);

		if (mRetrieveTask != null
				&& mRetrieveTask.getStatus() == GenericTask.Status.RUNNING) {
			outState.putBoolean(SIS_RUNNING_KEY, true);
		}
	}

	@Override
	protected void onRestoreInstanceState(Bundle bundle) {
		super.onRestoreInstanceState(bundle);
		// mTweetEdit.updateCharsRemain();
	}

	@Override
	protected void onDestroy() {
		Log.i(TAG, "onDestroy.");

		if (mRetrieveTask != null
				&& mRetrieveTask.getStatus() == GenericTask.Status.RUNNING) {
			mRetrieveTask.cancel(true);
		}

		// Don't need to cancel FollowersTask (assuming it ends properly).

		super.onDestroy();
	}
	
	@Override
    protected void onPause() {
		Log.i(TAG, "onPause.");
        super.onPause();
        lastPosition = mTweetList.getFirstVisiblePosition();
    }

    @Override
    protected void onRestart() {
		Log.i(TAG, "onRestart.");
        super.onRestart();
    }

    @Override
    protected void onStart() {
		Log.i(TAG, "onStart.");
        super.onStart();
    }

    @Override
    protected void onStop() {
		Log.i(TAG, "onStop.");
        super.onStop();
    }

	// UI helpers.
	
	@Override
    protected String getActivityTitle() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
	protected void adapterRefresh() {
		mTweetAdapter.notifyDataSetChanged();
		mTweetAdapter.refresh();
	}

	//  Retrieve interface
	public void updateProgress(String progress) {
		mProgressText.setText(progress);
	}
	public void draw() {
		mTweetAdapter.refresh();
	}
	public void goTop() {
        Log.i(TAG, "goTop.");
		mTweetList.setSelectionAfterHeaderView();
	}
	
	private void doRetrieveFollowers() {
        Log.i(TAG, "Attempting followers retrieve.");

        if (mFollowersRetrieveTask != null && mFollowersRetrieveTask.getStatus() == GenericTask.Status.RUNNING){
        	return;
        }else{
        	mFollowersRetrieveTask = new FollowersRetrieveTask();
        	mFollowersRetrieveTask.setListener(new TaskListener(){

				@Override
				public String getName() {
					return "FollowerRetrieve";
				}

				@Override
				public void onCancelled(GenericTask task) {
					// TODO Auto-generated method stub
					
				}

				@Override
				public void onPostExecute(GenericTask task, TaskResult result) {
					if (result == TaskResult.OK) {
						SharedPreferences sp = getPreferences();
						SharedPreferences.Editor editor = sp.edit();
						editor.putLong(Preferences.LAST_FOLLOWERS_REFRESH_KEY,
								Utils.getNowTime());
						editor.commit();
					} else {
						// Do nothing.
					}
				}

				@Override
				public void onPreExecute(GenericTask task) {
					// TODO Auto-generated method stub
					
				}

				@Override
				public void onProgressUpdate(GenericTask task, Object param) {
					// TODO Auto-generated method stub
					
				}
        		
        	});
        	mFollowersRetrieveTask.execute();
        }
    }
	
	public void onRetrieveBegin() {
		updateProgress(getString(R.string.page_status_refreshing));
	}

	public void doRetrieve() {
		Log.i(TAG, "Attempting retrieve.");

		// 旋转刷新按钮
		animRotate(refreshButton);

		if (mRetrieveTask != null && mRetrieveTask.getStatus() == GenericTask.Status.RUNNING){
			return;
		}else{
			mRetrieveTask = new RetrieveTask();
			mRetrieveTask.setListener(new TaskListener(){

				@Override
				public String getName() {
					return "RetrieveTask";
				}

				@Override
				public void onCancelled(GenericTask task) {
					// TODO Auto-generated method stub
					
				}

				@Override
				public void onPostExecute(GenericTask task, TaskResult result) {
					if (result == TaskResult.AUTH_ERROR) {
						logout();
					} else if (result == TaskResult.OK) {
						SharedPreferences.Editor editor = getPreferences().edit();
						editor.putLong(Preferences.LAST_TWEET_REFRESH_KEY, Utils
								.getNowTime());
						editor.commit();
						draw();
						goTop();
					} else {
						// Do nothing.
					}

					// 刷新按钮停止旋转
					getRefreshButton().clearAnimation();
					updateProgress("");
				}

				@Override
				public void onPreExecute(GenericTask task) {
					onRetrieveBegin();
				}

				@Override
				public void onProgressUpdate(GenericTask task, Object param) {
					draw();
				}
				
			});
			mRetrieveTask.execute();
		}
	}
	// for Retrievable interface
	public ImageButton getRefreshButton() {
		return refreshButton;
	}
	
	private class RetrieveTask extends GenericTask{

		@Override
		protected TaskResult _doInBackground(TaskParams... params) {
			List<com.ch_linghu.fanfoudroid.weibo.Status> statusList;

			String maxId = fetchMaxId(); // getDb().fetchMaxMentionId();

			try {
				statusList = getMessageSinceId(maxId);
			} catch (WeiboException e) {
				Log.e(TAG, e.getMessage(), e);
				return TaskResult.IO_ERROR;
			}

			ArrayList<Tweet> tweets = new ArrayList<Tweet>();
			HashSet<String> imageUrls = new HashSet<String>();
			
			for (com.ch_linghu.fanfoudroid.weibo.Status status : statusList) {
				if (isCancelled()) {
					return TaskResult.CANCELLED;
				}

				Tweet tweet;

				tweet = Tweet.create(status);
				tweets.add(tweet);

				imageUrls.add(tweet.profileImageUrl);

				if (isCancelled()) {
					return TaskResult.CANCELLED;
				}
			}

			addMessages(tweets, false); // getDb().addMentions(tweets, false);

			if (isCancelled()) {
				return TaskResult.CANCELLED;
			}

			//task.publishProgress();

			for (String imageUrl : imageUrls) {
				if (!Utils.isEmpty(imageUrl)) {
					// Fetch image to cache.
					try {
						getImageManager().put(imageUrl);
					} catch (IOException e) {
						Log.e(TAG, e.getMessage(), e);
					}
				}

				if (isCancelled()) {
					return TaskResult.CANCELLED;
				}
			}

			return TaskResult.OK;
		}
		
	}
	
	private class FollowersRetrieveTask extends GenericTask{

		@Override
		protected TaskResult _doInBackground(TaskParams... params) {
			try {
				// TODO: 目前仅做新API兼容性改动，待完善Follower处理
				IDs followers = getApi().getFollowersIDs();
				List<String> followerIds = Arrays.asList(followers.getIDs());
				getDb().syncFollowers(followerIds);
			} catch (WeiboException e) {
				Log.e(TAG, e.getMessage(), e);
				return TaskResult.IO_ERROR;
			}
			return TaskResult.OK;
		}
		
	}
	
}