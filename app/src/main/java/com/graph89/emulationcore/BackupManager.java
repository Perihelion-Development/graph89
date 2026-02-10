/*
 *   Graph89 - Emulator for Android
 *  
 *	 Copyright (C) 2012-2013  Dritan Hashorva
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.

 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

package com.graph89.emulationcore;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.io.StreamCorruptedException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Message;
import android.provider.DocumentsContract;
import android.support.v4.view.ViewCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.eanema.graph89.R;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.graph89.common.CalculatorInstance;
import com.graph89.common.CalculatorInstanceHelper;
import com.graph89.common.Directories;
import com.graph89.common.ProgressDialogControl;
import com.graph89.common.Util;
import com.graph89.common.ZipHelper;
import com.graph89.controls.ControlBar;

public class BackupManager extends Graph89ActivityBase
{
	public static final int					HANDLER_SHOWPROGRESSDIALOG		= Graph89ActivityBase.MAX_HANDLER_ID + 1;
	public static final int					HANDLER_UPDATEPROGRESSDIALOG	= Graph89ActivityBase.MAX_HANDLER_ID + 2;
	public static final int					HANDLER_HIDEPROGRESSDIALOG		= Graph89ActivityBase.MAX_HANDLER_ID + 3;

	public static final int					CREATE_BACKUP_CODE				= 12;
	public static final int					READ_BACKUP_CODE				= 13;
	public static final String				BACKUP_EXTENSION				= ".g89.bak";

	private ControlBar						mControlBar						= null;
	private TextView						mExtensionMsgTextView			= null;
	private Button							mCreateBackup					= null;
	private Button 							mRestoreBackup					= null;
	private List<SelectedInstance>			mSelectedInstances				= null;
	private String							mRestoreDirectory				= null;
	private String 							mInstanceDirectory				= null;

	private static CalculatorInstanceHelper	mCalculatorInstances			= null;

	public static ProgressDialogControl		ProgressDialogObj				= new ProgressDialogControl();
	private IncomingHandler					mHandler						= new IncomingHandler(this);

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
        getWindow().setDecorFitsSystemWindows(false);
		setContentView(R.layout.backup_manager_main);
		this.setRequestedOrientation(EmulatorActivity.Orientation);

		mRestoreDirectory = Directories.getRestoreDirectory(this);
		mInstanceDirectory = Directories.getInstanceDirectory(this);

		mCalculatorInstances = new CalculatorInstanceHelper(this);

		mControlBar = new ControlBar(this);
		mControlBar.HideCalculatorTypeSpinner();

		mExtensionMsgTextView = (TextView) this.findViewById(R.id.backup_manager_extension_message_textview);
		mExtensionMsgTextView.setText("File extension used by backup manager is: " + BACKUP_EXTENSION);

		mCreateBackup = (Button) this.findViewById(R.id.backup_manager_backup_button);
		mCreateBackup.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				// create new file selection intent
				Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
				intent.addCategory(Intent.CATEGORY_OPENABLE);
				intent.setType("*/*");
				intent.putExtra(Intent.EXTRA_TITLE, Util.getTimestamp() + BACKUP_EXTENSION);
				intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, Environment.getExternalStorageDirectory().getAbsolutePath());
				// start the intent
				startActivityForResult(intent, CREATE_BACKUP_CODE);

			}
		});

		mRestoreBackup = (Button) this.findViewById(R.id.backup_manager_restore_button);
		mRestoreBackup.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				// create new file selection intent
				Intent myIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
				myIntent.addCategory(Intent.CATEGORY_OPENABLE);
				myIntent.setType("*/*");
				myIntent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, Environment.getExternalStorageDirectory().getAbsolutePath());
				// start the intent
				startActivityForResult(myIntent, READ_BACKUP_CODE);
			}
		});

        View root = findViewById(R.id.backup_manager_main);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            v.setPadding(
                    insets.getSystemWindowInsetLeft(),
                    insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(),
                    insets.getSystemWindowInsetBottom()
            );
            return insets;
        });

	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode == RESULT_OK) {
			switch (requestCode) {
				case READ_BACKUP_CODE:
					if (resultCode == Activity.RESULT_OK) {
						if (data != null && data.getData() != null) {
							// get the filename from the input URI
							String filename = Util.getFileName(this, data.getData());

							//check if file extension is correct
							if (filename.toUpperCase().endsWith(BACKUP_EXTENSION.toUpperCase())) {
								try {
									RestoreBackup(data.getData());
								} catch (Exception e) {
									String errorMsg = "Caught exception restoring backup";
									Log.d("Graph89", errorMsg);
									Log.d("Graph89", e.getStackTrace().toString());
									Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
								}
							} else {
								// bad file extension
								String errorMsg = "Bad file extension. Extension must be: " + BACKUP_EXTENSION;
								Log.d("Graph89", errorMsg);
								Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
							}
						} else {
							// data is null
							Log.d("Graph89","File URI not found");
						}
					} else {
						// result code is RESULT_OK
						Log.d("Graph89", "User cancelled file browsing");
					}
					break;

				case CREATE_BACKUP_CODE:
					if (resultCode == Activity.RESULT_OK) {
						if (data != null && data.getData() != null) {
							// get the filename from the input URI
							String filename = Util.getFileName(this, data.getData());

							//check if file extension is correct
							if (filename.toUpperCase().endsWith(BACKUP_EXTENSION.toUpperCase())) {
								try {
									CreateNewBackup(data.getData());
								} catch (Exception e) {
									String errorMsg = "Caught exception creating backup";
									Log.d("Graph89", errorMsg);
									Log.d("Graph89", e.getStackTrace().toString());
									Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
								}
							} else {
								// bad file extension
								String errorMsg = "Bad file extension. Extension must be: " + BACKUP_EXTENSION;
								Log.d("Graph89", errorMsg);
								Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
							}
						} else {
							// data is null
							Log.d("Graph89","File URI not found");
						}
					} else {
						// result code is RESULT_OK
						Log.d("Graph89", "User cancelled file browsing");
					}
					break;
			}
		}
	}

	private void CreateNewBackup(Uri outFile) throws Exception
	{
		ProgressDialogObj.Message = "Backing up ...";
		HandlerShowProgressDialog();

		Backup bk = new Backup();
		bk.BackupDescription = Util.getFileName(this, outFile);
		bk.BackupDate = new Date();
		bk.ConfigJson = mCalculatorInstances.toJson();
		bk.BackupData = ZipHelper.zipDir(mInstanceDirectory);

		WriteBackupToFile(this, bk, outFile);

		HandlerHideProgressDialog();
	}

	private static void WriteBackupToFile(Context context, Backup bk, Uri outFile) throws IOException
	{
		OutputStream file = context.getContentResolver().openOutputStream(outFile);
		OutputStream buffer = new BufferedOutputStream(file);
		ObjectOutput output = new ObjectOutputStream(buffer);
		try
		{
			output.writeObject(bk);
		}
		finally
		{
			output.close();
		}
	}

	private static Backup getBackupFromFile(Context context, Uri inFile) throws StreamCorruptedException, IOException, ClassNotFoundException {
		// open the input streams
		InputStream is = context.getContentResolver().openInputStream(inFile);
		ObjectInputStream ois = new ObjectInputStream(is);
		Backup b = (Backup) ois.readObject();
		b.FileName = Util.getFileName(context, inFile);
		ois.close();
		return b;
	}

	private void RestoreBackup(Uri inFile) throws StreamCorruptedException, IOException, ClassNotFoundException
	{
		final View view = LayoutInflater.from(this).inflate(R.layout.backup_manager_restore_backup, (ViewGroup) this.findViewById(R.id.backup_manager_restore_backup_layout));
		final ListView restoreList = (ListView) view.findViewById(R.id.backup_manager_restore_list);
		final Spinner restoreType = (Spinner) view.findViewById(R.id.backup_manager_restore_type);

		mSelectedInstances = new ArrayList<SelectedInstance>();

		final Backup backupToRestore = getBackupFromFile(this, inFile);

		ArrayList<CalculatorInstance> instances = new ArrayList<CalculatorInstance>();

		Gson gsonHelper = new Gson();
		instances = gsonHelper.fromJson(backupToRestore.ConfigJson, new TypeToken<List<CalculatorInstance>>() {
		}.getType());

		for (int i = 0; i < instances.size(); ++i)
		{
			SelectedInstance sb = new SelectedInstance();
			sb.Instance = (CalculatorInstance) instances.get(i);
			sb.IsSelected = true;
			mSelectedInstances.add(sb);
		}

		BackupListAdapter adapter = new BackupListAdapter(this, mSelectedInstances);
		restoreList.setAdapter(adapter);

		String windowTitle = "Restore Backup";

		final AlertDialog addEditdialog = new AlertDialog.Builder(this).setView(view).setTitle(windowTitle).setPositiveButton(android.R.string.ok, null).setNegativeButton(android.R.string.cancel, new Dialog.OnClickListener() {
			@Override
			public void onClick(DialogInterface d, int which)
			{
				d.dismiss();
			}
		}).create();

		addEditdialog.setOnShowListener(new DialogInterface.OnShowListener() {
			@Override
			public void onShow(DialogInterface dialog)
			{
				Button b = addEditdialog.getButton(AlertDialog.BUTTON_POSITIVE);
				b.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View view)
					{
						new Thread(new Runnable() {

							public void run()
							{
								try
								{
									RestoreBackup(backupToRestore, restoreType.getSelectedItem().toString());
								}
								catch (Exception e)
								{
								}
							}
						}).start();

						addEditdialog.dismiss();
					}
				});
			}
		});

		addEditdialog.setCanceledOnTouchOutside(false);

		addEditdialog.show();
	}

	private void RestoreBackup(Backup backupToRestore, String restoreType) throws Exception
	{
		ProgressDialogObj.Message = "Restoring ...";
		HandlerShowProgressDialog();

		Util.deleteDirectory(new File(mRestoreDirectory));
		Util.CreateDirectory(mRestoreDirectory);

		ZipHelper.Unzip(mRestoreDirectory, backupToRestore.BackupData);

		boolean isMerge = restoreType.startsWith("Merge");

		ArrayList<IDNamePair> installedInstances = new ArrayList<IDNamePair>();
		ArrayList<IDNamePair> intancesToInstall = new ArrayList<IDNamePair>();

		for (int i = 0; i < mCalculatorInstances.size(); ++i)
		{
			IDNamePair pair = new IDNamePair();
			pair.ID = mCalculatorInstances.GetByIndex(i).ID;
			pair.Name = mCalculatorInstances.GetByIndex(i).Title;
			pair.index = i;
			installedInstances.add(pair);
		}

		for (int i = 0; i < mSelectedInstances.size(); ++i)
		{
			SelectedInstance si = mSelectedInstances.get(i);
			if (si.IsSelected)
			{
				IDNamePair pair = new IDNamePair();
				pair.ID = si.Instance.ID;
				pair.Name = si.Instance.Title;
				pair.index = i;
				intancesToInstall.add(pair);
			}
		}

		if (isMerge)
		{
			for (int i = 0; i < intancesToInstall.size(); ++i)
			{
				IDNamePair newInstance = intancesToInstall.get(i);

				IDNamePair oldInstance = null;

				for (int j = 0; j < installedInstances.size(); ++j)
				{
					IDNamePair c = installedInstances.get(j);

					if (!c.matched && c.Name.equals(newInstance.Name))
					{
						oldInstance = c;
						c.matched = true;
						break;
					}
				}

				if (oldInstance == null)
				{
					AddNewImage(mSelectedInstances.get(newInstance.index).Instance, backupToRestore);
				}
				else
				{
					OverwriteImage(mSelectedInstances.get(newInstance.index).Instance, mCalculatorInstances.GetByIndex(oldInstance.index), backupToRestore);
				}
			}
		}
		else
		{
			for (int i = 0; i < intancesToInstall.size(); ++i)
			{
				CalculatorInstance instance = mSelectedInstances.get(intancesToInstall.get(i).index).Instance;
				AddNewImage(instance, backupToRestore);
			}
		}

		HandlerHideProgressDialog();
	}

	private void OverwriteImage(CalculatorInstance backedupInstance, CalculatorInstance destinationInstance, Backup bk)
	{
		File imgFile = new File(backedupInstance.ImageFilePath);
		File stateFile = new File(backedupInstance.StateFilePath);

		File newImgFile = new File(mInstanceDirectory + destinationInstance.ID + "/" + imgFile.getName());
		File newStateFile = new File(mInstanceDirectory + destinationInstance.ID + "/" + stateFile.getName());

		File oldImgFile = new File(mRestoreDirectory + backedupInstance.ID + "/" + imgFile.getName());
		File oldStateFile = new File(mRestoreDirectory + backedupInstance.ID + "/" + stateFile.getName());

		backedupInstance.ImageFilePath = newImgFile.getAbsolutePath();
		backedupInstance.StateFilePath = newStateFile.getAbsolutePath();

		Util.deleteDirectory(newImgFile.getParentFile());
		Util.CreateDirectory(newImgFile.getParentFile().getAbsolutePath());
		Util.CreateDirectory(newStateFile.getParentFile().getAbsolutePath());

		oldImgFile.renameTo(newImgFile);
		oldStateFile.renameTo(newStateFile);

		List<CalculatorInstance> installedInstances = mCalculatorInstances.GetInstances();

		for (int i = 0; i < installedInstances.size(); ++i)
		{
			if (installedInstances.get(i).ID == destinationInstance.ID)
			{
				backedupInstance.ID = destinationInstance.ID;
				installedInstances.set(i, backedupInstance);
				break;
			}
		}

		mCalculatorInstances.Save();
	}

	private void AddNewImage(CalculatorInstance backedupInstance, Backup bk)
	{
		int oldID = backedupInstance.ID;

		mCalculatorInstances.Add(backedupInstance);

		File imgFile = new File(backedupInstance.ImageFilePath);
		File stateFile = new File(backedupInstance.StateFilePath);

		File newImgFile = new File(mInstanceDirectory + backedupInstance.ID + "/" + imgFile.getName());
		File newStateFile = new File(mInstanceDirectory + backedupInstance.ID + "/" + stateFile.getName());

		File oldImgFile = new File(mRestoreDirectory + oldID + "/" + imgFile.getName());
		File oldStateFile = new File(mRestoreDirectory + oldID + "/" + stateFile.getName());

		backedupInstance.ImageFilePath = newImgFile.getAbsolutePath();
		backedupInstance.StateFilePath = newStateFile.getAbsolutePath();

		Util.deleteDirectory(newImgFile.getParentFile());
		Util.CreateDirectory(newImgFile.getParentFile().getAbsolutePath());
		Util.CreateDirectory(newStateFile.getParentFile().getAbsolutePath());

		oldImgFile.renameTo(newImgFile);
		oldStateFile.renameTo(newStateFile);

		mCalculatorInstances.Save();
	}

	public void HandlerShowProgressDialog()
	{
		mHandler.sendEmptyMessage(BackupManager.HANDLER_SHOWPROGRESSDIALOG);
	}

	public void HandlerHideProgressDialog()
	{
		mHandler.sendEmptyMessage(BackupManager.HANDLER_HIDEPROGRESSDIALOG);
	}

	private void ShowProgressDialog()
	{
		if (ProgressDialogObj.Dialog != null) ProgressDialogObj.Dialog.dismiss();

		ProgressDialogObj.Dialog = new ProgressDialog(this);
		ProgressDialogObj.Dialog.setMessage(ProgressDialogObj.Message);
		ProgressDialogObj.Dialog.setCancelable(false);
		ProgressDialogObj.Dialog.show();
	}

	private void UpdateProgressDialog()
	{
		if (ProgressDialogObj.Dialog == null) return;
		ProgressDialogObj.Dialog.setMessage(ProgressDialogObj.Message);
	}

	private void HideProgressDialog()
	{
		if (ProgressDialogObj.Dialog == null) return;
		ProgressDialogObj.Dialog.dismiss();
		ProgressDialogObj.Dialog = null;
		ProgressDialogObj.Message = "";
	}

	@Override
	protected void handleMessage(Message msg)
	{
		super.handleMessage(msg);

		switch (msg.what)
		{
			case BackupManager.HANDLER_SHOWPROGRESSDIALOG:
				ShowProgressDialog();
				break;
			case BackupManager.HANDLER_UPDATEPROGRESSDIALOG:
				UpdateProgressDialog();
				break;
			case BackupManager.HANDLER_HIDEPROGRESSDIALOG:
				HideProgressDialog();
				break;
		}
	}

	private class BackupListAdapter extends ArrayAdapter<SelectedInstance>
	{
		private List<SelectedInstance>	mObjects	= null;

		public BackupListAdapter(Context context, List<SelectedInstance> objects)
		{
			super(context, R.layout.backup_list_item, android.R.id.text1, objects);
			mObjects = objects;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent)
		{
			View row = null;

			if (convertView == null)
			{
				LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				row = inflater.inflate(R.layout.backup_list_item, parent, false);
			}
			else
			{
				row = convertView;
			}

			CheckBox select = (CheckBox) row.findViewById(R.id.backup_listitem_checkbox);
			SelectedInstance object = mObjects.get(position);
			select.setTag(position);

			select.setChecked(object.IsSelected);

			select.setOnCheckedChangeListener(new OnCheckedChangeListener() {
				public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
				{
					int pos = (Integer) buttonView.getTag();
					mSelectedInstances.get(pos).IsSelected = isChecked;
				}
			});

			TextView textView = (TextView) row.findViewById(R.id.backup_listitem_instance);
			// Set single line
			textView.setSingleLine(true);

			textView.setText(object.Instance.Title);

			return row;
		}
	}
}

class IDNamePair
{
	public int		ID;
	public String	Name;

	public int		index			= 0;
	public boolean	matched			= false;
	public int		matchedWithID	= 0;
}

class SelectedInstance
{
	public CalculatorInstance	Instance;
	public Boolean				IsSelected;
}

@SuppressWarnings("serial")
class Backup implements Serializable
{
	public String	BackupDescription	= null;
	public Date		BackupDate			= null;
	public String	ConfigJson			= null;
	public byte[]	BackupData			= null;

	public String	FileName			= null;

	public String	ReservedString		= null;
	public int		ReservedInt			= 0;
}
