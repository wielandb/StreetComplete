package de.westnordost.streetcomplete.location;

import android.Manifest;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;

import de.westnordost.streetcomplete.R;

import static android.location.LocationManager.PROVIDERS_CHANGED_ACTION;

/** Manages the process to ensure that the app can access the user's location. Two steps:
 *  <ol>
 *      <li>ask for permission</li>
 *      <li>ask for location to be turned on</li>
 *  </ol>
 *
 *  This fragment reports back to the Activity it is attached to via LocationRequestListener.
 *  The process is started via {@link #startRequest()} */
public class LocationRequestFragment extends Fragment
{
	private static final int LOCATION_PERMISSION_REQUEST = 1;
	private static final int LOCATION_TURN_ON_REQUEST = 2;

	public interface LocationRequestListener
	{
		void onLocationRequestFinished(LocationState withLocationState);
	}
	private LocationRequestListener callbackListener;

	private LocationState state;
	private boolean inProgress;
	private BroadcastReceiver locationProviderChangedReceiver;

	public LocationRequestFragment()
	{
		super();
		state = null;
	}

	/** Start location request process. When already started, will not be started again. */
	public void startRequest()
	{
		if(!inProgress)
		{
			inProgress = true;
			state = null;
			nextStep();
		}
	}

	private void nextStep()
	{
		if(state == null || state == LocationState.DENIED)
		{
			requestLocationPermissions();
		}
		else if(state == LocationState.ALLOWED)
		{
			requestLocationSettingsToBeOn();
		}
		else if(state == LocationState.ENABLED)
		{
			finish();
		}
	}

	private void finish()
	{
		inProgress = false;
		callbackListener.onLocationRequestFinished(state);
	}

	@Override public void onAttach(Context context) {
		super.onAttach(context);
		callbackListener = (LocationRequestListener) context;
	}

	@Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
										   @NonNull int[] grantResults)
	{
		// must be for someone else...
		if(requestCode != LOCATION_PERMISSION_REQUEST) return;
		if(permissions.length == 0 || !permissions[0].equals(Manifest.permission.ACCESS_FINE_LOCATION))
			return;

		if(grantResults[0] == PackageManager.PERMISSION_GRANTED)
		{
			requestLocationPermissions(); // retry then...
		}
		else
		{
			new AlertDialog.Builder(getContext())
					.setMessage(R.string.no_location_permission_warning)
					.setPositiveButton(R.string.retry,
							new DialogInterface.OnClickListener()
							{
								@Override
								public void onClick(DialogInterface dialog, int which)
								{
									requestLocationPermissions();
								}
							})
					.setNegativeButton(android.R.string.cancel,
							new DialogInterface.OnClickListener()
							{
								@Override public void onClick(DialogInterface dialog, int which)
								{
									state = LocationState.DENIED;
									finish();
								}
							})
					.setOnCancelListener(
							new DialogInterface.OnCancelListener()
							{
								@Override public void onCancel(DialogInterface dialog)
								{
									state = LocationState.DENIED;
									finish();
								}
							}
					)
					.show();
		}
	}

	@Override public void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		// must be for someone else...
		if(requestCode != LOCATION_TURN_ON_REQUEST) return;
		// we ignore the resultCode, because we always get Activity.RESULT_CANCELED. Instead, we
		// check if the conditions are fulfilled now
		requestLocationSettingsToBeOn();
	}

	private void requestLocationPermissions()
	{
		if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION)
				== PackageManager.PERMISSION_GRANTED)
		{
			state = LocationState.ALLOWED;
			nextStep();

		} else {
			requestPermissions(new String[]{ Manifest.permission.ACCESS_FINE_LOCATION },
					LOCATION_PERMISSION_REQUEST);
		}
	}

	private void requestLocationSettingsToBeOn()
	{
		LocationManager mgr = (LocationManager) getContext().getSystemService(Context.LOCATION_SERVICE);

		if(mgr.isProviderEnabled(LocationManager.GPS_PROVIDER))
		{
			state = LocationState.ENABLED;
			nextStep();
		}
		else
		{
			final AlertDialog dlg = new AlertDialog.Builder(getContext())
					.setMessage(R.string.turn_on_location_request)
					.setPositiveButton(android.R.string.yes,
							new DialogInterface.OnClickListener()
							{
								@Override public void onClick(DialogInterface dialog, int which)
								{
									dialog.dismiss();
									Intent viewIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
									startActivityForResult(viewIntent, LOCATION_TURN_ON_REQUEST);
								}
							})
					.setNegativeButton(android.R.string.no,
							new DialogInterface.OnClickListener()
							{
								@Override public void onClick(DialogInterface dialog, int which)
								{
									cancelTurnLocationOnDialog();
								}
							})
					.setOnCancelListener(
							new DialogInterface.OnCancelListener()
							{
								@Override public void onCancel(DialogInterface dialog)
								{
									cancelTurnLocationOnDialog();
								}
							}
					).create();

			// the user may turn on location in the pull-down-overlay, without actually going into
			// settings dialog
			registerForLocationProviderChanges(dlg);

			dlg.show();
		}
	}

	private void cancelTurnLocationOnDialog()
	{
		unregisterForLocationProviderChanges();
		finish();
	}

	private void registerForLocationProviderChanges(final AlertDialog dlg)
	{
		locationProviderChangedReceiver = new BroadcastReceiver()
		{
			@Override public void onReceive(Context context, Intent intent)
			{
				dlg.dismiss();
				unregisterForLocationProviderChanges();
				requestLocationSettingsToBeOn();
			}
		};
		getActivity().registerReceiver(locationProviderChangedReceiver,
				new IntentFilter(PROVIDERS_CHANGED_ACTION));
	}

	private void unregisterForLocationProviderChanges()
	{
		if(locationProviderChangedReceiver != null)
		{
			getActivity().unregisterReceiver(locationProviderChangedReceiver);
			locationProviderChangedReceiver = null;
		}
	}

	@Override public void onStop()
	{
		super.onStop();
		unregisterForLocationProviderChanges();
	}

	public LocationState getState()
	{
		return state != null ? state : LocationState.DENIED;
	}

	@Override public void onSaveInstanceState(Bundle outState)
	{
		super.onSaveInstanceState(outState);
		if(state != null) outState.putString("locationState", state.name());
		outState.putBoolean("inProgress", inProgress);
	}

	@Override public void onActivityCreated(@Nullable Bundle savedInstanceState)
	{
		super.onActivityCreated(savedInstanceState);
		if(savedInstanceState != null)
		{
			String stateName = savedInstanceState.getString("locationState");
			if(stateName != null) state = LocationState.valueOf(stateName);
			inProgress = savedInstanceState.getBoolean("inProgress");
		}
	}
}
