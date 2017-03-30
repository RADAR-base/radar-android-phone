package org.radarcns.phone;

import android.os.Parcelable;

import org.radarcns.android.device.DeviceServiceProvider;

import java.util.Arrays;
import java.util.List;

import static android.Manifest.permission.READ_CALL_LOG;
import static android.Manifest.permission.READ_SMS;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

public class PhoneSensorProvider extends DeviceServiceProvider<PhoneState> {
    @Override
    public Class<?> getServiceClass() {
        return PhoneSensorService.class;
    }

    @Override
    public Parcelable.Creator<PhoneState> getStateCreator() {
        return PhoneState.CREATOR;
    }

    @Override
    public String getDisplayName() {
        return getActivity().getString(R.string.phoneServiceDisplayName);
    }

    @Override
    public List<String> needsPermissions() {
        return Arrays.asList(WRITE_EXTERNAL_STORAGE, READ_CALL_LOG, READ_SMS);
    }
}
